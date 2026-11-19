package dev.vibe.module.impl.movement;

import dev.vibe.features.FeatureModule;
import dev.vibe.module.Category;
import dev.vibe.setting.ModeSetting;
import net.minecraft.potion.Potion;

/** Exire WatchdogJesus at PlayerMoveEvent, exposed as Rewound. */
public final class JesusModule extends FeatureModule {
    public final ModeSetting mode=addSetting(new ModeSetting("Mode","Rewound","Rewound","RewoundNovoline","RewoundAdjust"));
    public JesusModule(){super("Jesus","Maintain the Rewound water movement vector",Category.MOVEMENT);}
    @Override protected void onEnable(){dev.vibe.movement.AdjustJesus.reset();}
    @Override protected void onDisable(){dev.vibe.movement.AdjustJesus.reset();}
    public void novolinePreUpdate(Object entity){
        if(!isEnabled()||!mode.is("RewoundNovoline")||entity!=mc.thePlayer||!playing()||mc.thePlayer.isSpectator()||mc.thePlayer.isRiding()
                ||mc.thePlayer.movementInput.sneak||!(mc.thePlayer.isInWater()||mc.thePlayer.isInLava()))return;
        net.minecraft.block.state.IBlockState state=mc.theWorld.getBlockState(new net.minecraft.util.BlockPos(mc.thePlayer));
        if(!(state.getBlock() instanceof net.minecraft.block.BlockLiquid))return;
        SpeedModule speed=module(SpeedModule.class);
        if(state.getValue(net.minecraft.block.BlockLiquid.LEVEL)<(speed!=null&&speed.isEnabled()?2:4))mc.thePlayer.motionY=.12;
    }
    public double[] move(Object entity,double x,double y,double z){
        if(!mode.is("Rewound")||!isEnabled()||entity!=mc.thePlayer||!playing()||mc.thePlayer.isSpectator()||mc.thePlayer.isRiding()
                ||!mc.thePlayer.isInWater()||mc.thePlayer.onGround||mc.gameSettings.keyBindJump.isKeyDown()||mc.gameSettings.keyBindSneak.isKeyDown())return null;
        double speed=.12;
        if(mc.thePlayer.isPotionActive(Potion.moveSpeed))speed*=1+.2*(mc.thePlayer.getActivePotionEffect(Potion.moveSpeed).getAmplifier()+1);
        if(mc.thePlayer.isPotionActive(Potion.moveSlowdown))speed/=1+.2*(mc.thePlayer.getActivePotionEffect(Potion.moveSlowdown).getAmplifier()+1);
        double forward=mc.thePlayer.movementInput.moveForward,strafe=mc.thePlayer.moveStrafing;float yaw=mc.thePlayer.rotationYaw;
        double[] orbit=TargetStrafeModule.speedDirection();
        if(orbit!=null){x=orbit[0]*speed;z=orbit[1]*speed;}
        else{
            if(forward!=0){if(strafe>0)yaw+=forward>0?-45:45;else if(strafe<0)yaw+=forward>0?45:-45;strafe=0;forward=Math.signum(forward);}
            strafe=Math.signum(strafe);double c=Math.cos(Math.toRadians(yaw+90)),s=Math.sin(Math.toRadians(yaw+90));
            x=forward*speed*c+strafe*speed*s;z=forward*speed*s-strafe*speed*c;
        }
        mc.thePlayer.motionY=(double)1E-5F;
        return new double[]{x,(double)1E-5F,z,mc.thePlayer.getEntityBoundingBox().minY};
    }
}
