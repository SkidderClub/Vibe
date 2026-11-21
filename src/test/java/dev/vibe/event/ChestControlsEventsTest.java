package dev.vibe.event;

import dev.vibe.module.Module;
import dev.vibe.module.impl.combat.KillAuraModule;
import dev.vibe.module.impl.world.ChestStealerModule;
import dev.vibe.path.PathTestFixture;
import java.util.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChestControlsEventsTest {
    @Test public void buttonsFollowEnabledModulesAndDisableWithoutTogglingAnotherModule()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            KillAuraModule aura=new KillAuraModule();ChestStealerModule stealer=new ChestStealerModule();f.modules.registerDynamic(aura);f.modules.registerDynamic(stealer);
            GuiChest chest=PathTestFixture.allocate(GuiChest.class);chest.width=320;chest.height=240;List<GuiButton> buttons=new ArrayList<>();ChestControlsEvents events=new ChestControlsEvents();
            events.init(new GuiScreenEvent.InitGuiEvent.Post(chest,buttons));assertEquals(4,buttons.size());
            GuiButton close=buttons.get(0),auraButton=buttons.get(1),stealerButton=buttons.get(2),copy=buttons.get(3);
            assertFalse(close.visible);assertFalse(auraButton.visible);assertFalse(stealerButton.visible);assertTrue(copy.visible);assertEquals("Copy Chest NBT",copy.displayString);
            PathTestFixture.set(Module.class,aura,"enabled",true);PathTestFixture.set(Module.class,stealer,"enabled",true);events.draw(new GuiScreenEvent.DrawScreenEvent.Pre(chest,0,0,0));
            assertTrue(auraButton.visible);assertTrue(stealerButton.visible);assertEquals(8,auraButton.yPosition);assertTrue(auraButton.xPosition+auraButton.width<=312);
            GuiScreenEvent.ActionPerformedEvent.Pre click=new GuiScreenEvent.ActionPerformedEvent.Pre(chest,auraButton,buttons){@Override public boolean isCancelable(){return true;}};events.click(click);
            assertTrue(click.isCanceled());assertFalse(aura.isEnabled());assertTrue(stealer.isEnabled());assertFalse(auraButton.visible);assertEquals(8,stealerButton.yPosition);
            events.click(click);assertFalse(aura.isEnabled());
            assertTrue("Raw clicks must be consumed before the chest handles underlying slots",events.press(chest,stealerButton.xPosition+5,stealerButton.yPosition+5));assertFalse(stealer.isEnabled());assertFalse(stealerButton.visible);
            assertFalse(events.press(chest,10,100));
            events.init(new GuiScreenEvent.InitGuiEvent.Post(chest,buttons));assertEquals(4,buttons.size());
        }
    }
    @Test public void otherInventoryScreensDoNotGetChestButtons()throws Exception{
        GuiScreen inventory=new GuiScreen(){};List<GuiButton> buttons=new ArrayList<>();new ChestControlsEvents().init(new GuiScreenEvent.InitGuiEvent.Post(inventory,buttons));assertTrue(buttons.isEmpty());
    }
    @Test public void playerInventoryGetsOnlyItsOwnCornerActions() throws Exception {
        try (PathTestFixture f = new PathTestFixture()) {
            GuiInventory inventory = PathTestFixture.allocate(GuiInventory.class);
            inventory.width = 320;
            List<GuiButton> buttons = new ArrayList<>();
            new ChestControlsEvents().init(new GuiScreenEvent.InitGuiEvent.Post(inventory, buttons));
            assertEquals(2, buttons.size());
            assertEquals(ChestControlsEvents.AURA, buttons.get(0).id);
            assertEquals("Copy Inventory NBT", buttons.get(1).displayString);
        }
    }
}
