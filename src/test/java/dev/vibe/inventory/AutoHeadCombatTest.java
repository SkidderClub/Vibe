package dev.vibe.inventory;

import dev.vibe.module.impl.combat.*;
import dev.vibe.module.impl.combat.AutoBuffModule.Kind;
import dev.vibe.combat.SilentMurderPacketsTest;
import java.util.Collections;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AutoHeadCombatTest {
    @Test public void auraBlockYieldsBeforeSlotChangeAndDroppedUsePulsesRetry() throws Exception {
        for(String mode:new String[]{"Basic","Silent"})try(AutomationFixture f=new AutomationFixture()){
            KillAuraModule aura=new KillAuraModule();AutoBuffModule buff=new AutoBuffModule();
            f.base.modules.registerDynamic(aura);f.base.modules.registerDynamic(buff);
            f.mc.playerController=SilentMurderPacketsTest.nativeController(f.mc,f.queue);
            ItemStack sword=new ItemStack(Items.diamond_sword);f.player.inventory.mainInventory[0]=sword;
            f.player.inventory.mainInventory[8]=new ItemStack(Items.skull,3,3);f.health(8);
            f.mc.playerController.updateController();f.queue.packets.clear();aura.setEnabled(true);
            for(dev.vibe.setting.Setting<?> setting:aura.getSettings())if(setting.getRawName().equals("Autoblock Mode"))((dev.vibe.setting.ModeSetting)setting).setValue("Vanilla");
            dev.vibe.path.PathTestFixture.set(KillAuraModule.class,aura,"blockOwned",true);
            dev.vibe.path.PathTestFixture.set(KillAuraModule.class,aura,"useOwned",true);
            f.player.setItemInUse(sword,72000);assertTrue(f.player.isUsingItem());
            buff.switching.mode.setValue(mode);buff.modes.setValue(Collections.singleton(Kind.HEAD.label));
            buff.refill.setEnabled(false);buff.delays.get(Kind.HEAD).setRange(0,0);buff.setEnabled(true);
            buff.tick(1000);buff.tick(1000);assertTrue(AutoBuffModule.ownsUseInput());assertFalse(f.player.isUsingItem());
            assertTrue(f.queue.packets.get(0) instanceof C07PacketPlayerDigging);
            assertEquals(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,((C07PacketPlayerDigging)f.queue.packets.get(0)).getStatus());
            AutoBuffModule.prepareInputHook();assertTrue(f.mc.gameSettings.keyBindUseItem.isPressed());
            // Vanilla may consume the pulse without using while rightClickDelayTimer > 0.
            AutoBuffModule.finishInputHook(1000);HotbarSwitches.finishInputHook();assertTrue(AutoBuffModule.ownsUseInput());
            AutoBuffModule.prepareInputHook();assertTrue("Retry the lost pulse",f.mc.gameSettings.keyBindUseItem.isPressed());
            assertSame(Items.skull,f.player.getHeldItem().getItem());
            f.mc.playerController.sendUseItem(f.player,f.mc.theWorld,f.player.getHeldItem());
            AutoBuffModule.finishInputHook(1050);HotbarSwitches.finishInputHook();
            assertFalse(AutoBuffModule.ownsUseInput());assertEquals(0,f.player.inventory.currentItem);
            int uses=0;for(Packet packet:f.queue.packets)if(packet instanceof C08PacketPlayerBlockPlacement){uses++;assertSame(Items.skull,((C08PacketPlayerBlockPlacement)packet).getStack().getItem());}
            assertEquals("One actual head use, through vanilla's controller",1,uses);
            buff.setEnabled(false);aura.setEnabled(false);
        }
    }
}
