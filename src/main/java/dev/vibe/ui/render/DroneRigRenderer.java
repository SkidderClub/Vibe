package dev.vibe.ui.render;
import dev.vibe.camera.*;
import dev.vibe.features.WorldDraw;
import dev.vibe.module.impl.movement.FreecamModule;
import dev.vibe.ui.effect.EffectState;
import java.util.*;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.*;
/** Low-poly carbon quadcopter, batched emissive trail and cached path obstruction preview. */
public final class DroneRigRenderer {
    private long checkAt;private final List<Boolean> clear=new ArrayList<>();private Object world;
    public void render(FreecamModule m){Minecraft mc=Minecraft.getMinecraft();if(m==null||!m.hasCamera()||mc.theWorld==null)return;FpvFlight f=m.getFlight();long now=System.nanoTime()/1000000;
        try(EffectState state=new EffectState()){GL20.glUseProgram(0);GL11.glPushMatrix();try{GL11.glTranslated(-mc.getRenderManager().viewerPosX,-mc.getRenderManager().viewerPosY,-mc.getRenderManager().viewerPosZ);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_LIGHTING);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glEnable(GL11.GL_DEPTH_TEST);
            if(m.droneTrail.isEnabled()&&m.trail.size()>1){GL11.glDepthMask(false);for(int pass=m.trailStyle.glow.isEnabled()?0:1;pass<2;pass++){GL11.glBlendFunc(GL11.GL_SRC_ALPHA,pass==0?GL11.GL_ONE:GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glLineWidth(m.trailStyle.width.getFloat()+(pass==0?4:0));GL11.glBegin(GL11.GL_LINE_STRIP);for(FreecamModule.TrailPoint p:m.trail){float age=Math.max(0,1-(now-p.born)/(float)(m.trailLifetime.getDouble()*1000));int c=m.trailStyle.color(mc.thePlayer.getPositionVector().distanceTo(p.position),128,p.born*.0002);WorldDraw.color((c&0xFFFFFF)|((int)((c>>>24)*age*(pass==0?.22:1))<<24));GL11.glVertex3d(p.position.xCoord,p.position.yCoord,p.position.zCoord);}GL11.glEnd();}}
            if(m.cameraRig.isEnabled()&&m.rigPreview.isEnabled()){List<CameraRig.Frame> frames=m.rig.frames();if(world!=mc.theWorld||now>=checkAt||clear.size()!=Math.max(0,frames.size()-1)){world=mc.theWorld;checkAt=now+500;clear.clear();for(int i=1;i<frames.size();i++)clear.add(CameraRig.clear(frames.get(i-1).position,frames.get(i).position));}GL11.glLineWidth(1.6F);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glBegin(GL11.GL_LINES);for(int i=1;i<frames.size();i++){WorldDraw.color(clear.get(i-1)?0xCC8DDD8A:0xEFFF7088);net.minecraft.util.Vec3 a=frames.get(i-1).position,b=frames.get(i).position;GL11.glVertex3d(a.xCoord,a.yCoord,a.zCoord);GL11.glVertex3d(b.xCoord,b.yCoord,b.zCoord);}GL11.glEnd();}
            if(m.droneView()){GL11.glDepthMask(true);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glTranslated(f.x,f.y,f.z);GL11.glRotated(-f.yaw(),0,1,0);GL11.glRotated(f.pitch(),1,0,0);GL11.glRotated(f.roll(),0,0,1);box(-.105,-.05,-.15,.105,.055,.15,0xFF28303B);box(-.075,.055,-.105,.075,.1,.08,0xFF46515D);
                for(int x:new int[]{-1,1})for(int z:new int[]{-1,1}){GL11.glPushMatrix();GL11.glRotatef(x*z*45,0,1,0);box(-.025,-.025,0,.025,.025,.34,0xFF171C25);GL11.glPopMatrix();GL11.glPushMatrix();GL11.glTranslatef(x*.245F,0,z*.245F);box(-.055,-.045,-.055,.055,.045,.055,0xFF536371);GL11.glTranslatef(0,.055F,0);GL11.glRotatef(now*(float)(.3+f.throttle*1.5)*(x*z),0,1,0);box(-.13,0,-.014,.13,.005,.014,0xA2BBC9D4);box(-.014,0,-.13,.014,.005,.13,0xA2BBC9D4);GL11.glPopMatrix();}
                box(-.085,-.025,.151,.085,.025,.18,0xFF0D131E);box(-.045,-.015,.181,.045,.015,.185,0xFF4AC7E3);box(-.09,-.02,-.157,-.045,.02,-.151,0xFFEF6973);box(.045,-.02,-.157,.09,.02,-.151,0xFFEF6973);
            }
        }finally{GL11.glPopMatrix();}}
    }
    private static void box(double x,double y,double z,double X,double Y,double Z,int color){double[][] p={{x,y,z},{X,y,z},{X,Y,z},{x,Y,z},{x,y,Z},{X,y,Z},{X,Y,Z},{x,Y,Z}};int[][] faces={{0,1,2,3},{5,4,7,6},{4,0,3,7},{1,5,6,2},{3,2,6,7},{4,5,1,0}};GL11.glBegin(GL11.GL_QUADS);for(int i=0;i<faces.length;i++){float light=.65F+i*.065F;GL11.glColor4f((color>>16&255)/255F*light,(color>>8&255)/255F*light,(color&255)/255F*light,(color>>>24)/255F);for(int at:faces[i])GL11.glVertex3d(p[at][0],p[at][1],p[at][2]);}GL11.glEnd();}
}
