package dev.vibe.ui.render;

import dev.vibe.Vibe;
import dev.vibe.module.impl.visual.BreakProgressModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

/** World-space text renderer matching RavenBS's BreakProgress presentation. */
public final class BreakProgressRenderer {
    private final Minecraft minecraft = Minecraft.getMinecraft();

    public void render(RenderWorldLastEvent event) {
        BreakProgressModule module = Vibe.getInstance().getModuleManager().getModule(BreakProgressModule.class);
        if (module == null || !module.isEnabled() || module.getBlock() == null || module.getProgress() == 0.0F
                || minecraft.thePlayer == null || minecraft.theWorld == null) return;
        double x = module.getBlock().getX() + 0.5D - minecraft.getRenderManager().viewerPosX;
        double y = module.getBlock().getY() + 0.5D - minecraft.getRenderManager().viewerPosY;
        double z = module.getBlock().getZ() + 0.5D - minecraft.getRenderManager().viewerPosZ;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate((float) x, (float) y, (float) z);
            GlStateManager.rotate(-minecraft.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate((minecraft.gameSettings.thirdPersonView == 2 ? -1 : 1)
                    * minecraft.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
            GlStateManager.scale(-0.02266667F, -0.02266667F, -0.02266667F);
            GlStateManager.depthMask(false);
            GlStateManager.disableDepth();
            GL11.glEnable(GL11.GL_BLEND);
            int alpha = Math.max(10, (int) (255.0F * module.getProgress()));
            int color = module.getFadeIn().isEnabled() ? (alpha << 24) | 0xFFFFFF : 0xFFFFFFFF;
            String text = module.getProgressText();
            minecraft.fontRendererObj.drawString(text, -minecraft.fontRendererObj.getStringWidth(text) / 2, -3, color, true);
            GL11.glDisable(GL11.GL_BLEND);
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        } finally {
            GlStateManager.popMatrix();
        }
    }
}
