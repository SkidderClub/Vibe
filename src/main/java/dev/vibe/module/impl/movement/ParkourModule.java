package dev.vibe.module.impl.movement;
import dev.vibe.features.*;
import dev.vibe.module.Category;
import dev.vibe.setting.*;
import net.minecraft.util.*;
public final class ParkourModule extends FeatureModule {
    public final NumberSetting distance=addSetting(new NumberSetting("Edge Distance",0,0,.8,.01));
    public ParkourModule(){super("Parkour","Jump at unsupported edges using Eagle's future-footprint collision check",Category.MOVEMENT);}
    @Override public void input(MovementInput input){if(!controlling()||!mc.thePlayer.onGround||input.sneak||mc.thePlayer.isInWater()||mc.thePlayer.isOnLadder()||input.moveForward==0&&input.moveStrafe==0)return;
        MovementInput corrected=MovementForecast.copy(input);MoveFixModule fix=module(MoveFixModule.class);if(fix!=null)fix.correctInjectedInput(corrected);
        double yaw=Math.toRadians(MoveFixModule.movementYawHook(mc.thePlayer,mc.thePlayer.rotationYaw)),dx=-Math.sin(yaw)*corrected.moveForward+Math.cos(yaw)*corrected.moveStrafe,dz=Math.cos(yaw)*corrected.moveForward+Math.sin(yaw)*corrected.moveStrafe,length=Math.hypot(dx,dz);
        double probe=Math.max(.12,Math.hypot(mc.thePlayer.motionX,mc.thePlayer.motionZ))+distance.getDouble();AxisAlignedBB box=mc.thePlayer.getEntityBoundingBox();
        // Only test the surface at the player's actual feet. A full player box shifted
        // down one block sees the floor under beds/chests and incorrectly reports support.
        AxisAlignedBB feet=new AxisAlignedBB(box.minX+.2,box.minY-.08,box.minZ+.2,box.maxX-.2,box.minY+.001,box.maxZ-.2).offset(dx/length*probe,0,dz/length*probe);
        AxisAlignedBB headroom=new AxisAlignedBB(box.minX,box.maxY,box.minZ,box.maxX,box.maxY+.5,box.maxZ);
        if(MovementForecast.loaded(feet)&&mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer,feet).isEmpty()&&mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer,headroom).isEmpty())input.jump=true;
    }
}
