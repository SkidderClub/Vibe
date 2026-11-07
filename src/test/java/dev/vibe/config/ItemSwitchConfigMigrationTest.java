package dev.vibe.config;

import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ItemSwitchConfigMigrationTest {
    @Test public void preservesLegacyModesColorsAndMurderPreferenceWithoutOverwritingNewSettings() {
        JsonArray modules=new JsonParser().parse("[{id:'autotool',settings:{Mode:'Silent','Silent Slot Color':123}},"
                +"{id:'pickenswitch',settings:{Mode:'Basic'}},{id:'scaffold',settings:{'Spoof Slot':false}},"
                +"{id:'hypixel',settings:{'Silent murderer':true}},"
                +"{id:'autotool',settings:{Mode:'Silent','Item Switching':'Basic','Silent Slot Color':123,'Slot Highlight Color':456}}]").getAsJsonArray();
        ItemSwitchConfigMigration.migrate(modules);
        assertEquals("Silent",settings(modules,0).get("Item Switching").getAsString());
        assertEquals(123,settings(modules,0).get("Slot Highlight Color").getAsInt());
        assertEquals("Basic",settings(modules,1).get("Item Switching").getAsString());
        assertEquals("Basic",settings(modules,2).get("Item Switching").getAsString());
        assertTrue(settings(modules,3).get("Auto Weapon").getAsBoolean());
        assertEquals("Basic",settings(modules,4).get("Item Switching").getAsString());
        assertEquals(456,settings(modules,4).get("Slot Highlight Color").getAsInt());
        String first=modules.toString();ItemSwitchConfigMigration.migrate(modules);assertEquals(first,modules.toString());
    }
    private static JsonObject settings(JsonArray modules,int i){return modules.get(i).getAsJsonObject().getAsJsonObject("settings");}
}
