package dev.vibe.ui.render;

import java.nio.FloatBuffer;
import net.minecraft.client.model.*;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Matrix4f;

/** Immutable native joints and world-space model transform, frozen independently of the live entity. */
public final class HologramPose {
    public final ResourceLocation skin;
    public final boolean slim;
    public final long created;
    public final double originX,originY,originZ;
    private final float[] root=new float[16];
    private final Joint[] joints;
    public HologramPose(ModelPlayer model,Matrix4f world,ResourceLocation skin,boolean slim,long now){
        this(model,world,skin,slim,now,0,0,0);
    }
    public HologramPose(ModelPlayer model,Matrix4f world,ResourceLocation skin,boolean slim,long now,double x,double y,double z){
        this.skin=skin;this.slim=slim;created=now;originX=x;originY=y;originZ=z;world.store(FloatBuffer.wrap(root));
        ModelRenderer[] parts=parts(model);joints=new Joint[parts.length];for(int i=0;i<parts.length;i++)joints[i]=new Joint(parts[i]);
    }
    private HologramPose(HologramPose pose,long now){skin=pose.skin;slim=pose.slim;created=now;originX=pose.originX;originY=pose.originY;originZ=pose.originZ;System.arraycopy(pose.root,0,root,0,16);joints=pose.joints;}
    public HologramPose at(long now){return new HologramPose(this,now);}
    /** Off-screen deaths still use the player's actual skin and native limb calculation. */
    public static HologramPose playerPose(net.minecraft.entity.player.EntityPlayer player,long now){
        net.minecraft.client.entity.AbstractClientPlayer client=player instanceof net.minecraft.client.entity.AbstractClientPlayer?(net.minecraft.client.entity.AbstractClientPlayer)player:null;
        boolean slim=client!=null&&"slim".equals(client.getSkinType());ModelPlayer model=new ModelPlayer(0,slim);model.isSneak=player.isSneaking();model.isRiding=player.isRiding();
        model.setRotationAngles(player.limbSwing,player.limbSwingAmount,player.ticksExisted,net.minecraft.util.MathHelper.wrapAngleTo180_float(player.rotationYawHead-player.renderYawOffset),player.rotationPitch,.0625F,player);
        Matrix4f root=new Matrix4f();root.translate(new org.lwjgl.util.vector.Vector3f(0,1.5078125F,0));root.rotate((float)Math.toRadians(180-player.renderYawOffset),new org.lwjgl.util.vector.Vector3f(0,1,0));root.scale(new org.lwjgl.util.vector.Vector3f(-1,-1,1));
        return new HologramPose(model,root,client==null?net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin(player.getUniqueID()):client.getLocationSkin(),slim,now,player.posX,player.posY,player.posZ);
    }
    public void matrix(FloatBuffer buffer){buffer.clear();buffer.put(root).flip();}
    public void apply(ModelPlayer model){ModelRenderer[] parts=parts(model);for(int i=0;i<parts.length;i++)joints[i].apply(parts[i]);}
    public static ModelRenderer[] parts(ModelPlayer m){return new ModelRenderer[]{m.bipedHead,m.bipedBody,m.bipedRightArm,m.bipedLeftArm,m.bipedRightLeg,m.bipedLeftLeg,m.bipedHeadwear,m.bipedBodyWear,m.bipedRightArmwear,m.bipedLeftArmwear,m.bipedRightLegwear,m.bipedLeftLegwear};}
    private static final class Joint {
        final float x,y,z,px,py,pz,ox,oy,oz;final boolean show,hidden;
        Joint(ModelRenderer p){x=p.rotateAngleX;y=p.rotateAngleY;z=p.rotateAngleZ;px=p.rotationPointX;py=p.rotationPointY;pz=p.rotationPointZ;ox=p.offsetX;oy=p.offsetY;oz=p.offsetZ;show=p.showModel;hidden=p.isHidden;}
        void apply(ModelRenderer p){p.rotateAngleX=x;p.rotateAngleY=y;p.rotateAngleZ=z;p.rotationPointX=px;p.rotationPointY=py;p.rotationPointZ=pz;p.offsetX=ox;p.offsetY=oy;p.offsetZ=oz;p.showModel=show;p.isHidden=hidden;}
    }
}
