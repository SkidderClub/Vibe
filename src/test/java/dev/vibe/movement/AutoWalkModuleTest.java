package dev.vibe.movement;

import dev.vibe.module.impl.movement.*;
import dev.vibe.path.PathTestFixture;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;
import org.junit.*;
import static org.junit.Assert.*;

public class AutoWalkModuleTest {
    private PathTestFixture fixture;
    private AutoWalkModule walk;
    private MoveFixModule fix;
    private Keys keys;
    @Before public void setup()throws Exception{
        fixture=new PathTestFixture();fixture.player.onGround=true;fixture.player.stepHeight=.6F;
        fixture.player.setEntityBoundingBox(new AxisAlignedBB(.2,64,.2,.8,65.8,.8));
        keys=new Keys();fixture.player.movementInput=keys;
        fix=new MoveFixModule();walk=new AutoWalkModule();walk.sprint.setEnabled(false);
        fixture.modules.registerDynamic(fix);fixture.modules.registerDynamic(walk);fix.installInputHook();walk.setEnabled(true);
    }
    @After public void cleanup()throws Exception{if(walk!=null)walk.setEnabled(false);if(fixture!=null)fixture.close();}
    private MovementInput input(){fixture.player.movementInput.updatePlayerMoveState();return fixture.player.movementInput;}
    @Test public void playerRemainsDefaultAndCoordinateSettingsFollowTheSelectedMode(){
        assertEquals("Player",walk.pathMode.getValue());assertFalse(walk.positionX.isVisible());
        walk.mode.setValue("Path");assertFalse(walk.positionX.isVisible());walk.pathMode.setValue("Position");assertTrue(walk.positionX.isVisible());assertTrue(walk.patrol.isVisible());assertFalse(walk.patrolRadius.isVisible());walk.patrol.setEnabled(true);assertTrue(walk.patrolRadius.isVisible());
    }
    @Test public void positionRoutesWithoutAnEnemyAndStopsAtTheDestination()throws Exception{
        position(7,64,0);walk.noRotate.setEnabled(true);
        for(int i=0;i<180&&!walk.getStatus().equals("At position");i++)simulate(i);
        assertEquals("At position",walk.getStatus());assertEquals(7.5,fixture.player.posX,.36);assertEquals(.5,fixture.player.posZ,.36);assertFalse(walk.isDriving());assertEquals(0,input().moveForward,0);assertNull(fixture.module.getTarget());assertTrue(fixture.module.shouldRender());
        walk.setEnabled(false);assertNull(fixture.module.getWalkingPosition());assertFalse(fixture.module.shouldRender());
    }
    @Test public void positionUsesTheReachableGroundBelowAnAirCoordinate()throws Exception{
        position(5,67,0);walk.noRotate.setEnabled(true);for(int i=0;i<160&&!walk.getStatus().equals("At position");i++)simulate(i);
        assertEquals("At position",walk.getStatus());assertEquals(5.5,fixture.player.posX,.36);
    }
    @Test public void switchingBackToPlayerModeReleasesTheCoordinateRoute()throws Exception{
        position(7,64,0);walk.tick();fixture.finishRoute();assertNotNull(fixture.module.getWalkingPosition());
        PathTestFixture.Player target=fixture.addPlayer(2,"Opponent",2.5,64,.5);walk.pathMode.setValue("Player");walk.tick();fixture.module.tick();walk.tick();
        assertNull(fixture.module.getWalkingPosition());assertSame(target,fixture.module.getTarget());assertTrue(walk.isDriving());
    }
    @Test public void positionFollowsThePlannerAroundSolidWalls()throws Exception{
        position(7,64,0);walk.noRotate.setEnabled(true);for(int z=-1;z<=1;z++)for(int y=64;y<=65;y++)fixture.block(2,y,z,Blocks.stone.getDefaultState());
        for(int i=0;i<300&&!walk.getStatus().equals("At position");i++){simulate(i);assertTrue("Walked through wall",clear(fixture.player.posX,fixture.player.posZ));}
        assertEquals("At position",walk.getStatus());
    }
    @Test public void patrolCompletesACircleAndKeepsTheRequestedRadius()throws Exception{
        position(0,64,0);walk.noRotate.setEnabled(true);walk.patrol.setEnabled(true);walk.patrolRadius.setValue(4D);fixture.player.setPosition(4.5,64,.5);
        double previous=0,angle=0;int moving=0;
        for(int i=0;i<500;i++){simulate(i);double a=Math.atan2(fixture.player.posZ-.5,fixture.player.posX-.5);angle+=MathHelper.wrapAngleTo180_double(Math.toDegrees(a-previous));previous=a;if(walk.isDriving())moving++;assertEquals("Patrol radius drifted",4,Math.hypot(fixture.player.posX-.5,fixture.player.posZ-.5),.9);}
        assertTrue("Patrol did not complete a full circle: "+angle,angle>360);assertTrue("Patrol stopped repeatedly",moving>450);assertNull(fixture.module.getTarget());
    }
    @Test public void fixedPositionDoesNotBlindlyWalkTowardVoidOrUnloadedGoals()throws Exception{
        for(int x=1;x<=4;x++)for(int z=-12;z<=12;z++)for(int y=60;y<=63;y++)fixture.world.blocks.remove(new BlockPos(x,y,z));
        position(2,64,0);walk.tick();fixture.finishRoute();for(int i=0;i<20;i++){walk.tick();assertFalse(walk.isDriving());assertEquals(0,input().moveForward,0);}
        walk.setPosition(new BlockPos(200,64,0));fixture.world.loaded=false;walk.tick();fixture.module.tick();walk.tick();assertFalse(walk.isDriving());
    }
    @Test public void guiMoveAllowsBothWalkModesOnlyInSelectedNonTypingScreens()throws Exception{
        GuiMoveModule gui=new GuiMoveModule();fixture.modules.registerDynamic(gui);gui.setEnabled(true);fixture.mc.currentScreen=PathTestFixture.allocate(net.minecraft.client.gui.inventory.GuiInventory.class);
        walk.tick();assertFalse(walk.isDriving());gui.allowAutoWalk.setEnabled(true);walk.tick();assertEquals(1,input().moveForward,0);
        position(4,64,0);walk.tick();fixture.finishRoute();walk.tick();assertTrue(walk.isDriving());assertTrue(input().moveForward!=0||input().moveStrafe!=0);
        fixture.mc.currentScreen=new net.minecraft.client.gui.GuiChat();walk.tick();assertFalse(walk.isDriving());assertEquals(0,input().moveForward,0);
        fixture.mc.currentScreen=PathTestFixture.allocate(net.minecraft.client.gui.inventory.GuiChest.class);walk.tick();assertFalse(walk.isDriving());gui.screens.toggle("Chests");walk.tick();fixture.finishRoute();walk.tick();assertTrue(walk.isDriving());
        gui.setEnabled(false);assertEquals(0,input().moveForward,0);
    }
    private void position(int x,int y,int z){walk.mode.setValue("Path");walk.pathMode.setValue("Position");walk.setPosition(new BlockPos(x,y,z));}
    private void simulate(int tick)throws Exception{
        walk.tick();fixture.module.tick();walk.tick();MovementInput movement=input();double yaw=Math.toRadians(fixture.player.rotationYaw),normal=Math.max(1,Math.hypot(movement.moveForward,movement.moveStrafe));
        double dx=(-Math.sin(yaw)*movement.moveForward+Math.cos(yaw)*movement.moveStrafe)*.16/normal,dz=(Math.cos(yaw)*movement.moveForward+Math.sin(yaw)*movement.moveStrafe)*.16/normal;
        double x=fixture.player.posX,z=fixture.player.posZ;if(collisionFree(x+dx,z))x+=dx;if(collisionFree(x,z+dz))z+=dz;
        fixture.player.setPosition(x,64,z);fixture.player.ticksExisted++;
        if(tick%4==3)fixture.forceReplan();
    }
    private boolean collisionFree(double x,double z){
        AxisAlignedBB body=new AxisAlignedBB(x-.3,64.001,z-.3,x+.3,65.8,z+.3);
        for(BlockPos p:BlockPos.getAllInBox(new BlockPos(body.minX,body.minY,body.minZ),new BlockPos(body.maxX,body.maxY,body.maxZ))){net.minecraft.block.state.IBlockState state=fixture.world.getBlockState(p);AxisAlignedBB b=state.getBlock().getCollisionBoundingBox(fixture.world,p,state);if(b!=null&&b.intersectsWith(body))return false;}return true;
    }
    @Test public void forwardActsLikeWAndReleasesWithoutStuckKeys(){
        walk.tick();assertEquals(1,input().moveForward,0);assertEquals(0,fixture.player.rotationYaw,0);
        keys.forward=-1;walk.tick();assertEquals(0,input().moveForward,0);
        keys.forward=0;keys.strafe=.5F;walk.tick();assertEquals(.5F,input().moveStrafe,0);
        keys.sneak=true;walk.tick();assertEquals(.3F,input().moveForward,0);
        keys.sneak=false;walk.setEnabled(false);assertEquals(0,input().moveForward,0);
    }
    @Test public void movementEntersBeforeSilentMoveFixCorrection(){
        fix.getMode().setValue("Silent");fix.setFakeRotation("test",90,0);walk.tick();MovementInput input=input();
        assertEquals(0,input.moveForward,.001);assertEquals(1,input.moveStrafe,.001);
    }
    @Test public void noRotateFollowsThePathWithAnUnchangedCameraAndSilentYaw() throws Exception {
        fixture.addPlayer(2, "Opponent", 5.5, 64, .5);
        walk.mode.setValue("Path");
        walk.noRotate.setEnabled(true);
        walk.tick();
        fixture.finishRoute();
        fixture.player.rotationYaw = 64;
        fixture.player.rotationPitch = 13;
        fix.getMode().setValue("Silent");
        fix.setFakeRotation("antiaim", -121, 90);
        walk.tick();
        MovementInput movement = input();
        double yaw = Math.toRadians(MoveFixModule.movementYawHook(fixture.player, fixture.player.rotationYaw));
        assertEquals(1, -Math.sin(yaw) * movement.moveForward + Math.cos(yaw) * movement.moveStrafe, 1e-6);
        assertEquals(0, Math.cos(yaw) * movement.moveForward + Math.sin(yaw) * movement.moveStrafe, 1e-6);
        assertEquals(64, fixture.player.rotationYaw, 0);
        assertEquals(13, fixture.player.rotationPitch, 0);
    }
    @Test public void menusAndOtherMovementOwnersPauseWalking(){
        walk.tick();assertEquals(1,input().moveForward,0);fixture.mc.currentScreen=new GuiScreen(){};walk.tick();assertFalse(walk.isDriving());assertEquals(0,input().moveForward,0);
        fixture.mc.currentScreen=null;fix.setForcedMovement("other",0,1,false);walk.tick();assertFalse(walk.isDriving());
        fix.clearForcedMovement("other");walk.tick();assertTrue(walk.isDriving());
    }
    @Test public void pathRunsAndDrawsWithoutChangingThePathModuleToggle()throws Exception{
        fixture.addPlayer(2,"Opponent",6.5,64,.5);walk.mode.setValue("Path");walk.tick();
        for(int i=0;i<120&&fixture.module.getRoute().nodes.size()<2;i++)fixture.module.tick();
        assertFalse(fixture.module.isEnabled());assertTrue(fixture.module.shouldRender());assertTrue(fixture.module.getRoute().nodes.size()>1);
        fixture.player.rotationYaw=90;walk.tick();float previous=fixture.player.rotationYaw;MovementInput turning=input();assertTrue(turning.moveForward!=0||turning.moveStrafe!=0);
        assertTrue(Math.abs(MathHelper.wrapAngleTo180_float(fixture.player.rotationYaw-previous))<=walk.yawSpeed.getFloat()/20+.001);
        for(int i=0;i<25;i++){walk.tick();input();}
        assertTrue(fixture.player.movementInput.moveForward>0);assertTrue(walk.getStatus().startsWith("Following"));
        walk.setEnabled(false);assertFalse(fixture.module.isEnabled());assertFalse(fixture.module.shouldRender());assertEquals(0,input().moveForward,0);
    }
    @Test public void pathKeepsWalkingIntoCloseOpponentsAndChoosesTheNextLivingOne()throws Exception{
        PathTestFixture.Player close=fixture.addPlayer(2,"Close",1.5,64,.5);PathTestFixture.Player next=fixture.addPlayer(3,"Next",5.5,64,.5);
        walk.mode.setValue("Path");walk.tick();fixture.module.tick();walk.tick();assertTrue(walk.isDriving());
        MovementInput moving=input();assertTrue(moving.moveForward!=0||moving.moveStrafe!=0);assertEquals("Following Close",walk.getStatus());
        close.isDead=true;fixture.module.tick();assertSame(next,fixture.module.getTarget());
    }
    @Test public void autoJumpCanBeDisabledAndDoesNotJumpIntoTallWalls(){
        fixture.block(0,64,1,Blocks.stone.getDefaultState());fixture.player.isCollidedHorizontally=true;walk.tick();assertTrue(input().jump);
        walk.autoJump.setEnabled(false);walk.tick();assertFalse(input().jump);
        walk.autoJump.setEnabled(true);fixture.block(0,65,1,Blocks.stone.getDefaultState());walk.tick();assertFalse(input().jump);
    }
    @Test public void nearbyOpponentBehindAWallStillNeedsTheDetour()throws Exception{
        fixture.addPlayer(2,"Opponent",2.5,64,.5);fixture.block(1,64,0,Blocks.stone.getDefaultState());fixture.block(1,65,0,Blocks.stone.getDefaultState());
        walk.mode.setValue("Path");walk.tick();fixture.module.tick();walk.tick();assertFalse("Cannot walk straight through a wall",fixture.module.getRoute().nodes.size()==1&&walk.isDriving());
    }
    @Test public void keepsForwardHeldWhileTheViewMakesALongSlowTurnEvenWhenItDrifts()throws Exception{
        PathTestFixture.Player target=fixture.addPlayer(2,"Opponent",1.5,64,.5);walk.mode.setValue("Path");walk.yawSpeed.setValue(20D);
        walk.tick();fixture.module.tick();fixture.player.rotationYaw=90;
        for(int i=0;i<100;i++){
            float previous=fixture.player.rotationYaw;walk.tick();MovementInput moving=input();
            assertTrue("Movement paused during a turn",moving.moveForward!=0||moving.moveStrafe!=0);
            assertTrue(Math.abs(MathHelper.wrapAngleTo180_float(fixture.player.rotationYaw-previous))<=1.001);
            assertEquals("Forward was released during the turn",1,moving.moveForward,0);
        }
    }
    @Test public void driftingOffTheRouteAtACornerDoesNotPauseForAReplacement()throws Exception{
        fixture.addPlayer(2,"Opponent",7.5,64,.5);for(int z=-1;z<=1;z++)for(int y=64;y<=65;y++)fixture.block(2,y,z,Blocks.stone.getDefaultState());
        walk.mode.setValue("Path");walk.tick();fixture.finishRoute();
        fixture.player.setPosition(-2.2,64,1.7);fixture.player.rotationYaw=30;
        for(int i=0;i<20;i++){walk.tick();assertTrue(walk.isDriving());assertEquals(1,input().moveForward,0);}
        // Even a stale/temporarily empty shared route must not release forward.
        fixture.setRouteState("route",dev.vibe.path.PathRoute.EMPTY);
        fixture.world.blocks.put(new BlockPos(1,64,0),Blocks.stone.getDefaultState());fixture.forceReplan();
        walk.tick();assertEquals(1,input().moveForward,0);
    }
    @Test public void waterUsesNormalJumpInputEvenWithLandAutoJumpOffAndWithoutATarget()throws Exception{
        PathTestFixture.set(net.minecraft.entity.Entity.class,fixture.player,"inWater",true);fixture.player.onGround=false;
        walk.autoJump.setEnabled(false);walk.tick();MovementInput swim=input();assertTrue(swim.jump);assertEquals(1,swim.moveForward,0);
        // This is the actual vanilla water-jump handler reached by that input.
        fixture.player.motionY=0;java.lang.reflect.Method lift=net.minecraft.entity.EntityLivingBase.class.getDeclaredMethod("updateAITick");
        lift.setAccessible(true);if(swim.jump)lift.invoke(fixture.player);assertEquals(.04,fixture.player.motionY,.000001);
        walk.mode.setValue("Path");walk.tick();assertFalse(walk.isDriving());assertTrue(input().jump);
        walk.setEnabled(false);assertFalse(input().jump);
    }
    @Test public void pathModeSwimsAcrossWaterAndKeepsForwardWhileSurfacing()throws Exception{
        fixture.addPlayer(2,"Opponent",7.5,64,.5);
        for(int x=1;x<=5;x++)for(int z=-3;z<=3;z++)for(int y=60;y<=63;y++)fixture.block(x,y,z,Blocks.water.getDefaultState());
        fixture.player.setPosition(2.5,61,.5);fixture.player.onGround=false;PathTestFixture.set(net.minecraft.entity.Entity.class,fixture.player,"inWater",true);
        walk.autoJump.setEnabled(false);walk.mode.setValue("Path");walk.tick();fixture.finishRoute();
        assertTrue(fixture.module.getRoute().complete);
        for(int i=0;i<40;i++){
            walk.tick();MovementInput swim=input();assertEquals(1,swim.moveForward,0);assertTrue(swim.jump);
            fixture.player.setPosition(2.5,Math.min(63.6,fixture.player.posY+.08),.5);
            if(i%5==4)fixture.forceReplan();fixture.module.tick();
        }
    }
    @Test public void followsExactTargetPositionInTheSameBlockAndAfterTheLastNode()throws Exception{
        PathTestFixture.Player target=fixture.addPlayer(2,"Opponent",.8,64,.6);walk.mode.setValue("Path");walk.tick();fixture.module.tick();
        assertEquals(1,fixture.module.getRoute().nodes.size());
        for(int i=0;i<20;i++){
            target.posX=.7+i*.02;target.posZ=.4+i*.015;walk.tick();MovementInput moving=input();assertTrue(walk.isDriving());
            assertTrue(moving.moveForward!=0||moving.moveStrafe!=0);
        }
        target.posX=fixture.player.posX;target.posZ=fixture.player.posZ;walk.tick();assertEquals(1,input().moveForward,0);
    }
    @Test public void rotationsTakeTheShortWayAndRespectTheirSpeed(){
        assertEquals(181,AutoWalkModule.turn(179,-179,10),0);assertEquals(175,AutoWalkModule.turn(170,-170,5),0);assertEquals(10,AutoWalkModule.turn(0,90,10),0);
    }
    @Test public void followsTheRenderedDetourAroundARealTwoBlockWall()throws Exception{
        PathTestFixture.Player target=fixture.addPlayer(2,"Opponent",6.5,64,.5);
        for(int z=-1;z<=1;z++)for(int y=64;y<=65;y++)fixture.block(2,y,z,Blocks.stone.getDefaultState());
        walk.mode.setValue("Path");
        for(int tick=0;tick<420&&fixture.player.getDistanceSqToEntity(target)>4;tick++){
            walk.tick();MovementInput movement=input();double angle=Math.toRadians(fixture.player.rotationYaw);
            double normal=Math.max(1,Math.hypot(movement.moveForward,movement.moveStrafe));
            double dx=(-Math.sin(angle)*movement.moveForward+Math.cos(angle)*movement.moveStrafe)*.2/normal;
            double dz=(Math.cos(angle)*movement.moveForward+Math.sin(angle)*movement.moveStrafe)*.2/normal;
            double x=fixture.player.posX,z=fixture.player.posZ;
            if(clear(x+dx,z))x+=dx;if(clear(x,z+dz))z+=dz;fixture.player.setPosition(x,64,z);
            if(tick%5==4)fixture.forceReplan();
            fixture.module.tick();
        }
        assertTrue("Could not follow the drawn route: "+walk.getStatus()+" at "+fixture.player.posX+", "+fixture.player.posZ,
                fixture.player.getDistanceSqToEntity(target)<=4);
    }
    private static boolean clear(double x,double z){return x+.3<=2||x-.3>=3||z+.3<=-1||z-.3>=2;}
    private static final class Keys extends MovementInput{
        float forward,strafe;boolean sneak;
        @Override public void updatePlayerMoveState(){moveForward=forward;moveStrafe=strafe;jump=false;super.sneak=sneak;}
    }
}
