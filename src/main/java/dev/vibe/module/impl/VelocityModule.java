package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.NumberSetting;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** Scales knockback during the vanilla hurt window. */
public final class VelocityModule extends Module {

    private final MultiSelectSetting modes = addSetting(new MultiSelectSetting("Modes",
            Arrays.asList("Normal", "Jump"), Collections.singletonList("Normal")));
    private final NumberSetting horizontal = addSetting(new NumberSetting("Horizontal", 90.0D, 0.0D, 100.0D, 1.0D,
            () -> modes.isSelected("Normal")));
    private final NumberSetting vertical = addSetting(new NumberSetting("Vertical", 100.0D, 0.0D, 100.0D, 1.0D,
            () -> modes.isSelected("Normal")));
    private final NumberSetting chance = addSetting(new NumberSetting("Jump Chance", 100.0D, 0.0D, 100.0D, 1.0D,
            () -> modes.isSelected("Jump")));
    private final NumberSetting delay = addSetting(new NumberSetting("Jump Delay", 0.0D, 0.0D, 10.0D, 1.0D,
            () -> modes.isSelected("Jump")));
    private final NumberSetting positionThreshold = addSetting(new NumberSetting("Position Threshold (ms)", 100.0D,
            0.0D, 1000.0D, 10.0D));

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final Random random = new Random();
    private int hurtTicks;
    private boolean jumpPending;
    private boolean jumpTriggered;
    private long positionCorrectionUntil;

    public VelocityModule() {
        super("Velocity", "Reduce received knockback", Category.COMBAT, Keyboard.KEY_NONE);
    }

    public void tick() {
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.thePlayer.hurtTime <= 0
                || System.currentTimeMillis() < positionCorrectionUntil) {
            if (minecraft.thePlayer == null || minecraft.thePlayer.hurtTime <= 0) {
                hurtTicks = 0;
                jumpPending = false;
                jumpTriggered = false;
            }
            return;
        }
        if (minecraft.thePlayer.hurtTime == minecraft.thePlayer.maxHurtTime) {
            jumpPending = true;
            jumpTriggered = false;
            hurtTicks = 0;
        }
        if (hurtTicks++ < delay.getInt()) {
            return;
        }
        if (modes.isSelected("Normal")) {
            minecraft.thePlayer.motionX *= horizontal.getDouble() / 100.0D;
            minecraft.thePlayer.motionZ *= horizontal.getDouble() / 100.0D;
            minecraft.thePlayer.motionY *= vertical.getDouble() / 100.0D;
        }
        if (modes.isSelected("Jump") && jumpPending && !jumpTriggered && minecraft.thePlayer.onGround
                && !minecraft.thePlayer.isInWater() && !minecraft.thePlayer.isOnLadder()
                && minecraft.thePlayer.motionY <= 0.0D) {
            if (random.nextDouble() * 100.0D < chance.getDouble()) {
                minecraft.thePlayer.jump();
            }
            jumpTriggered = true;
            jumpPending = false;
        }
    }

    public void markPositionCorrection() {
        positionCorrectionUntil = System.currentTimeMillis() + positionThreshold.getInt();
        hurtTicks = 0;
    }
}
