package dev.vibe.module;

import dev.vibe.module.impl.client.*;
import dev.vibe.module.impl.combat.*;
import dev.vibe.module.impl.meme.*;
import dev.vibe.module.impl.movement.*;
import dev.vibe.module.impl.visual.*;
import dev.vibe.module.impl.world.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModuleManager {

    private final List<Module> modules = new ArrayList<Module>();

    public ModuleManager() {
        register(new EspModule());
        register(new EspEditorModule());
        register(new TargetEspModule());
        register(new ItemEspModule());
        register(new HitmarkerModule());
        register(new CosmeticsModule());
        register(new CosmeticsEditorModule());
        register(new ChestEspModule());
        register(new BedEspModule());
        register(new BlockChangeEspModule());
        register(new BlockOverlayModule());
        register(new CuteVisualsModule());
        register(new TrajectoriesModule());
        register(new FullBrightModule());
        register(new FovChangerModule());
        register(new CustomCrosshairModule());
        register(new CustomCosmeticsModule());
        register(new CustomModelRendererModule());
        register(new FogModule());
        register(new MusicModule());
        register(new AnimationsModule());
        register(new AmbienceModule());
        register(new TargetsModule());
        register(new AimAssistModule());
        register(new BacktrackModule());
        register(new LagRangeModule());
        register(new TickBaseModule());
        register(new TimerRangeModule());
        register(new WTapModule());
        register(new ReachModule());
        register(new VelocityModule());
        register(new AutoClickerModule());
        register(new SprintModule());
        register(new NoSlowModule());
        register(new MoveFixModule());
        register(new TestModule());
        register(new KillAuraModule());
        register(new SpeedModule());
        register(new FlyModule());
        register(new LongJumpModule());
        register(new NoFallModule());
        register(new BlurModule());
        register(new WaifuModule());
        register(new NesEmulatorModule());
        register(new Gta7Module());
        register(new Gta8Module());
        register(new Battlefront3Module());
        register(new GirlfriendModule());
        register(new ChessModule());
        register(new TicTacToeModule());
        register(new FourWinsModule());
        register(new SlotsModule());
        register(new HypixelModule());
        register(new StatisticsModule());
        register(new FlagDetectorModule());
        register(new ParticlesModule());
        register(new DebugModule());
        register(new LanguageModule());
        register(new QolModule());
        register(new NoJumpDelayModule());
        register(new EagleModule());
        register(new FastPlaceModule());
        register(new ScaffoldModule());
        register(new InventoryManagerModule());
        register(new ChestStealerModule());
        register(new AutoToolModule());
        register(new PickenSwitchModule());
        register(new BedAuraModule());
        register(new FastBreakModule());
        register(new FriendsModule());
        register(new HudModule());
        register(new HudEditorModule());
        register(new NameProtectModule());
        register(new InventoryEditorModule());
        register(new FriendEditorModule());
        register(new ConfigEditorModule());
        register(new KeybindEditorModule());
        register(new ScriptsModule());
        register(new ClickGuiModule());
        // Complete the selector before VibeConfig loads or saves its selection.
        // First-frame registration would otherwise overwrite saved visibility.
        getModule(HudModule.class).synchronizeArrayListModules(modules);
    }

    private void register(Module module) {
        modules.add(module);
    }

    /** Registers a module supplied by the local scripting runtime. */
    public void registerDynamic(Module module) {
        if (module != null && !modules.contains(module)) {
            modules.add(module);
        }
    }

    /** Removes an obsolete compiled script module during a reload. */
    public void unregisterDynamic(Module module) {
        if (module != null) {
            if (module.isEnabled()) module.setEnabled(false);
            modules.remove(module);
        }
    }

    public List<Module> getModules() {
        return Collections.unmodifiableList(modules);
    }

    public List<Module> getModules(Category category) {
        List<Module> found = new ArrayList<Module>();
        for (Module module : modules) {
            if (module.getCategory() == category) {
                found.add(module);
            }
        }
        return found;
    }

    public Module getModule(String name) {
        if (name == null) {
            return null;
        }
        for (Module module : modules) {
            if (module.getRawName().equalsIgnoreCase(name) || module.getId().equalsIgnoreCase(name)) {
                return module;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T getModule(Class<T> type) {
        for (Module module : modules) {
            if (type.isInstance(module)) {
                return (T) module;
            }
        }
        return null;
    }
}
