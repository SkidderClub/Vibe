package keystrokesmod.script;

import dev.vibe.Vibe;
import dev.vibe.module.Module;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import net.minecraft.client.Minecraft;

/**
 * Raven-compatible manager facade. The actual lifecycle is owned by
 * {@link dev.vibe.script.ScriptRuntime}; this facade keeps the original public
 * fields and entry points available to helper mods and scripts.
 */
public class ScriptManager {
    public final Minecraft mc = Minecraft.getMinecraft();
    public final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    public final File directory = new File(new File(mc.mcDataDir, "vibe"), "scripts");
    public final File COMPILED_DIR = new File(directory, ".compiled");
    public final LinkedHashMap<Script, Module> scripts = new LinkedHashMap<Script, Module>();

    public ScriptManager() { directory.mkdirs(); }
    public String createScript(String name) { return Vibe.getInstance().getScriptRuntime().create(name); }
    public void loadScripts() { Vibe.getInstance().getScriptRuntime().reload(); }
    public Module getModule(Script script) {
        if (script == null) return null;
        for (Map.Entry<Script, Module> entry : scripts.entrySet()) if (entry.getKey() == script) return entry.getValue();
        return Vibe.getInstance().getScriptRuntime().getModule(script.name);
    }
}
