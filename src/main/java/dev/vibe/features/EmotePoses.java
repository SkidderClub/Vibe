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
    /** Undo only our positional offset before vanilla calculates the next frame's pose. */
    public static void before(Object model){if(!(model instanceof ModelBiped))return;ModelBiped b=(ModelBiped)model;float[] points=shifted.remove(b);if(points!=null){ModelRenderer[] bones=bones(b);for(int i=0;i<bones.length;i++)bones[i].rotationPointY=points[i];}}
    public static void apply(ModelBiped b){
        EmoteWheelModule e=FeatureModule.module(EmoteWheelModule.class);float sit=preview==null?(e!=null&&e.isEnabled()?e.sit():0):("Sit".equals(preview)?1:0),dab=preview==null?(e!=null&&e.isEnabled()?e.dab():0):("Dab".equals(preview)?1:0);
        if(sit>.001F){ModelRenderer[] bones=bones(b);float[] ys=new float[bones.length];for(int i=0;i<bones.length;i++){ys[i]=bones[i].rotationPointY;bones[i].rotationPointY+=10.5F*sit;}shifted.put(b,ys);
            b.bipedRightLeg.rotateAngleX=blend(b.bipedRightLeg.rotateAngleX,-1.55F,sit);b.bipedLeftLeg.rotateAngleX=blend(b.bipedLeftLeg.rotateAngleX,-1.55F,sit);b.bipedRightLeg.rotateAngleY=-.2F*sit;b.bipedLeftLeg.rotateAngleY=.2F*sit;
            b.bipedRightArm.rotateAngleX=blend(b.bipedRightArm.rotateAngleX,-.55F,sit);b.bipedLeftArm.rotateAngleX=blend(b.bipedLeftArm.rotateAngleX,-.55F,sit);b.bipedBody.rotateAngleX=.05F*sit;
        }
        if(dab>.001F)DabModule.applyPose(b,dab);
        BobbleheadModule bobble=FeatureModule.module(BobbleheadModule.class);if(preview==null&&bobble!=null&&bobble.isEnabled()){b.bipedHead.rotateAngleZ+=bobble.roll();b.bipedHead.rotateAngleX+=bobble.pitch();}
        clothes(b);
    }
    private static float blend(float a,float b,float t){return a+(b-a)*t;}
    public static void clothes(ModelBiped b){ModelBase.copyModelAngles(b.bipedHead,b.bipedHeadwear);if(b instanceof ModelPlayer){ModelPlayer p=(ModelPlayer)b;ModelBase.copyModelAngles(p.bipedBody,p.bipedBodyWear);ModelBase.copyModelAngles(p.bipedLeftArm,p.bipedLeftArmwear);ModelBase.copyModelAngles(p.bipedRightArm,p.bipedRightArmwear);ModelBase.copyModelAngles(p.bipedLeftLeg,p.bipedLeftLegwear);ModelBase.copyModelAngles(p.bipedRightLeg,p.bipedRightLegwear);}}
}
