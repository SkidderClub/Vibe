package dev.vibe.ui.render;

import dev.vibe.Vibe;
import dev.vibe.module.impl.combat.TargetsModule;
import dev.vibe.module.impl.visual.PathModule;
import dev.vibe.path.PathRoute;
import dev.vibe.ui.effect.EffectState;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.*;

/** Cached route only: no block queries, searches or settings mutations in the render pass. */
public final class PathRenderer {
    private double[] vertices = new double[96];
    private int count;

    public void render(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        PathModule module = Vibe.getInstance().getModuleManager().getModule(PathModule.class);
        if (module == null || !module.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
        EntityLivingBase target = module.getTarget();
        if (target == null || target.worldObj != mc.theWorld || target.isDead
                || mc.theWorld.getEntityByID(target.getEntityId()) != target) return;
        TargetsModule filters = Vibe.getInstance().getModuleManager().getModule(TargetsModule.class);
        int team = filters != null && target instanceof EntityPlayer ? filters.teamColor((EntityPlayer) target) : 0;
        draw(module.getRoute(), module,
                interpolate(mc.thePlayer, event.partialTicks), interpolate(target, event.partialTicks),
                new Vec3(mc.getRenderManager().viewerPosX, mc.getRenderManager().viewerPosY, mc.getRenderManager().viewerPosZ),
                team, target.hurtTime > 0);
    }

    /** The same world-space path is used by offscreen GL checks, including Freecam's player-relative origin. */
    public void draw(PathRoute route, PathModule module, Vec3 player, Vec3 target, Vec3 origin, int team, boolean hurt) {
        if (route.points.isEmpty() || !route.complete && !module.getPartial().isEnabled()) return;
        prepareVertices(route, player, target);
        if (count < 2) return;
        int first = route.complete ? module.getColor().resolve(team, hurt) : module.getPartialColor().getArgb();
        int last = route.complete && module.getGradient().isEnabled() ? module.getEndColor().resolve(team, hurt) : first;
        double height = module.getHeightOffset().getDouble();
        double length = 0;
        for (int i = 1; i < count; i++) length += length(i - 1, i);
        try (EffectState ignored = new EffectState()) {
            GL20.glUseProgram(0);
            for (int unit = 0; unit < 3; unit++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit); GL11.glDisable(GL11.GL_TEXTURE_2D);
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glEnable(GL11.GL_BLEND); GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            if (module.getThroughWalls().isEnabled()) GL11.glDisable(GL11.GL_DEPTH_TEST);
            else { GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthFunc(GL11.GL_LEQUAL); }
            GL11.glDepthMask(false); GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
            GL11.glLineWidth(module.getLineWidth().getFloat());
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(-origin.xCoord, -origin.yCoord + height, -origin.zCoord);
                if (module.getLineStyle().is("Dashed")) dashed(length, Math.max(module.getDashLength().getDouble(), length / 1024), first, last);
                else solid(length, first, last);
                if (module.getEndpoint().isEnabled()) endpoint(module, last);
            } finally { GL11.glPopMatrix(); }
        }
    }

