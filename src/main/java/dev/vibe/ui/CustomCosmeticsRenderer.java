package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.impl.CustomCosmeticsModule;
import java.awt.Color;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

/** State-safe world rendering for CustomCosmetics. */
public final class CustomCosmeticsRenderer {
    private final Minecraft minecraft = Minecraft.getMinecraft();

    public void render(RenderWorldLastEvent event) {
        CustomCosmeticsModule module = Vibe.getInstance().getModuleManager().getModule(CustomCosmeticsModule.class);
        if (module == null || !module.isEnabled() || minecraft.thePlayer == null || minecraft.theWorld == null
                || module.getOnlyThirdPerson().isEnabled() && minecraft.gameSettings.thirdPersonView == 0) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT);
        WorldRenderUtils.begin(true);
        try {
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            if (module.isActive("Trail")) drawTrail(module);
            if (module.isActive("Jump Circle")) drawCircles(module);
            if (module.isActive("China Hat")) drawHat(module, event.partialTicks);
        } finally {
            WorldRenderUtils.end(true);
            GL11.glPopAttrib();
            GlStateManager.color(1, 1, 1, 1);
        }
    }

    private void drawHat(CustomCosmeticsModule module, float partial) {
        EntityPlayerSP player = minecraft.thePlayer;
        double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partial - minecraft.getRenderManager().viewerPosX;
        double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partial - minecraft.getRenderManager().viewerPosY
                + player.getEyeHeight() + 0.22D;
        double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partial - minecraft.getRenderManager().viewerPosZ;
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x, y, z);
            GL11.glRotatef(-player.rotationYaw, 0, 1, 0);
            if (module.getFollowHeadPitch().isEnabled()) GL11.glRotatef(player.rotationPitch, 1, 0, 0);
            int segments = module.getHatSegments().getInt();
            double radius = module.getHatRadius().getDouble(), height = module.getHatHeight().getDouble();
            if (module.getHatFill().isEnabled()) {
                GL11.glBegin(GL11.GL_TRIANGLE_FAN);
                vertexLocal(0, height, 0, color(module, 0, 1));
                for (int i = 0; i <= segments; i++) {
                    double angle = Math.PI * 2.0D * i / segments;
                    vertexLocal(Math.cos(angle) * radius, 0, Math.sin(angle) * radius, color(module, i / (float) segments, 1));
                }
                GL11.glEnd();
            }
            if (module.getHatOutline().isEnabled()) {
                GL11.glLineWidth(1.4F);
                GL11.glBegin(GL11.GL_LINE_LOOP);
                for (int i = 0; i < segments; i++) {
                    double angle = Math.PI * 2.0D * i / segments;
                    vertexLocal(Math.cos(angle) * radius, 0, Math.sin(angle) * radius, color(module, i / (float) segments, 1));
                }
                GL11.glEnd();
                GL11.glBegin(GL11.GL_LINES);
                for (int i = 0; i < segments; i += Math.max(1, segments / 12)) {
                    double angle = Math.PI * 2.0D * i / segments;
                    int shade = color(module, i / (float) segments, 1);
                    vertexLocal(0, height, 0, shade); vertexLocal(Math.cos(angle) * radius, 0, Math.sin(angle) * radius, shade);
                }
                GL11.glEnd();
            }
        } finally { GL11.glPopMatrix(); }
    }

    private void drawTrail(CustomCosmeticsModule module) {
        Deque<CustomCosmeticsModule.TimedPoint> points = module.getTrail();
        if (points.size() < 2) return;
        int total = points.size(), index = 0;
        if (module.getTrailMode().is("Dots")) {
            GL11.glPointSize(Math.max(1, module.getTrailWidth().getFloat())); GL11.glBegin(GL11.GL_POINTS);
            for (CustomCosmeticsModule.TimedPoint point : points) vertex(point.point, color(module, index++ / (float) total, trailOpacity(module, index, total)));
            GL11.glEnd(); return;
        }
        GL11.glLineWidth(Math.max(.5F, module.getTrailWidth().getFloat())); GL11.glBegin(GL11.GL_LINE_STRIP);
        for (CustomCosmeticsModule.TimedPoint point : points) vertex(point.point, color(module, index++ / (float) total, trailOpacity(module, index, total)));
        GL11.glEnd();
        if (module.getTrailMode().is("Ribbon")) {
            index = 0; double half = module.getTrailWidth().getDouble() * 0.012D;
            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (CustomCosmeticsModule.TimedPoint point : points) {
                int color = color(module, index++ / (float) total, trailOpacity(module, index, total));
                vertex(point.point.xCoord - half, point.point.yCoord, point.point.zCoord - half, color);
                vertex(point.point.xCoord + half, point.point.yCoord, point.point.zCoord + half, color);
            }
            GL11.glEnd();
        }
    }

    private void drawCircles(CustomCosmeticsModule module) {
        long now = System.currentTimeMillis();
        for (CustomCosmeticsModule.TimedPoint marker : module.getCircles()) {
            float progress = Math.min(1, (now - marker.created) / (float) module.getJumpDuration().getInt());
            float alpha = module.getJumpFade().isEnabled() ? 1 - progress : 1;
            double radius = module.getJumpRadius().getDouble() * (module.getJumpMode().is("Ring") ? 1 : 0.4D + progress * 0.9D);
            double rise = module.getJumpMode().is("Pulse") || module.getJumpMode().is("Spiral") ? progress * .38D : 0;
            int detail = module.getJumpDetail().getInt();
            if (module.getJumpFill().isEnabled() && !module.getJumpMode().is("Star") && !module.getJumpMode().is("Spiral")) {
                GL11.glBegin(GL11.GL_TRIANGLE_FAN);
                vertex(marker.point.xCoord, marker.point.yCoord + rise, marker.point.zCoord, color(module, 0, alpha * .17F));
                for (int i = 0; i <= detail; i++) point(module, marker.point, radiusAt(module, radius, i, detail, progress), i, detail, rise, alpha);
                GL11.glEnd();
            }
            GL11.glLineWidth(module.getJumpLineWidth().getFloat());
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int i = 0; i <= detail; i++) point(module, marker.point, radiusAt(module, radius, i, detail, progress), i, detail, rise, alpha);
            GL11.glEnd();
            if (module.getJumpMode().is("Flower")) {
                for (int petal = 0; petal < 4; petal++) {
                    GL11.glBegin(GL11.GL_LINE_STRIP);
                    for (int i = 0; i <= detail / 2; i++) {
                        double a = Math.PI * 2 * i / detail + petal * Math.PI / 2;
                        double r = radius * Math.sin(Math.PI * 2 * i / detail);
                        vertex(marker.point.xCoord + Math.cos(a) * r, marker.point.yCoord + rise, marker.point.zCoord + Math.sin(a) * r,
                                color(module, i / (float) detail, alpha));
                    }
                    GL11.glEnd();
                }
            }
        }
    }

    private double radiusAt(CustomCosmeticsModule module, double radius, int index, int detail, float progress) {
        double a = Math.PI * 2 * index / detail;
        if (module.getJumpMode().is("Star")) return radius * (index % 2 == 0 ? 1 : .43D);
        if (module.getJumpMode().is("Flower")) return radius * (.7D + .3D * Math.sin(a * 4));
        if (module.getJumpMode().is("Spiral")) return radius * index / (double) detail;
        return radius;
    }
    private void point(CustomCosmeticsModule m, Vec3 centre, double radius, int index, int detail, double rise, float alpha) {
        double angle = Math.PI * 2 * index / detail + (m.getJumpMode().is("Spiral") ? rise * 7 : 0);
        vertex(centre.xCoord + Math.cos(angle) * radius, centre.yCoord + rise, centre.zCoord + Math.sin(angle) * radius,
                color(m, index / (float) detail, alpha));
    }
    private float trailOpacity(CustomCosmeticsModule m, int index, int total) { return m.getTrailFade().isEnabled() ? index / (float) total : 1; }
    private int color(CustomCosmeticsModule m, float phase, float opacity) {
        int value;
        if (m.getRainbow().isEnabled()) value = Color.HSBtoRGB((float) ((System.currentTimeMillis() / 3500D * m.getRainbowSpeed().getDouble() + phase) % 1D), .8F, 1F);
        else value = RenderUtils.blend(m.getPrimaryColor().getArgb(), m.getSecondaryColor().getArgb(), phase);
        int alpha = Math.max(0, Math.min(255, Math.round(((value >>> 24) & 255) * m.getOpacity().getFloat() * opacity)));
        return (alpha << 24) | (value & 0xFFFFFF);
    }
    private void vertex(Vec3 point, int color) { vertex(point.xCoord, point.yCoord, point.zCoord, color); }
    private void vertex(double x, double y, double z, int color) {
        WorldRenderUtils.color(color);
        GL11.glVertex3d(x - minecraft.getRenderManager().viewerPosX, y - minecraft.getRenderManager().viewerPosY, z - minecraft.getRenderManager().viewerPosZ);
    }
    private void vertexLocal(double x, double y, double z, int color) {
        WorldRenderUtils.color(color);
        GL11.glVertex3d(x, y, z);
    }
}
