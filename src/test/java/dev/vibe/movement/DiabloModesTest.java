package dev.vibe.movement;

import dev.vibe.Vibe;
import dev.vibe.combat.CombatTimerAccess;
import dev.vibe.module.impl.combat.CriticalsModule;
import dev.vibe.module.impl.movement.LongJumpModule;
import dev.vibe.module.impl.visual.AnimationsModule;
import dev.vibe.network.PacketDelayService;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.potion.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Native packet serialization and constants from the supplied Diablo Watchdog modes. */
public class DiabloModesTest {
    @Test public void criticalOffsetsAlternateWithoutForcingPositionSerialization() throws Exception {
        DiabloCriticals state=new DiabloCriticals();Object player=new Object(),world=new Object();
        state.prepare(player,world,true,true,64);
        C03PacketPlayer first=new C03PacketPlayer.C04PacketPlayerPosition(2,64,3,true);state.outbound(first);
        assertEquals(64.01,first.getPositionY(),0);assertFalse(first.isOnGround());
        C03PacketPlayer look=new C03PacketPlayer.C05PacketPlayerLook(20,30,true);state.outbound(look);
        assertEquals(64.007,look.getPositionY(),0);assertFalse(look.isMoving());
        PacketBuffer bytes=new PacketBuffer(Unpooled.buffer());try{look.writePacketData(bytes);assertEquals(9,bytes.readableBytes());}finally{bytes.release();}
        state.prepare(player,world,false,false,65);C03PacketPlayer air=new C03PacketPlayer(true);state.outbound(air);assertTrue(air.isOnGround());
        state.prepare(player,world,true,true,64);C03PacketPlayer third=new C03PacketPlayer();state.outbound(third);assertEquals(64.01,third.getPositionY(),0);
        state.prepare(player,world,true,false,64);state.outbound(new C03PacketPlayer());
        state.prepare(player,world,true,true,64);C03PacketPlayer reset=new C03PacketPlayer();state.outbound(reset);assertEquals(64.01,reset.getPositionY(),0);
        state.prepare(player,new Object(),true,true,70);C03PacketPlayer changed=new C03PacketPlayer();state.outbound(changed);assertEquals(70.01,changed.getPositionY(),0);
        state.reset();C03PacketPlayer disabled=new C03PacketPlayer(true);state.outbound(disabled);assertTrue(disabled.isOnGround());
    }
    @Test public void longJumpSends49UnmodifiedPairsAndAnOrdinaryGroundPacket() throws Exception {
        try(RewoundModesTest.Fixture f=new RewoundModesTest.Fixture()){
            DiabloLongJump jump=new DiabloLongJump();jump.begin(f.player,f.world);
            assertEquals(99,f.queue.packets.size());
            for(int i=0;i<98;i++){
                C03PacketPlayer p=(C03PacketPlayer)f.queue.packets.get(i);
                assertTrue(p.isMoving());assertEquals(i%2==0?64.0625:64,p.getPositionY(),0);
                assertFalse(p.isOnGround());assertTrue(PacketDelayService.isUnmodified(p));
            }
            C03PacketPlayer last=(C03PacketPlayer)f.queue.packets.get(98);
            assertTrue(last.isOnGround());assertFalse(PacketDelayService.isUnmodified(last));assertEquals(64,last.getPositionY(),0);
            assertFalse(jump.pre());assertEquals((double).70F,f.player.motionY,0);
            f.player.onGround=false;jump.post();assertEquals(f.player.capabilities.getWalkSpeed()*2.925*1.14F,f.player.motionZ,1E-12);
            assertFalse(jump.pre());f.player.onGround=true;assertTrue(jump.pre());jump.reset();assertEquals(1,CombatTimerAccess.getSpeed(),0);
        }
    }
    @Test public void longJumpUsesSourcePotionAndDiagonalMotionAndDoesNotJumpWithoutInput() throws Exception {
        try(RewoundModesTest.Fixture f=new RewoundModesTest.Fixture()){
            DiabloLongJump jump=new DiabloLongJump();f.player.onGround=false;jump.begin(f.player,f.world);assertTrue(f.queue.packets.isEmpty());
            f.player.onGround=true;f.player.moveForward=0;assertFalse(jump.pre());assertEquals(0,f.player.motionY,0);
            jump.post();assertEquals(0,f.player.motionX,0);assertEquals(0,f.player.motionZ,0);
            f.player.moveForward=1;f.player.moveStrafing=1;f.player.rotationYaw=0;
            f.player.effects.put(Potion.moveSpeed.id,new PotionEffect(Potion.moveSpeed.id,100,1));
            f.player.effects.put(Potion.moveSlowdown.id,new PotionEffect(Potion.moveSlowdown.id,100,0));jump.post();
            double speed=f.player.capabilities.getWalkSpeed()*2.925/1.15*1.4*1.14F;
            assertEquals(speed/Math.sqrt(2),f.player.motionX,1E-12);assertEquals(speed/Math.sqrt(2),f.player.motionZ,1E-12);jump.reset();
        }
    }
    @Test public void moduleHooksDisableOnLandingAndModeChangesReleaseState() throws Exception {
        try(RewoundModesTest.Fixture f=new RewoundModesTest.Fixture()){
            LongJumpModule jump=new LongJumpModule();Vibe.getInstance().getModuleManager().registerDynamic(jump);
            jump.getMode().setValue("RewoundDiablo");jump.setEnabled(true);assertEquals(99,f.queue.packets.size());
            RewoundHooks.beforeWalking(f.player);assertEquals((double).70F,f.player.motionY,0);
            f.player.onGround=false;RewoundHooks.afterWalking(f.player);assertTrue(f.player.motionZ>.3);
            f.player.onGround=true;RewoundHooks.beforeWalking(f.player);assertFalse(jump.isEnabled());assertEquals(1,CombatTimerAccess.getSpeed(),0);
            CriticalsModule criticals=new CriticalsModule();Vibe.getInstance().getModuleManager().registerDynamic(criticals);
            criticals.mode.setValue("RewoundDiablo");criticals.setEnabled(true);criticals.tick();f.player.movementInput.jump=false;criticals.input(f.player.movementInput);assertFalse(f.player.movementInput.jump);
            criticals.setEnabled(false);C03PacketPlayer p=new C03PacketPlayer(true);criticals.outbound(p);assertTrue(p.isOnGround());
            AnimationsModule animations=new AnimationsModule();assertTrue(animations.getBlockingStyle().getModes().contains("Diablo"));assertTrue(animations.getBlockingStyle().getModes().contains("ExihiDiablo"));
        }
    }
    @Test public void markerUsesTheSourceLinearReversingHeight() {
        dev.vibe.ui.render.DiabloTargetMarker marker=new dev.vibe.ui.render.DiabloTargetMarker();
        assertEquals(2,marker.height(1.8F,1000),0);assertEquals(.5,marker.height(1.8F,1250),0);assertEquals(-1,marker.height(1.8F,1500),0);
        assertEquals(-1,marker.height(1.8F,1501),0);assertEquals(.5,marker.height(1.8F,1751),0);assertEquals(2,marker.height(1.8F,2001),0);
    }
    @Test public void motionUsesMoveFixYawWithoutTurningTheVisibleCamera() throws Exception {
        try(RewoundModesTest.Fixture f=new RewoundModesTest.Fixture()){
            dev.vibe.module.impl.movement.MoveFixModule fix=new dev.vibe.module.impl.movement.MoveFixModule();
            Vibe.getInstance().getModuleManager().registerDynamic(fix);fix.setEnabled(true);
            fix.setFakeRotation("diablo-test",90,0,"Direct");f.player.rotationYaw=0;
            DiabloLongJump.motion(f.player,.5);assertEquals(-.5,f.player.motionX,1E-12);assertEquals(0,f.player.motionZ,1E-12);
            assertEquals(0,f.player.rotationYaw,0);fix.discardOwnedState("diablo-test");fix.setEnabled(false);
        }
    }
    @Test public void allDiabloChoicesPersistThroughProfileReload() throws Exception {
        try(RewoundModesTest.Fixture f=new RewoundModesTest.Fixture()){
            dev.vibe.module.ModuleManager modules=Vibe.getInstance().getModuleManager();
            CriticalsModule criticals=new CriticalsModule();LongJumpModule jump=new LongJumpModule();AnimationsModule animations=new AnimationsModule();
            dev.vibe.module.impl.client.ClickGuiModule gui=new dev.vibe.module.impl.client.ClickGuiModule();
            dev.vibe.module.impl.client.HudModule hud=new dev.vibe.module.impl.client.HudModule();
            dev.vibe.module.impl.client.KillsayModule say=new dev.vibe.module.impl.client.KillsayModule();
            for(dev.vibe.module.Module module:new dev.vibe.module.Module[]{criticals,jump,animations,gui,hud,say})modules.registerDynamic(module);
            criticals.mode.setValue("RewoundDiablo");jump.getMode().setValue("RewoundDiablo");animations.getBlockingStyle().setValue("ExihiDiablo");gui.getTheme().setValue("Diablo");hud.targetHud.mode.setValue("Diablo");say.mode.setValue("Diablo");
            dev.vibe.config.VibeConfig profile=new dev.vibe.config.VibeConfig(java.nio.file.Files.createTempDirectory("diablo-profile-").toFile());assertTrue(profile.save("diablo",modules));
            criticals.mode.setValue("Jump");jump.getMode().setValue("Custom");animations.getBlockingStyle().setValue("Vanilla");gui.getTheme().setValue("Skeet");hud.targetHud.mode.setValue("Novoline");say.mode.setValue("Vibe");
            assertTrue(profile.load("diablo",modules));assertEquals("RewoundDiablo",criticals.mode.getValue());assertEquals("RewoundDiablo",jump.getMode().getValue());
            assertEquals("ExihiDiablo",animations.getBlockingStyle().getValue());assertEquals("Diablo",gui.getTheme().getValue());assertEquals("Diablo",hud.targetHud.mode.getValue());assertEquals("Diablo",say.mode.getValue());
        }
    }
}
