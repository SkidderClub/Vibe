package dev.vibe.config;

import com.google.gson.*;

/** Keep existing hotbar preferences when loading profiles from the separate switchers. */
final class ItemSwitchConfigMigration {
    static void migrate(JsonArray modules){
        for(JsonElement entry:modules){
            if(!entry.isJsonObject())continue;JsonObject module=entry.getAsJsonObject();
            if(!module.has("id")||!module.has("settings")||!module.get("settings").isJsonObject())continue;
            String id=module.get("id").getAsString();JsonObject settings=module.getAsJsonObject("settings");
            if(id.equalsIgnoreCase("autotool")||id.equalsIgnoreCase("pickenswitch"))copy(settings,"Mode","Item Switching");
            if(id.equalsIgnoreCase("scaffold")&&!settings.has("Item Switching")&&settings.has("Spoof Slot")){
                try{settings.addProperty("Item Switching",settings.get("Spoof Slot").getAsBoolean()?"Silent":"Basic");}catch(RuntimeException ignored){}
            }
            if(id.equalsIgnoreCase("hypixel"))copy(settings,"Silent murderer","Auto Weapon");
            copy(settings,"Silent Slot Color","Slot Highlight Color");
        }
    }
    private static void copy(JsonObject settings,String old,String name){if(!settings.has(name)&&settings.has(old))settings.add(name,settings.get(old));}
}
