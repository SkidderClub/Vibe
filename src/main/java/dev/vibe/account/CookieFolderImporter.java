package dev.vibe.account;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** One file per poll, on the account worker. No cookie values are included in results. */
public final class CookieFolderImporter {
    private final Path directory;
    private final Login login;
    private final Saver saver;
    private final Map<Path, Observation> observed = new HashMap<>();
    private Path lastAttempt;
    private volatile boolean paused;
    private volatile String status = "Drop Netscape .txt exports into vibe/cookies. Checking automatically.";
    private volatile int valid, invalid, errors, pending;

    CookieFolderImporter(Path directory, Login login, Saver saver) {
        this.directory = directory.toAbsolutePath().normalize();
        this.login = login;
        this.saver = saver;
    }

    public Path getDirectory() { return directory; }
    public boolean isPaused() { return paused; }
    public void setPaused(boolean value) { paused = value; }
    public String getStatus() { return status; }
    public String getSummary() {
        return valid + " valid / " + invalid + " invalid / " + errors + " errors / " + pending + " pending";
    }

    void scan() {
        if (paused) return;
        try {
            ensureDirectory(directory);
            for (String outcome : new String[] {"valid", "invalid", "error"}) ensureDirectory(directory.resolve(outcome));
            List<Path> files;
            try (Stream<Path> stream = Files.list(directory)) {
                // Bound the in-memory batch; archived files make room for the next batch.
                files = stream.filter(CookieFolderImporter::isExport).limit(1000).sorted().collect(Collectors.toList());
            }
            pending = files.size();
            observed.keySet().retainAll(files);
            Path candidate = null;
            Path fallback = null;
            for (Path file : files) {
                checkCancelled();
                try {
                    BasicFileAttributes stamp = attributes(file);
                    Observation previous = observed.get(file);
                    if (previous == null || !sameStamp(previous.stamp, stamp)) {
                        observed.put(file, new Observation(stamp));
                    } else {
                        if (fallback == null) fallback = file;
                        if (candidate == null && (lastAttempt == null || file.compareTo(lastAttempt) > 0)) candidate = file;
                    }
                } catch (IOException e) {
                    status = "Cannot inspect a cookie file. Check the folder permissions.";
                }
            }
            // Wait for an unchanged file across two polls so ordinary copies can finish.
            if (candidate == null) candidate = fallback;
            if (candidate != null && !paused) {
                lastAttempt = candidate;
                process(candidate, observed.get(candidate));
            }
        } catch (CancellationException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // OS errors and arbitrary exception messages may contain sensitive paths/values.
            status = "Cannot process the cookie folder. Check folder access and available disk space.";
        }
    }

    private void process(Path file, Observation observation) throws IOException {
        if (observation.outcome == null) {
            status = "Checking " + displayName(file) + "...";
            byte[] data = null;
            Account account;
            try {
                data = MicrosoftCookies.readBytes(file);
                if (!sameStamp(observation.stamp, attributes(file))) { observed.remove(file); return; }
                observation.digest = digest(data);
                try (MicrosoftCookies cookies = MicrosoftCookies.parse(new String(data, StandardCharsets.UTF_8),
                        System.currentTimeMillis() / 1000)) {
                    account = login.authenticate(cookies);
                }
                checkCancelled();
            } catch (InvalidCookiesException e) {
                observation.complete("invalid", e.getMessage());
                archive(file, observation);
                return;
            } catch (IOException e) {
                observation.complete("error", "Could not verify this export. Check the Netscape format, file size (max 1 MiB) and connection. "
                        + "Microsoft may require consent or an interactive sign-in. Use Cookie login for details, then move the export back to vibe/cookies to retry.");
                archive(file, observation);
                return;
            } catch (CancellationException e) {
                throw e;
            } catch (RuntimeException e) {
                observation.complete("error", "Sign-in returned an unusable response. Try Cookie login for details, then move the export back to retry.");
                archive(file, observation);
                return;
            } finally {
                if (data != null) Arrays.fill(data, (byte) 0);
            }
            try {
                boolean added = saver.save(account);
                observation.complete("valid", (added ? "Added " : "Updated ") + account.getName() + ". Minecraft Java profile verified and saved.");
            } catch (IOException e) {
                observation.complete("error", "Profile verified, but the account could not be saved. Check vault access, disk space and the 1000-account limit, then retry.");
            }
        }
        archive(file, observation);
    }

    private void archive(Path file, Observation observation) throws IOException {
        checkCancelled();
        if (!sameStamp(observation.stamp, attributes(file))) { observed.remove(file); return; }
        if (observation.digest != null) {
            byte[] current = MicrosoftCookies.readBytes(file);
            try {
                if (!Arrays.equals(observation.digest, digest(current))) { observed.remove(file); return; }
            } finally { Arrays.fill(current, (byte) 0); }
        }
        Path folder = directory.resolve(observation.outcome);
        ensureDirectory(folder);
        // All sources are direct children of our normalized inbox; targets stay in an outcome folder.
        String originalName = file.getFileName().toString();
        String archiveName = originalName.length() > 180 ? UUID.randomUUID() + ".txt" : originalName;
        Path target = folder.resolve(archiveName).normalize();
        if (!directory.equals(file.getParent()) || !folder.equals(target.getParent())) throw new IOException();
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                || Files.exists(target.resolveSibling(target.getFileName() + ".result"), LinkOption.NOFOLLOW_LINKS)) {
            target = folder.resolve(UUID.randomUUID() + "-" + archiveName);
        }
        // Never overwrite an earlier export. If moving fails, keep the completed result in memory
        // and retry archiving on the next poll without repeating authentication/token rotation.
        Files.move(file, target);
        observed.remove(file);
        pending = Math.max(0, pending - 1);
        if ("valid".equals(observation.outcome)) valid++;
        else if ("invalid".equals(observation.outcome)) invalid++;
        else errors++;
        status = displayName(file) + ": " + observation.message;
        try {
            Files.write(target.resolveSibling(target.getFileName() + ".result"),
                    (observation.outcome.toUpperCase(Locale.ROOT) + "\n" + observation.message + "\n").getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            status = "Filed " + displayName(file) + " under " + observation.outcome + "; could not write its .result note.";
        }
    }

    private static boolean isExport(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt")
                && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static void ensureDirectory(Path path) throws IOException {
        Files.createDirectories(path);
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException();
    }

    private static BasicFileAttributes attributes(Path path) throws IOException {
        BasicFileAttributes result = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!result.isRegularFile()) throw new IOException();
        return result;
    }

    private static boolean sameStamp(BasicFileAttributes first, BasicFileAttributes second) {
        return first.size() == second.size() && first.lastModifiedTime().equals(second.lastModifiedTime())
                && java.util.Objects.equals(first.fileKey(), second.fileKey());
    }

    private static byte[] digest(byte[] data) {
        try { return MessageDigest.getInstance("SHA-256").digest(data); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }

    private static String displayName(Path path) {
        String name = path.getFileName().toString().replaceAll("[\\p{Cntrl}\\u00a7]", "?");
        return name.length() <= 80 ? name : name.substring(0, 77) + "...";
    }

    private static void checkCancelled() { if (Thread.currentThread().isInterrupted()) throw new CancellationException(); }

    interface Login { Account authenticate(MicrosoftCookies cookies) throws IOException; }
    interface Saver { boolean save(Account account) throws IOException; }

    private static final class Observation {
        final BasicFileAttributes stamp;
        byte[] digest;
        String outcome, message;
        Observation(BasicFileAttributes stamp) { this.stamp = stamp; }
        void complete(String outcome, String message) { this.outcome = outcome; this.message = message; }
    }
}
