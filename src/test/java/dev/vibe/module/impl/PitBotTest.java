package dev.vibe.module.impl;

import dev.vibe.Vibe;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.setting.BooleanSetting;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.FoodStats;
import net.minecraft.util.MovementInput;
import net.minecraft.world.World;
import org.junit.*;
import static org.junit.Assert.*;

public class PitBotTest {
    private Minecraft mc,previousMc;
    private Vibe previousVibe;
    private HypixelModule pit;
    private MoveFixModule fix;
    private Player player,target;
    private List<net.minecraft.entity.player.EntityPlayer> players;

    @Before public void setup() throws Exception {
        previousMc=Minecraft.getMinecraft();previousVibe=Vibe.getInstance();
        mc=allocate(Minecraft.class);set(Minecraft.class,null,"theMinecraft",mc);
        mc.gameSettings=new GameSettings();mc.gameSettings.limitFramerate=144;mc.gameSettings.pauseOnLostFocus=true;
        mc.theWorld=allocate(WorldClient.class);
        players=new ArrayList<>();set(World.class,mc.theWorld,"playerEntities",players);
        player=allocate(Player.class);player.name="Local";player.movementInput=new MovementInput();
        net.minecraft.entity.DataWatcher watcher=new net.minecraft.entity.DataWatcher(player);
        watcher.addObject(6,20F);set(net.minecraft.entity.Entity.class,player,"dataWatcher",watcher);
        target=allocate(Player.class);target.name="Target";target.posX=8;
        player.setEntityId(1);target.setEntityId(2);
        mc.thePlayer=player;players.add(player);players.add(target);
        Vibe vibe=new Vibe();set(Vibe.class,null,"instance",vibe);
        ModuleManager manager=allocate(ModuleManager.class);set(Vibe.class,vibe,"moduleManager",manager);
        pit=new HypixelModule();fix=new MoveFixModule();
        set(ModuleManager.class,manager,"modules",new ArrayList<Module>(Arrays.asList(pit,fix)));
        pit.getModes().setValue(new HashSet<String>(Arrays.asList(HypixelModule.PIT_BOT)));
        for(dev.vibe.setting.Setting<?> setting:pit.getSettings())
            if(setting.getRawName().equals("Pathfinding"))((BooleanSetting)setting).setEnabled(false);
        pit.setEnabled(true);fix.beginRotationTick();fix.installInputHook();
    }
    @After public void cleanup() throws Exception {
        try { if(pit!=null)pit.setEnabled(false); }
        finally { set(Minecraft.class,null,"theMinecraft",previousMc);set(Vibe.class,null,"instance",previousVibe); }
    }

    @Test public void capsFpsAndRestoresFocusAndFpsOnModeChangeAndDisconnect() {
        pit.tick();assertEquals(30,mc.gameSettings.limitFramerate);assertFalse(mc.gameSettings.pauseOnLostFocus);
        assertTrue(pit.suppressVisuals());assertTrue(pit.blockEscapeMenu());assertTrue(pit.isEnabled());
        pit.getModes().setValue(Collections.emptySet());pit.tick();
        assertEquals(144,mc.gameSettings.limitFramerate);assertTrue(mc.gameSettings.pauseOnLostFocus);assertFalse(pit.suppressVisuals());
        mc.gameSettings.pauseOnLostFocus=false;
        pit.getModes().toggle(HypixelModule.PIT_BOT);pit.tick();mc.theWorld=null;pit.tick();
        assertEquals(144,mc.gameSettings.limitFramerate);assertFalse(mc.gameSettings.pauseOnLostFocus);
    }

