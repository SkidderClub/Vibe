package dev.vibe.cosmetic;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vibe.friend.FriendManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/** Persistent, local selection of real Cosmetica catalog entries. */
public final class CosmeticPresetManager {
    private final File file;
    private final List<CosmeticPreset> presets = new ArrayList<CosmeticPreset>();
    private String selectedId;

    public CosmeticPresetManager(File minecraftConfigDirectory) {
        file = new File(new File(minecraftConfigDirectory, "vibe"), "cosmetics.json");
        load();
        if (presets.isEmpty()) {
            CosmeticPreset preset = new CosmeticPreset(UUID.randomUUID().toString(), "Default");
            presets.add(preset); selectedId = preset.getId(); save();
        }
        if (getSelected() == null) selectedId = presets.get(0).getId();
    }

    public synchronized List<CosmeticPreset> getPresets() { return Collections.unmodifiableList(new ArrayList<CosmeticPreset>(presets)); }
    public synchronized CosmeticPreset getSelected() { return findById(selectedId); }
    public synchronized String getSelectedId() { return selectedId; }
    public synchronized void select(String id) { if (findById(id) != null) { selectedId = id; save(); } }
    public synchronized CosmeticPreset create(String name) { CosmeticPreset preset = new CosmeticPreset(UUID.randomUUID().toString(), uniqueName(name)); presets.add(preset); selectedId = preset.getId(); save(); return preset; }
    public synchronized boolean delete(String id) {
        if (presets.size() <= 1) return false;
        CosmeticPreset preset = findById(id); if (preset == null) return false;
        presets.remove(preset); if (preset.getId().equals(selectedId)) selectedId = presets.get(0).getId(); save(); return true;
    }
    public synchronized CosmeticPreset findById(String id) { if (id == null) return null; for (CosmeticPreset preset : presets) if (id.equals(preset.getId())) return preset; return null; }
    /** None is the first choice; both directions include every saved profile. */
    public synchronized String cycleId(String current, boolean backwards) {
        int index = 0;
        for (int i = 0; i < presets.size(); i++) if (presets.get(i).getId().equals(current)) index = i + 1;
        int next = (index + (backwards ? -1 : 1) + presets.size() + 1) % (presets.size() + 1);
        return next == 0 ? "" : presets.get(next - 1).getId();
    }

    public synchronized CosmeticPreset resolveFriend(String name, FriendManager friends) {
        FriendManager.Friend friend = friends == null ? null : friends.findPlayer(name);
        return friend == null ? null : findById(friend.getCosmeticPreset());
    }
    public synchronized CosmeticPreset resolve(EntityPlayer player, FriendManager friends) {
        if (player == null) return null;
        if (player == Minecraft.getMinecraft().thePlayer) return getSelected();
        return resolveFriend(player.getGameProfile().getName(), friends);
    }

    public synchronized void save() {
        File parent = file.getParentFile(); if (!parent.isDirectory() && !parent.mkdirs()) return;
        try {
            JsonObject root = new JsonObject(); root.addProperty("selected", selectedId == null ? "" : selectedId);
            JsonArray items = new JsonArray(); for (CosmeticPreset preset : presets) items.add(toJson(preset)); root.add("presets", items);
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
            try { new GsonBuilder().setPrettyPrinting().create().toJson(root, writer); } finally { writer.close(); }
        } catch (Exception ignored) { }
    }

    private void load() {
        if (!file.isFile()) return;
        try {
            InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8); JsonElement parsed;
            try { parsed = new JsonParser().parse(reader); } finally { reader.close(); }
            if (!parsed.isJsonObject()) return;
            JsonObject root = parsed.getAsJsonObject(); selectedId = string(root, "selected", null);
            if (!root.has("presets") || !root.get("presets").isJsonArray()) return;
            for (JsonElement item : root.getAsJsonArray("presets")) if (item.isJsonObject()) {
                CosmeticPreset preset = fromJson(item.getAsJsonObject());
                if (preset != null && findById(preset.getId()) == null) presets.add(preset);
            }
        } catch (Exception ignored) { }
    }

    private CosmeticPreset fromJson(JsonObject object) {
        String id = string(object, "id", null); if (id == null || id.trim().isEmpty()) return null;
        CosmeticPreset preset = new CosmeticPreset(id, string(object, "name", "Preset"));
        preset.setSkinName(string(object, "skin", "xHeist_")); preset.setOnlyThirdPerson(bool(object, "onlyThirdPerson"));
        if (object.has("accessories") && object.get("accessories").isJsonArray()) {
            for (JsonElement item : object.getAsJsonArray("accessories")) if (item.isJsonObject()) {
                CosmeticaAccessory accessory = CosmeticaAccessory.fromJson(item.getAsJsonObject()); if (accessory != null) preset.equip(accessory);
            }
        }
        return preset;
    }

    private JsonObject toJson(CosmeticPreset preset) {
        JsonObject object = new JsonObject(); object.addProperty("id", preset.getId()); object.addProperty("name", preset.getName()); object.addProperty("skin", preset.getSkinName()); object.addProperty("onlyThirdPerson", preset.isOnlyThirdPerson());
        JsonArray accessories = new JsonArray(); for (CosmeticaAccessory accessory : preset.getAccessories()) accessories.add(accessory.toJson()); object.add("accessories", accessories); return object;
    }
    private String uniqueName(String value) { String clean = value == null || value.trim().isEmpty() ? "New preset" : value.trim(); String candidate = clean; int suffix = 2; while (containsName(candidate)) candidate = clean + " " + suffix++; return candidate; }
    private boolean containsName(String value) { for (CosmeticPreset preset : presets) if (preset.getName().equalsIgnoreCase(value)) return true; return false; }
    private static String string(JsonObject object, String key, String fallback) { try { return object.has(key) ? object.get(key).getAsString() : fallback; } catch (Exception ignored) { return fallback; } }
    private static boolean bool(JsonObject object, String key) { try { return object.has(key) && object.get(key).getAsBoolean(); } catch (Exception ignored) { return false; } }
}
