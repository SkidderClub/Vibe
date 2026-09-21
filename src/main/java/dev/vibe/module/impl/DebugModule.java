package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.MultiSelectSetting;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import org.lwjgl.input.Keyboard;

/** Optional local diagnostics for timing-sensitive combat helpers. */
public final class DebugModule extends Module {
    private final MultiSelectSetting modes = addSetting(new MultiSelectSetting("Modes",
            Arrays.asList("Wtap", "FirstHit", "Hit on Backtrack"),
            Arrays.asList("Wtap", "FirstHit")));

    public DebugModule() {
        super("Debug", "Print selected module diagnostics in local chat", Category.CLIENT, Keyboard.KEY_NONE);
    }

    public MultiSelectSetting getModes() { return modes; }

    public static void log(String mode, String message) {
        if (dev.vibe.Vibe.getInstance() == null || dev.vibe.Vibe.getInstance().getModuleManager() == null) return;
        DebugModule debug = dev.vibe.Vibe.getInstance().getModuleManager().getModule(DebugModule.class);
        Minecraft minecraft = Minecraft.getMinecraft();
        if (debug == null || !debug.isEnabled() || !debug.modes.isSelectedIgnoreCase(mode) || minecraft.thePlayer == null) return;
        minecraft.addScheduledTask(new Runnable() {
            @Override public void run() {
                if (minecraft.thePlayer != null) {
                    minecraft.thePlayer.addChatMessage(new ChatComponentText("§8[§bVibe Debug§8] §7" + mode + " §f" + message));
                }
            }
        });
    }
}
