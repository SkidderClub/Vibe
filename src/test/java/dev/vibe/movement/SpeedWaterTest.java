package dev.vibe.movement;

import dev.vibe.combat.CombatTimerAccess;
import dev.vibe.module.impl.movement.*;
import dev.vibe.path.PathTestFixture;
import dev.vibe.setting.*;
import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.MovementInput;
import org.junit.Test;
import static org.junit.Assert.*;

public class SpeedWaterTest {
    @Test public void adjustJesusSurfacePausesEverySpeedModeUntilFourTicksAfterReturningToShore() throws Exception {
        try (RewoundModesTest.Fixture f = new RewoundModesTest.Fixture()) {
            JesusModule jesus = new JesusModule();
            dev.vibe.Vibe.getInstance().getModuleManager().registerDynamic(jesus);
            jesus.mode.setValue("RewoundAdjust");
            jesus.setEnabled(true);
            ModeSetting mode = setting(f.speed, "Mode", ModeSetting.class);
            setting(f.speed, "Not On Void", BooleanSetting.class).setEnabled(false);

            for (String value : mode.getModes()) {
                mode.setValue(value);
                f.world.underBlock = Blocks.water.getDefaultState();
                f.player.onGround = true;
                f.player.motionY = -.0784;
                int jumps = f.player.jumps;
                float timer = CombatTimerAccess.getSpeed();
                assertFalse(f.player.isInWater());
                assertTrue(AdjustJesus.supportsFeet(f.player));
                f.speed.setEnabled(true);
                f.speed.tick();
                f.player.moveEntity(0, -.0784, .15);
                assertArrayEquals(value, new double[] {0, -.0784, .15}, f.player.lastMove, 0);
                assertEquals(value, -.0784, f.player.motionY, 0);
                assertEquals(value, jumps, f.player.jumps);
                assertEquals(value, timer, CombatTimerAccess.getSpeed(), 0);
                f.speed.setEnabled(false);
            }

            mode.setValue("Rewound");
            f.speed.setEnabled(true);
            assertNull(RewoundHooks.move(f.player, 0, -.0784, .15));
            f.world.underBlock = Blocks.stone.getDefaultState();
            for (int tick = 0; tick < 4; tick++) {
                f.player.ticksExisted++;
                assertNull("Waiting after leaving the water surface", RewoundHooks.move(f.player, 0, -.0784, .15));
            }
            f.player.ticksExisted++;
            assertEquals(.407, RewoundHooks.move(f.player, 0, -.0784, .15)[1], 0);
            jesus.setEnabled(false);
        }
    }
    @Test public void everySpeedModeLeavesSwimmingMotionInputAndTimerUntouchedIncludingEnableAndDisable() throws Exception {
        try(RewoundModesTest.Fixture f=new RewoundModesTest.Fixture()) {
            PathTestFixture.set(Entity.class,f.player,"inWater",true);f.player.onGround=false;
            AutoWalkModule walk=new AutoWalkModule();MoveFixModule fix=new MoveFixModule();
            dev.vibe.Vibe.getInstance().getModuleManager().registerDynamic(walk);dev.vibe.Vibe.getInstance().getModuleManager().registerDynamic(fix);
            f.player.movementInput=new MovementInput(){@Override public void updatePlayerMoveState(){moveForward=0;moveStrafe=.37F;jump=false;}};
            fix.installInputHook();f.slow.installInputHook();walk.setEnabled(true);
            setting(f.speed,"Reset XZ",BooleanSetting.class).setEnabled(true);setting(f.speed,"Reset Y",BooleanSetting.class).setEnabled(true);
            ModeSetting mode=setting(f.speed,"Mode",ModeSetting.class);
            java.lang.reflect.Method lift=EntityLivingBase.class.getDeclaredMethod("updateAITick");lift.setAccessible(true);
            for(String value:mode.getModes()) {
                mode.setValue(value);f.player.motionX=.23;f.player.motionY=-.12;f.player.motionZ=-.17;
                f.player.jumpMovementFactor=.029F;AacMovementSupport.speedInAir(f.player,.031F);CombatTimerAccess.setSpeed(1.375F);
                f.speed.setEnabled(true);walk.tick();f.speed.maintainRewound();f.speed.tick();
                f.player.movementInput.updatePlayerMoveState();assertTrue(value+" cancelled surfacing",f.player.movementInput.jump);
                assertEquals(1,f.player.movementInput.moveForward,0);assertEquals(.37F,f.player.movementInput.moveStrafe,0);
                assertNull(value,RewoundHooks.move(f.player,.23,-.12,-.17));
                S08PacketPlayerPosLook packet=new S08PacketPlayerPosLook(0,64,0,12,9,Collections.emptySet());
                f.player.rotationYaw=83;f.speed.rewoundCorrection(packet);assertEquals(12,packet.getYaw(),0);
                f.speed.pauseForRewoundStep(true);f.speed.pauseForRewoundStep(false);f.speed.setEnabled(false);
                assertEquals(value,.23,f.player.motionX,0);assertEquals(value,-.12,f.player.motionY,0);assertEquals(value,-.17,f.player.motionZ,0);
                assertEquals(value,.029F,f.player.jumpMovementFactor,0);assertEquals(value,.031F,AacMovementSupport.speedInAir(f.player),0);
                assertEquals(value,1.375F,CombatTimerAccess.getSpeed(),0);
                f.player.motionY=0;if(f.player.movementInput.jump)lift.invoke(f.player);assertEquals(.04,f.player.motionY,1E-7);
            }
            walk.setEnabled(false);
        }
    }
    @Test public void enteringWaterReleasesOnlySpeedOwnedTimerAndAirSettingsOnce() throws Exception {
        for(String mode:new String[]{"AACHop4","AACHop5","AACYSpoof","Custom","Rewound","AACHop3.3.13","AACHop3.5.0"}) {
            try(RewoundModesTest.Fixture f=new RewoundModesTest.Fixture()) {
                setting(f.speed,"Mode",ModeSetting.class).setValue(mode);setting(f.speed,"Not On Void",BooleanSetting.class).setEnabled(false);
                setting(f.speed,"Air Timer Tick",dev.vibe.setting.NumberSetting.class).setValue(1D);
                setting(f.speed,"Air Timer",dev.vibe.setting.NumberSetting.class).setValue(1.5D);
                f.player.onGround=false;f.player.fallDistance=.05F;f.player.jumpMovementFactor=.025F;
                AacMovementSupport.speedInAir(f.player,.031F);CombatTimerAccess.setSpeed(1.375F);
                f.speed.setEnabled(true);f.speed.tick();if(mode.equals("Rewound"))RewoundHooks.move(f.player,0,0,.2);
                double x=f.player.motionX,y=f.player.motionY,z=f.player.motionZ;
                PathTestFixture.set(Entity.class,f.player,"inWater",true);f.speed.maintainRewound();f.speed.tick();
                assertEquals(mode,1.375F,CombatTimerAccess.getSpeed(),0);assertEquals(mode,.025F,f.player.jumpMovementFactor,0);
                assertEquals(mode,.031F,AacMovementSupport.speedInAir(f.player),0);
                assertEquals(x,f.player.motionX,0);assertEquals(y,f.player.motionY,0);assertEquals(z,f.player.motionZ,0);
                CombatTimerAccess.setSpeed(1.7F);f.speed.tick();f.speed.setEnabled(false);assertEquals(mode,1.7F,CombatTimerAccess.getSpeed(),0);
            }
        }
    }
    private static <T extends Setting<?>> T setting(SpeedModule module,String name,Class<T> type) {
        for(Setting<?> setting:module.getSettings())if(setting.getRawName().equals(name))return type.cast(setting);
        throw new AssertionError(name);
    }
    @Test public void switchingToRewoundBeforeEnteringWaterDoesNotRetainThePreviousModesTimer()throws Exception{
        try(RewoundModesTest.Fixture f=new RewoundModesTest.Fixture()){
            ModeSetting mode=setting(f.speed,"Mode",ModeSetting.class);mode.setValue("AACHop4");
            f.player.onGround=false;f.player.fallDistance=.05F;CombatTimerAccess.setSpeed(1.375F);
            f.speed.setEnabled(true);f.speed.tick();assertEquals(1.5F,CombatTimerAccess.getSpeed(),0);
            mode.setValue("Rewound");RewoundHooks.move(f.player,0,0,.2);
            PathTestFixture.set(Entity.class,f.player,"inWater",true);f.speed.maintainRewound();
            assertEquals(1.375F,CombatTimerAccess.getSpeed(),0);f.speed.setEnabled(false);
        }
    }
}
