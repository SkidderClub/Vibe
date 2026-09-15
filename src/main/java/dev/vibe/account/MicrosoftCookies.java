package dev.vibe.account;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpCookie;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;

/** A per-login cookie jar. It never accesses browser profiles or writes imported cookies. */
final class MicrosoftCookies implements AutoCloseable {
    private static final int MAX_BYTES = 1024 * 1024;
    private static final int MAX_COOKIES = 512;
    private final List<Entry> entries = new ArrayList<>();

    static MicrosoftCookies read(Path path) throws IOException {
        byte[] data = readBytes(path);
        try { return parse(new String(data, StandardCharsets.UTF_8), System.currentTimeMillis() / 1000); }
        finally { Arrays.fill(data, (byte) 0); }
    }

    static byte[] readBytes(Path path) throws IOException {
        byte[] data;
        try (InputStream in = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            try {
                int count;
                while ((count = in.read(buffer)) != -1) {
                    checkCancelled();
                    if (out.size() + count > MAX_BYTES) throw new IOException();
                    out.write(buffer, 0, count);
                }
                data = out.toByteArray();
            } finally { Arrays.fill(buffer, (byte) 0); }
        } catch (IOException e) {
            throw new IOException("Cannot read cookie export. Choose a Netscape .txt file smaller than 1 MiB.");
        }
        return data;
    }

    static MicrosoftCookies parse(String text, long now) throws IOException {
        MicrosoftCookies jar = new MicrosoftCookies();
        try {
            if (text.length() > MAX_BYTES) throw invalid();
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            int rows = 0;
            for (String line : text.split("\r?\n")) {
                checkCancelled();
                if (line.trim().isEmpty()) continue;
                if (line.startsWith("#HttpOnly_")) line = line.substring(10);
                else if (line.startsWith("#")) continue;
                String[] fields = line.split("\t", -1);
                if (fields.length != 7 || ++rows > MAX_COOKIES) throw invalid();
                String domain = fields[0].toLowerCase(Locale.ROOT);
                if (domain.startsWith(".")) domain = domain.substring(1);
                // Ignore unrelated cookies in a full-browser export.
                if (!acceptedDomain(domain)) continue;
                boolean subdomains = flag(fields[1]);
                boolean secure = flag(fields[3]);
                long expires;
                try { expires = Long.parseLong(fields[4]); }
                catch (NumberFormatException e) { throw invalid(); }
                Entry entry = new Entry(domain, subdomains, fields[2], secure, expires, fields[5], fields[6]);
                if (!entry.valid() || expires < 0) throw invalid();
                if (expires == 0 || expires > now) jar.put(entry);
            }
            if (jar.entries.isEmpty()) throw new InvalidCookiesException("No unexpired Microsoft sign-in cookies found. Export your signed-in session again.");
            return jar;
        } catch (IOException | RuntimeException e) {
            jar.close();
            throw e;
        }
    }

    String header(URI uri) throws IOException {
        if (!trusted(uri)) throw new IOException("Cookie sign-in returned an unsupported sign-in address.");
        checkCancelled();
        long now = System.currentTimeMillis() / 1000;
        entries.removeIf(entry -> entry.expires != 0 && entry.expires <= now);
        List<Entry> matching = new ArrayList<>();
        for (Entry entry : entries) if (entry.matches(uri)) matching.add(entry);
        matching.sort(Comparator.comparingInt((Entry entry) -> entry.path.length()).reversed());
        StringBuilder result = new StringBuilder();
        for (Entry entry : matching) {
            if (result.length() > 0) result.append("; ");
            result.append(entry.name).append('=').append(entry.value);
            if (result.length() > 65536) throw new IOException("Too many Microsoft cookies in this export.");
        }
        return result.toString();
    }

