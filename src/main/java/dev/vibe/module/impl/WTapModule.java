package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.NumberSetting;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.input.Keyboard;

/**
 * W-Tap with the complete timing/options exposed by Vape's WTap: chance,
 * release delay, re-press delay and selective vulnerable-hit triggering.
 */
public final class WTapModule extends Module {
    private final NumberSetting chance = addSetting(new NumberSetting("Chance", 90.0D, 0.0D, 100.0D, 1.0D));
    private final NumberSetting releaseDelay = addSetting(new NumberSetting("Release Delay (ms)", 0.0D, 0.0D, 500.0D, 1.0D));
    private final NumberSetting rePressDelay = addSetting(new NumberSetting("Re-press Delay (ms)", 50.0D, 0.0D, 500.0D, 1.0D));
    private final BooleanSetting selectHits = addSetting(new BooleanSetting("Select Hits", true));
    private final BooleanSetting playersOnly = addSetting(new BooleanSetting("Players Only", true));

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final Random random = new Random();
    private long releaseAt = -1L;
    private long repressAt = -1L;
    private boolean forcedRelease;

    public WTapModule() {
        super("W-Tab", "Release and re-press forward after a selected hit", Category.COMBAT, Keyboard.KEY_NONE);
    }

    public void tick() {
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.thePlayer.isDead || minecraft.currentScreen != null) {
            clearForcedState();
            return;
        }
        long now = System.currentTimeMillis();
        KeyBinding forward = minecraft.gameSettings.keyBindForward;
        if (releaseAt >= 0L && now >= releaseAt) {
            releaseAt = -1L;
            KeyBinding.setKeyBindState(forward.getKeyCode(), false);
            forcedRelease = true;
            repressAt = now + rePressDelay.getInt();
            DebugModule.log("Wtap", "released forward");
        }
        if (repressAt >= 0L && now >= repressAt) {
            repressAt = -1L;
            // Never invent input: only restore W if the physical forward key
            // is still held. Vanilla remains the owner of later key updates.
            if (isPhysicalDown(forward)) {
                KeyBinding.setKeyBindState(forward.getKeyCode(), true);
                DebugModule.log("Wtap", "re-pressed forward");
            }
            forcedRelease = false;
        }
    }

    /** Called from Minecraft's normal attack event, including aura attacks. */
    public void onAttack(EntityLivingBase target) {
        if (!isEnabled() || minecraft.thePlayer == null || target == null || target.isDead || target.deathTime != 0
                || (playersOnly.isEnabled() && !(target instanceof EntityPlayer)) || releaseAt >= 0L || repressAt >= 0L
                || !isPhysicalDown(minecraft.gameSettings.keyBindForward) || random.nextDouble() * 100.0D > chance.getDouble()) {
            return;
        }
        // The target's damage-resistance window means an immediate re-hit is
        // not a fresh vulnerable hit. Select Hits mirrors Vape's intent while
        // retaining vanilla timing and interaction ownership.
        if (selectHits.isEnabled() && target.hurtResistantTime > 14) return;
        releaseAt = System.currentTimeMillis() + releaseDelay.getInt();
    }

    @Override protected void onEnable() { clearForcedState(); }
    @Override protected void onDisable() { clearForcedState(); }

    private boolean isPhysicalDown(KeyBinding binding) {
        int key = binding.getKeyCode();
        return key > 0 && Keyboard.isKeyDown(key);
    }

    private void clearForcedState() {
        if (forcedRelease && minecraft.gameSettings != null) {
            KeyBinding forward = minecraft.gameSettings.keyBindForward;
            if (isPhysicalDown(forward)) KeyBinding.setKeyBindState(forward.getKeyCode(), true);
        }
        releaseAt = -1L;
        repressAt = -1L;
        forcedRelease = false;
    }

    public NumberSetting getChance() { return chance; }
    public NumberSetting getReleaseDelay() { return releaseDelay; }
    public NumberSetting getRePressDelay() { return rePressDelay; }
    public BooleanSetting getSelectHits() { return selectHits; }
    public BooleanSetting getPlayersOnly() { return playersOnly; }
}
