package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.impl.CuteVisualsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

/** Exact visual model used by the supplied Cute Visuals script, adapted to 1.8.9. */
public final class CuteVisualsRenderer {
    private static final double[] RAINBOW_RED = {.85D, .60D, .50D, .50D, 1.00D, 1.00D, 1.00D};
    private static final double[] RAINBOW_GREEN = {.50D, .50D, .75D, 1.00D, .90D, .60D, .40D};
    private static final double[] RAINBOW_BLUE = {1.00D, 1.00D, 1.00D, .65D, .50D, .40D, .50D};
    private static final double[] BED_RED = {1.00D, 1.00D, 1.00D, .50D, .50D, .60D, .85D};
    private static final double[] BED_GREEN = {.40D, .60D, .90D, 1.00D, .75D, .50D, .50D};
    private static final double[] BED_BLUE = {.50D, .40D, .50D, .65D, 1.00D, 1.00D, 1.00D};
    private final Minecraft minecraft = Minecraft.getMinecraft();

    public void render(RenderWorldLastEvent event) {
        CuteVisualsModule module = Vibe.getInstance().getModuleManager().getModule(CuteVisualsModule.class);
        if (module == null || !module.isEnabled() || minecraft.thePlayer == null) return;
        long now = System.currentTimeMillis();
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean smooth = GL11.glIsEnabled(GL11.GL_LINE_SMOOTH);
        WorldRenderUtils.begin(false);
        try {
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            for (CuteVisualsModule.Particle particle : module.getParticles()) {
                if (particle.type == CuteVisualsModule.HEART && module.getHearts().isEnabled()) drawTrailHeart(module, particle, now);
                else if (particle.type == CuteVisualsModule.DOT && module.getDots().isEnabled()) drawTrailDot(module, particle, now);
            }
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            for (CuteVisualsModule.Rainbow rainbow : module.getRainbows()) drawRainbow(module, rainbow, now);
            for (CuteVisualsModule.Particle particle : module.getParticles()) {
                if (particle.type == CuteVisualsModule.BED_BURST && module.getBedBurst().isEnabled()) drawBedParticle(module, particle, now);
            }
        } finally {
            if (depth) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
            if (smooth) GL11.glEnable(GL11.GL_LINE_SMOOTH); else GL11.glDisable(GL11.GL_LINE_SMOOTH);
            if (cull) GL11.glEnable(GL11.GL_CULL_FACE); else GL11.glDisable(GL11.GL_CULL_FACE);
            WorldRenderUtils.end(false);
        }
    }

