package dev.vibe.launcher.minecraft;

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
import dev.vibe.launcher.core.Text;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Minecraft 1.8.9, Forge and OptiFine as prepared by the launcher.
 *
 * <p>The runtime is downloaded once from Mojang's libraries and unpacked
 * into {@code minecraft/}: the client JAR, every library, the asset index and its
 * objects, the LWJGL natives, Forge's launch profile and OptiFine. A stamp file
 * records what was installed, so later launches start without the network.</p>
 */
public final class GameRuntime {
    public static final String MC_VERSION = "1.8.9";
    public static final String FORGE_COORD = "1.8.9-11.15.1.2318-1.8.9";
    public static final String FORGE_BASE = "https://maven.minecraftforge.net/net/minecraftforge/forge/" + FORGE_COORD + "/";
    public static final String OPTIFINE_NAME = "preview_OptiFine_1.8.9_HD_U_M6_pre2.jar";
    // Pinned SHA-1 checksums: Forge and OptiFine publish none that the launcher could trust.
    static final String FORGE_INSTALLER_SHA1 = "ec0293ff0776b8831f2ed90511bab76e635dda0c";
    static final String FORGE_UNIVERSAL_SHA1 = "beda619c465af293e63952dd573c137c17c0a4cd";
    static final String OPTIFINE_SHA1 = "0bb792be16ec78d8b62586cc4856950befb3391b";
    private static final String VERSION_MANIFEST = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String MOJANG_MAVEN = "https://libraries.minecraft.net/";
    private static final String FORGE_MAVEN = "https://maven.minecraftforge.net/";
    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";
    private static final long LIBRARY_LIMIT = 256L * 1024L * 1024L;

    /** Everything {@link LaunchCommand} needs, read from an installed runtime. */
    public static final class Prepared {
        public final Path gameDir, minecraft, clientJar, forgeJar, optifineJar, natives, assets;
        public final Path assetIndex;
        public final String assetIndexName, versionName;
        public final List<Path> classpath;
        public final List<String> gameArguments;

        Prepared(Path gameDir, Path minecraft, Path clientJar, Path forgeJar, Path optifineJar, Path natives,
                Path assets, Path assetIndex, String assetIndexName, String versionName,
                List<Path> classpath, List<String> gameArguments) {
            this.gameDir = gameDir;
            this.minecraft = minecraft;
            this.clientJar = clientJar;
            this.forgeJar = forgeJar;
            this.optifineJar = optifineJar;
            this.natives = natives;
            this.assets = assets;
            this.assetIndex = assetIndex;
            this.assetIndexName = assetIndexName;
            this.versionName = versionName;
            this.classpath = classpath;
            this.gameArguments = gameArguments;
        }
    }

    private final AppPaths paths;
    private final AppLog log;

    public GameRuntime(AppPaths paths, AppLog log) {
        this.paths = paths;
        this.log = log;
    }

    private Path root() { return paths.minecraft(); }
    private Path versionJson() { return root().resolve("versions").resolve(MC_VERSION + ".json"); }
    private Path clientJar() { return root().resolve("client.jar"); }
    private Path forgeDir() { return root().resolve("forge"); }
    private Path installProfile() { return forgeDir().resolve("install_profile.json"); }
    private Path forgeJar() { return forgeDir().resolve("forge-" + FORGE_COORD + "-universal.jar"); }
    private Path optifineDir() { return root().resolve("optifine"); }
    private Path optifineJar() { return optifineDir().resolve(OPTIFINE_NAME); }
    private Path nativesDir() { return root().resolve("natives").resolve(MavenLibrary.osName() + "-" + MavenLibrary.archName()); }
    private Path stamp() { return root().resolve(".stamp"); }

