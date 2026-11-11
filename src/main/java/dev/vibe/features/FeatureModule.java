package dev.vibe.features;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovementInput;

/** Common lifecycle only; action ownership remains in the existing Vibe services. */
public abstract class FeatureModule extends Module {
    protected final Minecraft mc=Minecraft.getMinecraft();
    protected FeatureModule(String name,String description,Category category){super(name,description,category,0);}
    public void tick(){}
    public void applyRotation(){}
    public void input(MovementInput input){}
    public void afterWalking(){}
    public void render(float partial){}
    protected boolean playing(){return mc.thePlayer!=null&&mc.theWorld!=null&&!mc.thePlayer.isDead&&!mc.isGamePaused();}
    protected boolean controlling(){return playing()&&mc.currentScreen==null&&mc.inGameHasFocus&&!mc.thePlayer.isRiding()&&!dev.vibe.module.impl.movement.FreecamModule.cameraActiveHook();}
    public static <T extends Module> T module(Class<T> type){Vibe v=Vibe.getInstance();return v==null||v.getModuleManager()==null?null:v.getModuleManager().getModule(type);}
}
