package dev.vibe.ui.menu;
import net.minecraft.client.gui.GuiScreen;
/** Compatibility entry point: shader settings now open the complete theme browser. */
@Deprecated
public final class ShaderMenuGui extends MenuThemesGui {
    public ShaderMenuGui(GuiScreen parent,MainMenuShaderManager shaders){super(parent,shaders);}
}
