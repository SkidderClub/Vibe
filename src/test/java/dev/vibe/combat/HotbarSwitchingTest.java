package dev.vibe.combat;

import dev.vibe.inventory.*;
import dev.vibe.path.PathTestFixture;
import java.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.world.World;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real transformed vanilla slot cache/use packets, plus ownership and render state. */
public class HotbarSwitchingTest {
    private static ItemSwitching settings(String mode) { return new ItemSwitching(setting -> {},mode); }
    private static void nativeController(AutomationFixture f) throws Exception {
        f.mc.playerController=SilentMurderPacketsTest.nativeController(f.mc,f.queue);
        f.player.inventory.setInventorySlotContents(0,new ItemStack(Items.diamond_sword));
        f.player.inventory.setInventorySlotContents(8,new ItemStack(Items.bread));
        f.player.getFoodStats().setFoodLevel(12);
        f.mc.playerController.updateController();f.queue.packets.clear();
    }
    private static List<Integer> slots(AutomationFixture f) {
        List<Integer> slots=new ArrayList<>();
        for(Packet packet:f.queue.packets)if(packet instanceof C09PacketHeldItemChange)slots.add(((C09PacketHeldItemChange)packet).getSlotId());
        return slots;
    }

    @Test public void bothModesUseVanillasSlotCacheAndNativeFoodStack() throws Exception {
        for(String mode:new String[]{"Basic","Silent"})try(AutomationFixture f=new AutomationFixture()) {
            nativeController(f);
            HotbarSwitches.Lease lease=HotbarSwitches.acquire(new Object(),settings(mode),8,HotbarSwitches.BUFF);
            assertEquals(mode.equals("Silent")?0:8,f.player.inventory.currentItem);
            assertEquals(Collections.singletonList(8),slots(f));
            // Even when the caller captured the visibly held sword, the wrapper supplies bread.
            f.mc.playerController.sendUseItem(f.player,f.mc.theWorld,f.player.getHeldItem());
            assertTrue(f.player.isUsingItem());assertSame(Items.bread,f.player.getItemInUse().getItem());
            C08PacketPlayerBlockPlacement use=null;
            for(Packet packet:f.queue.packets)if(packet instanceof C08PacketPlayerBlockPlacement)use=(C08PacketPlayerBlockPlacement)packet;
            assertNotNull(use);assertSame(Items.bread,use.getStack().getItem());
            assertEquals("Native cache must not reselect every use",Collections.singletonList(8),slots(f));
            for(int tick=0;tick<32;tick++) {
                int scope=HotbarSwitches.beginPlayerHook(f.player);
                try {assertSame("Vanilla item-use tick must see exactly its in-use stack",f.player.getItemInUse(),f.player.getHeldItem());}
                finally {HotbarSwitches.endActionHook(scope);}
                assertEquals(mode.equals("Silent")?0:8,f.player.inventory.currentItem);
            }
            lease.close();assertEquals(Arrays.asList(8,0),slots(f));assertEquals(0,f.player.inventory.currentItem);
            assertEquals(8,HotbarSwitches.highlight().slot);
        }
    }

    @Test public void prioritiesResumeAndManualSelectionWinsEvenInsideAnInputScope() throws Exception {
        try(AutomationFixture f=new AutomationFixture()) {
            nativeController(f);
            HotbarSwitches.Lease tool=HotbarSwitches.acquire(new Object(),settings("Silent"),2,HotbarSwitches.TOOL);
            HotbarSwitches.Lease buff=HotbarSwitches.acquire(new Object(),settings("Basic"),8,HotbarSwitches.BUFF);
            assertFalse(tool.active());assertTrue(buff.active());assertEquals(8,HotbarSwitches.selectedSlot());
            buff.close();assertTrue(tool.active());assertEquals(2,HotbarSwitches.selectedSlot());assertEquals(0,f.player.inventory.currentItem);
            HotbarSwitches.beginInputHook();assertEquals(2,f.player.inventory.currentItem);
            f.player.inventory.currentItem=5;
            assertFalse(tool.valid());HotbarSwitches.finishInputHook();
            assertEquals(5,HotbarSwitches.visibleSlot());assertEquals(5,HotbarSwitches.selectedSlot());assertNull(HotbarSwitches.highlight());
            tool.close();assertEquals(5,f.player.inventory.currentItem);
        }
    }

