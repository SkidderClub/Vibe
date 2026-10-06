package dev.vibe.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Finds, loads, downloads and caches custom models.
 *
 * <p>Threading: {@link #get} and {@link #tick} run on the client thread, which owns the GL
 * context. Parsing, fitting and simplifying run on one low-priority worker so the game never
 * stutters while a 100k triangle character loads; downloads use a second worker. Finished work
 * is uploaded to the GPU on the client thread, at most one model per 50 ms.
 */
public final class CustomModelManager {

    /**
     * A model selection plus the settings that change its prepared geometry. Changing any of
     * them reloads the model in the background. Callers keep and reuse instances, so the
     * per-frame lookup allocates nothing.
     */
    public static final class Request {
        public final ModelKind kind;
        public final String name;
        final int triangleBudget;
        final int textureSize;
        final int yaw;
        final boolean fixPose;
        final String key;

        public Request(ModelKind kind, String name, int triangleBudget, int textureSize, int yaw, boolean fixPose) {
            this.kind = kind;
            this.name = name == null ? "" : name;
            this.triangleBudget = triangleBudget;
            this.textureSize = textureSize;
            this.yaw = kind == ModelKind.SWORDS ? 0 : yaw;
            this.fixPose = kind != ModelKind.SWORDS && fixPose;
            this.key = kind.id + "|" + this.name.toLowerCase(Locale.ROOT) + "|" + triangleBudget + "|" + textureSize
                    + "|" + this.yaw + "|" + this.fixPose;
        }

        public boolean matches(String name, int triangleBudget, int textureSize, int yaw, boolean fixPose) {
            return this.name.equals(name) && this.triangleBudget == triangleBudget && this.textureSize == textureSize
                    && this.yaw == (kind == ModelKind.SWORDS ? 0 : yaw) && this.fixPose == (kind != ModelKind.SWORDS && fixPose);
        }
    }

    private static final long RELEASE_AFTER_MS = 20000L;
    private static final long SCAN_INTERVAL_MS = 3000L;
    private static final long IDLE_SCAN_INTERVAL_MS = 15000L;

    private final File root;
    private final File cache;
    private final File tokenFile;
    private final ModelCatalog catalog;
    private final ExecutorService loader = Executors.newSingleThreadExecutor(factory("Vibe-CustomModels"));
    private final ExecutorService downloader = Executors.newSingleThreadExecutor(factory("Vibe-ModelDownloads"));
    private final ExecutorService scanner = Executors.newSingleThreadExecutor(factory("Vibe-ModelScan"));
    private final java.util.concurrent.atomic.AtomicBoolean scanning = new java.util.concurrent.atomic.AtomicBoolean();
    private final Map<String, Slot> slots = new HashMap<String, Slot>();
    private final ConcurrentLinkedQueue<String> messages = new ConcurrentLinkedQueue<String>();
    private final Set<String> downloading = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Set<String> reported = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private volatile Map<ModelKind, Map<String, File>> local = new EnumMap<ModelKind, Map<String, File>>(ModelKind.class);
    private volatile boolean rescanRequested = true;
    private volatile String token;
    private volatile long lastScan;
    private long lastCompile;

    private static final class Slot {
        final ModelKind kind;
        final String name;
        volatile PreparedModel prepared;
        volatile String error;
        CompiledModel compiled;
        long lastUsed;

        Slot(ModelKind kind, String name) {
            this.kind = kind;
            this.name = name;
        }
    }

    public CustomModelManager(File minecraftDirectory) {
        root = new File(minecraftDirectory, "vibe/models");
        cache = new File(root, ".cache");
        tokenFile = new File(root, ".sketchfab-token");
        for (ModelKind kind : ModelKind.values()) folder(kind).mkdirs();
        catalog = new ModelCatalog(new File(root, "catalog.json"));
        token = readToken();
        writeReadme();
        // Saved selections are validated against the choices when the profile loads.
        rescan();
    }

    public File getRoot() {
        return root;
    }

    public File folder(ModelKind kind) {
        return new File(root, kind.id);
    }

    public ModelCatalog getCatalog() {
        return catalog;
    }

    /** Status lines for chat, produced by worker threads. */
    public String pollMessage() {
        return messages.poll();
    }

    // ---- selection lists ----

    /** Local models first (sorted), then downloadable catalogue entries that are not on disk yet. */
    public List<String> choices(ModelKind kind) {
        List<String> names = new ArrayList<String>(localModels(kind).keySet());
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        for (ModelCatalog.Entry entry : catalog.entries(kind)) {
            if (entry.downloadable && !containsIgnoreCase(names, entry.name)) names.add(entry.name);
        }
        return names;
    }

    public Map<String, File> localModels(ModelKind kind) {
        Map<String, File> models = local.get(kind);
        return models == null ? Collections.<String, File>emptyMap() : models;
    }

    public boolean isLocal(ModelKind kind, String name) {
        return find(kind, name) != null;
    }

    private File find(ModelKind kind, String name) {
        for (Map.Entry<String, File> entry : localModels(kind).entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        }
        return null;
    }

    private static boolean containsIgnoreCase(List<String> names, String name) {
        for (String existing : names) if (existing.equalsIgnoreCase(name)) return true;
        return false;
    }

    /** Re-reads both model folders. Model files named after a catalogue entry keep that name via their info file. */
    public void rescan() {
        // Cleared first, so a download finishing during the scan requests another one.
        rescanRequested = false;
        Map<ModelKind, Map<String, File>> scanned = new EnumMap<ModelKind, Map<String, File>>(ModelKind.class);
        for (ModelKind kind : ModelKind.values()) {
            Map<String, File> models = new LinkedHashMap<String, File>();
            File[] files = folder(kind).listFiles();
            if (files != null) {
                java.util.Arrays.sort(files);
                for (File file : files) {
                    if (file.getName().startsWith(".")) continue;
                    String name = null;
                    if (file.isDirectory()) {
                        if (ModelFiles.findModelFile(file) == null) continue;
                        name = infoName(file);
                    } else if (!ModelFiles.isModelFile(file) && !ModelFiles.isArchive(file)) {
                        continue;
                    }
                    if (name == null) name = ModelFiles.baseName(file);
                    if (!models.containsKey(name)) models.put(name, file);
                }
            }
            scanned.put(kind, Collections.unmodifiableMap(models));
        }
        local = scanned;
        lastScan = System.currentTimeMillis();
    }

    private static String infoName(File directory) {
        File info = new File(directory, ModelFiles.INFO_FILE);
        if (!info.isFile()) return null;
        try {
            JsonObject object = new JsonParser().parse(new String(Files.readAllBytes(info.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
            String name = GltfLoader.string(object, "name", null);
            return name == null || name.trim().isEmpty() ? null : name.trim();
        } catch (Exception ignored) {
            return null;
        }
    }

    public String attribution(ModelKind kind, String name) {
        File file = find(kind, name);
        ModelCatalog.Entry entry = catalog.find(kind, name);
        if (file != null && file.isDirectory()) {
            File info = new File(file, ModelFiles.INFO_FILE);
            if (info.isFile()) {
                try {
                    JsonObject object = new JsonParser().parse(new String(Files.readAllBytes(info.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
                    return "\"" + GltfLoader.string(object, "name", name) + "\" by " + GltfLoader.string(object, "author", "unknown")
                            + " (" + GltfLoader.string(object, "license", "unknown license") + ") " + GltfLoader.string(object, "source", "");
                } catch (Exception ignored) {
                    // Fall through to the catalogue.
                }
            }
        }
        if (entry != null) return "\"" + entry.name + "\" by " + entry.author + " (" + entry.license + ") " + entry.pageUrl();
        return file == null ? null : "\"" + name + "\" (local file " + file.getName() + ")";
    }

    // ---- loading ----

    /**
     * Returns the compiled model for a selection, or null while it is loading, downloading or
     * failed. While a new variant loads, the previous variant of the same model keeps rendering.
     */
    public CompiledModel get(Request request) {
        if (request.name.isEmpty()) return null;
        ModelKind kind = request.kind;
        String name = request.name;
        long now = System.currentTimeMillis();
        Slot slot = slots.get(request.key);
        if (slot == null) {
            File file = find(kind, name);
            if (file == null) {
                requestMissing(kind, name);
                return null;
            }
            slot = new Slot(kind, name);
            slots.put(request.key, slot);
            submit(slot, file, request);
        }
        slot.lastUsed = now;
        if (slot.compiled == null && slot.prepared != null && now - lastCompile >= 50L) {
            lastCompile = now;
            PreparedModel prepared = slot.prepared;
            slot.prepared = null;
            try {
                slot.compiled = CompiledModel.compile(prepared);
                messages.add("§aLoaded §f" + name + " §7(" + slot.compiled.getTriangles() + " triangles"
                        + (slot.compiled.getTriangles() < slot.compiled.getSourceTriangles()
                        ? " of " + slot.compiled.getSourceTriangles() : "")
                        + (slot.compiled.getNotes().isEmpty() ? "" : ", " + slot.compiled.getNotes()) + ")");
            } catch (RuntimeException error) {
                slot.error = "GPU upload failed: " + error;
                messages.add("§c" + name + ": " + slot.error);
            }
        }
        if (slot.compiled != null) return slot.compiled;
        // Keep showing an older variant (e.g. before a detail change) until the new one is ready.
        Slot fallback = null;
        for (Slot other : slots.values()) {
            if (other != slot && other.kind == kind && other.compiled != null && other.name.equalsIgnoreCase(name)
                    && (fallback == null || other.lastUsed > fallback.lastUsed)) fallback = other;
        }
        if (fallback != null) {
            fallback.lastUsed = now;
            return fallback.compiled;
        }
        return null;
    }

    public String status(ModelKind kind, String name) {
        for (Slot slot : slots.values()) {
            if (slot.kind == kind && slot.name.equalsIgnoreCase(name)) {
                if (slot.compiled != null) return "loaded";
                if (slot.error != null) return "failed: " + slot.error;
                return "loading";
            }
        }
        ModelCatalog.Entry entry = catalog.find(kind, name);
        if (entry != null && downloading.contains(entry.uid)) return "downloading";
        return isLocal(kind, name) ? "not loaded" : entry != null ? "not downloaded" : "missing";
    }

    private void submit(final Slot slot, final File file, final Request settings) {
        loader.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    File model = resolve(file);
                    MeshData mesh = ModelLoader.load(model, new ModelLoadOptions(settings.textureSize, 3000000, 768L << 20));
                    slot.prepared = slot.kind == ModelKind.SWORDS
                            ? SwordFitter.prepare(mesh, settings.triangleBudget)
                            : PlayerFitter.prepare(mesh, settings.triangleBudget, settings.yaw, settings.fixPose);
                } catch (Throwable error) {
                    String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                    if (error instanceof OutOfMemoryError) message = "not enough memory; lower Detail Limit or pick a smaller model";
                    slot.error = message;
                    messages.add("§c" + slot.name + " could not be loaded: " + message);
                }
            }
        });
    }

    /** A folder or archive becomes the model file inside it; archives are extracted once into .cache. */
    private File resolve(File entry) throws IOException {
        if (entry.isDirectory()) {
            File model = ModelFiles.findModelFile(entry);
            if (model == null) throw new IOException("No .glb, .gltf or .obj file in " + entry.getName());
            return model;
        }
        if (!ModelFiles.isArchive(entry)) return entry;
        String prefix = ModelFiles.safeName(ModelFiles.baseName(entry)) + "-";
        File target = new File(cache, prefix + Long.toHexString(entry.lastModified() ^ (entry.length() << 20)));
        File model = target.isDirectory() ? ModelFiles.findModelFile(target) : null;
        if (model == null) {
            // Drop extractions of older versions of the same archive.
            File[] previous = cache.listFiles();
            if (previous != null) for (File old : previous) if (old.getName().startsWith(prefix)) SketchfabClient.deleteRecursively(old);
            ModelFiles.extract(entry, target);
            model = ModelFiles.findModelFile(target);
        }
        if (model == null) throw new IOException("No .glb, .gltf or .obj file in " + entry.getName());
        return model;
    }

    // ---- downloads ----

    private void requestMissing(ModelKind kind, String name) {
        ModelCatalog.Entry entry = catalog.find(kind, name);
        if (entry == null) return;
        if (!entry.downloadable) {
            once("nodl|" + entry.uid, "§e" + entry.name + " §7is not downloadable on Sketchfab. Put its file into §f"
                    + folder(kind).getPath());
            return;
        }
        if (getToken() == null) {
            once("token|" + kind.id, "§e" + entry.name + " §7must be downloaded once. Set your Sketchfab API token with "
                    + "§b.models token <token> §7(sketchfab.com → Settings → Password & API).");
            return;
        }
        // A failed download is retried only after a reload or a new token.
        if (!reported.contains("dl|" + entry.uid)) download(entry);
    }

    /** Starts a download unless one for the same model is running. Returns false if already running. */
    public boolean download(final ModelCatalog.Entry entry) {
        final String token = getToken();
        if (token == null || !downloading.add(entry.uid)) return false;
        messages.add("§7Downloading §f" + entry.name + " §7from Sketchfab (" + entry.license + ", by " + entry.author + ")…");
        downloader.submit(new Runnable() {
            private int lastPercent = -1;

            @Override
            public void run() {
                try {
                    SketchfabClient.download(entry, token, folder(entry.kind), new SketchfabClient.Progress() {
                        @Override
                        public void update(long done, long total) {
                            int percent = total > 0 ? (int) (done * 100 / total) : -1;
                            if (percent >= 0 && percent / 25 != lastPercent / 25 && percent < 100) {
                                lastPercent = percent;
                                messages.add("§7" + entry.name + ": " + percent + "%");
                            }
                        }
                    });
                    messages.add("§aDownloaded §f" + entry.name);
                    reported.remove("dl|" + entry.uid);
                } catch (Throwable error) {
                    messages.add("§c" + entry.name + " download failed: " + error.getMessage());
                    // Do not retry every frame; a reload or a new token tries again.
                    reported.add("dl|" + entry.uid);
                } finally {
                    downloading.remove(entry.uid);
                    rescanRequested = true;
                }
            }
        });
        return true;
    }

    /**
     * Imports a Sketchfab model or collection URL in the background. A model is downloaded right
     * away when a token is set; a collection's models are added to the catalogue and download
     * when selected. Without {@code kind}, knife-like names go to swords and everything else to players.
     */
    public void importUrl(final String url, final ModelKind kind) {
        final String uid = SketchfabClient.parseUid(url);
        if (uid == null) {
            messages.add("§cNot a Sketchfab model or collection link: " + url);
            return;
        }
        final boolean collection = SketchfabClient.isCollectionUrl(url);
        messages.add("§7Reading Sketchfab " + (collection ? "collection" : "model") + " " + uid + "…");
        downloader.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    if (collection) {
                        ModelKind target = kind == null ? ModelKind.PLAYERS : kind;
                        List<ModelCatalog.Entry> entries = SketchfabClient.collection(target, uid);
                        int downloadable = 0;
                        for (ModelCatalog.Entry entry : entries) if (entry.downloadable) downloadable++;
                        int added = catalog.addUserEntries(target, entries);
                        messages.add("§aAdded " + added + " models §7to " + target.id + " (" + downloadable + " of " + entries.size()
                                + " downloadable). Select one in CustomModelRenderer to download it.");
                    } else {
                        ModelCatalog.Entry probe = SketchfabClient.model(ModelKind.PLAYERS, uid);
                        ModelKind target = kind != null ? kind : guessKind(probe.name);
                        ModelCatalog.Entry entry = SketchfabClient.model(target, uid);
                        catalog.addUserEntries(target, Collections.singletonList(entry));
                        ModelCatalog.Entry named = catalog.findUid(uid);
                        if (named == null) named = entry;
                        if (!named.downloadable) {
                            messages.add("§e" + named.name + " §7is not downloadable on Sketchfab.");
                        } else if (getToken() == null) {
                            messages.add("§7Added §f" + named.name + " §7to " + target.id + ". Set §b.models token <token> §7to download it.");
                        } else {
                            reported.remove("dl|" + named.uid);
                            download(named);
                        }
                    }
                } catch (Exception error) {
                    messages.add("§cSketchfab import failed: " + error.getMessage());
                } finally {
                    rescanRequested = true;
                }
            }
        });
    }

    static ModelKind guessKind(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        for (String word : new String[] {"knife", "knive", "sword", "karambit", "bayonet", "dagger", "blade", "katana", "machete",
                "kukri", "axe", "saber", "sabre", "lightsaber"}) {
            if (lower.contains(word)) return ModelKind.SWORDS;
        }
        return ModelKind.PLAYERS;
    }

    /** Queues every downloadable catalogue model of a kind that is not on disk yet; returns how many. */
    public int downloadAll(ModelKind kind) {
        int queued = 0;
        for (ModelCatalog.Entry entry : catalog.entries(kind)) {
            if (!entry.downloadable || isLocal(kind, entry.name)) continue;
            reported.remove("dl|" + entry.uid);
            if (download(entry)) queued++;
        }
        return queued;
    }

    public boolean isDownloading(String uid) {
        return downloading.contains(uid);
    }

    private void once(String key, String message) {
        if (reported.add(key)) messages.add(message);
    }

    public String getToken() {
        return token;
    }

    private String readToken() {
        try {
            if (!tokenFile.isFile()) return null;
            String token = new String(Files.readAllBytes(tokenFile.toPath()), StandardCharsets.UTF_8).trim();
            return token.isEmpty() ? null : token;
        } catch (IOException ignored) {
            return null;
        }
    }

    /** Stores the token outside of config profiles, so sharing a profile never shares the token. */
    public void setToken(String token) throws IOException {
        if (token == null || token.trim().isEmpty()) {
            tokenFile.delete();
            this.token = null;
        } else {
            root.mkdirs();
            Files.write(tokenFile.toPath(), token.trim().getBytes(StandardCharsets.UTF_8));
            this.token = token.trim();
        }
        reported.clear();
    }

    // ---- lifecycle ----

    /**
     * Client-thread housekeeping: rescans the folders in the background (every 3 seconds while
     * {@code active}, otherwise every 15) and frees models that are no longer drawn.
     */
    public void tick(boolean active) {
        long now = System.currentTimeMillis();
        if ((rescanRequested || now - lastScan >= (active ? SCAN_INTERVAL_MS : IDLE_SCAN_INTERVAL_MS)) && scanning.compareAndSet(false, true)) {
            lastScan = now;
            scanner.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        rescan();
                    } finally {
                        scanning.set(false);
                    }
                }
            });
        }
        Iterator<Map.Entry<String, Slot>> iterator = slots.entrySet().iterator();
        while (iterator.hasNext()) {
            Slot slot = iterator.next().getValue();
            if (now - slot.lastUsed < RELEASE_AFTER_MS) continue;
            if (slot.compiled != null) slot.compiled.delete();
            iterator.remove();
        }
    }

    /** Forgets every loaded model and error, e.g. after files changed. */
    public void reload() {
        for (Slot slot : slots.values()) if (slot.compiled != null) slot.compiled.delete();
        slots.clear();
        reported.clear();
        catalog.reload();
        rescan();
    }

    /** Frees GPU memory of everything; used when the module is disabled. */
    public void releaseAll() {
        for (Slot slot : slots.values()) if (slot.compiled != null) slot.compiled.delete();
        slots.clear();
    }

    private void writeReadme() {
        File readme = new File(root, "README.txt");
        if (readme.isFile()) return;
        String text = "Vibe CustomModelRenderer\r\n\r\n"
                + "swords/   models that replace swords in your hand (knives work best)\r\n"
                + "players/  models that replace the player model\r\n\r\n"
                + "Drop a .glb, .gltf (with its .bin and textures) or .obj (with .mtl and textures) file,\r\n"
                + "a folder containing one, or a Sketchfab download .zip into a folder. It appears in the\r\n"
                + "module's model list within a few seconds. Use .models token <token> to let Vibe download\r\n"
                + "the default Sketchfab collections and .models import <url> for any other Sketchfab model.\r\n";
        try {
            Files.write(readme.toPath(), text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // The readme is a convenience only.
        }
    }

    private static ThreadFactory factory(final String name) {
        return new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, name);
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            }
        };
    }
}
