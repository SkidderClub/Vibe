package dev.vibe.movement;

import dev.vibe.Vibe;
import dev.vibe.combat.CombatTimerAccess;
import dev.vibe.module.Module;
import dev.vibe.module.impl.movement.*;
import dev.vibe.module.impl.world.ScaffoldModule;
import dev.vibe.module.impl.visual.AnimationsModule;
import dev.vibe.network.PacketDelayService;
import dev.vibe.path.PathTestFixture;
import dev.vibe.setting.ModeSetting;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class NovolineModesTest {
    @Test public void stepUsesSourceOffsetsAndResetsItsTimerNextUpdate() throws Exception {
        try(RewoundModesTest.Fixture f=new RewoundModesTest.Fixture()){
            StepModule step=register(new StepModule());step.mode.setValue("RewoundNovoline");step.setEnabled(true);
            RewoundHooks.preUpdate(f.player);
            assertEquals(1,RewoundHooks.preStepHeight(f.player,.6F),0);
            f.player.setEntityBoundingBox(f.player.getEntityBoundingBox().offset(0,1,0));
            RewoundHooks.postStep(f.player);
            assertEquals(2,f.queue.packets.size());
            assertEquals(64+.41999998688698,((C03PacketPlayer)f.queue.packets.get(0)).getPositionY(),0);
            assertEquals(64+.7531999805212,((C03PacketPlayer)f.queue.packets.get(1)).getPositionY(),0);
            assertFalse(((C03PacketPlayer)f.queue.packets.get(0)).isOnGround());
            assertEquals(.3F,CombatTimerAccess.getSpeed(),0);
            RewoundHooks.preUpdate(f.player);assertEquals(1,CombatTimerAccess.getSpeed(),0);
            step.setEnabled(false);assertEquals(.6F,f.player.stepHeight,0);
        }
    }
    @Test public void fractionalStepScalesOffsetsAndDisableRestoresTheTimer() throws Exception {
        try(RewoundModesTest.Fixture f=new RewoundModesTest.Fixture()){
            StepModule step=register(new StepModule());step.mode.setValue("RewoundNovoline");step.setEnabled(true);
            RewoundHooks.preStepHeight(f.player,.6F);f.player.setEntityBoundingBox(f.player.getEntityBoundingBox().offset(0,.75,0));RewoundHooks.postStep(f.player);
            assertEquals(64+.41999998688698*.75,((C03PacketPlayer)f.queue.packets.get(0)).getPositionY(),0);
            assertEquals(64+.7531999805212*.75,((C03PacketPlayer)f.queue.packets.get(1)).getPositionY(),0);
            step.setEnabled(false);assertEquals(1,CombatTimerAccess.getSpeed(),0);
        }
    }
    @Test public void stepDoesNotRaiseItsLimitInAirOrWhileSpeedIsEnabled() throws Exception {
        try(RewoundModesTest.Fixture f=new RewoundModesTest.Fixture()){
            StepModule step=register(new StepModule());step.mode.setValue("RewoundNovoline");step.setEnabled(true);
            f.player.onGround=false;assertEquals(.625F,RewoundHooks.preStepHeight(f.player,.6F),0);
            f.player.onGround=true;f.speed.setEnabled(true);assertEquals(.625F,RewoundHooks.preStepHeight(f.player,.6F),0);
            step.setEnabled(false);
        }
    }
    @Test public void waterwalkRespectsSourceLiquidLevelsSneakAndSpeedThreshold() throws Exception {
        try(RewoundModesTest.Fixture f=new RewoundModesTest.Fixture()){
            JesusModule jesus=register(new JesusModule());jesus.mode.setValue("RewoundNovoline");jesus.setEnabled(true);
            LiquidWorld world=PathTestFixture.allocate(LiquidWorld.class);world.state=Blocks.water.getDefaultState().withProperty(BlockLiquid.LEVEL,3);world.obstacles=new ArrayList<>();world.loaded=true;
            f.mc.theWorld=world;f.player.worldObj=world;PathTestFixture.set(Entity.class,f.player,"inWater",true);
            f.player.motionY=-.08;jesus.novolinePreUpdate(f.player);assertEquals(.12,f.player.motionY,0);
            assertNull("Novoline must retain the native horizontal movement vector",jesus.move(f.player,.2,-.08,.3));
            f.speed.setEnabled(true);f.player.motionY=-.08;jesus.novolinePreUpdate(f.player);assertEquals(-.08,f.player.motionY,0);
            world.state=world.state.withProperty(BlockLiquid.LEVEL,1);jesus.novolinePreUpdate(f.player);assertEquals(.12,f.player.motionY,0);
            f.player.movementInput.sneak=true;f.player.motionY=-.08;jesus.novolinePreUpdate(f.player);assertEquals(-.08,f.player.motionY,0);
            f.player.movementInput.sneak=false;world.state=Blocks.air.getDefaultState();jesus.novolinePreUpdate(f.player);assertEquals(-.08,f.player.motionY,0);
            jesus.setEnabled(false);
        }
    }
    @Test public void autoblockOwnPacketsBypassItsVetoAndReleaseOnceOnDisable() throws Exception {
        try(RewoundModesTest.Fixture f=new RewoundModesTest.Fixture()){
            select(f.aura,"Autoblock Mode","RewoundNovoline");f.aura.setEnabled(true);
            Field field=f.aura.getClass().getDeclaredField("novolineBlock");field.setAccessible(true);Object block=field.get(f.aura);
            Method send=block.getClass().getDeclaredMethod("block");send.setAccessible(true);send.invoke(block);send.invoke(block);
            assertEquals(1,f.queue.packets.size());assertTrue(f.queue.packets.get(0) instanceof C08PacketPlayerBlockPlacement);
            assertTrue(PacketDelayService.isUnmodified(f.queue.packets.get(0)));
            assertTrue(f.aura.novolineVeto(new C08PacketPlayerBlockPlacement(f.player.getHeldItem())));
            assertTrue(f.aura.novolineVeto(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,BlockPos.ORIGIN,EnumFacing.DOWN)));
            assertFalse(f.aura.novolineVeto(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.START_DESTROY_BLOCK,BlockPos.ORIGIN,EnumFacing.DOWN)));
            assertFalse(f.aura.novolineVeto(new C08PacketPlayerBlockPlacement(BlockPos.ORIGIN,1,f.player.getHeldItem(),.5F,1,.5F)));
            f.aura.setEnabled(false);assertEquals(2,f.queue.packets.size());assertTrue(f.queue.packets.get(1) instanceof C07PacketPlayerDigging);assertTrue(PacketDelayService.isUnmodified(f.queue.packets.get(1)));
            f.aura.novolineCorrection();assertEquals(2,f.queue.packets.size());
        }
    }
    @Test public void allNewModesAndFontsPersistInProfiles() throws Exception {
        try(RewoundModesTest.Fixture f=new RewoundModesTest.Fixture()){
            StepModule step=register(new StepModule());JesusModule jesus=register(new JesusModule());ScaffoldModule scaffold=register(new ScaffoldModule());AnimationsModule animations=register(new AnimationsModule());
            step.mode.setValue("RewoundNovoline");jesus.mode.setValue("RewoundNovoline");scaffold.getTower().setValue("RewoundNovoline");scaffold.line.setEnabled(true);scaffold.lineColor.setValue(0x9867ABEF);select(f.aura,"Autoblock Mode","RewoundNovoline");
            for(String mode:dev.vibe.ui.render.NovolineAnimations.MODES)assertTrue(animations.getBlockingStyle().getModes().contains(mode));
            animations.getBlockingStyle().setValue("Ethereal");
            dev.vibe.config.VibeConfig config=new dev.vibe.config.VibeConfig(Files.createTempDirectory("novoline-config-").toFile());assertTrue(config.save("novoline",Vibe.getInstance().getModuleManager()));
            step.mode.setValue("Jump");jesus.mode.setValue("Rewound");scaffold.getTower().setValue("None");scaffold.line.setEnabled(false);animations.getBlockingStyle().setValue("Vanilla");
            assertTrue(config.load("novoline",Vibe.getInstance().getModuleManager()));
            assertEquals("RewoundNovoline",step.mode.getValue());assertEquals("RewoundNovoline",jesus.mode.getValue());assertEquals("RewoundNovoline",scaffold.getTower().getValue());assertTrue(scaffold.line.isEnabled());assertEquals(0x9867ABEF,scaffold.lineColor.getArgb());assertEquals("Ethereal",animations.getBlockingStyle().getValue());
            dev.vibe.hud.ArrayListSettings array=new dev.vibe.hud.ArrayListSettings(s->{});assertTrue(array.font.getModes().containsAll(Arrays.asList("Novoline Client","Novoline Vanilla")));
        }
    }
    private static <T extends Module>T register(T module){Vibe.getInstance().getModuleManager().registerDynamic(module);return module;}
    private static void select(Module module,String name,String value){for(dev.vibe.setting.Setting<?> s:module.getSettings())if(s.getRawName().equals(name))((ModeSetting)s).setValue(value);}
    public static class LiquidWorld extends RewoundModesTest.FixtureWorld {IBlockState state;@Override public IBlockState getBlockState(BlockPos pos){return state;}}
}
