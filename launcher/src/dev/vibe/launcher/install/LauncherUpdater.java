package dev.vibe.launcher.install;

import dev.vibe.launcher.VibeLauncher;
import dev.vibe.launcher.core.AppLog;
import dev.vibe.launcher.core.AppPaths;
import dev.vibe.launcher.core.ErrorCode;
import dev.vibe.launcher.core.Http;
import dev.vibe.launcher.core.I18n;
import dev.vibe.launcher.core.Json;
import dev.vibe.launcher.core.LauncherException;
import dev.vibe.launcher.core.Platform;
import dev.vibe.launcher.core.Progress;
import dev.vibe.launcher.core.Version;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Self-updates from GitHub releases. Only assets named {@code VibeLauncher*.jar}
 * with a matching {@code .sha256} file count, so the mod's own release JARs are
 * never mistaken for a launcher build.
 */
public final class LauncherUpdater {
    public static final class Release {
        public final String version, jarUrl, checksumUrl, pageUrl;
        Release(String version, String jarUrl, String checksumUrl, String pageUrl) {
            this.version = version; this.jarUrl = jarUrl; this.checksumUrl = checksumUrl; this.pageUrl = pageUrl;
        }
    }

    private static final Pattern JAR = Pattern.compile("(?i)VibeLauncher[-_ ]?v?([0-9][0-9A-Za-z.\\-]*)?\\.jar");
    private final AppPaths paths;
    private final AppLog log;

    public LauncherUpdater(AppPaths paths, AppLog log) {
        this.paths = paths;
        this.log = log;
    }

    /** The newest launcher release that is newer than this build, or {@code null}. */
    public Release check() throws IOException {
        String url = "https://api.github.com/repos/" + VibeLauncher.OWNER + "/" + VibeLauncher.REPOSITORY + "/releases?per_page=30";
        Release best = null;
        for (Object item : Json.array(Json.parse(Http.getText(url, 8 * 1024 * 1024)))) {
            Map<String, Object> release = Json.object(item);
            if (Json.bool(release, "draft") || Json.bool(release, "prerelease")) continue;
            String tag = Json.string(release, "tag_name");
            String jar = null, checksum = null, version = null;
            List<Object> assets = Json.array(release, "assets");
            for (Object assetItem : assets) {
                Map<String, Object> asset = Json.object(assetItem);
                Matcher matcher = JAR.matcher(Json.string(asset, "name"));
                if (!matcher.matches()) continue;
                jar = trusted(Json.string(asset, "browser_download_url"));
                version = matcher.group(1);
                String expected = Json.string(asset, "name") + ".sha256";
                for (Object other : assets) {
                    Map<String, Object> candidate = Json.object(other);
                    String name = Json.string(candidate, "name");
                    if (name.equalsIgnoreCase(expected) || name.equalsIgnoreCase(Json.string(asset, "name").replaceFirst("(?i)\\.jar$", ".sha256"))) {
                        checksum = trusted(Json.string(candidate, "browser_download_url"));
                    }
                }
                break;
            }
            if (jar == null || checksum == null) continue;
            if (version == null || version.isEmpty()) {
                if (!tag.toLowerCase(Locale.ROOT).contains("launcher")) continue;
                version = tag.replaceFirst("(?i)^launcher[-_]?", "");
            }
            version = version.replaceFirst("^[vV]", "");
            if (!Version.isNewer(version, VibeLauncher.VERSION)) continue;
            if (best == null || Version.isNewer(version, best.version)) best = new Release(version, jar, checksum, Json.string(release, "html_url"));
        }
        return best;
    }

    /** Downloads and verifies the release. Returns the staged JAR. */
    public Path download(Release release, Progress progress) throws IOException {
        Path staged = paths.root().resolve("VibeLauncher-" + release.version.replaceAll("[^0-9A-Za-z.-]", "") + ".jar");
        Http.download(release.jarUrl, staged, 64L * 1024L * 1024L, progress.bytes(I18n.t("Downloading launcher {0}", release.version)));
        String expected = Http.getText(release.checksumUrl, 4096).trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (!expected.matches("[0-9a-f]{64}") || !expected.equals(Http.sha256(staged))) {
            Files.deleteIfExists(staged);
            throw new LauncherException(ErrorCode.LAUNCHER_CHECKSUM, I18n.t("The launcher update failed its checksum test and was discarded."));
        }
        return staged;
    }

    /**
     * Replaces the running JAR once this process has exited, then starts it again.
     * The work is done by {@link SelfUpdate} in a new JVM that runs from a copy of
     * this JAR, so it neither depends on the downloaded version nor locks either file.
     * The caller must exit the JVM right after this returns.
     */
    public void installOnExit(Path staged) throws IOException {
        Path current = Platform.currentJar();
        if (current == null) throw new LauncherException(ErrorCode.NOT_A_JAR, I18n.t("Automatic updates only work when the launcher runs from VibeLauncher.jar."));
        if (!Files.isWritable(current)) {
            throw new LauncherException(ErrorCode.ACCESS_DENIED, I18n.t("{0} cannot be replaced. Move the launcher to a folder you can write to, such as your desktop.", current));
        }
        Path helper = paths.root().resolve("launcher-updater.jar");
        Files.copy(current, helper, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        String java = Platform.currentJava(true).toString();
        try {
            start(new ProcessBuilder(java, "-cp", helper.toString(), SelfUpdate.class.getName(),
                    staged.toString(), current.toString(), java, paths.lockFile().toString()));
        } catch (IOException error) {
            throw new LauncherException(ErrorCode.LAUNCHER_UPDATE, I18n.t("The update helper could not be started."), error);
        }
        log.info("Launcher update " + staged.getFileName() + " staged; restarting.");
    }

    /** Deletes files a finished self-update leaves in the data folder. */
    public void cleanUp() {
        for (Path file : dev.vibe.launcher.core.FileUtil.children(paths.root())) {
            String name = file.getFileName().toString();
            boolean leftover = name.equals("launcher-updater.jar") || name.equals("update-launcher.cmd") || name.equals("update-launcher.sh")
                    || name.equals("replace-launcher.cmd") || (name.startsWith("VibeLauncher-") && name.endsWith(".jar"));
            if (!leftover || file.equals(Platform.currentJar())) continue;
            try { Files.deleteIfExists(file); } catch (IOException ignored) { /* still in use; next time */ }
        }
    }

    /** Starts this launcher again after the current process has exited. */
    public static void restart() throws IOException {
        Path current = Platform.currentJar();
        if (current == null) throw new LauncherException(ErrorCode.NOT_A_JAR, I18n.t("Restart the launcher manually to apply this change."));
        start(new ProcessBuilder(Platform.currentJava(true).toString(), "-jar", current.toString(), "--wait-for-lock"));
    }

    private static void start(ProcessBuilder builder) throws IOException {
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(Platform.nullDevice()));
        builder.redirectInput(ProcessBuilder.Redirect.from(Platform.nullDevice()));
        builder.start();
    }

    private static String trusted(String url) {
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && "github.com".equalsIgnoreCase(uri.getHost()) ? url : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
