package dev.vibe.module.impl.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

/** Exire KillAuraNcpAutoBlock and IAutoBlock.unblock: post-hit C08, release when done. */
final class RewoundAutoblock {
    private EntityPlayerSP owner;
    private int slot;

    boolean blocking() {
        EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
        return owner != null && owner == p && p.inventory.currentItem == slot
                && p.getHeldItem() != null && p.getHeldItem().getItem() instanceof ItemSword;
    }

    void afterAttack() {
        EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
        if (p == null || p.sendQueue == null || p.getHeldItem() == null || !(p.getHeldItem().getItem() instanceof ItemSword)) return;
        p.sendQueue.addToSendQueue(new C08PacketPlayerBlockPlacement(p.getHeldItem()));
        owner = p;
        slot = p.inventory.currentItem;
    }

    void release() {
        EntityPlayerSP p = owner;
        owner = null;
        if (p != null && p == Minecraft.getMinecraft().thePlayer && p.sendQueue != null) {
            p.sendQueue.addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                    BlockPos.ORIGIN, EnumFacing.DOWN));
        }
    }
}
