package dev.vibe.module.impl.world;

import dev.vibe.module.*;
import dev.vibe.setting.RangeSetting;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.input.Keyboard;

public final class AutoRespawnModule extends dev.vibe.module.Module {
    public final RangeSetting delay = addSetting(new RangeSetting("Delay (ms)", 500, 1000, 0, 10000, 50));
    private final Minecraft mc = Minecraft.getMinecraft();
    private final Random random = new Random();
    private EntityPlayer player;
    private Object world;
    private long due = -1;
    private boolean sent;
    public AutoRespawnModule() { super("AutoRespawn", "Respawn once after the configured death-screen delay", Category.WORLD, Keyboard.KEY_NONE); }
    public void tick() { tick(System.nanoTime() / 1000000L); }
    public void tick(long now) {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) { reset(); return; }
        if (player != mc.thePlayer || world != mc.theWorld) { reset(); player = mc.thePlayer; world = mc.theWorld; }
        if (!player.isDead && player.getHealth() > 0) { due = -1; sent = false; return; }
        if (!(mc.currentScreen instanceof GuiGameOver)) { due = -1; return; }
        if (mc.theWorld.getWorldInfo() != null && mc.theWorld.getWorldInfo().isHardcoreModeEnabled()) return;
        if (sent) return;
        if (due < 0) due = now + delay.getMinInt() + random.nextInt(delay.getMaxInt() - delay.getMinInt() + 1);
        if (now < due) return;
        // The same method/button path as vanilla's death screen, including its client-status packet.
        player.respawnPlayer(); sent = true; mc.displayGuiScreen(null);
    }
    @Override protected void onDisable() { reset(); }
    private void reset() { player = null; world = null; due = -1; sent = false; }
}
