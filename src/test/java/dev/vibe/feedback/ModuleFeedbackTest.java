package dev.vibe.feedback;

import dev.vibe.Vibe;
import dev.vibe.config.VibeConfig;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.client.HudModule;
import dev.vibe.module.impl.visual.NoHurtCamModule;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.Minecraft;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ModuleFeedbackTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Object previous;
    private Object previousMinecraft;
    private Vibe vibe;
    private ModuleManager manager;
    private VibeConfig config;

    @Before public void setup() throws Exception {
        previous = field(Vibe.class, "instance").get(null);
        previousMinecraft = field(Minecraft.class, "theMinecraft").get(null);
        Minecraft minecraft = allocate(Minecraft.class);
        minecraft.gameSettings = new net.minecraft.client.settings.GameSettings();
        field(Minecraft.class, "mcDataDir").set(minecraft, temporary.newFolder("minecraft"));
        field(Minecraft.class, "theMinecraft").set(null, minecraft);
        vibe = new Vibe(); field(Vibe.class, "instance").set(null, vibe);
        manager = allocate(ModuleManager.class); field(ModuleManager.class, "modules").set(manager, new ArrayList<Module>());
        field(Vibe.class, "moduleManager").set(vibe, manager);
        config = new VibeConfig(temporary.newFolder("config")); field(Vibe.class, "config").set(vibe, config);
    }
    @After public void cleanup() throws Exception {
        field(Vibe.class, "instance").set(null, previous);
        field(Minecraft.class, "theMinecraft").set(null, previousMinecraft);
    }

    @Test public void reportsActualTransitionsAndSkipsStartupNoOpsAndSelfRevertingActions() {
        Module module = new Module("Example", "", Category.CLIENT, 0) { };
        manager.registerDynamic(module);
        module.setEnabled(true);
        assertTrue(active().isEmpty());
        vibe.getFeedback().start();
        module.setEnabled(false);
        assertEquals(1, active().size()); assertFalse(active().get(0).enabled);
        long first = active().get(0).started;
        module.setEnabled(false); assertEquals(first, active().get(0).started);
        module.toggle(); assertEquals(2, active().size());
        assertFalse(active().get(0).enabled); assertTrue(active().get(1).enabled);
        Module action = new Module("Action", "", Category.CLIENT, 0) {
            @Override protected void onEnable() { setEnabled(false); }
        };
        manager.registerDynamic(action); action.setEnabled(true);
        assertFalse(action.isEnabled()); assertEquals(2, active().size());
    }
    @Test public void profilesAndExtensionRegistrationStaySilent() throws Exception {
        Module module = new Module("Example", "", Category.CLIENT, 0) { };
        manager.registerDynamic(module); vibe.getFeedback().start();
        field(VibeConfig.class, "loading").setBoolean(config, true);
        module.setEnabled(true); assertTrue(active().isEmpty());
        field(VibeConfig.class, "loading").setBoolean(config, false);
        field(Vibe.class, "extensionLoading").setBoolean(vibe, true);
        module.setEnabled(false); assertTrue(active().isEmpty());
        field(Vibe.class, "extensionLoading").setBoolean(vibe, false);
        module.setEnabled(true); assertEquals(1, active().size());
    }
    @Test public void oldProfilesGainNotificationsOnceAndCanSaveThemOff() throws Exception {
        HudModule hud = new HudModule(); manager.registerDynamic(hud);
        hud.getHudElements().toggle("notifications");
        assertTrue(config.save("old", manager));
        java.nio.file.Path path = config.getDirectory().toPath().resolve("old.json");
        com.google.gson.JsonObject root = new com.google.gson.JsonParser().parse(new String(java.nio.file.Files.readAllBytes(path), "UTF-8")).getAsJsonObject();
        root.remove("toggleFeedbackVersion");
        java.nio.file.Files.write(path, root.toString().getBytes("UTF-8"));
        assertTrue(config.load("old", manager)); assertTrue(hud.getHudElements().isSelected("notifications"));
        hud.getHudElements().toggle("notifications"); assertTrue(config.save("new", manager));
        hud.getHudElements().toggle("notifications");
        assertTrue(config.load("new", manager)); assertFalse(hud.getHudElements().isSelected("notifications"));
    }
    @Test public void noHurtCamHookFollowsOnlyItsModule() {
        NoHurtCamModule module = new NoHurtCamModule(); manager.registerDynamic(module);
        assertFalse(NoHurtCamModule.suppressHook()); module.setEnabled(true);
        assertTrue(NoHurtCamModule.suppressHook()); module.setEnabled(false); assertFalse(NoHurtCamModule.suppressHook());
    }
    @Test public void queueKeepsRepeatedModuleTransitionsAndBoundsTheStack() {
        ModuleNotifications queue = new ModuleNotifications();
        queue.add("a", "First", true, 0); queue.add("a", "First", false, 100);
        assertEquals(2, queue.active(100).size());
        assertTrue(queue.active(100).get(0).enabled); assertFalse(queue.active(100).get(1).enabled);
        queue.add("b", "Second", true, 200); queue.add("c", "Third", true, 300); queue.add("d", "Fourth", true, 400);
        assertEquals(ModuleNotifications.LIMIT, queue.active(400).size());
        assertEquals("a", queue.active(400).get(0).id); assertFalse(queue.active(400).get(0).enabled);
        ModuleNotifications.Entry entry = queue.active(400).get(3);
        assertEquals(0, entry.visibility(400), 0); assertEquals(1, entry.visibility(700), 0);
        assertTrue(entry.visibility(400 + ModuleNotifications.DURATION - 100) < 1);
        assertTrue(queue.active(400 + ModuleNotifications.DURATION).isEmpty());
    }
    @Test public void burstCardsStaySeparatedDuringEntranceAndOverflow() {
        ModuleNotifications queue = new ModuleNotifications();
        for (int i = 0; i < ModuleNotifications.LIMIT; i++) queue.add("a", "Repeated", i % 2 == 0, 0);
        for (long now = 0; now <= 150; now += 10) assertSpaced(queue.active(now), now);
        queue.add("last", "Last", true, 150);
        for (long now = 150; now <= 450; now += 10) assertSpaced(queue.active(now), now);
        List<ModuleNotifications.Entry> entries = queue.active(450);
        assertEquals(0, entries.get(entries.size() - 1).slot(450), .0001F);
        assertEquals(3, entries.get(0).slot(450), .0001F);
    }
    @Test public void expirationDoesNotMoveOrResetNewerCards() {
        ModuleNotifications queue = new ModuleNotifications();
        queue.add("a", "First", true, 0); queue.add("b", "Second", false, 1000);
        ModuleNotifications.Entry newest = queue.active(1500).get(1);
        assertEquals(0, newest.slot(1500), 0);
        assertEquals(1, queue.active(ModuleNotifications.DURATION).size());
        assertSame(newest, queue.active(ModuleNotifications.DURATION).get(0));
        assertEquals(0, newest.slot(ModuleNotifications.DURATION), 0);
        assertEquals(1000, newest.started);
    }
    private static void assertSpaced(List<ModuleNotifications.Entry> entries, long now) {
        for (int i = 1; i < entries.size(); i++)
            assertTrue("Cards overlap at " + now, entries.get(i - 1).slot(now) - entries.get(i).slot(now) >= .999F);
    }
    private List<ModuleNotifications.Entry> active() { return vibe.getFeedback().notifications().active(System.nanoTime() / 1000000L); }
    private static Field field(Class<?> type, String name) throws Exception { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; }
    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        return type.cast(unsafe.getMethod("allocateInstance", Class.class).invoke(field(unsafe, "theUnsafe").get(null), type));
    }
}
