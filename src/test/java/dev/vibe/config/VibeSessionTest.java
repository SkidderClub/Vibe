package dev.vibe.config;

import com.google.gson.JsonObject;
import dev.vibe.Vibe;
import dev.vibe.hud.HudManager;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.path.PathTestFixture;
import dev.vibe.setting.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Real JSON restart recovery, independent of explicit named profile saves. */
public class VibeSessionTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private PathTestFixture f;
    private File folder, session;
    private VibeConfig config;
    private Values values;

    @Before public void setup() throws Exception {
        f = new PathTestFixture(); folder = temporary.newFolder("config");
        PathTestFixture.set(Vibe.class, f.vibe, "minecraft", f.mc);
        PathTestFixture.set(Vibe.class, f.vibe, "hudManager", new HudManager(folder));
        config = config(); values = register();
        session = new File(folder, "vibe/session.json");
    }
    @After public void cleanup() throws Exception { if (f != null) f.close(); }

    @Test public void restoresUnsavedTypedSettingsToggleBindAndHudAfterRestartWithoutCreatingProfiles() throws Exception {
        assertFalse(config.restoreSession(f.modules));
        values.setEnabled(true); values.setKey(37); values.flag.setEnabled(true);
        values.number.setValue(7.5D); values.range.setRange(3, 8); values.color.setValue(0x8044AADD);
        values.mode.setValue("Second"); values.multi.setValue(Collections.singleton("Two")); values.text.setValue("Saved \"text\" \u00e4");
        JsonObject layout = f.vibe.getHudManager().toJson();
        layout.getAsJsonObject(HudManager.WATERMARK).addProperty("x", 123);
        f.vibe.getHudManager().fromJson(layout);
        assertTrue(config.saveSession(f.modules));
        f.modules.unregisterDynamic(values); values = register();
        PathTestFixture.set(Vibe.class, f.vibe, "hudManager", new HudManager(folder));
        config = config(); config.load(f.modules); assertTrue(config.restoreSession(f.modules));
        assertTrue(values.isEnabled()); assertEquals(37, values.getKey()); assertTrue(values.flag.isEnabled());
        assertEquals(7.5, values.number.getDouble(), 0); assertEquals(3, values.range.getMin(), 0); assertEquals(8, values.range.getMax(), 0);
        assertEquals(0x8044AADD, values.color.getArgb()); assertEquals("Second", values.mode.getValue());
        assertEquals(Collections.singleton("Two"), values.multi.getValue()); assertEquals("Saved \"text\" \u00e4", values.text.getValue());
        assertEquals(123, f.vibe.getHudManager().toJson().getAsJsonObject(HudManager.WATERMARK).get("x").getAsInt());
        assertTrue(config.list().isEmpty()); assertFalse(config.getDirectory().exists()); assertFalse(config.isLoading());
    }

    @Test public void restartUsesLatestSessionWhileExplicitLoadStillRestoresTheUntouchedNamedSnapshot() throws Exception {
        config.restoreSession(f.modules); values.setKey(21); assertTrue(config.save("manual", f.modules));
        File profile = new File(config.getDirectory(), "manual.json"); byte[] before = Files.readAllBytes(profile.toPath());
        values.setKey(38); values.text.setValue("unsaved edits"); config.tickSession(f.modules);
        assertTrue(session.isFile()); assertArrayEquals(before, Files.readAllBytes(profile.toPath()));
        config = config(); config.load(f.modules); assertEquals(21, values.getKey());
        assertTrue(config.restoreSession(f.modules)); assertEquals(38, values.getKey()); assertEquals("unsaved edits", values.text.getValue());
        assertEquals("manual", config.getActiveName()); assertEquals(Collections.singletonList("manual"), config.list());
        assertTrue(config.load("manual", f.modules)); assertEquals(21, values.getKey());
        assertArrayEquals(before, Files.readAllBytes(profile.toPath()));
    }

    @Test public void restoresOnlineStateOfflineWithoutCreatingALocalProfile() throws Exception {
        config.restoreSession(f.modules);
        assertTrue(config.loadOnline("folder/preset", OnlineConfigService.parseProfile("{\"modules\":[{\"id\":\"session-values\",\"key\":45,\"settings\":{\"Text\":\"online\"}}]}"), f.modules, true, true));
        values.number.setValue(9D); assertTrue(config.saveSession(f.modules));
        f.modules.unregisterDynamic(values); values = register(); config = config(); config.load(f.modules);
        assertTrue(config.restoreSession(f.modules)); assertEquals("online/folder/preset", config.getActiveName());
        assertEquals(45, values.getKey()); assertEquals("online", values.text.getValue()); assertEquals(9, values.number.getDouble(), 0);
        assertTrue(config.list().isEmpty()); assertFalse(config.getDirectory().exists());
    }

    @Test public void temporaryExtensionAbsenceDoesNotEraseItsSavedState() throws Exception {
        config.restoreSession(f.modules); values.text.setValue("extension options"); values.setKey(41);
        assertTrue(config.saveSession(f.modules)); f.modules.unregisterDynamic(values);
        config = config(); assertTrue(config.restoreSession(f.modules)); assertTrue(config.saveSession(f.modules));
        values = register(); config = config(); assertTrue(config.restoreSession(f.modules));
        assertEquals("extension options", values.text.getValue()); assertEquals(41, values.getKey());
    }

    @Test public void unchangedCheckpointsDoNotRewriteTheFileAndShutdownSaveCapturesTheLastEdit() throws Exception {
        assertFalse(config.saveSession(f.modules)); assertFalse(session.exists()); // startup has not restored state yet
        config.restoreSession(f.modules); assertTrue(config.saveSession(f.modules));
        assertTrue(session.setLastModified(123456000L)); byte[] before = Files.readAllBytes(session.toPath());
        assertTrue(config.saveSession(f.modules)); assertEquals(123456000L, session.lastModified());
        assertArrayEquals(before, Files.readAllBytes(session.toPath()));
        values.setKey(46); assertTrue(config.saveSession(f.modules)); // same call used before extension shutdown
        config = config(); assertTrue(config.restoreSession(f.modules)); assertEquals(46, values.getKey());
        assertFalse(new File(folder, "vibe/.session.json.tmp").exists());
    }

    @Test public void brokenSessionFallsBackToTheSavedProfileAndLoadingNeverTriggersEditorActions() throws Exception {
        values.setKey(25); assertTrue(config.save("fallback", f.modules));
        Files.write(session.toPath(), "{bad json".getBytes(StandardCharsets.UTF_8));
        config = config(); values.setKey(0); config.load(f.modules);
        assertFalse(config.restoreSession(f.modules)); assertEquals(25, values.getKey()); assertFalse(config.isLoading());
        values.action.setEnabled(true); assertTrue(config.saveSession(f.modules)); values.action.setEnabled(false);
        values.action.onEnable(() -> { if (!f.vibe.getConfig().isLoading()) values.opened++; values.action.setEnabled(false); });
        config = config(); assertTrue(config.restoreSession(f.modules)); assertEquals(0, values.opened); assertFalse(values.action.isEnabled());
    }

    private Values register() { Values module = new Values(); f.modules.registerDynamic(module); return module; }
    private VibeConfig config() throws Exception {
        VibeConfig result = new VibeConfig(folder); PathTestFixture.set(Vibe.class, f.vibe, "config", result); return result;
    }

    private static final class Values extends Module {
        final BooleanSetting flag = addSetting(new BooleanSetting("Flag", false));
        final NumberSetting number = addSetting(new NumberSetting("Number", 1, 0, 10, .5));
        final RangeSetting range = addSetting(new RangeSetting("Range", 1, 2, 0, 10, 1));
        final ColorSetting color = addSetting(new ColorSetting("Color", 0xFFFFFFFF));
        final ModeSetting mode = addSetting(new ModeSetting("Mode", "First", "First", "Second"));
        final MultiSelectSetting multi = addSetting(new MultiSelectSetting("Multi", Arrays.asList("One", "Two"), Collections.singletonList("One")));
        final StringSetting text = addSetting(new StringSetting("Text", "default"));
        final BooleanSetting action = addSetting(new BooleanSetting("Editor", false));
        int opened;
        Values() { super("Session values", "Session fixture", Category.MEME, 0); }
        @Override public String getId() { return "session-values"; }
    }
}
