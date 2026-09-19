package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.ui.StatisticsGui;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** Opens Vibe's local encrypted activity dashboard. */
public final class StatisticsModule extends Module {
    public StatisticsModule() { super("Statistics", "View encrypted client and account activity", Category.CLIENT, Keyboard.KEY_NONE); }
    @Override protected void onEnable() { if (!isConfigLoading()) Minecraft.getMinecraft().displayGuiScreen(new StatisticsGui(this)); }
}
