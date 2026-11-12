package dev.vibe.features;

import dev.vibe.combat.ProjectileFixture;
import dev.vibe.combat.SilentMurderPacketsTest;
import dev.vibe.inventory.*;
import dev.vibe.module.impl.world.AutoFishModule;
import dev.vibe.module.impl.visual.BeverageNotificationModule;
import dev.vibe.path.PathTestFixture;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeFeatureActionsTest {
    @Test public void combatPreparationReleasesFishingBeforeNativeAttacks()throws Exception{try(AutomationFixture f=new AutomationFixture()){fishingWorld(f);f.player.inventory.mainInventory[0]=new ItemStack(Items.stone_sword);f.player.inventory.mainInventory[7]=new ItemStack(Items.fishing_rod);AutoFishModule fish=new AutoFishModule();fish.delay.setValue(0D);fish.setEnabled(true);fish.tick();fish.afterWalking();assertNotNull(HotbarSwitches.owned(fish));dev.vibe.module.impl.combat.KillAuraModule aura=new dev.vibe.module.impl.combat.KillAuraModule();f.base.modules.registerDynamic(aura);aura.setEnabled(true);PathTestFixture.set(dev.vibe.module.impl.combat.KillAuraModule.class,aura,"target",f.player);fish.applyRotation();assertNull(HotbarSwitches.owned(fish));assertEquals(0,f.player.inventory.currentItem);aura.setEnabled(false);fish.setEnabled(false);}}
    private static void fishingWorld(AutomationFixture f)throws Exception{ProjectileFixture.TestWorld world=PathTestFixture.allocate(ProjectileFixture.TestWorld.class);world.blocks=new HashMap<>();world.loaded=true;PathTestFixture.set(World.class,world,"isRemote",true);PathTestFixture.set(World.class,world,"loadedEntityList",new ArrayList<Entity>());f.mc.theWorld=world;f.player.worldObj=world;f.mc.playerController=SilentMurderPacketsTest.nativeController(f.mc,f.queue);}
    @Test public void fishingUsesNativeC09ThenC08AndRestoresBothSwitchingModes()throws Exception{for(String mode:new String[]{"Basic","Silent"})try(AutomationFixture f=new AutomationFixture()){fishingWorld(f);f.player.inventory.mainInventory[0]=new ItemStack(Items.stone_sword);f.player.inventory.mainInventory[7]=new ItemStack(Items.fishing_rod);f.mc.playerController.updateController();f.queue.packets.clear();AutoFishModule fish=new AutoFishModule();f.base.modules.registerDynamic(fish);fish.delay.setValue(0D);fish.switching.mode.setValue(mode);fish.setEnabled(true);fish.tick();fish.afterWalking();int slot=-1,use=-1;for(int i=0;i<f.queue.packets.size();i++){Packet p=f.queue.packets.get(i);if(p instanceof C09PacketHeldItemChange&&((C09PacketHeldItemChange)p).getSlotId()==7)slot=i;if(p instanceof C08PacketPlayerBlockPlacement){use=i;assertSame(Items.fishing_rod,((C08PacketPlayerBlockPlacement)p).getStack().getItem());}}assertTrue("Native slot selection must precede use",slot>=0&&use>slot);assertEquals(mode.equals("Silent")?0:7,f.player.inventory.currentItem);fish.setEnabled(false);assertEquals(0,f.player.inventory.currentItem);assertNull(HotbarSwitches.owned(fish));}}
    @Test public void durabilityReservePreventsBothSlotSwitchAndNativeUse()throws Exception{try(AutomationFixture f=new AutomationFixture()){fishingWorld(f);ItemStack rod=new ItemStack(Items.fishing_rod);rod.setItemDamage(rod.getMaxDamage()-5);f.player.inventory.mainInventory[6]=rod;f.mc.playerController.updateController();f.queue.packets.clear();AutoFishModule fish=new AutoFishModule();fish.delay.setValue(0D);fish.reserve.setValue(5D);fish.setEnabled(true);fish.tick();fish.afterWalking();assertTrue(f.queue.packets.isEmpty());assertNull(HotbarSwitches.owned(fish));fish.setEnabled(false);}}
}
