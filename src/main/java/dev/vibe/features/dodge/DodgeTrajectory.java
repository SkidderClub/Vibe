/*
 * Adapted from LiquidBounce's SimulatedArrow.kt, Copyright (c) 2015-2026 CCBlueX.
 * GPL-3.0-or-later. Minecraft 1.8.9 throwable gravity and water drag are handled here.
 */
package dev.vibe.features.dodge;

import dev.vibe.features.MovementForecast;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.*;
import net.minecraft.block.material.Material;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/** Block-only flight simulation, as AutoDodge compares entities against their future positions separately. */
public final class DodgeTrajectory {
    private static final Field IN_GROUND=ReflectionHelper.findField(EntityArrow.class,"inGround","field_70254_i");
    public final List<Vec3> positions=new ArrayList<>(),velocities=new ArrayList<>();
    public boolean loaded=true;
    private DodgeTrajectory(){}
    public static boolean flying(Entity entity){if(entity.isDead)return false;if(entity instanceof EntityArrow)try{if(IN_GROUND.getBoolean(entity))return false;}catch(IllegalAccessException e){return false;}return entity.motionX*entity.motionX+entity.motionY*entity.motionY+entity.motionZ*entity.motionZ>1E-8;}
    public static DodgeTrajectory trace(World world,Entity projectile,int ticks){DodgeTrajectory result=new DodgeTrajectory();Vec3 position=projectile.getPositionVector(),velocity=new Vec3(projectile.motionX,projectile.motionY,projectile.motionZ);boolean arrow=projectile instanceof EntityArrow;double gravity=arrow||projectile instanceof EntityPotion?.05:.03;result.positions.add(position);result.velocities.add(velocity);
        for(int i=0;i<ticks;i++){Vec3 next=position.add(velocity);AxisAlignedBB swept=new AxisAlignedBB(position.xCoord,position.yCoord,position.zCoord,next.xCoord,next.yCoord,next.zCoord).expand(.3,.3,.3);if(!MovementForecast.loaded(swept)){result.loaded=false;break;}
            MovingObjectPosition block=world.rayTraceBlocks(position,next,false,true,false);double drag=world.getBlockState(new BlockPos(position)).getBlock().getMaterial()==Material.water?(arrow?.6:.8):.99;velocity=new Vec3(velocity.xCoord*drag,velocity.yCoord*drag-gravity,velocity.zCoord*drag);
            position=block==null?next:block.hitVec;result.positions.add(position);result.velocities.add(velocity);if(block!=null||position.yCoord<0)break;
        }return result;
    }
}
