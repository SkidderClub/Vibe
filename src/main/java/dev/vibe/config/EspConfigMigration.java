package dev.vibe.config;

import com.google.gson.*;

/** Upgrades old ESP/Skeletal profiles before the normal typed-setting reader runs. */
final class EspConfigMigration {
    private EspConfigMigration() { }
    static void migrate(JsonArray modules) {
        JsonObject esp=null,skeletal=null;
        for(JsonElement element:modules)if(element.isJsonObject()) {
            JsonObject module=element.getAsJsonObject();
            String id=module.has("id")?module.get("id").getAsString():"";
            if("esp".equalsIgnoreCase(id))esp=module;
            if("skeletal".equalsIgnoreCase(id))skeletal=module;
        }
        if(esp==null&&skeletal!=null) {esp=new JsonObject();esp.addProperty("id","esp");modules.add(esp);}
        if(esp==null)return;
        JsonObject settings=settings(esp);
        if(!settings.has("2D Box Enabled")) {
            copy(settings,settings,"Names","2D Name Enabled");
            copy(settings,settings,"Health Bar","2D Health Bar Enabled");
            copy(settings,settings,"Held Item","2D Item Name Enabled");
            copy(settings,settings,"Distance","2D Distance Enabled");
            copy(settings,settings,"Health Bar Width","2D Health Bar Width");
            copy(settings,settings,"Health Start Color","2D Health Bar Color Static");
            copy(settings,settings,"Outline Color","2D Box Color Static");
            copy(settings,settings,"Fill Color","2D Box Background");
            copy(settings,settings,"Depth Backplate","2D Box Outline");
            String[][] positions={{"Name Position","Name"},{"Health Bar Position","Health Bar"},{"Distance Position","Distance"},{"Held Item Position","Item Name"}};
            for(String[] pair:positions)if(settings.has(pair[0])) {
                String side=settings.get(pair[0]).getAsString();
                if("Name".equals(side))side="Top";
                if(!"Health Bar".equals(pair[1])&&("Left".equals(side)||"Right".equals(side)))side+=" Up";
                settings.addProperty("2D "+pair[1]+" Position",side);
            }
            settings.addProperty("2D Box Enabled",true);
        }
        if(skeletal!=null&&!settings.has("Skeletal Color")) {
            JsonObject source=settings(skeletal);
            for(String name:new String[]{"Color","Rainbow","Line Width","Only Targets","Through Walls","Depth Backplate"})copy(source,settings,name,"Skeletal "+name);
            if(skeletal.has("enabled")&&skeletal.get("enabled").getAsBoolean()) {
                JsonArray modes=settings.has("ESP Modes")&&settings.get("ESP Modes").isJsonArray()?settings.getAsJsonArray("ESP Modes"):new JsonArray();
                boolean found=false;for(JsonElement mode:modes)if("Skeletal".equals(mode.getAsString()))found=true;
                if(!found)modes.add(new JsonPrimitive("Skeletal"));settings.add("ESP Modes",modes);esp.addProperty("enabled",true);
            }
        }
    }
    private static JsonObject settings(JsonObject module) {
        if(!module.has("settings")||!module.get("settings").isJsonObject())module.add("settings",new JsonObject());
        return module.getAsJsonObject("settings");
    }
    private static void copy(JsonObject from,JsonObject to,String oldName,String name) {if(from.has(oldName)&&!to.has(name))to.add(name,from.get(oldName));}
}
