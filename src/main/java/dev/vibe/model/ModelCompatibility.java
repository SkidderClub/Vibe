package dev.vibe.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Tested per-model defaults; IDs distinguish identically named Sketchfab uploads. */
public final class ModelCompatibility {
    private static final Map<String, Profile> PROFILES = new HashMap<String, Profile>();
    private static final Set<String> EXCLUDED = new HashSet<String>();
    static {
        try (InputStream stream = ModelCompatibility.class.getResourceAsStream("/assets/vibe/models/compatibility.json")) {
            if (stream != null) {
                JsonObject root = new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("profiles").entrySet())
                    PROFILES.put(entry.getKey(), new Profile(entry.getValue().getAsJsonObject()));
                for (JsonElement entry : root.getAsJsonArray("excluded")) EXCLUDED.add(entry.getAsString());
            }
        } catch (Exception error) { throw new ExceptionInInitializerError(error); }
    }
    private ModelCompatibility() { }
    public static boolean excluded(String uid) { return EXCLUDED.contains(uid); }
    public static Profile profile(ModelCatalog.Entry entry) { return entry == null ? null : PROFILES.get(entry.uid); }
    public static final class Profile {
        public final boolean animate, fixPose, head, legs;
        public final int yaw;
        Profile(JsonObject json) {
            animate = json.get("animate").getAsBoolean(); fixPose = json.get("fixPose").getAsBoolean();
            head = json.get("head").getAsBoolean(); legs = json.get("legs").getAsBoolean(); yaw = json.get("yaw").getAsInt();
        }
    }
}
