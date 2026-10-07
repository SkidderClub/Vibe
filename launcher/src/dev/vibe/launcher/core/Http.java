package dev.vibe.launcher.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** HTTPS-only transfers with size limits, progress and readable errors. */
public final class Http {
    public interface Progress { void transferred(long bytes, long total); }

    /** An HTTP response outside 2xx, with a message that can be shown to the user. */
    public static final class StatusException extends IOException {
        private static final long serialVersionUID = 1L;
        public final int status;
        public final String host;
        /** GitHub's hourly or secondary request limit, not a real server failure. */
        public final boolean rateLimited;
        StatusException(int status, String host, boolean rateLimited, String message) {
            super(message);
            this.status = status;
            this.host = host;
            this.rateLimited = rateLimited;
        }
    }

    private static volatile String userAgent = "VibeLauncher";

    private Http() { }

    public static void setUserAgent(String value) { userAgent = value; }

    public static String getText(String url, int limit) throws IOException {
        HttpURLConnection connection = open(url, 15000, 30000);
        try {
            check(connection, url);
            InputStream input = connection.getInputStream();
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[16384];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > limit) throw new IOException("The response from " + host(url) + " is unexpectedly large.");
                    output.write(buffer, 0, count);
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Cancelled.");
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            } finally {
                input.close();
            }
        } finally {
            connection.disconnect();
        }
    }

    /** Status code without reading a body; used for lookups where 204/404 mean "not found". */
    public static int status(String url) throws IOException {
        HttpURLConnection connection = open(url, 10000, 20000);
        try { return connection.getResponseCode(); } finally { connection.disconnect(); }
    }

    /**
     * Downloads to {@code target}, retrying dropped or timed-out transfers twice. The file
     * is written next to the target first and only moved into place once complete.
     */
    public static void download(String url, Path target, long limit, Progress progress) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                downloadOnce(url, target, limit, progress);
                return;
            } catch (StatusException error) {
                throw error;
            } catch (IOException error) {
                // A read timeout is an InterruptedIOException too, but worth another attempt.
                if (LauncherException.cancelled(error)) throw error;
                if (!retryable(ErrorCode.of(error))) throw error;
                last = error;
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Cancelled.");
                try { Thread.sleep(1500L * attempt); } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("Cancelled.");
                }
            }
        }
        throw last;
    }

    /** Network hiccups are retried; a full disk, a certificate problem or a missing file would fail again. */
    private static boolean retryable(ErrorCode code) {
        switch (code) {
            case NO_INTERNET: case CONNECTION_BLOCKED: case TIMEOUT: case DOWNLOAD_INTERRUPTED: case UNEXPECTED: return true;
            default: return false;
        }
    }

    private static void downloadOnce(String url, Path target, long limit, Progress progress) throws IOException {
        HttpURLConnection connection = open(url, 20000, 60000);
        Path partial = target.resolveSibling(target.getFileName() + ".part");
        try {
            check(connection, url);
            long total = connection.getContentLengthLong();
            if (total > limit) throw new IOException("The download from " + host(url) + " exceeds the safety limit.");
            Files.createDirectories(target.toAbsolutePath().getParent());
            InputStream input = connection.getInputStream();
            try {
                OutputStream output = Files.newOutputStream(partial);
                try {
                    byte[] buffer = new byte[65536];
                    long count = 0;
                    long reported = 0;
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Cancelled.");
                        count += read;
                        if (count > limit) throw new IOException("The download from " + host(url) + " exceeds the safety limit.");
                        output.write(buffer, 0, read);
                        if (progress != null && count - reported >= 256 * 1024) {
                            reported = count;
                            progress.transferred(count, total);
                        }
                    }
                    if (total > 0 && count != total) throw new IOException("The download from " + host(url) + " ended early.");
                    if (progress != null) progress.transferred(count, total > 0 ? total : count);
                } finally {
                    output.close();
                }
            } finally {
                input.close();
            }
            Files.move(partial, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            connection.disconnect();
            Files.deleteIfExists(partial);
        }
    }

    public static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            InputStream input = Files.newInputStream(file);
            try {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            } finally {
                input.close();
            }
            StringBuilder hex = new StringBuilder();
            for (byte value : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", value & 0xFF));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IOException("SHA-256 is unavailable.", error);
        }
    }

    private static HttpURLConnection open(String url, int connectTimeout, int readTimeout) throws IOException {
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IOException("Refusing a non-HTTPS connection to " + uri.getHost() + ".");
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", userAgent);
        if ("api.github.com".equalsIgnoreCase(uri.getHost())) {
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        }
        return connection;
    }

    private static void check(HttpURLConnection connection, String url) throws IOException {
        int code = connection.getResponseCode();
        if (code >= 200 && code < 300) return;
        String host = host(url);
        // GitHub answers 403 or 429 for its hourly limit (no requests remaining) and for its
        // secondary limit (with Retry-After).
        boolean github = host != null && host.toLowerCase(Locale.ROOT).endsWith("github.com");
        boolean limited = github && (code == 429 || (code == 403 && ("0".equals(connection.getHeaderField("X-RateLimit-Remaining"))
                || connection.getHeaderField("Retry-After") != null)));
        if (limited) {
            String reset = connection.getHeaderField("X-RateLimit-Reset");
            String when = "";
            try { when = " " + I18n.t("(until {0})", new SimpleDateFormat("HH:mm").format(new Date(Long.parseLong(reset) * 1000L))); }
            catch (Exception ignored) { /* no reset time */ }
            throw new StatusException(code, host, true, I18n.t("GitHub's request limit is reached{0}.", when));
        }
        throw new StatusException(code, host, false, I18n.t("{0} answered HTTP {1}.", host, code));
    }

    private static String host(String url) {
        try { return URI.create(url).getHost(); } catch (Exception ignored) { return "the server"; }
    }
}
