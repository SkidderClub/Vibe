package dev.vibe.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.vibe.Vibe;
import dev.vibe.module.impl.client.HudModule;
import dev.vibe.module.impl.client.NameProtectModule;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.RangeSetting;
import dev.vibe.setting.Setting;
import dev.vibe.setting.StringSetting;
import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.text.SimpleDateFormat;
import java.util.Date;

/** JSON-backed profiles for modules, keybinds, and all typed settings. */
public final class VibeConfig {

    private static final int FORMAT_VERSION = 3;
    private static final String DEFAULT_PROFILE = "default";

    private final File directory;
    private final File activeProfileFile;
    private final File sessionFile;
    private String activeName;
    private boolean loading;
    private volatile boolean sessionReady;
    private JsonObject previousSession = new JsonObject();
    private String lastSessionJson;
    private long nextSessionCheck;

    public VibeConfig(File minecraftConfigDirectory) {
        File vibeDirectory = new File(minecraftConfigDirectory, "vibe");
        directory = new File(vibeDirectory, "configs");
        activeProfileFile = new File(vibeDirectory, "active-profile.txt");
        sessionFile = new File(vibeDirectory, "session.json");
        activeName = readActiveName();
    }

    public void load(ModuleManager manager) {
        if (!getFile(activeName).isFile()) {
            // A removed custom profile must not make every future launch use
            // stale settings. Fall back to the compiled first-run profile,
            // which is deliberately never copied into the configs folder.
            if (!DEFAULT_PROFILE.equals(activeName)) {
                activeName = DEFAULT_PROFILE;
                rememberActiveName();
            }
            if (!getFile(DEFAULT_PROFILE).isFile()) {
                loadCompiledDefaults(manager);
                if (Vibe.getInstance() != null && Vibe.getInstance().getStatistics() != null) Vibe.getInstance().getStatistics().recordConfigLoad();
                return;
            }
        }
        load(activeName, manager);
    }

    public boolean load(String name, ModuleManager manager) {
        return load(name, manager, true, true);
    }

    public boolean load(String name, ModuleManager manager, boolean withKeybinds, boolean withVisuals) {
        String safeName = normalizeName(name);
        if (safeName == null) return false;
        File file = getFile(safeName);
        if (!file.isFile()) {
            return false;
        }
        loading = true;
        try {
            Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            JsonElement rootElement;
            try {
                rootElement = new JsonParser().parse(reader);
            } finally {
                reader.close();
            }
            if (!rootElement.isJsonObject() || !apply(rootElement.getAsJsonObject(), manager, withKeybinds, withVisuals)) return false;
            activeName = safeName;
            rememberActiveName();
            if (Vibe.getInstance() != null && Vibe.getInstance().getStatistics() != null) Vibe.getInstance().getStatistics().recordConfigLoad();
            return true;
        } catch (Exception ignored) {
            return false;
        } finally {
            loading = false;
        }
    }

    /** Kept for binary compatibility with extensions that previously requested autosaves.
     * Profile contents are written only by the explicit, named save operation. */
    @Deprecated
    public void save(ModuleManager manager) {
        // Runtime edits remain in memory until the user saves a named profile.
        // The independent session checkpoint restores them across client restarts.
    }

    /** Called after extensions and scripts are registered; never opens editor/action modules. */
    public synchronized boolean restoreSession(ModuleManager manager) {
        if (manager == null) return false;
        try {
            JsonObject root = readObject(sessionFile);
            if (getInt(root, "sessionVersion", 0) != 1 || !root.has("modules") || !root.get("modules").isJsonArray()) return false;
            if (!apply(root, manager, true, true)) return false;
            String profile = getString(root, "profile", activeName);
            activeName = profile.startsWith("online/") ? profile : normalizeName(profile);
            if (activeName == null) activeName = DEFAULT_PROFILE;
            previousSession = root;
            lastSessionJson = root.toString();
            return true;
        } catch (RuntimeException invalidSession) {
            return false;
        } finally {
            sessionReady = true;
        }
    }

    /** Main-thread checkpoint: at most once every two seconds, writing only changed state. */
    public void tickSession(ModuleManager manager) {
        if (!sessionReady || loading) return;
        long now = System.nanoTime();
        if (now < nextSessionCheck) return;
        nextSessionCheck = now + 2_000_000_000L;
        saveSession(manager);
    }

