package dev.vibe.ui.render;

import dev.vibe.Vibe;
import dev.vibe.module.impl.visual.KillEffectsModule;
import dev.vibe.module.impl.visual.KillEffectsModule.Death;
import dev.vibe.ui.effect.*;
import java.nio.FloatBuffer;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Bounded local meshes and frozen models. No game entities, sounds, packets or world changes. */
public final class KillEffectsRenderer implements AutoCloseable {
    private final ModelPlayer regular=new ModelPlayer(0,false),slim=new ModelPlayer(0,true);
    private final HologramPose neutralRegular=new HologramPose(regular,new org.lwjgl.util.vector.Matrix4f(),new ResourceLocation("textures/entity/steve.png"),false,0),neutralSlim=new HologramPose(slim,new org.lwjgl.util.vector.Matrix4f(),new ResourceLocation("textures/entity/alex.png"),true,0);
    private final FloatBuffer matrix=BufferUtils.createFloatBuffer(16);
    private EffectProgram ghost;private boolean failed;
    public void render(){
        Minecraft mc=Minecraft.getMinecraft();KillEffectsModule m=Vibe.getInstance().getModuleManager().getModule(KillEffectsModule.class);
        if(m==null||!m.isEnabled()||mc.theWorld==null||mc.getRenderManager()==null||m.deaths().isEmpty())return;
        render(m,mc.getRenderManager().viewerPosX,mc.getRenderManager().viewerPosY,mc.getRenderManager().viewerPosZ,System.currentTimeMillis());
    }
    public void render(KillEffectsModule m,double ox,double oy,double oz,long now){
        Minecraft mc=Minecraft.getMinecraft();if(m.deaths().isEmpty())return;
        if(ghost==null&&!failed)try{ghost=new EffectProgram("Hologram.vert","KillGhost.frag");}catch(Exception e){failed=true;org.apache.logging.log4j.LogManager.getLogger("Vibe").warn("Kill effect shader unavailable; using native model colors",e);}
        Map<Death,Integer> skins=new IdentityHashMap<>();int prior=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try{for(Death d:m.deaths())if(!d.mode.equals("Shockwave")&&!d.mode.equals("Shattered Glass")){
            ResourceLocation location=d.pose==null?DefaultPlayerSkin.getDefaultSkin(d.id):d.pose.skin;
            mc.getTextureManager().bindTexture(location);skins.put(d,GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
        }}finally{GlStateManager.bindTexture(prior);}
        int matrixMode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);GL11.glMatrixMode(GL11.GL_MODELVIEW);
        try(EffectState ignored=new EffectState()){
            GL20.glUseProgram(0);GL11.glEnable(GL11.GL_BLEND);GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD,GL14.GL_FUNC_ADD);GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
            for(int unit=1;unit<=2;unit++){GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glDisable(GL11.GL_TEXTURE_2D);}GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glDepthMask(false);if(m.throughWalls.isEnabled())GL11.glDisable(GL11.GL_DEPTH_TEST);else GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_LINE_SMOOTH);GL11.glLineWidth(1.2F);
            for(Death d:m.deaths()){
                float t=d.progress(now);if(t>=1)continue;double dx=d.x-ox,dy=d.y-oy,dz=d.z-oz;if(dx*dx+dy*dy+dz*dz>Math.pow(m.range.getDouble()+24,2))continue;
                float alpha=m.opacity.getFloat()*(1-smooth((t-.72F)/.28F));
                GL11.glPushMatrix();try{GL11.glTranslated(dx,dy,dz);GL11.glDisable(GL11.GL_TEXTURE_2D);
                    switch(d.mode){
                        case "Shattered Glass":glass(m,d,t,alpha);break;
                        case "Dissolving Silhouette":model(m,d,ox,oy,oz,0,m.dissolveRise.getDouble()*t,t,alpha,skins.get(d),false);break;
                        case "Holy":holy(m,d,ox,oy,oz,t,alpha,skins.get(d));break;
                        case "Helicopter":helicopter(m,d,ox,oy,oz,t,alpha,skins.get(d));break;
                        default:shockwave(m,t,alpha);
                    }
                }finally{GL20.glUseProgram(0);GL11.glPopMatrix();}
            }
            dev.vibe.ui.render.esp.ChamsGlow.finish();
        }finally{GL11.glMatrixMode(matrixMode);GlStateManager.resetColor();}
    }
    private void model(KillEffectsModule m,Death d,double ox,double oy,double oz,double shift,double rise,float dissolve,float alpha,int texture,boolean holy){
        ModelPlayer model=d.pose!=null&&d.pose.slim?slim:regular;
        boolean floating=holy||d.mode.equals("Helicopter");
        if(d.pose!=null&&!floating)d.pose.apply(model);else{(model==slim?neutralSlim:neutralRegular).apply(model);model.isSneak=model.isRiding=model.isChild=false;model.swingProgress=0;model.heldItemLeft=model.heldItemRight=0;model.aimedBow=false;}
        if(holy){model.bipedRightArm.rotateAngleX=model.bipedLeftArm.rotateAngleX=0;model.bipedRightArm.rotateAngleZ=1.8F;model.bipedLeftArm.rotateAngleZ=-1.8F;model.bipedHead.rotateAngleX=-.18F;
            model.bipedRightLeg.rotateAngleX=model.bipedLeftLeg.rotateAngleX=0;ModelBase.copyModelAngles(model.bipedRightArm,model.bipedRightArmwear);ModelBase.copyModelAngles(model.bipedLeftArm,model.bipedLeftArmwear);ModelBase.copyModelAngles(model.bipedHead,model.bipedHeadwear);}
        else if(floating){model.bipedRightArm.rotateAngleZ=.12F;model.bipedLeftArm.rotateAngleZ=-.12F;model.bipedHead.rotateAngleX=.25F;ModelBase.copyModelAngles(model.bipedRightArm,model.bipedRightArmwear);ModelBase.copyModelAngles(model.bipedLeftArm,model.bipedLeftArmwear);ModelBase.copyModelAngles(model.bipedHead,model.bipedHeadwear);}
        int color=holy?m.holyColor.getArgb():m.color.getArgb();
        GL11.glPushMatrix();try{
            // Undo the effect's local base; saved model matrices already contain their world translation.
            GL11.glTranslated(shift-d.x+ox,rise-d.y+oy,-d.z+oz);
            if(d.pose!=null&&!floating){GL11.glTranslated(d.pose.originX-ox,d.pose.originY-oy,d.pose.originZ-oz);d.pose.matrix(matrix);GL11.glMultMatrix(matrix);}
            else{GL11.glTranslated(d.x-ox,d.y-oy+1.5078125,d.z-oz);GL11.glRotatef(180-d.yaw,0,1,0);GL11.glScalef(-1,-1,1);}
            GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            if(ghost!=null){ghost.bind();ghost.integer("Skin",0);ghost.vec3("Tint",(color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F);ghost.scalar("Opacity",alpha);ghost.scalar("Progress",dissolve);ghost.scalar("Detail",m.dissolveScale.getFloat());ghost.integer("Dissolve",d.mode.equals("Dissolving Silhouette")?1:0);ghost.integer("OriginalSkin",m.skin.isEnabled()?1:0);ghost.integer("MaskPass",0);ghost.scalar("GlowStrength",m.glowStrength.getFloat());}
            else{if(!m.skin.isEnabled())GL11.glDisable(GL11.GL_TEXTURE_2D);color(color,alpha);}
            ModelRenderer[] parts=HologramPose.parts(model);for(int i=0;i<parts.length;i++){if(ghost!=null)ghost.integer("OuterLayer",i>=6?1:0);parts[i].render(.0625F);}
            if(ghost!=null&&m.glow.isEnabled())dev.vibe.ui.render.esp.ChamsGlow.capture(()->{ghost.bind();ghost.integer("MaskPass",1);if(m.throughWalls.isEnabled())GL11.glDisable(GL11.GL_DEPTH_TEST);for(int i=0;i<parts.length;i++){ghost.integer("OuterLayer",i>=6?1:0);parts[i].render(.0625F);}ghost.integer("MaskPass",0);});
        }finally{GL20.glUseProgram(0);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glPopMatrix();}
    }
    private static void glass(KillEffectsModule m,Death d,float t,float alpha){
        Random random=new Random(d.seed);double seconds=t*d.duration/1000D;
        for(int i=0;i<m.shards.getInt();i++){
            double angle=random.nextDouble()*Math.PI*2,speed=m.burstSpeed.getDouble()*(.25+random.nextDouble()*.75),base=.2+random.nextDouble()*1.6;
            double x=(random.nextDouble()-.5)*.6+Math.cos(angle)*speed*seconds,y=base+(random.nextDouble()*1.8)*seconds-m.gravity.getDouble()*seconds*seconds*.5,z=(random.nextDouble()-.5)*.6+Math.sin(angle)*speed*seconds;
            if(y<.03)y=.03+Math.abs(y)*.07*Math.exp(-seconds);
            double size=m.shardSize.getDouble()*(.4+random.nextDouble());float spin=(float)(random.nextDouble()*360+seconds*(90+random.nextDouble()*180));
            GL11.glPushMatrix();GL11.glTranslated(x,y,z);GL11.glRotatef(spin,1,.4F,.7F);
            color(m.color.getArgb(),alpha*.32F);GL11.glBegin(GL11.GL_TRIANGLES);GL11.glVertex3d(-size,0,0);GL11.glVertex3d(size*.65,size*.8,0);GL11.glVertex3d(size*.5,-size*.55,0);GL11.glEnd();
            if(m.glow.isEnabled()){color(m.color.getArgb(),alpha*m.glowStrength.getFloat()*.16F);GL11.glLineWidth(3);triangle(size);}
            color(0xFFE6F8FF,alpha*.85F);GL11.glLineWidth(1);triangle(size);GL11.glPopMatrix();
        }
    }
    private static void triangle(double s){GL11.glBegin(GL11.GL_LINE_LOOP);GL11.glVertex3d(-s,0,0);GL11.glVertex3d(s*.65,s*.8,0);GL11.glVertex3d(s*.5,-s*.55,0);GL11.glEnd();}
    private static void shockwave(KillEffectsModule m,float t,float alpha){
        for(int i=0;i<m.waves.getInt();i++){float p=(t-i*.11F)/(.75F);if(p<0||p>=1)continue;double radius=m.radius.getDouble()*(1-Math.pow(1-p,2));
            ring(radius,.04+i*.008,m.waveWidth.getDouble(),m.color.getArgb(),alpha*(1-p),m.glow.isEnabled()?m.glowStrength.getFloat():0);
        }
    }
    private void holy(KillEffectsModule m,Death d,double ox,double oy,double oz,float t,float alpha,int texture){
        double rise=m.rise.getDouble()*smooth(t);int color=m.holyColor.getArgb();
        if(m.beam.isEnabled()){GL11.glBegin(GL11.GL_QUAD_STRIP);for(int i=0;i<=48;i++){double a=i*Math.PI*2/48; color(color,alpha*.1F);GL11.glVertex3d(Math.cos(a)*.55,0,Math.sin(a)*.55);color(color,0);GL11.glVertex3d(Math.cos(a)*1.2,rise+4,Math.sin(a)*1.2);}GL11.glEnd();}
        model(m,d,ox,oy,oz,0,rise,0,alpha,texture,true);
        if(m.halo.isEnabled()){GL11.glPushMatrix();GL11.glTranslated(0,rise+2.2,0);ring(.28,0,.035,color,alpha,m.glow.isEnabled()?m.glowStrength.getFloat():0);GL11.glPopMatrix();}
        if(m.wings.isEnabled()){GL11.glPushMatrix();GL11.glTranslated(0,rise+1.35,.12);GL11.glRotatef(-d.yaw,0,1,0);
            for(int side:new int[]{-1,1})for(int i=0;i<9;i++){double u=i/8D,root=side*.12,tip=side*(.45+u*1.1),yy=.75*Math.sin(u*Math.PI*.7),flap=.1*Math.sin(t*10);
                color(color,alpha*(.8F-(float)u*.25F));GL11.glBegin(GL11.GL_TRIANGLES);GL11.glVertex3d(root,0,0);GL11.glVertex3d(tip,yy+flap,.08);GL11.glVertex3d(tip-side*.12,yy-.45-u*.25+flap,.04);GL11.glEnd();
            }GL11.glPopMatrix();}
    }
    private void helicopter(KillEffectsModule m,Death d,double ox,double oy,double oz,float t,float alpha,int texture){
        double rise=m.lift.getDouble()*smooth((t-.28F)/.52F),exit=6*smooth((t-.82F)/.18F),approach=-6*(1-smooth(t/.2F)),shift=approach+exit;
        double anchorY=rise+m.rope.getDouble()+1.35,ropeBottom=t<.25?anchorY-m.rope.getDouble()*smooth((t-.08F)/.17F):rise+1.35;
        model(m,d,ox,oy,oz,exit,rise,0,alpha,texture,false);
        color(0xFFD0BD91,alpha);GL11.glLineWidth(2);GL11.glBegin(GL11.GL_LINE_STRIP);for(int i=0;i<=16;i++){double u=i/16D;GL11.glVertex3d(shift+(exit-shift)*u+Math.sin(u*Math.PI)*.08*Math.sin(t*17),anchorY+(ropeBottom-anchorY)*u,0);}GL11.glEnd();
        GL11.glPushMatrix();try{GL11.glTranslated(shift,anchorY+.3,0);double size=m.helicopterSize.getDouble();GL11.glScaled(size,size,size);int body=m.helicopterColor.getArgb();
            box(-.52,-.18,-.95,.52,.42,.8,body,alpha);box(-.4,-.1,-1.22,.4,.32,-.93,0xFF152E3E,alpha);box(-.53,.04,-.82,-.515,.35,.15,0xFF558296,alpha);box(.515,.04,-.82,.53,.35,.15,0xFF558296,alpha);
            box(-.12,.05,.65,.12,.23,2.65,body,alpha);box(-.06,.12,2.3,.06,.8,2.76,body,alpha);box(-.7,.17,2.18,.7,.23,2.5,body,alpha);
            for(int side:new int[]{-1,1}){box(side*.68-.035,-.55,-1,side*.68+.035,-.48,.95,0xFF73838A,alpha);for(double zz:new double[]{-.55,.5}){box(side*.4-.035,-.5,zz,side*.4+.035,-.15,zz+.07,0xFF55666B,alpha);}}
            box(-.06,.42,-.06,.06,.72,.06,0xFF6F7F84,alpha);GL11.glPushMatrix();GL11.glTranslated(0,.73,0);GL11.glRotatef(t*d.duration*.36F*m.rotorSpeed.getFloat(),0,1,0);
            for(int i=0;i<4;i++){GL11.glRotatef(90,0,1,0);box(.12,-.012,-.07,2.1,.012,.07,0xFF263037,alpha*.88F);}GL11.glPopMatrix();
            if(m.glow.isEnabled()){color(0xFFB6D4E4,alpha*.07F);GL11.glBegin(GL11.GL_TRIANGLE_FAN);GL11.glVertex3d(0,.735,0);for(int i=0;i<=48;i++){double a=i*Math.PI*2/48;GL11.glVertex3d(Math.cos(a)*2.1,.735,Math.sin(a)*2.1);}GL11.glEnd();}
            GL11.glPushMatrix();GL11.glTranslated(.08,.43,2.6);GL11.glRotatef(t*d.duration*.6F*m.rotorSpeed.getFloat(),1,0,0);for(int i=0;i<3;i++){GL11.glRotatef(120,1,0,0);box(-.025,0,-.035,.025,.48,.035,0xFF202B31,alpha);}GL11.glPopMatrix();
            box(-.56,.23,-.1,-.52,.28,0,0xFFFF5353,alpha);box(.52,.23,-.1,.56,.28,0,0xFF64FFAA,alpha);
        }finally{GL11.glPopMatrix();}
    }
    private static void box(double x,double y,double z,double X,double Y,double Z,int color,float alpha){
        GL11.glBegin(GL11.GL_QUADS);color(color,alpha);quad(x,Y,z,X,Y,z,X,Y,Z,x,Y,Z);color(shade(color,.65F),alpha);quad(x,y,z,x,y,Z,X,y,Z,X,y,z);color(shade(color,.8F),alpha);quad(x,y,z,X,y,z,X,Y,z,x,Y,z);quad(x,y,Z,x,Y,Z,X,Y,Z,X,y,Z);color(shade(color,.9F),alpha);quad(x,y,z,x,Y,z,x,Y,Z,x,y,Z);quad(X,y,z,X,y,Z,X,Y,Z,X,Y,z);GL11.glEnd();
    }
    private static void quad(double... p){for(int i=0;i<p.length;i+=3)GL11.glVertex3d(p[i],p[i+1],p[i+2]);}
    private static int shade(int c,float s){return c&0xFF000000|Math.round((c>>16&255)*s)<<16|Math.round((c>>8&255)*s)<<8|Math.round((c&255)*s);}
    private static void ring(double radius,double y,double width,int color,float alpha,float glow){
        if(glow>0)ringMesh(Math.max(0,radius-width*2),radius+width*2,y,color,alpha*glow*.12F);
        ringMesh(Math.max(0,radius-width/2),radius+width/2,y+.001,color,alpha);
    }
    private static void ringMesh(double r,double R,double y,int c,float alpha){color(c,alpha);GL11.glBegin(GL11.GL_QUAD_STRIP);for(int i=0;i<=80;i++){double a=i*Math.PI*2/80;GL11.glVertex3d(Math.cos(a)*r,y,Math.sin(a)*r);GL11.glVertex3d(Math.cos(a)*R,y,Math.sin(a)*R);}GL11.glEnd();}
    private static void color(int c,float alpha){GL11.glColor4f((c>>16&255)/255F,(c>>8&255)/255F,(c&255)/255F,(c>>>24)/255F*Math.max(0,alpha));}
    private static float smooth(float p){p=Math.max(0,Math.min(1,p));return p*p*(3-2*p);}
    @Override public void close(){if(ghost!=null)ghost.close();ghost=null;failed=false;}
}
