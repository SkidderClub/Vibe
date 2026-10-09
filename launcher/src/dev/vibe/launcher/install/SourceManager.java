package dev.vibe.launcher.install;

import dev.vibe.launcher.VibeLauncher;
import dev.vibe.launcher.core.AppLog;
import dev.vibe.launcher.core.AppPaths;
import dev.vibe.launcher.core.ErrorCode;
import dev.vibe.launcher.core.FileUtil;
import dev.vibe.launcher.core.Http;
import dev.vibe.launcher.core.I18n;
import dev.vibe.launcher.core.Json;
import dev.vibe.launcher.core.LauncherException;
import dev.vibe.launcher.core.Progress;
import dev.vibe.launcher.core.Settings;
import dev.vibe.launcher.core.Text;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Keeps the private Vibe checkout in sync with {@code SkidderClub/Vibe}.
 *
 * <p>Small updates are applied as deltas: GitHub's compare API lists the changed
 * files and only those are downloaded. Large or diverged updates fall back to a
 * full source archive that is swapped in atomically. The game profile in
 * {@code run/} and the cached OptiFine download always survive an update.</p>
 */
public final class SourceManager {
    public static final class Commit {
        public final String sha, message, author;
        public final long time;
        Commit(String sha, String message, String author, long time) {
            this.sha = sha; this.message = message; this.author = author; this.time = time;
        }
    }

    public static final class Remote {
        public final String head;
        public final List<Commit> commits;
        Remote(String head, List<Commit> commits) { this.head = head; this.commits = commits; }
        Commit headCommit() { return commits.isEmpty() ? null : commits.get(0); }
    }

    private static final String API = "https://api.github.com/repos/" + VibeLauncher.OWNER + "/" + VibeLauncher.REPOSITORY;
    private static final int DELTA_FILE_LIMIT = 250;

    private final AppPaths paths;
    private final Settings settings;
    private final AppLog log;

    public SourceManager(AppPaths paths, Settings settings, AppLog log) {
        this.paths = paths;
        this.settings = settings;
        this.log = log;
    }

    public Path root() { return paths.source(); }

    public boolean isInstalled() { return isVibeRoot(paths.source()) && !settings.sourceRevision().isEmpty(); }

    public String installedRevision() { return settings.sourceRevision(); }

    static boolean isVibeRoot(Path root) {
        return root != null && Files.isRegularFile(root.resolve("build.gradle")) && Files.isDirectory(root.resolve("src"))
                && (Files.isRegularFile(root.resolve("gradlew")) || Files.isRegularFile(root.resolve("gradlew.bat")));
    }

    /** One request for both the newest revision and the commit feed shown on the home page. */
    public Remote fetchRemote() throws IOException {
        String json = Http.getText(API + "/commits?sha=" + VibeLauncher.BRANCH + "&per_page=15", 4 * 1024 * 1024);
        List<Commit> commits = new ArrayList<Commit>();
        for (Object item : Json.array(Json.parse(json))) {
            Map<String, Object> entry = Json.object(item);
            Map<String, Object> commit = Json.object(entry, "commit");
            Map<String, Object> author = Json.object(commit, "author");
            String sha = Json.string(entry, "sha");
            if (!sha.matches("[0-9a-f]{40}")) continue;
            commits.add(new Commit(sha, Text.firstLine(Json.string(commit, "message")), Json.string(author, "name"),
                    parseTime(Json.string(author, "date"))));
        }
        if (commits.isEmpty()) throw new LauncherException(ErrorCode.UNEXPECTED_RESPONSE, I18n.t("GitHub did not return any Vibe commits."));
        return new Remote(commits.get(0).sha, Collections.unmodifiableList(commits));
    }

    public boolean needsUpdate(Remote remote) {
        return !isInstalled() || settings.sourceIncomplete() || (remote != null && !remote.head.equals(settings.sourceRevision()));
    }

