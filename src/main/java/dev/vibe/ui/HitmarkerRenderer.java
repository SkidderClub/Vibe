package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.impl.HitmarkerModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

/** Renders the currently active hit confirmation without changing gameplay. */
public final class HitmarkerRenderer {

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final dev.vibe.ui.effect.TorusRenderer torus = new dev.vibe.ui.effect.TorusRenderer();

    public void renderOverlay() {
        HitmarkerModule module = module();
        float progress = module == null ? 0.0F : module.progress();
        if (progress <= 0.0F || !module.getModes().isSelected("2D (Crosshair)")) {
            return;
        }
        ScaledResolution resolution = new ScaledResolution(minecraft);
        float opacity = module.getFade().isEnabled() ? progress : 1.0F;
        int color = RenderUtils.alpha(module.getColor().getArgb(), Math.round(((module.getColor().getArgb() >>> 24) & 255) * opacity));
        float size = module.getSize2d().getFloat() * (0.72F + (1.0F - progress) * 0.28F);
        float centreX = resolution.getScaledWidth() * 0.5F;
        float centreY = resolution.getScaledHeight() * 0.5F;
        float extent = size + module.getWidth2d().getFloat() / (2 * resolution.getScaleFactor());
        if (DebugOverlay.overlaps(centreX - extent, centreY - extent, centreX + extent, centreY + extent)) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glLineWidth(module.getWidth2d().getFloat());
            WorldRenderUtils.color(color);
            GL11.glBegin(GL11.GL_LINES);
            float gap = Math.max(2.0F, size * 0.38F);
            crossArm(centreX, centreY, -1.0F, -1.0F, gap, size);
            crossArm(centreX, centreY, 1.0F, -1.0F, gap, size);
            crossArm(centreX, centreY, -1.0F, 1.0F, gap, size);
            crossArm(centreX, centreY, 1.0F, 1.0F, gap, size);
            GL11.glEnd();
        } finally {
            GL11.glPopAttrib();
        }
    }

    public void renderWorld(RenderWorldLastEvent event) {
        HitmarkerModule module = module();
        if (module == null || !module.isEnabled()) return;
        // Draw the reliable fixed-function world marker before the optional
        // reflective torus. Its shader captures the scene and may alter raw
        // GL state on older OptiFine render paths.
        if (module.getModes().isSelected("3D (World)")) {
            for (HitmarkerModule.Marker marker : module.getMarkers()) {
                float progress = module.progress(marker);
                if (progress > 0.0F) renderWorldMarker(module, marker, progress);
            }
        }
        if (module.getModes().isSelected("Torus")) torus.render(module);
    }

    private void renderWorldMarker(HitmarkerModule module, HitmarkerModule.Marker marker, float progress) {
        double x = marker.x - minecraft.getRenderManager().viewerPosX;
        double y = marker.y - minecraft.getRenderManager().viewerPosY;
        double z = marker.z - minecraft.getRenderManager().viewerPosZ;
        float opacity = module.getFade().isEnabled() ? progress : 1.0F;
        int color = RenderUtils.alpha(module.getColor().getArgb(), Math.round(((module.getColor().getArgb() >>> 24) & 255) * opacity));
        double expansion = module.getWorldAnimation().is("Expand") ? 0.62D + (1.0D - progress) * 1.35D : 1.0D;
        double size = module.getSize3d().getDouble() * expansion;
        // Do not rely on the GL cache used by other world renderers. The
        // marker is often the last effect of a frame, so it must be fully
        // self-contained even after a shader or ESP renderer has run.
        int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_TEXTURE_BIT);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            if (module.getThroughWalls().isEnabled()) GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
            double normalX = marker.lookX;
            double normalY = marker.lookY;
            double normalZ = marker.lookZ;
            double horizontal = Math.sqrt(normalX * normalX + normalZ * normalZ);
            double rightX = horizontal < 0.0001D ? 1.0D : -normalZ / horizontal;
            double rightY = 0.0D;
            double rightZ = horizontal < 0.0001D ? 0.0D : normalX / horizontal;
            // right x view-normal gives the saved view's local vertical axis.
            double upX = rightY * normalZ - rightZ * normalY;
            double upY = rightZ * normalX - rightX * normalZ;
            double upZ = rightX * normalY - rightY * normalX;
            double gap = Math.min(size * 0.86D, Math.max(0.005D, module.getGap3d().getDouble()));
            if (module.getOutline3d().isEnabled()) {
                GL11.glLineWidth(module.getWidth3d().getFloat() + 1.5F);
                color(RenderUtils.alpha(module.getOutlineColor3d().getArgb(), Math.round(module.getOutlineColor3d().getAlpha() * opacity)));
                drawWorldCross(x, y, z, rightX, rightY, rightZ, upX, upY, upZ, size, gap);
            }
            GL11.glLineWidth(module.getWidth3d().getFloat());
            color(color);
            drawWorldCross(x, y, z, rightX, rightY, rightZ, upX, upY, upZ, size, gap);
        } finally {
            GL11.glPopMatrix();
            GL11.glMatrixMode(matrixMode);
            GL11.glPopAttrib();
        }
    }

    private HitmarkerModule module() {
        return Vibe.getInstance() == null ? null : Vibe.getInstance().getModuleManager().getModule(HitmarkerModule.class);
    }
    public void close() { torus.close(); }

    private static void color(int argb) {
        GL11.glColor4f(((argb >>> 16) & 255) / 255.0F, ((argb >>> 8) & 255) / 255.0F,
                (argb & 255) / 255.0F, ((argb >>> 24) & 255) / 255.0F);
    }

    private void crossArm(float x, float y, float dx, float dy, float gap, float size) {
        GL11.glVertex2f(x + dx * gap, y + dy * gap);
        GL11.glVertex2f(x + dx * size, y + dy * size);
    }

    /** Four separated diagonals make the world marker read as a real hit X. */
    private void drawWorldCross(double x, double y, double z, double rightX, double rightY, double rightZ,
                                double upX, double upY, double upZ, double size, double gap) {
        GL11.glBegin(GL11.GL_LINES);
        worldArm(x, y, z, rightX, rightY, rightZ, upX, upY, upZ, -1.0D, -1.0D, gap, size);
        worldArm(x, y, z, rightX, rightY, rightZ, upX, upY, upZ, 1.0D, -1.0D, gap, size);
        worldArm(x, y, z, rightX, rightY, rightZ, upX, upY, upZ, -1.0D, 1.0D, gap, size);
        worldArm(x, y, z, rightX, rightY, rightZ, upX, upY, upZ, 1.0D, 1.0D, gap, size);
        GL11.glEnd();
    }

    private void worldArm(double x, double y, double z, double rightX, double rightY, double rightZ,
                          double upX, double upY, double upZ, double signRight, double signUp,
                          double gap, double size) {
        GL11.glVertex3d(x + signRight * rightX * gap + signUp * upX * gap,
                y + signRight * rightY * gap + signUp * upY * gap,
                z + signRight * rightZ * gap + signUp * upZ * gap);
        GL11.glVertex3d(x + signRight * rightX * size + signUp * upX * size,
                y + signRight * rightY * size + signUp * upY * size,
                z + signRight * rightZ * size + signUp * upZ * size);
    }

}
