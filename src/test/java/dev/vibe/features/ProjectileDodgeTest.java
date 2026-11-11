package dev.vibe.features;

import dev.vibe.features.dodge.*;
import dev.vibe.module.impl.combat.ProjectileDodgeModule;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.path.PathTestFixture;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real 1.8.9 block shapes and simulated movement, driven through the actual MoveFix input wrapper. */
public class ProjectileDodgeTest {
    private static EntityArrow arrow(MovementForecastTest.Fixture f,double x,double y,double z,double vx,double vy,double vz){EntityArrow arrow=new EntityArrow(f.world,x,y,z);arrow.motionX=vx;arrow.motionY=vy;arrow.motionZ=vz;f.world.loadedEntityList.add(arrow);return arrow;}
    private static ProjectileDodgeModule module(MovementForecastTest.Fixture f){ProjectileDodgeModule dodge=new ProjectileDodgeModule();f.base.base.modules.registerDynamic(dodge);dodge.setEnabled(true);return dodge;}
    private static MovementInput forward(){MovementInput input=new MovementInput();input.moveForward=1;return input;}

    @Test public void imminentArrowStillCausesABestEffortPerpendicularDodge()throws Exception{try(MovementForecastTest.Fixture f=new MovementForecastTest.Fixture()){
        ProjectileDodgeModule dodge=module(f);arrow(f,.5,65.2,1.8,0,0,-1);MovementInput input=new MovementInput();dodge.input(input);assertTrue(dodge.dodging());assertEquals(0,input.moveForward,0);assertEquals(1,Math.abs(input.moveStrafe),0);assertTrue(dodge.escapePath().safe);
    }}
    @Test public void forwardInputDoesNotDisableAssistanceAndCanBeExplicitlyRespected()throws Exception{try(MovementForecastTest.Fixture f=new MovementForecastTest.Fixture()){
        ProjectileDodgeModule dodge=module(f);dodge.ticks.setValue(20D);arrow(f,.5,65.2,5,0,0,-1);MovementInput input=forward();dodge.input(input);assertTrue(dodge.dodging());assertNotEquals(0,input.moveStrafe,0);dodge.respectMovement.setEnabled(true);input=forward();dodge.input(input);assertFalse(dodge.dodging());assertEquals(1,input.moveForward,0);assertEquals(0,input.moveStrafe,0);
    }}
    @Test public void supportedDodgeActuallyClearsAnArrowThatWouldHitTheStationaryBody()throws Exception{try(MovementForecastTest.Fixture f=new MovementForecastTest.Fixture()){
        ProjectileDodgeModule dodge=module(f);EntityArrow arrow=arrow(f,.5,72.2,12.5,0,0,-.75);DodgeTrajectory trajectory=DodgeTrajectory.trace(f.world,arrow,80);boolean originalHit=false;for(int tick=1;tick<trajectory.positions.size();tick++)if(f.base.self.getEntityBoundingBox().expand(.3,.3,.3).calculateIntercept(trajectory.positions.get(tick-1),trajectory.positions.get(tick))!=null)originalHit=true;assertTrue("Fixture arrow must really intersect the original body",originalHit);
        dodge.input(new MovementInput());assertTrue(dodge.dodging());MovementForecast.Path escape=dodge.escapePath();for(int tick=1;tick<trajectory.positions.size()&&tick<escape.bodies.size();tick++)assertNull("Predicted dodge still intersects arrow at tick "+tick,escape.bodies.get(tick).expand(.3,.3,.3).calculateIntercept(trajectory.positions.get(tick-1),trajectory.positions.get(tick)));
    }}
    @Test public void actualMoveFixInputWrapperDispatchesTheDodge()throws Exception{try(MovementForecastTest.Fixture f=new MovementForecastTest.Fixture()){
        MoveFixModule fix=new MoveFixModule();f.base.base.modules.registerDynamic(fix);ProjectileDodgeModule dodge=module(f);arrow(f,.5,65.2,4,0,0,-1);f.base.self.movementInput.moveForward=1;fix.installInputHook();f.base.self.movementInput.updatePlayerMoveState();assertTrue(dodge.dodging());assertNotEquals(0,f.base.self.movementInput.moveStrafe,0);
    }}
    @Test public void anEmbeddedArrowWithResidualMotionDoesNotTriggerDodge()throws Exception{try(MovementForecastTest.Fixture f=new MovementForecastTest.Fixture()){
        ProjectileDodgeModule dodge=module(f);EntityArrow arrow=arrow(f,.5,65.2,2,0,0,-1);PathTestFixture.set(EntityArrow.class,arrow,"inGround",true);MovementInput input=forward();dodge.input(input);assertFalse(dodge.dodging());assertEquals(1,input.moveForward,0);
    }}
    @Test public void arrowsBehindSolidCoverAndOwnArrowsAreIgnored()throws Exception{try(MovementForecastTest.Fixture f=new MovementForecastTest.Fixture()){
        ProjectileDodgeModule dodge=module(f);EntityArrow own=arrow(f,.5,65.2,3,0,0,-1);own.shootingEntity=f.base.self;dodge.input(new MovementInput());assertFalse(dodge.dodging());own.isDead=true;arrow(f,.5,65.2,12,0,0,-1);for(int y=64;y<68;y++)f.world.blocks.put(new BlockPos(0,y,7),Blocks.stone.getDefaultState());dodge.input(new MovementInput());assertFalse(dodge.dodging());
    }}
    @Test public void wallOnPreferredSideMakesItChooseTheOtherBorder()throws Exception{try(MovementForecastTest.Fixture f=new MovementForecastTest.Fixture()){
        ProjectileDodgeModule dodge=module(f);arrow(f,.5,65.2,5,0,0,-1);for(int y=64;y<67;y++)f.world.blocks.put(new BlockPos(1,y,0),Blocks.stone.getDefaultState());MovementInput input=new MovementInput();dodge.input(input);assertTrue(dodge.dodging());assertTrue(input.moveStrafe<0);
    }}
    @Test public void voidOnOneSideSelectsTheSupportedSideIncludingBraking()throws Exception{try(MovementForecastTest.Fixture f=new MovementForecastTest.Fixture()){
        ProjectileDodgeModule dodge=module(f);f.world.blocks.entrySet().removeIf(e->e.getKey().getX()>=1);arrow(f,.5,65.2,5,0,0,-1);MovementInput input=new MovementInput();dodge.input(input);assertTrue(dodge.dodging());assertTrue(input.moveStrafe<0);assertTrue(dodge.escapePath().safe);assertTrue(dodge.escapePath().loaded);
    }}
    @Test public void bothVoidSidesOrUnloadedTerrainLeaveInputAlone()throws Exception{try(MovementForecastTest.Fixture f=new MovementForecastTest.Fixture()){
        ProjectileDodgeModule dodge=module(f);f.world.blocks.entrySet().removeIf(e->e.getKey().getX()!=0);arrow(f,.5,65.2,5,0,0,-1);MovementInput input=new MovementInput();dodge.input(input);assertFalse(dodge.dodging());assertEquals(0,input.moveStrafe,0);f.world.loaded=false;int reads=f.world.reads;dodge.input(input);assertFalse(dodge.dodging());assertEquals(reads,f.world.reads);
    }}
    @Test public void correctionModesMatchThePredictedEscapeAndPreserveAuraOwnership()throws Exception{for(String mode:new String[]{"Off","Prevent Backwards Sprinting","Direct","Silent"})try(MovementForecastTest.Fixture f=new MovementForecastTest.Fixture()){
        MoveFixModule fix=new MoveFixModule();f.base.base.modules.registerDynamic(fix);fix.getCorrectMovement().setValue(mode);fix.setFakeRotation("aura",90,12);ProjectileDodgeModule dodge=module(f);dodge.allowRotation.setEnabled(true);arrow(f,.5,65.2,4,0,0,-1);MovementInput input=new MovementInput();dodge.input(input);assertTrue(mode,dodge.dodging());assertTrue(fix.ownsRotation("aura"));assertEquals(90,fix.getRotationYaw(),0);fix.correctInjectedInput(input);Vec3 world=DodgePlanner.worldDirection(input.moveForward,input.moveStrafe,MoveFixModule.movementYawHook(f.base.self,0));Vec3 expected=dodge.escapePath().points.get(dodge.escapePath().points.size()-1).subtract(f.base.self.getPositionVector());assertTrue(mode,world.xCoord*expected.xCoord+world.zCoord*expected.zCoord>0);
    }}
    @Test public void optionalRotationUsesMoveFixAndDisableReleasesOnlyItsOwnInput()throws Exception{try(MovementForecastTest.Fixture f=new MovementForecastTest.Fixture()){
        MoveFixModule fix=new MoveFixModule();f.base.base.modules.registerDynamic(fix);ProjectileDodgeModule dodge=module(f);dodge.allowRotation.setEnabled(true);arrow(f,.5,65.2,2,0,0,-1);dodge.input(new MovementInput());assertTrue(dodge.dodging());assertTrue(fix.ownsRotation(dodge.getId()));assertEquals("Silent",fix.getEffectiveCorrection());assertTrue(fix.hasForcedMovement());dodge.setEnabled(false);assertFalse(fix.ownsRotation(dodge.getId()));assertFalse(fix.hasForcedMovement());
    }}
    @Test public void previewOnlyNeverEditsInput()throws Exception{try(MovementForecastTest.Fixture f=new MovementForecastTest.Fixture()){
        ProjectileDodgeModule dodge=module(f);dodge.assist.setEnabled(false);arrow(f,.5,65.2,4,0,0,-1);MovementInput input=forward();dodge.input(input);assertNotNull(dodge.escapePath());assertFalse(dodge.dodging());assertEquals(1,input.moveForward,0);assertEquals(0,input.moveStrafe,0);
    }}
    @Test public void upstreamPlannerUsesMomentumAndItsExactSafetyDistances(){assertEquals(1.2,DodgePlanner.SAFE_DISTANCE,1E-8);assertEquals(1.5,DodgePlanner.SAFE_DISTANCE_WITH_PADDING,0);List<DodgePlanner.Plan> plans=DodgePlanner.plans(new Vec3(0,64,0),new Vec3(.2,0,0),0,new Vec3(0,65,5),new Vec3(0,0,-1),3,(a,b)->a.distanceTo(b));assertEquals(2,plans.size());assertTrue(plans.get(0).relative.xCoord>0);assertTrue(DodgePlanner.plans(new Vec3(2,64,0),new Vec3(0,0,0),0,new Vec3(0,65,5),new Vec3(0,0,-1),3,(a,b)->a.distanceTo(b)).isEmpty());}
}
