package dev.vibe.launcher.game;

import dev.vibe.launcher.core.FileUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Public extension choices and imported JARs. */
public final class ExtensionLibrary {
    public static final class Entry {
        public final String id, name, description;
        public final ModLibrary.Mod installed;
        public final boolean bundled, enabled;

        Entry(String id, String name, String description, ModLibrary.Mod installed, boolean bundled, boolean enabled) {
            this.id = id; this.name = name; this.description = description;
            this.installed = installed; this.bundled = bundled; this.enabled = enabled;
        }
    }

    private final ModLibrary mods;
    private final Path choices;

    public ExtensionLibrary(ModLibrary mods, Path choices) { this.mods = mods; this.choices = choices; }

    public List<Entry> list(List<ModLibrary.Mod> installed) {
        Properties preferences = FileUtil.readProperties(choices);
        List<Entry> result = new ArrayList<Entry>();
        add(result, installed, preferences, "cosmetica", "Cosmetica", "Cosmetica accessories and editor. CustomCosmetics is included in Vibe.", true);
        add(result, installed, preferences, "example", "Example extension", "The Test module for extension developers.", true);
        for (ModLibrary.Mod mod : installed) {
            if (!mod.extension || isKnown(mod.extensionId)) continue;
            result.add(new Entry(mod.extensionId, mod.name, mod.description, mod, false,
                    Boolean.parseBoolean(preferences.getProperty(mod.extensionId, Boolean.toString(mod.enabled)))));
        }
        return result;
    }

    private static void add(List<Entry> result, List<ModLibrary.Mod> installed, Properties preferences,
                            String id, String name, String description, boolean bundled) {
        ModLibrary.Mod found = null;
        for (ModLibrary.Mod mod : installed) if (mod.extension && id.equals(mod.extensionId)) { found = mod; break; }
        boolean enabled = Boolean.parseBoolean(preferences.getProperty(id,
                Boolean.toString(found != null ? found.enabled : "cosmetica".equals(id))));
        if (!bundled && found == null) enabled = false;
        result.add(new Entry(id, found == null ? name : found.name, description, found, bundled, enabled));
    }

    private static boolean isKnown(String id) { return "cosmetica".equals(id) || "example".equals(id); }

    public void setEnabled(Entry entry, boolean enabled) throws IOException {
        setEnabled(entry, enabled, true);
    }

    /** While Minecraft builds/runs, record the choice without moving an open JAR. */
    public void setEnabled(Entry entry, boolean enabled, boolean applyFiles) throws IOException {
        if (!entry.bundled && entry.installed == null) throw new IOException("Import the extension JAR first.");
        Properties preferences = FileUtil.readProperties(choices);
        preferences.setProperty(entry.id, Boolean.toString(enabled));
        FileUtil.writeProperties(choices, preferences, "Vibe extension choices - applied on next launch");
        if (applyFiles && entry.installed != null) mods.setEnabled(entry.installed, enabled);
    }

    public void imported(List<String> ids) throws IOException {
        if (ids.isEmpty()) return;
        Properties preferences = FileUtil.readProperties(choices);
        for (String id : ids) preferences.remove(id);
        FileUtil.writeProperties(choices, preferences, "Vibe extension choices - applied on next launch");
    }

    public void delete(Entry entry) throws IOException {
        setEnabled(entry, false);
        if (entry.installed != null) {
            // setEnabled may have renamed the file; resolve its current location again.
            for (ModLibrary.Mod mod : mods.list()) if (entry.id.equals(mod.extensionId)) { mods.delete(mod); break; }
        }
    }
}
