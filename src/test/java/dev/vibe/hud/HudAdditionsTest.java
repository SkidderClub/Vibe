package dev.vibe.hud;

import dev.vibe.module.impl.client.HudModule;
import dev.vibe.inventory.*;
import dev.vibe.path.PathTestFixture;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import org.junit.Test;
import static org.junit.Assert.*;

public class HudAdditionsTest {
    @Test public void effectsAnchorExpandsAwayFromTheNearestScreenEdgeAndContainerPreviewIsFixed()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            f.mc.displayWidth=1280;f.mc.displayHeight=720;f.mc.gameSettings.guiScale=1;
            PathTestFixture.set(net.minecraft.client.Minecraft.class,f.mc,"mcLanguageManager",new net.minecraft.client.resources.LanguageManager(new net.minecraft.client.resources.data.IMetadataSerializer(),"en_US"));
            HudManager manager=new HudManager(new java.io.File("build/hud-polish-unit"));net.minecraft.client.gui.ScaledResolution res=new net.minecraft.client.gui.ScaledResolution(f.mc);
            HudManager.HudElement effects=manager.getElement(HudManager.EFFECTS);effects.setBounds(0,0,154,90);effects.moveTo(100,500,res);
            assertEquals(500,effects.top(res,90));assertEquals(470,effects.top(res,120));
            effects.moveTo(100,100,res);assertEquals(100,effects.top(res,90));assertEquals(100,effects.top(res,120));
            HudManager.HudElement gui=manager.getElement(HudManager.CUSTOM_GUIS);gui.setScale(1.5F);gui.moveTo(50,100,res);assertEquals(1,gui.getScale(),0);assertEquals(0,gui.getY());
        }
    }
    @Test public void blockCountsUseOnlyTheChosenSlotOrHotbarIncludingSilentSelection()throws Exception{
        try(AutomationFixture f=new AutomationFixture()){
            f.player.inventory.mainInventory[0]=new ItemStack(Items.diamond_sword);
            f.player.inventory.mainInventory[2]=new ItemStack(Blocks.stone,64);
            f.player.inventory.mainInventory[4]=new ItemStack(Blocks.glass,32);
            f.player.inventory.mainInventory[9]=new ItemStack(Blocks.stone,64);
            assertEquals(96,BlockCounterState.count(f.player.inventory,0,true));assertEquals(0,BlockCounterState.count(f.player.inventory,0,false));
            HotbarSwitches.Lease lease=HotbarSwitches.acquire(new Object(),new ItemSwitching(s-> {},"Silent"),4,HotbarSwitches.SCAFFOLD,false);
            assertEquals(0,f.player.inventory.currentItem);
            assertEquals(32,BlockCounterState.count(f.player.inventory,HotbarSwitches.selectedSlot(),false));lease.close(false,true);
        }
    }
    @Test public void depletionStartsFullFallsWithUseAndResetsWhenShownAgain(){
        BlockCounterState state=new BlockCounterState();
        assertEquals(1,state.fraction(128,true,"Total"),0);assertEquals(.75F,state.fraction(96,true,"Total"),0);
        assertEquals(.25F,state.fraction(32,true,"Total"),0);assertEquals(1,state.fraction(64,true,"Slot"),0);
        assertEquals(.5F,state.fraction(32,true,"Slot"),0);state.fraction(32,false,"Slot");assertEquals(1,state.fraction(32,true,"Slot"),0);
        assertEquals(1,state.fraction(96,true,"Slot"),0);
    }
    @Test public void scaffoldAndEagleFiltersAreAnEitherCondition(){
        assertTrue(BlockCounterState.allowed(false,false,false,false));assertFalse(BlockCounterState.allowed(true,false,false,true));
        assertTrue(BlockCounterState.allowed(true,true,true,false));assertTrue(BlockCounterState.allowed(true,true,false,true));assertFalse(BlockCounterState.allowed(true,true,false,false));
    }
    @Test public void islandPriorityAndOwnershipDoNotRequireStandaloneWidgets()throws Exception{
        assertEquals(DynamicIslandRenderer.View.TAB,DynamicIslandRenderer.choose(true,true,true));
        assertEquals(DynamicIslandRenderer.View.NOTIFICATION,DynamicIslandRenderer.choose(false,true,true));
        assertEquals(DynamicIslandRenderer.View.TARGET,DynamicIslandRenderer.choose(false,false,true));
        assertEquals(DynamicIslandRenderer.View.IDLE,DynamicIslandRenderer.choose(false,false,false));
        try(PathTestFixture f=new PathTestFixture()){
            HudModule hud=new HudModule();f.modules.registerDynamic(hud);hud.setEnabled(true);
            assertFalse(hud.getHudElements().isSelected(HudManager.DYNAMIC_ISLAND));assertFalse(hud.getHudElements().isSelected(HudManager.BLOCK_COUNTER));
            hud.getHudElements().toggle(HudManager.DYNAMIC_ISLAND);assertFalse(hud.getHudElements().isSelected(HudManager.TAB));
            assertTrue(DynamicIslandRenderer.owns(hud,"Tab"));assertTrue(HudManager.replaces(HudManager.TAB));
            hud.island.transforms.toggle("Tab");assertFalse(DynamicIslandRenderer.owns(hud,"Tab"));assertFalse(HudManager.replaces(HudManager.TAB));
            hud.setEnabled(false);assertFalse(DynamicIslandRenderer.owns(hud,"Notification"));
        }
    }
    @Test public void animationRetargetsWithoutJumpsAndSettlesForEachEasing(){
        for(String easing:new String[]{"Linear","Smoothstep","Cubic"}){
            HudTween t=new HudTween();t.snap(0);assertEquals(0,t.update(100,1000,200,easing),0);
            float mid=t.update(100,1100,200,easing);assertTrue(mid>0&&mid<100);
            assertEquals(mid,t.update(20,1100,200,easing),0);assertEquals(20,t.update(20,1300,200,easing),0);
            assertEquals(70,t.update(70,1400,0,easing),0);
        }
    }
    @Test public void tpsUsesServerTicksAndResetsForNewConnectionsAndStaleData(){
        Object connection=new Object();ServerTickRate.observe(connection,100,1000);assertTrue(Double.isNaN(ServerTickRate.value(connection,1001)));
        ServerTickRate.observe(connection,120,2000);assertEquals(20,ServerTickRate.value(connection,2000),0);
        ServerTickRate.observe(connection,140,4000);assertTrue(ServerTickRate.value(connection,4000)<20);
        assertTrue(Double.isNaN(ServerTickRate.value(connection,15000)));assertTrue(Double.isNaN(ServerTickRate.value(new Object(),4000)));
    }
}
