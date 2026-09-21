package dev.vibe.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vibe.Vibe;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.SpeedModule;
import dev.vibe.module.impl.VelocityModule;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.Setting;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Regression for the no-file first-run contract. */
public class VibeConfigFirstRunTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Object previousMinecraft;
    private Object previousVibe;
    private Field minecraftField, vibeField;

    @Before public void prepareClient() throws Exception {
        minecraftField = field(Minecraft.class, "theMinecraft");
        vibeField = field(Vibe.class, "instance");
        previousMinecraft = minecraftField.get(null); previousVibe = vibeField.get(null);
        Minecraft minecraft = allocate(Minecraft.class);
        minecraft.gameSettings = allocate(GameSettings.class);
        field(Minecraft.class, "mcDataDir").set(minecraft, temporary.newFolder("minecraft"));
        minecraftField.set(null, minecraft);
    }

    @After public void restoreClient() throws Exception {
        minecraftField.set(null, previousMinecraft); vibeField.set(null, previousVibe);
    }

    @Test public void appliesCompiledDefaultsWithoutWritingTheSuppliedProfile() throws Exception {
        Vibe vibe = new Vibe(); vibeField.set(null, vibe);
        ModuleManager manager = new ModuleManager();
        File forgeConfig = temporary.newFolder("forge-config");
        VibeConfig config = new VibeConfig(forgeConfig);
        field(Vibe.class, "moduleManager").set(vibe, manager);
        field(Vibe.class, "config").set(vibe, config);

        config.load(manager);

        assertFalse(new File(config.getDirectory(), "default.json").exists());
        assertFalse(new File(new File(forgeConfig, "vibe"), "active-profile.txt").exists());
        assertTrue(manager.getModule("ESP").isEnabled());
    }

    @Test public void migratesVelocitySelectionAndRemovedBhopCustomMode() throws Exception {
        Vibe vibe = new Vibe(); vibeField.set(null, vibe);
        ModuleManager manager = new ModuleManager();
        File forgeConfig = temporary.newFolder("forge-config");
        VibeConfig config = new VibeConfig(forgeConfig);
        field(Vibe.class, "moduleManager").set(vibe, manager);
        field(Vibe.class, "config").set(vibe, config);

        JsonObject root = new JsonObject();
        JsonArray modules = new JsonArray();
        modules.add(module("velocity", "Mode", "AACReverse"));
        modules.add(module("bhop", "Mode", "Custom"));
        root.add("modules", modules);
        Files.createDirectories(config.getDirectory().toPath());
        Files.write(new File(config.getDirectory(), "legacy.json").toPath(),
                root.toString().getBytes(StandardCharsets.UTF_8));

        assertTrue(config.load("legacy", manager));
        MultiSelectSetting modes = setting(manager.getModule(VelocityModule.class), "Modes", MultiSelectSetting.class);
        assertTrue(modes.isSelected("AACReverse"));
        assertEquals(1, modes.getValue().size());
        ModeSetting speedMode = setting(manager.getModule(SpeedModule.class), "Mode", ModeSetting.class);
        assertEquals("Custom", speedMode.getValue());

        assertTrue(config.save("round-trip", manager));
        JsonObject saved = new JsonParser().parse(new String(Files.readAllBytes(
                new File(config.getDirectory(), "round-trip.json").toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray savedModules = saved.getAsJsonArray("modules");
        for (com.google.gson.JsonElement element : savedModules) {
            JsonObject data = element.getAsJsonObject();
            if (!"velocity".equals(data.get("id").getAsString())) continue;
            assertTrue(data.getAsJsonObject("settings").get("Modes").isJsonArray());
            return;
        }
        fail("Velocity was not persisted");
    }

    private static JsonObject module(String id, String setting, String value) {
        JsonObject data = new JsonObject();
        data.addProperty("id", id);
        data.addProperty("enabled", false);
        JsonObject settings = new JsonObject();
        settings.addProperty(setting, value);
        data.add("settings", settings);
        return data;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Setting<?>> T setting(dev.vibe.module.Module module, String name, Class<T> type) {
        for (Setting<?> setting : module.getSettings()) {
            if (name.equals(setting.getRawName())) return (T) setting;
        }
        throw new AssertionError("Missing setting: " + name);
    }

    private static Field field(Class<?> type, String name) throws Exception { Field value = type.getDeclaredField(name); value.setAccessible(true); return value; }
    @SuppressWarnings("unchecked") private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe"); Object value = field(unsafe, "theUnsafe").get(null);
        return (T) unsafe.getMethod("allocateInstance", Class.class).invoke(value, type);
    }
}
