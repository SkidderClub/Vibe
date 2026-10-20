package dev.vibe.extension;

import com.google.gson.JsonObject;
import dev.vibe.BuildInfo;
import dev.vibe.config.VibeConfig;
import dev.vibe.extension.fixture.Fixtures;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.statistics.StatisticsService;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.nio.file.Files;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ExtensionManagerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private File game;
    private ModuleManager modules;
    private StatisticsService statistics;
    private ExtensionManager manager;
    private CountingBus forge, fml;
    private final ArrayList<URLClassLoader> loaders = new ArrayList<URLClassLoader>();
    private int mounts, unmounts;
    @Before public void setup() throws Exception {
        game = temporary.newFolder("game");
        Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        modules = (ModuleManager) unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, ModuleManager.class);
        Field list = ModuleManager.class.getDeclaredField("modules"); list.setAccessible(true); list.set(modules, new ArrayList<Module>());
        statistics = new StatisticsService(null, temporary.newFolder("statistics").toPath());
        forge = new CountingBus(); fml = new CountingBus();
        manager = new ExtensionManager(game, modules, statistics, forge, fml, (jar, name) -> {
            URLClassLoader loader = new URLClassLoader(new URL[] {jar.toURI().toURL()}, getClass().getClassLoader()) {
                @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    if (name.startsWith("dev.vibe.extension.fixture.")) {
                        Class<?> type = findLoadedClass(name);
                        if (type == null) type = findClass(name);
                        if (resolve) resolveClass(type);
                        return type;
                    }
                    return super.loadClass(name, resolve);
                }
            };
            loaders.add(loader); return Class.forName(name, false, loader);
        }, jar -> { mounts++; return () -> unmounts++; });
    }
    @After public void close() throws Exception {
        manager.close(); for (URLClassLoader loader : loaders) loader.close();
    }
    @Test public void absentExtensionsAndOrdinaryForgeJarsAddNothing() throws Exception {
        jar("mods/ordinary.jar", null, Fixtures.Good.class);
        manager.load(); assertTrue(manager.getLoaded().isEmpty()); assertTrue(manager.getStatus().isEmpty());
        assertTrue(modules.getModules().isEmpty()); assertEquals(0, mounts);
        assertNull(getClass().getResource("/assets/vibe/lego/catalog.json"));
        assertNull(getClass().getResource("/assets/vibe/cosmetica/catalog.json"));
        assertNotNull(Class.forName("dev.vibe.module.impl.visual.CustomCosmeticsModule"));
        try { Class.forName("dev.vibe.module.impl.visual.CosmeticsModule"); fail("Cosmetica leaked onto core's classpath"); }
        catch (ClassNotFoundException expected) { }
        try { Class.forName("dev.vibe.module.impl.meme.LegoModule"); fail("LEGO leaked onto core's classpath"); }
        catch (ClassNotFoundException expected) { }
        try { Class.forName("dev.vibe.module.impl.movement.TestModule"); fail("Test leaked onto core's classpath"); }
        catch (ClassNotFoundException expected) { }
    }
    @Test public void disabledJarsAreIgnoredEvenWithAnEnabledCopyOfTheSameId() throws Exception {
        jar("mods/fixture.jar.disabled", metadata("fixture", Fixtures.Good.class), Fixtures.Good.class);
        jar("mods/1.8.9/fixture.jar", metadata("fixture", Fixtures.Good.class), Fixtures.Good.class);
        manager.load();
        assertEquals(1, manager.getLoaded().size()); assertEquals(1, modules.getModules().size()); assertEquals(1, mounts);
        assertEquals(1, forge.listeners.size()); assertEquals(1, fml.listeners.size());
    }
    @Test public void discoversVersionFolderOnceAndWiresNormalModulesAndStatistics() throws Exception {
        jar("mods/1.8.9/fixture.jar", metadata("fixture", Fixtures.Good.class), Fixtures.Good.class);
        manager.load(); manager.load();
        assertEquals(1, manager.getLoaded().size()); assertEquals(1, modules.getModules().size()); assertEquals(1, mounts);
        Module fixture = modules.getModule("fixture");
        assertEquals("Fixture service", manager.getService(CharSequence.class).toString());
        assertTrue(fixture.getClass().getClassLoader() instanceof URLClassLoader);
        assertEquals(1, forge.listeners.size()); assertEquals(1, fml.listeners.size());
        manager.tickStart(); manager.tickStart();
        assertEquals(2, value(fixture, "ticks"));
        activity(); assertEquals(1, value(fixture, "activities"));
        manager.shutdown(); manager.shutdown(); assertEquals(1, value(fixture, "stops"));
        manager.close(); assertTrue(modules.getModules().isEmpty());
        assertNull(manager.getService(CharSequence.class));
        assertTrue(forge.listeners.isEmpty()); assertTrue(fml.listeners.isEmpty());
        manager.tickStart(); activity(); assertEquals(2, value(fixture, "ticks")); assertEquals(1, value(fixture, "activities"));
    }
    @Test public void failedInitializationRollsBackEverythingAndOtherExtensionsStillLoad() throws Exception {
        jar("mods/01-bad.jar", metadata("bad", Fixtures.Bad.class), Fixtures.Bad.class);
        jar("mods/02-good.jar", metadata("good", Fixtures.Good.class), Fixtures.Good.class);
        manager.load();
        assertNull(modules.getModule("rollback")); assertNotNull(modules.getModule("fixture"));
        assertNull(manager.getService(Runnable.class));
        assertEquals(1, forge.listeners.size()); assertEquals(1, fml.listeners.size());
        assertEquals(1, manager.getLoaded().size()); assertEquals(1, unmounts);
        manager.tickStart(); activity(); assertEquals(1, value(modules.getModule("fixture"), "activities"));
        assertTrue(manager.getStatus().toString().contains("Initialization failed"));
    }
    @Test public void duplicateIdsInTwoModFoldersAreBothRejectedBeforeLoadingCode() throws Exception {
        jar("mods/first.jar", metadata("same", Fixtures.Good.class), Fixtures.Good.class);
        jar("mods/1.8.9/second.jar", metadata("same", Fixtures.Bad.class), Fixtures.Bad.class);
        manager.load(); assertTrue(manager.getLoaded().isEmpty()); assertTrue(loaders.isEmpty());
        assertTrue(manager.getStatus().toString().contains("Duplicate"));
    }
    @Test public void incompatibleApiVersionAndMissingEntrypointAreRejectedBeforeLoadingCode() throws Exception {
        JsonObject incompatible = metadata("future", Fixtures.Good.class); incompatible.addProperty("apiVersion", 99);
        jar("mods/future.jar", incompatible, Fixtures.Good.class);
        JsonObject missing = metadata("missing", Fixtures.Bad.class); missing.addProperty("entrypoint", "missing.Entrypoint");
        jar("mods/missing.jar", missing, Fixtures.Bad.class);
        manager.load(); assertTrue(loaders.isEmpty()); assertTrue(manager.getLoaded().isEmpty());
        assertTrue(manager.getStatus().toString().contains("Unsupported extension API"));
        assertTrue(manager.getStatus().toString().contains("Entrypoint class is missing"));
    }
    @Test public void moduleCollisionKeepsTheOriginalModule() throws Exception {
        jar("mods/01-good.jar", metadata("good", Fixtures.Good.class), Fixtures.Good.class);
        jar("mods/02-collision.jar", metadata("collision", Fixtures.Collision.class), Fixtures.Collision.class);
        manager.load(); assertEquals(1, modules.getModules().size()); assertEquals(1, manager.getLoaded().size());
        manager.tickStart(); assertEquals(1, value(modules.getModule("fixture"), "ticks"));
        assertTrue(manager.getStatus().toString().contains("Module id already registered"));
    }
    @Test public void failedDisableCannotLeaveAPartialModuleRegistered() throws Exception {
        jar("mods/01-failure.jar", metadata("failure", Fixtures.DisableFailure.class), Fixtures.DisableFailure.class);
        jar("mods/02-good.jar", metadata("good", Fixtures.Good.class), Fixtures.Good.class);
        manager.load(); assertNull(modules.getModule("brokendisable"));
        assertEquals(1, modules.getModules().size()); assertEquals(1, manager.getLoaded().size());
        manager.tickStart(); assertEquals(1, value(modules.getModule("fixture"), "ticks"));
    }
    @Test public void crashingTickCallbackStopsOnceAndDoesNotPreventOtherCallbacks() throws Exception {
        jar("mods/01-failure.jar", metadata("failure", Fixtures.TickFailure.class), Fixtures.TickFailure.class);
        jar("mods/02-good.jar", metadata("good", Fixtures.Good.class), Fixtures.Good.class);
        manager.load(); manager.tickStart(); manager.tickStart();
        assertEquals(1, value(modules.getModule("tickfailure"), "ticks"));
        assertEquals(2, value(modules.getModule("fixture"), "ticks"));
    }
    @Test public void temporarilyAbsentExtensionRetainsSettingsAcrossProfileSaves() throws Exception {
        jar("mods/good.jar", metadata("good", Fixtures.Good.class), Fixtures.Good.class); manager.load();
        Module fixture = modules.getModule("fixture"); fixture.setKey(42);
        ((dev.vibe.setting.NumberSetting) fixture.getSettings().get(0)).setValue(7D);
        VibeConfig config = new VibeConfig(temporary.newFolder("config")); assertTrue(config.save("extensions", modules));
        manager.close(); assertTrue(config.load("extensions", modules)); assertTrue(config.save("extensions", modules));
        modules.registerDynamic(fixture); fixture.setKey(0);
        ((dev.vibe.setting.NumberSetting) fixture.getSettings().get(0)).setValue(0D);
        assertTrue(config.load("extensions", modules));
        assertEquals(42, fixture.getKey()); assertEquals(7D, ((dev.vibe.setting.NumberSetting) fixture.getSettings().get(0)).getDouble(), 0);
    }
    private void activity() throws Exception {
        java.lang.reflect.Method method = StatisticsService.class.getDeclaredMethod("activity", StatisticsService.Activity.class,
                net.minecraft.util.BlockPos.class, net.minecraft.entity.EntityLivingBase.class);
        method.setAccessible(true); method.invoke(statistics, StatisticsService.Activity.JUMP, null, null);
    }
    private static int value(Module module, String field) throws Exception { return module.getClass().getField(field).getInt(module); }
    private static JsonObject metadata(String id, Class<?> type) {
        JsonObject json = new JsonObject(); json.addProperty("apiVersion", 1); json.addProperty("id", id);
        json.addProperty("name", id); json.addProperty("version", "1.0"); json.addProperty("vibeVersion", BuildInfo.VERSION);
        json.addProperty("entrypoint", type.getName()); return json;
    }
    private void jar(String path, JsonObject metadata, Class<?> type) throws Exception {
        File target = new File(game, path); target.getParentFile().mkdirs();
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(target.toPath()))) {
            if (metadata != null) {
                jar.putNextEntry(new JarEntry(ExtensionDescriptor.PATH)); jar.write(metadata.toString().getBytes(StandardCharsets.UTF_8)); jar.closeEntry();
            }
            ArrayList<Class<?>> classes = new ArrayList<Class<?>>(); classes.add(Fixtures.class);
            java.util.Collections.addAll(classes, Fixtures.class.getDeclaredClasses());
            for (Class<?> entry : classes) {
                String resource = entry.getName().replace('.', '/') + ".class";
                jar.putNextEntry(new JarEntry(resource));
                try (InputStream in = entry.getResourceAsStream("/" + resource)) {
                    byte[] bytes = new byte[8192]; int count; while ((count = in.read(bytes)) != -1) jar.write(bytes, 0, count);
                }
                jar.closeEntry();
            }
        }
    }
    static final class CountingBus extends EventBus {
        final Set<Object> listeners = new HashSet<Object>();
        @Override public void register(Object listener) { listeners.add(listener); }
        @Override public void unregister(Object listener) { listeners.remove(listener); }
    }
}
