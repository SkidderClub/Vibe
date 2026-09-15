package dev.vibe.target;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.entity.player.EntityPlayer;

/** Persistent priority target roster for combat modules. */
public final class TargetManager {
    private final File file;
    private final Set<String> targets = new LinkedHashSet<String>();
    public TargetManager(File minecraftConfigDirectory) { file = new File(new File(minecraftConfigDirectory, "vibe"), "targets.json"); load(); }
    public synchronized boolean add(String name) { String clean = clean(name); if (clean == null || !targets.add(clean)) return false; save(); return true; }
    public synchronized boolean remove(String name) { String found = match(name); if (found == null) return false; targets.remove(found); save(); return true; }
    public synchronized boolean isTarget(EntityPlayer player) { return player != null && isTarget(player.getName()); }
    public synchronized boolean isTarget(String name) { return match(name) != null; }
    public synchronized Set<String> getTargets() { return Collections.unmodifiableSet(new LinkedHashSet<String>(targets)); }
    private String match(String name) { if (name == null) return null; for (String target : targets) if (target.equalsIgnoreCase(name)) return target; return null; }
    private String clean(String value) { if (value == null) return null; String v = value.trim(); return v.matches("[A-Za-z0-9_]{1,16}") ? v : null; }
    private void load() { if (!file.isFile()) return; try { InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8); JsonElement value; try { value = new JsonParser().parse(reader); } finally { reader.close(); } if (!value.isJsonObject()) return; JsonArray list = value.getAsJsonObject().has("targets") ? value.getAsJsonObject().getAsJsonArray("targets") : new JsonArray(); for (JsonElement item : list) { String clean = clean(item.getAsString()); if (clean != null) targets.add(clean); } } catch (Exception ignored) {} }
    private void save() { File parent = file.getParentFile(); if (!parent.isDirectory() && !parent.mkdirs()) return; try { JsonArray list = new JsonArray(); for (String target : targets) list.add(new com.google.gson.JsonPrimitive(target)); JsonObject root = new JsonObject(); root.add("targets", list); OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8); try { new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root, writer); } finally { writer.close(); } } catch (Exception ignored) {} }
}
