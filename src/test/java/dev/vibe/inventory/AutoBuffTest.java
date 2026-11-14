package dev.vibe.inventory;

import dev.vibe.module.impl.combat.*;
import dev.vibe.module.impl.combat.AutoBuffModule.Kind;
import dev.vibe.module.impl.client.InventoryEditorModule;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.inventory.InventoryLayout.Type;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AutoBuffTest {
    @Test public void headUsesOneDelayAndRestoresInTheSameNativeInputPassWithoutServerConfirmation() throws Exception {
        try (AutomationFixture f=new AutomationFixture()) {
            AutoBuffModule m=new AutoBuffModule();f.base.modules.registerDynamic(m);
            m.modes.setValue(java.util.Collections.singleton(Kind.HEAD.label));m.refill.setEnabled(false);
            m.delays.get(Kind.HEAD).setRange(200,200);m.headPause.setRange(600,600);
            m.switchDelay.setRange(500,500);m.restoreDelay.setRange(1000,1000);
            assertFalse(m.switchDelay.isVisible());assertFalse(m.restoreDelay.isVisible());
            f.player.inventory.mainInventory[0]=new ItemStack(Items.diamond_sword);
            f.player.inventory.mainInventory[8]=new ItemStack(Items.skull,3,3);f.health(10);
            f.mc.playerController=dev.vibe.combat.SilentMurderPacketsTest.nativeController(f.mc,f.queue);
            f.mc.playerController.updateController();f.queue.packets.clear();m.setEnabled(true);
            m.tick(1000);m.tick(1199);assertEquals(0,f.player.inventory.currentItem);
            m.tick(1200);AutoBuffModule.prepareInputHook();assertTrue(f.mc.gameSettings.keyBindUseItem.isPressed());
            // The unmodified stack returns false locally; the server still receives its C08.
            f.mc.playerController.sendUseItem(f.player,f.mc.theWorld,f.player.getHeldItem());
            assertEquals(8,f.player.inventory.currentItem);AutoBuffModule.finishInputHook(1200);
            assertEquals(0,f.player.inventory.currentItem);assertFalse(AutoBuffModule.ownsUseInput());
            assertEquals(3,f.player.inventory.mainInventory[8].stackSize);assertEquals(10,f.player.getHealth(),0);
            assertEquals(3,f.queue.packets.size());assertEquals(8,((C09PacketHeldItemChange)f.queue.packets.get(0)).getSlotId());
            assertTrue(f.queue.packets.get(1) instanceof C08PacketPlayerBlockPlacement);assertEquals(0,((C09PacketHeldItemChange)f.queue.packets.get(2)).getSlotId());
            HotbarSwitches.finishInputHook();m.tick(1799);assertEquals(0,f.player.inventory.currentItem);
            m.tick(1800);m.tick(1999);assertEquals(0,f.player.inventory.currentItem);m.tick(2000);assertTrue(AutoBuffModule.ownsUseInput());
            m.setEnabled(false);assertEquals(0,f.player.inventory.currentItem);
        }
    }
    @Test public void instantHeadRestoresThePreviousSilentToolAndEndsNamedFoodHeadUse() throws Exception {
        for (boolean food : new boolean[]{false,true}) try (AutomationFixture f=new AutomationFixture()) {
            AutoBuffModule m=new AutoBuffModule();dev.vibe.module.impl.world.AutoToolModule tool=new dev.vibe.module.impl.world.AutoToolModule();
            f.base.modules.registerDynamic(m);f.base.modules.registerDynamic(tool);tool.setEnabled(true);tool.switching.mode.setValue("Silent");
            m.switching.mode.setValue("Silent");m.modes.setValue(java.util.Collections.singleton(Kind.HEAD.label));m.refill.setEnabled(false);m.delays.get(Kind.HEAD).setRange(0,0);
            f.player.inventory.mainInventory[0]=new ItemStack(Items.diamond_sword);f.player.inventory.mainInventory[2]=new ItemStack(Items.iron_pickaxe);
            f.player.inventory.mainInventory[8]=food?new ItemStack(Items.golden_apple).setStackDisplayName("Golden Head"):new ItemStack(Items.skull,1,3);
            f.health(10);f.mc.playerController=dev.vibe.combat.SilentMurderPacketsTest.nativeController(f.mc,f.queue);
            HotbarSwitches.acquire(tool,tool.switching,2,HotbarSwitches.TOOL);m.setEnabled(true);m.tick(1000);m.tick(1000);AutoBuffModule.prepareInputHook();
            f.mc.playerController.sendUseItem(f.player,f.mc.theWorld,f.player.getHeldItem());
            AutoBuffModule.finishInputHook(1000);HotbarSwitches.finishInputHook();
            assertEquals(0,f.player.inventory.currentItem);assertEquals(2,dev.vibe.module.impl.world.AutoToolModule.serverSlotHook(0));
            assertFalse(f.player.isUsingItem());assertFalse(AutoBuffModule.ownsUseInput());
            assertFalse(f.mc.gameSettings.keyBindUseItem.isKeyDown());
            m.setEnabled(false);tool.setEnabled(false);
        }
    }
    @Test public void backwardEatingUsesNativeMovementAndRespectsIndependentOptionsAndCleanup() throws Exception {
        for (Kind kind : new Kind[]{Kind.EAT,Kind.GAPPLE}) try (AutomationFixture f=new AutomationFixture()) {
            AutoBuffModule m=new AutoBuffModule();MoveFixModule fix=new MoveFixModule();
            f.base.modules.registerDynamic(m);f.base.modules.registerDynamic(fix);
            m.modes.setValue(java.util.Collections.singleton(kind.label));m.refill.setEnabled(false);
            m.delays.get(kind).setRange(0,0);m.switchDelay.setRange(0,0);m.restoreDelay.setRange(0,0);
            dev.vibe.setting.BooleanSetting option=kind==Kind.EAT?m.eatWalkBackwards:m.gappleWalkBackwards;
            dev.vibe.setting.BooleanSetting other=kind==Kind.EAT?m.gappleWalkBackwards:m.eatWalkBackwards;
            assertFalse(option.isEnabled());other.setEnabled(true);
            f.player.movementInput=new net.minecraft.util.MovementInput() {
                @Override public void updatePlayerMoveState(){moveForward=1;moveStrafe=0;jump=true;sneak=false;}
            };
            fix.installInputHook();
            f.player.inventory.setInventorySlotContents(8,new ItemStack(kind==Kind.EAT?Items.bread:Items.golden_apple));
            f.health(10);f.player.getFoodStats().setFoodLevel(12);m.setEnabled(true);
            m.tick(1000);m.tick(1000);option.setEnabled(true);
            f.player.movementInput.updatePlayerMoveState();assertEquals("No walking back during slot selection",1,f.player.movementInput.moveForward,0);
            f.player.ticksExisted+=2;m.tick(1100);AutoBuffModule.prepareInputHook();
            f.player.movementInput.updatePlayerMoveState();assertEquals("Wait for actual eating",1,f.player.movementInput.moveForward,0);
            f.mc.playerController.sendUseItem(f.player,f.mc.theWorld,f.player.getHeldItem());assertTrue(f.player.isUsingItem());
            option.setEnabled(false);f.player.movementInput.updatePlayerMoveState();assertEquals("The other mode's setting cannot activate this mode",1,f.player.movementInput.moveForward,0);
            option.setEnabled(true);f.player.movementInput.updatePlayerMoveState();
            assertEquals(-1,f.player.movementInput.moveForward,0);assertEquals(0,f.player.movementInput.moveStrafe,0);assertTrue(f.player.movementInput.jump);
            fix.setFakeRotation("test",90,0,"Silent");f.player.movementInput.updatePlayerMoveState();
            assertEquals("Backward world direction survives silent rotation",0,f.player.movementInput.moveForward,0);
            assertEquals(-1,f.player.movementInput.moveStrafe,0);fix.discardOwnedState("test");
            f.mc.currentScreen=new net.minecraft.client.gui.GuiScreen() {};f.player.movementInput.updatePlayerMoveState();assertEquals(1,f.player.movementInput.moveForward,0);f.mc.currentScreen=null;
            f.player.inventory.currentItem=0;f.player.movementInput.updatePlayerMoveState();assertEquals(1,f.player.movementInput.moveForward,0);f.player.inventory.currentItem=8;
            f.player.clearItemInUse();m.tick(1150);f.player.movementInput.updatePlayerMoveState();assertEquals("Eating stopped",1,f.player.movementInput.moveForward,0);
            m.setEnabled(false);f.player.movementInput.updatePlayerMoveState();assertEquals("No synthetic key remains held",1,f.player.movementInput.moveForward,0);
        }
    }

    @Test public void perModeHealthHungerAndBeneficialPotionChecksAreIndependent() throws Exception {
        try(AutomationFixture f=new AutomationFixture()) {
            AutoBuffModule m=new AutoBuffModule();
            f.health(15);assertFalse(m.shouldUse(Kind.SOUP,new ItemStack(Items.mushroom_stew)));
            f.health(10);assertTrue(m.shouldUse(Kind.SOUP,new ItemStack(Items.mushroom_stew)));
            assertTrue(m.shouldUse(Kind.HEAD,new ItemStack(Items.skull,1,3)));
            assertTrue(m.shouldUse(Kind.POT,new ItemStack(Items.potionitem,1,16421)));
            assertFalse(m.shouldUse(Kind.POT,new ItemStack(Items.potionitem,1,16428)));
            f.player.getFoodStats().setFoodLevel(20);assertFalse(m.shouldUse(Kind.EAT,new ItemStack(Items.bread)));
            f.player.getFoodStats().setFoodLevel(12);assertTrue(m.shouldUse(Kind.EAT,new ItemStack(Items.bread)));
            ItemStack head=new ItemStack(Items.golden_apple);head.setStackDisplayName("Golden Head");
            assertTrue(AutoBuffModule.matches(Kind.HEAD,head));assertFalse(AutoBuffModule.matches(Kind.GAPPLE,head));
        }
    }
    @Test public void nativeUseEdgesWaitForSelectionAndRestoreThePreviousSlotAfterConsumption() throws Exception {
        try(AutomationFixture f=new AutomationFixture()) {
            AutoBuffModule m=new AutoBuffModule();f.base.modules.registerDynamic(m);
            m.refill.setEnabled(false);m.delays.get(Kind.SOUP).setRange(100,100);m.switchDelay.setRange(50,50);m.restoreDelay.setRange(100,100);
            f.player.inventory.currentItem=0;f.player.inventory.setInventorySlotContents(0,new ItemStack(Items.diamond_sword));
            f.player.inventory.setInventorySlotContents(8,new ItemStack(Items.mushroom_stew));f.health(10);f.player.getFoodStats().setFoodLevel(12);m.setEnabled(true);
            m.tick(1000);m.tick(1099);assertFalse(AutoBuffModule.ownsUseInput());m.tick(1100);assertEquals(8,f.player.inventory.currentItem);
            AutoBuffModule.prepareInputHook();assertFalse(f.mc.gameSettings.keyBindUseItem.isPressed());
            f.player.ticksExisted+=2;m.tick(1150);AutoBuffModule.prepareInputHook();
            assertTrue(f.mc.gameSettings.keyBindUseItem.isPressed());assertTrue(f.mc.gameSettings.keyBindUseItem.isKeyDown());
            f.mc.playerController.sendUseItem(f.player,f.mc.theWorld,f.player.getHeldItem());
            assertTrue(f.queue.packets.get(0) instanceof C09PacketHeldItemChange);
            assertTrue(f.queue.packets.get(1) instanceof C08PacketPlayerBlockPlacement);
            AutoBuffModule.prepareInputHook();assertFalse("Held food is not a repeated right-click edge",f.mc.gameSettings.keyBindUseItem.isPressed());
            f.player.inventory.setInventorySlotContents(8,new ItemStack(Items.bowl));m.tick(1200);
            assertFalse(f.mc.gameSettings.keyBindUseItem.isKeyDown());m.tick(1300);
            assertEquals(0,f.player.inventory.currentItem);assertFalse(AutoBuffModule.ownsUseInput());m.setEnabled(false);
        }
    }
    @Test public void splashPotionOwnsDownwardRotationBeforeNativeUse() throws Exception {
        try(AutomationFixture f=new AutomationFixture()) {
            MoveFixModule fix=new MoveFixModule();AutoBuffModule m=new AutoBuffModule();
            f.base.modules.registerDynamic(fix);f.base.modules.registerDynamic(m);
            m.refill.setEnabled(false);m.delays.get(Kind.POT).setRange(0,0);m.switchDelay.setRange(0,0);
            f.health(10);f.player.inventory.setInventorySlotContents(7,new ItemStack(Items.potionitem,1,16421));m.setEnabled(true);
            m.tick(1000);m.tick(1000);fix.beginRotationTick();m.tick(1001);
            assertEquals(90,fix.getRotationPitch(),0);AutoBuffModule.prepareInputHook();assertFalse(f.mc.gameSettings.keyBindUseItem.isPressed());
            fix.setFakeRotation("autowalk",0,0);
            f.player.ticksExisted+=2;fix.beginRotationTick();m.tick(1050);fix.setFakeRotation("autowalk",0,0);AutoBuffModule.prepareInputHook();
            assertEquals(90,fix.getRotationPitch(),0);assertTrue(f.mc.gameSettings.keyBindUseItem.isPressed());m.setEnabled(false);
        }
    }
    @Test public void consumptionTemporarilyTakesPriorityOverAnExistingSilentToolSlot() throws Exception {
        try(AutomationFixture f=new AutomationFixture()) {
            AutoBuffModule m=new AutoBuffModule();dev.vibe.module.impl.world.AutoToolModule tool=new dev.vibe.module.impl.world.AutoToolModule();
            f.base.modules.registerDynamic(tool);f.base.modules.registerDynamic(m);tool.setEnabled(true);
            tool.switching.mode.setValue("Silent");
            HotbarSwitches.acquire(tool,tool.switching,2,HotbarSwitches.TOOL,false);
            assertTrue(tool.hasSilentSlot());
            m.refill.setEnabled(false);m.delays.get(Kind.SOUP).setRange(0,0);
            f.health(10);f.player.inventory.setInventorySlotContents(8,new ItemStack(Items.mushroom_stew));m.setEnabled(true);m.tick(1000);m.tick(1000);
            assertEquals(8,dev.vibe.module.impl.world.AutoToolModule.serverSlotHook(8));
            int scope=dev.vibe.module.impl.world.AutoToolModule.beginActionHook();assertTrue(scope>0);dev.vibe.module.impl.world.AutoToolModule.endActionHook(scope);
            m.setEnabled(false);assertEquals(2,dev.vibe.module.impl.world.AutoToolModule.serverSlotHook(8));tool.setEnabled(false);
        }
    }
    @Test public void closingAnOwnedInventorySessionPreservesAManuallyCarriedItem() throws Exception {
        try(AutomationFixture f=new AutomationFixture()) {
            PlayerInventorySession session=new PlayerInventorySession(new ContainerClickDispatcher());
            assertTrue(session.open("Open Inventory"));
            Object gui=f.mc.currentScreen;ItemStack sword=new ItemStack(Items.diamond_sword);f.player.inventory.setItemStack(sword);
            assertFalse(new ContainerClickDispatcher().click(f.player.inventoryContainer,9,0,1,125));
            session.close();assertFalse(PlayerInventorySession.isBusy());
            assertSame(gui,f.mc.currentScreen);assertSame(sword,f.player.inventory.getItemStack());assertEquals(0,f.player.closes);
        }
    }
    @Test public void serverHealingCompletesAnInstantUseBeforeTheStackUpdateArrives() throws Exception {
        try(AutomationFixture f=new AutomationFixture()) {
            AutoBuffModule m=new AutoBuffModule();f.base.modules.registerDynamic(m);m.refill.setEnabled(false);
            m.delays.get(Kind.SOUP).setRange(0,0);m.switchDelay.setRange(0,0);m.restoreDelay.setRange(100,100);
            f.player.inventory.setInventorySlotContents(8,new ItemStack(Items.mushroom_stew));f.health(10);m.setEnabled(true);
            m.tick(1000);m.tick(1000);f.player.ticksExisted+=2;m.tick(1100);AutoBuffModule.prepareInputHook();
            assertTrue(AutoBuffModule.ownsUseInput());f.health(20);
            m.tick(1150);m.tick(1250);
            assertFalse(AutoBuffModule.ownsUseInput());assertEquals(0,f.player.inventory.currentItem);m.setEnabled(false);
        }
    }
    @Test public void refillHonorsRepeatedSlotsWeaponSlotsIgnoreAndPotionRoles() throws Exception {
        try(AutomationFixture f=new AutomationFixture()) {
            AutoBuffModule m=new AutoBuffModule();InventoryEditorModule editor=new InventoryEditorModule();
            f.base.modules.registerDynamic(editor);f.base.modules.registerDynamic(m);
            InventoryLayout layout=new InventoryLayout();layout.set(0,Type.SWORD);for(int i=1;i<9;i++)layout.set(i,Type.SOUP);
            f.player.inventory.setInventorySlotContents(0,new ItemStack(Items.diamond_sword));
            f.player.inventory.setInventorySlotContents(1,new ItemStack(Items.mushroom_stew));f.player.inventory.setInventorySlotContents(9,new ItemStack(Items.mushroom_stew));
            assertArrayEquals(new int[]{9,2},m.refillPlan(layout));
            layout.set(9,Type.IGNORE);assertNull(m.refillPlan(layout));
            layout=new InventoryLayout();layout.set(1,Type.HEALING_POTION);
            f.player.inventory.setInventorySlotContents(9,new ItemStack(Items.potionitem,1,16386));
            f.player.inventory.setInventorySlotContents(10,new ItemStack(Items.potionitem,1,16421));
            m.modes.toggle("Auto Soup");
            assertArrayEquals(new int[]{10,1},m.refillPlan(layout));
        }
    }
    @Test public void hiddenRefillUsesOneVanillaSwapAndPausesOtherInventoryOwners() throws Exception {
        try(AutomationFixture f=new AutomationFixture()) {
            AutoBuffModule m=new AutoBuffModule();InventoryEditorModule editor=new InventoryEditorModule();
            f.base.modules.registerDynamic(editor);f.base.modules.registerDynamic(m);
            InventoryLayout layout=new InventoryLayout();layout.set(0,Type.SWORD);layout.set(8,Type.SOUP);editor.setLayout(layout);
            f.player.inventory.setInventorySlotContents(0,new ItemStack(Items.diamond_sword));f.player.inventory.setInventorySlotContents(9,new ItemStack(Items.mushroom_stew));
            m.refillMode.setValue("Spoof");m.openDelay.setRange(0,0);m.setEnabled(true);m.tick(1000);
            assertTrue(PlayerInventorySession.isBusy());assertNull(f.mc.currentScreen);
            assertTrue(f.queue.packets.get(0) instanceof C16PacketClientStatus);
            ContainerClickDispatcher other=new ContainerClickDispatcher();
            assertFalse(other.click(f.player.inventoryContainer,9,8,2,125));
            m.tick(1001);assertEquals(Items.mushroom_stew,f.player.inventory.getStackInSlot(8).getItem());
            assertEquals(Items.diamond_sword,f.player.inventory.getStackInSlot(0).getItem());
            assertEquals(1,f.queue.packets.stream().filter(p->p instanceof C0EPacketClickWindow).count());
            m.tick(1300);assertFalse(PlayerInventorySession.isBusy());assertEquals(1,f.player.closes);m.setEnabled(false);
        }
    }
}
