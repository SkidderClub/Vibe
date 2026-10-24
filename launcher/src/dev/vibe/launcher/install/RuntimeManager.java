package dev.vibe.launcher.install;

import dev.vibe.launcher.core.AppLog;
import dev.vibe.launcher.core.AppPaths;
import dev.vibe.launcher.core.ErrorCode;
import dev.vibe.launcher.core.FileUtil;
import dev.vibe.launcher.core.Http;
import dev.vibe.launcher.core.I18n;
import dev.vibe.launcher.core.Json;
import dev.vibe.launcher.core.LauncherException;
import dev.vibe.launcher.core.Platform;
import dev.vibe.launcher.core.Progress;
import dev.vibe.launcher.core.Settings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The private Eclipse Temurin 8 runtime that starts Minecraft 1.8.9.
 *
 * <p>Downloads come from the Adoptium API and are verified against the SHA-256
 * checksum it publishes before they are unpacked. Minecraft 1.8.9 only runs on
 * Java 8, so that is the only runtime the launcher keeps.</p>
 */
public final class RuntimeManager {
    private final AppPaths paths;
    private final Settings settings;
    private final AppLog log;

    public RuntimeManager(AppPaths paths, Settings settings, AppLog log) {
        this.paths = paths;
        this.settings = settings;
        this.log = log;
    }

    /** Whether Java 8 is present, without touching the network. */
    public boolean isReady() {
        return usable(settings.java8Home(), false, 8) != null;
    }

    public Path ensure(Progress progress) throws IOException {
        Path java8 = usable(settings.java8Home(), false, 8);
        if (java8 == null) {
            warnIfWrongVersion(settings.java8Home(), 8);
            java8 = install(progress);
            settings.setJava8Home(java8.toString());
        }
        return java8;
    }

    /** A runtime configured by hand that has the wrong version is replaced by the launcher's own. */
    private void warnIfWrongVersion(String configured, int expected) {
        if (configured == null || configured.trim().isEmpty()) return;
        try {
            int actual = majorVersion(Paths.get(configured.trim()));
            if (actual > 0 && actual != expected) {
                log.warn(ErrorCode.WRONG_JAVA.id() + " The configured Java " + expected + " at " + configured + " is Java " + actual
                        + "; the launcher installs its own Java 8 instead.");
            }
        } catch (Exception ignored) {
            // Not a valid path: install() replaces it anyway.
        }
    }

    /** Deletes the private runtime and downloads it again. */
    public Path reinstall(Progress progress) throws IOException {
        settings.setJava8Home("");
        FileUtil.deleteTree(paths.runtimes().resolve("temurin-8"));
        return ensure(progress);
    }

