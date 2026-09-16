package dev.vibe.ui;

import dev.vibe.module.impl.EspModule;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.*;
import net.minecraft.entity.EntityLivingBase;

/** Vanilla player, skin parts, pose and equipment layers with editor-only material context. */
final class EspPreviewRenderer extends RenderPlayer {
    EspModule esp;
    int profile,team;
    boolean occluded,hurt;
    EspPreviewRenderer(RenderManager manager,boolean slim){
        super(manager,slim);
        layerRenderers.removeIf(layer->layer instanceof LayerArmorBase);
        LayerBipedArmor armor=new LayerBipedArmor(this) {
            @Override public void doRenderLayer(EntityLivingBase entity,float swing,float amount,float partial,float age,float yaw,float pitch,float scale){
                Runnable draw=()->super.doRenderLayer(entity,swing,amount,partial,age,yaw,pitch,scale);
                if(esp.getModes().isSelected("Chams"))ChamsRenderer.preview(esp,profile,occluded,team,hurt,true,draw);else draw.run();
            }
        };
        addLayer(armor);
        addLayer(new LayerRenderer<AbstractClientPlayer>() {
            @Override public void doRenderLayer(AbstractClientPlayer player,float swing,float amount,float partial,float age,float yaw,float pitch,float scale){
                if(esp.getModes().isSelected("Skeletal"))SkeletalRenderer.preview(getMainModel(),esp.getSkeletal(esp.resolvedProfile(profile)),team,hurt);
            }
            @Override public boolean shouldCombineTextures(){return false;}
        });
    }
    @Override protected boolean canRenderName(AbstractClientPlayer player){return false;}
    @Override protected void renderModel(AbstractClientPlayer player,float swing,float amount,float age,float yaw,float pitch,float scale){
        if(!esp.getModes().isSelected("Chams")){super.renderModel(player,swing,amount,age,yaw,pitch,scale);return;}
        bindEntityTexture(player);
        ChamsRenderer.preview(esp,profile,occluded,team,hurt,false,()->getMainModel().render(player,swing,amount,age,yaw,pitch,scale));
    }
}
