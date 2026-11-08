package dev.vibe.companion;

import net.minecraft.client.model.*;
import net.minecraft.entity.Entity;

/** Adult proportions, a tapered outfit, layered hair and aligned facial overlays. */
public final class CompanionModel extends ModelPlayer {
    private final ModelRenderer hairBack,hairLeft,hairRight,mouth,lidLeft,lidRight;
    private final ModelRenderer casualBody,chicBody,bootLeft,bootRight,choker,pendant;
    public boolean speaking;
    public CompanionModel(){
        super(0,true);
        bipedHead=new ModelRenderer(this,0,0){@Override public void render(float scale){net.minecraft.client.renderer.GlStateManager.pushMatrix();try{net.minecraft.client.renderer.GlStateManager.scale(.9F,.9F,.9F);super.render(scale);}finally{net.minecraft.client.renderer.GlStateManager.popMatrix();}}};
        bipedHead.addBox(-4,-8,-4,8,8,8,0);bipedHead.setRotationPoint(0,0,0);
        bipedHeadwear.showModel=false;
        // An opaque raised scalp sits outside the head; the face cannot intersect its top surface.
        ModelRenderer scalp=new ModelRenderer(this,32,0);scalp.addBox(-4,-8,-4,8,1,8,.08F);bipedHead.addChild(scalp);
        hairBack=new ModelRenderer(this,32,0);hairBack.addBox(-4.1F,-6,2.8F,8,14,2,.05F);bipedHead.addChild(hairBack);
        hairLeft=new ModelRenderer(this,32,0);hairLeft.addBox(-4.2F,-4.8F,-2.3F,1,12,2,.08F);bipedHead.addChild(hairLeft);
        hairRight=new ModelRenderer(this,32,0);hairRight.addBox(3.2F,-3.4F,-2.4F,1,13,2,.08F);bipedHead.addChild(hairRight);
        ModelRenderer clip=new ModelRenderer(this,4,0);clip.addBox(3.9F,-4.8F,-3.1F,1,3,1,0);bipedHead.addChild(clip);
        mouth=new ModelRenderer(this,0,0);mouth.addBox(-1,-1.55F,-4.04F,2,1,0,0);bipedHead.addChild(mouth);
        lidLeft=new ModelRenderer(this,0,4);lidLeft.addBox(-3,-4.5F,-4.06F,2,1,0,0);bipedHead.addChild(lidLeft);
        lidRight=new ModelRenderer(this,0,4);lidRight.addBox(1,-4.5F,-4.06F,2,1,0,0);bipedHead.addChild(lidRight);
        casualBody=bipedBody;chicBody=new ModelRenderer(this);
        ModelRenderer chest=new ModelRenderer(this,16,16);chest.addBox(-4,0,-2,8,5,4);chicBody.addChild(chest);
        ModelRenderer waist=new ModelRenderer(this,17,21);waist.addBox(-3,5,-2,6,4,4);chicBody.addChild(waist);
        ModelRenderer hips=new ModelRenderer(this,16,25);hips.addBox(-4,9,-2,8,3,4);chicBody.addChild(hips);
        ModelRenderer skirtTop=new ModelRenderer(this,16,32);skirtTop.addBox(-4,9,-2.1F,8,2,4,.1F);chicBody.addChild(skirtTop);
        ModelRenderer skirtHem=new ModelRenderer(this,16,36);skirtHem.addBox(-4.5F,11,-2.5F,9,4,5,.03F);chicBody.addChild(skirtHem);
        // A modest fully clothed bust, with a smaller lower tier to soften the block silhouette.
        ModelRenderer bust=new ModelRenderer(this,48,32);bust.addBox(-3.35F,2.1F,-3,3,3,1,.03F);bust.addBox(.35F,2.1F,-3,3,3,1,.03F);
        bust.addBox(-2.85F,4.5F,-2.7F,2,1,1,.02F);bust.addBox(.85F,4.5F,-2.7F,2,1,1,.02F);chicBody.addChild(bust);casualBody.addChild(bust);
        choker=new ModelRenderer(this,48,40);choker.addBox(-2,0,-2.1F,4,1,1,.03F);chicBody.addChild(choker);
        pendant=new ModelRenderer(this,48,46);pendant.addBox(-.5F,.85F,-2.16F,1,1,0,0);chicBody.addChild(pendant);
        bootLeft=new ModelRenderer(this,0,32);bootLeft.addBox(-2,4,-2,4,1,4,.12F);bipedLeftLeg.addChild(bootLeft);
        bootRight=new ModelRenderer(this,0,32);bootRight.addBox(-2,4,-2,4,1,4,.12F);bipedRightLeg.addChild(bootRight);
        outfit("Goth");
    }
    public void outfit(String style){boolean fitted=!"Casual".equals(style);bipedBody=fitted?chicBody:casualBody;bootLeft.showModel=bootRight.showModel=fitted;choker.showModel=pendant.showModel="Goth".equals(style);bipedBodyWear.showModel=bipedRightLegwear.showModel=bipedLeftLegwear.showModel=bipedRightArmwear.showModel=bipedLeftArmwear.showModel=false;}
    @Override public void setRotationAngles(float swing,float amount,float age,float yaw,float pitch,float scale,Entity entity){
        super.setRotationAngles(swing,amount,age,yaw,pitch,scale,entity);
        hairBack.rotateAngleX=.025F+(float)Math.sin(swing*.66F)*amount*.07F;hairLeft.rotateAngleZ=(float)Math.sin(age*.065F)*.015F+amount*.025F;hairRight.rotateAngleZ=-hairLeft.rotateAngleZ;
        mouth.showModel=speaking;mouth.offsetY=speaking?(float)Math.sin(age*1.8F)*.012F:0;
        boolean blink=age%97>93.5F;lidLeft.showModel=lidRight.showModel=blink;
    }
}