    /**
     * Brings the checkout to {@code remote.head}. With no remote information an
     * installed checkout is used as-is, so Vibe still starts offline.
     */
    public void update(Remote remote, Progress progress) throws IOException {
        if (remote == null) {
            if (settings.sourceIncomplete()) {
                // A half-applied update would build a mix of two versions.
                throw new LauncherException(ErrorCode.UPDATE_INCOMPLETE, I18n.t("The last Vibe update did not finish. Connect to the internet so it can be repaired."));
            }
            if (isInstalled()) return;
            throw new LauncherException(ErrorCode.VIBE_DOWNLOAD, I18n.t("Vibe could not be downloaded. Check your internet connection and try again."));
        }
        if (!needsUpdate(remote)) return;
        String installed = settings.sourceRevision();
        boolean updated = false;
        if (isInstalled() && !settings.sourceIncomplete()) {
            try {
                updated = applyDelta(installed, remote.head, progress);
            } catch (IOException error) {
                if (LauncherException.cancelled(error)) throw error;
                log.warn("Delta update failed; downloading the full source instead", error);
            }
        }
        if (!updated) installArchive(remote.head, progress);
        Commit head = remote.headCommit();
        if (head != null) settings.setSourceCommit(head.message, head.time);
        settings.setSourceUpdatedAt(System.currentTimeMillis());
    }

    // ---- delta updates -----------------------------------------------------

    private boolean applyDelta(String from, String to, Progress progress) throws IOException {
        progress.update(I18n.t("Checking what changed"), Text.shortSha(from) + " → " + Text.shortSha(to), -1);
        Map<String, Object> compare = Json.object(Json.parse(Http.getText(API + "/compare/" + from + "..." + to, 48 * 1024 * 1024)));
        String status = Json.string(compare, "status");
        if (!"ahead".equals(status) && !"identical".equals(status)) {
            log.info("Source history diverged (" + status + "); a full download is required.");
            return false;
        }
        List<Object> files = Json.array(compare, "files");
        if (files.size() >= DELTA_FILE_LIMIT || Json.number(compare, "total_commits", 0) > 240) {
            log.info("The update touches " + files.size() + " files; a full download is faster.");
            return false;
        }
        Path root = paths.source();
        Path stage = Files.createDirectories(paths.staging()).resolve("delta-" + System.currentTimeMillis());
        try {
            List<Path[]> writes = new ArrayList<Path[]>();
            List<Path> deletes = new ArrayList<Path>();
            int index = 0;
            for (Object item : files) {
                Map<String, Object> file = Json.object(item);
                String name = Json.string(file, "filename");
                String fileStatus = Json.string(file, "status");
                if (name.startsWith(".git/")) continue;
                Path target = FileUtil.resolveInside(root, name);
                index++;
                if ("removed".equals(fileStatus)) {
                    deletes.add(target);
                    continue;
                }
                if ("renamed".equals(fileStatus)) {
                    String previous = Json.string(file, "previous_filename");
                    if (!previous.isEmpty()) deletes.add(FileUtil.resolveInside(root, previous));
                }
                Path staged = FileUtil.resolveInside(stage, name);
                progress.update(I18n.t("Downloading Vibe update"), index + " / " + files.size() + "  ·  " + name,
                        (double) index / Math.max(1, files.size()));
                Http.download(rawUrl(to, name), staged, 256L * 1024L * 1024L, null);
                writes.add(new Path[] { staged, target });
            }
            // Everything is downloaded; only now is the checkout touched.
            settings.setSourceIncomplete(true);
            for (Path delete : deletes) {
                boolean rewritten = false;
                for (Path[] write : writes) if (write[1].equals(delete)) rewritten = true;
                if (!rewritten) Files.deleteIfExists(delete);
            }
            for (Path[] write : writes) {
                Files.createDirectories(write[1].getParent());
                FileUtil.move(write[0], write[1]);
                if (write[1].getFileName().toString().equals("gradlew")) write[1].toFile().setExecutable(true, false);
            }
            settings.setSourceRevision(to);
            settings.setSourceIncomplete(false);
            log.info("Applied Vibe update " + Text.shortSha(from) + ".." + Text.shortSha(to) + " (" + writes.size() + " files changed, " + deletes.size() + " removed).");
            return true;
        } finally {
            FileUtil.deleteTreeQuietly(stage);
        }
    }

