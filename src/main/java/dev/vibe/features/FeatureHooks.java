package dev.vibe.features;

import dev.vibe.Vibe;
import dev.vibe.module.Module;
import net.minecraft.util.MovementInput;

/** Hooks run on the Minecraft thread, in the same phases as existing producers. */
public final class FeatureHooks {
    private static final dev.vibe.ui.render.DroneRigRenderer droneRenderer=new dev.vibe.ui.render.DroneRigRenderer();
    private FeatureHooks(){}
    private static Iterable<Module> modules(){Vibe v=Vibe.getInstance();return v==null||v.getModuleManager()==null?java.util.Collections.emptyList():v.getModuleManager().getModules();}
    public static void tick(){dev.vibe.hud.SessionJournal.INSTANCE.tick();Vibe v=Vibe.getInstance();if(v!=null&&v.getHudManager()!=null)v.getHudManager().dashboards.tick(v.getHudManager());for(Module m:modules())if(m instanceof FeatureModule&&m.isEnabled())((FeatureModule)m).tick();}
    public static void rotate(){for(Module m:modules())if(m instanceof FeatureModule&&m.isEnabled())((FeatureModule)m).applyRotation();}
    public static void input(MovementInput input){for(Module m:modules())if(m instanceof FeatureModule&&m.isEnabled())((FeatureModule)m).input(input);}
    public static void afterWalking(Object entity){if(entity!=net.minecraft.client.Minecraft.getMinecraft().thePlayer)return;for(Module m:modules())if(m instanceof FeatureModule&&m.isEnabled())((FeatureModule)m).afterWalking();}
    public static void render(float partial){if(dev.vibe.module.impl.meme.HypixelModule.visualsSuppressed())return;for(Module m:modules())if(m instanceof FeatureModule&&m.isEnabled())((FeatureModule)m).render(partial);dev.vibe.module.impl.visual.CustomCosmeticsModule cosmetics=FeatureModule.module(dev.vibe.module.impl.visual.CustomCosmeticsModule.class);if(cosmetics!=null&&cosmetics.isEnabled())cosmetics.ground.render(partial);droneRenderer.render(FeatureModule.module(dev.vibe.module.impl.movement.FreecamModule.class));}
}
