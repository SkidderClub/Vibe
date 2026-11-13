package dev.vibe.features;

import dev.vibe.combat.*;
import dev.vibe.inventory.*;
import dev.vibe.module.impl.combat.*;
import dev.vibe.module.impl.movement.*;
import dev.vibe.module.impl.world.AutoWaterModule;
import dev.vibe.path.PathTestFixture;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.util.*;
import net.minecraft.world.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ActionModulesTest {
    @Test public void noGroundChangesOnlyTheGroundFlagAndStopsImmediately()throws Exception{try(MovementForecastTest.Fixture f=new MovementForecastTest.Fixture()){
        CriticalsModule c=new CriticalsModule();f.base.base.modules.registerDynamic(c);c.setEnabled(true);c.tick();C03PacketPlayer.C06PacketPlayerPosLook packet=new C03PacketPlayer.C06PacketPlayerPosLook(1,64,2,75,20,true);c.outbound(packet);assertFalse(packet.isOnGround());assertEquals(64,packet.getPositionY(),0);assertEquals(75,packet.getYaw(),0);
        MovementInput input=new MovementInput();c.input(input);assertTrue("NoGround starts with a native jump",input.jump);assertEquals(0,f.base.self.motionY,0);
        c.mode.setValue("Jump");c.tick();C03PacketPlayer normal=new C03PacketPlayer(true);c.outbound(normal);assertTrue(normal.isOnGround());c.setEnabled(false);normal=new C03PacketPlayer(true);c.outbound(normal);assertTrue(normal.isOnGround());
    }}
    @Test public void jumpModeQueuesNativeInputFromActualControllerAttacksWithoutExtraMovementPackets()throws Exception{try(AutomationFixture f=new AutomationFixture()){
        // Use a world with native loaded collision shapes for the jump clearance check.
        WaterWorld world=waterWorld(f);world.blocks.put(new BlockPos(0,63,0),Blocks.stone.getDefaultState());
        CriticalsModule c=new CriticalsModule();f.base.modules.registerDynamic(c);c.mode.setValue("Jump");c.setEnabled(true);c.tick();f.mc.playerController=SilentMurderPacketsTest.nativeController(f.mc,f.queue);
        f.player.getAttributeMap().registerAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(1);AttackVictim victim=PathTestFixture.allocate(AttackVictim.class);victim.worldObj=world;victim.setEntityId(42);f.mc.playerController.attackEntity(f.player,victim);MovementInput input=new MovementInput();c.input(input);assertTrue(input.jump);assertEquals(0,f.player.motionY,0);for(Packet packet:f.queue.packets)assertFalse("Jump must not manufacture C03 packets",packet instanceof C03PacketPlayer);c.setEnabled(false);
    }}
    @Test public void targetStrafeKeepsCombatRotationAndWorksAcrossMoveFixModes()throws Exception{
        for(String mode:new String[]{"Silent","Direct","Off"})try(MovementForecastTest.Fixture f=new MovementForecastTest.Fixture()){
            MoveFixModule fix=new MoveFixModule();TargetStrafeModule strafe=new TargetStrafeModule();f.base.base.modules.registerDynamic(fix);f.base.base.modules.registerDynamic(strafe);fix.getMode().setValue(mode);fix.setFakeRotation("killaura",90,12);
            f.base.player("Enemy",.5,64,3).worldObj=f.world;strafe.targetMode.setValue("Nearest Target");strafe.setEnabled(true);f.base.self.movementInput=new MovementInput();fix.installInputHook();f.base.self.movementInput.updatePlayerMoveState();
            MovementInput input=f.base.self.movementInput;double yaw=Math.toRadians(MoveFixModule.movementYawHook(f.base.self,f.base.self.rotationYaw));double x=-Math.sin(yaw)*input.moveForward+Math.cos(yaw)*input.moveStrafe,z=Math.cos(yaw)*input.moveForward+Math.sin(yaw)*input.moveStrafe;
            assertTrue(mode+" must orbit in world space",x>.9&&Math.abs(z)<.01);assertTrue(fix.ownsRotation("killaura"));assertEquals(90,fix.getRotationYaw(),0);assertEquals(12,fix.getRotationPitch(),0);
            strafe.setEnabled(false);assertFalse(fix.hasForcedMovement());assertTrue(fix.ownsRotation("killaura"));
        }
    }
    @Test public void targetStrafeDoesNotAutomateIntoTheVoidOrOverrideOtherMovementOwners()throws Exception{try(MovementForecastTest.Fixture f=new MovementForecastTest.Fixture()){
        MoveFixModule fix=new MoveFixModule();TargetStrafeModule strafe=new TargetStrafeModule();f.base.base.modules.registerDynamic(fix);f.base.base.modules.registerDynamic(strafe);f.base.player("Enemy",.5,64,3).worldObj=f.world;strafe.targetMode.setValue("Nearest Target");strafe.setEnabled(true);
        fix.setForcedMovement("projectiledodge",1,0,false);MovementInput input=new MovementInput();strafe.input(input);assertTrue(fix.hasForcedMovement());fix.clearForcedMovement("projectiledodge");
        f.world.blocks.clear();input.moveForward=1;strafe.input(input);assertEquals(0,input.moveForward,0);assertEquals(0,input.moveStrafe,0);strafe.setEnabled(false);
    }}
    @Test public void targetStrafeReversesBeforeAHighWall()throws Exception{try(MovementForecastTest.Fixture f=new MovementForecastTest.Fixture()){
        MoveFixModule fix=new MoveFixModule();TargetStrafeModule strafe=new TargetStrafeModule();f.base.base.modules.registerDynamic(fix);f.base.base.modules.registerDynamic(strafe);f.base.player("Enemy",.5,64,3).worldObj=f.world;strafe.targetMode.setValue("Nearest Target");strafe.setEnabled(true);
        for(int z=-1;z<=1;z++)for(int y=64;y<=67;y++)f.world.blocks.put(new BlockPos(1,y,z),Blocks.stone.getDefaultState());MovementInput input=new MovementInput();strafe.input(input);assertTrue("Choose the open side of the orbit",input.moveStrafe<0);strafe.setEnabled(false);
    }}
    @Test public void waterUsesNativeBucketWithSilentAndBasicSwitchesAndRestoresCameraAndSlot()throws Exception{
        for(String mode:new String[]{"Silent","Basic"})for(net.minecraft.block.Block destination:new net.minecraft.block.Block[]{Blocks.air,Blocks.fire})try(AutomationFixture f=new AutomationFixture()){
            WaterWorld world=waterWorld(f);world.blocks.put(new BlockPos(0,63,0),Blocks.stone.getDefaultState());world.blocks.put(new BlockPos(0,64,0),destination.getDefaultState());f.player.getDataWatcher().updateObject(0,(byte)1);
            MoveFixModule fix=new MoveFixModule();AutoWaterModule water=new AutoWaterModule();f.base.modules.registerDynamic(fix);f.base.modules.registerDynamic(water);water.switching.mode.setValue(mode);water.setEnabled(true);
            f.player.inventory.mainInventory[0]=new ItemStack(Items.stone_sword);f.player.inventory.mainInventory[7]=new ItemStack(Items.water_bucket);f.player.rotationYaw=31;f.player.rotationPitch=-20;f.mc.playerController=SilentMurderPacketsTest.nativeController(f.mc,f.queue);f.mc.playerController.updateController();f.queue.packets.clear();
            water.tick();assertTrue(AutoWaterModule.reserving());water.applyRotation();water.applyRotation();water.afterWalking();assertEquals(Items.bucket,f.player.inventory.mainInventory[7].getItem());assertEquals(0,f.player.inventory.currentItem);assertEquals(31,f.player.rotationYaw,0);assertEquals(-20,f.player.rotationPitch,0);assertNull(HotbarSwitches.owned(water));assertEquals(Blocks.flowing_water,world.getBlockState(new BlockPos(0,64,0)).getBlock());
            int selected=-1,used=-1;for(int i=0;i<f.queue.packets.size();i++){Packet p=f.queue.packets.get(i);if(p instanceof C09PacketHeldItemChange&&((C09PacketHeldItemChange)p).getSlotId()==7)selected=i;if(p instanceof C08PacketPlayerBlockPlacement){used=i;assertEquals(Items.water_bucket,((C08PacketPlayerBlockPlacement)p).getStack().getItem());}}
            assertTrue("C09 selection precedes native bucket use",selected>=0&&used>selected);water.setEnabled(false);
        }
    }
    @Test public void waterDoesNotTriggerWithoutFireBucketOrReachableFloor()throws Exception{try(AutomationFixture f=new AutomationFixture()){
        WaterWorld world=waterWorld(f);world.blocks.put(new BlockPos(0,63,0),Blocks.stone.getDefaultState());AutoWaterModule water=new AutoWaterModule();f.base.modules.registerDynamic(new MoveFixModule());f.base.modules.registerDynamic(water);water.setEnabled(true);f.player.inventory.mainInventory[1]=new ItemStack(Items.water_bucket);
        water.tick();assertFalse(AutoWaterModule.reserving());f.player.getDataWatcher().updateObject(0,(byte)1);f.player.inventory.mainInventory[1]=null;water.tick();assertFalse(AutoWaterModule.reserving());f.player.inventory.mainInventory[1]=new ItemStack(Items.water_bucket);world.blocks.clear();water.tick();assertFalse(AutoWaterModule.reserving());assertTrue(f.queue.packets.isEmpty());water.setEnabled(false);
    }}
    @Test public void waterCanReplaceLavaBelowABurningPlayerButDoesNotWasteBucketsInTheNether()throws Exception{
        for(boolean nether:new boolean[]{false,true})try(AutomationFixture f=new AutomationFixture()){
            WaterWorld world=waterWorld(f);world.blocks.put(new BlockPos(0,63,0),Blocks.stone.getDefaultState());world.blocks.put(new BlockPos(0,64,0),Blocks.lava.getDefaultState());world.lava=true;if(nether)PathTestFixture.set(World.class,world,"provider",new WorldProviderHell(){@Override public boolean doesWaterVaporize(){return true;}});f.player.getDataWatcher().updateObject(0,(byte)1);
            MoveFixModule fix=new MoveFixModule();AutoWaterModule water=new AutoWaterModule();f.base.modules.registerDynamic(fix);f.base.modules.registerDynamic(water);f.player.inventory.mainInventory[2]=new ItemStack(Items.water_bucket);f.mc.playerController=SilentMurderPacketsTest.nativeController(f.mc,f.queue);water.setEnabled(true);water.tick();
            if(nether){assertFalse(AutoWaterModule.reserving());assertTrue(f.queue.packets.isEmpty());}else{assertTrue(AutoWaterModule.reserving());water.applyRotation();water.afterWalking();assertEquals(Blocks.flowing_water,world.getBlockState(new BlockPos(0,64,0)).getBlock());}
            water.setEnabled(false);
        }
    }
    @Test public void actionRotationRestoresCameraOnFailureAndRejectsForeignOwners()throws Exception{try(AutomationFixture f=new AutomationFixture()){
        MoveFixModule fix=new MoveFixModule();f.base.modules.registerDynamic(fix);f.player.rotationYaw=20;f.player.rotationPitch=-15;fix.setFakeRotation("autowater",70,90);
        assertFalse(fix.withActionRotation("other",()->{fail("A foreign owner ran an action");return true;}));
        try{fix.withActionRotation("autowater",()->{assertEquals(70,f.player.rotationYaw,0);assertEquals(90,f.player.rotationPitch,0);throw new IllegalStateException("test");});fail("Expected action failure");}catch(IllegalStateException expected){assertEquals("test",expected.getMessage());}
        assertEquals(20,f.player.rotationYaw,0);assertEquals(-15,f.player.rotationPitch,0);
    }}
    private static WaterWorld waterWorld(AutomationFixture f)throws Exception{WaterWorld world=PathTestFixture.allocate(WaterWorld.class);world.blocks=new HashMap<>();world.loaded=true;PathTestFixture.set(World.class,world,"isRemote",true);PathTestFixture.set(World.class,world,"provider",new WorldProviderSurface());PathTestFixture.set(World.class,world,"loadedEntityList",new ArrayList<Entity>());PathTestFixture.set(World.class,world,"playerEntities",new ArrayList<EntityPlayer>());f.mc.theWorld=world;f.player.worldObj=world;return world;}
    public static class AttackVictim extends net.minecraft.entity.passive.EntityPig {
        private AttackVictim(){super(null);}
        @Override public boolean isEntityAlive(){return true;}
        @Override public boolean attackEntityFrom(DamageSource source,float amount){return false;}
    }
    public static class WaterWorld extends ProjectileFixture.TestWorld {
        boolean lava;
        private WaterWorld(){super();}
        @Override public boolean isBlockModifiable(EntityPlayer player,BlockPos pos){return true;}
        @Override public boolean setBlockState(BlockPos pos,IBlockState state,int flags){blocks.put(pos,state);return true;}
        @Override public void playSoundEffect(double x,double y,double z,String sound,float volume,float pitch){}
        @Override public boolean isMaterialInBB(AxisAlignedBB box,net.minecraft.block.material.Material material){return lava&&material==net.minecraft.block.material.Material.lava;}
        @Override public boolean handleMaterialAcceleration(AxisAlignedBB box,net.minecraft.block.material.Material material,Entity entity){return false;}
    }
}
