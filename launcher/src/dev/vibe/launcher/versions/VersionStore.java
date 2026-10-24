package dev.vibe.launcher.versions;

import dev.vibe.launcher.core.AppLog;
import dev.vibe.launcher.core.AppPaths;
import dev.vibe.launcher.core.ErrorCode;
import dev.vibe.launcher.core.FileUtil;
import dev.vibe.launcher.core.Http;
import dev.vibe.launcher.core.I18n;
import dev.vibe.launcher.core.LauncherException;
import dev.vibe.launcher.core.Progress;
import dev.vibe.launcher.core.Settings;
import dev.vibe.launcher.core.Text;
import dev.vibe.launcher.core.Version;
import dev.vibe.launcher.install.ChangelogSection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipFile;

/**
 * Downloaded Vibe versions on disk and the JAR that is active in the game profile.
 *
 * <p>Every release lives in {@code versions/<tag>} with its original GitHub JAR
 * name, a {@code release.properties} summary and the release notes. Activating a
 * version hard-links (or copies) its JAR into {@code mods/} and removes the other
 * managed Vibe JARs, so exactly one version is loaded.</p>
 */
public final class VersionStore {
    public static final String JAR_PREFIX = "Vibe-1.8.9-";
    private static final long DEFAULT_LIMIT = 512L * 1024L * 1024L;
    private static final String PROPERTIES = "release.properties";
    private static final String NOTES = "changelog.md";

    public static final class Installed {
        public final String tag, name, version, asset, sha256, url;
        public final long published, size, installedAt;
        public final boolean prerelease;
        public final Path directory, jar;

        Installed(String tag, String name, String version, String asset, String sha256, String url,
                long published, long size, long installedAt, boolean prerelease, Path directory, Path jar) {
            this.tag = tag;
            this.name = name;
            this.version = version;
            this.asset = asset;
            this.sha256 = sha256;
            this.url = url;
            this.published = published;
            this.size = size;
            this.installedAt = installedAt;
            this.prerelease = prerelease;
            this.directory = directory;
            this.jar = jar;
        }

        /** "v0.0.6" → "0.0.6". */
        public String displayVersion() { return version.isEmpty() ? tag : version; }
        public String title() { return Text.blank(name) ? tag : name; }
    }

    private final AppPaths paths;
    private final Settings settings;
    private final AppLog log;

    public VersionStore(AppPaths paths, Settings settings, AppLog log) {
        this.paths = paths;
        this.settings = settings;
        this.log = log;
    }

    // ---- installed versions ----------------------------------------------

    public List<Installed> listInstalled() {
        List<Installed> result = new ArrayList<Installed>();
        for (Path directory : FileUtil.children(paths.versions())) {
            if (!Files.isDirectory(directory)) continue;
            Installed installed = read(directory);
            if (installed != null) result.add(installed);
        }
        Collections.sort(result, new Comparator<Installed>() {
            @Override public int compare(Installed left, Installed right) {
                int order = Version.compare(right.version, left.version);
                return order != 0 ? order : left.tag.compareToIgnoreCase(right.tag);
            }
        });
        return result;
    }

    public Installed installed(String tag) {
        if (tag == null || tag.isEmpty()) return null;
        for (Installed installed : listInstalled()) if (installed.tag.equalsIgnoreCase(tag)) return installed;
        return null;
    }

    public Installed selected() { return installed(settings.selectedVibeVersion()); }

    private Installed read(Path directory) {
        Path jar = null;
        for (Path child : FileUtil.children(directory)) {
            String name = child.getFileName().toString();
            if (Files.isRegularFile(child) && name.startsWith(JAR_PREFIX) && name.endsWith(".jar")) jar = child;
        }
        if (jar == null) return null;
        Properties values = FileUtil.readProperties(directory.resolve(PROPERTIES));
        String tag = value(values, "tag", directory.getFileName().toString());
        String version = value(values, "version", tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag);
        return new Installed(tag, value(values, "name", ""), version,
                value(values, "asset", jar.getFileName().toString()), value(values, "sha256", ""),
                value(values, "url", ""), number(values, "published"), number(values, "size"),
                number(values, "installedAt"), "true".equals(values.getProperty("prerelease")), directory, jar);
    }

    // ---- download, install, select, remove ---------------------------------

