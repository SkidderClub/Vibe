package dev.vibe.config;

import com.google.gson.*;
import java.util.Map;

/** Merge standalone Fog into Ambience without enabling unwanted time/weather changes. */
final class AmbienceConfigMigration {
    private AmbienceConfigMigration(){}
    static void migrate(JsonArray modules){
        migrateFog(modules);
        migrateWeather(modules);
    }
    private static void migrateFog(JsonArray modules){
        JsonObject fog=null,ambience=null;
        for(JsonElement value:modules)if(value.isJsonObject()){
            JsonObject entry=value.getAsJsonObject();String id=text(entry,"id");
            if("fog".equalsIgnoreCase(id))fog=entry;
            if("ambience".equalsIgnoreCase(id))ambience=entry;
        }
        if(fog==null)return;
        if(ambience==null){ambience=new JsonObject();ambience.addProperty("id","ambience");ambience.addProperty("enabled",false);modules.add(ambience);}
        JsonObject settings=object(ambience,"settings");
        // An explicit modern selection is authoritative, even in a mixed old profile.
        if(settings.has("Effects"))return;
        boolean ambienceOn=enabled(ambience),fogOn=enabled(fog);
        JsonArray effects=new JsonArray();
        if(ambienceOn){effects.add(new JsonPrimitive("Time"));effects.add(new JsonPrimitive("Weather"));}
        if(fogOn)effects.add(new JsonPrimitive("Fog"));
        settings.add("Effects",effects);
        JsonObject old=object(fog,"settings");
        for(Map.Entry<String,JsonElement> entry:old.entrySet())settings.add(entry.getKey(),entry.getValue());
        if(!settings.has("Primary Color")&&settings.has("Fog Color"))settings.add("Primary Color",settings.get("Fog Color"));
        if(!settings.has("Secondary Color")&&settings.has("Fog Color"))settings.add("Secondary Color",settings.get("Fog Color"));
        if("Rainbow".equalsIgnoreCase(text(settings,"Color Mode")))settings.addProperty("Color Mode","Love");
        ambience.addProperty("enabled",ambienceOn||fogOn);
        if(!ambienceOn&&fogOn&&fog.has("key"))ambience.add("key",fog.get("key"));
    }
    private static void migrateWeather(JsonArray modules){
        for(JsonElement value:modules)if(value.isJsonObject()){
            JsonObject entry=value.getAsJsonObject();
            if(!"ambience".equalsIgnoreCase(text(entry,"id")))continue;
            JsonObject settings=object(entry,"settings");
            if(!"None".equalsIgnoreCase(text(settings,"Weather Mode")))continue;
            settings.addProperty("Weather Mode","Sun");
            JsonArray effects=new JsonArray();
            if(settings.has("Effects")&&settings.get("Effects").isJsonArray()){
                for(JsonElement effect:settings.getAsJsonArray("Effects"))
                    if(!effect.isJsonPrimitive()||!"Weather".equalsIgnoreCase(effect.getAsString()))effects.add(effect);
            }else effects.add(new JsonPrimitive("Time"));
            settings.add("Effects",effects);
        }
    }
    private static JsonObject object(JsonObject data,String key){if(data.has(key)&&data.get(key).isJsonObject())return data.getAsJsonObject(key);JsonObject o=new JsonObject();data.add(key,o);return o;}
    private static String text(JsonObject data,String key){try{return data.has(key)?data.get(key).getAsString():"";}catch(RuntimeException ignored){return "";}}
    private static boolean enabled(JsonObject data){try{return data.has("enabled")&&data.get("enabled").getAsBoolean();}catch(RuntimeException ignored){return false;}}
}
