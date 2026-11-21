package dev.vibe.module.impl.world;

import dev.vibe.combat.CombatTimerAccess;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** A user-set multiplier for the local game timer. */
public final class TimerModule extends Module {
    private final NumberSetting speed = addSetting(new NumberSetting("Timer Speed", 1.2, .1, 10, .1));

    public TimerModule() {
        super("Timer", "Set the client tick rate", Category.WORLD, Keyboard.KEY_NONE);
    }

    @Override protected void onEnable() { tick(); }

    @Override protected void onDisable() { CombatTimerAccess.releaseTemporarySpeed(this); }

    public void tick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.thePlayer == null || mc.thePlayer.worldObj != mc.theWorld) {
            CombatTimerAccess.releaseTemporarySpeed(this);
            return;
        }
        CombatTimerAccess.setTemporarySpeed(this, speed.getFloat());
    }
}
