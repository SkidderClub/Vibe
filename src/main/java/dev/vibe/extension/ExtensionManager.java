package dev.vibe.extension;

import dev.vibe.module.ModuleManager;
import dev.vibe.model.CompiledModel;
import dev.vibe.module.impl.client.HudModule;
import dev.vibe.statistics.StatisticsService;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Discovers optional extension JARs once, before profile restoration. No extensions are embedded. */
public final class ExtensionManager {
    private static final Logger LOG = LogManager.getLogger("Vibe extensions");
    interface ClassPath { Class<?> load(File jar, String entrypoint) throws Exception; }
    interface Resources { Runnable mount(File jar); }
    final File gameDirectory;
    final ModuleManager modules;
    final StatisticsService statistics;
    final EventBus forgeEvents, fmlEvents;
    final List<TickCallback> tickCallbacks = new ArrayList<TickCallback>();
    final List<ModelProvider> playerModels = new ArrayList<ModelProvider>();
    final Map<Class<?>, Object> services = new HashMap<Class<?>, Object>();
    private final ClassPath classes;
    private final Resources resources;
    private final List<ExtensionContext> contexts = new ArrayList<ExtensionContext>();
    private final List<String> status = new ArrayList<String>();
    private final List<ExtensionDescriptor> loaded = new ArrayList<ExtensionDescriptor>();
    private final Set<String> reported = new HashSet<String>();
    private boolean discovered;

