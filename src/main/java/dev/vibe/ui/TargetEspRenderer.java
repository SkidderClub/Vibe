package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.impl.AimAssistModule;
import dev.vibe.module.impl.KillAuraModule;
import dev.vibe.module.impl.TargetEspModule;
import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

/** Animated combat target markers rendered independently of regular ESP. */
public final class TargetEspRenderer {

    private final Minecraft minecraft = Minecraft.getMinecraft();
    public void render(RenderWorldLastEvent event) {
        TargetEspModule module = Vibe.getInstance().getModuleManager().getModule(TargetEspModule.class);
        EntityLivingBase target = target();
        if (module == null || !module.isEnabled() || target == null || minecraft.thePlayer == null) {
            return;
        }
        double x = interpolate(target.lastTickPosX, target.posX, event.partialTicks) - minecraft.getRenderManager().viewerPosX;
        double y = interpolate(target.lastTickPosY, target.posY, event.partialTicks) - minecraft.getRenderManager().viewerPosY;
        double z = interpolate(target.lastTickPosZ, target.posZ, event.partialTicks) - minecraft.getRenderManager().viewerPosZ;
        float time = (System.currentTimeMillis() % 9000L) / 1000.0F * module.getSpeed().getFloat();
        WorldRenderUtils.begin(false);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(module.getLineWidth().getFloat());
        try {
            if (module.getModes().isSelected(TargetEspModule.TIRE)) {
                torus(x, y + target.height * 0.55D, z, time, module, 0.6F);
            }
            if (module.getModes().isSelected(TargetEspModule.SIGMA)) {
                sigma(x, y, z, target.height, time, module);
            }
            if (module.getModes().isSelected(TargetEspModule.CIRCLE)) {
                circle(x, y + 0.04D, z, 0.72F, module, 1.0F);
            }
            if (module.getModes().isSelected(TargetEspModule.HELIX)) {
                helix(x, y, z, target.height, time, module);
            }
            if (module.getModes().isSelected(TargetEspModule.SIMS)) {
                sims(x, y + target.height + 0.34D, z, time, module);
            }
            if (module.getModes().isSelected(TargetEspModule.TRACER)) {
                tracer(x, y, z, target.height, event.partialTicks, module);
            }
            if (module.getModes().isSelected(TargetEspModule.HIZZY)) {
                hizzy(x, y, z, target.width, target.height, time, module);
            }
        } finally {
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
            WorldRenderUtils.end(true);
        }
    }

    private EntityLivingBase target() {
        KillAuraModule aura = Vibe.getInstance().getModuleManager().getModule(KillAuraModule.class);
        if (aura != null && aura.isEnabled() && aura.getTarget() != null) return aura.getTarget();
        AimAssistModule aim = Vibe.getInstance().getModuleManager().getModule(AimAssistModule.class);
        return aim != null && aim.isEnabled() ? aim.getTarget() : null;
    }

