package dev.vibe.friend;

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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;

/** Profile-independent friend roster shared by combat, ESP and Friend Editor. */
public final class FriendManager {
    private final File file;
    private final List<Friend> friends = new ArrayList<Friend>();

    public FriendManager(File minecraftConfigDirectory) {
        file = new File(new File(minecraftConfigDirectory, "vibe"), "friends.json");
        load();
    }

    public synchronized boolean add(String name, String alias) {
        String clean = clean(name);
        if (clean == null || find(clean) != null) return false;
        friends.add(new Friend(clean, clean(alias) == null ? clean : clean(alias), System.currentTimeMillis()));
        sort(); save(); return true;
    }

    public synchronized boolean remove(String name) {
        Friend entry = find(name);
        if (entry == null) return false;
        friends.remove(entry); save(); return true;
    }

    /** Changes the display alias while retaining the real in-game identifier. */
    public synchronized boolean rename(String name, String alias) {
        Friend entry = find(name);
        String cleanAlias = clean(alias);
        if (entry == null || cleanAlias == null) return false;
        entry.alias = cleanAlias; save(); return true;
    }

    /** Assigns a locally-rendered Vibe cosmetic preset to this friend. */
    public synchronized boolean setCosmeticPreset(String name, String presetId) {
        Friend entry = find(name);
        if (entry == null) return false;
        entry.cosmeticPreset = presetId == null ? "" : presetId;
        save();
        return true;
    }

    public synchronized boolean isFriend(EntityPlayer player) { return player != null && isFriend(player.getName()); }
    public synchronized boolean isFriend(String name) { return find(name) != null; }
    /** Rendering must resolve the real profile, never another friend's display alias. */
    public synchronized Friend findPlayer(String name) {
        if (name == null) return null;
        for (Friend friend : friends) if (friend.name.equalsIgnoreCase(name)) return friend;
        return null;
    }
    public synchronized Friend find(String name) {
        if (name == null) return null;
        for (Friend friend : friends) if (friend.name.equalsIgnoreCase(name) || friend.alias.equalsIgnoreCase(name)) return friend;
        return null;
    }
    public synchronized List<Friend> getFriends() { return Collections.unmodifiableList(new ArrayList<Friend>(friends)); }

    private void sort() { Collections.sort(friends, new Comparator<Friend>() { @Override public int compare(Friend a, Friend b) { return a.name.compareToIgnoreCase(b.name); }}); }
    private String clean(String value) { if (value == null) return null; String result = value.trim(); return result.matches("[A-Za-z0-9_]{1,16}") ? result : null; }

    private void load() {
        if (!file.isFile()) return;
        try {
            InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            JsonElement element;
            try { element = new JsonParser().parse(reader); } finally { reader.close(); }
            if (!element.isJsonObject()) return;
            JsonArray data = element.getAsJsonObject().has("friends") ? element.getAsJsonObject().getAsJsonArray("friends") : new JsonArray();
            for (JsonElement item : data) if (item.isJsonObject()) {
                JsonObject object = item.getAsJsonObject();
                String name = clean(object.has("name") ? object.get("name").getAsString() : null);
                if (name == null || find(name) != null) continue;
                String alias = clean(object.has("alias") ? object.get("alias").getAsString() : null);
                long added = object.has("added") ? object.get("added").getAsLong() : System.currentTimeMillis();
                String preset = object.has("cosmeticPreset") ? object.get("cosmeticPreset").getAsString() : "";
                friends.add(new Friend(name, alias == null ? name : alias, added, preset));
            }
            sort();
        } catch (Exception ignored) { }
    }

    private void save() {
        File parent = file.getParentFile(); if (!parent.isDirectory() && !parent.mkdirs()) return;
        try {
            JsonArray data = new JsonArray();
            for (Friend friend : friends) { JsonObject object = new JsonObject(); object.addProperty("name", friend.name); object.addProperty("alias", friend.alias); object.addProperty("added", friend.added); object.addProperty("cosmeticPreset", friend.cosmeticPreset); data.add(object); }
            JsonObject root = new JsonObject(); root.add("friends", data);
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
            try { new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root, writer); } finally { writer.close(); }
        } catch (Exception ignored) { }
    }

    public static final class Friend {
        private final String name;
        private String alias;
        private final long added;
        private String cosmeticPreset;
        private Friend(String name, String alias, long added) { this(name, alias, added, ""); }
        private Friend(String name, String alias, long added, String cosmeticPreset) { this.name = name; this.alias = alias; this.added = added; this.cosmeticPreset = cosmeticPreset == null ? "" : cosmeticPreset; }
        public String getName() { return name; }
        public String getAlias() { return alias; }
        public long getAdded() { return added; }
        public String getCosmeticPreset() { return cosmeticPreset; }
        public String getAddedDate() { return new SimpleDateFormat("yyyy-MM-dd").format(new Date(added)); }
    }
}
