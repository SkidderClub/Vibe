package dev.vibe.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vibe.BuildInfo;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sketchfab's public Data API for collection listings and its Download API for model archives.
 * Listing is anonymous; downloading needs the player's personal API token
 * (sketchfab.com → Settings → Password &amp; API → API Token) and only works for models the
 * author marked downloadable.
 */
public final class SketchfabClient {

    public interface Progress {
        void update(long done, long total);
    }

    private static final String API = "https://api.sketchfab.com/v3";
    private static final Pattern UID = Pattern.compile("([0-9a-fA-F]{32})(?![0-9a-fA-F])");
    private static final long MAX_DOWNLOAD = 700L * 1024L * 1024L;

    private SketchfabClient() {
    }

    public static boolean isUid(String text) {
        return text != null && text.matches("[0-9a-fA-F]{32}");
    }

    /** Extracts the 32 character id from a model or collection URL, or accepts a bare id. */
    public static String parseUid(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        int query = trimmed.indexOf('?');
        if (query >= 0) trimmed = trimmed.substring(0, query);
        Matcher matcher = UID.matcher(trimmed);
        String last = null;
        while (matcher.find()) last = matcher.group(1);
        return last == null ? null : last.toLowerCase(java.util.Locale.ROOT);
    }

    public static boolean isCollectionUrl(String text) {
        return text != null && text.contains("/collections/");
    }

    public static ModelCatalog.Entry model(ModelKind kind, String uid) throws IOException {
        return entry(kind, parse(get(API + "/models/" + uid, null)));
    }

    /** Every model of a public collection, following Sketchfab's cursor pagination. */
    public static List<ModelCatalog.Entry> collection(ModelKind kind, String uid) throws IOException {
        List<ModelCatalog.Entry> result = new ArrayList<ModelCatalog.Entry>();
        String next = API + "/collections/" + uid + "/models?count=24";
        for (int page = 0; next != null && page < 100; page++) {
            JsonObject root = parse(get(next, null));
            JsonArray results = GltfLoader.array(root, "results");
            if (results != null) {
                for (JsonElement element : results) {
                    if (!element.isJsonObject()) continue;
                    ModelCatalog.Entry entry = entry(kind, element.getAsJsonObject());
                    if (entry != null) result.add(entry);
                }
            }
            String candidate = GltfLoader.string(root, "next", null);
            next = candidate != null && candidate.startsWith(API + "/") ? candidate : null;
        }
        return result;
    }

    private static ModelCatalog.Entry entry(ModelKind kind, JsonObject model) {
        String uid = GltfLoader.string(model, "uid", "");
        if (!isUid(uid)) return null;
        JsonObject user = GltfLoader.object(model, "user");
        JsonObject license = GltfLoader.object(model, "license");
        boolean downloadable = model.has("isDownloadable") && model.get("isDownloadable").getAsBoolean();
        return new ModelCatalog.Entry(kind, uid, GltfLoader.string(model, "name", uid).trim().replaceAll("\\s+", " "),
                GltfLoader.string(user, "username", ""), GltfLoader.string(license, "label", "Unknown"), downloadable,
                GltfLoader.integer(model, "faceCount", 0));
    }

    /**
     * Downloads a model into {@code folder/<safe name>/} and records its attribution in
     * {@link ModelFiles#INFO_FILE}. Prefers the single-file GLB, otherwise the glTF archive.
     */
    public static File download(ModelCatalog.Entry entry, String token, File folder, Progress progress) throws IOException {
        if (token == null || token.trim().isEmpty()) throw new IOException("No Sketchfab API token set");
        JsonObject links = parse(get(API + "/models/" + entry.uid + "/download", token.trim()));
        JsonObject glb = GltfLoader.object(links, "glb"), gltf = GltfLoader.object(links, "gltf");
        JsonObject chosen = glb != null ? glb : gltf;
        String url = GltfLoader.string(chosen, "url", null);
        if (url == null || !url.startsWith("https://")) throw new IOException("Sketchfab offered no glTF download for this model");
        long size = (long) GltfLoader.number(chosen, "size", 0);
        if (size > MAX_DOWNLOAD) throw new IOException("Download is " + (size >> 20) + " MB; the limit is " + (MAX_DOWNLOAD >> 20) + " MB");
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Cannot create " + folder);
        String name = ModelFiles.safeName(entry.name);
        File staging = new File(folder, "." + name + ".download");
        File part = new File(folder, "." + name + ".part");
        deleteRecursively(staging);
        try {
            fetch(url, part, size, progress);
            if (glb != null) {
                if (!staging.mkdirs()) throw new IOException("Cannot create " + staging);
                Files.move(part.toPath(), new File(staging, "model.glb").toPath());
            } else {
                ModelFiles.extract(part, staging);
            }
            if (ModelFiles.findModelFile(staging) == null) throw new IOException("The download contains no glTF model");
            JsonObject info = entry.toJson();
            info.addProperty("kind", entry.kind.id);
            info.addProperty("source", entry.pageUrl());
            Files.write(new File(staging, ModelFiles.INFO_FILE).toPath(), info.toString().getBytes(StandardCharsets.UTF_8));
            File target = new File(folder, name);
            deleteRecursively(target);
            if (!staging.renameTo(target)) throw new IOException("Cannot move the download to " + target);
            return target;
        } finally {
            part.delete();
            deleteRecursively(staging);
        }
    }

    private static void fetch(String address, File target, long expected, Progress progress) throws IOException {
        HttpURLConnection connection = open(address, null);
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("Download failed with HTTP " + status);
            long total = connection.getContentLengthLong() > 0 ? connection.getContentLengthLong() : expected;
            InputStream input = connection.getInputStream();
            OutputStream output = new FileOutputStream(target);
            try {
                byte[] buffer = new byte[64 * 1024];
                long done = 0;
                int read;
                while ((read = input.read(buffer)) > 0) {
                    done += read;
                    if (done > MAX_DOWNLOAD) throw new IOException("Download exceeds " + (MAX_DOWNLOAD >> 20) + " MB");
                    output.write(buffer, 0, read);
                    if (progress != null) progress.update(done, total);
                }
            } finally {
                output.close();
                input.close();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String get(String address, String token) throws IOException {
        HttpURLConnection connection = open(address, token);
        try {
            int status = connection.getResponseCode();
            if (status == 401) throw new IOException("Sketchfab rejected the API token (HTTP 401)");
            if (status == 403) throw new IOException("The author does not allow downloading this model (HTTP 403)");
            if (status == 404) throw new IOException("Sketchfab model or collection not found (HTTP 404)");
            if (status == 429) throw new IOException("Sketchfab rate limit reached; try again in a minute (HTTP 429)");
            if (status < 200 || status >= 300) throw new IOException("Sketchfab answered HTTP " + status);
            InputStream input = connection.getInputStream();
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ModelFiles.copy(input, output);
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            } finally {
                input.close();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String address, String token) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Vibe/" + BuildInfo.VERSION + " (Minecraft 1.8.9 client)");
        connection.setRequestProperty("Accept", "application/json, */*");
        if (token != null) connection.setRequestProperty("Authorization", "Token " + token);
        return connection;
    }

    private static JsonObject parse(String json) throws IOException {
        try {
            return new JsonParser().parse(json).getAsJsonObject();
        } catch (RuntimeException error) {
            throw new IOException("Unexpected Sketchfab response", error);
        }
    }

    static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        file.delete();
    }
}
