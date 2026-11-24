package dev.vibe.module.impl.movement;

import dev.vibe.module.Category;
import dev.vibe.setting.*;
import dev.vibe.input.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.*;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;

/** Physical key state feeds vanilla MovementInput, including the existing MoveFix correction. */
public final class GuiMoveModule extends dev.vibe.module.Module {
    public final MultiSelectSetting screens=addSetting(new MultiSelectSetting("GUIs",Arrays.asList("ClickGUI","Inventory","Chests","Other Vanilla GUIs"),Arrays.asList("ClickGUI","Inventory")));
    public final BooleanSetting jump=addSetting(new BooleanSetting("Allow Jumping",true));
    public final BooleanSetting sprint=addSetting(new BooleanSetting("Allow Sprinting",true));
    public final BooleanSetting allowKillaura=addSetting(new BooleanSetting("Allow Killaura",false));
    public final BooleanSetting allowCombatInChat=addSetting(new BooleanSetting("Killaura and MoveFix in Chat",false));
    public final BooleanSetting allowAutoWalk=addSetting(new BooleanSetting("Allow AutoWalk",false));
    private final Set<KeyBinding> owned=Collections.newSetFromMap(new IdentityHashMap<>());
    public GuiMoveModule(){super("GUI Move","Move in selected GUIs; pauses while typing or binding keys",Category.MOVEMENT,Keyboard.KEY_NONE);}
    public boolean allows(GuiScreen screen){
        if(screen==null||screen instanceof GuiChat||screen instanceof GuiEditSign||screen instanceof GuiScreenBook||screen instanceof GuiCommandBlock)return false;
        if(screen instanceof dev.vibe.ui.clickgui.VibeClickGui)return screens.isSelected("ClickGUI")&&!((dev.vibe.ui.clickgui.VibeClickGui)screen).isTypingForMovement();
        if(GuiInputFocus.typing(screen))return false;
        if(screen instanceof GuiInventory)return screens.isSelected("Inventory");
        if(screen instanceof GuiChest)return screens.isSelected("Chests");
        return screen.getClass().getName().startsWith("net.minecraft.client.gui.")&&screens.isSelected("Other Vanilla GUIs");
    }
    /** All GUI automation shares the selected-screen, typing and window-focus guards. */
    private static boolean automationAllowed(boolean aura){
        Minecraft mc=Minecraft.getMinecraft();GuiMoveModule module=dev.vibe.features.FeatureModule.module(GuiMoveModule.class);
        if(mc==null||module==null||!module.isEnabled()||!windowActive(mc))return false;
        if(mc.currentScreen instanceof GuiChat)return aura&&module.allowCombatInChat.isEnabled();
        return (aura?module.allowKillaura:module.allowAutoWalk).isEnabled()&&module.allows(mc.currentScreen);
    }
    private static boolean windowActive(Minecraft mc){return mc.displayWidth<=0||mc.displayHeight<=0||!Display.isCreated()||Display.isActive();}
    public static boolean allowsKillaura(){return automationAllowed(true);}
    public static boolean allowsAutoWalk(){return automationAllowed(false);}
    public void tick(){
        Minecraft mc=Minecraft.getMinecraft();
        if(!isEnabled()||mc.thePlayer==null||mc.theWorld==null||!windowActive(mc)||!allows(mc.currentScreen)){release();return;}
        KeyBinding[] keys={mc.gameSettings.keyBindForward,mc.gameSettings.keyBindBack,mc.gameSettings.keyBindLeft,mc.gameSettings.keyBindRight,mc.gameSettings.keyBindJump,mc.gameSettings.keyBindSprint};
        for(int i=0;i<keys.length;i++){boolean allowed=i<4||i==4&&jump.isEnabled()||i==5&&sprint.isEnabled();owned.add(keys[i]);KeyBinding.setKeyBindState(keys[i].getKeyCode(),allowed&&VanillaClicks.physicallyDown(keys[i]));}
    }
    private void release(){Minecraft mc=Minecraft.getMinecraft();for(KeyBinding key:owned)KeyBinding.setKeyBindState(key.getKeyCode(),mc.currentScreen==null&&VanillaClicks.physicallyDown(key));owned.clear();}
    @Override protected void onDisable(){release();}
}
