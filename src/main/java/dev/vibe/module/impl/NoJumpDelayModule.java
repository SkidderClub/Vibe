package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import org.lwjgl.input.Keyboard;

/** Removes EntityLivingBase's built-in delay between jumps. */
public final class NoJumpDelayModule extends Module {

    private final Minecraft minecraft = Minecraft.getMinecraft();

    public NoJumpDelayModule() {
        super("NoJumpDelay", "Removes the vanilla jump delay", Category.MOVEMENT, Keyboard.KEY_NONE);
    }

    public void tick() {
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.currentScreen != null) {
            return;
        }
        ObfuscationReflectionHelper.setPrivateValue(net.minecraft.entity.EntityLivingBase.class,
                minecraft.thePlayer, 0, "field_70773_bE", "jumpTicks");
    }
}