    private static String rawUrl(String revision, String path) throws IOException {
        StringBuilder encoded = new StringBuilder();
        for (String segment : path.split("/")) {
            if (encoded.length() > 0) encoded.append('/');
            encoded.append(URLEncoder.encode(segment, "UTF-8").replace("+", "%20"));
        }
        return "https://raw.githubusercontent.com/" + VibeLauncher.OWNER + "/" + VibeLauncher.REPOSITORY + "/" + revision + "/" + encoded;
    }

    // ---- full installation -------------------------------------------------

    private void installArchive(String revision, Progress progress) throws IOException {
        Path staging = Files.createDirectories(paths.staging());
        Path work = Files.createTempDirectory(staging, "source-");
        try {
            Path archive = work.resolve("vibe.zip");
            String message = I18n.t("Downloading Vibe");
            progress.update(message, null, -1);
            Http.download(API + "/zipball/" + revision, archive, 2L * 1024L * 1024L * 1024L, progress.range(0, 0.8).bytes(message));
            Path extracted = work.resolve("extracted");
            try {
                Archives.unzip(archive, extracted, I18n.t("Unpacking Vibe"), progress.range(0.8, 0.98));
            } catch (IOException error) {
                throw LauncherException.wrap(error, ErrorCode.SOURCE_INVALID, I18n.t("The downloaded Vibe archive is damaged."));
            }
            Files.deleteIfExists(archive);
            Path candidate = null;
            if (isVibeRoot(extracted)) candidate = extracted;
            else for (Path child : FileUtil.children(extracted)) if (Files.isDirectory(child) && isVibeRoot(child)) candidate = child;
            if (candidate == null) throw new LauncherException(ErrorCode.SOURCE_INVALID, I18n.t("The downloaded Vibe archive does not contain a Gradle project."));
            Path gradlew = candidate.resolve("gradlew");
            if (Files.isRegularFile(gradlew)) gradlew.toFile().setExecutable(true, false);
            progress.update(I18n.t("Installing Vibe"), null, 0.99);
            swap(candidate);
            settings.setSourceRevision(revision);
            settings.setSourceIncomplete(false);
            log.info("Installed Vibe source " + Text.shortSha(revision) + ".");
        } finally {
            FileUtil.deleteTreeQuietly(work);
        }
    }

    /** Moves a freshly extracted checkout into place while keeping {@code run/} and the OptiFine cache. */
    private void swap(Path candidate) throws IOException {
        Path active = paths.source();
        Path previous = null;
        if (Files.exists(active)) {
            previous = paths.root().resolve("source-old-" + System.currentTimeMillis());
            try {
                move(active, previous);
            } catch (IOException error) {
                throw new LauncherException(ErrorCode.FILE_IN_USE, I18n.t("The Vibe folder is in use. Close programs that have files open in it and try again."), error);
            }
        }
        try {
            move(candidate, active);
        } catch (IOException error) {
            if (previous != null) move(previous, active);
            throw error;
        }
        if (previous == null) return;
        Path oldRun = previous.resolve("run");
        if (Files.exists(oldRun)) {
            try {
                move(oldRun, active.resolve("run"));
            } catch (IOException error) {
                // Never lose the profile: put the old checkout back as it was.
                // If even that fails, recoverProfile() finds it on the next start.
                move(active, candidate);
                move(previous, active);
                throw new LauncherException(ErrorCode.PROFILE_CARRY_OVER, I18n.t("The game profile could not be carried over to the new Vibe version."), error);
            }
        }
        Path optifine = previous.resolve("build").resolve("optifine");
        if (Files.isDirectory(optifine)) {
            try {
                Files.createDirectories(active.resolve("build"));
                Files.move(optifine, active.resolve("build").resolve("optifine"));
            } catch (IOException ignored) {
                // Gradle downloads OptiFine again if needed.
            }
        }
        if (!FileUtil.deleteTreeQuietly(previous)) log.warn("Could not remove the previous Vibe source at " + previous + "; it will be cleaned up later.");
    }

