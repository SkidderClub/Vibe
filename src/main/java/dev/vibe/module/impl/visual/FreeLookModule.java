package dev.vibe.module.impl.visual;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;

/** Render-camera angles only. The player's aim and MoveFix's packet rotations are never modified. */
public final class FreeLookModule extends dev.vibe.module.Module {
    public final BooleanSetting hold=addSetting(new BooleanSetting("Hold",true));
    private Entity player;private World world;private float yaw,pitch;
    public FreeLookModule(){super("FreeLook","Look around independently of player aim; Hold uses this module's keybind",Category.VISUAL,Keyboard.KEY_LMENU);}
    public void keyChanged(boolean pressed,boolean allowed){if(hold.isEnabled())setEnabled(pressed&&allowed);}
    @Override protected void onEnable(){Minecraft mc=Minecraft.getMinecraft();player=mc.thePlayer;world=mc.theWorld;if(player!=null){yaw=player.rotationYaw;pitch=player.rotationPitch;}}
    @Override protected void onDisable(){player=null;world=null;}
    public void tick(){
        Minecraft mc=Minecraft.getMinecraft();
        if(isEnabled()&&(player!=mc.thePlayer||world!=mc.theWorld||mc.thePlayer==null||!mc.inGameHasFocus&&mc.currentScreen==null
                ||hold.isEnabled()&&!down(getKey())))setEnabled(false);
    }
    private static boolean down(int key){return key>0&&key<Keyboard.KEYBOARD_SIZE&&Keyboard.isCreated()&&Keyboard.isKeyDown(key);}
    private static FreeLookModule active(){
        Vibe v=Vibe.getInstance();Minecraft mc=Minecraft.getMinecraft();if(v==null||v.getModuleManager()==null)return null;
        FreeLookModule m=v.getModuleManager().getModule(FreeLookModule.class);
        return m!=null&&m.isEnabled()&&m.player==mc.thePlayer&&m.world==mc.theWorld&&mc.thePlayer!=null&&mc.currentScreen==null
                &&mc.inGameHasFocus&&!dev.vibe.module.impl.movement.FreecamModule.cameraActiveHook()?m:null;
    }
    public static boolean cameraActive(){return active()!=null;}
    public static boolean mouse(Object entity,float dx,float dy){FreeLookModule m=active();if(m==null||entity!=m.player)return false;m.yaw+=dx*.15F;m.pitch=Math.max(-90,Math.min(90,m.pitch-dy*.15F));return true;}
    public static float yaw(Object entity,float original){FreeLookModule m=active();return m!=null&&entity==m.player?m.yaw:original;}
    public static float pitch(Object entity,float original){FreeLookModule m=active();return m!=null&&entity==m.player?m.pitch:original;}
}
