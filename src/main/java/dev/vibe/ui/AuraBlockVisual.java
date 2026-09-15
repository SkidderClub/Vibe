package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.impl.KillAuraModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

/** A render-only sword-use scope. Never calls controller use/release methods. */
public final class AuraBlockVisual {
    private AuraBlockVisual() { }

    public static EntityPlayerSP begin() {
        Vibe vibe = Vibe.getInstance();
        if (vibe == null || vibe.getModuleManager() == null) return null;
        KillAuraModule aura = vibe.getModuleManager().getModule(KillAuraModule.class);
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (aura == null || !aura.isVisualBlocking() || player == null || player.isUsingItem()) return null;
        ItemStack held = player.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemSword)) return null;
        player.setItemInUse(held, held.getMaxItemUseDuration());
        return player;
    }

    public static void end(EntityPlayerSP player) {
        if (player != null) player.clearItemInUse();
    }

    public static void applyThirdPerson(Object renderer, Object entity) {
        Vibe vibe = Vibe.getInstance();
        if (vibe == null || vibe.getModuleManager() == null || entity != Minecraft.getMinecraft().thePlayer) return;
        KillAuraModule aura = vibe.getModuleManager().getModule(KillAuraModule.class);
        if (aura != null && aura.isVisualBlocking()) {
            // RenderPlayer resets this model pose on every render. No item-use
            // state is installed in Forge Pre/Post events (which can cancel).
            ((net.minecraft.client.renderer.entity.RenderPlayer) renderer).getMainModel().heldItemRight = 3;
        }
    }
}
