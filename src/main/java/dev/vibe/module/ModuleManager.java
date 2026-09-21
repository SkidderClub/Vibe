package dev.vibe.module;

import dev.vibe.module.impl.SprintModule;
import dev.vibe.module.impl.AutoClickerModule;
import dev.vibe.module.impl.AimAssistModule;
import dev.vibe.module.impl.ChestEspModule;
import dev.vibe.module.impl.ClickGuiModule;
import dev.vibe.module.impl.EagleModule;
import dev.vibe.module.impl.EspModule;
import dev.vibe.module.impl.FastPlaceModule;
import dev.vibe.module.impl.FovChangerModule;
import dev.vibe.module.impl.FullBrightModule;
import dev.vibe.module.impl.HudEditorModule;
import dev.vibe.module.impl.HudModule;
import dev.vibe.module.impl.ReachModule;
import dev.vibe.module.impl.WTapModule;
import dev.vibe.module.impl.DebugModule;
import dev.vibe.module.impl.LanguageModule;
import dev.vibe.module.impl.NameProtectModule;
import dev.vibe.module.impl.NoJumpDelayModule;
import dev.vibe.module.impl.NoSlowModule;
import dev.vibe.module.impl.MoveFixModule;
import dev.vibe.module.impl.TestModule;
import dev.vibe.module.impl.KillAuraModule;
import dev.vibe.module.impl.ItemEspModule;
import dev.vibe.module.impl.Gta7Module;
import dev.vibe.module.impl.Battlefront3Module;
import dev.vibe.module.impl.GirlfriendModule;
import dev.vibe.module.impl.TargetsModule;
import dev.vibe.module.impl.TargetEspModule;
import dev.vibe.module.impl.CosmeticsModule;
import dev.vibe.module.impl.CosmeticsEditorModule;
import dev.vibe.module.impl.VelocityModule;
import dev.vibe.module.impl.FlyModule;
import dev.vibe.module.impl.SpeedModule;
import dev.vibe.module.impl.LongJumpModule;
import dev.vibe.module.impl.NoFallModule;
import dev.vibe.module.impl.BlurModule;
import dev.vibe.module.impl.WaifuModule;
import dev.vibe.module.impl.CustomCrosshairModule;
import dev.vibe.module.impl.CustomCosmeticsModule;
import dev.vibe.module.impl.AnimationsModule;
import dev.vibe.module.impl.ParticlesModule;
import dev.vibe.module.impl.QolModule;
import dev.vibe.module.impl.HitmarkerModule;
import dev.vibe.module.impl.InventoryEditorModule;
import dev.vibe.module.impl.InventoryManagerModule;
import dev.vibe.module.impl.ChestStealerModule;
import dev.vibe.module.impl.AutoToolModule;
import dev.vibe.module.impl.BedAuraModule;
import dev.vibe.module.impl.FastBreakModule;
import dev.vibe.module.impl.FriendsModule;
import dev.vibe.module.impl.FriendEditorModule;
import dev.vibe.module.impl.ConfigEditorModule;
import dev.vibe.module.impl.KeybindEditorModule;
import dev.vibe.module.impl.EspEditorModule;
import dev.vibe.module.impl.AmbienceModule;
import dev.vibe.module.impl.NesEmulatorModule;
import dev.vibe.module.impl.BacktrackModule;
import dev.vibe.module.impl.LagRangeModule;
import dev.vibe.module.impl.TickBaseModule;
import dev.vibe.module.impl.TimerRangeModule;
import dev.vibe.module.impl.BedEspModule;
import dev.vibe.module.impl.BlockChangeEspModule;
import dev.vibe.module.impl.BlockOverlayModule;
import dev.vibe.module.impl.CuteVisualsModule;
import dev.vibe.module.impl.TrajectoriesModule;
import dev.vibe.module.impl.ScriptsModule;
import dev.vibe.module.impl.ChessModule;
import dev.vibe.module.impl.TicTacToeModule;
import dev.vibe.module.impl.FourWinsModule;
import dev.vibe.module.impl.SlotsModule;
import dev.vibe.module.impl.StatisticsModule;
import dev.vibe.module.impl.FlagDetectorModule;
import dev.vibe.module.impl.HypixelModule;
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
        register(new dev.vibe.module.impl.FogModule());
        register(new dev.vibe.module.impl.MusicModule());
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
        register(new InventoryManagerModule());
        register(new ChestStealerModule());
        register(new AutoToolModule());
        register(new dev.vibe.module.impl.PickenSwitchModule());
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
