package dev.vibe.ui.screen;

import dev.vibe.module.impl.visual.ChestEspModule;
import dev.vibe.module.impl.visual.ChestEspModule.Type;
import dev.vibe.ui.GuiRenderState;
import dev.vibe.ui.WorldRenderUtils;
import dev.vibe.ui.render.esp.BoxProjection;
import dev.vibe.ui.render.esp.ChestEspRenderer;
import dev.vibe.ui.render.esp.EspLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelChest;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/** Uses Minecraft's chest model and the same boxes as the live ESP pass. */
final class ChestEspPreview {
    private static final ResourceLocation[] TEXTURES = {
        new ResourceLocation("textures/entity/chest/normal.png"),
        new ResourceLocation("textures/entity/chest/trapped.png"),
        new ResourceLocation("textures/entity/chest/ender.png")
    };
    private final ModelChest model = new ModelChest();
    private final BoxProjection.Camera camera = new BoxProjection.Camera();

    void draw(ChestEspModule module, Type type, float distance, float yaw, int centerX, int centerY,
              double screenWidth, double screenHeight) {
        EspLayout.Rect projected = null;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.pushMatrix();
        try {
            GuiRenderState.prepare(true);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            RenderHelper.enableGUIStandardItemLighting();
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GlStateManager.translate(centerX, centerY, 50);
            GlStateManager.scale(126, -126, 126);
            GlStateManager.rotate(22, 1, 0, 0);
            GlStateManager.rotate(yaw, 0, 1, 0);
            GlStateManager.translate(-.5F, -.4375F, -.5F);
            GlStateManager.pushMatrix();
            try {
                Minecraft.getMinecraft().getTextureManager().bindTexture(TEXTURES[type.ordinal()]);
                GlStateManager.translate(0, 1, 1);
                GlStateManager.scale(1, -1, -1);
                model.renderAll();
            } finally {
                GlStateManager.popMatrix();
                RenderHelper.disableStandardItemLighting();
            }
            if (distance <= 1 && module.getStyle(type).enabled.isEnabled()) {
                if (module.is2D()) {
                    camera.capture();
                    projected = camera.project(ChestEspRenderer.box2D(), screenWidth, screenHeight);
                } else {
                    WorldRenderUtils.begin(true);
                    try {
                        ChestEspRenderer.drawBox3D(module, type, distance, ChestEspRenderer.box3D());
                    } finally {
                        WorldRenderUtils.end(true);
                    }
                }
            }
        } finally {
            GlStateManager.popMatrix();
            GL11.glPopAttrib();
            GuiRenderState.prepare(false);
        }
        if (projected != null) ChestEspRenderer.drawPreview2D(module, type, distance, projected);
    }
}
