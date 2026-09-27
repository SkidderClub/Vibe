package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.ui.Gta8Gui;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** Opens GTA8: Los Vibes, a realistic open-world city with its own shader renderer. */
public final class Gta8Module extends Module {
    public final ModeSetting graphics = addSetting(new ModeSetting("Graphics", "High", "Low", "Medium", "High", "Ultra"));
    public final NumberSetting renderScale = addSetting(new NumberSetting("Render Scale", 100, 50, 200, 5));
    public final ModeSetting density = addSetting(new ModeSetting("Traffic Density", "Medium", "Low", "Medium", "High", "Very High"));
    public final BooleanSetting bloom = addSetting(new BooleanSetting("Bloom", true));
    public final BooleanSetting antialiasing = addSetting(new BooleanSetting("FXAA", true));
    public final BooleanSetting sound = addSetting(new BooleanSetting("Game Audio", true));

    public Gta8Module() {
        super("GTA8", "Los Vibes: a realistic open city with driving physics, traffic, police, weather and a shader renderer", Category.MEME, Keyboard.KEY_NONE);
    }

    @Override protected void onEnable() {
        if (isConfigLoading()) { setEnabled(false); return; }
        Minecraft.getMinecraft().displayGuiScreen(new Gta8Gui(this));
    }

    public int qualityLevel() {
        String g = graphics.getValue();
        return "Low".equals(g) ? 0 : "Medium".equals(g) ? 1 : "Ultra".equals(g) ? 3 : 2;
    }
    public int densityLevel() {
        String d = density.getValue();
        return "Low".equals(d) ? 0 : "High".equals(d) ? 2 : "Very High".equals(d) ? 3 : 1;
    }
}
