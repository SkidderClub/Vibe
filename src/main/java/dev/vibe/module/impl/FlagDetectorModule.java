package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.ChatComponentText;
import org.lwjgl.input.Keyboard;

/** Reports small server position corrections after a world has settled. */
public final class FlagDetectorModule extends Module {
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private Object world;
    private int stableTicks;
    private int flags;

    public FlagDetectorModule() { super("Flag Detector", "Reports nearby S08 position corrections", Category.CLIENT, Keyboard.KEY_NONE); }

    public void tick() {
        if (minecraft.theWorld != world) { world = minecraft.theWorld; stableTicks = 0; return; }
        if (world != null) stableTicks++;
    }

    /** Runs on the client thread after the inbound packet is observed. */
    public void observe(S08PacketPlayerPosLook packet) {
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.theWorld == null || minecraft.theWorld != world || stableTicks < 20) return;
        double dx = packet.getX() - minecraft.thePlayer.posX;
        double dy = packet.getY() - minecraft.thePlayer.posY;
        double dz = packet.getZ() - minecraft.thePlayer.posZ;
        // Relative corrections are normal movement packets; resolve them
        // before comparing the final correction distance.
        java.util.Set<S08PacketPlayerPosLook.EnumFlags> relative = packet.func_179834_f();
        if (relative.contains(S08PacketPlayerPosLook.EnumFlags.X)) dx = packet.getX();
        if (relative.contains(S08PacketPlayerPosLook.EnumFlags.Y)) dy = packet.getY();
        if (relative.contains(S08PacketPlayerPosLook.EnumFlags.Z)) dz = packet.getZ();
        if (dx * dx + dy * dy + dz * dz > 9.0D) return;
        flags++;
        minecraft.thePlayer.addChatMessage(new ChatComponentText("§8[§bVibe§8] §fflag detected: §b" + flags));
    }
}
