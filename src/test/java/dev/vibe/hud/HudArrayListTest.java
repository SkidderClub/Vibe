package dev.vibe.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.vibe.Vibe;
import dev.vibe.config.VibeConfig;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.HudModule;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Exercises the real module registry and profile persistence without a GL window. */
public class HudArrayListTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Minecraft previousMinecraft;
    private Vibe previousVibe;
    private Vibe vibe;
    private VibeConfig config;
    private ModuleManager manager;
    private Field minecraftSingleton;
    private Field vibeSingleton;

    @Before public void setUp() throws Exception {
        minecraftSingleton = field(Minecraft.class, "theMinecraft");
        vibeSingleton = field(Vibe.class, "instance");
        previousMinecraft = (Minecraft) minecraftSingleton.get(null);
        previousVibe = (Vibe) vibeSingleton.get(null);
        Minecraft minecraft = allocate(Minecraft.class);
        minecraft.gameSettings = allocate(GameSettings.class);
        field(Minecraft.class, "mcDataDir").set(minecraft, temporary.newFolder("minecraft"));
        minecraftSingleton.set(null, minecraft);
        vibe = new Vibe();
        vibeSingleton.set(null, vibe);
        config = new VibeConfig(temporary.newFolder("config"));
        field(Vibe.class, "config").set(vibe, config);
        restartModules();
    }

    @After public void tearDown() throws Exception {
        if (vibeSingleton != null) vibeSingleton.set(null, previousVibe);
        if (minecraftSingleton != null) minecraftSingleton.set(null, previousMinecraft);
    }

    @Test public void selectiveLoadsKeepKeybindsAndVisualsIndependent() throws Exception {
        Module visual = new Module("LoadTestVisual", "", Category.VISUAL, 11) { };
        Module combat = new Module("LoadTestCombat", "", Category.COMBAT, 12) { };
        manager.registerDynamic(visual); manager.registerDynamic(combat);
        visual.setEnabled(true); combat.setEnabled(true);
        assertTrue(config.save("imported", manager));
        for (boolean keys : new boolean[] {false, true}) for (boolean visuals : new boolean[] {false, true}) {
            assertTrue(config.save("working", manager));
            visual.setKey(21); combat.setKey(22);
            visual.setEnabled(false); combat.setEnabled(false);
            assertTrue(config.load("imported", manager, keys, visuals));
            assertEquals(keys ? 11 : 21, visual.getKey());
            assertEquals(keys ? 12 : 22, combat.getKey());
            assertEquals(visuals, visual.isEnabled());
            assertTrue(combat.isEnabled());
        }
    }

    @Test public void savedVisibleModulesStayVisibleAcrossRestarts() throws Exception {
        HudModule hud = hud();
        // This is the same synchronization the first HUD frame performs.
        hud.synchronizeArrayListModules();
        assertTrue(hud.getArrayListModules().isSelected("MoveFix"));
        Set<String> selected = hud.getArrayListModules().copyValue();
        Set<String> visible = visibleModules();
        assertTrue(visible.contains("MoveFix"));

        for (int restart = 0; restart < 3; restart++) {
            assertTrue(config.save("visible", manager));
            restartModules();
            assertTrue(config.load("visible", manager));
            hud().synchronizeArrayListModules();
            assertEquals(selected, hud().getArrayListModules().getValue());
            assertEquals(visible, visibleModules());
        }
    }

    @Test public void savedHiddenModulesStayHiddenAcrossRestarts() throws Exception {
        hud().synchronizeArrayListModules();
        hud().getArrayListModules().toggle("MoveFix");
        Set<String> selected = hud().getArrayListModules().copyValue();
        assertFalse(selected.contains("MoveFix"));
        assertTrue(config.save("hidden", manager));

        restartModules();
        assertTrue(config.load("hidden", manager));
        for (int frame = 0; frame < 3; frame++) hud().synchronizeArrayListModules();
        assertEquals(selected, hud().getArrayListModules().getValue());
        assertFalse(visibleModules().contains("MoveFix"));
    }

    @Test public void defaultsAreCompleteBeforeTheFirstProfileIsSaved() {
        assertTrue(hud().getArrayListModules().getOptions().contains("MoveFix"));
        assertTrue(hud().getArrayListModules().isSelected("MoveFix"));
        assertTrue(hud().getArrayListModules().isSelected("Killaura"));
        assertFalse(hud().getArrayListModules().isSelected("HUD"));
        assertFalse(hud().getArrayListModules().isSelected("Blur"));
    }

    @Test public void lateRegistrationPreservesSavedScriptSelection() {
        String name = "Saved Script";
        Set<String> selected = hud().getArrayListModules().copyValue();
        selected.add(name);
        hud().getArrayListModules().setValue(selected);
        Module script = new Module(name, "", Category.SCRIPTS, 0) { };
        manager.registerDynamic(script);
        hud().synchronizeArrayListModules();
        assertTrue(hud().getArrayListModules().getOptions().contains(name));
        assertEquals(selected, hud().getArrayListModules().getValue());

        // Reloading a script in the same session must also respect a later opt-out.
        hud().getArrayListModules().toggle(name);
        manager.unregisterDynamic(script);
        manager.registerDynamic(new Module(name, "", Category.SCRIPTS, 0) { });
        hud().synchronizeArrayListModules();
        assertFalse(hud().getArrayListModules().isSelected(name));
    }

    @Test public void legacyDefaultProfileRecoversVisibleEntriesAndSurvivesRestarts() throws Exception {
        // Matches the reported profile: HUD is enabled, but none of the active
        // modules appears in the old, hard-coded ArrayList selection.
        writeProfile("legacy", legacyProfile());
        assertTrue(config.load("legacy", manager));
        assertTrue(hud().isEnabled());
        assertTrue(hud().getHudElements().isSelected("arraylist"));
        assertTrue("The old profile must no longer leave the ArrayList empty", visibleModules().contains("MoveFix"));
        assertTrue(hud().getArrayListModules().isSelected("Killaura"));
        assertFalse(hud().getArrayListModules().isSelected("HUD"));
        assertFalse(hud().getArrayListModules().isSelected("Blur"));
        Set<String> selected = hud().getArrayListModules().copyValue();
        Set<String> visible = visibleModules();
        for (int restart = 0; restart < 3; restart++) {
            assertTrue(config.save("legacy", manager));
            restartModules();
            assertTrue(config.load("legacy", manager));
            hud().synchronizeArrayListModules();
            assertEquals(selected, hud().getArrayListModules().getValue());
            assertEquals(visible, visibleModules());
        }
    }

    @Test public void explicitSelectionMatchingLegacyDefaultsIsRespectedAfterMigration() throws Exception {
        JsonObject legacy = legacyProfile();
        Set<String> explicit = new LinkedHashSet<String>();
        for (JsonElement value : legacyHudSettings(legacy).getAsJsonArray("ArrayList Modules")) {
            explicit.add(value.getAsString());
        }
        writeProfile("legacy", legacy);
        assertTrue(config.load("legacy", manager));
        // The user deliberately hides the newly recovered modules again.
        hud().getArrayListModules().setValue(explicit);
        assertTrue(visibleModules().isEmpty());
        assertTrue(config.save("explicit", manager));
        restartModules();
        assertTrue(config.load("explicit", manager));
        hud().synchronizeArrayListModules();
        assertEquals(explicit, hud().getArrayListModules().getValue());
        assertTrue(visibleModules().isEmpty());
    }

    @Test public void legacyCustomAndEmptySelectionsArePreserved() throws Exception {
        for (String option : new String[] {null, "MoveFix"}) {
            JsonObject legacy = legacyProfile();
            JsonArray values = new JsonArray();
            Set<String> expected = new LinkedHashSet<String>();
            if (option != null) {
                values.add(new JsonPrimitive(option));
                expected.add(option);
            }
            legacyHudSettings(legacy).add("ArrayList Modules", values);
            writeProfile("custom", legacy);
            assertTrue(config.load("custom", manager));
            hud().synchronizeArrayListModules();
            assertEquals(expected, hud().getArrayListModules().getValue());
        }
    }

    private JsonObject legacyProfile() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("legacy-arraylist.json")) {
            assertNotNull(stream);
            return new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private JsonObject legacyHudSettings(JsonObject profile) {
        for (JsonElement module : profile.getAsJsonArray("modules")) {
            JsonObject data = module.getAsJsonObject();
            if ("hud".equals(data.get("id").getAsString())) return data.getAsJsonObject("settings");
        }
        throw new AssertionError("Missing HUD fixture");
    }

    private void writeProfile(String name, JsonObject profile) throws Exception {
        Files.createDirectories(config.getDirectory().toPath());
        Files.write(new File(config.getDirectory(), name + ".json").toPath(),
                profile.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void restartModules() throws Exception {
        // Match preInit: the new registry is assigned after its constructor finishes.
        field(Vibe.class, "moduleManager").set(vibe, null);
        manager = new ModuleManager();
        field(Vibe.class, "moduleManager").set(vibe, manager);
    }

    private HudModule hud() {
        return manager.getModule(HudModule.class);
    }

    private Set<String> visibleModules() {
        Set<String> visible = new LinkedHashSet<String>();
        for (Module module : manager.getModules()) {
            if (module.isEnabled() && hud().getArrayListModules().isSelected(module.getRawName())) {
                visible.add(module.getRawName());
            }
        }
        return visible;
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Object unsafe = field(unsafeClass, "theUnsafe").get(null);
        return type.cast(unsafeClass.getMethod("allocateInstance", Class.class).invoke(unsafe, type));
    }
}
