package dev.vibe.module.impl.movement;
import dev.vibe.features.*;
import dev.vibe.module.Category;
import dev.vibe.setting.*;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.*;
public final class StepModule extends FeatureModule {
    public final ModeSetting mode=addSetting(new ModeSetting("Mode","Vanilla","Vanilla","Jump","Rewound"));
    public final NumberSetting height=addSetting(new NumberSetting("Height",1,.6,2.5,.1,()->!mode.is("Rewound")));
    public final BooleanSetting timer=addSetting(new BooleanSetting("Timer",false,()->mode.is("Rewound")));
    public final NumberSetting delay=addSetting(new NumberSetting("Delay",150,0,500,50,()->mode.is("Rewound")));
    public final BooleanSetting speedCheck=addSetting(new BooleanSetting("Speed check",false,()->mode.is("Rewound")));
    private final dev.vibe.movement.RewoundStep rewound=new dev.vibe.movement.RewoundStep();
    private EntityPlayerSP rewoundOwner;
    private net.minecraft.world.World rewoundWorld;
    private EntityPlayerSP owner;private float previous;
    public StepModule(){super("Step","Controls collision stepping, normal jumps and Rewound stepping",Category.MOVEMENT);}
    private void restore(){if(owner!=null){owner.stepHeight=previous;owner=null;}}
    @Override public void tick(){maintainRewound();if(!controlling()||owner!=null&&owner!=mc.thePlayer||mode.is("Rewound"))restore();}
    @Override public void input(MovementInput input){if(!controlling()||mc.thePlayer.isInWater()||mc.thePlayer.isOnLadder()){restore();return;}
        if(mode.is("Rewound")){restore();return;}
        if(mode.is("Vanilla")){if(owner==null){owner=mc.thePlayer;previous=owner.stepHeight;}owner.stepHeight=height.getFloat();}
        else{restore();if(mc.thePlayer.onGround&&mc.thePlayer.isCollidedHorizontally&&(input.moveForward!=0||input.moveStrafe!=0)){
            double a=Math.toRadians(mc.thePlayer.rotationYaw),dx=-Math.sin(a)*input.moveForward+Math.cos(a)*input.moveStrafe,dz=Math.cos(a)*input.moveForward+Math.sin(a)*input.moveStrafe,length=Math.hypot(dx,dz);
            AxisAlignedBB ahead=mc.thePlayer.getEntityBoundingBox().offset(dx/length*.45,0,dz/length*.45);if(!MovementForecast.loaded(ahead))return;
            double obstacle=0;for(AxisAlignedBB b:mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer,ahead))obstacle=Math.max(obstacle,b.maxY-ahead.minY);
            if(obstacle>.01&&obstacle<=Math.min(1.25,height.getDouble())+.001&&mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer,ahead.offset(0,1.25,0)).isEmpty())input.jump=true;
        }}}
    private boolean maintainRewound(){boolean active=isEnabled()&&mode.is("Rewound")&&playing()&&mc.thePlayer.worldObj==mc.theWorld&&!mc.thePlayer.isSpectator()&&!mc.thePlayer.isRiding();EntityPlayerSP next=active?mc.thePlayer:null;
        if(rewoundOwner!=next||rewoundWorld!=mc.theWorld){rewound.reset(next);rewoundOwner=next;rewoundWorld=mc.theWorld;}return active;}
    public void rewoundPreUpdate(Object entity){if(entity==mc.thePlayer&&maintainRewound())rewound.preUpdate(delay.getInt(),speedCheck.isEnabled());}
    public float rewoundPreStep(Object entity,float vanilla){return entity==mc.thePlayer&&maintainRewound()?rewound.preStep(delay.getInt(),speedCheck.isEnabled()):vanilla;}
    public float rewoundStepHeight(Object entity,float vanilla){return entity==mc.thePlayer&&maintainRewound()?rewound.height():vanilla;}
    public void rewoundPostStep(Object entity){if(entity==mc.thePlayer&&maintainRewound())rewound.postStep(timer.isEnabled());}
    @Override protected void onDisable(){restore();rewound.reset(null);rewoundOwner=null;}
}
