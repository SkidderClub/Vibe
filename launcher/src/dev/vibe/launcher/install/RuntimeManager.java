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
 * Private Eclipse Temurin runtimes: Java 8 runs Minecraft 1.8.9, JDK 21 runs
 * Gradle. Downloads come from the Adoptium API and are verified against the
 * SHA-256 checksum it publishes before they are unpacked.
 */
public final class RuntimeManager {
    public static final class Runtimes {
        public final Path java8, jdk21;
        Runtimes(Path java8, Path jdk21) { this.java8 = java8; this.jdk21 = jdk21; }
    }

    private final AppPaths paths;
    private final Settings settings;
    private final AppLog log;

    public RuntimeManager(AppPaths paths, Settings settings, AppLog log) {
        this.paths = paths;
        this.settings = settings;
        this.log = log;
    }

    /** Whether both runtimes are present, without touching the network. */
    public boolean isReady() {
        return usable(settings.java8Home(), false, 8) != null && usable(settings.jdk21Home(), true, 21) != null;
    }

    public Runtimes ensure(Progress progress) throws IOException {
        Path java8 = usable(settings.java8Home(), false, 8);
        if (java8 == null) {
            warnIfWrongVersion(settings.java8Home(), 8);
            java8 = install(8, "jre", "temurin-8", false, progress.range(0, 0.35));
            settings.setJava8Home(java8.toString());
        }
        Path jdk21 = usable(settings.jdk21Home(), true, 21);
        if (jdk21 == null) {
            warnIfWrongVersion(settings.jdk21Home(), 21);
            jdk21 = install(21, "jdk", "temurin-21", true, progress.range(0.35, 1));
            settings.setJdk21Home(jdk21.toString());
        }
        return new Runtimes(java8, jdk21);
    }

    /** A runtime configured by hand that has the wrong version is replaced by the launcher's own. */
    private void warnIfWrongVersion(String configured, int expected) {
        if (configured == null || configured.trim().isEmpty()) return;
        try {
            int actual = majorVersion(Paths.get(configured.trim()));
            if (actual > 0 && actual != expected) {
                log.warn(ErrorCode.WRONG_JAVA.id() + " The configured Java " + expected + " at " + configured + " is Java " + actual
                        + "; the launcher installs its own Java " + expected + " instead.");
            }
        } catch (Exception ignored) {
            // Not a valid path: install() replaces it anyway.
        }
    }

    /** Deletes both private runtimes and downloads them again. */
    public Runtimes reinstall(Progress progress) throws IOException {
        settings.setJava8Home("");
        settings.setJdk21Home("");
        FileUtil.deleteTree(paths.runtimes().resolve("temurin-8"));
        FileUtil.deleteTree(paths.runtimes().resolve("temurin-21"));
        return ensure(progress);
    }

    private Path install(int feature, String imageType, String folder, boolean requireJdk, Progress progress) throws IOException {
        Path output = paths.runtimes().resolve(folder);
        Path existing = findHome(output, requireJdk, feature);
        if (existing != null) return existing;

        String label = feature == 8 ? "Java 8" : "Java 21";
        progress.update(I18n.t("Looking up {0}", label), null, -1);
        // Minecraft 1.8.9's LWJGL 2 has no Apple Silicon natives; Java 8 runs under Rosetta there.
        String arch = Platform.os() == Platform.Os.MAC && feature == 8 ? "x64" : Platform.architecture();
        String query = "https://api.adoptium.net/v3/assets/latest/" + feature + "/hotspot?architecture=" + arch
                + "&image_type=" + imageType + "&os=" + Platform.adoptiumOs() + "&vendor=eclipse";
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
        Path archive = paths.runtimes().resolve(name.isEmpty() ? folder + ".zip" : name.replaceAll("[^A-Za-z0-9._-]", "_"));
        Path temporary = paths.runtimes().resolve(folder + ".partial");
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
            Path home = findHome(output, requireJdk, feature);
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

    /**
     * Also requires the class library, so a half-deleted runtime is downloaded again, and
     * the expected major version: Gradle refuses a Java 17 handed over as Java 8.
     */
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
