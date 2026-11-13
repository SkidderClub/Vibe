package dev.vibe.module.impl.visual;

import dev.vibe.features.FeatureModule;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.impl.meme.HypixelModule;
import dev.vibe.setting.*;
import org.lwjgl.util.glu.GLU;

/** Change only perspective projection; orthographic menus and HUD keep their native shape. */
public final class AspectRatioModule extends Module {
    public final ModeSetting preset=addSetting(new ModeSetting("Ratio","4:3","4:3","16:9","16:10","21:9","Custom"));
    public final NumberSetting custom=addSetting(new NumberSetting("Custom Ratio",1.33,.5,3.5,.01,()->preset.is("Custom")));
    public final BooleanSetting hand=addSetting(new BooleanSetting("Affect Hand",false));
    public AspectRatioModule(){super("Aspect Ratio","Change world proportions independently of the HUD and first-person hand",Category.VISUAL,0);}
    public float ratio(){switch(preset.getValue()){case "4:3":return 4F/3;case "16:9":return 16F/9;case "16:10":return 16F/10;case "21:9":return 21F/9;default:return custom.getFloat();}}
    public static float resolve(float original,boolean handPass){AspectRatioModule module=FeatureModule.module(AspectRatioModule.class);return module!=null&&module.isEnabled()&&!HypixelModule.visualsSuppressed()&&(!handPass||module.hand.isEnabled())?module.ratio():original;}
    public static void perspective(float fov,float original,float near,float far,boolean handPass){GLU.gluPerspective(fov,resolve(original,handPass),near,far);}
}
