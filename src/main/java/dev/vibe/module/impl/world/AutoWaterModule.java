package dev.vibe.module.impl.world;

import dev.vibe.features.*;
import dev.vibe.inventory.*;
import dev.vibe.combat.RotationMath;
import dev.vibe.module.Category;
import dev.vibe.module.impl.combat.*;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.setting.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;

/** Extinguish with a real hotbar bucket and vanilla use-item logic after walking. */
public final class AutoWaterModule extends FeatureModule {
    public final ItemSwitching switching=new ItemSwitching(this::addSetting,"Silent");
    public final NumberSetting rotationSpeed=addSetting(new NumberSetting("Rotation Speed",90,5,180,1));
    public final NumberSetting delay=addSetting(new NumberSetting("Delay (ms)",500,150,2000,25));
    private MovingObjectPosition surface;
    private Object world;
    private int slot=-1;
    private long next;
    private String status="Idle";
    public AutoWaterModule(){super("Auto Water","Place water below you to extinguish fire",Category.WORLD);}
    private boolean allowed(){return controlling()&&mc.playerController!=null&&mc.theWorld.provider!=null&&!mc.theWorld.provider.doesWaterVaporize()
            &&MovementForecast.loaded(mc.thePlayer.getEntityBoundingBox().expand(.01,5,.01))&&!mc.thePlayer.isInWater()&&!mc.thePlayer.isUsingItem()&&!mc.playerController.getIsHittingBlock()
            &&!PlayerInventorySession.isBusy()&&!HotbarSwitches.busy(this,HotbarSwitches.BUFF)&&!ScaffoldModule.isActive()
            &&!AutoBuffModule.ownsUseInput()&&!AntiFireballModule.reserving()&&!PearlCatcherModule.reserving()&&!ProjectileAuraModule.reserving()&&!BowAimbotModule.drawing();}
    @Override public void tick(){surface=null;slot=-1;if(world!=mc.theWorld){release();world=mc.theWorld;next=0;}
        if(!allowed()){status="Paused";release();return;}if(!mc.thePlayer.isBurning()){status="Not burning";release();return;}
        if(System.nanoTime()/1000000<next){status="Waiting";release();return;}
        for(int i=0;i<9;i++){ItemStack stack=mc.thePlayer.inventory.mainInventory[i];if(stack!=null&&stack.stackSize>0&&stack.getItem()==Items.water_bucket){slot=i;break;}}
        if(slot<0){status="No water bucket in hotbar";release();return;}
        Vec3 eye=mc.thePlayer.getPositionEyes(1);double reach=Math.min(5,mc.playerController.getBlockReachDistance());
        surface=mc.theWorld.rayTraceBlocks(eye,eye.addVector(0,-reach,0),false,true,false);
        if(!valid(surface)){surface=null;status="No reachable surface below";release();return;}status="Extinguishing";
    }
    private boolean valid(MovingObjectPosition hit){if(hit==null||hit.typeOfHit!=MovingObjectPosition.MovingObjectType.BLOCK||hit.sideHit!=EnumFacing.UP||!mc.theWorld.isBlockLoaded(hit.getBlockPos().up()))return false;
        BlockPos dest=hit.getBlockPos().up();net.minecraft.block.material.Material material=mc.theWorld.getBlockState(dest).getBlock().getMaterial();
        // Match bucket placement rather than ItemBlock replaceability. Fire and
        // lava are legal bucket destinations; existing water needs no new bucket.
        return dest.getX()==MathHelper.floor_double(mc.thePlayer.posX)&&dest.getZ()==MathHelper.floor_double(mc.thePlayer.posZ)
                &&dest.getY()<=mc.thePlayer.getEntityBoundingBox().minY+1&&!material.isSolid()&&material!=net.minecraft.block.material.Material.water;
    }
    @Override public void applyRotation(){if(surface==null||!allowed())return;MoveFixModule fix=module(MoveFixModule.class);if(fix==null||fix.hasOtherRotationOwner(getId()))return;
        float speed=rotationSpeed.getFloat();fix.setFakeRotation(getId(),fix.getRotationYaw(),fix.getRotationPitch()+RotationMath.clamp(90-fix.getRotationPitch(),-speed,speed));}
    @Override public void afterWalking(){MoveFixModule fix=module(MoveFixModule.class);if(surface==null||!allowed()||!mc.thePlayer.isBurning()||fix==null||!fix.ownsRotation(getId())||fix.getRotationPitch()<89.9F)return;
        Vec3 eye=mc.thePlayer.getPositionEyes(1);MovingObjectPosition hit=mc.theWorld.rayTraceBlocks(eye,eye.addVector(0,-Math.min(5,mc.playerController.getBlockReachDistance()),0),false,true,false);
        if(!valid(hit)||!hit.getBlockPos().equals(surface.getBlockPos()))return;ItemStack bucket=mc.thePlayer.inventory.mainInventory[slot];if(bucket==null||bucket.getItem()!=Items.water_bucket){release();return;}
        HotbarSwitches.Lease lease=HotbarSwitches.acquire(this,switching,slot,HotbarSwitches.BUFF+1);if(lease==null)return;
        try{if(!lease.active())return;fix.withActionRotation(getId(),()->{int token=HotbarSwitches.beginActionHook();try{return mc.playerController.sendUseItem(mc.thePlayer,mc.theWorld,HotbarSwitches.stack());}finally{HotbarSwitches.endActionHook(token);}});next=System.nanoTime()/1000000+delay.getInt();}
        finally{lease.close();surface=null;release();}
    }
    public String getStatus(){return isEnabled()?status:"Off";}
    @Override public String getDescription(){return super.getDescription()+(isEnabled()?" · "+getStatus():"");}
    public static boolean reserving(){AutoWaterModule water=module(AutoWaterModule.class);return water!=null&&water.isEnabled()&&water.surface!=null;}
    private void release(){MoveFixModule fix=module(MoveFixModule.class);if(fix!=null)fix.clearFakeRotation(getId());HotbarSwitches.releaseOwner(this);}
    @Override protected void onDisable(){surface=null;slot=-1;world=null;next=0;status="Idle";release();}
}
