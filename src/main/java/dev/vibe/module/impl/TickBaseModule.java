package dev.vibe.module.impl;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.RangeSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;

/** Direct state/settings port of Gothaj's TickBase module. */
public final class TickBaseModule extends Module {
    private final RangeSetting hitRange = addSetting(new RangeSetting("Range", 3.0D, 4.0D, 0.5D, 7.0D, 0.1D));
    private final NumberSetting balanceRegen = addSetting(new NumberSetting("Balance Regen", 1.0D, 0.0D, 2.0D, 0.1D));
    private final NumberSetting maxBalance = addSetting(new NumberSetting("Max Balance", 20.0D, 0.0D, 200.0D, 1.0D));
    private final NumberSetting maxSkips = addSetting(new NumberSetting("Max Skips At Once", 4.0D, 1.0D, 20.0D, 1.0D));
    private final NumberSetting postSkipPause = addSetting(new NumberSetting("Pause After Skip", 0.0D, 0.0D, 20.0D, 1.0D));
    private final NumberSetting skipCooldown = addSetting(new NumberSetting("Skip Cooldown", 0.0D, 0.0D, 100.0D, 1.0D));
    private final BooleanSetting requireGround = addSetting(new BooleanSetting("Require Ground", false));
    private final BooleanSetting requireKillAura = addSetting(new BooleanSetting("Only With KillAura", true));
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final List<CombatRangeSupport.Snapshot> simulationHistory = new ArrayList<CombatRangeSupport.Snapshot>();
    private int pendingPauseTicks;
    private int skipTicksBuffer;
    private float tickStock;
    private int skipCooldownTicks;
    private EntityLivingBase target;
    private boolean executing;
    private Object sessionWorld;

    public TickBaseModule() { super("TickBase", "", Category.COMBAT, Keyboard.KEY_NONE); }

    @Override protected void onEnable() { reset(); }
    @Override protected void onDisable() { reset(); simulationHistory.clear(); }

    /** Bridges Gothaj's LivingUpdate, Move and NormalUpdate event sequence. */
    public void tick() {
        if (!isEnabled() || executing) return;
        synchronizeWorld();
        executing = true;
        try {
            updateTargetAndBalance();
            buildSimulationHistory();
            handleNormalUpdate();
        } finally {
            executing = false;
        }
    }

    private void updateTargetAndBalance() {
        BacktrackModule backtrack = Vibe.getInstance().getModuleManager().getModule(BacktrackModule.class);
        KillAuraModule aura = Vibe.getInstance().getModuleManager().getModule(KillAuraModule.class);
        Entity entity = backtrack != null && backtrack.isEnabled() && minecraft.theWorld != null && backtrack.getTargetId() >= 0
                ? minecraft.theWorld.getEntityByID(backtrack.getTargetId()) : null;
        target = entity instanceof EntityLivingBase ? (EntityLivingBase) entity
                : aura != null && aura.getTarget() != null ? aura.getTarget() : CombatRangeSupport.getTarget(7.0D);
        if (tickStock < maxBalance.getFloat()) {
            tickStock += balanceRegen.getFloat();
            if (tickStock > maxBalance.getFloat()) tickStock = maxBalance.getFloat();
        }
    }

    private void buildSimulationHistory() {
        simulationHistory.clear();
        if (minecraft.thePlayer == null) return;
        MoveFixModule moveFix = Vibe.getInstance().getModuleManager().getModule(MoveFixModule.class);
        float yaw = moveFix == null ? minecraft.thePlayer.rotationYaw : moveFix.getRotationYaw();
        int steps = Math.min((int) tickStock, maxSkips.getInt());
        simulationHistory.addAll(CombatRangeSupport.simulate(minecraft.thePlayer, steps, yaw));
    }

    private void handleNormalUpdate() {
        if (pendingPauseTicks > 0) { pendingPauseTicks--; return; }
        if (skipTicksBuffer-- > 0) return;
        if (skipCooldownTicks > 0) { skipCooldownTicks--; return; }
        if (minecraft.thePlayer == null || target == null || simulationHistory.isEmpty()) return;
        if (minecraft.thePlayer.isDead || minecraft.thePlayer.isSpectator() || target.isDead) return;
        EntityLivingBase valid = validateTarget();
        if (valid == null) return;
        double playerDistSq = minecraft.thePlayer.getPositionVector().squareDistanceTo(valid.getPositionVector());
        double minRangeSq = Math.pow(hitRange.getMin(), 2.0D);
        double maxRangeSq = Math.pow(hitRange.getMax(), 2.0D);
        List<Integer> validTicks = new ArrayList<Integer>();
        for (int index = 0; index < simulationHistory.size(); index++) {
            CombatRangeSupport.Snapshot snapshot = simulationHistory.get(index);
            double distance = snapshot.position.distanceTo(valid.getPositionVector());
            double distanceSq = distance * distance;
            if (distanceSq < playerDistSq && distanceSq >= minRangeSq && distanceSq <= maxRangeSq
                    && (!requireGround.isEnabled() || snapshot.onGround)) validTicks.add(index);
        }
        if (validTicks.isEmpty()) return;
        int bestAdvance = validTicks.get(0);
        if (bestAdvance == 0) return;
        KillAuraModule aura = Vibe.getInstance().getModuleManager().getModule(KillAuraModule.class);
        if (requireKillAura.isEnabled() && (aura == null || !aura.isEnabled())) return;
        skipTicksBuffer = bestAdvance;
        pendingPauseTicks = postSkipPause.getInt();
        for (int index = 0; index < bestAdvance; index++) {
            CombatTickRunner.runOrThrow();
            tickStock -= 1.0F;
        }
        skipCooldownTicks = skipCooldown.getInt();
    }

    private EntityLivingBase validateTarget() {
        if (target == null || minecraft.thePlayer == null || target.isDead || minecraft.thePlayer.isDead || minecraft.thePlayer.isSpectator()) return null;
        KillAuraModule aura = Vibe.getInstance().getModuleManager().getModule(KillAuraModule.class);
        if (requireKillAura.isEnabled() && (aura == null || !aura.isEnabled())) return null;
        return minecraft.thePlayer.canEntityBeSeen(target) ? target : null;
    }

    private void reset() {
        skipTicksBuffer = 0;
        tickStock = 0.0F;
        skipCooldownTicks = 0;
        pendingPauseTicks = 0;
        target = null;
        executing = false;
    }

    private void synchronizeWorld() {
        Object world = minecraft.theWorld;
        if (world == sessionWorld) return;
        sessionWorld = world;
        reset();
        simulationHistory.clear();
    }
}