    @Test public void followsThroughGuiAndReleasesWhenTargetLeavesOrBotDisables() {
        mc.currentScreen=new net.minecraft.client.gui.GuiChat();
        pit.getPitTarget().setValue("tArGeT");pit.tick();player.movementInput.updatePlayerMoveState();
        assertEquals(1F,player.movementInput.moveForward,0);assertTrue(player.sprinting);
        assertEquals(0F,player.rotationYaw,0);
        players.remove(target);pit.tick();player.movementInput.updatePlayerMoveState();
        assertEquals(0F,player.movementInput.moveForward,0);assertFalse(player.sprinting);
        players.add(target);pit.tick();pit.setEnabled(false);player.movementInput.updatePlayerMoveState();
        assertEquals(0F,player.movementInput.moveForward,0);assertEquals(144,mc.gameSettings.limitFramerate);
    }

    @Test public void ignoresSelfAndStopsWithinFollowDistance() {
        pit.getPitTarget().setValue("Local");pit.tick();player.movementInput.updatePlayerMoveState();
        assertEquals(0F,player.movementInput.moveForward,0);
        pit.getPitTarget().setValue("Target");target.posX=1;pit.tick();player.movementInput.updatePlayerMoveState();
        assertEquals(0F,player.movementInput.moveForward,0);
    }

    @Test public void murderWeaponsMatchTheRegistryIdsSavedByTheEditor() throws Exception {
        net.minecraft.init.Bootstrap.register();
        java.lang.reflect.Method method=HypixelModule.class.getDeclaredMethod("isMurderWeapon",net.minecraft.item.ItemStack.class);
        method.setAccessible(true);
        assertEquals(true,method.invoke(pit,new net.minecraft.item.ItemStack(net.minecraft.init.Items.iron_sword)));
        assertEquals(false,method.invoke(pit,new net.minecraft.item.ItemStack(net.minecraft.init.Items.apple)));
        pit.getMurderWeapons().setValue("swordiron");
        assertEquals(true,method.invoke(pit,new net.minecraft.item.ItemStack(net.minecraft.init.Items.iron_sword)));
    }

    @Test public void visualModulesStayEnabledWhileTheirEffectsAreSuspended() throws Exception {
        pit.setEnabled(false);
        FullBrightModule bright=new FullBrightModule();FovChangerModule fov=new FovChangerModule();
        set(ModuleManager.class,Vibe.getInstance().getModuleManager(),"modules",new ArrayList<Module>(Arrays.asList(pit,fix,bright,fov)));
        mc.gameSettings.gammaSetting=.6F;mc.gameSettings.fovSetting=70;
        bright.setEnabled(true);fov.setEnabled(true);fov.tick();
        assertEquals(1000F,mc.gameSettings.gammaSetting,0);assertEquals(105F,mc.gameSettings.fovSetting,0);
        pit.setEnabled(true);pit.tick();bright.tick();fov.tick();
        assertTrue(bright.isEnabled());assertTrue(fov.isEnabled());
        assertEquals(.6F,mc.gameSettings.gammaSetting,0);assertEquals(70F,mc.gameSettings.fovSetting,0);
        pit.setEnabled(false);bright.tick();fov.tick();
        assertEquals(1000F,mc.gameSettings.gammaSetting,0);assertEquals(105F,mc.gameSettings.fovSetting,0);
    }

    private static void set(Class<?> type,Object object,String name,Object value)throws Exception {
        Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(object,value);
    }
    private static <T>T allocate(Class<T> type)throws Exception {
        Class<?> unsafe=Class.forName("sun.misc.Unsafe");Field f=unsafe.getDeclaredField("theUnsafe");f.setAccessible(true);
        return type.cast(unsafe.getMethod("allocateInstance",Class.class).invoke(f.get(null),type));
    }
    public static final class Player extends EntityPlayerSP {
        String name;boolean sprinting;
        private Player(){super(null,null,null,null);}
        @Override public String getName(){return name;}
        @Override public FoodStats getFoodStats(){return new FoodStats();}
        @Override public boolean isSneaking(){return false;}
        @Override public boolean isUsingItem(){return false;}
        @Override public void setSprinting(boolean value){sprinting=value;}
    }
}