    /**
     * Directory renames on Windows fail briefly while a virus scanner or the search
     * indexer holds a file, so they are retried for a moment before giving up.
     */
    private static void move(Path source, Path target) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                Files.move(source, target);
                return;
            } catch (java.nio.file.FileSystemException error) {
                if (attempt >= 8 || error instanceof java.nio.file.FileAlreadyExistsException || error instanceof java.nio.file.NoSuchFileException) throw error;
                try { Thread.sleep(250L * attempt); } catch (InterruptedException interrupted) {
                    // Finish the move anyway: stopping halfway could strand the profile.
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Puts a game profile back if an interrupted update left it in a {@code source-old-*}
     * folder (for example after a power cut in the middle of the swap).
     */
    public void recoverProfile() {
        List<Path> leftovers = new ArrayList<Path>();
        for (Path child : FileUtil.children(paths.root())) if (child.getFileName().toString().startsWith("source-old-")) leftovers.add(child);
        Collections.sort(leftovers, Collections.reverseOrder());
        Path active = paths.source();
        for (Path leftover : leftovers) {
            try {
                if (!Files.exists(active)) {
                    move(leftover, active);
                    settings.setSourceIncomplete(true);
                    log.warn("Restored the Vibe checkout from " + leftover.getFileName() + " after an interrupted update.");
                } else if (Files.exists(leftover.resolve("run")) && !Files.exists(active.resolve("run"))) {
                    move(leftover.resolve("run"), active.resolve("run"));
                    log.warn("Restored the game profile from " + leftover.getFileName() + " after an interrupted update.");
                }
            } catch (IOException error) {
                log.error("Could not restore the game profile from " + leftover, error);
            }
        }
    }

    /** Removes leftovers of earlier updates, never touching a folder that still holds a profile. */
    public void cleanLeftovers() {
        recoverProfile();
        FileUtil.deleteTreeQuietly(paths.staging());
        for (Path child : FileUtil.children(paths.root())) {
            String name = child.getFileName().toString();
            boolean leftover = name.startsWith("source-old-") || name.startsWith("source-backup-") || name.matches("source-\\d+.*");
            if (!leftover || !Files.isDirectory(child)) continue;
            if (Files.exists(child.resolve("run").resolve("client"))) {
                log.warn("Keeping " + child + " because it still contains a game profile.");
                continue;
            }
            if (FileUtil.deleteTreeQuietly(child)) log.info("Removed leftover folder " + child.getFileName() + ".");
        }
    }

    /** Deletes Gradle's build output so the next launch compiles Vibe from scratch. */
    public void cleanBuild() throws IOException {
        Path build = paths.source().resolve("build");
        for (Path child : FileUtil.children(build)) {
            if (child.getFileName().toString().equals("optifine")) continue;
            FileUtil.deleteTree(child);
        }
        FileUtil.deleteTree(paths.source().resolve(".gradle"));
        log.info("Cleared the Vibe build cache.");
    }

    /** Forces the next update to download the complete source again. */
    public void markForReinstall() { settings.setSourceIncomplete(true); }

    /** The newest sections of docs/CHANGELOG.md from the installed checkout. */
    public List<ChangelogSection> changelog() {
        Path file = paths.source().resolve("docs").resolve("CHANGELOG.md");
        if (!Files.isRegularFile(file)) return Collections.emptyList();
        try {
            return ChangelogSection.parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8), 4);
        } catch (IOException ignored) {
            return Collections.emptyList();
        }
    }

    /** The version declared in build.gradle, e.g. "0.0.6". */
    public String vibeVersion() {
        try {
            for (String line : Files.readAllLines(paths.source().resolve("build.gradle"), StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("version") && trimmed.contains("'")) {
                    int start = trimmed.indexOf('\'') + 1, end = trimmed.indexOf('\'', start);
                    if (end > start) return trimmed.substring(start, end);
                }
            }
        } catch (IOException ignored) {
            // Not installed yet.
        }
        return "";
    }

    static long parseTime(String iso) {
        if (iso == null || iso.isEmpty()) return 0;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        try { return format.parse(iso).getTime(); } catch (ParseException ignored) { return 0; }
    }
}