    private void circle(double x, double y, double z, float radius, TargetEspModule module, float alpha) {
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int step = 0; step <= 72; step++) {
            float angle = step * ((float) Math.PI * 2.0F / 72.0F);
            WorldRenderUtils.color(RenderUtils.alpha(gradient(module, TargetEspModule.CIRCLE, step / 72.0F), Math.round(alpha * 255.0F)));
            GL11.glVertex3d(x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius);
        }
        GL11.glEnd();
    }

    private void hizzy(double x, double y, double z, float playerWidth, float height, float time, TargetEspModule module) {
        float progress = (float) ((Math.sin(time * 2.0F) + 1.0F) * 0.5F);
        double ringY = y + 0.10D + progress * Math.max(0.1F, height - 0.20F);
        double radius = Math.max(0.35D, playerWidth);
        int segments = 64;
        // HIZZY deliberately uses one animated rainbow sample for the whole
        // ring.  Sampling the hue per vertex creates a striped wheel and also
        // makes the fill appear translucent; a single opaque sample keeps the
        // classic solid rainbow marker.
        int rainbow = hizzyColor(module, 0.0F, 255);
        boolean cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        try {
            // The disk is deliberately two-sided.  With face culling left
            // enabled, its winding disappears when the camera is below it.
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            WorldRenderUtils.color(rainbow);
            GL11.glVertex3d(x, ringY, z);
            for (int step = 0; step <= segments; step++) {
                double angle = step / (double) segments * Math.PI * 2.0D;
                GL11.glVertex3d(x + Math.cos(angle) * radius, ringY, z + Math.sin(angle) * radius);
            }
            GL11.glEnd();
            // Keep the outline noticeably heavier than the other target
            // markers; this remains user-scalable through the shared setting.
            GL11.glLineWidth(Math.max(3.0F, module.getLineWidth().getFloat() * 1.5F));
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int step = 0; step <= segments; step++) {
                double angle = step / (double) segments * Math.PI * 2.0D;
                WorldRenderUtils.color(rainbow);
                GL11.glVertex3d(x + Math.cos(angle) * radius, ringY, z + Math.sin(angle) * radius);
            }
            GL11.glEnd();
        } finally {
            if (cullWasEnabled) GL11.glEnable(GL11.GL_CULL_FACE);
            if (!blendWasEnabled) GL11.glDisable(GL11.GL_BLEND);
        }
    }

    private int hizzyColor(TargetEspModule module, float phase, int alpha) {
        float cycle = (System.currentTimeMillis() % 4200L) / 4200.0F;
        int rainbow = Color.HSBtoRGB(cycle % 1.0F, 0.88F, 1.0F) | 0xFF000000;
        return RenderUtils.alpha(rainbow, 255);
    }

    /**
     * A two-part combat tracer: the first segment originates on the player's
     * view ray (the crosshair), reaches the target's centre, then climbs
     * vertically through the middle of the player to the head.
     */
    private void tracer(double x, double y, double z, float height, float partialTicks, TargetEspModule module) {
        // First-person origin is the active camera itself.  This is the exact
        // screen centre, unaffected by player limb/walk animation. Third
        // person keeps a short forward ray from the camera.
        double startX;
        double startY;
        double startZ;
        if (minecraft.gameSettings.thirdPersonView == 0) {
            startX = 0.0D;
            startY = 0.0D;
            startZ = 0.0D;
        } else {
            Vec3 eyes = minecraft.thePlayer.getPositionEyes(partialTicks);
            Vec3 look = minecraft.thePlayer.getLook(partialTicks);
            startX = eyes.xCoord + look.xCoord * 0.16D - minecraft.getRenderManager().viewerPosX;
            startY = eyes.yCoord + look.yCoord * 0.16D - minecraft.getRenderManager().viewerPosY;
            startZ = eyes.zCoord + look.zCoord * 0.16D - minecraft.getRenderManager().viewerPosZ;
        }
        double centreY = y + height * 0.5D;
        GL11.glBegin(GL11.GL_LINE_STRIP);
        WorldRenderUtils.color(module.getPrimaryColor(TargetEspModule.TRACER).getArgb());
        GL11.glVertex3d(startX, startY, startZ);
        WorldRenderUtils.color(RenderUtils.blend(module.getPrimaryColor(TargetEspModule.TRACER).getArgb(), module.getSecondaryColor(TargetEspModule.TRACER).getArgb(), 0.38F));
        GL11.glVertex3d(x, centreY, z);
        WorldRenderUtils.color(module.getSecondaryColor(TargetEspModule.TRACER).getArgb());
        GL11.glVertex3d(x, y + height, z);
        GL11.glEnd();
    }

    private void torus(double x, double y, double z, float time, TargetEspModule module, float radius) {
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glRotatef(time * 28.0F, 0.0F, 1.0F, 0.0F);
        for (int slice = 0; slice < 12; slice++) {
            float theta = slice * ((float) Math.PI * 2.0F / 12.0F);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int loop = 0; loop <= 32; loop++) {
                float phi = loop * ((float) Math.PI * 2.0F / 32.0F);
                float minor = 0.13F;
                float ring = radius + minor * (float) Math.cos(theta);
                WorldRenderUtils.color(RenderUtils.alpha(gradient(module, TargetEspModule.TIRE, loop / 32.0F + slice * 0.04F), 210));
                GL11.glVertex3f(ring * (float) Math.cos(phi), minor * (float) Math.sin(theta),
                        ring * (float) Math.sin(phi));
            }
            GL11.glEnd();
        }
        GL11.glPopMatrix();
    }

    private void helix(double x, double y, double z, float height, float time, TargetEspModule module) {
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int step = 0; step <= 96; step++) {
            float progress = step / 96.0F;
            float angle = progress * (float) Math.PI * 6.0F + time * 2.6F;
            int color = RenderUtils.alpha(gradient(module, TargetEspModule.HELIX, progress + time * 0.08F), Math.round((0.20F + progress * 0.80F) * 255.0F));
            WorldRenderUtils.color(color);
            GL11.glVertex3d(x + Math.cos(angle) * 0.58D, y + progress * height, z + Math.sin(angle) * 0.58D);
        }
        GL11.glEnd();
    }

    private void sims(double x, double y, double z, float time, TargetEspModule module) {
        float wobble = (float) Math.sin(time * 2.2F) * 0.07F;
        boolean cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glPushMatrix();
        GL11.glTranslated(x, y + wobble, z);
        GL11.glRotatef(time * 72.0F, 0.0F, 1.0F, 0.0F);
        // A proper Sims plumbob: two four-sided pyramids joined at the waist,
        // with transparent faces and luminous edges.
        GL11.glBegin(GL11.GL_TRIANGLES);
        int tip = RenderUtils.alpha(module.getPrimaryColor(TargetEspModule.SIMS).getArgb(), 240);
        int base = RenderUtils.alpha(module.getSecondaryColor(TargetEspModule.SIMS).getArgb(), 185);
        plumbobFace(0.0D, 0.46D, 0.0D, -0.20D, 0.08D, 0.0D, 0.0D, 0.08D, -0.20D, tip, base);
        plumbobFace(0.0D, 0.46D, 0.0D, 0.0D, 0.08D, -0.20D, 0.20D, 0.08D, 0.0D, tip, base);
        plumbobFace(0.0D, 0.46D, 0.0D, 0.20D, 0.08D, 0.0D, 0.0D, 0.08D, 0.20D, tip, base);
        plumbobFace(0.0D, 0.46D, 0.0D, 0.0D, 0.08D, 0.20D, -0.20D, 0.08D, 0.0D, tip, base);
        plumbobFace(0.0D, -0.22D, 0.0D, 0.0D, 0.08D, -0.20D, -0.20D, 0.08D, 0.0D, base, tip);
        plumbobFace(0.0D, -0.22D, 0.0D, 0.20D, 0.08D, 0.0D, 0.0D, 0.08D, -0.20D, base, tip);
        plumbobFace(0.0D, -0.22D, 0.0D, 0.0D, 0.08D, 0.20D, 0.20D, 0.08D, 0.0D, base, tip);
        plumbobFace(0.0D, -0.22D, 0.0D, -0.20D, 0.08D, 0.0D, 0.0D, 0.08D, 0.20D, base, tip);
        GL11.glEnd();
        GL11.glPointSize(3.0F);
        GL11.glBegin(GL11.GL_POINTS);
        WorldRenderUtils.color(0xFFE5FFF0);
        GL11.glVertex3d(0.0D, 0.08D, 0.0D);
        GL11.glEnd();
        GL11.glPointSize(1.0F);
        GL11.glBegin(GL11.GL_LINES);
        WorldRenderUtils.color(module.getPrimaryColor(TargetEspModule.SIMS).getArgb());
        plumbobEdges();
        GL11.glEnd();
        GL11.glPopMatrix();
        if (cullWasEnabled) {
            GL11.glEnable(GL11.GL_CULL_FACE);
        }
    }

    private void plumbobFace(double ax, double ay, double az, double bx, double by, double bz,
                              double cx, double cy, double cz, int tipColor, int baseColor) {
        WorldRenderUtils.color(tipColor);
        GL11.glVertex3d(ax, ay, az);
        WorldRenderUtils.color(baseColor);
        GL11.glVertex3d(bx, by, bz);
        GL11.glVertex3d(cx, cy, cz);
    }

    private void plumbobEdges() {
        double[][] ring = {{-0.20D, 0.08D, 0.0D}, {0.0D, 0.08D, -0.20D},
                {0.20D, 0.08D, 0.0D}, {0.0D, 0.08D, 0.20D}};
        for (int index = 0; index < ring.length; index++) {
            double[] point = ring[index];
            GL11.glVertex3d(0.0D, 0.46D, 0.0D);
            GL11.glVertex3d(point[0], point[1], point[2]);
            GL11.glVertex3d(0.0D, -0.22D, 0.0D);
            GL11.glVertex3d(point[0], point[1], point[2]);
            double[] next = ring[(index + 1) % ring.length];
            GL11.glVertex3d(point[0], point[1], point[2]);
            GL11.glVertex3d(next[0], next[1], next[2]);
        }
    }

    /** JelloSigma-style travelling ring with a soft, fading jelly volume. */
    private void sigma(double x, double y, double z, float height, float time, TargetEspModule module) {
        int rings = 20;
        int segments = 72;
        float range = Math.max(0.25F, height - 0.05F);
        float travel = (float) ((Math.sin(time * 2.1F) + 1.0F) * 0.5F) * range;
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        // A group of trailing rings makes the marker look like animated jelly
        // rather than the old single circle and unrelated line trace.
        for (int ring = rings - 1; ring >= 0; ring--) {
            float tail = ring / (float) rings;
            float ringY = (float) (y + travel - tail * 0.72F);
            if (ringY < y + 0.02F) continue;
            float radius = 0.66F + (float) Math.sin((time * 3.0F - tail * 4.2F)) * 0.055F + tail * 0.05F;
            float alpha = (1.0F - tail) * (1.0F - tail) * 0.78F;
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int step = 0; step <= segments; step++) {
                float progress = step / (float) segments;
                float angle = progress * (float) Math.PI * 2.0F + time * 0.32F;
                int color = RenderUtils.alpha(gradient(module, TargetEspModule.SIGMA, progress + tail * 0.28F), Math.round(alpha * 255.0F));
                WorldRenderUtils.color(color);
                GL11.glVertex3d(x + Math.cos(angle) * radius, ringY, z + Math.sin(angle) * radius);
            }
            GL11.glEnd();
        }
        // The leading ribbon is a translucent, wavy skin underneath the ring.
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for (int step = 0; step <= segments; step++) {
            float progress = step / (float) segments;
            float angle = progress * (float) Math.PI * 2.0F + time * 0.32F;
            float wobble = (float) Math.sin(angle * 3.0F + time * 4.0F) * 0.035F;
            int topColor = RenderUtils.alpha(gradient(module, TargetEspModule.SIGMA, progress), 112);
            int bottomColor = RenderUtils.alpha(gradient(module, TargetEspModule.SIGMA, progress + 0.23F), 8);
            WorldRenderUtils.color(topColor);
            GL11.glVertex3d(x + Math.cos(angle) * (0.66F + wobble), y + travel,
                    z + Math.sin(angle) * (0.66F + wobble));
            WorldRenderUtils.color(bottomColor);
            GL11.glVertex3d(x + Math.cos(angle) * (0.73F + wobble), Math.max(y + 0.02F, y + travel - 0.78F),
                    z + Math.sin(angle) * (0.73F + wobble));
        }
        GL11.glEnd();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private int gradient(TargetEspModule module, String mode, float amount) {
        float wave = (float) ((Math.sin(amount * Math.PI * 2.0D) + 1.0D) * 0.5D);
        return RenderUtils.blend(module.getPrimaryColor(mode).getArgb(), module.getSecondaryColor(mode).getArgb(), wave);
    }

    private double interpolate(double previous, double current, float partialTicks) {
        return previous + (current - previous) * partialTicks;
    }

}
