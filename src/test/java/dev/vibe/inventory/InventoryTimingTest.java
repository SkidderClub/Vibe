package dev.vibe.inventory;

import dev.vibe.module.Module;
import dev.vibe.module.impl.world.InventoryManagerModule;
import dev.vibe.setting.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.client.gui.inventory.GuiInventory;
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
