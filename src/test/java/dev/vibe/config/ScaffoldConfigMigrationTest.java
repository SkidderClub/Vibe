package dev.vibe.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScaffoldConfigMigrationTest {
    @Test public void renamedRotationsKeepTheirMeaning() {
        assertEquals("Normal", migrated("Intave"));
        assertEquals("GodBridge", migrated("Polar"));
        assertEquals("GodBridge", migrated("God Bridge"));
        assertEquals("GodBridge", migrated("GodBridge"));
        assertEquals("Normal", migrated("Normal"));
    }

    @Test public void otherModulesAndSettingsAreUntouched() {
        JsonArray modules = new JsonParser().parse("[{id:'killaura',settings:{Rotations:'Polar'}},"
                + "{id:'scaffold',settings:{Tower:'Intave'}},{id:'scaffold'}]").getAsJsonArray();
        String before = modules.toString();
        ScaffoldConfigMigration.migrate(modules);
        assertEquals(before, modules.toString());
    }

    private static String migrated(String rotations) {
        JsonArray modules = new JsonParser().parse("[{id:'scaffold',settings:{Rotations:'" + rotations + "'}}]").getAsJsonArray();
        ScaffoldConfigMigration.migrate(modules);
        return modules.get(0).getAsJsonObject().getAsJsonObject("settings").get("Rotations").getAsString();
    }
}
