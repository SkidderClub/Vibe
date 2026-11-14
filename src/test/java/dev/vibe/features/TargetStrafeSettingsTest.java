package dev.vibe.features;

import dev.vibe.module.impl.combat.KillAuraModule;
import dev.vibe.module.impl.movement.*;
import dev.vibe.movement.OrbitSteering;
import dev.vibe.movement.RewoundSpeed;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TargetStrafeSettingsTest {
    @Test public void targetRangeDoesNotPullThePlayerIntoDistantOrbitsAndRadiusChangesApplyImmediately() throws Exception {
        try (MovementForecastTest.Fixture f=new MovementForecastTest.Fixture()) {
            MoveFixModule fix=new MoveFixModule();TargetStrafeModule m=module(f,fix,6);
            MovementInput input=new MovementInput();input.moveForward=1;m.input(input);
            assertFalse(fix.hasForcedMovement());assertEquals(1,input.moveForward,0);assertEquals(0,input.moveStrafe,0);
            m.radius.setValue(6D);m.input(new MovementInput());assertTrue(fix.hasForcedMovement());
            m.radius.setValue(2D);input=new MovementInput();input.moveForward=1;m.input(input);
            assertFalse(fix.hasForcedMovement());assertEquals(1,input.moveForward,0);m.setEnabled(false);
        }
    }
    @Test public void independentAuraAndSpeedRequirementsReleaseOwnershipWhenDisabled() throws Exception {
        try (MovementForecastTest.Fixture f=new MovementForecastTest.Fixture()) {
            MoveFixModule fix=new MoveFixModule();TargetStrafeModule m=module(f,fix,2.5);
            KillAuraModule aura=new KillAuraModule();SpeedModule speed=new SpeedModule();
            f.base.base.modules.registerDynamic(aura);f.base.base.modules.registerDynamic(speed);
            m.onlyKillaura.setEnabled(true);m.input(new MovementInput());assertFalse(fix.hasForcedMovement());
            aura.setEnabled(true);m.input(new MovementInput());assertTrue(fix.hasForcedMovement());
            m.onlySpeed.setEnabled(true);m.input(new MovementInput());assertFalse(fix.hasForcedMovement());
            speed.setEnabled(true);m.input(new MovementInput());assertTrue(fix.hasForcedMovement());
            aura.setEnabled(false);m.tick();assertFalse(fix.hasForcedMovement());
            m.onlyKillaura.setEnabled(false);m.input(new MovementInput());assertTrue(fix.hasForcedMovement());
            speed.setEnabled(false);m.tick();assertFalse(fix.hasForcedMovement());m.setEnabled(false);
        }
    }
    @Test public void automaticWallContactReversesOnceInsteadOfFlippingEveryTick() throws Exception {
        try (MovementForecastTest.Fixture f=new MovementForecastTest.Fixture()) {
            TargetStrafeModule m=module(f,new MoveFixModule(),2.5);
            MovementInput input=new MovementInput();m.input(input);assertTrue(input.moveStrafe>0);
            f.base.self.isCollidedHorizontally=true;
            for(int tick=0;tick<4;tick++){input=new MovementInput();m.input(input);assertTrue(input.moveStrafe<0);}
            f.base.self.isCollidedHorizontally=false;m.input(new MovementInput());
            f.base.self.isCollidedHorizontally=true;input=new MovementInput();m.input(input);assertTrue(input.moveStrafe>0);m.setEnabled(false);
        }
    }
    @Test public void automaticDirectionReversesBeforeUnsupportedGround() throws Exception {
        try (MovementForecastTest.Fixture f=new MovementForecastTest.Fixture()) {
            TargetStrafeModule m=module(f,new MoveFixModule(),2.5);
            f.world.blocks.entrySet().removeIf(e->e.getKey().getX()>=1);
            MovementInput input=new MovementInput();m.input(input);assertTrue("Choose the supported west side",input.moveStrafe<0);m.setEnabled(false);
        }
    }
    @Test public void fixedDirectionRetainsItsExistingAutoJumpOverLowObstacles() throws Exception {
        try (MovementForecastTest.Fixture f=new MovementForecastTest.Fixture()) {
            TargetStrafeModule m=module(f,new MoveFixModule(),2.5);m.direction.setValue("Left");
            f.world.blocks.put(new BlockPos(1,64,0),Blocks.stone.getDefaultState());
            MovementInput input=new MovementInput();m.input(input);assertTrue(input.moveStrafe>0);assertTrue(input.jump);m.setEnabled(false);
        }
    }
    @Test public void fractionalRadiusCorrectionsSurviveSilentYawAndRewoundSpeed() throws Exception {
        try (MovementForecastTest.Fixture f=new MovementForecastTest.Fixture()) {
            MoveFixModule fix=new MoveFixModule();TargetStrafeModule m=module(f,fix,2.25);fix.setFakeRotation("killaura",123,12);
            MovementInput input=new MovementInput();m.input(input);double[] orbit=TargetStrafeModule.speedDirection();assertNotNull(orbit);
            double[] desired=OrbitSteering.direction(0,-2.25,2.5,1,0);
            assertEquals(desired[0],orbit[0],1E-7);assertEquals(desired[1],orbit[1],1E-7);
            f.base.self.movementInput=input;f.base.self.moveForward=input.moveForward;f.base.self.moveStrafing=input.moveStrafe;
            f.base.self.onGround=false;f.base.self.posY=64.5;f.base.self.setEntityBoundingBox(new AxisAlignedBB(.2,64.5,.2,.8,66.3,.8));
            RewoundSpeed speed=new RewoundSpeed(()->0);double[] move=speed.move(f.base.self,0,.2,0,false,"Off");
            double length=Math.hypot(move[0],move[2]);assertEquals(orbit[0],move[0]/length,1E-7);assertEquals(orbit[1],move[2]/length,1E-7);
            assertTrue(fix.ownsRotation("killaura"));assertEquals(123,fix.getRotationYaw(),0);m.setEnabled(false);
        }
    }
    @Test public void unsafeBothSidesAlsoStopRewoundDisplacementWhenTheChosenInputIsZero() throws Exception {
        try (MovementForecastTest.Fixture f=new MovementForecastTest.Fixture()) {
            TargetStrafeModule m=module(f,new MoveFixModule(),2.5);f.world.blocks.clear();
            MovementInput input=new MovementInput();input.moveForward=1;m.input(input);
            assertEquals(0,input.moveForward,0);assertEquals(0,input.moveStrafe,0);
            f.base.self.movementInput=input;f.base.self.moveForward=f.base.self.moveStrafing=0;f.base.self.onGround=false;
            f.base.self.motionX=.3;RewoundSpeed speed=new RewoundSpeed(()->0);
            double[] move=speed.move(f.base.self,.3,-.1,.2,false,"Off");
            assertEquals(0,move[0],0);assertEquals(0,move[2],0);
            assertEquals("Steering changes requested movement, without rewriting knockback state",.3,f.base.self.motionX,0);m.setEnabled(false);
        }
    }
    private TargetStrafeModule module(MovementForecastTest.Fixture f,MoveFixModule fix,double distance) throws Exception {
        TargetStrafeModule m=new TargetStrafeModule();f.base.base.modules.registerDynamic(fix);f.base.base.modules.registerDynamic(m);
        f.base.player("Enemy",.5,64,.5+distance).worldObj=f.world;m.targetMode.setValue("Nearest Target");m.setEnabled(true);return m;
    }
}