    @Test public void basicCommitSilentRestoreAndWorldChangesCannotLeaveStaleSlots() throws Exception {
        try(AutomationFixture f=new AutomationFixture()) {
            nativeController(f);
            HotbarSwitches.acquire(new Object(),settings("Basic"),4,HotbarSwitches.AFK).closePreservingSelection();
            assertEquals(4,f.player.inventory.currentItem);
            HotbarSwitches.Lease silent=HotbarSwitches.acquire(new Object(),settings("Silent"),8,HotbarSwitches.PROJECTILE);
            assertEquals(4,f.player.inventory.currentItem);silent.closePreservingSelection();assertEquals(4,f.player.inventory.currentItem);
            HotbarSwitches.Lease old=HotbarSwitches.acquire(new Object(),settings("Silent"),8,HotbarSwitches.PROJECTILE);
            int scope=HotbarSwitches.beginActionHook();assertEquals(8,f.player.inventory.currentItem);
            f.mc.theWorld=PathTestFixture.allocate(net.minecraft.client.multiplayer.WorldClient.class);f.player.worldObj=f.mc.theWorld;
            assertFalse(old.valid());assertEquals(4,HotbarSwitches.selectedSlot());assertNull(HotbarSwitches.highlight());
            HotbarSwitches.endActionHook(scope);assertEquals(4,f.player.inventory.currentItem);
        }
    }

    @Test public void nativeUseFailureStillRestoresTheVisibleItemAndNestedScopes() throws Exception {
        try(AutomationFixture f=new AutomationFixture()) {
            nativeController(f);
            f.player.inventory.mainInventory[8]=new ItemStack(new Item() {
                @Override public ItemStack onItemRightClick(ItemStack stack,World world,EntityPlayer player) {throw new IllegalStateException("native use");}
            });
            HotbarSwitches.Lease lease=HotbarSwitches.acquire(new Object(),settings("Silent"),8,HotbarSwitches.PROJECTILE);
            try {f.mc.playerController.sendUseItem(f.player,f.mc.theWorld,f.player.getHeldItem());fail("Expected item failure");}
            catch(IllegalStateException expected){assertEquals("native use",expected.getMessage());}
            assertEquals(0,f.player.inventory.currentItem);assertTrue(lease.active());
            int outer=HotbarSwitches.beginActionHook(),inner=HotbarSwitches.beginActionHook();
            HotbarSwitches.endActionHook(inner);assertEquals(8,f.player.inventory.currentItem);
            HotbarSwitches.endActionHook(outer);assertEquals(0,f.player.inventory.currentItem);
            lease.close();assertEquals(Arrays.asList(8,0),slots(f));
        }
    }

    @Test public void everyNativeSlotUserHasTheSameSettingsAndOwnerCleanup() throws Exception {
        try(AutomationFixture f=new AutomationFixture()) {
            nativeController(f);
            List<dev.vibe.module.Module> modules=Arrays.asList(new dev.vibe.module.impl.world.AutoToolModule(),
                    new dev.vibe.module.impl.combat.PickenSwitchModule(),new dev.vibe.module.impl.combat.AutoBuffModule(),
                    new dev.vibe.module.impl.combat.ProjectileAuraModule(),new dev.vibe.module.impl.world.ScaffoldModule(),
                    new dev.vibe.module.impl.meme.HypixelModule(),new dev.vibe.module.impl.world.BedAuraModule(),
                    new dev.vibe.module.impl.client.ChatterModule(),new dev.vibe.module.impl.movement.AntiAFKModule());
            for(dev.vibe.module.Module module:modules) {
                f.base.modules.registerDynamic(module);
                ItemSwitching switching=(ItemSwitching)module.getClass().getField("switching").get(module);
                assertTrue(module.getSettings().contains(switching.mode));assertTrue(module.getSettings().contains(switching.highlight));assertTrue(module.getSettings().contains(switching.color));
                switching.mode.setValue("Silent");module.setEnabled(true);
                HotbarSwitches.Lease lease=HotbarSwitches.acquire(module,switching,8,HotbarSwitches.BUFF);
                assertEquals(module.getName(),8,HotbarSwitches.highlight().slot);
                module.setEnabled(false);assertFalse(module.getName(),lease.valid());assertEquals(0,HotbarSwitches.selectedSlot());assertNull(HotbarSwitches.highlight());
            }
        }
    }
}