    void receive(URI uri, List<String> headers) throws IOException {
        if (!trusted(uri)) throw new IOException("Cookie sign-in returned an unsupported sign-in address.");
        for (String header : headers) {
            if (header == null || header.length() > 65536) continue;
            try {
                for (HttpCookie cookie : HttpCookie.parse(header)) {
                    boolean subdomains = cookie.getDomain() != null;
                    String domain = subdomains ? cookie.getDomain().toLowerCase(Locale.ROOT) : uri.getHost();
                    if (domain.startsWith(".")) domain = domain.substring(1);
                    if (!acceptedDomain(domain) || !domainMatches(uri.getHost(), domain, subdomains)) continue;
                    String path = cookie.getPath();
                    if (path == null || !path.startsWith("/")) {
                        String requestPath = uri.getRawPath();
                        int slash = requestPath.lastIndexOf('/');
                        path = slash <= 0 ? "/" : requestPath.substring(0, slash);
                    }
                    long age = cookie.getMaxAge();
                    long expires = age < 0 ? 0 : System.currentTimeMillis() / 1000 + Math.min(age, 315360000L);
                    Entry entry = new Entry(domain, subdomains, path, cookie.getSecure(), expires, cookie.getName(), cookie.getValue());
                    if (entry.valid()) put(entry);
                }
            } catch (IllegalArgumentException ignored) { /* Malformed provider cookies are never displayed. */ }
        }
    }

    private void put(Entry entry) throws IOException {
        entries.removeIf(old -> old.domain.equals(entry.domain) && old.path.equals(entry.path) && old.name.equals(entry.name));
        if (entries.size() >= MAX_COOKIES) throw new IOException("Too many Microsoft cookies in this export.");
        entries.add(entry);
    }

    static boolean trusted(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443)
                // Microsoft appends a bare '#' to ordinary query-mode OAuth redirects.
                && (uri.getRawFragment() == null || uri.getRawFragment().isEmpty())
                && ("login.live.com".equalsIgnoreCase(uri.getHost()) || "login.microsoftonline.com".equalsIgnoreCase(uri.getHost()));
    }

    private static boolean acceptedDomain(String domain) {
        return domain.equals("live.com") || domain.equals("login.live.com")
                || domain.equals("microsoftonline.com") || domain.equals("login.microsoftonline.com");
    }

    private static boolean domainMatches(String host, String domain, boolean subdomains) {
        return host.equalsIgnoreCase(domain) || (subdomains && host.toLowerCase(Locale.ROOT).endsWith("." + domain));
    }

    private static boolean flag(String value) throws IOException {
        if ("TRUE".equalsIgnoreCase(value)) return true;
        if ("FALSE".equalsIgnoreCase(value)) return false;
        throw invalid();
    }

    private static IOException invalid() { return new InvalidCookiesException("Invalid cookie export. Use Netscape format with seven tab-separated fields per cookie."); }
    private static void checkCancelled() { if (Thread.currentThread().isInterrupted()) throw new CancellationException(); }
    @Override public void close() { entries.clear(); }

    private static final class Entry {
        final String domain, path, name, value;
        final boolean subdomains, secure;
        final long expires;

        Entry(String domain, boolean subdomains, String path, boolean secure, long expires, String name, String value) {
            this.domain = domain; this.subdomains = subdomains; this.path = path; this.secure = secure;
            this.expires = expires; this.name = name; this.value = value;
        }

        boolean valid() {
            return path.startsWith("/") && path.chars().allMatch(c -> c >= 0x21 && c <= 0x7e)
                    && name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+") && value != null
                    && value.length() <= 32768 && value.chars().allMatch(c -> c == 0x21 || (c >= 0x23 && c <= 0x2b)
                    || (c >= 0x2d && c <= 0x3a) || (c >= 0x3c && c <= 0x5b) || (c >= 0x5d && c <= 0x7e));
        }

        boolean matches(URI uri) {
            String requestPath = uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            return domainMatches(uri.getHost(), domain, subdomains) && (!secure || "https".equalsIgnoreCase(uri.getScheme()))
                    && (requestPath.equals(path) || (requestPath.startsWith(path)
                    && (path.endsWith("/") || requestPath.charAt(path.length()) == '/')));
        }
    }
}
