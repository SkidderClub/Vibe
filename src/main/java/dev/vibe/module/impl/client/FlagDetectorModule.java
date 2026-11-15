package dev.vibe.module.impl.client;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import org.lwjgl.input.Keyboard;
import dev.vibe.setting.*;
import java.util.*;

/** Reports small server position corrections after a world has settled. */
public final class FlagDetectorModule extends Module {
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private Object world;
    private int stableTicks;
    private int flags;
    public final BooleanSetting stopMovement=addSetting(new BooleanSetting("Stop Movement",false));
    public final NumberSetting ticks=addSetting(new NumberSetting("Ticks",20,1,200,1,stopMovement::isEnabled));
    private final Set<Module> paused=Collections.newSetFromMap(new IdentityHashMap<Module,Boolean>());
    private Object pausePlayer,pauseWorld;
    private int resumeTick;

    public FlagDetectorModule() { super("Flag Detector", "Reports nearby S08 position corrections", Category.CLIENT, Keyboard.KEY_NONE); }

    public void tick() {
        maintainPause();
        if (minecraft.theWorld != world) { world = minecraft.theWorld; stableTicks = 0; return; }
        if (world != null) stableTicks++;
    }
    public void maintainPause(){
        if(!isEnabled()||!stopMovement.isEnabled()||minecraft.thePlayer==null||minecraft.theWorld!=pauseWorld||minecraft.thePlayer!=pausePlayer||minecraft.thePlayer.ticksExisted>=resumeTick){release();return;}
        for(Module module:dev.vibe.Vibe.getInstance().getModuleManager().getModules())
            if((module.getCategory()==Category.MOVEMENT||module instanceof dev.vibe.module.impl.world.ScaffoldModule)&&paused.add(module))module.suspend(true);
    }
    private void release(){for(Module module:paused)module.suspend(false);paused.clear();pausePlayer=pauseWorld=null;resumeTick=0;}
    @Override protected void onDisable(){release();}

    /** Runs on the client thread after the inbound packet is observed. */
    public void observe(S08PacketPlayerPosLook packet) {
        if (!isEnabled() || minecraft.thePlayer == null || minecraft.theWorld == null || minecraft.theWorld != world || stableTicks < 20) return;
        double dx = packet.getX() - minecraft.thePlayer.posX;
        double dy = packet.getY() - minecraft.thePlayer.posY;
        double dz = packet.getZ() - minecraft.thePlayer.posZ;
        // Relative corrections are normal movement packets; resolve them
        // before comparing the final correction distance.
        java.util.Set<S08PacketPlayerPosLook.EnumFlags> relative = packet.func_179834_f();
        if (relative.contains(S08PacketPlayerPosLook.EnumFlags.X)) dx = packet.getX();
        if (relative.contains(S08PacketPlayerPosLook.EnumFlags.Y)) dy = packet.getY();
        if (relative.contains(S08PacketPlayerPosLook.EnumFlags.Z)) dz = packet.getZ();
        if (dx * dx + dy * dy + dz * dz > 9.0D) return;
        flags++;
        if(stopMovement.isEnabled()){
            pausePlayer=minecraft.thePlayer;pauseWorld=minecraft.theWorld;
            resumeTick=minecraft.thePlayer.ticksExisted+ticks.getInt();maintainPause();
        }
        dev.vibe.chat.ClientChat.send("§fFlag detected: §b" + flags);
    }
}
