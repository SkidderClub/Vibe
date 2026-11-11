package dev.vibe.module.impl.meme;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.statistics.StatisticsService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.BlockPos;
import org.lwjgl.input.Keyboard;

/** A local, two-second kill celebration. Does not change player or packet rotations. */
public final class DabModule extends dev.vibe.module.Module implements StatisticsService.ActivityListener {
    private StatisticsService statistics;
    private Object world,player;
    private int age=-1;
    public DabModule(){super("Dab","Celebrate your kills with a third-person dab",Category.MEME,Keyboard.KEY_NONE);}
    @Override protected void onEnable(){subscribe();}
    @Override protected void onDisable(){if(statistics!=null)statistics.removeActivityListener(this);statistics=null;reset();}
    private void subscribe(){StatisticsService next=Vibe.getInstance()==null?null:Vibe.getInstance().getStatistics();if(next==statistics)return;if(statistics!=null)statistics.removeActivityListener(this);statistics=next;if(next!=null)next.addActivityListener(this);}
    @Override public void onActivity(StatisticsService.Activity activity,BlockPos position,EntityLivingBase target){
        if(activity!=StatisticsService.Activity.KILL||target==null||!isEnabled())return;Minecraft mc=Minecraft.getMinecraft();
        if(mc.theWorld==null||mc.thePlayer==null||mc.thePlayer.isDead)return;world=mc.theWorld;player=mc.thePlayer;age=0;
    }
    public void tick(){
        if(!isEnabled()){reset();return;}subscribe();Minecraft mc=Minecraft.getMinecraft();
        if(mc.theWorld!=world||mc.thePlayer!=player||mc.thePlayer==null||mc.thePlayer.isDead){reset();return;}
        if(!mc.isGamePaused()&&age>=0&&++age>=40)age=-1;
    }
    public void reset(){age=-1;world=player=null;}
    public float amount(float partial){return envelope(age<0?-1:age+Math.max(0,Math.min(1,partial)));}
    public static float envelope(float ticks){if(ticks<0||ticks>=40)return 0;return smooth(ticks/7)*(1-smooth((ticks-28)/12));}
    private static float smooth(float value){float t=Math.max(0,Math.min(1,value));return t*t*(3-2*t);}
    /** Called after vanilla pose calculation, for the main model and its armour. */
    public static void poseHook(Object model,Object entity,float ageInTicks){
        if(!(model instanceof ModelBiped))return;Minecraft mc=Minecraft.getMinecraft();Vibe vibe=Vibe.getInstance();
        if(vibe==null||vibe.getModuleManager()==null||mc==null||mc.thePlayer!=entity||mc.theWorld==null)return;
        boolean preview=dev.vibe.features.EmotePoses.preview!=null;
        if(!preview&&(mc.gameSettings.thirdPersonView==0||!dev.vibe.ui.render.esp.ChamsRenderer.worldRendering()))return;
        dev.vibe.features.EmotePoses.apply((ModelBiped)model);if(preview)return;
        DabModule dab=vibe.getModuleManager().getModule(DabModule.class);if(dab==null||!dab.isEnabled()||dab.world!=mc.theWorld||dab.player!=entity)return;
        float amount=dab.amount(ageInTicks-mc.thePlayer.ticksExisted);if(amount>0)applyPose((ModelBiped)model,amount);
    }
    public static void basePoseHook(Object model,Object entity,float ageInTicks){if(!(model instanceof ModelPlayer))poseHook(model,entity,ageInTicks);}
    public static void applyPose(ModelBiped model,float amount){
        // One arm crosses the face; the other reaches diagonally upward.
        model.bipedHead.rotateAngleX=lerp(model.bipedHead.rotateAngleX,.58F,amount);model.bipedHead.rotateAngleY=lerp(model.bipedHead.rotateAngleY,.45F,amount);
        arm(model.bipedRightArm,-.35F,-.65F,-2F,amount);arm(model.bipedLeftArm,-.12F,.1F,-2.25F,amount);model.bipedBody.rotateAngleX+=.08F*amount;
        ModelBase.copyModelAngles(model.bipedHead,model.bipedHeadwear);
        if(model instanceof ModelPlayer){ModelPlayer p=(ModelPlayer)model;ModelBase.copyModelAngles(p.bipedRightArm,p.bipedRightArmwear);ModelBase.copyModelAngles(p.bipedLeftArm,p.bipedLeftArmwear);ModelBase.copyModelAngles(p.bipedBody,p.bipedBodyWear);}
    }
    private static void arm(ModelRenderer arm,float x,float y,float z,float amount){arm.rotateAngleX=lerp(arm.rotateAngleX,x,amount);arm.rotateAngleY=lerp(arm.rotateAngleY,y,amount);arm.rotateAngleZ=lerp(arm.rotateAngleZ,z,amount);}
    private static float lerp(float from,float to,float amount){return from+(to-from)*amount;}
}
