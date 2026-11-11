package dev.vibe.module.impl.movement;
import dev.vibe.features.*;
import dev.vibe.module.Category;
import dev.vibe.setting.*;
import net.minecraft.util.*;
public final class ReverseStepModule extends FeatureModule {
    public final ModeSetting mode=addSetting(new ModeSetting("Mode","Pull","Instant","Pull"));
    public final BooleanSetting voidCheck=addSetting(new BooleanSetting("Void Check",true));
    public final NumberSetting drop=addSetting(new NumberSetting("Maximum Drop",3,.5,8,.1)),speed=addSetting(new NumberSetting("Pull Speed",.5,.1,2,.05,()->mode.is("Pull")));
    private boolean grounded;private int sinceGround;private Object world;
    public ReverseStepModule(){super("Reverse Step","Descend supported ledges; Instant drops directly, Pull accelerates down",Category.MOVEMENT);}
    @Override public void input(MovementInput input){if(!controlling()||world!=mc.theWorld){world=mc.theWorld;grounded=false;sinceGround=20;return;}
        boolean now=mc.thePlayer.onGround;if(now)sinceGround=0;else sinceGround++;
        if(!now&&sinceGround<=4&&grounded&&!input.jump&&!input.sneak&&!mc.thePlayer.isInWater()&&!mc.thePlayer.isInLava()&&!mc.thePlayer.isOnLadder()&&mc.thePlayer.motionY<=.01){
            AxisAlignedBB box=mc.thePlayer.getEntityBoundingBox();double distance=drop.getDouble();boolean found=false;
            if(!MovementForecast.loaded(box.addCoord(0,-distance,0)))return;
            for(AxisAlignedBB support:mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer,box.addCoord(0,-distance,0))){double d=box.minY-support.maxY;if(d>.02&&d<=distance){distance=d;found=true;}}
            if(found||!voidCheck.isEnabled()){if(mode.is("Instant")){mc.thePlayer.moveEntity(0,-distance,0);}else mc.thePlayer.motionY=Math.min(mc.thePlayer.motionY,-speed.getDouble());}
        }grounded=now||sinceGround<=3;
    }
    @Override protected void onDisable(){world=null;grounded=false;sinceGround=20;}
}
