package dev.vibe.module.impl.movement;
import dev.vibe.features.*;
import dev.vibe.module.Category;
import dev.vibe.setting.*;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.*;
public final class StepModule extends FeatureModule {
    public final ModeSetting mode=addSetting(new ModeSetting("Mode","Vanilla","Vanilla","Jump"));
    public final NumberSetting height=addSetting(new NumberSetting("Height",1,.6,2.5,.1));
    private EntityPlayerSP owner;private float previous;
    public StepModule(){super("Step","Vanilla: collision stepping; Jump: normal jumps at obstacles",Category.MOVEMENT);}
    private void restore(){if(owner!=null){owner.stepHeight=previous;owner=null;}}
    @Override public void tick(){if(!controlling()||owner!=null&&owner!=mc.thePlayer)restore();}
    @Override public void input(MovementInput input){if(!controlling()||mc.thePlayer.isInWater()||mc.thePlayer.isOnLadder()){restore();return;}
        if(mode.is("Vanilla")){if(owner==null){owner=mc.thePlayer;previous=owner.stepHeight;}owner.stepHeight=height.getFloat();}
        else{restore();if(mc.thePlayer.onGround&&mc.thePlayer.isCollidedHorizontally&&(input.moveForward!=0||input.moveStrafe!=0)){
            double a=Math.toRadians(mc.thePlayer.rotationYaw),dx=-Math.sin(a)*input.moveForward+Math.cos(a)*input.moveStrafe,dz=Math.cos(a)*input.moveForward+Math.sin(a)*input.moveStrafe,length=Math.hypot(dx,dz);
            AxisAlignedBB ahead=mc.thePlayer.getEntityBoundingBox().offset(dx/length*.45,0,dz/length*.45);if(!MovementForecast.loaded(ahead))return;
            double obstacle=0;for(AxisAlignedBB b:mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer,ahead))obstacle=Math.max(obstacle,b.maxY-ahead.minY);
            if(obstacle>.01&&obstacle<=Math.min(1.25,height.getDouble())+.001&&mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer,ahead.offset(0,1.25,0)).isEmpty())input.jump=true;
        }}}
    @Override protected void onDisable(){restore();}
}