    private void drawTrailHeart(CuteVisualsModule module, CuteVisualsModule.Particle particle, long now) {
        long age = now - particle.born;
        int lifetime = module.getHeartLifetime();
        if (age < 0L || age > lifetime) return;
        double progress = age / (double) lifetime;
        double alpha = progress < .1D ? progress / .1D : progress > .6D ? (1.0D - progress) / .4D : 1.0D;
        double scale = progress < .1D ? progress / .1D : progress > .8D ? (1.0D - progress) / .2D : 1.0D;
        double x = particle.x + Math.sin(age * .002D + particle.index * 1.7D) * .1D;
        double y = particle.y + progress * 1.5D;
        double z = particle.z + Math.cos(age * .0015D + particle.index * 2.3D) * .1D;
        double dx = x - minecraft.getRenderManager().viewerPosX;
        double dy = y - minecraft.getRenderManager().viewerPosY;
        double dz = z - minecraft.getRenderManager().viewerPosZ;
        double[][] palette = {{1.0D, .5D, .8D}, {1.0D, .3D, .6D}, {.9D, .4D, .9D}};
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(dx, dy, dz);
            GL11.glRotated(Math.toDegrees(Math.atan2(-dx, -dz)), 0.0D, 1.0D, 0.0D);
            GL11.glRotated((age * .1D + particle.rotationY) % 360.0D, 0.0D, 1.0D, 0.0D);
            GL11.glRotated(particle.rotationZ, 0.0D, 0.0D, 1.0D);
            GL11.glLineWidth(4.0F);
            double[] source = palette[particle.variant];
            double[] color = module.palette(source[0], source[1], source[2], particle.variant / 2F);
            drawHeart(particle.size * scale, alpha * module.getOpacity() * color[3], color[0], color[1], color[2], 30, 3, .08D, .3D);
            GL11.glLineWidth(1.0F);
        } finally { GL11.glPopMatrix(); }
    }

    private void drawTrailDot(CuteVisualsModule module, CuteVisualsModule.Particle particle, long now) {
        long age = now - particle.born;
        int lifetime = module.getDotLifetime();
        if (age < 0L || age > lifetime) return;
        double progress = age / (double) lifetime;
        double fade = progress < .1D ? progress / .1D : progress > .5D ? (1.0D - progress) / .5D : 1.0D;
        double pulse = module.isPulse() ? .5D + .5D * Math.sin(age * .01D * (3.0D + particle.variant * 1.5D) + particle.index * 2.7D) : 1.0D;
        double seconds = age / 1000.0D;
        double x = particle.x + particle.velocityX * seconds + Math.sin(seconds * 1.5D + particle.index * 1.3D) * .15D;
        double y = particle.y + particle.velocityY * seconds;
        double z = particle.z + particle.velocityZ * seconds + Math.cos(seconds * 1.2D + particle.index * 2.1D) * .15D;
        double dx = x - minecraft.getRenderManager().viewerPosX;
        double dy = y - minecraft.getRenderManager().viewerPosY;
        double dz = z - minecraft.getRenderManager().viewerPosZ;
        double[][] palette = {{1.0D, .45D, .7D}, {1.0D, .6D, .85D}, {1.0D, .3D, .55D}, {1.0D, .75D, .95D}};
        double alpha = fade * pulse * module.getOpacity();
        if (alpha < .02D) return;
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(dx, dy, dz);
            GL11.glRotated(Math.toDegrees(Math.atan2(-dx, -dz)), 0.0D, 1.0D, 0.0D);
            double[] source = palette[particle.variant];
            double[] color = module.palette(source[0], source[1], source[2], particle.variant / 3F);
            setColor(color[0], color[1], color[2], alpha * color[3]);
            GL11.glLineWidth(2.0F);
            drawFilledDot(particle.size);
        } finally { GL11.glPopMatrix(); }
    }

    private void drawRainbow(CuteVisualsModule module, CuteVisualsModule.Rainbow rainbow, long now) {
        long age = now - rainbow.born;
        int duration = module.getRainbowDuration();
        if (age < 0L || age > duration) return;
        double progress = age / (double) duration;
        double alpha = progress < .15D ? progress / .15D : progress > .6D ? (1.0D - progress) / .4D : 1.0D;
        double arc = progress < .2D ? Math.pow(progress / .2D, 2.0D) : 1.0D;
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(rainbow.x - minecraft.getRenderManager().viewerPosX, rainbow.y - minecraft.getRenderManager().viewerPosY,
                    rainbow.z - minecraft.getRenderManager().viewerPosZ);
            GL11.glRotatef(rainbow.yaw, 0.0F, 1.0F, 0.0F);
            for (int band = 0; band < 7; band++) {
                double[] color = module.palette(RAINBOW_RED[band], RAINBOW_GREEN[band], RAINBOW_BLUE[band], band / 6F);
                double radius = 3.0D + (band - 3) * .15D;
                drawRainbowArc(radius, color[0], color[1], color[2], alpha * color[3] * .15D, module.getRainbowLineWidth().getFloat() + 3.0F, arc);
                drawRainbowArc(radius, color[0], color[1], color[2], alpha * color[3] * .30D, module.getRainbowLineWidth().getFloat() + 1.5F, arc);
                drawRainbowArc(radius, color[0], color[1], color[2], alpha * color[3] * .85D, module.getRainbowLineWidth().getFloat(), arc);
            }
            if (arc > .5D) drawRainbowSparkles(module, now, arc, alpha);
        } finally { GL11.glPopMatrix(); }
    }

    private void drawRainbowArc(double radius, double red, double green, double blue, double alpha, float width, double progress) {
        GL11.glLineWidth(width); setColor(red, green, blue, alpha); GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int segment = 0; segment <= 30; segment++) {
            double angle = segment / 30.0D * Math.PI * progress;
            GL11.glVertex3d(Math.cos(angle) * radius, Math.sin(angle) * radius, 0.0D);
        }
        GL11.glEnd();
    }

    private void drawRainbowSparkles(CuteVisualsModule module, long now, double arcProgress, double alpha) {
        double rotation = now * .003D, c = Math.cos(rotation), s = Math.sin(rotation);
        double[] color = module.palette(1, 1, .8D, .5F);
        setColor(color[0], color[1], color[2], alpha * .7D * color[3]);
        for (int end = 0; end < 2; end++) {
            double angle = end == 0 ? 0.0D : Math.PI * arcProgress;
            double x = Math.cos(angle) * 3.0D, y = Math.sin(angle) * 3.0D;
            sparkleRay(x, y, c, s); sparkleRay(x, y, -s, c); sparkleRay(x, y, -c, -s); sparkleRay(x, y, s, -c);
        }
    }

    private void sparkleRay(double x, double y, double dx, double dy) {
        GL11.glBegin(GL11.GL_LINE_STRIP); GL11.glVertex3d(x, y, 0.0D); GL11.glVertex3d(x + dx * .15D, y + dy * .15D, 0.0D); GL11.glEnd();
    }

    private void drawBedParticle(CuteVisualsModule module, CuteVisualsModule.Particle particle, long now) {
        long age = now - particle.born;
        int lifetime = module.getBurstLifetime();
        if (age < 0L || age > lifetime) return;
        double progress = age / (double) lifetime, seconds = age / 1000.0D;
        double alpha = progress < .1D ? progress / .1D : progress > .7D ? (1.0D - progress) / .3D : 1.0D;
        double scale = progress < .1D ? progress / .1D : progress > .7D ? (1.0D - progress) / .3D : 1.0D;
        double x = particle.x + particle.velocityX * seconds + Math.sin(age * .002D + particle.index * 1.7D) * .05D;
        double y = particle.y + particle.velocityY * seconds - 1.5D * seconds * seconds;
        double z = particle.z + particle.velocityZ * seconds + Math.cos(age * .0015D + particle.index * 2.3D) * .05D;
        double dx = x - minecraft.getRenderManager().viewerPosX;
        double dy = y - minecraft.getRenderManager().viewerPosY;
        double dz = z - minecraft.getRenderManager().viewerPosZ;
        int color = Math.floorMod(particle.index, 7);
        double[] tint = module.palette(BED_RED[color], BED_GREEN[color], BED_BLUE[color], color / 6F);
        alpha *= tint[3];
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(dx, dy, dz);
            GL11.glRotated(Math.toDegrees(Math.atan2(dx, dz)), 0.0D, 1.0D, 0.0D);
            GL11.glRotated(seconds * 40.0D + particle.index * 60.0D, 0.0D, 0.0D, 1.0D);
            GL11.glLineWidth(module.getRainbowLineWidth().getFloat());
            if (particle.variant == 0) drawHeart(particle.size * scale, alpha, tint[0], tint[1], tint[2], 20, 2, .1D, .25D);
            else if (particle.variant == 1) drawStar(particle.size * scale, alpha, tint[0], tint[1], tint[2]);
            else if (particle.variant == 2) drawBedDot(particle.size * scale, alpha, tint[0], tint[1], tint[2]);
            else drawDiamond(particle.size * scale, alpha, tint[0], tint[1], tint[2]);
        } finally { GL11.glPopMatrix(); }
    }

    private void drawHeart(double size, double alpha, double red, double green, double blue, int segments, int layers, double growth, double glow) {
        double base = size / 16.0D;
        for (int layer = layers; layer >= 0; layer--) {
            double scale = base * (1.0D + layer * growth), a = layer == 0 ? alpha * .9D : alpha * (glow / layer);
            setColor(red, green, blue, a); GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int i = 0; i <= segments; i++) {
                double t = i / (double) segments * Math.PI * 2.0D;
                GL11.glVertex3d(16.0D * Math.pow(Math.sin(t), 3.0D) * scale,
                        (13.0D * Math.cos(t) - 5.0D * Math.cos(2.0D * t) - 2.0D * Math.cos(3.0D * t) - Math.cos(4.0D * t)) * scale, 0.0D);
            }
            GL11.glEnd();
        }
    }

    private void drawStar(double size, double alpha, double red, double green, double blue) {
        for (int layer = 2; layer >= 0; layer--) {
            double scale = size / 16.0D * (1.0D + layer * .1D), a = layer == 0 ? alpha * .9D : alpha * (.25D / layer);
            setColor(red, green, blue, a); GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int i = 0; i <= 8; i++) { double angle = i * Math.PI / 4.0D - Math.PI / 2.0D; double radius = (i % 2 == 0 ? 12.0D : 5.0D) * scale; GL11.glVertex3d(Math.cos(angle) * radius, Math.sin(angle) * radius, 0.0D); }
            GL11.glEnd();
        }
    }

    private void drawBedDot(double size, double alpha, double red, double green, double blue) {
        for (int layer = 2; layer >= 0; layer--) {
            double scale = size / 2.0D * (1.0D + layer * .15D), a = layer == 0 ? alpha * .9D : alpha * (.25D / layer);
            setColor(red, green, blue, a); GL11.glBegin(GL11.GL_TRIANGLE_FAN); GL11.glVertex3d(0.0D, 0.0D, 0.0D);
            for (int i = 0; i <= 8; i++) { double angle = i * Math.PI * 2.0D / 8.0D; GL11.glVertex3d(Math.cos(angle) * scale, Math.sin(angle) * scale, 0.0D); }
            GL11.glEnd();
        }
    }

    private void drawDiamond(double size, double alpha, double red, double green, double blue) {
        for (int layer = 2; layer >= 0; layer--) {
            double scale = size / 16.0D * (1.0D + layer * .1D), a = layer == 0 ? alpha * .9D : alpha * (.25D / layer);
            setColor(red, green, blue, a); GL11.glBegin(GL11.GL_LINE_STRIP);
            GL11.glVertex3d(0.0D, 14.0D * scale, 0.0D); GL11.glVertex3d(8.0D * scale, 0.0D, 0.0D); GL11.glVertex3d(0.0D, -14.0D * scale, 0.0D); GL11.glVertex3d(-8.0D * scale, 0.0D, 0.0D); GL11.glVertex3d(0.0D, 14.0D * scale, 0.0D);
            GL11.glEnd();
        }
    }

    private void drawFilledDot(double size) {
        GL11.glBegin(GL11.GL_TRIANGLE_FAN); GL11.glVertex3d(0.0D, 0.0D, 0.0D);
        for (int i = 0; i <= 6; i++) { double angle = i * Math.PI * 2.0D / 6.0D; GL11.glVertex3d(Math.cos(angle) * size, Math.sin(angle) * size, 0.0D); }
        GL11.glEnd(); GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = 0; i <= 8; i++) { double angle = i * Math.PI * 2.0D / 8.0D; GL11.glVertex3d(Math.cos(angle) * size, Math.sin(angle) * size, 0.0D); }
        GL11.glEnd();
    }

    private static void setColor(double red, double green, double blue, double alpha) {
        GlStateManager.color((float) red, (float) green, (float) blue, (float) Math.max(0.0D, Math.min(1.0D, alpha)));
    }
}
