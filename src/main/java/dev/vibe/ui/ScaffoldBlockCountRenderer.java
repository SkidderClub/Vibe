/* Gothaj counter rendering, revision 8aa9e1c056bc5b6393b29dc03bcb06b529b40a07. See LICENSES/GOTHAJ-SCAFFOLD.md. */
package dev.vibe.ui;
import java.awt.Color;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import dev.vibe.module.impl.scaffold.InventoryUtil;
import dev.vibe.ui.scaffold.CFont;
import dev.vibe.ui.scaffold.CounterRender;
import net.minecraft.client.Minecraft;
import java.awt.Font;
import java.io.InputStream;

public final class ScaffoldBlockCountRenderer {
    private final Minecraft mc = Minecraft.getMinecraft();
    private double x = -2.147483648E9, y = -2.147483648E9, width = 154.0;
    private float barWidth;
    private CFont small, large;
    private CFont font18() { loadFonts(); return large; }
    private CFont font14() { loadFonts(); return small; }
    private void loadFonts() {
        if (small != null) return;
        try (InputStream in = getClass().getResourceAsStream("/assets/vibe/scaffold/nunito-semibold.ttf")) {
            Font font = Font.createFont(Font.TRUETYPE_FONT, in);
            large = new CFont(font.deriveFont(Font.PLAIN, 18));
            small = new CFont(font.deriveFont(Font.PLAIN, 14));
        } catch (Exception error) { throw new IllegalStateException("Gothaj counter font", error); }
    }
    public void render(ScaledResolution sr) {
        if (mc.thePlayer == null || mc.thePlayer.getHeldItem() == null
                || !(mc.thePlayer.getHeldItem().getItem() instanceof ItemBlock)) return;
        dev.vibe.hud.HudManager manager = dev.vibe.Vibe.getInstance() == null ? null : dev.vibe.Vibe.getInstance().getHudManager();
        dev.vibe.hud.HudManager.HudElement element = manager == null ? null : manager.getElement(dev.vibe.hud.HudManager.BLOCK_COUNT);
        float scale = element == null ? 1 : element.getScale();
        int left = sr.getScaledWidth() / 2 - 160, top = sr.getScaledHeight() / 2 + 13;
        int scaledWidth = Math.round(154 * scale), scaledHeight = Math.round(30 * scale);
        if (element != null) {
            element.ensureOnScreen(sr, scaledWidth, scaledHeight);
            left = element.left(sr, scaledWidth); top = element.top(sr, scaledHeight);
            element.setBounds(left, top, scaledWidth, scaledHeight);
        }
        dev.vibe.module.impl.BlurModule blur = dev.vibe.Vibe.getInstance() == null ? null
                : dev.vibe.Vibe.getInstance().getModuleManager().getModule(dev.vibe.module.impl.BlurModule.class);
        if (blur != null && blur.isEnabled() && blur.getElements().isSelected(dev.vibe.module.impl.BlurModule.SCAFFOLD_COUNT))
            KawaseBlur.drawRoundedRegion(left, top, left + scaledWidth, top + scaledHeight, 12.2F * scale, blur.getStrength().getInt(), 0);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(left, top, 0);
            GlStateManager.scale(scale, scale, 1);
            x = y = 0;
            drawCounter(sr);
        } finally { GlStateManager.popMatrix(); GlStateManager.color(1, 1, 1, 1); }
    }

    private void drawCounter(ScaledResolution sr) {
        Item item;
        boolean initPosition;
        int blockCount = InventoryUtil.getBlockCount();
        boolean bl = initPosition = this.x == -2.147483648E9 || this.y == -2.147483648E9;
        if (initPosition) {
            this.x = (float)sr.getScaledWidth() / 2.0f - 160.0f;
            this.y = (float)sr.getScaledHeight() / 2.0f + 13.0f;
            this.width = 154.0;
        }
        if (this.mc.thePlayer.getHeldItem() != null && (item = this.mc.thePlayer.getHeldItem().getItem()) instanceof ItemBlock) {
            Item item2;
            ItemBlock block = (ItemBlock)item;
            int index = InventoryUtil.findSlot(this.mc.thePlayer.getHeldItem().getItem());
            CounterRender.drawRound((float)this.x, (float)this.y, 154.0f, 30.0f, 12.0f, new Color(20, 20, 20, 100));
            GlStateManager.pushMatrix();
            GlStateManager.scale(1.2f, 1.2f, 1.2f);
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            RenderHelper.enableGUIStandardItemLighting();
            float s = 1.2f;
            float adjX = (float)(this.x / (double)s);
            float adjY = (float)(this.y / (double)s);
            this.mc.getRenderItem().renderItemAndEffectIntoGUI(this.mc.thePlayer.inventory.mainInventory[index], (int)adjX + 7, (int)adjY + 5);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableBlend();
            GlStateManager.disableRescaleNormal();
            GlStateManager.popMatrix();
            float percentage = Math.min(1.0f, (float)blockCount / 64.0f);
            Color base = Color.WHITE;
            Color darker = Color.WHITE;
            if (this.mc.thePlayer.getHeldItem() != null && (item2 = this.mc.thePlayer.getHeldItem().getItem()) instanceof ItemBlock) {
                ItemBlock itemBlock = (ItemBlock)item2;
                Block blockType = itemBlock.getBlock();
                if (blockType == Blocks.wool) {
                    int meta = this.mc.thePlayer.getHeldItem().getMetadata();
                    IBlockState state = blockType.getStateFromMeta(meta);
                    int mapRGB = state.getBlock().getMapColor((IBlockState)state).colorValue;
                    base = new Color(mapRGB).brighter();
                    darker = base.darker().darker().darker();
                } else {
                    IBlockState state = blockType.getDefaultState();
                    int mapRGB = state.getBlock().getMapColor((IBlockState)state).colorValue;
                    base = new Color(mapRGB).brighter();
                    darker = base.darker().darker().darker();
                }
            }
            float maxWidth = 85.0f;
            float target = maxWidth * percentage;
            float alpha = 0.2f;
            this.barWidth += (target - this.barWidth) * alpha;
            CounterRender.drawRound((float)(this.x + 35.0), (float)(this.y + 14.0), maxWidth, 2.5f, 1.5f, new Color(30, 30, 30, 150));
            CounterRender.drawGradientHorizontal((float)(this.x + 35.0), (float)(this.y + 14.0), this.barWidth, 2.5f, 1.7f, darker, base);
            font18().drawString(String.valueOf(blockCount), (float)(this.x + 142.5 - (double)font18().getStringWidth(String.valueOf(blockCount))), (float)(this.y + 14.0 - (double)((float)font18().getHeight() / 2.0f)), Color.WHITE);
            font14().drawString(blockCount == 1 ? "block" : "blocks", (float)(this.x + (blockCount == 1 ? 134.0 : (blockCount >= 100 ? 132.5 : 132.0)) - (double)(font18().getStringWidth(String.valueOf(blockCount)) / 2.0f)), (float)(this.y + 21.0 - (double)((float)font14().getHeight() / 2.0f)), Color.GRAY);
        }

    }
}
