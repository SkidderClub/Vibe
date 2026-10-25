package dev.vibe.config;

import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class OofArrowsConfigMigrationTest {
    @Test public void previousFiltersKeepTheirMeaningAndNewChoicesWin() {
        JsonArray modules=new JsonParser().parse("[{id:'oofarrows',settings:{'Invisible Only':true,'Occluded Only':true}},"
                +"{id:'oofarrows',settings:{'Invisible Only':false,'Occluded Only':false}},"
                +"{id:'oofarrows',settings:{'Invisible Only':true,'Occluded Only':true,Invisibility:'Exclude',Visibility:'Visible only'}}]").getAsJsonArray();
        OofArrowsConfigMigration.migrate(modules);
        JsonObject first=modules.get(0).getAsJsonObject().getAsJsonObject("settings"),second=modules.get(1).getAsJsonObject().getAsJsonObject("settings"),third=modules.get(2).getAsJsonObject().getAsJsonObject("settings");
        assertEquals("Only",first.get("Invisibility").getAsString());assertEquals("Occluded only",first.get("Visibility").getAsString());
        assertEquals("Include",second.get("Invisibility").getAsString());assertEquals("All",second.get("Visibility").getAsString());
        assertEquals("Exclude",third.get("Invisibility").getAsString());assertEquals("Visible only",third.get("Visibility").getAsString());
        String once=modules.toString();OofArrowsConfigMigration.migrate(modules);assertEquals(once,modules.toString());
    }
    @Test public void unrelatedOrMalformedSettingsAreNotModified() {
        JsonArray modules=new JsonParser().parse("[{id:'esp',settings:{'Invisible Only':true}},{id:'oofarrows'},"
                +"{id:'oofarrows',settings:{'Invisible Only':null,'Occluded Only':'invalid'}},null]").getAsJsonArray();
        String before=modules.toString();OofArrowsConfigMigration.migrate(modules);assertEquals(before,modules.toString());
    }
}
