package dev.vibe.module.impl.combat;

import dev.vibe.network.PacketDelayService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

/** Supplied KillAura.sendBlock/sendUnBlock. Owned packets bypass its ordinary-use veto. */
final class NovolineAutoblock {
    private EntityPlayerSP owner;
    private ItemStack stack;
    private int slot;
    private volatile boolean blocking;

    boolean blocking() { return blocking; }
    void validate() {
        EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
        if (owner != p || p == null || p.inventory.currentItem != slot || p.getHeldItem() != stack) release();
    }
    void block() {
        EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
        validate();
        if (p == null || p.sendQueue == null || p.getHeldItem() == null || !(p.getHeldItem().getItem() instanceof ItemSword)) return;
        owner = p; stack = p.getHeldItem(); slot = p.inventory.currentItem;
        // Source Post order: invoke the item's native right-click, then sendBlock.
        stack.useItemRightClick(p.worldObj, p);
        if (!blocking) {
            PacketDelayService.sendUnmodified(p.sendQueue, new C08PacketPlayerBlockPlacement(stack));
            blocking = true;
        }
    }
    void release() {
        EntityPlayerSP p = owner;
        boolean send = blocking;
        blocking = false; owner = null;
        if (p != null && p == Minecraft.getMinecraft().thePlayer) {
            if (p.getItemInUse() == stack) p.clearItemInUse();
            if (send) PacketDelayService.sendUnmodified(p.sendQueue, new C07PacketPlayerDigging(
                    C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
        }
        stack = null;
    }
    boolean veto(Packet<?> packet, boolean shouldBlock) {
        return (blocking || shouldBlock) && (packet instanceof C07PacketPlayerDigging
                && ((C07PacketPlayerDigging) packet).getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM
                || packet instanceof C08PacketPlayerBlockPlacement && ((C08PacketPlayerBlockPlacement) packet).getPlacedBlockDirection() == 255);
    }
}