    /** Whether a complete runtime is on disk, without touching the network. */
    public boolean isReady() {
        try {
            if (!Files.isRegularFile(clientJar()) || !Files.isRegularFile(versionJson())) return false;
            if (!Files.isRegularFile(installProfile()) || !isChecked(forgeJar(), 0, FORGE_UNIVERSAL_SHA1)) return false;
            if (!isOptiFine(optifineJar()) || !Natives.hasFiles(nativesDir())) return false;
            Map<String, Object> stamp = stampValues();
            if (!MC_VERSION.equals(Json.string(stamp, "minecraft"))) return false;
            if (!FORGE_COORD.equals(Json.string(stamp, "forge"))) return false;
            if (!OPTIFINE_NAME.equals(Json.string(stamp, "optifine"))) return false;
            if (!MavenLibrary.osName().equals(Json.string(stamp, "os"))) return false;
            if (!MavenLibrary.archName().equals(Json.string(stamp, "arch"))) return false;
            String assetIndex = Json.string(stamp, "assetIndex");
            return !assetIndex.isEmpty() && Files.isRegularFile(root().resolve("assets").resolve("indexes").resolve(assetIndex + ".json"));
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    /**
     * Downloads and unpacks whatever the runtime is missing. {@code force} ignores
     * the stamp but still reuses files that are already intact.
     */
    public void ensure(Progress progress, boolean force) throws IOException {
        if (!force && isReady()) {
            progress.update(I18n.t("Minecraft is ready"), null, 1);
            return;
        }
        Files.createDirectories(root());
        progress.update(I18n.t("Checking Minecraft files"), null, 0.01);
        Map<String, Object> entry = versionEntry();
        Map<String, Object> version = fetchJson(Json.string(entry, "url"), versionJson(),
                ErrorCode.MINECRAFT_DOWNLOAD, 4 * 1024 * 1024);
        String assetIndexName = Json.string(Json.object(version, "assetIndex"), "id");
        if (assetIndexName.isEmpty()) assetIndexName = "1.8";

        Path client = clientJar();
        Map<String, Object> clientDownload = Json.object(Json.object(version, "downloads"), "client");
        downloadChecked(client, Json.string(clientDownload, "url"), Json.number(clientDownload, "size", 0),
                Json.string(clientDownload, "sha1"), ErrorCode.MINECRAFT_DOWNLOAD,
                I18n.t("Downloading Minecraft {0}", MC_VERSION), progress.range(0.02, 0.12));

        Map<String, Object> forge = installForge(progress.range(0.72, 0.86));
        List<Path> libraries = new ArrayList<Path>();
        libraries.addAll(downloadLibraries(version, forge, progress.range(0.12, 0.34)));
        unpackNatives(version, progress.range(0.34, 0.38));

        Map<String, Object> assetIndex = fetchJson(Json.string(Json.object(version, "assetIndex"), "url"),
                root().resolve("assets").resolve("indexes").resolve(assetIndexName + ".json"),
                ErrorCode.ASSET_DOWNLOAD, 64 * 1024 * 1024);
        downloadAssets(assetIndex, progress.range(0.38, 0.72));
        downloadOptiFine(progress.range(0.86, 1));

        writeStamp(assetIndexName);
        log.info("Minecraft " + MC_VERSION + " runtime ready (" + libraries.size() + " libraries).");
        progress.update(I18n.t("Minecraft is ready"), null, 1);
    }

    /** Deletes and downloads the runtime again; worlds and mods are untouched. */
    public void repair(Progress progress) throws IOException {
        log.info("Repairing the Minecraft runtime.");
        FileUtil.deleteTree(root());
        ensure(progress, true);
    }

    // ---- versions, libraries, natives, assets -----------------------------

    private Map<String, Object> versionEntry() throws IOException {
        Map<String, Object> manifest = fetchJson(VERSION_MANIFEST, root().resolve("version_manifest.json"),
                ErrorCode.MINECRAFT_DOWNLOAD, 8 * 1024 * 1024);
        for (Object item : Json.array(manifest, "versions")) {
            Map<String, Object> version = Json.object(item);
            if (MC_VERSION.equals(Json.string(version, "id"))) return version;
        }
        throw new LauncherException(ErrorCode.MINECRAFT_DOWNLOAD, I18n.t("Mojang no longer offers Minecraft {0}.", MC_VERSION));
    }

    /** Vanilla libraries plus Forge's, with Forge overriding the same artifact key. */
    private List<Path> downloadLibraries(Map<String, Object> version, Map<String, Object> forge, Progress progress) throws IOException {
        Map<String, Library> merged = new LinkedHashMap<String, Library>();
        collectLibraries(Json.array(version, "libraries"), false, merged);
        collectLibraries(Json.array(Json.object(forge, "versionInfo"), "libraries"), true, merged);
        // Forge's universal JAR is downloaded separately and must not appear twice.
        merged.remove("net.minecraftforge:forge");
        merged.remove("net.minecraftforge:forge:universal");
        merged.remove("forge");
        List<Library> libraries = new ArrayList<Library>(merged.values());
        List<Path> files = new ArrayList<Path>();
        int index = 0;
        for (Library library : libraries) {
            index++;
            Path target = root().resolve("libraries").resolve(library.path);
            progress.update(I18n.t("Downloading Minecraft libraries"), index + " / " + libraries.size() + "  ·  " + library.fileName,
                    (double) index / Math.max(1, libraries.size()));
            downloadLibrary(library, target);
            files.add(target);
        }
        return files;
    }

    private void collectLibraries(List<Object> items, boolean forge, Map<String, Library> into) {
        for (Object item : items) {
            Map<String, Object> entry = Json.object(item);
            if (!MavenLibrary.allowed(Json.array(entry, "rules"))) continue;
            String coordinate = Json.string(entry, "name");
            if (coordinate.isEmpty()) continue;
            String key = MavenLibrary.key(coordinate);
            if (key.isEmpty()) continue;
            if (classifierOnly(entry)) continue;
            Map<String, Object> artifact = Json.object(Json.object(entry, "downloads"), "artifact");
            Library library = new Library();
            library.coordinate = coordinate;
            library.fileName = MavenLibrary.fileName(coordinate);
            String path = Json.string(artifact, "path");
            library.path = path.isEmpty() ? MavenLibrary.path(coordinate) : path;
            library.sha1 = Json.string(artifact, "sha1");
            library.size = Json.number(artifact, "size", 0);
            library.baseUrl = Json.string(entry, "url");
            if (library.baseUrl.isEmpty()) library.baseUrl = Json.string(artifact, "url");
            if (forge) into.put(key, library);
            else if (!into.containsKey(key)) into.put(key, library);
        }
    }

    /**
     * True for libraries that only ship natives classifiers, for example
     * {@code net.java.jinput:jinput-platform:2.0.5}. They have no main JAR on
     * any Maven repository; {@link #unpackNatives} downloads the classifiers.
     */
    static boolean classifierOnly(Map<String, Object> entry) {
        Map<String, Object> downloads = Json.object(entry, "downloads");
        return !Json.object(entry, "natives").isEmpty()
                && Json.object(downloads, "artifact").isEmpty()
                && !Json.object(downloads, "classifiers").isEmpty();
    }

    private void unpackNatives(Map<String, Object> version, Progress progress) throws IOException {
        Path directory = nativesDir();
        Files.createDirectories(directory);
        int total = 0;
        for (Object item : Json.array(version, "libraries")) {
            Map<String, Object> entry = Json.object(item);
            Map<String, Object> natives = Json.object(entry, "natives");
            String classifier = Json.string(natives, MavenLibrary.osName());
            if (classifier.isEmpty()) continue;
            if (!MavenLibrary.allowed(Json.array(entry, "rules"))) continue;
            classifier = nativeClassifier(classifier);
            String coordinate = Json.string(entry, "name") + ":" + classifier;
            Map<String, Object> download = Json.object(Json.object(entry, "downloads"), "classifiers");
            download = Json.object(download, classifier);
            Path target = root().resolve("libraries").resolve(MavenLibrary.path(coordinate));
            Library library = new Library();
            library.coordinate = coordinate;
            library.fileName = MavenLibrary.fileName(coordinate);
            library.path = MavenLibrary.path(coordinate);
            library.sha1 = Json.string(download, "sha1");
            library.size = Json.number(download, "size", 0);
            String url = Json.string(download, "url");
            library.baseUrl = url;
            downloadLibrary(library, target);
            List<String> exclude = new ArrayList<String>();
            for (Object pattern : Json.array(Json.object(entry, "extract"), "exclude")) {
                if (pattern instanceof String) exclude.add((String) pattern);
            }
            try {
                Natives.extract(target, directory, exclude);
            } catch (IOException error) {
                throw LauncherException.wrap(error, ErrorCode.RUNTIME_INCOMPLETE, I18n.t("The game libraries could not be unpacked."));
            }
            total++;
        }
        if (total == 0) throw new LauncherException(ErrorCode.RUNTIME_INCOMPLETE, I18n.t("Minecraft has no game libraries for this system."));
    }

    private void downloadAssets(Map<String, Object> index, Progress progress) throws IOException {
        Map<String, Object> objects = Json.object(index, "objects");
        if (objects.isEmpty()) throw new LauncherException(ErrorCode.ASSET_DOWNLOAD, I18n.t("Minecraft's asset list is empty."));
        final Map<String, Long> assets = uniqueAssets(objects);
        final int total = assets.size();
        final AtomicInteger done = new AtomicInteger();
        final Path objectsDir = root().resolve("assets").resolve("objects");
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<?>> tasks = new ArrayList<Future<?>>();
        try {
            for (final Map.Entry<String, Long> asset : assets.entrySet()) {
                tasks.add(pool.submit(new Runnable() {
                    @Override public void run() {
                        String hash = asset.getKey();
                        long size = asset.getValue();
                        Path target = objectsDir.resolve(hash.substring(0, 2)).resolve(hash);
                        try {
                            downloadChecked(target, "https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash,
                                    size, hash, ErrorCode.ASSET_DOWNLOAD, null, null);
                        } catch (IOException error) {
                            throw new AssetFailure(error);
                        }
                        int count = done.incrementAndGet();
                        progress.update(I18n.t("Downloading Minecraft assets"), count + " / " + total, (double) count / total);
                    }
                }));
            }
            for (Future<?> task : tasks) {
                try {
                    task.get();
                } catch (ExecutionException error) {
                    Throwable cause = error.getCause();
                    if (cause instanceof AssetFailure) {
                        throw LauncherException.wrap((IOException) cause.getCause(), ErrorCode.ASSET_DOWNLOAD,
                                I18n.t("Minecraft's game files could not be downloaded."));
                    }
                    throw LauncherException.wrap((IOException) cause, ErrorCode.ASSET_DOWNLOAD,
                            I18n.t("Minecraft's game files could not be downloaded."));
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new java.io.InterruptedIOException("Cancelled.");
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Asset objects are keyed by name, but several names can point at the same file
     * (icons, dig/step sounds). Download each hash once: two threads sharing a
     * {@code .part} file would collide on Windows and fail with VL-204.
     */
    static Map<String, Long> uniqueAssets(Map<String, Object> objects) {
        Map<String, Long> unique = new LinkedHashMap<String, Long>();
        for (Object value : objects.values()) {
            Map<String, Object> file = Json.object(value);
            String hash = Json.string(file, "hash");
            if (!hash.matches("[0-9a-f]{40}") || unique.containsKey(hash)) continue;
            unique.put(hash, Json.number(file, "size", 0));
        }
        return unique;
    }

    private static final class AssetFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        AssetFailure(IOException cause) { super(cause); }
    }

    // ---- Forge and OptiFine -----------------------------------------------

    private Map<String, Object> installForge(Progress progress) throws IOException {
        Files.createDirectories(forgeDir());
        pass(progress, 0);
        Path installer = forgeDir().resolve("forge-" + FORGE_COORD + "-installer.jar");
        if (!Files.isRegularFile(installProfile())) {
            String message = I18n.t("Downloading Forge");
            progress.update(message, null, -1);
            try {
                Http.download(FORGE_BASE + "forge-" + FORGE_COORD + "-installer.jar", installer, 64L * 1024L * 1024L, null);
            } catch (IOException error) {
                throw LauncherException.wrap(error, ErrorCode.FORGE_DOWNLOAD, I18n.t("Forge could not be downloaded."));
            }
            if (!isChecked(installer, 0, FORGE_INSTALLER_SHA1)) {
                Files.deleteIfExists(installer);
                throw new LauncherException(ErrorCode.FORGE_DOWNLOAD, I18n.t("The Forge download is damaged."));
            }
            String profile = readEntry(installer, "install_profile.json");
            if (profile.isEmpty()) {
                Files.deleteIfExists(installer);
                throw new LauncherException(ErrorCode.FORGE_DOWNLOAD, I18n.t("The Forge download is incomplete."));
            }
            FileUtil.writeText(installProfile(), profile);
            Files.deleteIfExists(installer);
        }
        Map<String, Object> profile = readJson(installProfile(), ErrorCode.FORGE_DOWNLOAD);
        Map<String, Object> versionInfo = Json.object(profile, "versionInfo");
        if (versionInfo.isEmpty()) versionInfo = profile;
        if (Json.string(versionInfo, "minecraftArguments").isEmpty() || Json.array(versionInfo, "libraries").isEmpty()) {
            throw new LauncherException(ErrorCode.FORGE_DOWNLOAD, I18n.t("The Forge launch profile is incomplete."));
        }
        downloadChecked(forgeJar(), FORGE_BASE + "forge-" + FORGE_COORD + "-universal.jar", 0, FORGE_UNIVERSAL_SHA1,
                ErrorCode.FORGE_DOWNLOAD, I18n.t("Downloading Forge"), progress);
        pass(progress, 1);
        return profile;
    }

    private void downloadOptiFine(Progress progress) throws IOException {
        Path target = optifineJar();
        if (isOptiFine(target)) return;
        Files.createDirectories(optifineDir());
        Files.deleteIfExists(target);
        String message = I18n.t("Downloading OptiFine");
        progress.update(message, null, -1);
        Path temporary = optifineDir().resolve(OPTIFINE_NAME + ".part");
        try {
            String page = Http.getText("https://optifine.net/adloadx?f=" + OPTIFINE_NAME, 512 * 1024);
            java.util.regex.Matcher link = java.util.regex.Pattern.compile("href=['\"](downloadx\\?[^'\"]+)['\"]").matcher(page);
            if (!link.find()) throw new IOException("OptiFine did not offer a download link.");
            String url = "https://optifine.net/" + link.group(1).replace("&amp;", "&");
            Http.download(url, temporary, 32L * 1024L * 1024L, progress.bytes(message));
            if (!isOptiFine(temporary)) throw new IOException("The OptiFine download is not the expected OptiFine JAR.");
            Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.info("Downloaded OptiFine " + OPTIFINE_NAME + ".");
        } catch (IOException error) {
            throw LauncherException.wrap(error, ErrorCode.OPTIFINE_DOWNLOAD,
                    I18n.t("OptiFine could not be downloaded. Try again later or put {0} into {1}.", OPTIFINE_NAME, optifineDir()));
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static boolean isOptiFine(Path jar) {
        if (!isChecked(jar, 0, OPTIFINE_SHA1)) return false;
        try {
            ZipFile zip = new ZipFile(jar.toFile());
            try {
                return zip.getEntry("optifine/OptiFineTweaker.class") != null;
            } finally {
                zip.close();
            }
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    // ---- downloads and files ----------------------------------------------

    /** One library with everything needed to locate and verify it. */
    private static final class Library {
        String coordinate, fileName, path, sha1, baseUrl;
        long size;
    }

    private void downloadLibrary(Library library, Path target) throws IOException {
        if (isChecked(target, library.size, library.sha1)) return;
        List<String> urls = new ArrayList<String>();
        if (library.baseUrl != null && !library.baseUrl.isEmpty()) {
            String base = https(library.baseUrl);
            if (base.endsWith(".jar")) urls.add(base);
            else urls.add(base.endsWith("/") ? base + library.path : base + "/" + library.path);
        }
        urls.add(MOJANG_MAVEN + library.path);
        urls.add(FORGE_MAVEN + library.path);
        urls.add(MAVEN_CENTRAL + library.path);
        IOException last = null;
        for (String url : urls) {
            try {
                downloadChecked(target, url, library.size, library.sha1, ErrorCode.MINECRAFT_DOWNLOAD, null, null);
                return;
            } catch (IOException error) {
                last = error;
            }
        }
        throw LauncherException.wrap(last, ErrorCode.MINECRAFT_DOWNLOAD,
                I18n.t("The Minecraft library {0} could not be downloaded.", library.fileName));
    }

    private void downloadChecked(Path target, String url, long size, String sha1, ErrorCode fallback,
            String message, Progress progress) throws IOException {
        if (isChecked(target, size, sha1)) return;
        if (url == null || url.isEmpty()) throw new LauncherException(fallback, I18n.t("A Minecraft file has no download address."));
        Files.createDirectories(target.toAbsolutePath().getParent());
        Files.deleteIfExists(target);
        if (message != null && progress != null) progress.update(message, null, -1);
        try {
            Http.download(https(url), target, size > 0 ? size + 1024L * 1024L : LIBRARY_LIMIT,
                    message == null || progress == null ? null : progress.bytes(message));
        } catch (IOException error) {
            throw LauncherException.wrap(error, fallback, I18n.t("A Minecraft file could not be downloaded."));
        }
        if (!isChecked(target, size, sha1)) {
            Files.deleteIfExists(target);
            throw new LauncherException(fallback, I18n.t("A downloaded Minecraft file is damaged."));
        }
    }

    private static boolean isChecked(Path file, long size, String sha1) {
        if (!Files.isRegularFile(file)) return false;
        try {
            if (size > 0 && Files.size(file) != size) return false;
            return sha1 == null || sha1.isEmpty() || sha1.equalsIgnoreCase(Http.sha1(file));
        } catch (IOException ignored) {
            return false;
        }
    }

    private Map<String, Object> fetchJson(String url, Path cache, ErrorCode fallback, int limit) throws IOException {
        if (Files.isRegularFile(cache)) {
            try {
                return Json.object(Json.parse(new String(Files.readAllBytes(cache), StandardCharsets.UTF_8)));
            } catch (IOException ignored) {
                // Damaged cache: download it again.
            }
        }
        try {
            String text = Http.getText(https(url), limit);
            Map<String, Object> result = Json.object(Json.parse(text));
            if (result.isEmpty()) throw new IOException("The download is not a JSON object.");
            FileUtil.writeText(cache, text);
            return result;
        } catch (IOException error) {
            throw LauncherException.wrap(error, fallback, I18n.t("A Minecraft file list could not be downloaded."));
        }
    }

    private static Map<String, Object> readJson(Path file, ErrorCode fallback) throws IOException {
        try {
            return Json.object(Json.parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)));
        } catch (IOException error) {
            throw LauncherException.wrap(error, fallback, I18n.t("The Forge launch profile is unreadable."));
        }
    }

    private static String readEntry(Path jar, String name) throws IOException {
        if (!Files.isRegularFile(jar)) return "";
        try {
            ZipFile zip = new ZipFile(jar.toFile());
            try {
                ZipEntry entry = zip.getEntry(name);
                if (entry == null) return "";
                InputStream input = zip.getInputStream(entry);
                try {
                    java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[16384];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                        if (output.size() > 4 * 1024 * 1024) throw new IOException("Forge's profile is unexpectedly large.");
                    }
                    return new String(output.toByteArray(), StandardCharsets.UTF_8);
                } finally {
                    input.close();
                }
            } finally {
                zip.close();
            }
        } catch (IOException error) {
            throw LauncherException.wrap(error, ErrorCode.FORGE_DOWNLOAD, I18n.t("The Forge download is incomplete."));
        }
    }

    /** HTTP addresses in old Forge metadata are upgraded; {@link Http} only allows HTTPS. */
    private static String https(String url) {
        String value = url == null ? "" : url.trim();
        if (value.startsWith("http://")) value = "https://" + value.substring(7);
        return value.replace("https://files.minecraftforge.net/maven/", FORGE_MAVEN);
    }

    private static void pass(Progress progress, double fraction) { progress.update(I18n.t("Downloading Forge"), null, fraction); }

    // ---- stamp and launch -------------------------------------------------

    private Map<String, Object> stampValues() throws IOException {
        if (!Files.isRegularFile(stamp())) return Collections.emptyMap();
        return Json.object(Json.parse(new String(Files.readAllBytes(stamp()), StandardCharsets.UTF_8)));
    }

    private void writeStamp(String assetIndexName) throws IOException {
        StringBuilder json = new StringBuilder("{\n");
        json.append("  \"minecraft\": \"").append(MC_VERSION).append("\",\n");
        json.append("  \"forge\": \"").append(FORGE_COORD).append("\",\n");
        json.append("  \"optifine\": \"").append(OPTIFINE_NAME).append("\",\n");
        json.append("  \"assetIndex\": \"").append(assetIndexName).append("\",\n");
        json.append("  \"os\": \"").append(MavenLibrary.osName()).append("\",\n");
        json.append("  \"arch\": \"").append(MavenLibrary.archName()).append("\",\n");
        json.append("  \"installed\": ").append(System.currentTimeMillis()).append("\n}");
        FileUtil.writeText(stamp(), json.toString());
    }

    /** Reads the installed runtime for starting the game; fails when parts are missing. */
    public Prepared prepared() throws IOException {
        if (!isReady()) throw new LauncherException(ErrorCode.RUNTIME_INCOMPLETE,
                I18n.t("Minecraft's files are incomplete. Use Settings → Troubleshooting → Repair game files."));
        Map<String, Object> version = readJson(versionJson(), ErrorCode.RUNTIME_INCOMPLETE);
        Map<String, Object> profile = readJson(installProfile(), ErrorCode.RUNTIME_INCOMPLETE);
        Map<String, Object> versionInfo = Json.object(profile, "versionInfo");
        if (versionInfo.isEmpty()) versionInfo = profile;
        String assetIndexName = Json.string(Json.object(version, "assetIndex"), "id");
        if (assetIndexName.isEmpty()) assetIndexName = Json.string(stampValues(), "assetIndex");
        List<Path> classpath = new ArrayList<Path>();
        for (Path file : jarsUnder(root().resolve("libraries"))) classpath.add(file);
        classpath.add(clientJar());
        classpath.add(forgeJar());
        classpath.add(optifineJar());
        List<String> arguments = new ArrayList<String>();
        for (Object item : Json.array(versionInfo, "minecraftArguments")) {
            if (item instanceof String) arguments.add((String) item);
        }
        if (arguments.isEmpty()) {
            // install_profile stores the whole argument line as one string.
            for (String piece : Json.string(versionInfo, "minecraftArguments").trim().split("\\s+")) {
                if (!piece.isEmpty()) arguments.add(piece);
            }
        }
        String versionName = Json.string(versionInfo, "id");
        if (versionName.isEmpty()) versionName = MC_VERSION + "-forge" + FORGE_COORD;
        return new Prepared(paths.profile(), root(), clientJar(), forgeJar(), optifineJar(), nativesDir(),
                root().resolve("assets"), root().resolve("assets").resolve("indexes").resolve(assetIndexName + ".json"),
                assetIndexName, versionName, classpath, arguments);
    }

    /** Replaces the legacy {@code ${arch}} placeholder in native classifiers with 32 or 64. */
    static String nativeClassifier(String classifier) {
        return classifier.replace("${arch}", "x86".equals(MavenLibrary.archName()) ? "32" : "64");
    }

    private static List<Path> jarsUnder(Path directory) {
        List<Path> result = new ArrayList<Path>();
        collectJars(directory, result, 0);
        Collections.sort(result);
        return result;
    }

    private static void collectJars(Path directory, List<Path> into, int depth) {
        if (depth > 8 || !Files.isDirectory(directory)) return;
        for (Path child : FileUtil.children(directory)) {
            String name = child.getFileName().toString().toLowerCase(Locale.ROOT);
            if (Files.isDirectory(child)) collectJars(child, into, depth + 1);
            else if (name.endsWith(".jar") && Files.isRegularFile(child)) into.add(child);
        }
    }

    /** Builds the Java command that starts Forge with Vibe in {@code mods/}. */
    public LaunchCommand.Launch launch(Path javaHome, int memoryMb, String name, String uuid, Path readyFile) throws IOException {
        Prepared prepared = prepared();
        String player = Text.blank(name) ? "Player" : name.trim();
        String playerUuid = Text.blank(uuid) ? offlineUuid(player) : uuid.trim();
        Path java = Platform.javaExecutable(javaHome, false);
        if (!Files.isRegularFile(java)) {
            throw new LauncherException(ErrorCode.JAVA_BROKEN, I18n.t("Java 8 is missing at {0}.", java));
        }
        String accessToken = "0";
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("auth_player_name", player);
        values.put("version_name", prepared.versionName);
        values.put("game_directory", prepared.gameDir.toString());
        values.put("assets_root", prepared.assets.toString());
        values.put("assets_index_name", prepared.assetIndexName);
        values.put("auth_uuid", playerUuid.replace("-", ""));
        values.put("auth_access_token", accessToken);
        values.put("user_properties", "{}");
        values.put("user_type", "legacy");
        values.put("version_type", "release");
        return LaunchCommand.build(java, prepared, values, memoryMb, prepared.gameArguments, readyFile);
    }

    /** Deterministic offline UUID, as Minecraft itself derives it from the name. */
    static String offlineUuid(String name) {
        return java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
