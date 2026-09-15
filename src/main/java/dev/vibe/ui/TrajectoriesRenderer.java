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
        int color = module.getDefaultColor().getArgb(); if (module.getRainbow().isEnabled()) color = Color.HSBtoRGB((System.currentTimeMillis()%3500L)/3500.0F,.75F,1.0F)|0xFF000000;
        GL11.glLineWidth(module.getLineWidth().getFloat()); GL11.glBegin(GL11.GL_LINE_STRIP);
        for (Vec3 point : path.points) { float alpha = module.getFadeOut().isEnabled() ? .94F : 1.0F; color(color,alpha); GL11.glVertex3d(point.xCoord-minecraft.getRenderManager().viewerPosX,point.yCoord-minecraft.getRenderManager().viewerPosY,point.zCoord-minecraft.getRenderManager().viewerPosZ); }
        GL11.glEnd();
        if (path.hit == null) return;
        int impact = path.hitType == 1 ? module.getEnemyColor().getArgb() : path.hitType == 3 ? module.getGroundColor().getArgb() : module.getWallColor().getArgb();
        double x=path.hit.xCoord-minecraft.getRenderManager().viewerPosX,y=path.hit.yCoord-minecraft.getRenderManager().viewerPosY,z=path.hit.zCoord-minecraft.getRenderManager().viewerPosZ;
        if (module.getLandingBox().isEnabled()) WorldRenderUtils.box(new AxisAlignedBB(x-.2D,y-.2D,z-.2D,x+.2D,y+.2D,z+.2D),impact,0,1.5F);
        if (module.getLandingCross().isEnabled()) { GL11.glLineWidth(1.5F); GL11.glBegin(GL11.GL_LINES); color(impact,1); GL11.glVertex3d(x-.3D,y,z);GL11.glVertex3d(x+.3D,y,z);GL11.glVertex3d(x,y-.3D,z);GL11.glVertex3d(x,y+.3D,z);GL11.glVertex3d(x,y,z-.3D);GL11.glVertex3d(x,y,z+.3D);GL11.glEnd(); }
    }
    private void drawTrails(TrajectoriesModule module) { for (Map.Entry<Integer,Deque<Vec3>> entry : module.getTrails().entrySet()) { Deque<Vec3> points=entry.getValue(); if(points.size()<2)continue; GL11.glLineWidth(Math.max(.8F,module.getLineWidth().getFloat()*.7F)); GL11.glBegin(GL11.GL_LINE_STRIP); int index=0; for(Vec3 p:points){color(module.getDefaultColor().getArgb(),Math.min(1.0F,++index/(float)points.size()));GL11.glVertex3d(p.xCoord-minecraft.getRenderManager().viewerPosX,p.yCoord-minecraft.getRenderManager().viewerPosY,p.zCoord-minecraft.getRenderManager().viewerPosZ);}GL11.glEnd(); } }
    private static void color(int color,float alpha){GlStateManager.color(((color>>>16)&255)/255F,((color>>>8)&255)/255F,(color&255)/255F,Math.max(0,Math.min(1,alpha*((color>>>24)&255)/255F)));}
}
