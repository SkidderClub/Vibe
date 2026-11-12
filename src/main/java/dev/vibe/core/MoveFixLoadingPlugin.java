package dev.vibe.core;

import java.util.Map;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

/** Bootstrap for Vibe's client-side movement and rendering hooks. */
@IFMLLoadingPlugin.Name("VibeMoveFix")
@IFMLLoadingPlugin.MCVersion("1.8.9")
@IFMLLoadingPlugin.TransformerExclusions({"dev.vibe.core."})
public final class MoveFixLoadingPlugin implements IFMLLoadingPlugin {
    @Override
    public String[] getASMTransformerClass() {
        return new String[] {
                "dev.vibe.core.MoveFixTransformer",
                "dev.vibe.core.ChamsTransformer",
                "dev.vibe.core.MemeAnimationTransformer",
                "dev.vibe.core.BigbrainTransformer",
                "dev.vibe.core.FontLocalizationTransformer",
                "dev.vibe.core.CustomModelTransformer",
                "dev.vibe.core.HudItemsTransformer",
                "dev.vibe.core.GlintTransformer",
                "dev.vibe.core.BlockDebrisTransformer",
                "dev.vibe.core.ItemPhysicsTransformer",
                "dev.vibe.core.RavenFeatureTransformer",
                "dev.vibe.core.FreecamTransformer",
                "dev.vibe.core.AmbienceSkyTransformer",
                "dev.vibe.core.ChunkAnimationTransformer",
                "dev.vibe.core.ChatCompletionTransformer",
                "dev.vibe.core.ChatterTitleTransformer",
                "dev.vibe.core.HudChatTransformer",
                "dev.vibe.core.RecorderRenderTransformer",
                "dev.vibe.core.HotbarOverlayTransformer",
                "dev.vibe.core.ContainerThemeTransformer",
                "dev.vibe.core.HolidayTransformer",
                "dev.vibe.core.BrandingTransformer"
        };
    }

    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public void injectData(Map<String, Object> data) { }
    @Override public String getAccessTransformerClass() { return null; }
}
