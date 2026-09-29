package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.language.LanguageManager;
import dev.vibe.module.impl.ScaffoldModule;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/** Scaffold's Render Count panel: block icon, fill bar and remaining block total below the crosshair. */
public final class ScaffoldCountRenderer {
    private static final int WIDTH = 154;
    private static final int HEIGHT = 30;
    private static final float BAR_WIDTH = 85.0F;

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private float barWidth;

    public void render() {
        ScaffoldModule scaffold = Vibe.getInstance() == null || Vibe.getInstance().getModuleManager() == null ? null
                : Vibe.getInstance().getModuleManager().getModule(ScaffoldModule.class);
        if (scaffold == null || !scaffold.shouldRenderCount()) {
            barWidth = 0.0F;
            return;
        }
        ItemStack stack = scaffold.getBlockStack();
        int count = scaffold.getBlockCount();
        ScaledResolution resolution = new ScaledResolution(minecraft);
        int left = resolution.getScaledWidth() / 2 - WIDTH / 2;
        int top = resolution.getScaledHeight() / 2 + 13;
        if (DebugOverlay.overlaps(left, top, left + WIDTH, top + HEIGHT)) return;
        FontRenderer font = minecraft.fontRendererObj;
        RenderUtils.roundedRect(left, top, left + WIDTH, top + HEIGHT, 8.0F, 0xB4141414);

        if (stack != null) {
            GlStateManager.pushMatrix();
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            RenderHelper.enableGUIStandardItemLighting();
            minecraft.getRenderItem().renderItemAndEffectIntoGUI(stack, left + 8, top + 7);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableBlend();
            GlStateManager.disableRescaleNormal();
            GlStateManager.popMatrix();
        }

        int base = blockColor(stack);
        float target = BAR_WIDTH * Math.min(1.0F, count / 64.0F);
        barWidth += (target - barWidth) * 0.2F;
        int barLeft = left + 35;
        int barTop = top + 14;
        RenderUtils.roundedRect(barLeft, barTop, barLeft + (int) BAR_WIDTH, barTop + 3, 1.5F, 0x961E1E1E);
        int filled = Math.round(barWidth);
        if (filled > 0) {
            // Darker at the empty end, the block's own colour at the full end.
            int half = Math.max(1, filled / 2);
            Gui.drawRect(barLeft, barTop, barLeft + half, barTop + 3, RenderUtils.blend(darker(base), base, 0.35F));
            Gui.drawRect(barLeft + half, barTop, barLeft + filled, barTop + 3, base);
        }

        String amount = String.valueOf(count);
        String label = LanguageManager.translate(count == 1 ? "Block" : "Blocks");
        int textRight = left + WIDTH - 8;
        font.drawStringWithShadow(amount, textRight - font.getStringWidth(amount), top + 5, 0xFFFFFFFF);
        GlStateManager.pushMatrix();
        GlStateManager.translate(textRight, top + 18, 0.0F);
        GlStateManager.scale(0.75F, 0.75F, 1.0F);
        font.drawStringWithShadow(label, -font.getStringWidth(label), 0, 0xFF9A9A9A);
        GlStateManager.popMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** The block's map colour, brightened like java.awt.Color#brighter. */
    private static int blockColor(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemBlock)) return 0xFFFFFFFF;
        Block block = ((ItemBlock) stack.getItem()).getBlock();
        int rgb;
        try {
            rgb = block.getMapColor(block.getStateFromMeta(stack.getMetadata())).colorValue;
        } catch (RuntimeException invalidMeta) {
            rgb = block.getMapColor(block.getDefaultState()).colorValue;
        }
        return 0xFF000000 | brighter(rgb >> 16 & 255) << 16 | brighter(rgb >> 8 & 255) << 8 | brighter(rgb & 255);
    }

    private static int brighter(int channel) {
        return Math.min(255, Math.max(channel, 3) * 10 / 7);
    }

    private static int darker(int color) {
        int red = (int) ((color >> 16 & 255) * 0.343F);
        int green = (int) ((color >> 8 & 255) * 0.343F);
        int blue = (int) ((color & 255) * 0.343F);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