    public ExtensionManager(File gameDirectory, ModuleManager modules, StatisticsService statistics,
            EventBus forgeEvents, EventBus fmlEvents) {
        this(gameDirectory, modules, statistics, forgeEvents, fmlEvents, (jar, name) -> {
            // A separate URLClassLoader would bypass Forge's Minecraft/ASM transformations.
            if (Launch.classLoader == null) throw new IllegalStateException("Forge LaunchClassLoader unavailable");
            Launch.classLoader.addURL(jar.toURI().toURL());
            // Mount resources before constructors or static initializers can request textures.
            return Class.forName(name, false, Launch.classLoader);
        }, ExtensionResources::mount);
    }
    ExtensionManager(File gameDirectory, ModuleManager modules, StatisticsService statistics,
            EventBus forgeEvents, EventBus fmlEvents, ClassPath classes, Resources resources) {
        this.gameDirectory = gameDirectory; this.modules = modules; this.statistics = statistics;
        this.forgeEvents = forgeEvents; this.fmlEvents = fmlEvents; this.classes = classes; this.resources = resources;
    }
    public void load() {
        if (discovered) return;
        discovered = true;
        Map<File, ExtensionDescriptor> candidates = new java.util.LinkedHashMap<File, ExtensionDescriptor>();
        List<File> jars = new ArrayList<File>();
        collect(new File(gameDirectory, "mods"), jars);
        collect(new File(gameDirectory, "mods/1.8.9"), jars);
        jars.sort(Comparator.comparing(File::getAbsolutePath, String.CASE_INSENSITIVE_ORDER));
        Map<String, Integer> ids = new HashMap<String, Integer>(), entrypoints = new HashMap<String, Integer>();
        for (File jar : jars) {
            try (JarFile archive = new JarFile(jar)) {
                ExtensionDescriptor descriptor = ExtensionDescriptor.read(archive);
                if (descriptor == null) continue; // An ordinary Forge mod.
                candidates.put(jar, descriptor);
                ids.put(descriptor.id, ids.getOrDefault(descriptor.id, 0) + 1);
                entrypoints.put(descriptor.entrypoint, entrypoints.getOrDefault(descriptor.entrypoint, 0) + 1);
            } catch (Exception error) { report(jar.getName(), "Cannot read extension", error); }
        }
        for (Map.Entry<File, ExtensionDescriptor> candidate : candidates.entrySet()) {
            ExtensionDescriptor descriptor = candidate.getValue();
            if (ids.get(descriptor.id) > 1 || entrypoints.get(descriptor.entrypoint) > 1) {
                report(candidate.getKey().getName(), "Duplicate extension id or entrypoint: " + descriptor.id, null);
                continue;
            }
            ExtensionContext context = new ExtensionContext(this, descriptor, candidate.getKey());
            try {
                Class<?> type = classes.load(candidate.getKey(), descriptor.entrypoint);
                if (!VibeExtension.class.isAssignableFrom(type)) throw new IllegalArgumentException("Entrypoint must implement VibeExtension");
                context.addCleanup(resources.mount(candidate.getKey()));
                VibeExtension extension = (VibeExtension) type.getConstructor().newInstance();
                extension.initialize(context);
                contexts.add(context); loaded.add(descriptor);
                String message = descriptor.name + " " + descriptor.version + " loaded";
                status.add(message); LOG.info(message);
            } catch (Exception | LinkageError | AssertionError error) {
                context.close();
                report(descriptor.id, "Initialization failed", error);
            }
        }
        HudModule hud = modules.getModule(HudModule.class);
        if (hud != null) hud.synchronizeArrayListModules(modules.getModules());
    }
    private static void collect(File folder, List<File> jars) {
        File[] files = folder.listFiles(file -> file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (files != null) jars.addAll(Arrays.asList(files));
    }
    public List<ExtensionDescriptor> getLoaded() { return Collections.unmodifiableList(loaded); }
    /** Optional typed integrations; null when the supplying extension is absent. */
    public <T> T getService(Class<T> type) { return type.cast(services.get(type)); }
    public List<String> getStatus() { return Collections.unmodifiableList(status); }
    public void tickStart() {
        for (TickCallback tick : new ArrayList<TickCallback>(tickCallbacks)) {
            if (tick.failed) continue;
            try { tick.callback.run(); }
            catch (RuntimeException | LinkageError | AssertionError error) { tick.failed = true; report(tick.id, "Tick callback disabled after an error", error); }
        }
    }
    /** First registered applicable provider wins; a failed provider falls back to normal rendering. */
    public PlayerModelProvider playerModel(EntityPlayer player) {
        if (player == null) return null;
        for (ModelProvider model : playerModels) {
            if (model.failed) continue;
            try { if (model.provider.appliesTo(player)) return model; }
            catch (RuntimeException | LinkageError | AssertionError error) { model.failed = true; report(model.id, "Player model provider disabled after an error", error); }
        }
        return null;
    }
    public void close() {
        for (int i = contexts.size() - 1; i >= 0; i--) contexts.get(i).close();
        contexts.clear(); loaded.clear();
    }
    public void shutdown() { for (ExtensionContext context : contexts) context.shutdown(); }
    void report(String id, String message, Throwable error) {
        String detail = id + ": " + message + (error == null ? "" : " (" + error + ")");
        if (reported.add(detail)) { status.add(detail); LOG.error(detail, error); }
    }
    static final class TickCallback {
        final String id; final Runnable callback; boolean failed;
        TickCallback(String id, Runnable callback) {
            if (callback == null) throw new IllegalArgumentException("Missing tick callback");
            this.id = id; this.callback = callback;
        }
    }
    final class ModelProvider implements PlayerModelProvider {
        final String id; final PlayerModelProvider provider; boolean failed;
        ModelProvider(String id, PlayerModelProvider provider) { this.id = id; this.provider = provider; }
        public boolean appliesTo(EntityPlayer player) { return !failed && provider.appliesTo(player); }
        public CompiledModel model() {
            if (failed) return null;
            try { return provider.model(); }
            catch (RuntimeException | LinkageError | AssertionError error) {
                failed = true; report(id, "Player model provider disabled after an error", error); return null;
            }
        }
        public boolean hideArmor() {
            if (failed) return false;
            try { return provider.hideArmor(); }
            catch (RuntimeException | LinkageError | AssertionError error) {
                failed = true; report(id, "Player model provider disabled after an error", error); return false;
            }
        }
    }
}
