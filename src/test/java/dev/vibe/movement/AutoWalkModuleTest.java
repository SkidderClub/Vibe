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
    @Test public void keepsMovingTowardTheRouteWhileTheViewMakesALongSlowTurn()throws Exception{
        PathTestFixture.Player target=fixture.addPlayer(2,"Opponent",1.5,64,.5);walk.mode.setValue("Path");walk.yawSpeed.setValue(20D);
        walk.tick();fixture.module.tick();fixture.player.rotationYaw=90;
        for(int i=0;i<100;i++){
            float previous=fixture.player.rotationYaw;walk.tick();MovementInput moving=input();
            assertTrue("Movement paused during a turn",moving.moveForward!=0||moving.moveStrafe!=0);
            assertTrue(Math.abs(MathHelper.wrapAngleTo180_float(fixture.player.rotationYaw-previous))<=1.001);
            double angle=Math.toRadians(fixture.player.rotationYaw);
            double x=-Math.sin(angle)*moving.moveForward+Math.cos(angle)*moving.moveStrafe;
            assertTrue("Walking away from the target",x*(target.posX-fixture.player.posX)>0);
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
