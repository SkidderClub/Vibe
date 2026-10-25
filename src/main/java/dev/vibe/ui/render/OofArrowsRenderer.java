package dev.vibe.ui.render;

import dev.vibe.Vibe;
import dev.vibe.module.impl.visual.OofArrowsModule;
import dev.vibe.module.impl.meme.HypixelModule;
import dev.vibe.ui.effect.EffectState;
import dev.vibe.ui.render.esp.BoxProjection;
import java.awt.Color;
import java.nio.FloatBuffer;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.*;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Captures the actual world projection, including camera/FOV changes, before drawing in GUI space. */
public final class OofArrowsRenderer {
    private final FloatBuffer buffer=BufferUtils.createFloatBuffer(16);
    private final float[] view=new float[16],projection=new float[16];
    private final List<Arrow> arrows=new ArrayList<Arrow>();
    private Object world;
    public void clear() {arrows.clear();world=null;}
    public void capture(float partialTicks) {
        Minecraft mc=Minecraft.getMinecraft();OofArrowsModule s=settings();arrows.clear();world=mc.theWorld;
        if(s==null||!s.isEnabled()||mc.thePlayer==null||mc.theWorld==null)return;
        buffer.clear();GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,buffer);buffer.get(view);
        buffer.clear();GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX,buffer);buffer.get(projection);
        double cx=mc.getRenderManager().viewerPosX,cy=mc.getRenderManager().viewerPosY,cz=mc.getRenderManager().viewerPosZ;
        // Inverse rigid camera transform, relative to the world's render origin.
        // This includes third person and Freecam's detached camera translation.
        Vec3 eye=new Vec3(cx-view[0]*view[12]-view[1]*view[13]-view[2]*view[14],
                cy-view[4]*view[12]-view[5]*view[13]-view[6]*view[14],
                cz-view[8]*view[12]-view[9]*view[13]-view[10]*view[14]);
        boolean checkVisibility=!s.visibility.is("All")||s.visibleOpacity.getDouble()!=s.occludedOpacity.getDouble();
        HypixelModule hypixel=Vibe.getInstance().getModuleManager().getModule(HypixelModule.class);
        ScaledResolution resolution=new ScaledResolution(mc);
        for(Object value:mc.theWorld.loadedEntityList) {
            if(!(value instanceof EntityLivingBase))continue;
            EntityLivingBase e=(EntityLivingBase)value;
            if(e==mc.thePlayer||e==mc.getRenderViewEntity()||!e.isEntityAlive()||e.isDead||!accept(s,e))continue;
            double x=e.lastTickPosX+(e.posX-e.lastTickPosX)*partialTicks,
                    y=e.lastTickPosY+(e.posY-e.lastTickPosY)*partialTicks,z=e.lastTickPosZ+(e.posZ-e.lastTickPosZ)*partialTicks;
            double distance=Math.sqrt((x-eye.xCoord)*(x-eye.xCoord)+(y-eye.yCoord)*(y-eye.yCoord)+(z-eye.zCoord)*(z-eye.zCoord));
            boolean invisible=e.isInvisible();
            if(distance>s.range.getDouble()||s.invisibility.is("Only")&&!invisible||s.invisibility.is("Exclude")&&invisible)continue;
            if(s.ignoreFriends.isEnabled()&&e instanceof EntityPlayer&&Vibe.getInstance().getFriendManager()!=null
                    &&Vibe.getInstance().getFriendManager().isFriend(e.getName()))continue;
            AxisAlignedBB box=e.getEntityBoundingBox().offset(x-e.posX-cx,y-e.posY-cy,z-e.posZ-cz);
            boolean inView=visible(box,view,projection,resolution.getScaledWidth(),resolution.getScaledHeight());
            if(s.display.is("Out of FOV")&&inView||s.display.is("In FOV")&&!inView)continue;
            boolean lineOfSight=!checkVisibility||mc.theWorld.rayTraceBlocks(eye,new Vec3(x,y+e.getEyeHeight(),z),false,true,false)==null;
            if(s.visibility.is("Visible only")&&!lineOfSight||s.visibility.is("Occluded only")&&lineOfSight)continue;
            if((lineOfSight?s.visibleOpacity.getFloat():s.occludedOpacity.getFloat())<=0)continue;
            arrows.add(new Arrow(e.getName(),distance,bearing(view,x-cx,y+e.height*.5-cy,z-cz),lineOfSight,
                    hypixel==null?0:hypixel.murdererColor(e)));
        }
        Collections.sort(arrows,Comparator.comparingDouble(a->a.distance));
        if(arrows.size()>s.limit.getInt())arrows.subList(s.limit.getInt(),arrows.size()).clear();
    }
    private static boolean accept(OofArrowsModule s,EntityLivingBase e) {
        return e instanceof EntityPlayer?s.entities.isSelected("Players"):e instanceof IMob?s.entities.isSelected("Hostile mobs"):
                e instanceof EntityAnimal&&s.entities.isSelected("Animals");
    }
    public static boolean visible(AxisAlignedBB box,float[] view,float[] projection,int width,int height) {
        return BoxProjection.bounds(new double[]{box.minX,box.minY,box.minZ},new double[]{box.maxX,box.maxY,box.maxZ},view,projection,width,height)!=null;
    }
    private static OofArrowsModule settings() {
        return Vibe.getInstance()==null||Vibe.getInstance().getModuleManager()==null?null:Vibe.getInstance().getModuleManager().getModule(OofArrowsModule.class);
    }
    public void render() {
        Minecraft mc=Minecraft.getMinecraft();OofArrowsModule s=settings();
        if(s==null||!s.isEnabled()||mc.theWorld==null||mc.theWorld!=world||mc.gameSettings.hideGUI)return;
        ScaledResolution r=new ScaledResolution(mc);draw(s,arrows,r.getScaledWidth(),r.getScaledHeight(),System.currentTimeMillis());
    }
    public void draw(OofArrowsModule s,List<Arrow> entries,int width,int height,long time) {
        Minecraft mc=Minecraft.getMinecraft();
        for(int i=entries.size()-1;i>=0;i--) {
            Arrow arrow=entries.get(i);float size=s.size.getFloat(),length=size*s.length.getFloat(),half=size*s.width.getFloat()*.5F;
            float extent=Math.max(length*.5F,half)+(s.glow.isEnabled()?s.glowRadius.getFloat():0)+1.5F;
            float[] p=position(width,height,s.radius.getFloat(),extent,s.placement.is("Oval"),arrow.bearing);
            int c=color(s,time,i,arrow.distance,arrow.lineOfSight,arrow.overrideColor);
            if((c>>>24)==0)continue;
            try(EffectState ignored=new EffectState()) {
                GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glPushMatrix();GL11.glTranslatef(p[0],p[1],0);GL11.glRotatef(p[2],0,0,1);
                if(s.glow.isEnabled())for(int pass=4;pass>=1;pass--) {
                    float extra=s.glowRadius.getFloat()*pass/4;
                    glyph(s.style.getValue(),length+extra*2,half+extra*.6F,c,s.glowStrength.getFloat()*.035F*(5-pass));
                }
                if(s.outline.isEnabled())glyph(s.style.getValue(),length+2.2F,half+1.1F,s.outlineColor.getArgb(),(c>>>24)/255F);
                glyph(s.style.getValue(),length,half,c,1);
                GL11.glPopMatrix();
            }
            if((c>>>24)>=4&&(s.names.isEnabled()||s.distances.isEnabled())) {
                String text=(s.names.isEnabled()?arrow.name:"")+(s.distances.isEnabled()?(s.names.isEnabled()?" · ":"")+Math.round(arrow.distance)+"m":"");
                int tw=mc.fontRendererObj.getStringWidth(text);float tx=Math.max(2,Math.min(width-tw-2,p[0]-tw/2F));
                mc.fontRendererObj.drawStringWithShadow(text,tx,Math.max(2,Math.min(height-10,p[1]+size+4)),c);
            }
        }
        GlStateManager.color(1,1,1,1);
    }
    public static float[] position(int width,int height,float radius,float size,boolean oval,double bearing) {
        float margin=size+6,ry=Math.max(0,Math.min(radius,Math.min(width,height)/2F-margin));
        float rx=oval?Math.min(width/2F-margin,ry*width/(float)Math.max(1,height)):ry;
        float dx=(float)Math.sin(bearing)*rx,dy=-(float)Math.cos(bearing)*ry;
        return new float[]{width/2F+dx,height/2F+dy,(float)Math.toDegrees(Math.atan2(dy,dx))};
    }
    /** Clockwise from camera-forward; OpenGL camera-right is +X and forward is -Z. */
    public static double bearing(float[] view,double x,double y,double z) {
        double right=view[0]*x+view[4]*y+view[8]*z+view[12];
        double forward=-(view[2]*x+view[6]*y+view[10]*z+view[14]);
        return Math.atan2(right,forward);
    }
    public static int color(OofArrowsModule s,long now,int index,double distance) {
        return color(s,now,index,distance,true);
    }
    public static int color(OofArrowsModule s,long now,int index,double distance,boolean lineOfSight) {
        return color(s,now,index,distance,lineOfSight,0);
    }
    public static int color(OofArrowsModule s,long now,int index,double distance,boolean lineOfSight,int override) {
        double phase=Math.floorMod(now,600000L)*s.speed.getDouble()/6000+index*.085;
        int color=s.color.getArgb();
        if(override!=0)color=override;
        else if(s.colorMode.is("Fade"))color=dev.vibe.ui.RenderUtils.blend(color,s.secondary.getArgb(),(float)(.5+.5*Math.sin(phase*Math.PI*2)));
        else if(s.colorMode.is("Rainbow"))color=color&0xFF000000|Color.HSBtoRGB((float)(phase%1),s.saturation.getFloat(),1)&0xFFFFFF;
        float fade=s.distanceFade.isEnabled()?Math.max(s.minimumOpacity.getFloat(),1-(float)(distance/s.range.getDouble())):1;
        fade*=lineOfSight?s.visibleOpacity.getFloat():s.occludedOpacity.getFloat();
        return Math.round((color>>>24)*fade)<<24|color&0xFFFFFF;
    }
    private static void glyph(String style,float length,float half,int color,float opacity) {
        GL11.glColor4f((color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,(color>>>24)/255F*opacity);
        GL11.glBegin(GL11.GL_TRIANGLES);
        float tip=length*.5F,tail=-tip;
        if(style.equals("Triangle")) {v(tip,0);v(tail,-half);v(tail,half);}
        else {
            float notch=style.equals("Chevron")?length*.2F:-length*.18F;
            v(tip,0);v(tail,-half);v(notch,0);
            v(tip,0);v(notch,0);v(tail,half);
        }
        GL11.glEnd();
    }
    private static void v(float x,float y) {GL11.glVertex2f(x,y);}
    public static final class Arrow {
        public final String name;public final double distance,bearing;public final boolean lineOfSight;public final int overrideColor;
        public Arrow(String name,double distance,double bearing) {this(name,distance,bearing,true);}
        public Arrow(String name,double distance,double bearing,boolean lineOfSight) {this(name,distance,bearing,lineOfSight,0);}
        public Arrow(String name,double distance,double bearing,boolean lineOfSight,int overrideColor) {this.name=name;this.distance=distance;this.bearing=bearing;this.lineOfSight=lineOfSight;this.overrideColor=overrideColor;}
    }
}
