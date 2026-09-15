package dev.vibe.core;

import java.util.Map;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

/** Bootstrap for the three local 1.8.9 hooks required by MoveFix. */
@IFMLLoadingPlugin.Name("VibeMoveFix")
@IFMLLoadingPlugin.MCVersion("1.8.9")
@IFMLLoadingPlugin.TransformerExclusions({"dev.vibe.core."})
public final class MoveFixLoadingPlugin implements IFMLLoadingPlugin {
    @Override
    public String[] getASMTransformerClass() {
        return new String[] {"dev.vibe.core.MoveFixTransformer", "dev.vibe.core.ChamsTransformer"};
    }

    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public void injectData(Map<String, Object> data) { }
    @Override public String getAccessTransformerClass() { return null; }
}
