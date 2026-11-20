package dev.vibe.inventory;

import dev.vibe.module.Module;
import dev.vibe.module.impl.world.ChestStealerModule;
import dev.vibe.module.impl.world.InventoryManagerModule;
import dev.vibe.setting.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import org.junit.Test;
import static org.junit.Assert.*;

public class InventoryTimingTest {
    @Test public void vanillaModeSortsAndEquipsArmorWithoutOpeningTheScreenOrWaitingAnExtra125ms()throws Exception{
        try(AutomationFixture f=new AutomationFixture()){
            InventoryManagerModule module=new InventoryManagerModule();f.base.modules.registerDynamic(module);
            dev.vibe.module.impl.client.InventoryEditorModule editor=new dev.vibe.module.impl.client.InventoryEditorModule();InventoryLayout layout=editor.getLayout();layout.set(0,InventoryLayout.Type.SWORD);editor.setLayout(layout);f.base.modules.registerDynamic(editor);
            for(Setting<?> s:module.getSettings()){if(s instanceof RangeSetting)((RangeSetting)s).setRange(0,0);if(s.getRawName().equals("Mode"))((ModeSetting)s).setValue("Vanilla");}
            f.player.inventory.setInventorySlotContents(9,new ItemStack(Items.diamond_sword));f.player.inventory.setInventorySlotContents(10,new ItemStack(Items.diamond_helmet));
            module.setEnabled(true);module.tick();assertNull(f.mc.currentScreen);
            assertEquals(Items.diamond_sword,f.player.inventory.getStackInSlot(0).getItem());
            f.player.ticksExisted++;module.tick();assertEquals(Items.diamond_helmet,f.player.inventory.armorInventory[3].getItem());assertNull(f.mc.currentScreen);
            assertEquals(2,f.queue.packets.stream().filter(p->p instanceof C0EPacketClickWindow).count());
            f.player.ticksExisted++;module.tick();assertFalse(PlayerInventorySession.isBusy());module.setEnabled(false);
        }
    }
    @Test public void heldStackIsPlacedAndNeverSwappedForAnotherItem() throws Exception {
        try(AutomationFixture f=new AutomationFixture()){
            f.mc.currentScreen=new GuiInventory(f.player);
            f.player.inventory.setItemStack(new ItemStack(Items.diamond_sword));
            f.player.inventory.setInventorySlotContents(10,new ItemStack(Blocks.dirt));
            ContainerClickDispatcher clicks=new ContainerClickDispatcher(()->5000L);
            assertFalse(clicks.click(f.player.inventoryContainer,10,0,0,0));
            assertFalse(clicks.click(f.player.inventoryContainer,10,0,1,0));
            assertEquals(Items.diamond_sword,f.player.inventory.getItemStack().getItem());
            assertEquals(Item.getItemFromBlock(Blocks.dirt),f.player.inventory.getStackInSlot(10).getItem());
            assertTrue(clicks.click(f.player.inventoryContainer,11,0,0,0));
            assertNull(f.player.inventory.getItemStack());
            assertEquals(Items.diamond_sword,f.player.inventory.getStackInSlot(11).getItem());
        }
    }
    @Test public void managerParksTheOccupantBeforeTakingTheSword() throws Exception {
        try(AutomationFixture f=new AutomationFixture()){
            RecordingController controller=new RecordingController(f);f.mc.playerController=controller;
            InventoryManagerModule module=new InventoryManagerModule();f.base.modules.registerDynamic(module);
            dev.vibe.module.impl.client.InventoryEditorModule editor=new dev.vibe.module.impl.client.InventoryEditorModule();
            InventoryLayout layout=editor.getLayout();layout.set(9,InventoryLayout.Type.SWORD);editor.setLayout(layout);f.base.modules.registerDynamic(editor);
            for(Setting<?> s:module.getSettings()){if(s instanceof RangeSetting)((RangeSetting)s).setRange(0,0);if(s.getRawName().equals("Mode"))((ModeSetting)s).setValue("OpenInv");}
            f.mc.currentScreen=new GuiInventory(f.player);
            f.player.inventory.setInventorySlotContents(9,new ItemStack(Blocks.dirt));
            f.player.inventory.setInventorySlotContents(10,new ItemStack(Items.diamond_sword));
            module.setEnabled(true);module.tick();
            assertEquals(1,controller.clicks.size());
            assertArrayEquals(new int[]{9,0,0},controller.clicks.get(0));
            assertEquals(Item.getItemFromBlock(Blocks.dirt),f.player.inventory.getItemStack().getItem());
            f.player.ticksExisted++;module.tick();
            assertEquals(2,controller.clicks.size());
            assertArrayEquals(new int[]{11,0,0},controller.clicks.get(1));
            assertNull(f.player.inventory.getItemStack());
            assertEquals(Items.diamond_sword,f.player.inventory.getStackInSlot(10).getItem());
            module.setEnabled(false);
        }
    }
    @Test public void chestStealerPutsDownAHeldItemAndSkipsAFullInventory() throws Exception {
        try(AutomationFixture f=new AutomationFixture()){
            RecordingController controller=new RecordingController(f);f.mc.playerController=controller;
            InventoryBasic chestInv=new InventoryBasic("Chest",true,27);
            chestInv.setInventorySlotContents(0,new ItemStack(Items.diamond_sword));
            GuiChest gui=new GuiChest(f.player.inventory,chestInv);
            f.mc.currentScreen=gui;f.player.openContainer=gui.inventorySlots;gui.inventorySlots.windowId=2;
            f.player.inventory.setItemStack(new ItemStack(Items.diamond_helmet));
            ChestStealerModule module=new ChestStealerModule();
            for(Setting<?> s:module.getSettings()){if(s instanceof RangeSetting)((RangeSetting)s).setRange(0,0);if(s.getRawName().equals("Auto Close"))((BooleanSetting)s).setEnabled(false);}
            module.setEnabled(true);module.tick();
            assertEquals(1,controller.clicks.size());
            assertEquals(0,controller.clicks.get(0)[1]);assertEquals(0,controller.clicks.get(0)[2]);
            assertTrue(controller.clicks.get(0)[0]>=27);
            assertNull(f.player.inventory.getItemStack());
            f.player.ticksExisted++;module.tick();
            assertEquals(2,controller.clicks.size());
            assertArrayEquals(new int[]{0,0,1},controller.clicks.get(1));
            chestInv.setInventorySlotContents(0,new ItemStack(Items.diamond_sword));
            for(int slot=0;slot<36;slot++)f.player.inventory.setInventorySlotContents(slot,new ItemStack(Blocks.cobblestone));
            controller.clicks.clear();f.player.ticksExisted++;module.tick();
            assertTrue(controller.clicks.isEmpty());
            module.setEnabled(false);
        }
    }
    private static final class RecordingController extends PlayerControllerMP {
        final List<int[]> clicks=new ArrayList<int[]>();
        RecordingController(AutomationFixture f){super(f.mc,f.queue);}
        @Override public ItemStack windowClick(int window,int slot,int button,int mode,EntityPlayer player){
            clicks.add(new int[]{slot,button,mode});return super.windowClick(window,slot,button,mode,player);
        }
    }
    @Test public void fullInventoryThrowsTheCarriedStackInsteadOfSwapping() throws Exception {
        try(AutomationFixture f=new AutomationFixture()){
            RecordingController controller=new RecordingController(f);f.mc.playerController=controller;
            InventoryManagerModule module=new InventoryManagerModule();f.base.modules.registerDynamic(module);
            for(Setting<?> s:module.getSettings()){if(s instanceof RangeSetting)((RangeSetting)s).setRange(0,0);if(s.getRawName().equals("Mode"))((ModeSetting)s).setValue("OpenInv");}
            f.mc.currentScreen=new GuiInventory(f.player);
            for(int slot=0;slot<36;slot++)f.player.inventory.setInventorySlotContents(slot,new ItemStack(Blocks.cobblestone,64));
            f.player.inventory.setItemStack(new ItemStack(Items.diamond_sword));
            module.setEnabled(true);module.tick();
            assertEquals(1,controller.clicks.size());
            assertArrayEquals(new int[]{-999,0,0},controller.clicks.get(0));
            assertNull(f.player.inventory.getItemStack());
            assertEquals(Items.diamond_sword,f.player.droppedStacks.get(0).getItem());
            assertEquals(Item.getItemFromBlock(Blocks.cobblestone),f.player.inventory.getStackInSlot(9).getItem());
            f.player.inventory.setInventorySlotContents(9,new ItemStack(Blocks.cobblestone,32));
            f.player.inventory.setItemStack(new ItemStack(Blocks.cobblestone,10));
            controller.clicks.clear();f.player.droppedStacks.clear();f.player.ticksExisted++;module.tick();
            assertEquals(1,controller.clicks.size());
            assertArrayEquals(new int[]{9,0,0},controller.clicks.get(0));
            assertNull(f.player.inventory.getItemStack());
            assertTrue(f.player.droppedStacks.isEmpty());
            assertEquals(42,f.player.inventory.getStackInSlot(9).stackSize);
            module.setEnabled(false);
        }
    }
    @Test public void aClickThatDoesNotMoveTheStackIsNotRepeatedImmediately() throws Exception {
        try(AutomationFixture f=new AutomationFixture()){
            SwallowController controller=new SwallowController(f);f.mc.playerController=controller;
            InventoryManagerModule module=new InventoryManagerModule();f.base.modules.registerDynamic(module);
            dev.vibe.module.impl.client.InventoryEditorModule editor=new dev.vibe.module.impl.client.InventoryEditorModule();
            InventoryLayout layout=editor.getLayout();layout.set(0,InventoryLayout.Type.SWORD);editor.setLayout(layout);f.base.modules.registerDynamic(editor);
            for(Setting<?> s:module.getSettings()){if(s instanceof RangeSetting)((RangeSetting)s).setRange(0,0);if(s.getRawName().equals("Mode"))((ModeSetting)s).setValue("Vanilla");}
            f.player.inventory.setInventorySlotContents(9,new ItemStack(Items.diamond_sword));
            module.setEnabled(true);module.tick();module.tick();
            assertEquals(1,controller.clicks.size());
            assertArrayEquals(new int[]{9,0,2},controller.clicks.get(0));
            assertEquals(Items.diamond_sword,f.player.inventory.getStackInSlot(9).getItem());
            module.setEnabled(false);
        }
    }
    @Test public void vanillaModePutsDownAHeldStackBeforeSortingAnythingElse() throws Exception {
        try(AutomationFixture f=new AutomationFixture()){
            RecordingController controller=new RecordingController(f);f.mc.playerController=controller;
            InventoryManagerModule module=new InventoryManagerModule();f.base.modules.registerDynamic(module);
            for(Setting<?> s:module.getSettings()){if(s instanceof RangeSetting)((RangeSetting)s).setRange(0,0);if(s.getRawName().equals("Mode"))((ModeSetting)s).setValue("Vanilla");}
            f.player.inventory.setItemStack(new ItemStack(Items.diamond_sword));
            module.setEnabled(true);module.tick();
            assertEquals(1,controller.clicks.size());
            assertEquals(0,controller.clicks.get(0)[2]);
            assertNull(f.player.inventory.getItemStack());
            assertEquals(Items.diamond_sword,f.player.inventory.getStackInSlot(9).getItem());
            assertNull(f.mc.currentScreen);
            f.player.inventory.setItemStack(new ItemStack(Items.diamond_helmet));
            controller.clicks.clear();f.player.ticksExisted++;module.tick();
            assertEquals(1,controller.clicks.size());
            assertArrayEquals(new int[]{5,0,0},controller.clicks.get(0));
            assertNull(f.player.inventory.getItemStack());
            assertEquals(Items.diamond_helmet,f.player.inventory.armorInventory[3].getItem());
            module.setEnabled(false);
        }
    }
    private static final class SwallowController extends PlayerControllerMP {
        final List<int[]> clicks=new ArrayList<int[]>();
        SwallowController(AutomationFixture f){super(f.mc,f.queue);}
        @Override public ItemStack windowClick(int window,int slot,int button,int mode,EntityPlayer player){
            clicks.add(new int[]{slot,button,mode});return null;
        }
    }
    @Test public void zeroDelayStillSerializesClicksPerTickButHonorsConfiguredLongDelays()throws Exception{
        try(AutomationFixture f=new AutomationFixture()){
            f.mc.currentScreen=new GuiInventory(f.player);long[] now={1000};ContainerClickDispatcher clicks=new ContainerClickDispatcher(()->now[0]);
            assertTrue(clicks.click(f.player.inventoryContainer,9,0,0,0));assertFalse(clicks.click(f.player.inventoryContainer,10,0,0,0));
            f.player.ticksExisted++;assertTrue(clicks.click(f.player.inventoryContainer,10,0,0,300));
            f.player.ticksExisted++;now[0]+=299;assertFalse(clicks.click(f.player.inventoryContainer,11,0,0,0));
            now[0]++;assertTrue(clicks.click(f.player.inventoryContainer,11,0,0,0));
        }
    }
}
