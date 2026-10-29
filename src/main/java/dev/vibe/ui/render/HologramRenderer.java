package dev.vibe.ui.render;

import dev.vibe.Vibe;
import dev.vibe.module.impl.visual.HitmarkerModule;
import dev.vibe.ui.effect.*;
import dev.vibe.ui.render.esp.ChamsGlow;
import java.nio.FloatBuffer;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.util.vector.Matrix4f;

/** Real RenderPlayer poses, not approximated skeletons or reanimated copies of a moving target. */
public final class HologramRenderer implements AutoCloseable {
    private static Matrix4f inverseWorld;
    private static final FloatBuffer MATRIX=BufferUtils.createFloatBuffer(16);
    private final Set<RenderPlayer> installed=Collections.newSetFromMap(new IdentityHashMap<RenderPlayer,Boolean>());
    private final ModelPlayer normal=new ModelPlayer(0,false),slim=new ModelPlayer(0,true);
    private final FloatBuffer matrix=BufferUtils.createFloatBuffer(16);
    private EffectProgram material;
    private boolean failed;
    private static HitmarkerModule module(){Vibe vibe=Vibe.getInstance();return vibe==null||vibe.getModuleManager()==null?null:vibe.getModuleManager().getModule(HitmarkerModule.class);}
    public static void beginWorld(){
        inverseWorld=null;HitmarkerModule m=module();if(m==null||!m.isEnabled()||!m.getModes().isSelected("Hologram"))return;
        MATRIX.clear();GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,MATRIX);inverseWorld=Matrix4f.invert((Matrix4f)new Matrix4f().load(MATRIX),null);
    }
    public static void endWorld(){inverseWorld=null;}
    public void installLayers(){
        HitmarkerModule m=module();Minecraft mc=Minecraft.getMinecraft();
        if(m==null||!m.isEnabled()||!m.getModes().isSelected("Hologram")||mc.getRenderManager()==null)return;
        for(Map.Entry<String,RenderPlayer> entry:mc.getRenderManager().getSkinMap().entrySet()){
            final RenderPlayer renderer=entry.getValue();final boolean small=entry.getKey().equals("slim");if(!installed.add(renderer))continue;
            renderer.addLayer(new LayerRenderer<AbstractClientPlayer>(){
                @Override public boolean shouldCombineTextures(){return false;}
                @Override public void doRenderLayer(AbstractClientPlayer player,float swing,float amount,float partial,float age,float yaw,float pitch,float scale){
                    HitmarkerModule module=module();if(inverseWorld==null||module==null||!module.isEnabled()||!module.getModes().isSelected("Hologram")||player.worldObj!=Minecraft.getMinecraft().theWorld)return;
                    ModelPlayer model=renderer.getMainModel();
                    // Custom bodies can bypass ModelPlayer.render. Reuse its native
                    // joint calculation with the same renderer parameters in that case too.
                    model.setRotationAngles(swing,amount,age,yaw,pitch,scale,player);
                    GL11.glPushMatrix();try{
                        if(model.isSneak)GL11.glTranslatef(0,.2F,0);
                        MATRIX.clear();GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,MATRIX);
                        Matrix4f root=Matrix4f.mul(inverseWorld,(Matrix4f)new Matrix4f().load(MATRIX),null);
                        net.minecraft.client.renderer.entity.RenderManager manager=Minecraft.getMinecraft().getRenderManager();
                        module.captureHologram(player,new HologramPose(model,root,player.getLocationSkin(),small,System.currentTimeMillis(),manager.viewerPosX,manager.viewerPosY,manager.viewerPosZ));
                    }finally{GL11.glPopMatrix();}
                }
            });
        }
    }
    public void render(HitmarkerModule module){
        Minecraft mc=Minecraft.getMinecraft();if(mc.theWorld==null||mc.thePlayer==null)return;
        List<HologramPose> poses=module.getHolograms();if(poses.isEmpty())return;
        if(material==null&&!failed)try{material=new EffectProgram("Hologram.vert","Hologram.frag");}catch(Exception error){failed=true;org.apache.logging.log4j.LogManager.getLogger("Vibe").warn("Hologram material unavailable; using flat color",error);}
        int[] textures=skinTextures(poses);
        try(EffectState state=new EffectState()){
            GL11.glEnable(GL11.GL_BLEND);GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD,GL14.GL_FUNC_ADD);GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);GL11.glEnable(GL11.GL_CULL_FACE);GL11.glCullFace(GL11.GL_BACK);
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);GL11.glPolygonOffset(-1,-1);
            if(module.hologramThroughWalls.isEnabled())GL11.glDisable(GL11.GL_DEPTH_TEST);else GL11.glEnable(GL11.GL_DEPTH_TEST);
            for(int unit=1;unit<=2;unit++){GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glDisable(GL11.GL_TEXTURE_2D);}GL13.glActiveTexture(GL13.GL_TEXTURE0);
            for(int index=0;index<poses.size();index++){HologramPose pose=poses.get(index);
                float opacity=module.hologramOpacity(pose);if(opacity<=.001F)continue;
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,textures[index]);
                int tint=module.hologramTint(pose);float r=(tint>>16&255)/255F,g=(tint>>8&255)/255F,b=(tint&255)/255F;
                if(material!=null){
                    material.bind();material.integer("Skin",0);material.vec3("Tint",r,g,b);material.scalar("Opacity",opacity);material.scalar("SkinMix",module.hologramSkin.isEnabled()?module.hologramSkinOpacity.getFloat()/100:0);
                    material.integer("BrightnessOnly",module.hologramBrightness.isEnabled()?1:0);material.integer("MaskPass",0);material.scalar("GlowStrength",module.hologramGlowStrength.getFloat());
                }else{GL20.glUseProgram(0);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glColor4f(r,g,b,opacity);}
                drawPose(pose);
                if(material!=null&&module.hologramGlow.isEnabled())ChamsGlow.capture(()->{
                    material.bind();material.integer("MaskPass",1);
                    if(module.hologramThroughWalls.isEnabled())GL11.glDisable(GL11.GL_DEPTH_TEST);
                    drawPose(pose);material.integer("MaskPass",0);
                });
            }
            ChamsGlow.finish();
        }finally{GlStateManager.resetColor();}
    }
    private static int[] skinTextures(List<HologramPose> poses){
        // Downloaded skins can upload from getGlTextureId(). Do this before raw
        // EffectState rendering, with GlStateManager's active-unit cache aligned.
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GlStateManager.setActiveTexture(GL13.GL_TEXTURE1);GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        int previous=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);int[] result=new int[poses.size()];
        GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
        try{
            Minecraft mc=Minecraft.getMinecraft();Map<ResourceLocation,Integer> prepared=new HashMap<>();
            for(int i=0;i<poses.size();i++){
                ResourceLocation location=poses.get(i).skin;Integer texture=prepared.get(location);
                if(texture==null){ITextureObject skin=mc.getTextureManager().getTexture(location);if(skin==null){mc.getTextureManager().loadTexture(location,new SimpleTexture(location));skin=mc.getTextureManager().getTexture(location);}texture=skin==null?0:skin.getGlTextureId();prepared.put(location,texture);}
                result[i]=texture;
            }
        }finally{GL11.glPopClientAttrib();GlStateManager.bindTexture(previous);GlStateManager.setActiveTexture(active);}
        return result;
    }
    private void drawPose(HologramPose pose){
        Minecraft mc=Minecraft.getMinecraft();ModelPlayer model=pose.slim?slim:normal;pose.apply(model);
        GL11.glPushMatrix();try{
            GL11.glTranslated(pose.originX-mc.getRenderManager().viewerPosX,pose.originY-mc.getRenderManager().viewerPosY,pose.originZ-mc.getRenderManager().viewerPosZ);pose.matrix(matrix);GL11.glMultMatrix(matrix);
            ModelRenderer[] parts=HologramPose.parts(model);
            for(int i=0;i<parts.length;i++){if(material!=null)material.integer("OuterLayer",i>=6?1:0);parts[i].render(1/16F);}
        }finally{GL11.glPopMatrix();}
    }
    @Override public void close(){if(material!=null)material.close();material=null;failed=false;inverseWorld=null;}
}