    private Path install(Progress progress) throws IOException {
        Path output = paths.runtimes().resolve("temurin-8");
        Path existing = findHome(output, false, 8);
        if (existing != null) return existing;

        String label = "Java 8";
        progress.update(I18n.t("Looking up {0}", label), null, -1);
        // Minecraft 1.8.9's LWJGL 2 has no Apple Silicon natives; Java 8 runs under Rosetta there.
        String arch = Platform.os() == Platform.Os.MAC ? "x64" : Platform.architecture();
        String query = "https://api.adoptium.net/v3/assets/latest/8/hotspot?architecture=" + arch
                + "&image_type=jre&os=" + Platform.adoptiumOs() + "&vendor=eclipse";
        List<Object> assets = Json.array(Json.parse(Http.getText(query, 4 * 1024 * 1024)));
        if (assets.isEmpty()) {
            throw new LauncherException(ErrorCode.JAVA_UNAVAILABLE, I18n.t("Adoptium has no {0} build for {1}.", label, Platform.adoptiumOs() + "/" + arch));
        }
        Map<String, Object> binary = Json.object(Json.object(assets.get(0)), "binary");
        Map<String, Object> pack = Json.object(binary, "package");
        String link = Json.string(pack, "link");
        String name = Json.string(pack, "name");
        String checksum = Json.string(pack, "checksum").toLowerCase(Locale.ROOT);
        long size = Json.number(pack, "size", 0);
        if (link.isEmpty() || !checksum.matches("[0-9a-f]{64}")) {
            throw new LauncherException(ErrorCode.UNEXPECTED_RESPONSE, I18n.t("Adoptium returned an incomplete {0} download.", label));
        }

        Files.createDirectories(paths.runtimes());
        Path archive = paths.runtimes().resolve(name.isEmpty() ? "temurin-8.zip" : name.replaceAll("[^A-Za-z0-9._-]", "_"));
        Path temporary = paths.runtimes().resolve("temurin-8.partial");
        try {
            String message = I18n.t("Downloading {0}", label);
            Http.download(link, archive, Math.max(size * 2, 600L * 1024L * 1024L), progress.range(0, 0.85).bytes(message));
            progress.update(I18n.t("Verifying {0}", label), null, 0.86);
            String actual = Http.sha256(archive);
            if (!actual.equals(checksum)) {
                throw new LauncherException(ErrorCode.JAVA_DAMAGED, I18n.t("The {0} download is damaged (checksum mismatch).", label));
            }
            FileUtil.deleteTree(temporary);
            try {
                Archives.extract(archive, temporary, I18n.t("Unpacking {0}", label), progress.range(0.87, 1));
            } catch (IOException error) {
                throw LauncherException.wrap(error, ErrorCode.JAVA_DAMAGED, I18n.t("The {0} archive could not be unpacked.", label));
            }
            FileUtil.deleteTree(output);
            Files.move(temporary, output);
            Path home = findHome(output, false, 8);
            if (home == null) {
                throw new LauncherException(ErrorCode.JAVA_BROKEN, I18n.t("The {0} download does not contain a usable Java installation.", label));
            }
            log.info("Installed " + label + " at " + home + ".");
            return home;
        } finally {
            Files.deleteIfExists(archive);
            FileUtil.deleteTreeQuietly(temporary);
        }
    }

    static Path usable(String configured, boolean requireJdk, int major) {
        if (configured == null || configured.trim().isEmpty()) return null;
        try {
            Path home = Paths.get(configured.trim()).toAbsolutePath().normalize();
            return isHome(home, requireJdk, major) ? home : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Also requires the class library and the expected major version. */
    private static boolean isHome(Path home, boolean requireJdk, int major) {
        boolean library = Files.isRegularFile(home.resolve("lib").resolve("modules")) || Files.isRegularFile(home.resolve("lib").resolve("rt.jar"));
        if (!library || !Files.isRegularFile(Platform.javaExecutable(home, false)) || (requireJdk && !Files.isRegularFile(Platform.javacExecutable(home)))) return false;
        int actual = majorVersion(home);
        return major <= 0 || actual <= 0 || actual == major;
    }

    /** The major version from the {@code release} file of a Java home; 0 when unknown. */
    static int majorVersion(Path home) {
        Path release = home.resolve("release");
        if (!Files.isRegularFile(release)) return 0;
        try {
            for (String line : Files.readAllLines(release, StandardCharsets.ISO_8859_1)) {
                if (!line.startsWith("JAVA_VERSION=")) continue;
                String version = line.substring("JAVA_VERSION=".length()).replace("\"", "").trim();
                String[] parts = version.split("[._+-]");
                int first = Integer.parseInt(parts[0]);
                return first == 1 && parts.length > 1 ? Integer.parseInt(parts[1]) : first;
            }
        } catch (IOException | RuntimeException ignored) {
            // Unreadable release file: the version is unknown.
        }
        return 0;
    }

    /** Finds the Java home inside an extracted archive, including macOS's Contents/Home layout. */
    static Path findHome(Path root, boolean requireJdk, int major) {
        List<Path> level = new ArrayList<Path>();
        if (Files.isDirectory(root)) level.add(root);
        for (int depth = 0; depth <= 4 && !level.isEmpty(); depth++) {
            List<Path> next = new ArrayList<Path>();
            for (Path candidate : level) {
                if (isHome(candidate, requireJdk, major)) return candidate;
                for (Path child : FileUtil.children(candidate)) if (Files.isDirectory(child)) next.add(child);
            }
            level = next;
        }
        return null;
    }
}
