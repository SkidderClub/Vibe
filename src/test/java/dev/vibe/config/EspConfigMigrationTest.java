package dev.vibe.config;

import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class EspConfigMigrationTest {
    @Test public void legacySkeletalAndEspMergeIndependentOfOrder() {
        JsonArray modules=new JsonParser().parse("[{id:'skeletal',enabled:true,settings:{Color:123,Rainbow:true}},{id:'esp',enabled:false,settings:{'ESP Modes':['2D'],'Name Position':'Left','Names':false,'Health Bar Width':3}}]").getAsJsonArray();
        EspConfigMigration.migrate(modules);JsonObject esp=modules.get(1).getAsJsonObject(),s=esp.getAsJsonObject("settings");
        assertTrue(esp.get("enabled").getAsBoolean());assertEquals(123,s.get("Skeletal Color").getAsInt());
        assertEquals("Skeletal",s.getAsJsonArray("ESP Modes").get(1).getAsString());assertFalse(s.get("2D Name Enabled").getAsBoolean());
        assertEquals("Left Up",s.get("2D Name Position").getAsString());assertEquals(3,s.get("2D Health Bar Width").getAsInt());
        String migrated=modules.toString();EspConfigMigration.migrate(modules);assertEquals(migrated,modules.toString());
    }
    @Test public void newAppearanceWinsOverLegacyValues() {
        JsonArray modules=new JsonParser().parse("[{id:'esp',settings:{'2D Box Enabled':false,'Names':true,'2D Name Enabled':false,'Skeletal Color':456}},{id:'skeletal',enabled:true,settings:{Color:123}}]").getAsJsonArray();
        EspConfigMigration.migrate(modules);JsonObject s=modules.get(0).getAsJsonObject().getAsJsonObject("settings");
        assertFalse(s.get("2D Name Enabled").getAsBoolean());assertEquals(456,s.get("Skeletal Color").getAsInt());
    }
}
