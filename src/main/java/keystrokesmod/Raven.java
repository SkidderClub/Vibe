package keystrokesmod;

import dev.vibe.Vibe;
import dev.vibe.module.ModuleManager;
import keystrokesmod.script.ScriptManager;

/**
 * Minimal public Raven facade for scripts that explicitly reference
 * {@code Raven.scriptManager}.  It intentionally exposes Vibe's managers
 * rather than carrying a second competing client state.
 */
public final class Raven {
    public static boolean DEBUG;
    public static final ScriptManager scriptManager = new ScriptManager();

    private Raven() { }

    public static ModuleManager getModuleManager() {
        return Vibe.getInstance().getModuleManager();
    }
}
