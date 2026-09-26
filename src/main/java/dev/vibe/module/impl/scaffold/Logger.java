package dev.vibe.module.impl.scaffold;

import dev.vibe.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import org.apache.logging.log4j.LogManager;

/** Gothaj's diagnostic call sites, routed to Vibe's logger and chat prefix. */
public final class Logger {
    private Logger() { }
    public static void clientChat(String message) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null && mc.theWorld != null)
            mc.thePlayer.addChatMessage(new ChatComponentText("\u00a78[\u00a7dVibe\u00a78] \u00a77" + message));
    }
    public static void debugChat(Module module, String message) {
        LogManager.getLogger("Vibe/Scaffold").debug(message);
    }
}
