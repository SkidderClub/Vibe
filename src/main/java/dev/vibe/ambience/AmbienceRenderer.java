package dev.vibe.ambience;

import dev.vibe.Vibe;
import dev.vibe.module.impl.meme.HypixelModule;
import dev.vibe.module.impl.visual.AmbienceModule;
import dev.vibe.ui.effect.EffectProgram;
import dev.vibe.ui.effect.EffectState;
import java.nio.FloatBuffer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.Vec3;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Small sky meshes and depth-tested particles; no full-screen buffers or scene copies. */
public final class AmbienceRenderer implements AutoCloseable {
    public static final AmbienceRenderer INSTANCE=new AmbienceRenderer();
    private EffectProgram celestial,aurora,glow;
    private boolean celestialFailed,auroraFailed,glowFailed;
    private final FloatBuffer matrix=BufferUtils.createFloatBuffer(16);
    private double rx,ry,rz,ux,uy,uz;
    private static AmbienceModule active(){
        Minecraft mc=Minecraft.getMinecraft();Vibe vibe=Vibe.getInstance();
        if(vibe==null||vibe.getModuleManager()==null||mc.theWorld==null||!mc.theWorld.provider.isSurfaceWorld()||HypixelModule.visualsSuppressed())return null;
        AmbienceModule m=vibe.getModuleManager().getModule(AmbienceModule.class);return m!=null&&m.isEnabled()?m:null;
    }
    /** The buffer is always consumed, even when the vanilla celestial sprite is hidden. */
    public static void drawSun(Object tessellator,float partial){
        AmbienceModule m=active();
        INSTANCE.body((Tessellator)tessellator,m,false,partial);
        if(m!=null)INSTANCE.sky(m,partial);
    }
    public static void drawMoon(Object tessellator,float partial){INSTANCE.body((Tessellator)tessellator,active(),true,partial);}
    private void body(Tessellator tessellator,AmbienceModule module,boolean moon,float partial){
        if(module==null||!module.effect(moon?AmbienceModule.MOON:AmbienceModule.SUN)||!celestialReady()){tessellator.draw();return;}
        try(EffectState state=new EffectState()){
            GL11.glColorMask(false,false,false,false);tessellator.draw();
        }
        AmbienceSettings.Celestial settings=moon?module.moon:module.sun;
        int phase=moon?Minecraft.getMinecraft().theWorld.getMoonPhase():0;
        if(moon&&!settings.phase.is("World"))phase=settings.phase.is("Full")?0:settings.phase.is("Half")?2:3;
        celestial(settings,moon,phase,module.scene.seconds(partial),1-Minecraft.getMinecraft().theWorld.getRainStrength(partial));
    }
    private boolean celestialReady(){
        if(celestialFailed)return false;
        if(celestial==null)try{celestial=new EffectProgram("Ambience.vert","Celestial.frag");}
        catch(Exception e){celestialFailed=true;LogManager.getLogger("Vibe").warn("Custom celestial bodies unavailable",e);}
        return celestial!=null;
    }
    void celestial(AmbienceSettings.Celestial s,boolean moon,int phase,double time,float visibility){
        if(visibility<=0||!celestialReady())return;
        try(EffectState state=new EffectState()){
            GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            celestial.bind();rgb(celestial,"Color",s.color.getArgb());rgb(celestial,"GlowColor",s.glowColor.getArgb());
            celestial.scalar("Moon",moon?1:0);celestial.scalar("Phase",phase);celestial.scalar("Time",(float)time);
            celestial.scalar("Brightness",s.brightness.getFloat());celestial.scalar("Glow",s.glow.getFloat()*alpha(s.glowColor.getArgb()));
            celestial.scalar("Halo",s.softness.getFloat());celestial.scalar("Visibility",visibility*alpha(s.color.getArgb()));
            float radius=(moon?8:10)*s.size.getFloat()*4;
            celestial.scalar("Pass",0);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);skyQuad(radius,moon);
            celestial.scalar("Pass",1);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);skyQuad(radius,moon);
        }
    }
    private static void skyQuad(float radius,boolean moon){
        double y=moon?-100:100;
        GL11.glBegin(GL11.GL_QUADS);
        uvVertex(0,0,-radius,y,-radius);uvVertex(1,0,radius,y,-radius);
        uvVertex(1,1,radius,y,radius);uvVertex(0,1,-radius,y,radius);GL11.glEnd();
    }
    /** Called in vanilla's celestial coordinate system, before terrain and clouds. */
    private void sky(AmbienceModule m,float partial){
        if(!m.effect(AmbienceModule.AURORA)&&!m.effect(AmbienceModule.STARS))return;
        Minecraft mc=Minecraft.getMinecraft();float rain=1-mc.theWorld.getRainStrength(partial),night=AmbienceScene.night(mc.theWorld,partial);
        if(rain<=.001F)return;
        GL11.glPushMatrix();
        try{
            GL11.glRotatef(-mc.theWorld.getCelestialAngle(partial)*360,1,0,0);GL11.glRotatef(90,0,1,0);
            if(m.effect(AmbienceModule.AURORA))aurora(m.aurora,m.scene.seconds(partial),rain*(m.aurora.nightOnly.isEnabled()?night:1));
            if(m.effect(AmbienceModule.STARS))meteors(m.scene.meteors(),m.stars,m.scene.seconds(partial),rain*(m.stars.nightOnly.isEnabled()?night:1));
        }finally{GL11.glPopMatrix();}
    }
    void aurora(AmbienceSettings.Aurora s,double seconds,float visibility){
        if(visibility<.001F||auroraFailed)return;
        try(EffectState state=new EffectState()){
            if(aurora==null)aurora=new EffectProgram("Ambience.vert","Aurora.frag");
            additive(false);aurora.bind();rgb(aurora,"Lower",s.lower.getArgb());rgb(aurora,"Upper",s.upper.getArgb());
            double time=seconds*s.speed.getDouble(),direction=Math.toRadians(s.direction.getDouble());
            aurora.scalar("Time",(float)time);
            int segments=s.quality.is("High")?128:s.quality.is("Low")?48:80;
            for(int layer=0;layer<3;layer++){
                aurora.scalar("Layer",layer);aurora.scalar("Opacity",visibility*s.brightness.getFloat()*(layer==0?.75F:.38F)*alpha(s.lower.getArgb()));
                double radius=95+layer*13;
                GL11.glBegin(GL11.GL_QUAD_STRIP);
                for(int i=0;i<=segments;i++){
                    double u=i/(double)segments;
                    double a=direction+(u-.5)*2.85+layer*.17+Math.sin(u*10+time*.15+layer)*.12+Math.sin(u*24-time*.1)*.025;
                    double bottom=(11+Math.sin(u*8+layer+time*.1)*7+layer*4)*s.height.getDouble();
                    double top=bottom+(45+Math.sin(u*6+layer)*12)*s.height.getDouble();
                    uvVertex(u,0,Math.sin(a)*radius,bottom,Math.cos(a)*radius);
                    uvVertex(u,1,Math.sin(a)*radius,top,Math.cos(a)*radius);
                }
                GL11.glEnd();
            }
        }catch(Exception e){auroraFailed=true;LogManager.getLogger("Vibe").warn("Polar lights unavailable",e);}
    }
    private boolean glowReady(){
        if(glowFailed)return false;
        if(glow==null)try{glow=new EffectProgram("Ambience.vert","AmbienceGlow.frag");}
        catch(Exception e){glowFailed=true;LogManager.getLogger("Vibe").warn("Ambience glow unavailable",e);}
        return glow!=null;
    }
    void meteors(List<AmbienceScene.Meteor> stars,AmbienceSettings.ShootingStars s,double time,float visibility){
        if(stars.isEmpty()||visibility<=.001F||!glowReady())return;
        try(EffectState state=new EffectState()){
            additive(false);glow.bind();glow.scalar("Glow",s.glow.getFloat());glow.scalar("Sharpness",24);basis();
            glow.scalar("Trail",1);
            GL11.glBegin(GL11.GL_QUADS);
            for(AmbienceScene.Meteor star:stars){
                float fade=star.visibility(time)*visibility;if(fade<=0)continue;
                double tail=Math.min(time-star.born,Math.toRadians(s.length.getDouble())/.52*star.duration);
                for(int i=0;i<12;i++){
                    double a=i/12D,b=(i+1)/12D;
                    Vec3 first=times(star.position(time-tail*a),120),second=times(star.position(time-tail*b),120);
                    ribbon(first,second,s.width.getDouble()*8,s.color.getArgb(),fade*(float)Math.pow(1-a,1.6),fade*(float)Math.pow(1-b,1.6));
                }
            }
            GL11.glEnd();glow.scalar("Trail",0);GL11.glBegin(GL11.GL_QUADS);
            for(AmbienceScene.Meteor star:stars){float fade=star.visibility(time)*visibility;if(fade>0)sprite(times(star.position(time),120),s.width.getDouble()*12,s.color.getArgb(),fade);}
            GL11.glEnd();
        }
    }
    public void fireflies(float partial){
        AmbienceModule m=active();if(m==null||!m.effect(AmbienceModule.FIREFLIES))return;
        Minecraft mc=Minecraft.getMinecraft();float visibility=(1-mc.theWorld.getRainStrength(partial)*.65F)*(m.fireflies.nightOnly.isEnabled()?AmbienceScene.night(mc.theWorld,partial):1);
        fireflies(m.scene.swarm,m.fireflies,partial,mc.getRenderManager().viewerPosX,mc.getRenderManager().viewerPosY,mc.getRenderManager().viewerPosZ,visibility);
    }
    void fireflies(FireflySwarm swarm,AmbienceSettings.Fireflies s,float partial,double x,double y,double z,float visibility){
        if(swarm.flies().isEmpty()||visibility<.001F||!glowReady())return;
        try(EffectState state=new EffectState()){
            additive(true);glow.bind();basis();glow.scalar("Glow",s.glow.getFloat());glow.scalar("Sharpness",150);glow.scalar("Trail",1);
            int length=Math.min(FireflySwarm.HISTORY-1,Math.round(s.trail.getFloat()*20));
            GL11.glBegin(GL11.GL_QUADS);
            for(FireflySwarm.Fly fly:swarm.flies()){
                float opacity=fly.visibility(partial)*visibility;int samples=Math.min(length,fly.samples-1);
                for(int i=0;i<samples;i++){
                    int a=fly.sample(i),b=fly.sample(i+1);
                    Vec3 first=i==0?point(fly,partial,x,y,z):new Vec3(fly.trailX[a]-x,fly.trailY[a]-y,fly.trailZ[a]-z);
                    Vec3 second=new Vec3(fly.trailX[b]-x,fly.trailY[b]-y,fly.trailZ[b]-z);
                    float f1=1-i/(float)Math.max(1,samples),f2=1-(i+1)/(float)Math.max(1,samples);
                    ribbon(first,second,s.size.getDouble()*2.3,s.trailColor.getArgb(),opacity*f1*f1*.6F,opacity*f2*f2*.6F);
                }
            }
            GL11.glEnd();glow.scalar("Trail",0);GL11.glBegin(GL11.GL_QUADS);
            for(FireflySwarm.Fly fly:swarm.flies()){
                float pulse=(float)(.6+.4*Math.pow(Math.sin((fly.age+partial)*.065+fly.seed),2));
                sprite(point(fly,partial,x,y,z),s.size.getDouble()*5,s.color.getArgb(),fly.visibility(partial)*visibility*pulse);
            }
            GL11.glEnd();
        }
    }
    private static Vec3 point(FireflySwarm.Fly f,float p,double x,double y,double z){return new Vec3(f.previousX+(f.x-f.previousX)*p-x,f.previousY+(f.y-f.previousY)*p-y,f.previousZ+(f.z-f.previousZ)*p-z);}
    private void basis(){GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,matrix);rx=matrix.get(0);ry=matrix.get(4);rz=matrix.get(8);ux=matrix.get(1);uy=matrix.get(5);uz=matrix.get(9);}
    private void sprite(Vec3 p,double size,int color,float opacity){
        rgba(color,opacity);
        uvVertex(0,0,p.xCoord-rx*size-ux*size,p.yCoord-ry*size-uy*size,p.zCoord-rz*size-uz*size);
        uvVertex(1,0,p.xCoord+rx*size-ux*size,p.yCoord+ry*size-uy*size,p.zCoord+rz*size-uz*size);
        uvVertex(1,1,p.xCoord+rx*size+ux*size,p.yCoord+ry*size+uy*size,p.zCoord+rz*size+uz*size);
        uvVertex(0,1,p.xCoord-rx*size+ux*size,p.yCoord-ry*size+uy*size,p.zCoord-rz*size+uz*size);
    }
    private static void ribbon(Vec3 a,Vec3 b,double width,int color,float first,float second){
        Vec3 direction=b.subtract(a),middle=times(a.add(b),.5),side=times(direction.crossProduct(middle).normalize(),width);
        rgba(color,first);uvVertex(0,0,a.xCoord-side.xCoord,a.yCoord-side.yCoord,a.zCoord-side.zCoord);uvVertex(1,0,a.xCoord+side.xCoord,a.yCoord+side.yCoord,a.zCoord+side.zCoord);
        rgba(color,second);uvVertex(1,1,b.xCoord+side.xCoord,b.yCoord+side.yCoord,b.zCoord+side.zCoord);uvVertex(0,1,b.xCoord-side.xCoord,b.yCoord-side.yCoord,b.zCoord-side.zCoord);
    }
    private static void additive(boolean depth){
        if(depth){GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);}else GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);GL14.glBlendEquation(GL14.GL_FUNC_ADD);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);
    }
    private static void uvVertex(double u,double v,double x,double y,double z){GL11.glTexCoord2d(u,v);GL11.glVertex3d(x,y,z);}
    private static float alpha(int c){return (c>>>24)/255F;}
    private static Vec3 times(Vec3 v,double scale){return new Vec3(v.xCoord*scale,v.yCoord*scale,v.zCoord*scale);}
    private static void rgb(EffectProgram p,String name,int c){p.vec3(name,(c>>16&255)/255F,(c>>8&255)/255F,(c&255)/255F);}
    private static void rgba(int c,float a){GL11.glColor4f((c>>16&255)/255F,(c>>8&255)/255F,(c&255)/255F,a*alpha(c));}
    @Override public void close(){if(celestial!=null)celestial.close();if(aurora!=null)aurora.close();if(glow!=null)glow.close();celestial=aurora=glow=null;}
}
