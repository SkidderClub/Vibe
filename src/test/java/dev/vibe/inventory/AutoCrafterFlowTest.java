package dev.vibe.inventory;

import dev.vibe.module.impl.world.AutoCrafterModule;
import dev.vibe.path.PathTestFixture;
import java.util.*;
import net.minecraft.client.gui.inventory.GuiCrafting;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.*;
import net.minecraft.inventory.*;
import net.minecraft.item.*;
import net.minecraft.util.BlockPos;
import org.junit.Test;
import static org.junit.Assert.*;

public class AutoCrafterFlowTest {
    @Test public void delayedControllerFlowActuallyBuildsSticksThenOneSwordWithoutLosingMaterials() throws Exception {
        try(AutomationFixture f=new AutomationFixture()) {
            long[] now={10000};
            Controller controller=new Controller(f,now);f.mc.playerController=controller;
            GuiCrafting gui=new GuiCrafting(f.player.inventory,f.mc.theWorld,new BlockPos(0,64,0));
            f.mc.currentScreen=gui;f.player.openContainer=gui.inventorySlots;gui.inventorySlots.windowId=3;
            f.player.inventory.setInventorySlotContents(9,new ItemStack(Items.diamond,20));
            f.player.inventory.setInventorySlotContents(10,new ItemStack(Blocks.planks,2));
            AutoCrafterModule module=new AutoCrafterModule();module.gear.setValue(new LinkedHashSet<String>(Collections.singleton("Sword")));
            module.startDelay.setRange(0,0);module.clickDelay.setRange(125,125);module.craftDelay.setRange(0,0);module.recipeDelay.setRange(125,125);
            PathTestFixture.set(AutoCrafterModule.class,module,"clicks",new ContainerClickDispatcher(()->now[0]));module.setEnabled(true);
            for(int i=0;i<100;i++){module.tick(now[0]);now[0]+=200;f.player.ticksExisted+=4;}
            assertEquals(Arrays.asList(Items.stick,Items.diamond_sword),controller.outputs);
            assertEquals(18,count(f,Items.diamond));assertEquals(3,count(f,Items.stick));assertEquals(1,count(f,Items.diamond_sword));
            assertNull(f.player.inventory.getItemStack());
            for(int i=1;i<=9;i++)assertNull(f.player.openContainer.getSlot(i).getStack());
            for(int i=1;i<controller.times.size();i++)assertTrue(controller.times.get(i)-controller.times.get(i-1)>=125);
            assertTrue(controller.rightPlacements>=5);module.setEnabled(false);
        }
    }
    @Test public void manuallyPopulatedCraftingGridIsLeftUntouched() throws Exception {
        try(AutomationFixture f=new AutomationFixture()) {
            long[] now={10000};Controller controller=new Controller(f,now);f.mc.playerController=controller;
            GuiCrafting gui=new GuiCrafting(f.player.inventory,f.mc.theWorld,new BlockPos(0,64,0));f.mc.currentScreen=gui;f.player.openContainer=gui.inventorySlots;
            ((ContainerWorkbench)gui.inventorySlots).craftMatrix.setInventorySlotContents(0,new ItemStack(Items.apple));
            f.player.inventory.setInventorySlotContents(9,new ItemStack(Items.diamond,20));f.player.inventory.setInventorySlotContents(10,new ItemStack(Items.stick));
            AutoCrafterModule module=new AutoCrafterModule();module.startDelay.setRange(0,0);module.setEnabled(true);
            for(int i=0;i<10;i++){module.tick(now[0]);now[0]+=500;f.player.ticksExisted+=10;}
            assertTrue(controller.times.isEmpty());assertEquals(Items.apple,gui.inventorySlots.getSlot(1).getStack().getItem());module.setEnabled(false);
        }
    }
    @Test public void oneLogBecomesPlanksSticksAndExactlyOneWoodenSword() throws Exception {
        try(AutomationFixture f=new AutomationFixture()) {
            long[] now={10000};Controller controller=new Controller(f,now);f.mc.playerController=controller;
            GuiCrafting gui=new GuiCrafting(f.player.inventory,f.mc.theWorld,new BlockPos(0,64,0));
            f.mc.currentScreen=gui;f.player.openContainer=gui.inventorySlots;
            f.player.inventory.setInventorySlotContents(9,new ItemStack(Blocks.log));
            AutoCrafterModule module=new AutoCrafterModule();module.material.setValue("Wood");
            module.gear.setValue(new LinkedHashSet<String>(Collections.singleton("Sword")));
            module.startDelay.setRange(0,0);module.craftDelay.setRange(0,0);
            PathTestFixture.set(AutoCrafterModule.class,module,"clicks",new ContainerClickDispatcher(()->now[0]));module.setEnabled(true);
            for(int i=0;i<100;i++){module.tick(now[0]);now[0]+=500;f.player.ticksExisted+=10;}
            assertEquals(Arrays.asList(Item.getItemFromBlock(Blocks.planks),Items.stick,Items.wooden_sword),controller.outputs);
            assertEquals(1,count(f,Items.wooden_sword));assertEquals(3,count(f,Items.stick));
            assertEquals(0,count(f,Item.getItemFromBlock(Blocks.planks)));assertEquals(0,count(f,Item.getItemFromBlock(Blocks.log)));
            module.setEnabled(false);
        }
    }
    private static int count(AutomationFixture f,Item item){int n=0;for(ItemStack s:f.player.inventory.mainInventory)if(s!=null&&s.getItem()==item)n+=s.stackSize;return n;}
    private static class Controller extends PlayerControllerMP {
        final AutomationFixture fixture;final long[] now;
        final List<Long> times=new ArrayList<Long>();final List<Item> outputs=new ArrayList<Item>();int rightPlacements;
        Controller(AutomationFixture f,long[] now){super(f.mc,f.queue);fixture=f;this.now=now;}
        @Override public ItemStack windowClick(int window,int slot,int button,int mode,EntityPlayer player){
            assertEquals(player.openContainer.windowId,window);times.add(now[0]);
            if(slot==0&&mode==1){
                // Use the real matched output; avoid Forge's global crafting-event/statistics
                // bootstrap in a no-LaunchClassLoader unit fixture.
                ContainerWorkbench table=(ContainerWorkbench)player.openContainer;ItemStack out=table.getSlot(0).getStack().copy();
                outputs.add(out.getItem());assertTrue(player.inventory.addItemStackToInventory(out.copy()));
                for(int i=0;i<9;i++)if(table.craftMatrix.getStackInSlot(i)!=null)table.craftMatrix.decrStackSize(i,1);
                return out;
            }
            if(button==1&&mode==0)rightPlacements++;
            return super.windowClick(window,slot,button,mode,player);
        }
    }
}
