package dev.vibe.module.impl.combat;
import dev.vibe.features.*;
import dev.vibe.inventory.*;
import dev.vibe.combat.*;
import dev.vibe.module.Category;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.setting.*;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
public final class PearlCatcherModule extends FeatureModule {
    public final NumberSetting delay=addSetting(new NumberSetting("Delay (ms)",150,0,2000,10)),rotationSpeed=addSetting(new NumberSetting("Rotation Speed",40,1,180,1)),range=addSetting(new NumberSetting("Range",64,8,96,1));
    public final ItemSwitching switching=new ItemSwitching(this::addSetting,"Silent");
    public final BooleanSetting preview=addSetting(new BooleanSetting("Landing Preview",true));
    private final Set<Integer> handled=new HashSet<>();private Object world;private EntityEnderPearl target;private Entity thrower;private long detected;private Vec3 landing;private ProjectileAimSolver.Shot shot;private int slot=-1;
    public PearlCatcherModule(){super("Pearl Catcher","Predict another player's pearl landing and send your own pearl there",Category.COMBAT);}
    private boolean allowed(){return controlling()&&!mc.thePlayer.isUsingItem()&&!mc.playerController.getIsHittingBlock()&&!AutoBuffModule.ownsUseInput()&&!AntiFireballModule.reserving()&&!BowAimbotModule.drawing()&&!dev.vibe.module.impl.world.ScaffoldModule.isActive()&&!dev.vibe.inventory.PlayerInventorySession.isBusy();}
    @Override public void tick(){shot=null;slot=-1;if(world!=mc.theWorld){handled.clear();target=null;world=mc.theWorld;}if(!allowed()){clearRotation();return;}
        if(handled.size()>512)handled.clear();if(target!=null&&(target.worldObj!=mc.theWorld||(System.nanoTime()/1000000)-detected>5000||mc.thePlayer.getDistanceSqToEntity(target)>range.getDouble()*range.getDouble()))target=null;
        if(target==null)for(Entity entity:mc.theWorld.loadedEntityList){if(!(entity instanceof EntityEnderPearl)||entity.isDead||entity.ticksExisted>6||handled.contains(entity.getEntityId())||mc.thePlayer.getDistanceSqToEntity(entity)>range.getDouble()*range.getDouble())continue;
            Entity owner=ProjectileForecast.owner(entity);if(owner==null){double best=9;for(EntityPlayer p:mc.theWorld.playerEntities){double d=p.getDistanceSqToEntity(entity);if(d<best){best=d;owner=p;}}}
            handled.add(entity.getEntityId());if(owner==null||owner==mc.thePlayer)continue;target=(EntityEnderPearl)entity;thrower=owner;detected=(System.nanoTime()/1000000);landing=null;break;}
        if(target==null){landing=null;clearRotation();return;}if(!target.isDead){ProjectileForecast.Flight flight=ProjectileForecast.trace(mc.theWorld,target,100,thrower);if(!flight.loaded||flight.impact==null){clearRotation();return;}landing=flight.impact.hitVec;}if(landing==null){target=null;clearRotation();return;}
        if((System.nanoTime()/1000000)-detected<delay.getInt())return;for(int i=0;i<9;i++){ItemStack stack=mc.thePlayer.inventory.mainInventory[i];if(stack!=null&&stack.stackSize>0&&stack.getItem()==Items.ender_pearl){slot=i;break;}}
        if(slot<0)return;Vec3[] still=new Vec3[ProjectileAimSolver.HORIZON+1];Arrays.fill(still,new Vec3(0,0,0));shot=ProjectileAimSolver.solve(mc.thePlayer.getPositionEyes(1),landing,still,ProjectileAimSolver.Kind.THROWABLE);
    }
    @Override public void applyRotation(){MoveFixModule fix=module(MoveFixModule.class);if(shot==null||fix==null||!allowed()||fix.hasOtherRotationOwner(getId()))return;float speed=rotationSpeed.getFloat();fix.setFakeRotation(getId(),fix.getRotationYaw()+RotationMath.clamp(RotationMath.difference(shot.yaw,fix.getRotationYaw()),-speed,speed),fix.getRotationPitch()+RotationMath.clamp(shot.pitch-fix.getRotationPitch(),-speed,speed));}
    @Override public void afterWalking(){MoveFixModule fix=module(MoveFixModule.class);if(shot==null||fix==null||!fix.ownsRotation(getId())||!allowed()||Math.abs(RotationMath.difference(shot.yaw,fix.getRotationYaw()))>1.5||Math.abs(shot.pitch-fix.getRotationPitch())>1.5)return;
        ProjectileForecast.Flight own=ProjectileForecast.trace(mc.theWorld,ProjectileAimSolver.origin(mc.thePlayer.getPositionEyes(1),fix.getRotationYaw()),ProjectileAimSolver.velocity(fix.getRotationYaw(),fix.getRotationPitch()),false,.125,null,80,mc.thePlayer);
        if(!own.loaded||own.impact==null||own.impact.hitVec.squareDistanceTo(landing)>2.25)return;
        HotbarSwitches.Lease lease=HotbarSwitches.acquire(this,switching,slot,HotbarSwitches.PROJECTILE+5);if(lease==null)return;try{if(lease.active())mc.playerController.sendUseItem(mc.thePlayer,mc.theWorld,HotbarSwitches.stack());else return;}finally{lease.close();}
        target=null;shot=null;landing=null;clearRotation();
    }
    private void clearRotation(){MoveFixModule fix=module(MoveFixModule.class);if(fix!=null)fix.clearFakeRotation(getId());}
    @Override protected void onDisable(){handled.clear();target=null;shot=null;landing=null;clearRotation();HotbarSwitches.releaseOwner(this);}
    @Override public void render(float partial){if(preview.isEnabled()&&landing!=null)WorldDraw.box(new AxisAlignedBB(landing.xCoord-.3,landing.yCoord+.02,landing.zCoord-.3,landing.xCoord+.3,landing.yCoord+.06,landing.zCoord+.3),0xDD7DDFFF,1.5F,true);}
    public static boolean reserving(){PearlCatcherModule m=module(PearlCatcherModule.class);return m!=null&&m.isEnabled()&&m.shot!=null;}
}
