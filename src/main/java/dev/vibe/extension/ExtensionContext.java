package dev.vibe.extension;

import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.statistics.StatisticsService;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.fml.common.eventhandler.EventBus;

/** Startup registration API. Registrations are rolled back if initialization fails. */
public final class ExtensionContext {
    private final ExtensionManager owner;
    private final ExtensionDescriptor descriptor;
    private final File source;
    private final List<Runnable> cleanup = new ArrayList<Runnable>();
    private final List<Runnable> shutdown = new ArrayList<Runnable>();
    private boolean closed;
    private boolean stopped;

    ExtensionContext(ExtensionManager owner, ExtensionDescriptor descriptor, File source) {
        this.owner = owner; this.descriptor = descriptor; this.source = source;
    }
    public String getId() { return descriptor.id; }
    public File getSource() { return source; }
    public File getGameDirectory() { return owner.gameDirectory; }
    public File getDataDirectory() { return new File(getGameDirectory(), "vibe/extensions/" + getId()); }
    public ModuleManager getModules() { return owner.modules; }
    public StatisticsService getStatistics() { return owner.statistics; }
    /** Register an optional integration or an extension's own shared service. */
    public <T> T registerService(Class<T> type, T service) {
        checkOpen();
        if (type == null || service == null || !type.isInstance(service)) throw new IllegalArgumentException("Invalid service");
        if (owner.services.containsKey(type)) throw new IllegalArgumentException("Service already registered: " + type.getName());
        owner.services.put(type, service);
        cleanup.add(() -> owner.services.remove(type, service));
        return service;
    }

    public <T extends Module> T registerModule(T module) {
        checkOpen();
        if (module == null || module.getId().isEmpty()) throw new IllegalArgumentException("Invalid module");
        if (getModules().getModule(module.getId()) != null)
            throw new IllegalArgumentException("Module id already registered: " + module.getId());
        getModules().registerDynamic(module);
        cleanup.add(() -> getModules().unregisterDynamic(module));
        return module;
    }
    /** Forge render/game events and FML input/tick events. Listeners check their module's enabled state. */
    public void registerEvents(Object listener) {
        checkOpen();
        if (listener == null) throw new IllegalArgumentException("Missing event listener");
        register(owner.forgeEvents, listener);
        if (owner.fmlEvents != owner.forgeEvents) register(owner.fmlEvents, listener);
    }
    private void register(EventBus bus, Object listener) {
        cleanup.add(() -> bus.unregister(listener));
        bus.register(listener);
    }
    /** Runs after MoveFix starts its tick and before built-in combat/scaffold rotation producers. */
    public void onTickStart(Runnable callback) {
        checkOpen();
        ExtensionManager.TickCallback tick = new ExtensionManager.TickCallback(getId(), callback);
        owner.tickCallbacks.add(tick);
        cleanup.add(() -> owner.tickCallbacks.remove(tick));
    }
    public void onActivity(StatisticsService.ActivityListener callback) {
        checkOpen();
        if (getStatistics() == null || callback == null) throw new IllegalStateException("Statistics unavailable");
        StatisticsService.ActivityListener guarded = (action, position, target) -> {
            try { callback.onActivity(action, position, target); }
            catch (RuntimeException | LinkageError | AssertionError error) { owner.report(getId(), "Activity listener failed", error); }
        };
        getStatistics().addActivityListener(guarded);
        cleanup.add(() -> getStatistics().removeActivityListener(guarded));
    }
    public void registerPlayerModel(PlayerModelProvider provider) {
        checkOpen();
        if (provider == null) throw new IllegalArgumentException("Missing player model provider");
        ExtensionManager.ModelProvider model = owner.new ModelProvider(getId(), provider);
        owner.playerModels.add(model);
        cleanup.add(() -> owner.playerModels.remove(model));
    }
    /** Called on initialization rollback or JVM exit. Save data here; do not issue OpenGL calls. */
    public void onShutdown(Runnable callback) {
        checkOpen();
        if (callback == null) throw new IllegalArgumentException("Missing shutdown callback");
        shutdown.add(callback);
    }
    private void checkOpen() { if (closed) throw new IllegalStateException("Extension is closed"); }
    void addCleanup(Runnable callback) { cleanup.add(callback); }
    void close() {
        if (closed) return;
        closed = true;
        shutdown();
        for (int i = cleanup.size() - 1; i >= 0; i--) run(cleanup.get(i));
    }
    void shutdown() {
        if (stopped) return;
        stopped = true;
        for (int i = shutdown.size() - 1; i >= 0; i--) run(shutdown.get(i));
    }
    private void run(Runnable callback) {
        try { callback.run(); }
        catch (RuntimeException | LinkageError | AssertionError error) { owner.report(getId(), "Cleanup failed", error); }
    }
}
