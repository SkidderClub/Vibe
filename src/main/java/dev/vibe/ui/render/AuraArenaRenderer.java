package dev.vibe.ui.render;

import dev.vibe.aiaura.RecorderArena;
import dev.vibe.aiaura.MouseMath;
import dev.vibe.aiaura.ArenaPhysics;
import dev.vibe.ui.effect.EffectState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.util.glu.GLU;

/** A dark simulation space with contrasting geometry, drawn into the game window only. */
public final class AuraArenaRenderer {
    private final ModelPlayer rig=new ModelPlayer(0,false);
    private final ModelPlayer playerRig=new ModelPlayer(0,false),slimRig=new ModelPlayer(0,true);
    private final Entity dummy=new Dummy();
    public void render(RecorderArena arena,float partial){
        Minecraft mc=Minecraft.getMinecraft();int skin=skinTexture(mc),matrix=GL11.glGetInteger(GL11.GL_MATRIX_MODE);ModelPlayer ownRig=ownRig(mc);
        try(EffectState state=new EffectState()){
            GL20.glUseProgram(0);for(int unit=1;unit<=2;unit++){GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glDisable(GL11.GL_TEXTURE_2D);}GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glViewport(0,0,mc.displayWidth,mc.displayHeight);GL11.glDepthMask(true);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);GL11.glClearColor(.025F,.031F,.044F,1);GL11.glClearDepth(1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glEnable(GL11.GL_FOG);GL11.glFogi(GL11.GL_FOG_MODE,GL11.GL_LINEAR);GL11.glFogf(GL11.GL_FOG_START,12);GL11.glFogf(GL11.GL_FOG_END,44);GL11.glFog(GL11.GL_FOG_COLOR,(java.nio.FloatBuffer)BufferUtils.createFloatBuffer(4).put(new float[]{.025F,.031F,.044F,1}).flip());
            GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();GL11.glLoadIdentity();GLU.gluPerspective(arena.fov,(float)arena.aspect,.05F,100);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();GL11.glLoadIdentity();GL11.glTranslated(0,0,-cameraDistance(arena,partial));GL11.glRotatef(viewPitch(arena,partial),1,0,0);GL11.glRotatef(viewYaw(arena,partial)+180,0,1,0);
            double x=arena.previousX+(arena.x-arena.previousX)*partial,y=arena.previousY+(arena.y-arena.previousY)*partial,z=arena.previousZ+(arena.z-arena.previousZ)*partial;GL11.glTranslated(-x,-y-1.62,-z);
            try{
                GL11.glColor4f(.06F,.08F,.10F,1);quad(-64,0,-64,64,0,-64,64,0,64,-64,0,64);
                GL11.glEnable(GL11.GL_LINE_SMOOTH);GL11.glLineWidth(1);GL11.glBegin(GL11.GL_LINES);for(int i=-32;i<=32;i++){if(i%4==0)GL11.glColor4f(.3F,.44F,.49F,.65F);else GL11.glColor4f(.17F,.25F,.29F,.45F);GL11.glVertex3d(i*2,.003,-64);GL11.glVertex3d(i*2,.003,64);GL11.glVertex3d(-64,.003,i*2);GL11.glVertex3d(64,.003,i*2);}GL11.glEnd();
                for(RecorderArena.Pillar pillar:arena.pillars){GL11.glPushMatrix();GL11.glTranslated(pillar.x,0,pillar.z);cube(pillar.width,pillar.height);GL11.glPopMatrix();}
                for(RecorderArena.Enemy enemy:arena.enemies)enemy(arena,enemy,partial);
                if(arena.perspective!=0)avatar(arena,ownRig,skin,partial);
                if(arena.sparkTicks>0){GL11.glLineWidth(2);GL11.glColor4f(.08F,.69F,.78F,arena.sparkTicks/8F);GL11.glBegin(GL11.GL_LINES);for(int i=0;i<10;i++){double a=i*Math.PI*.2,length=(9-arena.sparkTicks)*.025;GL11.glVertex3d(arena.sparkX,arena.sparkY,arena.sparkZ);GL11.glVertex3d(arena.sparkX+Math.cos(a)*length,arena.sparkY+Math.sin(a)*length,arena.sparkZ+Math.sin(a*3)*length*.5);}GL11.glEnd();}
                if(arena.perspective==0)hand(arena,ownRig,skin,partial);
            }finally{GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopMatrix();GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();GL11.glMatrixMode(matrix);}
        }
    }
    private void enemy(RecorderArena arena,RecorderArena.Enemy e,float partial){
        double x=e.previousX+(e.x-e.previousX)*partial,y=e.previousY+(e.y-e.previousY)*partial,z=e.previousZ+(e.z-e.previousZ)*partial;float alpha=Math.min(1,e.age/10F)*(e.health<=0?Math.max(0,1-e.deadAge/16F):1);
        GL11.glPushMatrix();GL11.glTranslated(x,y,z);GL11.glRotatef(180-e.previousYaw-(float)MouseMath.wrap(e.yaw-e.previousYaw)*partial,0,1,0);GL11.glScalef(-.9375F,-.9375F,.9375F);GL11.glTranslatef(0,-1.5078125F,0);if(e.health<=0)GL11.glRotatef(e.deadAge*5.5F,0,0,1);
        try{
            float walk=(float)Math.hypot(e.vx,e.vz)*5;rig.isChild=false;rig.isSneak=false;rig.isRiding=false;rig.swingProgress=e.swingTicks>0?1-e.swingTicks/6F:0;rig.heldItemRight=0;if(e.hurtTicks>0)GL11.glColor4f(.72F,.31F,.28F,alpha);else GL11.glColor4f(.38F,.49F,.58F,alpha);
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);GL11.glPolygonOffset(1,1);rig.render(dummy,e.age*.7F,walk,e.age,0,0,.0625F);GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glDepthMask(false);GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK,GL11.GL_LINE);GL11.glLineWidth(e==arena.selected?1.5F:1);if(e==arena.selected)GL11.glColor4f(.68F,.78F,.42F,.85F*alpha);else GL11.glColor4f(.29F,.58F,.65F,.65F*alpha);rig.render(dummy,e.age*.7F,walk,e.age,0,0,.0625F);GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK,GL11.GL_FILL);GL11.glDepthMask(true);
        }finally{GL11.glPopMatrix();}
        if(e.health>0&&arena.visible(e)){
            GL11.glPushMatrix();GL11.glTranslated(x,y+2.05,z);GL11.glRotatef(-viewYaw(arena,partial),0,1,0);GL11.glRotatef(viewPitch(arena,partial),1,0,0);GL11.glColor4f(.13F,.16F,.19F,.9F);quad(-.38,0,0,.38,0,0,.38,.045,0,-.38,.045,0);GL11.glColor4f(.68F,.78F,.42F,.9F);double right=-.38+.76*Math.max(0,e.health)/20D;quad(-.38,0,-.001,right,0,-.001,right,.045,-.001,-.38,.045,-.001);GL11.glPopMatrix();
        }
    }
    private static int skinTexture(Minecraft mc){
        ResourceLocation skin=new ResourceLocation("textures/entity/steve.png");try{if(mc.thePlayer!=null)skin=mc.thePlayer.getLocationSkin();}catch(RuntimeException ignored){/* Offscreen/default-profile fallback. */}
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GlStateManager.setActiveTexture(GL13.GL_TEXTURE1);GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);int previous=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try{mc.getTextureManager().bindTexture(skin);return mc.getTextureManager().getTexture(skin).getGlTextureId();}finally{GlStateManager.bindTexture(previous);GlStateManager.setActiveTexture(active);}
    }
    private ModelPlayer ownRig(Minecraft mc){try{if(mc.thePlayer!=null&&"slim".equals(mc.thePlayer.getSkinType()))return slimRig;}catch(RuntimeException ignored){}return playerRig;}
    private void avatar(RecorderArena a,ModelPlayer model,int texture,float partial){
        GL11.glPushMatrix();try{
            GL11.glTranslated(a.previousX+(a.x-a.previousX)*partial,a.previousY+(a.y-a.previousY)*partial,a.previousZ+(a.z-a.previousZ)*partial);GL11.glRotatef(180-a.cameraYaw(partial),0,1,0);GL11.glScalef(-.9375F,-.9375F,.9375F);GL11.glTranslatef(0,-1.5078125F,0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL11.glColor4f(.85F,.85F,.85F,1);model.isChild=model.isRiding=false;model.isSneak=(a.keys&RecorderArena.SNEAK)!=0;model.heldItemRight=0;model.swingProgress=a.swingProgress(partial);
            model.render(dummy,a.limbSwing-a.limbAmount*(1-partial),a.previousLimbAmount+(a.limbAmount-a.previousLimbAmount)*partial,a.ticks+partial,0,a.cameraPitch(partial),.0625F);
        }finally{GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glPopMatrix();}
    }
    private void hand(RecorderArena a,ModelPlayer model,int texture,float partial){
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();GL11.glLoadIdentity();GLU.gluPerspective(70,(float)a.aspect,.05F,100);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();GL11.glLoadIdentity();
        try{
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);GL11.glDisable(GL11.GL_FOG);GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL11.glColor4f(.85F,.85F,.85F,1);
            float swing=a.swingProgress(partial),root=MathHelper.sqrt_float(swing),pi=(float)Math.PI;
            // Native ItemRenderer.renderPlayerArm transforms, with an equipped bare hand.
            GL11.glTranslatef(-.3F*MathHelper.sin(root*pi),.4F*MathHelper.sin(root*pi*2),-.4F*MathHelper.sin(swing*pi));GL11.glTranslatef(.64000005F,-.6F,-.71999997F);GL11.glRotatef(45,0,1,0);
            GL11.glRotatef(MathHelper.sin(root*pi)*70,0,1,0);GL11.glRotatef(MathHelper.sin(swing*swing*pi)*-20,0,0,1);GL11.glTranslatef(-1,3.6F,3.5F);GL11.glRotatef(120,0,0,1);GL11.glRotatef(200,1,0,0);GL11.glRotatef(-135,0,1,0);GL11.glTranslatef(5.6F,0,0);
            model.isChild=model.isSneak=model.isRiding=false;model.swingProgress=0;model.setRotationAngles(0,0,0,0,0,.0625F,dummy);model.bipedRightArm.rotateAngleX=0;model.renderRightArm();
        }finally{GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glPopMatrix();GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();GL11.glMatrixMode(GL11.GL_MODELVIEW);}
    }
    public static float viewYaw(RecorderArena arena,float partial){return arena.cameraYaw(partial)+(arena.perspective==2?180:0);}
    public static float viewPitch(RecorderArena arena,float partial){return arena.perspective==2?-arena.cameraPitch(partial):arena.cameraPitch(partial);}
    public static double cameraDistance(RecorderArena a,float partial){
        if(a.perspective==0)return .1;double yaw=Math.toRadians(viewYaw(a,partial)),pitch=Math.toRadians(viewPitch(a,partial));double x=a.previousX+(a.x-a.previousX)*partial,y=a.previousY+(a.y-a.previousY)*partial+1.62,z=a.previousZ+(a.z-a.previousZ)*partial;
        double dx=Math.sin(yaw)*Math.cos(pitch)*4,dy=Math.sin(pitch)*4,dz=-Math.cos(yaw)*Math.cos(pitch)*4,distance=4;
        for(int i=0;i<8;i++){Vec3 start=new Vec3(x+((i&1)==0?-.1:.1),y+((i&2)==0?-.1:.1),z+((i&4)==0?-.1:.1)),end=start.addVector(dx,dy,dz);for(AxisAlignedBB box:ArenaPhysics.geometry(a.pillars)){MovingObjectPosition hit=box.calculateIntercept(start,end);if(hit!=null)distance=Math.min(distance,Math.max(.1,start.distanceTo(hit.hitVec)-.15));}}
        return distance;
    }
    private static void cube(double w,double h){double a=w*.5;GL11.glColor4f(.25F,.3F,.35F,1);quad(-a,h,-a,a,h,-a,a,h,a,-a,h,a);GL11.glColor4f(.12F,.16F,.20F,1);quad(-a,0,-a,-a,h,-a,a,h,-a,a,0,-a);quad(a,0,a,a,h,a,-a,h,a,-a,0,a);GL11.glColor4f(.18F,.23F,.28F,1);quad(-a,0,a,-a,h,a,-a,h,-a,-a,0,-a);quad(a,0,-a,a,h,-a,a,h,a,a,0,a);GL11.glColor4f(.3F,.53F,.6F,.7F);GL11.glBegin(GL11.GL_LINES);for(double x:new double[]{-a,a})for(double z:new double[]{-a,a}){GL11.glVertex3d(x,0,z);GL11.glVertex3d(x,h,z);}GL11.glEnd();}
    private static void quad(double... xyz){GL11.glBegin(GL11.GL_QUADS);for(int i=0;i<xyz.length;i+=3)GL11.glVertex3d(xyz[i],xyz[i+1],xyz[i+2]);GL11.glEnd();}
    private static final class Dummy extends Entity {Dummy(){super(null);}@Override protected void entityInit(){}@Override protected void readEntityFromNBT(NBTTagCompound tag){}@Override protected void writeEntityToNBT(NBTTagCompound tag){}}
}
