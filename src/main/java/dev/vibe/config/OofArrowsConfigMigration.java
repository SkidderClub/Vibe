package dev.vibe.config;

import com.google.gson.*;

/** Keep the former two visibility switches when loading saved arrow settings. */
final class OofArrowsConfigMigration {
    private OofArrowsConfigMigration() { }
    static void migrate(JsonArray modules) {
        for(JsonElement entry:modules)if(entry.isJsonObject()) {
            JsonObject module=entry.getAsJsonObject();
            if(!module.has("id")||!module.get("id").isJsonPrimitive()||!"oofarrows".equalsIgnoreCase(module.get("id").getAsString())
                    ||!module.has("settings")||!module.get("settings").isJsonObject())continue;
            JsonObject s=module.getAsJsonObject("settings");
            copy(s,"Invisible Only","Invisibility","Only","Include");
            copy(s,"Occluded Only","Visibility","Occluded only","All");
        }
    }
    private static void copy(JsonObject settings,String oldName,String newName,String enabled,String disabled) {
        JsonElement old=settings.get(oldName);
        if(!settings.has(newName)&&old!=null&&old.isJsonPrimitive()&&old.getAsJsonPrimitive().isBoolean())
            settings.addProperty(newName,old.getAsBoolean()?enabled:disabled);
    }
}
