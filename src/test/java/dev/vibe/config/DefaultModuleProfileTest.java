package dev.vibe.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Ensures the supplied first-run values stay compiled in rather than copied as a JSON file. */
public class DefaultModuleProfileTest {
    @Test public void suppliedProfileIsAvailableInCodeWithGameGuiTargets() {
        JsonObject profile = DefaultModuleProfile.read();
        assertEquals(3, profile.get("format").getAsInt());
        JsonObject esp = module(profile, "esp");
        assertTrue(esp.get("enabled").getAsBoolean());
        assertEquals(25, esp.get("key").getAsInt());
        assertTrue(contains(module(profile, "blur").getAsJsonObject("settings").getAsJsonArray("Blur Elements"), "memegames"));
        assertTrue(contains(module(profile, "particles").getAsJsonObject("settings").getAsJsonArray("Show In"), "Meme Games"));
        assertNull("The old JSON resource must not be shipped", DefaultModuleProfile.class.getResource("/assets/vibe/config/default.json"));
    }

    private JsonObject module(JsonObject profile, String id) {
        for (JsonElement element : profile.getAsJsonArray("modules")) {
            JsonObject module = element.getAsJsonObject();
            if (id.equals(module.get("id").getAsString())) return module;
        }
        throw new AssertionError("Missing module " + id);
    }

    private boolean contains(Iterable<JsonElement> values, String expected) {
        for (JsonElement value : values) if (expected.equals(value.getAsString())) return true;
        return false;
    }
}
