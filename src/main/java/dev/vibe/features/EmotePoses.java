package dev.vibe.features;

import dev.vibe.module.impl.meme.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import java.util.*;

public final class EmotePoses {
    private static final Map<ModelBiped,float[]> shifted=new WeakHashMap<>();
    public static String preview;
    private EmotePoses(){}
    private static ModelRenderer[] bones(ModelBiped b){return new ModelRenderer[]{b.bipedHead,b.bipedHeadwear,b.bipedBody,b.bipedRightArm,b.bipedLeftArm,b.bipedRightLeg,b.bipedLeftLeg};}
    /** Vanilla does not reset every axis (notably head roll). Restore our entire
     * previous pose before it calculates the next one, even after disabling. */
    public static void before(Object model){if(!(model instanceof ModelBiped))return;ModelBiped b=(ModelBiped)model;float[] points=shifted.remove(b);if(points!=null){ModelRenderer[] bones=bones(b);for(int i=0;i<bones.length;i++){ModelRenderer p=bones[i];int j=i*6;p.rotationPointX=points[j];p.rotationPointY=points[j+1];p.rotationPointZ=points[j+2];p.rotateAngleX=points[j+3];p.rotateAngleY=points[j+4];p.rotateAngleZ=points[j+5];}clothes(b);}}
    public static void apply(ModelBiped b){
        EmoteWheelModule e=FeatureModule.module(EmoteWheelModule.class);float sit=preview==null?(e!=null&&e.isEnabled()?e.sit():0):("Sit".equals(preview)?1:0),dab=preview==null?(e!=null&&e.isEnabled()?e.dab():0):("Dab".equals(preview)?1:0);
        ModelRenderer[] bones=bones(b);float[] original=new float[bones.length*6];for(int i=0;i<bones.length;i++){ModelRenderer p=bones[i];int j=i*6;original[j]=p.rotationPointX;original[j+1]=p.rotationPointY;original[j+2]=p.rotationPointZ;original[j+3]=p.rotateAngleX;original[j+4]=p.rotateAngleY;original[j+5]=p.rotateAngleZ;}shifted.put(b,original);
        if(sit>.001F){for(ModelRenderer bone:bones)bone.rotationPointY+=10.5F*sit;
            b.bipedRightLeg.rotateAngleX=blend(b.bipedRightLeg.rotateAngleX,-1.55F,sit);b.bipedLeftLeg.rotateAngleX=blend(b.bipedLeftLeg.rotateAngleX,-1.55F,sit);b.bipedRightLeg.rotateAngleY=-.2F*sit;b.bipedLeftLeg.rotateAngleY=.2F*sit;
            b.bipedRightArm.rotateAngleX=blend(b.bipedRightArm.rotateAngleX,-.55F,sit);b.bipedLeftArm.rotateAngleX=blend(b.bipedLeftArm.rotateAngleX,-.55F,sit);b.bipedBody.rotateAngleX=.05F*sit;
        }
        if(dab>.001F)DabModule.applyPose(b,dab);
        double time=preview==null&&e!=null?e.elapsed():System.nanoTime()/1E9;
        float wave=weight(e,"Wave"),cheer=weight(e,"Cheer"),dance=weight(e,"Dance"),bow=weight(e,"Bow"),palm=weight(e,"Facepalm");
        b.bipedRightArm.rotateAngleX=blend(b.bipedRightArm.rotateAngleX,-2.8F,wave);b.bipedRightArm.rotateAngleZ=blend(b.bipedRightArm.rotateAngleZ,-.4F+(float)Math.sin(time*11)*.35F,wave);
        b.bipedRightArm.rotateAngleX=blend(b.bipedRightArm.rotateAngleX,-2.8F,cheer);b.bipedLeftArm.rotateAngleX=blend(b.bipedLeftArm.rotateAngleX,-2.8F,cheer);b.bipedRightArm.rotateAngleZ-=cheer*.55F;b.bipedLeftArm.rotateAngleZ+=cheer*.55F;
        float sway=(float)Math.sin(time*7);b.bipedBody.rotateAngleZ+=sway*.16F*dance;b.bipedHead.rotateAngleZ-=sway*.12F*dance;b.bipedRightArm.rotateAngleZ-=dance*(.65F+sway*.5F);b.bipedLeftArm.rotateAngleZ+=dance*(.65F-sway*.5F);b.bipedRightLeg.rotateAngleX+=sway*.45F*dance;b.bipedLeftLeg.rotateAngleX-=sway*.45F*dance;
        b.bipedBody.rotateAngleX=blend(b.bipedBody.rotateAngleX,.65F,bow);b.bipedHead.rotateAngleX=blend(b.bipedHead.rotateAngleX,.45F,bow);
        b.bipedHead.rotateAngleX=blend(b.bipedHead.rotateAngleX,.35F,palm);b.bipedRightArm.rotateAngleX=blend(b.bipedRightArm.rotateAngleX,-2.45F,palm);b.bipedRightArm.rotateAngleY=blend(b.bipedRightArm.rotateAngleY,-.5F,palm);b.bipedRightArm.rotateAngleZ=blend(b.bipedRightArm.rotateAngleZ,-.25F,palm);
        BobbleheadModule bobble=FeatureModule.module(BobbleheadModule.class);if(preview==null&&bobble!=null&&bobble.isEnabled()){b.bipedHead.rotateAngleZ+=bobble.roll();b.bipedHead.rotateAngleX+=bobble.pitch();}
        clothes(b);
    }
    private static float blend(float a,float b,float t){return a+(b-a)*t;}
    private static float weight(EmoteWheelModule module,String name){return preview!=null?(preview.equals(name)?1:0):module!=null&&module.isEnabled()?module.weight(name):0;}
    public static void clothes(ModelBiped b){ModelBase.copyModelAngles(b.bipedHead,b.bipedHeadwear);if(b instanceof ModelPlayer){ModelPlayer p=(ModelPlayer)b;ModelBase.copyModelAngles(p.bipedBody,p.bipedBodyWear);ModelBase.copyModelAngles(p.bipedLeftArm,p.bipedLeftArmwear);ModelBase.copyModelAngles(p.bipedRightArm,p.bipedRightArmwear);ModelBase.copyModelAngles(p.bipedLeftLeg,p.bipedLeftLegwear);ModelBase.copyModelAngles(p.bipedRightLeg,p.bipedRightLegwear);}}
}
