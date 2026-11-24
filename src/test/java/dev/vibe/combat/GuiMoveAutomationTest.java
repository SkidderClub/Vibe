package dev.vibe.combat;

import dev.vibe.module.impl.combat.KillAuraModule;
import dev.vibe.module.impl.movement.*;
import dev.vibe.path.PathTestFixture;
import dev.vibe.setting.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.*;
import net.minecraft.entity.EntityLivingBase;
import org.junit.Test;
import static org.junit.Assert.*;

public class GuiMoveAutomationTest {
    @Test public void auraAcquiresRotatesAndQueuesNativeClicksInAllowedGuis()throws Exception{
        try(ProjectileFixture f=new ProjectileFixture()){
            GuiMoveModule gui=new GuiMoveModule();MoveFixModule fix=new MoveFixModule();ProbeAura aura=new ProbeAura(f.player("Enemy",.5,64,2.5));
            f.base.modules.registerDynamic(gui);f.base.modules.registerDynamic(fix);f.base.modules.registerDynamic(aura);gui.setEnabled(true);fix.setEnabled(true);aura.setEnabled(true);
            f.base.mc.currentScreen=PathTestFixture.allocate(GuiInventory.class);f.base.mc.inGameHasFocus=false;
            aura.tickStart();assertNull(aura.getTarget());assertFalse(KillAuraModule.allowInputHook(false));
            gui.allowKillaura.setEnabled(true);aura.tickStart();assertNotNull(aura.getTarget());assertTrue(fix.ownsRotation(aura.getId()));assertTrue(KillAuraModule.allowInputHook(false));assertEquals(0,KillAuraModule.guiClickCounterHook(10000,10000));assertEquals(7,KillAuraModule.guiClickCounterHook(10000,7));assertEquals(7,KillAuraModule.guiClickCounterHook(7,0));
            KillAuraModule.prepareInputHook();assertTrue("Aura must use the normal attack key's press queue in a GUI",f.base.mc.gameSettings.keyBindAttack.isPressed());aura.tickEnd();
            f.base.mc.currentScreen=PathTestFixture.allocate(GuiChest.class);aura.tickStart();assertNull(aura.getTarget());gui.screens.toggle("Chests");aura.tickStart();assertNotNull(aura.getTarget());
            f.base.mc.currentScreen=new GuiChat();aura.tickStart();assertNull(aura.getTarget());assertFalse(KillAuraModule.allowInputHook(false));
            gui.allowCombatInChat.setEnabled(true);aura.tickStart();assertNotNull(aura.getTarget());assertTrue(fix.ownsRotation(aura.getId()));assertTrue(KillAuraModule.allowInputHook(false));
            assertFalse(gui.allows(f.base.mc.currentScreen));assertFalse(GuiMoveModule.allowsAutoWalk());
            f.base.mc.currentScreen=PathTestFixture.allocate(GuiEditSign.class);assertFalse(GuiMoveModule.allowsKillaura());
            f.base.mc.currentScreen=PathTestFixture.allocate(GuiInventory.class);gui.setEnabled(false);aura.tickStart();assertNull(aura.getTarget());aura.setEnabled(false);
        }
    }
    @Test public void clickGuiTypingAndBindingPauseBothPermissions()throws Exception{
        try(ProjectileFixture f=new ProjectileFixture()){
            GuiMoveModule gui=new GuiMoveModule();f.base.modules.registerDynamic(gui);gui.setEnabled(true);gui.allowKillaura.setEnabled(true);gui.allowAutoWalk.setEnabled(true);
            f.base.modules.registerDynamic(new dev.vibe.module.impl.client.ClickGuiModule());dev.vibe.ui.clickgui.VibeClickGui screen=new dev.vibe.ui.clickgui.VibeClickGui();f.base.mc.currentScreen=screen;
            assertTrue(GuiMoveModule.allowsKillaura());assertTrue(GuiMoveModule.allowsAutoWalk());
            PathTestFixture.set(dev.vibe.ui.clickgui.VibeClickGui.class,screen,"bindingModule",gui);assertFalse(GuiMoveModule.allowsKillaura());assertFalse(GuiMoveModule.allowsAutoWalk());
        }
    }
    @Test public void legacyContainerPermissionStillWorksIndependently()throws Exception{
        try(ProjectileFixture f=new ProjectileFixture()){
            ProbeAura aura=new ProbeAura(null);f.base.modules.registerDynamic(aura);aura.setEnabled(true);f.base.mc.currentScreen=PathTestFixture.allocate(GuiInventory.class);assertFalse(aura.allowed());
            for(Setting<?> s:aura.getSettings())if(s.getRawName().equals("Not In Inv"))((BooleanSetting)s).setEnabled(false);
            assertTrue(aura.allowed());f.base.mc.currentScreen=new GuiChat();assertFalse(aura.allowed());aura.setEnabled(false);
        }
    }
    private static final class ProbeAura extends KillAuraModule {
        private final EntityLivingBase enemy;
        ProbeAura(EntityLivingBase enemy){this.enemy=enemy;}
        @Override protected EntityLivingBase chooseTarget(){return enemy;}
        @Override protected boolean validTarget(EntityLivingBase e){return e!=null&&e==enemy;}
        @Override protected void advanceRotation(EntityLivingBase e){}
        @Override protected boolean clickDue(long now){return true;}
        @Override protected int pollClicks(long now){return 1;}
        boolean allowed(){return canOperate();}
    }
}
