package dev.vibe.ui.render.esp;

import dev.vibe.Vibe;
import dev.vibe.module.impl.world.InventoryManagerModule;
import dev.vibe.module.impl.visual.ItemEspModule;
import dev.vibe.module.impl.meme.HypixelModule;
import dev.vibe.ui.WorldRenderUtils;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;

/** World renderer for ItemESP. */
public final class ItemEspRenderer {
    private final Minecraft minecraft = Minecraft.getMinecraft();

    public void render(RenderWorldLastEvent event) {
        ItemEspModule module = Vibe.getInstance().getModuleManager().getModule(ItemEspModule.class);
        HypixelModule hypixel = Vibe.getInstance().getModuleManager().getModule(HypixelModule.class);
        boolean itemEsp = module != null && module.isEnabled();
        boolean murderGold = hypixel != null && hypixel.isMurderMysteryActive() && hypixel.getHighlightGold().isEnabled();
        if ((!itemEsp && !murderGold) || minecraft.theWorld == null || minecraft.thePlayer == null) return;
        WorldRenderUtils.begin(true);
        try {
            List<?> entities = minecraft.theWorld.loadedEntityList;
            for (Object value : entities) {
                if (!(value instanceof EntityItem)) continue;
                EntityItem item = (EntityItem) value;
                ItemStack stack = item.getEntityItem();
                boolean gold = murderGold && hypixel.shouldHighlightGold(item);
                if (item.isDead || stack == null || (!gold && (!itemEsp || !shouldDraw(module, stack)))) continue;
                double x = item.lastTickPosX + (item.posX - item.lastTickPosX) * event.partialTicks;
                double y = item.lastTickPosY + (item.posY - item.lastTickPosY) * event.partialTicks;
                double z = item.lastTickPosZ + (item.posZ - item.lastTickPosZ) * event.partialTicks;
                AxisAlignedBB box = item.getEntityBoundingBox().offset(x - item.posX - minecraft.getRenderManager().viewerPosX,
                        y - item.posY - minecraft.getRenderManager().viewerPosY, z - item.posZ - minecraft.getRenderManager().viewerPosZ).expand(0.04D, 0.04D, 0.04D);
                // The configured alpha is the fill alpha.  The previous
                // implicit quarter-alpha conversion made the colour picker
                // lie and made fully opaque fills permanently translucent.
                int itemColor = gold ? hypixel.getGoldColor().getArgb() : specialColor(stack, module.getColor().getArgb());
                int fill = itemColor;
                // Valuable drops intentionally keep their recognizable
                // Minecraft material colour for both parts of the ESP.
                int outline = gold ? 0xFF000000 | (itemColor & 0xFFFFFF) : module.getOutline().isEnabled()
                        ? specialColor(stack, module.getOutlineColor().getArgb()) : 0x00000000;
                WorldRenderUtils.box(box, outline, fill, gold ? 1.5F : module.getLineWidth().getFloat());
            }
        } finally {
            WorldRenderUtils.end(true);
        }
    }

    private boolean shouldDraw(ItemEspModule module, ItemStack stack) {
        if (!module.getSmart().isEnabled()) return true;
        if (stack.getItem() == Items.iron_ingot || stack.getItem() == Items.gold_ingot
                || stack.getItem() == Items.diamond || stack.getItem() == Items.emerald) return true;
        return InventoryManagerModule.shouldKeepForItemEsp(stack);
    }

    private int specialColor(ItemStack stack, int configured) {
        int alpha = configured & 0xFF000000;
        if (stack.getItem() == Items.iron_ingot) return alpha | 0x00E8E8E8;
        if (stack.getItem() == Items.gold_ingot) return alpha | 0x00FFD83D;
        if (stack.getItem() == Items.diamond) return alpha | 0x0055FFFF;
        if (stack.getItem() == Items.emerald) return alpha | 0x0055FF55;
        return configured;
    }
}
