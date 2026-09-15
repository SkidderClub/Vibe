package dev.vibe.ui;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;

/** Establish a complete GUI basis, including states left behind by framebuffer blits. */
public final class GuiRenderState {
    private GuiRenderState() { }

    public static void prepare(boolean model) {
        // Force both the driver and Minecraft's cache into agreement. A raw
        // glPopAttrib elsewhere can restore the driver without its Java cache.
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.enableTexture2D();
        GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.disableTexture2D();
        GlStateManager.enableTexture2D();
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager.bindTexture(0);
        GlStateManager.bindTexture(texture);
        GlStateManager.enableLighting();
        GlStateManager.disableLighting();
        for (int light = 0; light < 2; light++) {
            GlStateManager.enableLight(light);
            GlStateManager.disableLight(light);
        }
        GlStateManager.disableColorMaterial();
        GlStateManager.enableColorMaterial();
        GlStateManager.enableFog();
        GlStateManager.disableFog();
        GlStateManager.enableCull();
        GlStateManager.disableCull();
        GlStateManager.enableDepth();
        GlStateManager.disableDepth();
        if (model) GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_ALWAYS);
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(!model);
        GlStateManager.depthMask(model);
        GlStateManager.disableBlend();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(1, 0, 1, 0);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        org.lwjgl.opengl.GL14.glBlendEquation(org.lwjgl.opengl.GL14.GL_FUNC_ADD);
        GlStateManager.disableAlpha();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_ALWAYS, 0);
        GlStateManager.alphaFunc(GL11.GL_GREATER, .1F);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glFrontFace(GL11.GL_CCW);
        GlStateManager.colorMask(false, false, false, false);
        GlStateManager.colorMask(true, true, true, true);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        GlStateManager.resetColor();
        GlStateManager.color(1, 1, 1, 1);
    }
}