    /** Also called before extension shutdown so exit cleanup cannot erase enabled states. */
    public synchronized boolean saveSession(ModuleManager manager) {
        if (!sessionReady || loading || manager == null) return false;
        JsonObject root = snapshot(manager, previousSession);
        root.addProperty("sessionVersion", 1);
        root.addProperty("profile", activeName);
        String json = root.toString();
        if (json.equals(lastSessionJson) && sessionFile.isFile()) return true;
        File parent = sessionFile.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) return false;
        File temporary = new File(parent, ".session.json.tmp");
        try {
            Files.write(temporary.toPath(), json.getBytes(StandardCharsets.UTF_8));
            replaceFile(temporary, sessionFile);
            previousSession = root;
            lastSessionJson = json;
            return true;
        } catch (IOException ignored) {
            return false;
        } finally {
            if (temporary.isFile()) temporary.delete();
        }
    }

    /** Applies a downloaded profile without creating or overwriting any local file. */
    public boolean loadOnline(String name, JsonObject root, ModuleManager manager, boolean withKeybinds, boolean withVisuals) {
        if (!OnlineConfigService.isProfile(root) || !apply(root, manager, withKeybinds, withVisuals)) return false;
        activeName = "online/" + name;
        if (Vibe.getInstance() != null && Vibe.getInstance().getStatistics() != null)
            Vibe.getInstance().getStatistics().recordConfigLoad();
        return true;
    }

    public boolean save(String name, ModuleManager manager) {
        if (loading || manager == null) return false;
        String safeName = normalizeName(name);
        if (safeName == null || !ensureDirectory()) {
            return false;
        }
        File output = getFile(safeName);
        boolean created = !output.isFile();
        JsonObject previous = readObject(output);
        JsonObject root = snapshot(manager, previous);
        root.addProperty("profile", safeName);
        String now = formatDate(System.currentTimeMillis());
        String author = configAuthor();
        String creator = getString(previous, "creator", author);
        root.addProperty("creator", creator);
        root.addProperty("createdAt", getString(previous, "createdAt", now));
        root.addProperty("savedAt", now);
        JsonArray authors = previous.has("authors") && previous.get("authors").isJsonArray()
                ? previous.getAsJsonArray("authors") : new JsonArray();
        boolean alreadyAuthor = false;
        for (JsonElement value : authors) {
            if (value.isJsonPrimitive() && author.equalsIgnoreCase(value.getAsString())) {
                alreadyAuthor = true;
                break;
            }
        }
        if (!alreadyAuthor) authors.add(new JsonPrimitive(author));
        root.add("authors", authors);
        File temporary = new File(directory, "." + safeName + ".json.tmp");
        try {
            Writer writer = new OutputStreamWriter(new FileOutputStream(temporary), StandardCharsets.UTF_8);
            try {
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
            } finally {
                writer.close();
            }
            replaceFile(temporary, output);
            activeName = safeName;
            rememberActiveName();
            if (created && Vibe.getInstance() != null && Vibe.getInstance().getStatistics() != null) Vibe.getInstance().getStatistics().recordConfigCreated();
            return true;
        } catch (IOException ignored) {
            return false;
        } finally {
            if (temporary.isFile()) temporary.delete();
        }
    }

    /** Identical typed state for manual profiles and automatic session recovery. */
    private JsonObject snapshot(ModuleManager manager, JsonObject previous) {
        JsonObject root = new JsonObject();
        // Version 3 distinguishes deliberate selections from the former stock ArrayList list.
        root.addProperty("format", FORMAT_VERSION);
        root.addProperty("toggleFeedbackVersion", 1);
        if (Vibe.getInstance() != null && Vibe.getInstance().getHudManager() != null)
            root.add("hud", Vibe.getInstance().getHudManager().toJson());
        JsonArray modules = new JsonArray();
        for (Module module : manager.getModules()) {
            JsonObject data = new JsonObject();
            data.addProperty("id", module.getId());
            data.addProperty("enabled", module.isEnabled());
            data.addProperty("key", module.getKey());
            JsonObject settings = new JsonObject();
            for (Setting<?> setting : module.getSettings()) {
                settings.add(setting.getRawName(), writeSetting(setting));
            }
            data.add("settings", settings);
            modules.add(data);
        }
        // Removing an optional extension must not erase its settings/keybinds from the profile.
        if (previous.has("modules") && previous.get("modules").isJsonArray()) {
            java.util.Set<String> written = new java.util.HashSet<String>();
            for (Module module : manager.getModules()) written.add(module.getId());
            for (JsonElement entry : previous.getAsJsonArray("modules")) {
                if (!entry.isJsonObject()) continue;
                JsonObject data = entry.getAsJsonObject();
                String id = getString(data, "id", "");
                if (!id.isEmpty() && findModule(manager, id) == null
                        && written.add(id.toLowerCase(java.util.Locale.ROOT))) modules.add(data);
            }
        }
        root.add("modules", modules);
        return root;
    }

    public boolean delete(String name) {
        String safeName = normalizeName(name);
        if (safeName == null) return false;
        File file = getFile(safeName);
        boolean deleted = file.isFile() && file.delete();
        if (deleted && Vibe.getInstance() != null && Vibe.getInstance().getStatistics() != null) Vibe.getInstance().getStatistics().recordConfigDeleted();
        if (deleted && safeName != null && safeName.equalsIgnoreCase(activeName)) {
            activeName = DEFAULT_PROFILE;
            rememberActiveName();
        }
        return deleted;
    }

    public boolean rename(String from, String to) {
        String safeFrom = normalizeName(from);
        String safeTo = normalizeName(to);
        if (safeFrom == null || safeTo == null) {
            return false;
        }
        File source = getFile(safeFrom);
        File target = getFile(safeTo);
        if (!source.isFile() || target.exists() || !ensureDirectory()) {
            return false;
        }
        boolean renamed = source.renameTo(target);
        if (renamed && activeName.equals(safeFrom)) {
            activeName = safeTo;
            rememberActiveName();
        }
        return renamed;
    }

    public List<String> list() {
        if (!directory.isDirectory()) {
            return Collections.emptyList();
        }
        File[] files = directory.listFiles();
        if (files == null) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<String>();
        for (File file : files) {
            String fileName = file.getName();
            if (file.isFile() && fileName.endsWith(".json")) {
                names.add(fileName.substring(0, fileName.length() - 5));
            }
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public boolean openDirectory() {
        if (!ensureDirectory()) {
            return false;
        }
        File target;
        try {
            target = directory.getCanonicalFile();
        } catch (IOException ignored) {
            target = directory.getAbsoluteFile();
        }

        // Prism and some JDK distributions report Desktop support, but do
        // not actually hand a directory off to Explorer.  Prefer Explorer on
        // Windows so `.config openFolder` always opens the Vibe profiles
        // directory rather than depending on the Java desktop integration.
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            try {
                String windowsDirectory = System.getenv("WINDIR");
                File explorer = windowsDirectory == null || windowsDirectory.trim().isEmpty()
                        ? null : new File(windowsDirectory, "explorer.exe");
                String executable = explorer != null && explorer.isFile() ? explorer.getAbsolutePath() : "explorer.exe";
                new ProcessBuilder(executable, target.getAbsolutePath()).start();
                return true;
            } catch (Exception ignored) {
                // Fall through to the Desktop API and, finally, cmd/start.
            }
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                // OPEN is the cross-platform Desktop action intended for a
                // directory.  It avoids relying on the current working
                // directory or a shell-specific command.
                Desktop.getDesktop().open(target);
                return true;
            }
        } catch (Exception ignored) {
        }
        // Minecraft 1.8.9 is Windows-first in practice.  The Desktop API can
        // be unavailable on stripped-down JRE installs even though Explorer
        // itself is available, so retain a narrow, platform-specific fallback.
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            try {
                // `start` is available even when explorer.exe is not on the
                // inherited PATH (a common launcher/JRE edge case).
                new ProcessBuilder("cmd.exe", "/c", "start", "\"\"", target.getAbsolutePath()).start();
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    /** Read-only metadata used by the Config Editor without loading a profile. */
    public ConfigInfo getInfo(String name) {
        String safe = normalizeName(name);
        File file = getFile(safe);
        JsonObject root = readObject(file);
        List<String> authors = new ArrayList<String>();
        if (root.has("authors") && root.get("authors").isJsonArray()) {
            for (JsonElement author : root.getAsJsonArray("authors")) {
                if (author.isJsonPrimitive()) authors.add(author.getAsString());
            }
        }
        String fallback = file.isFile() ? formatDate(file.lastModified()) : "Unknown";
        return new ConfigInfo(safe == null ? "default" : safe,
                getString(root, "creator", "Unknown"), authors,
                getString(root, "createdAt", fallback), getString(root, "savedAt", fallback));
    }

    public File getDirectory() {
        return directory;
    }

    public String getActiveName() {
        return activeName;
    }

    public boolean isLoading() {
        return loading;
    }

    private JsonElement writeSetting(Setting<?> setting) {
        if (setting instanceof ColorSetting) {
            return new JsonPrimitive(((ColorSetting) setting).getHex());
        }
        if (setting instanceof MultiSelectSetting) {
            JsonArray values = new JsonArray();
            for (String value : ((MultiSelectSetting) setting).getValue()) {
                values.add(new JsonPrimitive(value));
            }
            return values;
        }
        if (setting instanceof RangeSetting) {
            RangeSetting range = (RangeSetting) setting;
            JsonObject value = new JsonObject();
            value.addProperty("min", range.getMin());
            value.addProperty("max", range.getMax());
            return value;
        }
        return new JsonPrimitive(String.valueOf(setting.getValue()));
    }

    private void readSetting(Setting<?> setting, JsonElement element) {
        try {
            if (setting instanceof BooleanSetting) {
                ((BooleanSetting) setting).setEnabled(element.getAsBoolean());
            } else if (setting instanceof NumberSetting) {
                ((NumberSetting) setting).setValue(element.getAsDouble());
            } else if (setting instanceof RangeSetting && element.isJsonObject()) {
                JsonObject range = element.getAsJsonObject();
                ((RangeSetting) setting).setRange(range.get("min").getAsDouble(), range.get("max").getAsDouble());
            } else if (setting instanceof ColorSetting) {
                ColorSetting color = (ColorSetting) setting;
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    color.setHex(element.getAsString());
                } else {
                    color.setValue(element.getAsInt());
                }
            } else if (setting instanceof ModeSetting) {
                ((ModeSetting) setting).setValue(element.getAsString());
            } else if (setting instanceof StringSetting) {
                ((StringSetting) setting).setValue(element.getAsString());
            } else if (setting instanceof MultiSelectSetting && element.isJsonArray()) {
                Set<String> values = new LinkedHashSet<String>();
                for (JsonElement value : element.getAsJsonArray()) {
                    values.add(value.getAsString());
                }
                ((MultiSelectSetting) setting).setValue(values);
            } else if (setting instanceof MultiSelectSetting && element.isJsonPrimitive()) {
                // Profiles written before Velocity became a multi-select
                // stored Mode as one string. Preserve that selection while
                // accepting the new JSON array format going forward.
                Set<String> values = new LinkedHashSet<String>();
                values.add(element.getAsString());
                ((MultiSelectSetting) setting).setValue(values);
            }
        } catch (Exception ignored) {
        }
    }

    private Module findModule(ModuleManager manager, String id) {
        // v0.0.6 renamed BHop to Speed. Keep old profiles and keybinds alive
        // rather than silently discarding the module record.
        if ("bhop".equalsIgnoreCase(id)) id = "speed";
        for (Module module : manager.getModules()) {
            if (module.getId().equalsIgnoreCase(id) || module.getRawName().equalsIgnoreCase(id)) {
                return module;
            }
        }
        return null;
    }

    private JsonArray getArray(JsonObject object, String name) {
        return object.has(name) && object.get(name).isJsonArray() ? object.getAsJsonArray(name) : new JsonArray();
    }

    private String getString(JsonObject object, String name, String fallback) {
        try {
            return object.has(name) ? object.get(name).getAsString() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int getInt(JsonObject object, String name, int fallback) {
        try {
            return object.has(name) ? object.get(name).getAsInt() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private boolean getBoolean(JsonObject object, String name, boolean fallback) {
        try {
            return object.has(name) ? object.get(name).getAsBoolean() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private boolean ensureDirectory() {
        return directory.isDirectory() || directory.mkdirs();
    }

    /** Applies the supplied first-run values without creating a config file. */
    private void loadCompiledDefaults(ModuleManager manager) {
        apply(DefaultModuleProfile.read(), manager, true, true);
        activeName = DEFAULT_PROFILE;
    }

    /** Shared reader for saved profiles and the compiled first-run profile. */
    private boolean apply(JsonObject root, ModuleManager manager, boolean withKeybinds, boolean withVisuals) {
        if (root == null || manager == null) return false;
        // Suppress editor/action callbacks while applying settings. Loading a
        // profile never writes its contents back to disk.
        loading = true;
        try {
            if (withVisuals && root.has("hud") && root.get("hud").isJsonObject() && Vibe.getInstance().getHudManager() != null) {
                Vibe.getInstance().getHudManager().fromJson(root.getAsJsonObject("hud"));
            }
            JsonArray modules = getArray(root, "modules");
            EspConfigMigration.migrate(modules);
            ScaffoldConfigMigration.migrate(modules);
            OofArrowsConfigMigration.migrate(modules);
            for (JsonElement element : modules) {
                if (!element.isJsonObject()) continue;
                JsonObject data = element.getAsJsonObject();
                String savedId = getString(data, "id", "");
                boolean legacyBhop = "bhop".equalsIgnoreCase(savedId);
                Module module = findModule(manager, savedId);
                if (module == null) continue;
                if (withKeybinds && data.has("key")) module.setKey(getInt(data, "key", module.getKey()));
                if (!withVisuals && (module.getCategory() == dev.vibe.module.Category.VISUAL
                        || module instanceof HudModule || module instanceof dev.vibe.module.impl.client.ClickGuiModule)) continue;
                JsonObject settings = data.has("settings") && data.get("settings").isJsonObject()
                        ? data.getAsJsonObject("settings") : new JsonObject();
                for (Setting<?> setting : module.getSettings()) {
                    JsonElement savedSetting = settings.get(setting.getRawName());
                    if (module instanceof dev.vibe.module.impl.visual.FogModule) {
                        dev.vibe.module.impl.visual.FogModule fog = (dev.vibe.module.impl.visual.FogModule) module;
                        if (setting == fog.color && savedSetting == null) savedSetting = settings.get("Fog Color");
                        if (setting == fog.tint && savedSetting != null && savedSetting.isJsonPrimitive()
                                && "Rainbow".equalsIgnoreCase(savedSetting.getAsString())) savedSetting = new JsonPrimitive("Love");
                    }
                    // Velocity used the singular Mode key briefly before its
                    // multi-select setting returned to the original Modes
                    // name. Accept both spellings during profile migration.
                    if (savedSetting == null && setting instanceof MultiSelectSetting
                            && "Modes".equals(setting.getRawName())) savedSetting = settings.get("Mode");
                    if (savedSetting == null) continue;
                    // The former BHop Custom mode was removed. Keep its
                    // profile selectable by mapping it to Speed's Custom
                    // branch instead of restoring a second hop mode.
                    if (legacyBhop && ("Mode".equals(setting.getRawName()) || "Modes".equals(setting.getRawName()))
                            && savedSetting.isJsonPrimitive()
                            && ("BHop Custom".equalsIgnoreCase(savedSetting.getAsString())
                                || "Custom".equalsIgnoreCase(savedSetting.getAsString()))) {
                        if (setting instanceof MultiSelectSetting) {
                            Set<String> migrated = new LinkedHashSet<String>();
                            migrated.add("Custom");
                            ((MultiSelectSetting) setting).setValue(migrated);
                        } else {
                            ((ModeSetting) setting).setValue("Custom");
                        }
                    } else readSetting(setting, savedSetting);
                }
                if (module instanceof dev.vibe.module.impl.visual.FogModule && settings.has("Fog Color") && !settings.has("Secondary Color")) {
                    dev.vibe.module.impl.visual.FogModule fog = (dev.vibe.module.impl.visual.FogModule) module;
                    fog.secondaryColor.setValue(fog.color.getArgb());
                }
                if (module instanceof HudModule) {
                    if (getInt(root, "format", 1) < 3) ((HudModule) module).migrateLegacyArrayListModules();
                    // Opt older profiles into the new widget once; saved off choices remain off.
                    if (!root.has("toggleFeedbackVersion")) {
                        Set<String> elements = ((HudModule) module).getHudElements().copyValue();
                        elements.add("notifications"); ((HudModule) module).getHudElements().setValue(elements);
                    }
                }
                if (data.has("enabled")) module.setEnabled(getBoolean(data, "enabled", module.isEnabled()));
            }
            return true;
        } finally {
            loading = false;
        }
    }

    /** Reads the last successfully loaded or saved Vibe profile. */
    private String readActiveName() {
        if (!activeProfileFile.isFile()) return DEFAULT_PROFILE;
        try {
            Reader reader = new InputStreamReader(new FileInputStream(activeProfileFile), StandardCharsets.UTF_8);
            try {
                char[] buffer = new char[64];
                int count = reader.read(buffer);
                String name = count <= 0 ? null : new String(buffer, 0, count).trim();
                String safe = normalizeName(name);
                return safe == null ? DEFAULT_PROFILE : safe;
            } finally {
                reader.close();
            }
        } catch (IOException ignored) {
            return DEFAULT_PROFILE;
        }
    }

    /** Saves the selected profile independently of its contents. */
    private void rememberActiveName() {
        File parent = activeProfileFile.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) return;
        File temporary = new File(parent, ".active-profile.txt.tmp");
        try {
            Writer writer = new OutputStreamWriter(new FileOutputStream(temporary), StandardCharsets.UTF_8);
            try {
                writer.write(activeName == null ? DEFAULT_PROFILE : activeName);
                writer.write('\n');
            } finally {
                writer.close();
            }
            replaceFile(temporary, activeProfileFile);
        } catch (IOException ignored) {
            // A profile itself remains valid if its small selector marker
            // cannot be updated; the next launch safely falls back to default.
        } finally {
            if (temporary.isFile()) temporary.delete();
        }
    }

    /** Replaces a profile only after its full JSON has been written. */
    private void replaceFile(File temporary, File target) throws IOException {
        try {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private JsonObject readObject(File file) {
        if (file == null || !file.isFile()) return new JsonObject();
        try {
            Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            try {
                JsonElement element = new JsonParser().parse(reader);
                return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
            } finally {
                reader.close();
            }
        } catch (Exception ignored) {
            return new JsonObject();
        }
    }

    private String configAuthor() {
        NameProtectModule protect = Vibe.getInstance() == null || Vibe.getInstance().getModuleManager() == null
                ? null : Vibe.getInstance().getModuleManager().getModule(NameProtectModule.class);
        if (protect != null && protect.isConfigured()) return Vibe.getInstance().getIdentity().getGamertag();
        if (Vibe.getInstance() != null && Vibe.getInstance().getMinecraft().getSession() != null
                && Vibe.getInstance().getMinecraft().getSession().getUsername() != null) {
            return Vibe.getInstance().getMinecraft().getSession().getUsername();
        }
        return "Unknown";
    }

    private String formatDate(long time) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT).format(new Date(time));
    }

    public static final class ConfigInfo {
        private final String name;
        private final String creator;
        private final List<String> authors;
        private final String createdAt;
        private final String savedAt;

        private ConfigInfo(String name, String creator, List<String> authors, String createdAt, String savedAt) {
            this.name = name;
            this.creator = creator;
            this.authors = Collections.unmodifiableList(new ArrayList<String>(authors));
            this.createdAt = createdAt;
            this.savedAt = savedAt;
        }
        public String getName() { return name; }
        public String getCreator() { return creator; }
        public List<String> getAuthors() { return authors; }
        public String getCreatedAt() { return createdAt; }
        public String getSavedAt() { return savedAt; }
    }

    private File getFile(String name) {
        String safeName = normalizeName(name);
        return new File(directory, (safeName == null ? "default" : safeName) + ".json");
    }

    private String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        String safe = name.trim().replaceAll("[^A-Za-z0-9._-]", "");
        return safe.isEmpty() || safe.length() > 48 ? null : safe;
    }
}
