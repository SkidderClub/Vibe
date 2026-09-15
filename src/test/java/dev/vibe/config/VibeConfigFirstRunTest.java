package dev.vibe.config;

import dev.vibe.Vibe;
import dev.vibe.module.ModuleManager;
import java.io.File;
import java.lang.reflect.Field;
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

    private static Field field(Class<?> type, String name) throws Exception { Field value = type.getDeclaredField(name); value.setAccessible(true); return value; }
    @SuppressWarnings("unchecked") private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe"); Object value = field(unsafe, "theUnsafe").get(null);
        return (T) unsafe.getMethod("allocateInstance", Class.class).invoke(value, type);
    }
}
