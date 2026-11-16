package dev.vibe.movement;

import dev.vibe.combat.CombatTimerAccess;
import dev.vibe.features.FeatureModule;
import dev.vibe.module.impl.movement.SpeedModule;
import dev.vibe.module.impl.movement.PhaseModule;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import dev.vibe.network.PacketDelayService;

/** Supplied cc.novoline.modules.move.Step, at its native pre-update/step-confirm sites. */
public final class NovolineStep {
    private EntityPlayerSP owner;
    private boolean resetTimer, prepared;
    private double baseY;
    private float height = .625F;

    public void reset(EntityPlayerSP next) {
        restoreTimer();
        if (owner != null) owner.stepHeight = .6F;
        owner = next;
        prepared = false;
    }
    private void restoreTimer() {
        if (resetTimer) { CombatTimerAccess.setSpeed(1); resetTimer = false; }
    }
    public void preUpdate() { restoreTimer(); }
    public float prepare() {
        SpeedModule speed = FeatureModule.module(SpeedModule.class);
        PhaseModule phase = FeatureModule.module(PhaseModule.class);
        boolean liquid = owner.isInWater() || owner.isInLava()
                || owner.worldObj.getBlockState(new net.minecraft.util.BlockPos(owner.posX, owner.posY-.1, owner.posZ)).getBlock() instanceof net.minecraft.block.BlockLiquid;
        height = liquid || speed != null && speed.isEnabled() || phase != null && phase.isEnabled() || !owner.onGround ? .625F : 1;
        baseY = owner.posY;
        prepared = true;
        return owner.stepHeight = height;
    }
    public float height() { return height; }
    public void confirm() {
        if (!prepared || owner == null) return;
        prepared = false;
        double difference = owner.getEntityBoundingBox().minY - baseY;
        if (difference <= .625 || difference > 1) return;
        boolean hypixel = NovolineSupport.hypixel();
        if (hypixel) {
            PacketDelayService.sendUnmodified(owner.sendQueue, new C0BPacketEntityAction(owner, C0BPacketEntityAction.Action.START_SNEAKING));
            owner.setSprinting(false);
            owner.motionX = owner.motionZ = 0;
        }
        CombatTimerAccess.setSpeed(.3F);
        resetTimer = true;
        double first = .41999998688698, second = .7531999805212;
        if (difference != 1) {
            first = Math.min(first * difference, .425);
            second = Math.max(.49, Math.min(.78, second * difference));
        }
        owner.sendQueue.addToSendQueue(new C04PacketPlayerPosition(owner.posX, baseY + first, owner.posZ, false));
        owner.sendQueue.addToSendQueue(new C04PacketPlayerPosition(owner.posX, baseY + second, owner.posZ, false));
        if (hypixel) PacketDelayService.sendUnmodified(owner.sendQueue, new C0BPacketEntityAction(owner, C0BPacketEntityAction.Action.STOP_SNEAKING));
        owner.stepHeight = .625F;
    }
}
