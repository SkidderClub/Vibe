package dev.vibe.module.impl.meme;

import dev.vibe.features.FeatureModule;
import dev.vibe.module.Category;
import dev.vibe.setting.NumberSetting;
import net.minecraft.util.MathHelper;

public final class BobbleheadModule extends FeatureModule {
    public final NumberSetting strength=addSetting(new NumberSetting("Strength",.65,0,2,.05));
    public final NumberSetting damping=addSetting(new NumberSetting("Damping",.75,.45,.95,.01));
    private Object player;private float lastYaw,lastY,roll,pitch,vr,vp;
    public BobbleheadModule(){super("Bobblehead","A spring-driven cosmetic head wobble",Category.MEME);}
    @Override public void tick(){if(!playing()){clear();return;}if(player!=mc.thePlayer){player=mc.thePlayer;lastYaw=mc.thePlayer.rotationYaw;lastY=(float)mc.thePlayer.motionY;return;}
        float turn=MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw-lastYaw);lastYaw=mc.thePlayer.rotationYaw;
        vr+=MathHelper.clamp_float(-turn*.006F,-.2F,.2F);vp+=MathHelper.clamp_float((lastY-(float)mc.thePlayer.motionY)*.18F,-.12F,.12F);lastY=(float)mc.thePlayer.motionY;
        vr=(vr-roll*.22F)*damping.getFloat();vp=(vp-pitch*.22F)*damping.getFloat();roll=MathHelper.clamp_float(roll+vr,-.4F,.4F);pitch=MathHelper.clamp_float(pitch+vp,-.3F,.3F);
    }
    public float roll(){return roll*strength.getFloat();}public float pitch(){return pitch*strength.getFloat();}
    private void clear(){player=null;roll=pitch=vr=vp=0;}@Override protected void onDisable(){clear();}
}
