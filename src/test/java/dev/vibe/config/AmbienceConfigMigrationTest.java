package dev.vibe.config;

import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AmbienceConfigMigrationTest {
    @Test public void standaloneFogPreservesItsEnabledStatePaletteAndKey(){
        JsonArray modules=parse("[{id:'fog',enabled:true,key:45,settings:{'Color Mode':'Rainbow','Fog Color':-65536,'Start Distance':27}}]");
        AmbienceConfigMigration.migrate(modules);JsonObject ambience=modules.get(1).getAsJsonObject(),settings=ambience.getAsJsonObject("settings");
        assertTrue(ambience.get("enabled").getAsBoolean());assertEquals(45,ambience.get("key").getAsInt());
        assertEquals("[\"Fog\"]",settings.get("Effects").toString());assertEquals("Love",settings.get("Color Mode").getAsString());
        assertEquals(-65536,settings.get("Primary Color").getAsInt());assertEquals(-65536,settings.get("Secondary Color").getAsInt());
        assertEquals(27,settings.get("Start Distance").getAsInt());
    }
    @Test public void existingTimeAndWeatherAreRetainedAndModernSelectionsWin(){
        JsonArray modules=parse("[{id:'ambience',enabled:true,settings:{'Time Mode':'Night'}},{id:'fog',enabled:true,settings:{'Blur Strength':9}}]");
        AmbienceConfigMigration.migrate(modules);JsonObject s=modules.get(0).getAsJsonObject().getAsJsonObject("settings");
        assertEquals("Night",s.get("Time Mode").getAsString());assertEquals("[\"Time\",\"Weather\",\"Fog\"]",s.get("Effects").toString());
        String first=modules.toString();AmbienceConfigMigration.migrate(modules);assertEquals(first,modules.toString());
        s.add("Effects",new JsonArray());s.addProperty("Blur Strength",4);AmbienceConfigMigration.migrate(modules);
        assertEquals("[]",s.get("Effects").toString());assertEquals(4,s.get("Blur Strength").getAsInt());
    }
    @Test public void disabledTimeDoesNotBecomeEnabledWhenMigratingActiveFog(){
        JsonArray modules=parse("[{id:'ambience',enabled:false,settings:{'Time Mode':'Dusk','Time':17}},{id:'fog',enabled:true}]");
        AmbienceConfigMigration.migrate(modules);JsonObject s=modules.get(0).getAsJsonObject().getAsJsonObject("settings");
        assertEquals("[\"Fog\"]",s.get("Effects").toString());assertEquals("Dusk",s.get("Time Mode").getAsString());assertEquals(17,s.get("Time").getAsInt());
    }
    private static JsonArray parse(String value){return new JsonParser().parse(value.replace('\'', '"')).getAsJsonArray();}
    @Test public void oldNoneWeatherBecomesADeselectedWeatherEffect(){
        JsonArray modules=parse("[{id:'ambience',enabled:true,settings:{'Weather Mode':'None','Effects':['Time','Weather','Fog']}}]");
        AmbienceConfigMigration.migrate(modules);JsonObject s=modules.get(0).getAsJsonObject().getAsJsonObject("settings");
        assertEquals("Sun",s.get("Weather Mode").getAsString());assertEquals("[\"Time\",\"Fog\"]",s.get("Effects").toString());
        String once=modules.toString();AmbienceConfigMigration.migrate(modules);assertEquals(once,modules.toString());
    }
}
