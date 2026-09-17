package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.impl.TrajectoriesModule;
import java.awt.Color;
import java.util.Deque;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;

/** World pass for the trajectory line, impact marker and projectile trails. */
public final class TrajectoriesRenderer {
    private final Minecraft minecraft = Minecraft.getMinecraft();
    public void render(RenderWorldLastEvent event) {
        TrajectoriesModule module = Vibe.getInstance().getModuleManager().getModule(TrajectoriesModule.class);
        if (module == null || !module.isEnabled() || minecraft.thePlayer == null || minecraft.theWorld == null) return;
        TrajectoriesModule.Trajectory trajectory = module.simulate(event.partialTicks);
        WorldRenderUtils.begin(true);
        try {
            GL11.glDisable(GL11.GL_CULL_FACE);
            if (trajectory != null) drawPath(module, trajectory, event.partialTicks);
            if (module.getEntityTrail().isEnabled()) drawTrails(module);
        } finally { GL11.glEnable(GL11.GL_CULL_FACE); WorldRenderUtils.end(true); }
    }
    private void drawPath(TrajectoriesModule module, TrajectoriesModule.Trajectory path, float partialTicks) {
        int color = path.hitType == 1 ? module.getEnemyColor().getArgb() : path.hitType == 2 ? module.getWallColor().getArgb()
                : path.hitType == 3 ? module.getGroundColor().getArgb() : module.getDefaultColor().getArgb();
        if (module.getRainbow().isEnabled()) color = Color.HSBtoRGB((System.currentTimeMillis()%3500L)/3500.0F,.75F,1.0F)|0xFF000000;
        GL11.glLineWidth(module.getLineWidth().getFloat()); GL11.glBegin(GL11.GL_LINE_STRIP);
        for (Vec3 point : path.points) { float alpha = module.getFadeOut().isEnabled() ? .94F : 1.0F; color(color,alpha); GL11.glVertex3d(point.xCoord-minecraft.getRenderManager().viewerPosX,point.yCoord-minecraft.getRenderManager().viewerPosY,point.zCoord-minecraft.getRenderManager().viewerPosZ); }
        GL11.glEnd();
        if (path.hit == null) return;
        double x=path.hit.xCoord-minecraft.getRenderManager().viewerPosX,y=path.hit.yCoord-minecraft.getRenderManager().viewerPosY,z=path.hit.zCoord-minecraft.getRenderManager().viewerPosZ;
        drawImpact(module, path.hitFace, x, y, z);
    }

    private void drawImpact(TrajectoriesModule module, EnumFacing face, double x, double y, double z) {
        int outline = module.getImpactOutlineColor(), fill = module.getImpactFillColor();
        double size = module.getImpactSize();
        String mode = module.getImpactMode().getValue();
        if ("Cube".equals(mode) || ("Basic".equals(mode) && module.getLandingBox().isEnabled())) {
            double radius = "Basic".equals(mode) ? size * 2 / 3 : size;
            WorldRenderUtils.box(new AxisAlignedBB(x-radius,y-radius,z-radius,x+radius,y+radius,z+radius), outline, fill, module.getImpactOutlineWidth());
        }
        if ("Cube".equals(mode)) return;
        if ("Basic".equals(mode)) {
            if (module.getLandingCross().isEnabled()) {
                GL11.glLineWidth(module.getImpactOutlineWidth()); WorldRenderUtils.color(outline);
                GL11.glBegin(GL11.GL_LINES);
                GL11.glVertex3d(x-size,y,z); GL11.glVertex3d(x+size,y,z);
                GL11.glVertex3d(x,y-size,z); GL11.glVertex3d(x,y+size,z);
                GL11.glVertex3d(x,y,z-size); GL11.glVertex3d(x,y,z+size);
                GL11.glEnd();
            }
            return;
        }
        // Project flat markers onto the surface actually hit, with a small depth offset.
        GL11.glPushMatrix();
        try {
            if (face == null) face = EnumFacing.UP;
            GL11.glTranslated(x + face.getFrontOffsetX() * .005, y + face.getFrontOffsetY() * .005, z + face.getFrontOffsetZ() * .005);
            switch (face.getAxis()) {
                case Y: GL11.glRotatef(90, 1, 0, 0); break;
                case X: GL11.glRotatef(90, 0, 1, 0); break;
                default: break;
            }
            double[][] vertices;
            if ("Square".equals(mode)) vertices = new double[][]{{-size,-size},{size,-size},{size,size},{-size,size}};
            else if ("Cross".equals(mode)) {
                double w = size * .24;
                vertices = new double[][]{{-w,-size},{w,-size},{w,-w},{size,-w},{size,w},{w,w},{w,size},{-w,size},{-w,w},{-size,w},{-size,-w},{-w,-w}};
            } else {
                double radius = "Dot".equals(mode) ? size * .22 : size;
                vertices = new double[64][2];
                for (int i=0; i<vertices.length; i++) {
                    double angle = Math.PI * 2 * i / vertices.length;
                    vertices[i][0] = Math.cos(angle) * radius; vertices[i][1] = Math.sin(angle) * radius;
                }
            }
            if ((fill >>> 24) != 0) {
                WorldRenderUtils.color(fill); GL11.glBegin(GL11.GL_TRIANGLE_FAN); GL11.glVertex3d(0,0,0);
                for (int i=0; i<=vertices.length; i++) GL11.glVertex3d(vertices[i%vertices.length][0],vertices[i%vertices.length][1],0);
                GL11.glEnd();
            }
            if ((outline >>> 24) != 0) {
                WorldRenderUtils.color(outline); GL11.glLineWidth(module.getImpactOutlineWidth()); GL11.glBegin(GL11.GL_LINE_LOOP);
                for (double[] vertex : vertices) GL11.glVertex3d(vertex[0],vertex[1],0);
                GL11.glEnd();
            }
        } finally { GL11.glPopMatrix(); }
    }
    private void drawTrails(TrajectoriesModule module) { for (Map.Entry<Integer,Deque<Vec3>> entry : module.getTrails().entrySet()) { Deque<Vec3> points=entry.getValue(); if(points.size()<2)continue; GL11.glLineWidth(Math.max(.8F,module.getLineWidth().getFloat()*.7F)); GL11.glBegin(GL11.GL_LINE_STRIP); int index=0; for(Vec3 p:points){color(module.getDefaultColor().getArgb(),Math.min(1.0F,++index/(float)points.size()));GL11.glVertex3d(p.xCoord-minecraft.getRenderManager().viewerPosX,p.yCoord-minecraft.getRenderManager().viewerPosY,p.zCoord-minecraft.getRenderManager().viewerPosZ);}GL11.glEnd(); } }
    private static void color(int color,float alpha){GlStateManager.color(((color>>>16)&255)/255F,((color>>>8)&255)/255F,(color&255)/255F,Math.max(0,Math.min(1,alpha*((color>>>24)&255)/255F)));}
}
