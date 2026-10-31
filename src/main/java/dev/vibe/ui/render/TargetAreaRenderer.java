package dev.vibe.ui.render;

import dev.vibe.Vibe;
import dev.vibe.module.impl.combat.TargetsModule;
import dev.vibe.ui.WorldRenderUtils;
import dev.vibe.ui.effect.EffectState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.*;

/** One translucent cuboid and twelve edges, regardless of the number of selected blocks. */
public final class TargetAreaRenderer {
    private static final int[][] FACES = {
            {0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1},
            {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}
    };

    public void render() {
        Minecraft mc = Minecraft.getMinecraft();
        TargetsModule module = Vibe.getInstance().getModuleManager().getModule(TargetsModule.class);
        if (module == null || !module.shouldDisplayTargetArea() || mc.theWorld == null || mc.thePlayer == null) return;
        draw(module, mc.getRenderManager().viewerPosX, mc.getRenderManager().viewerPosY, mc.getRenderManager().viewerPosZ);
    }

    public void draw(TargetsModule module, double cameraX, double cameraY, double cameraZ) {
        if (!module.shouldDisplayTargetArea()) return;
        AxisAlignedBB box = module.getTargetAreaBounds().offset(-cameraX, -cameraY, -cameraZ);
        // Amber identifies the excluded cuboid when the area is inverted.
        int rgb = module.invertArea.isEnabled() ? 0xF0B55C : 0x66C7FF;
        try (EffectState ignored = new EffectState()) {
            GL20.glUseProgram(0);
            for (int unit = 0; unit < 3; unit++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit); GL11.glDisable(GL11.GL_TEXTURE_2D);
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glEnable(GL11.GL_BLEND); GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glColor4f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F, 0.045F);
            GL11.glBegin(GL11.GL_QUADS);
            for (int[] face : FACES) for (int vertex : face) {
                GL11.glVertex3d((vertex & 1) == 0 ? box.minX : box.maxX,
                        (vertex & 2) == 0 ? box.minY : box.maxY,
                        (vertex & 4) == 0 ? box.minZ : box.maxZ);
            }
            GL11.glEnd();
            GL11.glEnable(GL11.GL_LINE_SMOOTH); GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
            WorldRenderUtils.box(box, 0xD9000000 | rgb, 0, 1.5F);
        }
    }
}
