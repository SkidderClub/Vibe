package dev.vibe.module.impl;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ChatComponentText;
import org.lwjgl.input.Keyboard;

/** Direct Vibe bridge of Gothaj's TimerRange module. */
public final class TimerRangeModule extends Module {
    private int balance;
    private boolean preStateIdle;
    private EntityLivingBase target;
    private long delayTimer;
    private final BooleanSetting onlyKillAura = addSetting(new BooleanSetting("Only Kill Aura", true));
    private final ModeSetting mode = addSetting(new ModeSetting("Mode", "Pre", "Pre", "Post"));
    private final NumberSetting range = addSetting(new NumberSetting("Range", 4.0D, 3.3D, 8.0D, 0.1D));
    private final NumberSetting chargeTimer = addSetting(new NumberSetting("Charge Timer", 0.0D, 0.0D, 1.0D, 0.01D));
    private final NumberSetting delay = addSetting(new NumberSetting("Delay", 200.0D, 0.0D, 3000.0D, 50.0D));
    private final BooleanSetting onlyOnGround = addSetting(new BooleanSetting("Only On Ground", true));
    private final BooleanSetting debug = addSetting(new BooleanSetting("Debug", false));
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private boolean executing;
    private Object sessionWorld;

    public TimerRangeModule() { super("TimerRange", "teleports to player to give you reach", Category.COMBAT, Keyboard.KEY_NONE); }
    @Override protected void onEnable() { CombatTimerAccess.setSpeed(1.0F); reset(); }
    @Override protected void onDisable() { CombatTimerAccess.setSpeed(1.0F); reset(); }

    /** Bridges Gothaj's TickEvent followed by TimeDelayEvent. */
    public void tick() {
        if (!isEnabled() || executing) return;
        synchronizeWorld();
        executing = true;
        try {
            balance++;
            handleTimeDelay();
        } catch (Throwable ignored) {
            // Timer ownership must never strand the game loop at zero when a
            // world unload or entity list changes during a charged frame.
            CombatTimerAccess.setSpeed(1.0F);
            reset();
        } finally { executing = false; }
    }

    /**
     * Gothaj's TimeDelayEvent continues while its timer is charging. Forge's
     * ClientTickEvent does not fire after a zero-speed Timer has stopped
     * producing game ticks, so RenderTick is the equivalent live clock here.
     * Without this bridge Charge Timer = 0 would permanently freeze Vibe.
     */
    public void frameTick() {
        if (!isEnabled() || executing) return;
        synchronizeWorld();
        if (CombatTimerAccess.getSpeed() > 0.0F) return;
        executing = true;
        try {
            handleTimeDelay();
        } catch (Throwable ignored) {
            CombatTimerAccess.setSpeed(1.0F);
            reset();
        } finally { executing = false; }
    }

    private void handleTimeDelay() {
        if (minecraft.thePlayer == null || minecraft.theWorld == null) { resetTimer(); return; }
        if (CombatTimerAccess.getSpeed() == chargeTimer.getFloat()) {
            for (Object value : minecraft.theWorld.loadedEntityList) {
                if (!(value instanceof EntityLivingBase) || value == minecraft.thePlayer) continue;
                try { minecraft.theWorld.updateEntity((Entity) value); } catch (Throwable ignored) { }
            }
        }
        balance--;
        if (mode.is("Pre")) handlePre(); else handlePost();
    }

    private void handlePre() {
        canWork();
        if (target == null) return;
        int maxTicks = -getTicks();
        if (balance == maxTicks && balance < 0) {
            CombatTimerAccess.setSpeed(1.0F);
            if ((!canWork() || isClose()) && minecraft.thePlayer.hurtTime == 0) return;
            delayTimer = System.currentTimeMillis();
            for (int index = maxTicks; index < 0; index++) {
                CombatTickRunner.runQuietly();
                if (!isClose()) continue;
                if (debug.isEnabled()) chat("Timer Range > hit on target");
                break;
            }
            return;
        }
        if (canWork() && !isClose() && elapsed(delay.getInt(), false)) chargeReleaseIdle(maxTicks);
        else chargeReleaseIdle(0);
    }

