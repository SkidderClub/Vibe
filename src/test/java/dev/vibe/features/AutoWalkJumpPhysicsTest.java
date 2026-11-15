package dev.vibe.features;

import dev.vibe.module.impl.movement.AutoWalkModule;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.module.impl.world.RavenSimulatedPlayer;
import dev.vibe.movement.WorldWalkTerrain;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AutoWalkJumpPhysicsTest {
    @Test public void alignsWithNarrowRaisedPassageBeforeJumpingAndClearsIt()throws Exception{
        clearsPassage(.8,7.5);
        clearsPassage(1.1,3.5);
    }
    private static void clearsPassage(double startX,double targetZ)throws Exception{
        try(MovementForecastTest.Fixture f=new MovementForecastTest.Fixture()){
            for(int z=1;z<=9;z++){
                f.base.block(1,64,z,Blocks.grass.getDefaultState());
                for(int y=64;y<=67;y++)for(int x:new int[]{0,2})f.base.block(x,y,z,Blocks.stone.getDefaultState());
            }
            f.base.self.setPosition(startX,64,.5);f.base.self.rotationYaw=-90;
            f.base.player("Opponent",1.5,65,targetZ).worldObj=f.world;
            f.base.self.movementInput=new MovementInput(){@Override public void updatePlayerMoveState(){moveForward=moveStrafe=0;jump=sneak=false;}};
            MoveFixModule fix=new MoveFixModule();AutoWalkModule walk=new AutoWalkModule();
            f.base.base.modules.registerDynamic(fix);f.base.base.modules.registerDynamic(walk);fix.installInputHook();
            walk.mode.setValue("Path");walk.rotatePitch.setEnabled(false);walk.setEnabled(true);
            try{
                walk.tick();f.base.base.finishRoute();assertTrue(f.base.base.module.getRoute().complete);
                RavenSimulatedPlayer simulation=RavenSimulatedPlayer.fromClientPlayer(f.base.self.movementInput);
                int launches=0;
                for(int tick=0;tick<200&&!(f.base.self.posZ>targetZ-1&&f.base.self.onGround);tick++){
                    walk.tick();f.base.self.movementInput.updatePlayerMoveState();
                    MovementInput input=f.base.self.movementInput;
                    if(input.jump&&simulation.onGround)launches++;
                    simulation.movementInput.moveForward=input.moveForward;simulation.movementInput.moveStrafe=input.moveStrafe;
                    simulation.movementInput.jump=input.jump;simulation.rotationYaw=f.base.self.rotationYaw;
                    simulation.tick();Vec3 pos=simulation.getPos();
                    f.base.self.setPosition(pos.xCoord,pos.yCoord,pos.zCoord);f.base.self.setEntityBoundingBox(simulation.box);
                    f.base.self.motionX=simulation.motionX;f.base.self.motionY=simulation.motionY;f.base.self.motionZ=simulation.motionZ;
                    f.base.self.onGround=simulation.onGround;f.base.self.isCollidedHorizontally=simulation.isCollidedHorizontally;
                    f.base.self.fallDistance=simulation.fallDistance;f.base.self.ticksExisted++;
                    if(tick%5==4)f.base.base.forceReplan();f.base.base.module.tick();
                }
                assertTrue("Did not clear passage: "+f.base.self.posX+", "+f.base.self.posY+", "+f.base.self.posZ+"; launches="+launches,
                        f.base.self.posZ>targetZ-1&&f.base.self.posY>=65&&f.base.self.onGround);
                assertEquals("A clear one-block step needs one jump",1,launches);
            }finally{walk.setEnabled(false);}
        }
    }
    @Test public void actualLaunchFootprintRejectsSideWallsAndLowCeilings()throws Exception{
        try(MovementForecastTest.Fixture f=new MovementForecastTest.Fixture()){
            f.base.block(1,64,1,Blocks.stone.getDefaultState());
            for(int y=64;y<=67;y++)f.base.block(0,y,1,Blocks.stone.getDefaultState());
            WorldWalkTerrain terrain=new WorldWalkTerrain(f.world,f.base.self);
            assertTrue(terrain.canMove(new BlockPos(1,64,0),new BlockPos(1,65,1)));
            f.base.self.setPosition(.95,64,.5);
            assertFalse(terrain.canJumpFrom(f.base.self.getEntityBoundingBox(),1.5,65,1.5));
            f.base.self.setPosition(1.5,64,.5);
            assertTrue(terrain.canJumpFrom(f.base.self.getEntityBoundingBox(),1.5,65,1.5));
            f.base.block(1,66,0,Blocks.stone.getDefaultState());
            assertFalse(terrain.canJumpFrom(f.base.self.getEntityBoundingBox(),1.5,65,1.5));
            assertFalse(terrain.canMove(new BlockPos(1,64,0),new BlockPos(1,65,1)));
        }
    }
}
