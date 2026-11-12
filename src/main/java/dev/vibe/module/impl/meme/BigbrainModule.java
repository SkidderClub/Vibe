package dev.vibe.module.impl.meme;

import dev.vibe.features.FeatureModule;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;

/** Render-only proportions; vanilla poses (including Bobblehead) remain intact. */
public final class BigbrainModule extends Module {
    public static final float HEAD_SCALE=1.7F, BODY_SCALE=.6F;

    public BigbrainModule(){super("Bigbrain","Give every player a big head and a tiny body",Category.MEME,0);}

    private static boolean active(){BigbrainModule module=FeatureModule.module(BigbrainModule.class);return module!=null&&module.isEnabled();}

    /** Shared by the skin, its outer layers and both native armour models. */
    public static void renderPart(Object renderer,float scale,Object model,Object entity){
        ModelRenderer part=(ModelRenderer)renderer;
        if(!(entity instanceof EntityPlayer)||!(model instanceof ModelBiped)||!active()){part.render(scale);return;}
        ModelBiped biped=(ModelBiped)model;
        renderScaled(part,scale,part==biped.bipedHead||part==biped.bipedHeadwear);
    }

    /** The player-only ear model does not receive an entity parameter. */
    public static void renderAttachment(Object renderer,float scale,boolean head){
        ModelRenderer part=(ModelRenderer)renderer;if(active())renderScaled(part,scale,head);else part.render(scale);
    }

    private static void renderScaled(ModelRenderer part,float scale,boolean head){
        GlStateManager.pushMatrix();
        try{transform(part,scale,head);part.render(scale);}finally{GlStateManager.popMatrix();}
    }

    /** Keep feet grounded and attach the enlarged head to the shrunken neck,
     * including vanilla sneaking pivots, without changing any model fields. */
    public static void transform(ModelRenderer part,float scale,boolean head){
        float size=head?HEAD_SCALE:BODY_SCALE,delta=BODY_SCALE-size;
        GlStateManager.translate(part.rotationPointX*delta*scale,
                (24*(1-BODY_SCALE)+part.rotationPointY*delta)*scale,part.rotationPointZ*delta*scale);
        GlStateManager.scale(size,size,size);
    }

    /** The caller owns this matrix: native held items must follow the small arm. */
    public static void postRenderArm(Object model,float scale,Object entity){
        ModelBiped biped=(ModelBiped)model;
        if(entity instanceof EntityPlayer&&active())transform(biped.bipedRightArm,scale,false);
        biped.postRenderArm(scale);
    }

    /** Pumpkins and skulls follow the same enlarged head as helmets and hats. */
    public static void postRenderHead(Object renderer,float scale,Object entity){
        ModelRenderer part=(ModelRenderer)renderer;
        if(entity instanceof EntityPlayer&&active())transform(part,scale,true);
        part.postRender(scale);
    }

    /** Apply proportions before cape wind rotations so its shoulder anchor stays attached. */
    public static void beginCape(Object entity){
        if(entity instanceof EntityPlayer&&active()){
            GlStateManager.translate(0,24*(1-BODY_SCALE)/16,0);GlStateManager.scale(BODY_SCALE,BODY_SCALE,BODY_SCALE);
        }
    }
}
