package dev.vibe.module.impl.movement;
import dev.vibe.features.*;
import dev.vibe.module.Category;
import dev.vibe.setting.*;
import net.minecraft.util.*;
public final class ParkourModule extends FeatureModule {
    public final NumberSetting distance=addSetting(new NumberSetting("Edge Distance",0,0,.8,.01));
    public ParkourModule(){super("Parkour","Jump at unsupported edges using Eagle's future-footprint collision check",Category.MOVEMENT);}
    @Override public void input(MovementInput input){if(!controlling()||!mc.thePlayer.onGround||input.sneak||mc.thePlayer.isInWater()||mc.thePlayer.isOnLadder()||input.moveForward==0&&input.moveStrafe==0)return;
        double yaw=Math.toRadians(mc.thePlayer.rotationYaw),dx=-Math.sin(yaw)*input.moveForward+Math.cos(yaw)*input.moveStrafe,dz=Math.cos(yaw)*input.moveForward+Math.sin(yaw)*input.moveStrafe,length=Math.hypot(dx,dz);
        double probe=Math.max(.12,Math.hypot(mc.thePlayer.motionX,mc.thePlayer.motionZ))+distance.getDouble();AxisAlignedBB box=mc.thePlayer.getEntityBoundingBox();
        AxisAlignedBB feet=box.expand(-.2,0,-.2).offset(dx/length*probe,-1,dz/length*probe);
        if(MovementForecast.loaded(feet)&&mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer,feet).isEmpty()&&mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer,box.offset(0,.5,0)).isEmpty())input.jump=true;
    }
}
