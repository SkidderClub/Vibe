package dev.vibe.inventory;

import dev.vibe.command.CommandManager;
import dev.vibe.module.impl.combat.AutoBuffModule;
import dev.vibe.path.PathTestFixture;
import java.util.*;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.*;
import net.minecraft.item.*;
import net.minecraft.network.play.client.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class InventoryDropperTest {
    private static final String[] ALIASES={".vomit",".clear",".clearinv",".clearinventory"};

    @Test public void everyAliasDropsWholeInventoryArmorAndCraftingInputsWithRealControllerClicks() throws Exception {
        for(String alias:ALIASES)try(AutomationFixture f=new AutomationFixture()) {
            long[] now={10000};Controller controller=new Controller(f,now);f.mc.playerController=controller;
            for(int i=0;i<36;i++)f.player.inventory.setInventorySlotContents(i,new ItemStack(Items.diamond,64));
            Item[] armor={Items.diamond_boots,Items.diamond_leggings,Items.diamond_chestplate,Items.diamond_helmet};
            for(int i=0;i<4;i++)f.player.inventory.armorInventory[i]=new ItemStack(armor[i]);
            ContainerPlayer inventory=(ContainerPlayer)f.player.inventoryContainer;
            for(int i=0;i<4;i++)inventory.craftMatrix.setInventorySlotContents(i,new ItemStack(Items.feather,2));
            List<String> messages=new ArrayList<String>();CommandManager commands=commands(now,messages);
            assertTrue(commands.execute(alias.toUpperCase(Locale.ROOT)));
            for(int i=0;i<200;i++){commands.tick();now[0]+=50;f.player.ticksExisted++;}
            for(ItemStack stack:f.player.inventory.mainInventory)assertNull(stack);
            for(ItemStack stack:f.player.inventory.armorInventory)assertNull(stack);
            for(int i=0;i<4;i++)assertNull(inventory.craftMatrix.getStackInSlot(i));
            assertEquals(44,f.player.droppedStacks.size());
            assertEquals(36*64+4+4*2,f.player.droppedStacks.stream().mapToInt(s->s.stackSize).sum());
            assertEquals(44,controller.slots.size());assertEquals(44,new HashSet<Integer>(controller.slots).size());
            assertFalse(controller.slots.contains(0));for(int i=1;i<=44;i++)assertTrue(controller.slots.contains(i));
            for(int i=1;i<controller.times.size();i++)assertTrue(controller.times.get(i)-controller.times.get(i-1)>=125);
            assertTrue(f.queue.packets.get(0) instanceof C16PacketClientStatus);
            assertEquals(44,f.queue.packets.stream().filter(p->p instanceof C0EPacketClickWindow).count());
            assertEquals(1,f.player.closes);assertFalse(PlayerInventorySession.isBusy());assertNull(f.player.inventory.getItemStack());
            assertEquals("Dropped 44 stacks from your inventory.",messages.get(messages.size()-1));
            assertTrue(commands.getSuggestions(alias).contains(alias));
        }
    }

    @Test public void waitsForChatToCloseAndCancelsWithoutTouchingAChest() throws Exception {
        try(AutomationFixture f=new AutomationFixture()) {
            long[] now={20000};Controller controller=new Controller(f,now);f.mc.playerController=controller;
            f.player.inventory.setInventorySlotContents(9,new ItemStack(Items.diamond,32));
            f.player.inventory.setInventorySlotContents(0,new ItemStack(Items.diamond_sword));
            List<String> messages=new ArrayList<String>();CommandManager commands=commands(now,messages);
            f.mc.currentScreen=new GuiChat();commands.execute(".clear");commands.tick();assertTrue(f.queue.packets.isEmpty());
            commands.execute(".vomit");assertEquals("Inventory clearing is already running.",messages.get(messages.size()-1));
            f.mc.currentScreen=null;commands.tick();assertEquals(1,controller.slots.size());
            InventoryBasic chest=new InventoryBasic("Chest",false,9);chest.setInventorySlotContents(0,new ItemStack(Items.emerald,64));
            GuiChest gui=new GuiChest(f.player.inventory,chest);f.mc.currentScreen=gui;f.player.openContainer=gui.inventorySlots;
            now[0]+=200;f.player.ticksExisted+=4;commands.tick();
            assertEquals(1,controller.slots.size());assertEquals(64,chest.getStackInSlot(0).stackSize);
            assertNotNull(f.player.inventory.getStackInSlot(0));assertFalse(PlayerInventorySession.isBusy());
            assertTrue(messages.get(messages.size()-1).contains("cancelled"));
        }
    }

    @Test public void worldChangesCancelAndARecollectedStackIsNotDroppedAgain() throws Exception {
        try(AutomationFixture f=new AutomationFixture()) {
            long[] now={30000};List<String> messages=new ArrayList<String>();CommandManager commands=commands(now,messages);
            f.player.inventory.setInventorySlotContents(9,new ItemStack(Items.diamond,64));
            f.player.inventory.setInventorySlotContents(10,new ItemStack(Items.emerald,32));
            commands.execute(".clearinv");commands.tick();
            f.player.inventory.setInventorySlotContents(9,new ItemStack(Items.diamond,64));
            now[0]+=200;f.player.ticksExisted+=4;commands.tick();commands.tick();
            assertEquals(2,f.player.droppedStacks.size());assertNotNull(f.player.inventory.getStackInSlot(9));assertFalse(PlayerInventorySession.isBusy());
            f.player.inventory.setInventorySlotContents(10,new ItemStack(Items.emerald,32));
            commands.execute(".clearinv");now[0]+=200;f.player.ticksExisted+=4;commands.tick();
            f.mc.theWorld=null;commands.tick();f.mc.theWorld=f.base.world;now[0]+=200;f.player.ticksExisted+=4;commands.tick();
            assertEquals(3,f.player.droppedStacks.size());assertEquals(32,f.player.inventory.getStackInSlot(10).stackSize);
            assertFalse(PlayerInventorySession.isBusy());assertTrue(messages.get(messages.size()-1).contains("cancelled"));
        }
    }

    @Test public void syntaxEmptyInventoryCursorAndHelpAreHandledLocally() throws Exception {
        try(AutomationFixture f=new AutomationFixture()) {
            long[] now={40000};List<String> messages=new ArrayList<String>();CommandManager commands=commands(now,messages);
            commands.execute(".vomit");assertEquals("Your inventory is already empty.",messages.get(0));
            f.player.inventory.setInventorySlotContents(0,new ItemStack(Items.diamond,64));
            commands.execute(".clear extra");commands.tick();assertTrue(f.queue.packets.isEmpty());
            f.player.inventory.setItemStack(new ItemStack(Items.emerald));commands.execute(".clearinventory");commands.tick();assertTrue(f.queue.packets.isEmpty());
            assertTrue(messages.get(messages.size()-1).contains("cursor"));f.player.inventory.setItemStack(null);
            commands.execute(".help");assertTrue(f.player.messages.stream().anyMatch(m->m.getUnformattedText().contains(".clearinventory")&&m.getUnformattedText().contains("armor")));
            for(String alias:ALIASES)assertTrue(commands.getSuggestions(".").contains(alias));
            assertFalse(commands.execute("/clear"));
            f.mc.theWorld=null;commands.execute(".clear");assertTrue(messages.get(messages.size()-1).contains("Join a world"));
        }
    }

    @Test public void activeEatingYieldsToTheDropSession() throws Exception {
        try(AutomationFixture f=new AutomationFixture()) {
            AutoBuffModule buff=new AutoBuffModule();f.base.modules.registerDynamic(buff);
            buff.refill.setEnabled(false);buff.modes.setValue(Collections.singleton("Auto Eat"));buff.delays.get(AutoBuffModule.Kind.EAT).setRange(0,0);
            f.player.getFoodStats().setFoodLevel(10);f.player.inventory.setInventorySlotContents(8,new ItemStack(Items.bread,8));
            f.player.inventory.setInventorySlotContents(9,new ItemStack(Items.diamond,64));buff.setEnabled(true);buff.tick(1000);buff.tick(1000);
            assertTrue(AutoBuffModule.ownsUseInput());
            long[] now={50000};CommandManager commands=commands(now,new ArrayList<String>());commands.execute(".vomit");commands.tick();
            assertTrue(PlayerInventorySession.isBusy());buff.tick(1100);assertFalse(AutoBuffModule.ownsUseInput());
            assertTrue(PlayerInventorySession.isBusy());assertEquals(0,f.player.inventory.currentItem);
            now[0]+=200;f.player.ticksExisted+=4;commands.tick();assertFalse(PlayerInventorySession.isBusy());buff.setEnabled(false);
        }
    }

    private static CommandManager commands(long[] now,List<String> messages) throws Exception {
        CommandManager commands=new CommandManager();
        PathTestFixture.set(CommandManager.class,commands,"inventoryDropper",new InventoryDropper(messages::add,()->now[0]));return commands;
    }
    private static final class Controller extends PlayerControllerMP {
        final long[] now;final List<Integer> slots=new ArrayList<Integer>();final List<Long> times=new ArrayList<Long>();
        Controller(AutomationFixture f,long[] now){super(f.mc,f.queue);this.now=now;}
        @Override public ItemStack windowClick(int window,int slot,int button,int mode,EntityPlayer player) {
            assertEquals(player.inventoryContainer,player.openContainer);assertEquals(0,window);assertEquals(1,button);assertEquals(4,mode);
            slots.add(slot);times.add(now[0]);return super.windowClick(window,slot,button,mode,player);
        }
    }
}
