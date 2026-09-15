package keystrokesmod.script;

import dev.vibe.module.Module;

/** Event holder kept source-compatible with Raven's event registration API. */
public class ScriptEvents {
    public final Module module;
    public ScriptEvents(Module module) { this.module = module; }
}
