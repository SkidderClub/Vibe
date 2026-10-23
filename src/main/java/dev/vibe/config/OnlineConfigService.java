package dev.vibe.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Read-only GitHub catalogue. Network work never runs on the game/render thread. */
public final class OnlineConfigService implements AutoCloseable {
    public static final String REPOSITORY = "https://github.com/SkidderClub/VibeConfig";
    private static final String TREE = "https://api.github.com/repos/SkidderClub/VibeConfig/git/trees/main?recursive=1";
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final long CACHE_MILLIS = 60_000;
    interface Transport { String get(String url) throws IOException; }
    private final Transport transport;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Vibe-Online-Configs"); thread.setDaemon(true); return thread;
    });
    private volatile List<Entry> entries = Collections.emptyList();
    private long refreshedAt;
    private CompletableFuture<List<Entry>> pending;

    public OnlineConfigService() { this(OnlineConfigService::download); }
    OnlineConfigService(Transport transport) { this.transport = transport; }

    public List<Entry> getEntries() { return entries; }

    public synchronized CompletableFuture<List<Entry>> refresh(boolean force) {
        if (pending != null && !pending.isDone()) return pending;
        if (!force && refreshedAt > 0 && System.currentTimeMillis() - refreshedAt < CACHE_MILLIS)
            return CompletableFuture.completedFuture(entries);
        pending = CompletableFuture.supplyAsync(() -> {
            try {
                List<Entry> result = parseCatalogue(transport.get(TREE));
                synchronized (this) { entries = result; refreshedAt = System.currentTimeMillis(); }
                return result;
            } catch (IOException error) { throw new CompletionException(error); }
        }, worker);
        return pending;
    }

    public CompletableFuture<JsonObject> fetch(String name) {
        return refresh(false).thenApplyAsync(catalogue -> {
            String requested = name == null ? "" : name.trim();
            if (requested.toLowerCase(Locale.ROOT).endsWith(".json")) requested = requested.substring(0, requested.length() - 5);
            for (Entry entry : catalogue) if (entry.name.equalsIgnoreCase(requested)) {
                try { return parseProfile(transport.get(entry.downloadUrl)); }
                catch (IOException error) { throw new CompletionException(error); }
            }
            throw new CompletionException(new IOException("Online config not found. Use .onlineconfig list."));
        }, worker);
    }

    static List<Entry> parseCatalogue(String text) throws IOException {
        try {
            JsonObject root = new JsonParser().parse(text).getAsJsonObject();
            String revision = root.get("sha").getAsString();
            if (!revision.matches("[a-fA-F0-9]{40}") || !root.has("tree") || !root.get("tree").isJsonArray())
                throw new IOException("GitHub returned an invalid config catalogue.");
            if (root.has("truncated") && root.get("truncated").getAsBoolean())
                throw new IOException("GitHub returned an incomplete config catalogue.");
            List<Entry> result = new ArrayList<Entry>(); Set<String> names = new HashSet<String>();
            for (JsonElement element : root.getAsJsonArray("tree")) {
                if (!element.isJsonObject()) continue;
                JsonObject file = element.getAsJsonObject();
                if (!file.has("type") || !"blob".equals(file.get("type").getAsString()) || !file.has("path")) continue;
                String path = file.get("path").getAsString();
                if (!path.toLowerCase(Locale.ROOT).endsWith(".json") || path.length() > 200 || path.contains("\\")) continue;
                String[] segments = path.split("/", -1); boolean safe = true;
                for (String segment : segments) if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) safe = false;
                if (!safe || (file.has("size") && file.get("size").getAsLong() > MAX_BYTES)) continue;
                String name = path.substring(0, path.length() - 5);
                if (name.isEmpty() || !names.add(name.toLowerCase(Locale.ROOT))) continue;
                StringBuilder encoded = new StringBuilder();
                for (String segment : segments) {
                    if (encoded.length() > 0) encoded.append('/');
                    encoded.append(URLEncoder.encode(segment, "UTF-8").replace("+", "%20"));
                }
                result.add(new Entry(name, "https://raw.githubusercontent.com/SkidderClub/VibeConfig/" + revision + "/" + encoded));
            }
            result.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
            return Collections.unmodifiableList(result);
        } catch (RuntimeException error) { throw new IOException("GitHub returned an invalid config catalogue.", error); }
    }

    static JsonObject parseProfile(String text) throws IOException {
        try {
            JsonElement root = new JsonParser().parse(text);
            if (!root.isJsonObject() || !isProfile(root.getAsJsonObject())) throw new IOException("The online file is not a valid Vibe config.");
            return root.getAsJsonObject();
        } catch (RuntimeException error) { throw new IOException("The online file is not valid JSON.", error); }
    }

    static boolean isProfile(JsonObject root) {
        if (root == null || !root.has("modules") || !root.get("modules").isJsonArray() || root.getAsJsonArray("modules").size() == 0) return false;
        for (JsonElement element : root.getAsJsonArray("modules")) {
            if (!element.isJsonObject()) return false;
            JsonObject module = element.getAsJsonObject();
            if (!module.has("id") || !module.get("id").isJsonPrimitive() || !module.getAsJsonPrimitive("id").isString()
                    || module.get("id").getAsString().trim().isEmpty()) return false;
            if (module.has("settings") && !module.get("settings").isJsonObject()) return false;
        }
        return true;
    }

    private static String download(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(10_000); connection.setReadTimeout(15_000);
        connection.setRequestProperty("User-Agent", "Vibe-Online-Configs");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        try {
            int status = connection.getResponseCode();
            if (status == 403 || status == 429) throw new IOException("GitHub rate limit reached. Try again later.");
            if (status != 200) throw new IOException("GitHub request failed (HTTP " + status + ").");
            if (connection.getContentLengthLong() > MAX_BYTES) throw new IOException("Online config response is too large.");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > MAX_BYTES) throw new IOException("Online config response is too large.");
                    output.write(buffer, 0, count);
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally { connection.disconnect(); }
    }

    public static String errorMessage(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        return error.getMessage() == null ? "Could not reach the online configs." : error.getMessage();
    }

    @Override public void close() { worker.shutdownNow(); }

    public static final class Entry {
        private final String name, downloadUrl;
        Entry(String name, String downloadUrl) { this.name = name; this.downloadUrl = downloadUrl; }
        public String getName() { return name; }
    }
}
