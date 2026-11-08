package dev.vibe.ui.render;

import dev.vibe.Vibe;
import dev.vibe.companion.*;
import dev.vibe.module.impl.meme.GirlfriendModule;
import dev.vibe.ui.effect.EffectState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Native model parts with their own local skin, independent of ESP, player cosmetics and nametags. */
public final class CompanionRenderer implements AutoCloseable {
    private static final java.nio.FloatBuffer LIGHT_A=vec(.3F,1,.4F,0),LIGHT_B=vec(-.5F,.6F,-.3F,0),DIFFUSE_A=vec(.65F,.65F,.65F,1),DIFFUSE_B=vec(.3F,.3F,.3F,1),AMBIENT=vec(.42F,.42F,.42F,1);
    private final CompanionModel model=new CompanionModel();private final ResourceLocation location=new ResourceLocation("vibe:companion/girlfriend");
    private DynamicTexture texture;private int hair,outfit,accent,eyes;private String style;
    public void render(float partial){
        Minecraft mc=Minecraft.getMinecraft();GirlfriendModule module=Vibe.getInstance().getModuleManager().getModule(GirlfriendModule.class);
        if(module==null||!module.isEnabled()||!module.follower().active||module.companion()==null||mc.getRenderManager()==null)return;
        CompanionFollower f=module.follower();double x=f.previousX+(f.x-f.previousX)*partial,y=f.previousY+(f.y-f.previousY)*partial,z=f.previousZ+(f.z-f.previousZ)*partial;
        float yaw=f.previousYaw+MathHelper.wrapAngleTo180_float(f.yaw-f.previousYaw)*partial,head=f.previousHeadYaw+MathHelper.wrapAngleTo180_float(f.headYaw-f.previousHeadYaw)*partial;
        float pitch=f.previousPitch+(f.pitch-f.previousPitch)*partial;
        render(module,module.companion(),x-mc.getRenderManager().viewerPosX,y-mc.getRenderManager().viewerPosY,z-mc.getRenderManager().viewerPosZ,yaw,head-yaw,pitch,partial);
    }
    public void render(GirlfriendModule settings,EntityOtherPlayerMP npc,double x,double y,double z,float yaw,float head,float pitch,float partial){
        Minecraft mc=Minecraft.getMinecraft();prepare(settings);int mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);GL11.glMatrixMode(GL11.GL_MODELVIEW);
        try(EffectState ignored=new EffectState()){
            GL20.glUseProgram(0);for(int unit=1;unit<=2;unit++){GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glDisable(GL11.GL_TEXTURE_2D);}GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(true);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glEnable(GL11.GL_ALPHA_TEST);GL11.glAlphaFunc(GL11.GL_GREATER,.1F);
            GL11.glPushMatrix();try{
                GL11.glEnable(GL11.GL_LIGHTING);GL11.glEnable(GL11.GL_LIGHT0);GL11.glEnable(GL11.GL_LIGHT1);GL11.glEnable(GL11.GL_COLOR_MATERIAL);GL11.glColorMaterial(GL11.GL_FRONT_AND_BACK,GL11.GL_AMBIENT_AND_DIFFUSE);GL11.glEnable(GL11.GL_NORMALIZE);
                GL11.glLight(GL11.GL_LIGHT0,GL11.GL_POSITION,LIGHT_A);GL11.glLight(GL11.GL_LIGHT0,GL11.GL_DIFFUSE,DIFFUSE_A);GL11.glLight(GL11.GL_LIGHT1,GL11.GL_POSITION,LIGHT_B);GL11.glLight(GL11.GL_LIGHT1,GL11.GL_DIFFUSE,DIFFUSE_B);GL11.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT,AMBIENT);GL11.glColor4f(1,1,1,1);
                GL11.glTranslated(x,y,z);GL11.glRotatef(180-yaw,0,1,0);float scale=settings.modelScale.getFloat();GL11.glScalef(-scale,-scale,scale);GL11.glTranslatef(0,-1.5078125F,0);
                GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture.getGlTextureId());GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL11.GL_TEXTURE_ENV_MODE,GL11.GL_MODULATE);
                model.outfit(settings.outfit.getValue());model.speaking=settings.speaking();model.isSneak=false;model.isRiding=false;model.isChild=false;
                float amount=npc.prevLimbSwingAmount+(npc.limbSwingAmount-npc.prevLimbSwingAmount)*partial;
                model.render(npc,npc.limbSwing-npc.limbSwingAmount*(1-partial),amount,npc.ticksExisted+partial,head,pitch,.0625F);
            }finally{GL11.glPopMatrix();}
        }finally{GL11.glMatrixMode(mode);GlStateManager.resetColor();}
    }
    private void prepare(GirlfriendModule m){
        int h=m.hairColor.getArgb()|0xFF000000,o=m.outfitColor.getArgb()|0xFF000000,a=m.accentColor.getArgb()|0xFF000000,e=m.eyeColor.getArgb()|0xFF000000;
        String selected=m.outfit.getValue();if(texture!=null&&h==hair&&o==outfit&&a==accent&&e==eyes&&selected.equals(style))return;
        int prior=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);if(texture!=null)Minecraft.getMinecraft().getTextureManager().deleteTexture(location);
        texture=new DynamicTexture(CompanionSkin.create(h,o,a,e,selected));Minecraft.getMinecraft().getTextureManager().loadTexture(location,texture);GlStateManager.bindTexture(prior);hair=h;outfit=o;accent=a;eyes=e;style=selected;
    }
    private static java.nio.FloatBuffer vec(float a,float b,float c,float d){java.nio.FloatBuffer v=BufferUtils.createFloatBuffer(4);v.put(new float[]{a,b,c,d}).flip();return v;}
    @Override public void close(){if(texture!=null)Minecraft.getMinecraft().getTextureManager().deleteTexture(location);texture=null;}
}
