package dev.vibe.launcher.install;

import dev.vibe.launcher.core.AppLog;
import dev.vibe.launcher.core.AppPaths;
import dev.vibe.launcher.core.FileUtil;
import dev.vibe.launcher.core.Http;
import dev.vibe.launcher.core.I18n;
import dev.vibe.launcher.core.Json;
import dev.vibe.launcher.core.Platform;
import dev.vibe.launcher.core.Progress;
import dev.vibe.launcher.core.Settings;
import java.io.IOException;
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
        return usable(settings.java8Home(), false) != null && usable(settings.jdk21Home(), true) != null;
    }

    public Runtimes ensure(Progress progress) throws IOException {
        Path java8 = usable(settings.java8Home(), false);
        if (java8 == null) {
            java8 = install(8, "jre", "temurin-8", false, progress.range(0, 0.35));
            settings.setJava8Home(java8.toString());
        }
        Path jdk21 = usable(settings.jdk21Home(), true);
        if (jdk21 == null) {
            jdk21 = install(21, "jdk", "temurin-21", true, progress.range(0.35, 1));
            settings.setJdk21Home(jdk21.toString());
        }
        return new Runtimes(java8, jdk21);
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
        Path existing = findHome(output, requireJdk);
        if (existing != null) return existing;

        String label = feature == 8 ? "Java 8" : "Java 21";
        progress.update(I18n.t("Looking up {0}", label), null, -1);
        // Minecraft 1.8.9's LWJGL 2 has no Apple Silicon natives; Java 8 runs under Rosetta there.
        String arch = Platform.os() == Platform.Os.MAC && feature == 8 ? "x64" : Platform.architecture();
        String query = "https://api.adoptium.net/v3/assets/latest/" + feature + "/hotspot?architecture=" + arch
                + "&image_type=" + imageType + "&os=" + Platform.adoptiumOs() + "&vendor=eclipse";
        List<Object> assets = Json.array(Json.parse(Http.getText(query, 4 * 1024 * 1024)));
        if (assets.isEmpty()) throw new IOException("Adoptium has no " + label + " build for " + Platform.adoptiumOs() + "/" + arch + ".");
        Map<String, Object> binary = Json.object(Json.object(assets.get(0)), "binary");
        Map<String, Object> pack = Json.object(binary, "package");
        String link = Json.string(pack, "link");
        String name = Json.string(pack, "name");
        String checksum = Json.string(pack, "checksum").toLowerCase(Locale.ROOT);
        long size = Json.number(pack, "size", 0);
        if (link.isEmpty() || !checksum.matches("[0-9a-f]{64}")) throw new IOException("Adoptium returned an incomplete " + label + " download.");

        Files.createDirectories(paths.runtimes());
        Path archive = paths.runtimes().resolve(name.isEmpty() ? folder + ".zip" : name.replaceAll("[^A-Za-z0-9._-]", "_"));
        Path temporary = paths.runtimes().resolve(folder + ".partial");
        try {
            String message = I18n.t("Downloading {0}", label);
            Http.download(link, archive, Math.max(size * 2, 600L * 1024L * 1024L), progress.range(0, 0.85).bytes(message));
            progress.update(I18n.t("Verifying {0}", label), null, 0.86);
            String actual = Http.sha256(archive);
            if (!actual.equals(checksum)) throw new IOException("The " + label + " download is damaged (checksum mismatch). Please try again.");
            FileUtil.deleteTree(temporary);
            Archives.extract(archive, temporary, I18n.t("Unpacking {0}", label), progress.range(0.87, 1));
            FileUtil.deleteTree(output);
            Files.move(temporary, output);
            Path home = findHome(output, requireJdk);
            if (home == null) throw new IOException("The " + label + " download does not contain a usable Java installation.");
            log.info("Installed " + label + " at " + home + ".");
            return home;
        } finally {
            Files.deleteIfExists(archive);
            FileUtil.deleteTreeQuietly(temporary);
        }
    }

    static Path usable(String configured, boolean requireJdk) {
        if (configured == null || configured.trim().isEmpty()) return null;
        try {
            Path home = Paths.get(configured.trim()).toAbsolutePath().normalize();
            return isHome(home, requireJdk) ? home : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Also requires the class library, so a half-deleted runtime is downloaded again. */
    private static boolean isHome(Path home, boolean requireJdk) {
        boolean library = Files.isRegularFile(home.resolve("lib").resolve("modules")) || Files.isRegularFile(home.resolve("lib").resolve("rt.jar"));
        return library && Files.isRegularFile(Platform.javaExecutable(home, false)) && (!requireJdk || Files.isRegularFile(Platform.javacExecutable(home)));
    }

    /** Finds the Java home inside an extracted archive, including macOS's Contents/Home layout. */
    static Path findHome(Path root, boolean requireJdk) {
        List<Path> level = new ArrayList<Path>();
        if (Files.isDirectory(root)) level.add(root);
        for (int depth = 0; depth <= 4 && !level.isEmpty(); depth++) {
            List<Path> next = new ArrayList<Path>();
            for (Path candidate : level) {
                if (isHome(candidate, requireJdk)) return candidate;
                for (Path child : FileUtil.children(candidate)) if (Files.isDirectory(child)) next.add(child);
            }
            level = next;
        }
        return null;
    }
}