    private void prepareVertices(PathRoute route, Vec3 player, Vec3 target) {
        count = 0;
        List<Vec3> points = route.points;
        if (points.size() == 1 && points.get(0).squareDistanceTo(player) > 2.25) return;
        int closest = 0; double best = Double.POSITIVE_INFINITY, bestT = 0;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 a = points.get(i), b = points.get(i + 1);
            double dx = b.xCoord-a.xCoord, dy = b.yCoord-a.yCoord, dz = b.zCoord-a.zCoord;
            double t = clamp(((player.xCoord-a.xCoord)*dx+(player.yCoord-a.yCoord)*dy+(player.zCoord-a.zCoord)*dz)
                    / Math.max(1e-12, dx*dx+dy*dy+dz*dz));
            double x = a.xCoord+dx*t-player.xCoord, y = a.yCoord+dy*t-player.yCoord, z = a.zCoord+dz*t-player.zCoord;
            double distance = x*x+y*y+z*z;
            if (distance < best) { best = distance; closest = i; bestT = t; }
        }
        // The player has left this old route: wait for the next tick's replan instead of cutting across walls.
        if (points.size() > 1 && best > 2.25) return;
        append(player.xCoord, player.yCoord, player.zCoord);
        if (points.size() > 1) {
            Vec3 a = points.get(closest), b = points.get(closest + 1);
            append(a.xCoord+(b.xCoord-a.xCoord)*bestT, a.yCoord+(b.yCoord-a.yCoord)*bestT, a.zCoord+(b.zCoord-a.zCoord)*bestT);
        }
        for (int i = points.size() == 1 ? 0 : closest + 1; i < points.size(); i++) {
            Vec3 point = points.get(i); append(point.xCoord, point.yCoord, point.zCoord);
        }
        Vec3 end = points.get(points.size() - 1);
        if (target != null && route.complete && route.end.equals(new BlockPos(target.xCoord, target.yCoord + .05, target.zCoord))
                && Math.abs(target.yCoord - end.yCoord) < .6) append(target.xCoord, target.yCoord, target.zCoord);
    }

    private void append(double x, double y, double z) {
        if (count > 0) {
            int i = (count - 1) * 3;
            double dx = vertices[i]-x, dy = vertices[i+1]-y, dz = vertices[i+2]-z;
            if (dx*dx+dy*dy+dz*dz < 1e-12) return;
        }
        if ((count + 1) * 3 > vertices.length) vertices = Arrays.copyOf(vertices, vertices.length * 2);
        vertices[count*3] = x; vertices[count*3+1] = y; vertices[count*3+2] = z; count++;
    }

    private void solid(double total, int first, int last) {
        GL11.glBegin(GL11.GL_LINE_STRIP);
        double travelled = 0;
        for (int i = 0; i < count; i++) {
            if (i > 0) travelled += length(i - 1, i);
            color(mix(first, last, travelled / Math.max(1e-9, total)));
            GL11.glVertex3d(vertices[i*3], vertices[i*3+1], vertices[i*3+2]);
        }
        GL11.glEnd();
    }

    private void dashed(double total, double dash, int first, int last) {
        GL11.glBegin(GL11.GL_LINES);
        double travelled = 0;
        for (int i = 1; i < count; i++) {
            double length = length(i - 1, i), at = 0;
            while (at < length - 1e-8) {
                int band = (int) Math.floor((travelled + at + 1e-8) / dash);
                double end = Math.min(length, (band + 1) * dash - travelled);
                if ((band & 1) == 0) {
                    vertex(i - 1, i, at / length, mix(first, last, (travelled + at) / Math.max(1e-9, total)));
                    vertex(i - 1, i, end / length, mix(first, last, (travelled + end) / Math.max(1e-9, total)));
                }
                at = end;
            }
            travelled += length;
        }
        GL11.glEnd();
    }

    private void vertex(int a, int b, double t, int color) {
        color(color); a *= 3; b *= 3;
        GL11.glVertex3d(vertices[a]+(vertices[b]-vertices[a])*t, vertices[a+1]+(vertices[b+1]-vertices[a+1])*t,
                vertices[a+2]+(vertices[b+2]-vertices[a+2])*t);
    }

    private void endpoint(PathModule module, int color) {
        color(color);
        int i = (count - 1) * 3;
        double x = vertices[i], y = vertices[i+1], z = vertices[i+2], half = module.getEndpointSize().getDouble() * .5;
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3d(x-half,y,z-half); GL11.glVertex3d(x+half,y,z-half);
        GL11.glVertex3d(x+half,y,z+half); GL11.glVertex3d(x-half,y,z+half);
        GL11.glEnd();
        if (!module.getEndpointStyle().is("Box")) return;
        double top = y + half * 2;
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3d(x-half,top,z-half); GL11.glVertex3d(x+half,top,z-half);
        GL11.glVertex3d(x+half,top,z+half); GL11.glVertex3d(x-half,top,z+half); GL11.glEnd();
        GL11.glBegin(GL11.GL_LINES);
        for (int sideX : new int[] {-1, 1}) for (int sideZ : new int[] {-1, 1}) {
            GL11.glVertex3d(x+sideX*half,y,z+sideZ*half); GL11.glVertex3d(x+sideX*half,top,z+sideZ*half);
        }
        GL11.glEnd();
    }

    private double length(int a, int b) {
        a *= 3; b *= 3;
        double x = vertices[a]-vertices[b], y = vertices[a+1]-vertices[b+1], z = vertices[a+2]-vertices[b+2];
        return Math.sqrt(x*x+y*y+z*z);
    }
    public static int mix(int first, int last, double t) {
        t = clamp(t); int result = 0;
        for (int shift = 0; shift <= 24; shift += 8)
            result |= (int) Math.round((first >>> shift & 255) * (1-t) + (last >>> shift & 255) * t) << shift;
        return result;
    }
    private static void color(int color) { GL11.glColor4f((color>>16&255)/255F, (color>>8&255)/255F, (color&255)/255F, (color>>>24)/255F); }
    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
    private static Vec3 interpolate(EntityLivingBase entity, float partial) {
        return new Vec3(entity.lastTickPosX+(entity.posX-entity.lastTickPosX)*partial,
                entity.lastTickPosY+(entity.posY-entity.lastTickPosY)*partial,
                entity.lastTickPosZ+(entity.posZ-entity.lastTickPosZ)*partial);
    }
}
