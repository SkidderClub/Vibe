package dev.vibe.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Known Sketchfab models: the bundled snapshot of the default knife and Star Wars collections
 * plus collections and models the player imported. Entries that are not on disk yet are
 * downloaded the first time they are selected.
 */
public final class ModelCatalog {

    public static final class Entry {
        public final ModelKind kind;
        public final String uid;
        /** Unique within its kind; used as the selectable name. */
        public final String name;
        public final String author;
        public final String license;
        public final boolean downloadable;
        public final int faces;

        public Entry(ModelKind kind, String uid, String name, String author, String license, boolean downloadable, int faces) {
            this.kind = kind;
            this.uid = uid;
            this.name = name;
            this.author = author == null ? "" : author;
            this.license = license == null || license.isEmpty() ? "Unknown" : license;
            this.downloadable = downloadable;
            this.faces = faces;
        }

        public String pageUrl() {
            return "https://sketchfab.com/3d-models/" + uid;
        }

        Entry renamed(String newName) {
            return new Entry(kind, uid, newName, author, license, downloadable, faces);
        }

        JsonObject toJson() {
            JsonObject object = new JsonObject();
            object.addProperty("uid", uid);
            object.addProperty("name", name);
            object.addProperty("author", author);
            object.addProperty("license", license);
            object.addProperty("downloadable", downloadable);
            object.addProperty("faces", faces);
            return object;
        }

        static Entry fromJson(ModelKind kind, JsonObject object) {
            String uid = GltfLoader.string(object, "uid", "");
            String name = GltfLoader.string(object, "name", "");
            if (!SketchfabClient.isUid(uid) || name.trim().isEmpty()) return null;
            boolean downloadable = object.has("downloadable") && object.get("downloadable").getAsBoolean();
            return new Entry(kind, uid, name.trim().replaceAll("\\s+", " "), GltfLoader.string(object, "author", ""),
                    GltfLoader.string(object, "license", "Unknown"), downloadable, GltfLoader.integer(object, "faces", 0));
        }
    }

    private final File userFile;
    private final Map<ModelKind, List<Entry>> entries = new LinkedHashMap<ModelKind, List<Entry>>();

    public ModelCatalog(File userFile) {
        this.userFile = userFile;
        reload();
    }

    public synchronized void reload() {
        List<Entry> all = new ArrayList<Entry>();
        InputStream bundled = ModelCatalog.class.getResourceAsStream("/assets/vibe/models/catalog.json");
        if (bundled != null) {
            try {
                all.addAll(parse(new InputStreamReader(bundled, StandardCharsets.UTF_8)));
            } catch (Exception ignored) {
                // A damaged resource leaves only the user's own entries.
            }
        }
        if (userFile != null && userFile.isFile()) {
            try {
                all.addAll(parse(Files.newBufferedReader(userFile.toPath(), StandardCharsets.UTF_8)));
            } catch (Exception ignored) {
                // Unreadable user catalogue: keep the bundled one.
            }
        }
        entries.clear();
        for (ModelKind kind : ModelKind.values()) entries.put(kind, unique(all, kind));
    }

    static List<Entry> parse(Reader reader) throws IOException {
        try {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            List<Entry> result = new ArrayList<Entry>();
            JsonArray collections = GltfLoader.array(root, "collections");
            for (int index = 0; collections != null && index < collections.size(); index++) {
                JsonObject collection = GltfLoader.element(collections, index);
                ModelKind kind = ModelKind.byId(GltfLoader.string(collection, "kind", ""));
                JsonArray models = GltfLoader.array(collection, "models");
                if (kind == null || models == null) continue;
                for (JsonElement model : models) {
                    if (!model.isJsonObject()) continue;
                    Entry entry = Entry.fromJson(kind, model.getAsJsonObject());
                    if (entry != null) result.add(entry);
                }
            }
            return result;
        } catch (RuntimeException error) {
            throw new IOException(error.getMessage(), error);
        } finally {
            reader.close();
        }
    }

    /** Drops repeated uids and gives repeated names the author's name, then a uid prefix. */
    private static List<Entry> unique(List<Entry> all, ModelKind kind) {
        List<Entry> result = new ArrayList<Entry>();
        Set<String> uids = new HashSet<String>();
        Map<String, Integer> nameCounts = new LinkedHashMap<String, Integer>();
        for (Entry entry : all) {
            if (entry.kind != kind || !uids.add(entry.uid)) continue;
            String key = entry.name.toLowerCase(Locale.ROOT);
            nameCounts.put(key, nameCounts.containsKey(key) ? nameCounts.get(key) + 1 : 1);
            result.add(entry);
        }
        Set<String> taken = new HashSet<String>();
        for (int index = 0; index < result.size(); index++) {
            Entry entry = result.get(index);
            String name = entry.name;
            if (nameCounts.get(name.toLowerCase(Locale.ROOT)) > 1 && !entry.author.isEmpty()) name = name + " (" + entry.author + ")";
            if (!taken.add(name.toLowerCase(Locale.ROOT))) {
                name = name + " [" + entry.uid.substring(0, 6) + "]";
                taken.add(name.toLowerCase(Locale.ROOT));
            }
            if (!name.equals(entry.name)) result.set(index, entry.renamed(name));
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized List<Entry> entries(ModelKind kind) {
        List<Entry> list = entries.get(kind);
        return list == null ? Collections.<Entry>emptyList() : list;
    }

    public synchronized Entry find(ModelKind kind, String name) {
        if (name == null) return null;
        for (Entry entry : entries(kind)) if (entry.name.equalsIgnoreCase(name)) return entry;
        return null;
    }

    public synchronized Entry findUid(String uid) {
        for (List<Entry> list : entries.values()) for (Entry entry : list) if (entry.uid.equalsIgnoreCase(uid)) return entry;
        return null;
    }

    /** Adds models to the user catalogue file and reloads. Returns how many were new. */
    public synchronized int addUserEntries(ModelKind kind, List<Entry> added) throws IOException {
        JsonObject root;
        if (userFile.isFile()) {
            try {
                root = new JsonParser().parse(new String(Files.readAllBytes(userFile.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (RuntimeException error) {
                root = new JsonObject();
            }
        } else {
            root = new JsonObject();
        }
        JsonArray collections = GltfLoader.array(root, "collections");
        if (collections == null) {
            collections = new JsonArray();
            root.add("collections", collections);
        }
        JsonObject target = null;
        for (int index = 0; index < collections.size(); index++) {
            JsonObject collection = GltfLoader.element(collections, index);
            if (collection != null && kind.id.equals(GltfLoader.string(collection, "kind", ""))) target = collection;
        }
        if (target == null) {
            target = new JsonObject();
            target.addProperty("kind", kind.id);
            target.addProperty("name", "Imported");
            target.add("models", new JsonArray());
            collections.add(target);
        }
        JsonArray models = GltfLoader.array(target, "models");
        Set<String> known = new HashSet<String>();
        for (Entry entry : entries(kind)) known.add(entry.uid);
        int count = 0;
        for (Entry entry : added) {
            if (!known.add(entry.uid)) continue;
            models.add(entry.toJson());
            count++;
        }
        File parent = userFile.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        Files.write(userFile.toPath(), root.toString().getBytes(StandardCharsets.UTF_8));
        reload();
        return count;
    }
}