    /** Downloads and verifies a release, then returns it; an intact copy is reused. */
    public Installed install(ReleaseCatalog.Release release, Progress progress) throws IOException {
        if (release == null) throw new LauncherException(ErrorCode.VIBE_DOWNLOAD, I18n.t("Vibe could not be downloaded. Check your internet connection and try again."));
        ReleaseCatalog.Asset asset = release.asset;
        if (asset == null || asset.url.isEmpty() || !isVibeJar(asset.name)) {
            throw new LauncherException(ErrorCode.RELEASE_ASSET_MISSING,
                    I18n.t("The release {0} has no downloadable JAR. Pick another version on the Versions page.", release.tag));
        }
        Path directory = directory(release.tag);
        Path target = directory.resolve(asset.name);
        String expected = asset.sha256;
        if (expected.isEmpty() && asset.sumUrl != null) expected = fetchSum(asset.sumUrl);
        if (isIntact(target, asset.size, expected)) {
            writeMeta(release, asset, target, expected);
            return read(directory);
        }
        Files.createDirectories(directory);
        long limit = asset.size > 0 ? asset.size + 1024L * 1024L : DEFAULT_LIMIT;
        String message = I18n.t("Downloading Vibe {0}", release.version());
        progress.update(message, asset.name, -1);
        try {
            Http.download(asset.url, target, limit, progress.bytes(message));
        } catch (IOException error) {
            Files.deleteIfExists(target);
            throw LauncherException.wrap(error, ErrorCode.VIBE_DOWNLOAD, I18n.t("Vibe {0} could not be downloaded.", release.version()));
        }
        long size = safeSize(target);
        if (asset.size > 0 && size != asset.size) {
            Files.deleteIfExists(target);
            throw damaged(release);
        }
        if (!expected.isEmpty() && !expected.equals(Http.sha256(target))) {
            Files.deleteIfExists(target);
            throw damaged(release);
        }
        if (!isVibeJar(target)) {
            Files.deleteIfExists(target);
            throw damaged(release);
        }
        writeMeta(release, asset, target, expected);
        log.info("Installed Vibe " + release.tag + " (" + asset.name + ", " + Text.bytes(size) + ").");
        return read(directory);
    }

    private void writeMeta(ReleaseCatalog.Release release, ReleaseCatalog.Asset asset, Path jar, String sha256) throws IOException {
        Path directory = jar.getParent();
        if (!Files.exists(directory.resolve(NOTES)) && !Text.blank(release.body)) {
            FileUtil.writeText(directory.resolve(NOTES), release.body);
        }
        Properties previous = FileUtil.readProperties(directory.resolve(PROPERTIES));
        Properties values = new Properties();
        values.setProperty("tag", release.tag);
        values.setProperty("name", release.name == null ? "" : release.name);
        values.setProperty("version", release.version());
        values.setProperty("asset", asset.name);
        values.setProperty("sha256", sha256 == null ? "" : sha256);
        values.setProperty("url", asset.url);
        values.setProperty("published", Long.toString(release.published));
        values.setProperty("size", Long.toString(safeSize(jar)));
        values.setProperty("prerelease", Boolean.toString(release.prerelease));
        long installedAt = number(previous, "installedAt");
        values.setProperty("installedAt", Long.toString(installedAt > 0 ? installedAt : System.currentTimeMillis()));
        FileUtil.writeProperties(directory.resolve(PROPERTIES), values, "Vibe " + release.tag);
    }

    private LauncherException damaged(ReleaseCatalog.Release release) {
        return new LauncherException(ErrorCode.RELEASE_CHECKSUM,
                I18n.t("Vibe {0} was damaged during the download and was discarded. Try again or pick another version.", release.tag));
    }

    /** Makes {@code tag} the version in {@code mods/} and remembers it in the settings. */
    public Installed select(String tag, Path modsDir) throws IOException {
        Installed installed = installed(tag);
        if (installed == null) throw new LauncherException(ErrorCode.SOURCE_DAMAGED, I18n.t("Vibe {0} is not installed.", tag));
        installLink(installed, modsDir);
        settings.setSelectedVibeVersion(installed.tag);
        return installed;
    }

    /** Ensures the selected version is the only Vibe JAR in {@code mods/}. */
    public Installed activate(String tag, Path modsDir) throws IOException {
        Installed installed = installed(tag);
        if (installed == null) return null;
        installLink(installed, modsDir);
        return installed;
    }

