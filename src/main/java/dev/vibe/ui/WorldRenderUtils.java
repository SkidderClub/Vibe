package dev.vibe.ui;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.client.renderer.RenderGlobal;
import org.lwjgl.opengl.GL11;

/** State-safe helpers for filled and outlined world-space boxes. */
public final class WorldRenderUtils {

    private WorldRenderUtils() {
    }

    public static void begin(boolean throughWalls) {
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        if (throughWalls) {
            GlStateManager.disableDepth();
        }
    }

    public static void end(boolean throughWalls) {
        if (throughWalls) {
            GlStateManager.enableDepth();
        }
        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    public static void box(AxisAlignedBB box, int outline, int fill, float lineWidth) {
        if ((fill >>> 24) != 0) {
            color(fill);
            drawFilledBox(box);
        }
        if ((outline >>> 24) != 0) {
            GL11.glLineWidth(Math.max(1.0F, lineWidth));
            RenderGlobal.drawOutlinedBoundingBox(box, (outline >>> 16) & 255, (outline >>> 8) & 255,
                    outline & 255, (outline >>> 24) & 255);
        }
    }

    public static void color(int argb) {
        GlStateManager.color(((argb >>> 16) & 255) / 255.0F, ((argb >>> 8) & 255) / 255.0F,
                (argb & 255) / 255.0F, ((argb >>> 24) & 255) / 255.0F);
    }

    private static void drawFilledBox(AxisAlignedBB box) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer world = tessellator.getWorldRenderer();
        world.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        world.pos(box.minX, box.minY, box.minZ).endVertex();
        world.pos(box.maxX, box.minY, box.minZ).endVertex();
        world.pos(box.maxX, box.minY, box.maxZ).endVertex();
        world.pos(box.minX, box.minY, box.maxZ).endVertex();
        world.pos(box.minX, box.maxY, box.minZ).endVertex();
        world.pos(box.minX, box.maxY, box.maxZ).endVertex();
        world.pos(box.maxX, box.maxY, box.maxZ).endVertex();
        world.pos(box.maxX, box.maxY, box.minZ).endVertex();
        world.pos(box.minX, box.minY, box.minZ).endVertex();
        world.pos(box.minX, box.maxY, box.minZ).endVertex();
        world.pos(box.maxX, box.maxY, box.minZ).endVertex();
        world.pos(box.maxX, box.minY, box.minZ).endVertex();
        world.pos(box.maxX, box.minY, box.minZ).endVertex();
        world.pos(box.maxX, box.maxY, box.minZ).endVertex();
        world.pos(box.maxX, box.maxY, box.maxZ).endVertex();
        world.pos(box.maxX, box.minY, box.maxZ).endVertex();
        world.pos(box.minX, box.minY, box.maxZ).endVertex();
        world.pos(box.maxX, box.minY, box.maxZ).endVertex();
        world.pos(box.maxX, box.maxY, box.maxZ).endVertex();
        world.pos(box.minX, box.maxY, box.maxZ).endVertex();
        world.pos(box.minX, box.minY, box.minZ).endVertex();
        world.pos(box.minX, box.minY, box.maxZ).endVertex();
        world.pos(box.minX, box.maxY, box.maxZ).endVertex();
        world.pos(box.minX, box.maxY, box.minZ).endVertex();
        tessellator.draw();
    }
}
