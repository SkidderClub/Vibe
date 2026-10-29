package dev.vibe.event;

import dev.vibe.module.Module;
import dev.vibe.module.impl.combat.KillAuraModule;
import dev.vibe.module.impl.world.ChestStealerModule;
import dev.vibe.path.PathTestFixture;
import java.util.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChestControlsEventsTest {
    @Test public void buttonsFollowEnabledModulesAndDisableWithoutTogglingAnotherModule()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            KillAuraModule aura=new KillAuraModule();ChestStealerModule stealer=new ChestStealerModule();f.modules.registerDynamic(aura);f.modules.registerDynamic(stealer);
            GuiChest chest=PathTestFixture.allocate(GuiChest.class);chest.width=320;chest.height=240;List<GuiButton> buttons=new ArrayList<>();ChestControlsEvents events=new ChestControlsEvents();
            events.init(new GuiScreenEvent.InitGuiEvent.Post(chest,buttons));assertEquals(2,buttons.size());for(GuiButton b:buttons)assertFalse(b.visible);
            PathTestFixture.set(Module.class,aura,"enabled",true);PathTestFixture.set(Module.class,stealer,"enabled",true);events.draw(new GuiScreenEvent.DrawScreenEvent.Pre(chest,0,0,0));
            assertTrue(buttons.get(0).visible);assertTrue(buttons.get(1).visible);assertEquals(6,buttons.get(0).yPosition);assertTrue(buttons.get(0).xPosition+buttons.get(0).width<=314);
            GuiScreenEvent.ActionPerformedEvent.Pre click=new GuiScreenEvent.ActionPerformedEvent.Pre(chest,buttons.get(0),buttons){@Override public boolean isCancelable(){return true;}};events.click(click);
            assertTrue(click.isCanceled());assertFalse(aura.isEnabled());assertTrue(stealer.isEnabled());assertFalse(buttons.get(0).visible);assertEquals(6,buttons.get(1).yPosition);
            events.click(click);assertFalse(aura.isEnabled());
            assertTrue("Raw clicks must be consumed before the chest handles underlying slots",events.press(chest,buttons.get(1).xPosition+5,buttons.get(1).yPosition+5));assertFalse(stealer.isEnabled());assertFalse(buttons.get(1).visible);
            assertFalse(events.press(chest,10,100));
            events.init(new GuiScreenEvent.InitGuiEvent.Post(chest,buttons));assertEquals(2,buttons.size());
        }
    }
    @Test public void otherInventoryScreensDoNotGetChestButtons()throws Exception{
        GuiScreen inventory=new GuiScreen(){};List<GuiButton> buttons=new ArrayList<>();new ChestControlsEvents().init(new GuiScreenEvent.InitGuiEvent.Post(inventory,buttons));assertTrue(buttons.isEmpty());
    }
}
