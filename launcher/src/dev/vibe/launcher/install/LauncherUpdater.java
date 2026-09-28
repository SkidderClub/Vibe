package dev.vibe.launcher.install;

import dev.vibe.launcher.VibeLauncher;
import dev.vibe.launcher.core.AppLog;
import dev.vibe.launcher.core.AppPaths;
import dev.vibe.launcher.core.Http;
import dev.vibe.launcher.core.I18n;
import dev.vibe.launcher.core.Json;
import dev.vibe.launcher.core.Platform;
import dev.vibe.launcher.core.Progress;
import dev.vibe.launcher.core.Version;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
            throw new IOException(I18n.t("The launcher update failed its checksum test and was discarded."));
        }
        return staged;
    }

    /**
     * Replaces the running JAR once this process has exited, then starts it again.
     * The caller must exit the JVM right after this returns.
     */
    public void installOnExit(Path staged) throws IOException {
        Path current = Platform.currentJar();
        if (current == null) throw new IOException(I18n.t("Automatic updates only work when the launcher runs from VibeLauncher.jar."));
        String java = Platform.currentJava(true).toString();
        if (Platform.windows()) {
            Path script = paths.root().resolve("update-launcher.cmd");
            String text = "@echo off\r\n"
                    + "set tries=0\r\n"
                    + ":retry\r\n"
                    // ping instead of timeout: timeout refuses to run without a console.
                    + "ping -n 2 127.0.0.1 >nul\r\n"
                    + "copy /y \"" + staged + "\" \"" + current + "\" >nul 2>&1\r\n"
                    + "if not errorlevel 1 goto started\r\n"
                    + "set /a tries+=1\r\n"
                    + "if %tries% lss 30 goto retry\r\n"
                    + "exit /b 1\r\n"
                    + ":started\r\n"
                    + "del /q \"" + staged + "\" >nul 2>&1\r\n"
                    + "start \"\" \"" + java + "\" -jar \"" + current + "\" --wait-for-lock\r\n";
            Files.write(script, text.getBytes(StandardCharsets.UTF_8));
            start(new ProcessBuilder("cmd.exe", "/c", script.toString()));
        } else {
            Path script = paths.root().resolve("update-launcher.sh");
            String text = "#!/bin/sh\n"
                    + "for i in $(seq 1 30); do\n"
                    + "  sleep 1\n"
                    + "  if cp -f " + quote(staged) + " " + quote(current) + "; then\n"
                    + "    rm -f " + quote(staged) + "\n"
                    + "    exec " + quote(Paths.get(java)) + " -jar " + quote(current) + " --wait-for-lock\n"
                    + "  fi\n"
                    + "done\n";
            Files.write(script, text.getBytes(StandardCharsets.UTF_8));
            start(new ProcessBuilder("/bin/sh", script.toString()));
        }
        log.info("Launcher update staged; restarting.");
    }

    /** Starts this launcher again after the current process has exited. */
    public static void restart() throws IOException {
        Path current = Platform.currentJar();
        if (current == null) throw new IOException(I18n.t("Restart the launcher manually to apply this change."));
        start(new ProcessBuilder(Platform.currentJava(true).toString(), "-jar", current.toString(), "--wait-for-lock"));
    }

    private static void start(ProcessBuilder builder) throws IOException {
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(Platform.nullDevice()));
        builder.redirectInput(ProcessBuilder.Redirect.from(Platform.nullDevice()));
        builder.start();
    }

    private static String quote(Path path) { return "'" + path.toString().replace("'", "'\\''") + "'"; }

    private static String trusted(String url) {
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && "github.com".equalsIgnoreCase(uri.getHost()) ? url : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
