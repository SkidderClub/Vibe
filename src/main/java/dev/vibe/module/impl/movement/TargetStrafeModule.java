package dev.vibe.module.impl.movement;

import dev.vibe.features.*;
import dev.vibe.movement.OrbitSteering;
import dev.vibe.module.Category;
import dev.vibe.module.impl.combat.*;
import dev.vibe.setting.*;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.util.*;

/** Own movement only: the combat module retains its yaw, pitch and aim point. */
public final class TargetStrafeModule extends FeatureModule {
    public final ModeSetting targetMode=addSetting(new ModeSetting("Target","Combat Target","Combat Target","Nearest Target"));
    public final NumberSetting radius=addSetting(new NumberSetting("Radius",2.5,1,6,.1)),range=addSetting(new NumberSetting("Target Range",8,3,16,.5));
    public final ModeSetting direction=addSetting(new ModeSetting("Direction","Automatic","Automatic","Left","Right"));
    public final BooleanSetting onlyKillaura=addSetting(new BooleanSetting("Only While Killaura",false)),onlySpeed=addSetting(new BooleanSetting("Only While Speed",false));
    public final BooleanSetting holdJump=addSetting(new BooleanSetting("Only While Jump Key",false)),autoJump=addSetting(new BooleanSetting("Auto Jump",true)),voidCheck=addSetting(new BooleanSetting("Void Check",true)),preview=addSetting(new BooleanSetting("Draw Radius",true));
    public final ColorSetting color=addSetting(new ColorSetting("Radius Color",0xB47ACFFF,preview::isEnabled));
    private EntityLivingBase target;
    private int side=1;
    private int lastManualSide;
    private boolean wallContact;
    private double[] steering;
    public TargetStrafeModule(){super("Targetstrafe","Circle a filtered target using shared MoveFix movement ownership",Category.MOVEMENT);}
    @Override public void input(MovementInput input){MoveFixModule fix=module(MoveFixModule.class);if(fix!=null)fix.clearForcedMovement(getId());EntityLivingBase previous=target;target=null;steering=null;
        ProjectileDodgeModule dodge=module(ProjectileDodgeModule.class);
        if(!isEnabled()||!controlling()||!dependenciesActive()||fix==null||fix.hasForcedMovement()||input.sneak||input.moveForward<0||mc.thePlayer.isInWater()||mc.thePlayer.isInLava()||mc.thePlayer.isRiding()
                ||!MovementForecast.loaded(mc.thePlayer.getEntityBoundingBox().expand(1,1,1))||mc.thePlayer.isOnLadder()||ScaffoldActive()||dev.vibe.module.impl.world.AutoWaterModule.reserving()
                ||dodge!=null&&dodge.dodging()||holdJump.isEnabled()&&!dev.vibe.input.VanillaClicks.physicallyDown(mc.gameSettings.keyBindJump))return;
        EntityLivingBase selected=select();if(selected==null)return;
        // Target Range finds candidates; it must never make the module chase
        // distant targets. A small exit margin prevents flicker at the orbit.
        double activation=radius.getDouble()+(selected==previous?.35:0);
        if(mc.thePlayer.getDistanceSqToEntity(selected)>activation*activation)return;
        if(selected!=previous)wallContact=false;
        target=selected;if(direction.is("Left"))side=1;else if(direction.is("Right"))side=-1;
        int manual=(int)Math.signum(input.moveStrafe);
        if(direction.is("Automatic")&&manual!=0&&manual!=lastManualSide)side=manual;
        lastManualSide=manual;
        int preferred=side;
        boolean contact=mc.thePlayer.isCollidedHorizontally;
        if(direction.is("Automatic")&&contact&&!wallContact)preferred=-side;
        wallContact=contact;
        float yaw=fix.getRotationYaw();MovementInput chosen=plan(input,yaw,preferred);
        if(chosen==null&&direction.is("Automatic")){MovementInput other=plan(input,yaw,-preferred);if(other!=null){preferred=-preferred;chosen=other;}}
        if(chosen==null){steering=new double[]{0,0};fix.setForcedMovement(getId(),0,0,false);input.moveForward=input.moveStrafe=0;return;}
        side=preferred;
        double radians=Math.toRadians(yaw);
        steering=new double[]{-Math.sin(radians)*chosen.moveForward+Math.cos(radians)*chosen.moveStrafe,Math.cos(radians)*chosen.moveForward+Math.sin(radians)*chosen.moveStrafe};
        fix.setForcedMovement(getId(),chosen.moveForward,chosen.moveStrafe,chosen.jump);input.moveForward=chosen.moveForward;input.moveStrafe=chosen.moveStrafe;input.jump=chosen.jump;
    }
    private boolean dependenciesActive(){KillAuraModule aura=KillAuraModule.activeController();SpeedModule speed=module(SpeedModule.class);return (!onlyKillaura.isEnabled()||aura!=null&&aura.isEnabled())&&(!onlySpeed.isEnabled()||speed!=null&&speed.isEnabled());}
    private boolean ScaffoldActive(){return dev.vibe.module.impl.world.ScaffoldModule.isActive();}
    private EntityLivingBase select(){AIAuraModule ai=module(AIAuraModule.class);KillAuraModule aura=module(KillAuraModule.class);EntityLivingBase picked=ai!=null&&ai.isEnabled()?ai.getTarget():null;if(picked==null&&aura!=null&&aura.isEnabled())picked=aura.getTarget();if(valid(picked))return picked;if(!targetMode.is("Nearest Target"))return null;
        double best=range.getDouble()*range.getDouble();for(Entity e:mc.theWorld.loadedEntityList)if(e instanceof EntityLivingBase&&valid((EntityLivingBase)e)){double d=mc.thePlayer.getDistanceSqToEntity(e);if(d<best){best=d;picked=(EntityLivingBase)e;}}return valid(picked)?picked:null;}
    private boolean valid(EntityLivingBase e){TargetsModule filter=module(TargetsModule.class);return e!=null&&e!=mc.thePlayer&&e.worldObj==mc.theWorld&&e.isEntityAlive()&&!e.isDead&&mc.thePlayer.getDistanceSqToEntity(e)<=range.getDouble()*range.getDouble()&&(filter==null||filter.canTarget(e));}
    private MovementInput plan(MovementInput original,float yaw,int sign){double[] vector=OrbitSteering.direction(mc.thePlayer.posX-target.posX,mc.thePlayer.posZ-target.posZ,radius.getDouble(),sign,mc.thePlayer.rotationYaw);float[] keys=OrbitSteering.analogKeys(vector[0],vector[1],yaw);
        MovementInput candidate=MovementForecast.copy(original);candidate.moveForward=keys[0];candidate.moveStrafe=keys[1];
        AxisAlignedBB ahead=mc.thePlayer.getEntityBoundingBox().offset(vector[0]*.6,0,vector[1]*.6);
        candidate.jump|=autoJump.isEnabled()&&mc.thePlayer.onGround&&!mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer,ahead).isEmpty()&&mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer,ahead.offset(0,1.25,0)).isEmpty();
        MovementForecast.Path path=MovementForecast.simulate(candidate,7,yaw);if(!path.loaded||direction.is("Automatic")&&path.collidedHorizontally||path.points.size()<2)return null;
        if(voidCheck.isEnabled()){if(!path.safe)return null;for(AxisAlignedBB box:path.bodies)if(!MovementForecast.supported(box,1.6))return null;}
        Vec3 end=path.points.get(path.points.size()-1),last=end.subtract(path.points.get(0)),velocity=end.subtract(path.points.get(path.points.size()-2));return last.xCoord*vector[0]+last.zCoord*vector[1]>.035&&velocity.xCoord*vector[0]+velocity.zCoord*vector[1]>.001?candidate:null;
    }
    @Override public void tick(){if(!controlling()||!dependenciesActive())release();}
    private void release(){target=null;steering=null;wallContact=false;lastManualSide=0;MoveFixModule fix=module(MoveFixModule.class);if(fix!=null)fix.clearForcedMovement(getId());}
    @Override protected void onDisable(){release();side=1;}
    /** Keep the chosen world vector when Rewound Speed supplies the horizontal magnitude. */
    public static double[] speedDirection(){TargetStrafeModule m=module(TargetStrafeModule.class);if(m==null||!m.isEnabled()||m.steering==null||!m.controlling()||!m.dependenciesActive())return null;MoveFixModule fix=module(MoveFixModule.class);return fix!=null&&fix.ownsForcedMovement(m.getId())?m.steering:null;}
    @Override public void render(float partial){if(target==null||target.worldObj!=mc.theWorld||!preview.isEnabled())return;List<Vec3> ring=new ArrayList<>();double x=target.lastTickPosX+(target.posX-target.lastTickPosX)*partial,y=target.getEntityBoundingBox().minY+.03,z=target.lastTickPosZ+(target.posZ-target.lastTickPosZ)*partial;for(int i=0;i<=48;i++){double angle=i*Math.PI/24;ring.add(new Vec3(x+Math.cos(angle)*radius.getDouble(),y,z+Math.sin(angle)*radius.getDouble()));}WorldDraw.lines(ring,color.getArgb(),1.5F,false);}
}
