package dev.vibe.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Maps Scaffold's renamed rotations in old profiles: Intave became Normal, Polar and God Bridge became GodBridge. */
final class ScaffoldConfigMigration {
    private ScaffoldConfigMigration() { }

    static void migrate(JsonArray modules) {
        for (JsonElement element : modules) {
            if (!element.isJsonObject()) continue;
            JsonObject module = element.getAsJsonObject();
            if (!module.has("id") || !"scaffold".equalsIgnoreCase(module.get("id").getAsString())
                    || !module.has("settings") || !module.get("settings").isJsonObject()) continue;
            JsonObject settings = module.getAsJsonObject("settings");
            JsonElement rotations = settings.get("Rotations");
            if (rotations == null || !rotations.isJsonPrimitive()) continue;
            String value = rotations.getAsString();
            if ("Intave".equalsIgnoreCase(value)) {
                settings.addProperty("Rotations", "Normal");
            } else if ("Polar".equalsIgnoreCase(value) || "God Bridge".equalsIgnoreCase(value)) {
                settings.addProperty("Rotations", "GodBridge");
            }
        }
    }
}