    private void handlePost() {
        if (balance == 0) {
            CombatTimerAccess.setSpeed(1.0F);
            if (canWork() && !isClose() && elapsed(delay.getInt(), true)) {
                int maxTicks = getTicks();
                for (int index = 0; index < maxTicks + 1; index++) {
                    if (isClose()) { if (debug.isEnabled()) chat("Timer Range > hit on target"); continue; }
                    CombatTickRunner.runQuietly();
                }
            }
        } else chargeReleaseIdle(0);
    }

    private boolean canWork() {
        if (minecraft.thePlayer == null || minecraft.theWorld == null) { target = null; resetTimer(); return false; }
        target = CombatRangeSupport.getTarget(14.0D);
        if (target == null) { resetTimer(); return false; }
        if (CombatRangeSupport.distanceToBox(target) > range.getDouble()) { target = null; resetTimer(); return false; }
        KillAuraModule aura = Vibe.getInstance().getModuleManager().getModule(KillAuraModule.class);
        if (((aura == null || !aura.isEnabled()) && onlyKillAura.isEnabled()) || minecraft.thePlayer.ticksExisted < 10
                || (onlyOnGround.isEnabled() && !minecraft.thePlayer.onGround) || CombatRangeSupport.isInWeb(minecraft.thePlayer)
                || minecraft.thePlayer.isInLava() || minecraft.thePlayer.isInWater() || minecraft.thePlayer.isCollidedHorizontally
                || !minecraft.gameSettings.keyBindForward.isKeyDown() || minecraft.thePlayer.hurtTime > 0) {
            target = null;
            resetTimer();
            return false;
        }
        return true;
    }

    private void chargeReleaseIdle(int tick) {
        if (tick == Integer.MIN_VALUE) return;
        if (balance > tick) CombatTimerAccess.setSpeed(chargeTimer.getFloat());
        else if (balance < tick) CombatTickRunner.runQuietly();
    }

    private int getTicks() {
        if (target == null || minecraft.thePlayer == null) return 0;
        MoveFixModule moveFix = Vibe.getInstance().getModuleManager().getModule(MoveFixModule.class);
        float yaw = moveFix == null ? minecraft.thePlayer.rotationYaw : moveFix.getRotationYaw();
        List<CombatRangeSupport.Snapshot> predictions = CombatRangeSupport.simulate(minecraft.thePlayer, getEstimatedTicks(), yaw);
        for (int index = 0; index < predictions.size(); index++) {
            CombatRangeSupport.Snapshot snapshot = predictions.get(index);
            if (CombatRangeSupport.distanceToBoxFromPosition(snapshot.position.xCoord, snapshot.position.yCoord, snapshot.position.zCoord, target) <= 3.0D) return index + 1;
        }
        return 0;
    }

    private boolean isClose() { return CombatRangeSupport.distanceToBox(target) <= 3.0D; }
    private void synchronizeWorld() {
        Object world = minecraft.theWorld;
        if (world == sessionWorld) return;
        sessionWorld = world;
        CombatTimerAccess.setSpeed(1.0F);
        reset();
    }
    private void reset() { preStateIdle = true; balance = 0; target = null; delayTimer = 0L; executing = false; }
    private void resetTimer() { CombatTimerAccess.setSpeed(1.0F); }
    private boolean elapsed(long milliseconds, boolean reset) {
        if (System.currentTimeMillis() - delayTimer < milliseconds) return false;
        if (reset) delayTimer = System.currentTimeMillis();
        return true;
    }
    private int getEstimatedTicks() {
        return target == null ? 0 : (int) Math.min(Math.floor((CombatRangeSupport.distanceToBox(target) - 3.0D)
                / CombatRangeSupport.baseMoveSpeed()) * 3.0D, (range.getDouble() - 3.0D) / CombatRangeSupport.baseMoveSpeed() * 3.0D);
    }
    private void chat(String text) { if (minecraft.thePlayer != null) minecraft.thePlayer.addChatMessage(new ChatComponentText(text)); }
}