    private void installLink(Installed installed, Path modsDir) throws IOException {
        Files.createDirectories(modsDir);
        removeManagedJars(modsDir);
        Path destination = modsDir.resolve(installed.jar.getFileName());
        try {
            Files.createLink(destination, installed.jar);
        } catch (IOException | UnsupportedOperationException | SecurityException ignored) {
            // Different drives or file systems cannot hard-link; a copy works the same here.
            Files.copy(installed.jar, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Deletes every launcher-managed Vibe JAR (including disabled copies) from {@code mods/}. */
    public void clearActive(Path modsDir) throws IOException {
        removeManagedJars(modsDir);
    }

    /** Deletes every launcher-managed Vibe JAR (including disabled copies) from {@code mods/}. */
    private static void removeManagedJars(Path modsDir) throws IOException {
        for (Path child : FileUtil.children(modsDir)) {
            String name = child.getFileName().toString();
            if (!name.startsWith(JAR_PREFIX)) continue;
            if (!Files.isRegularFile(child)) continue;
            try {
                Files.delete(child);
            } catch (IOException error) {
                throw new LauncherException(ErrorCode.FILE_IN_USE,
                        I18n.t("The Vibe JAR in the mods folder is in use. Close Minecraft and try again."), error);
            }
        }
    }

    /** Deletes the downloaded version; the caller picks and activates a replacement. */
    public void remove(String tag) throws IOException {
        Installed installed = installed(tag);
        Path directory = installed == null ? directory(tag) : installed.directory;
        if (!Files.exists(directory)) return;
        try {
            FileUtil.deleteTree(directory);
        } catch (IOException error) {
            throw new LauncherException(ErrorCode.FILE_IN_USE,
                    I18n.t("The Vibe {0} files are in use. Close Minecraft and try again.", tag), error);
        }
        if (tag.equalsIgnoreCase(settings.selectedVibeVersion())) settings.setSelectedVibeVersion("");
        log.info("Removed Vibe " + tag + ".");
    }

    public Path directory(String tag) {
        String safe = tag == null ? "" : tag.replaceAll("[^A-Za-z0-9._-]", "_").replaceFirst("^\\.+", "_");
        if (safe.isEmpty()) safe = "_";
        return paths.versions().resolve(safe);
    }

    // ---- notes and leftovers ----------------------------------------------

    public List<ChangelogSection> notes(String tag) {
        Installed installed = installed(tag);
        if (installed == null) return Collections.emptyList();
        return readNotes(installed.directory.resolve(NOTES));
    }

    public List<ChangelogSection> selectedNotes() {
        Installed installed = selected();
        return installed == null ? Collections.<ChangelogSection>emptyList() : readNotes(installed.directory.resolve(NOTES));
    }

    public List<ChangelogSection> notes(ReleaseCatalog.Release release) {
        if (release == null) return Collections.emptyList();
        return ReleaseNotes.parse(release.body);
    }

    private static List<ChangelogSection> readNotes(Path file) {
        if (!Files.isRegularFile(file)) return Collections.emptyList();
        try {
            return ReleaseNotes.parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            return Collections.emptyList();
        }
    }

    /** Removes half-written downloads after an interrupted install. */
    public void cleanLeftovers() {
        for (Path version : FileUtil.children(paths.versions())) {
            if (!Files.isDirectory(version)) continue;
            for (Path child : FileUtil.children(version)) {
                if (child.getFileName().toString().endsWith(".part") && FileUtil.deleteTreeQuietly(child)) {
                    log.info("Removed an unfinished Vibe download: " + child.getFileName());
                }
            }
        }
    }

    public Path modsDirectory() { return paths.profile().resolve("mods"); }

    // ---- helpers ---------------------------------------------------------

    private static String fetchSum(String url) throws IOException {
        String text = Http.getText(url, 4096).trim();
        for (String piece : text.split("\\s+")) {
            String lower = piece.toLowerCase(java.util.Locale.ROOT);
            if (lower.matches("[0-9a-f]{64}")) return lower;
        }
        return "";
    }

    private static boolean isIntact(Path jar, long size, String sha256) {
        if (!Files.isRegularFile(jar)) return false;
        if (size > 0 && safeSize(jar) != size) return false;
        if (!sha256.isEmpty()) {
            try {
                if (!sha256.equals(Http.sha256(jar))) return false;
            } catch (IOException ignored) {
                return false;
            }
        }
        return isVibeJar(jar);
    }

    /** A real mod JAR opens and carries {@code mcmod.info}; error pages and HTML do not. */
    static boolean isVibeJar(Path jar) {
        if (!Files.isRegularFile(jar)) return false;
        try {
            ZipFile zip = new ZipFile(jar.toFile());
            try {
                return zip.getEntry("mcmod.info") != null;
            } finally {
                zip.close();
            }
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isVibeJar(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith(JAR_PREFIX.toLowerCase(java.util.Locale.ROOT)) && lower.endsWith(".jar");
    }

    private static long safeSize(Path file) {
        try { return Files.size(file); } catch (IOException ignored) { return 0; }
    }

    private static String value(Properties values, String key, String fallback) {
        String value = values.getProperty(key);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static long number(Properties values, String key) {
        try { return Long.parseLong(value(values, key, "0")); } catch (NumberFormatException ignored) { return 0; }
    }
}
