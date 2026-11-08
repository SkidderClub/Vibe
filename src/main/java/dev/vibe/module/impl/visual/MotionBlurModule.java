package dev.vibe.module.impl.visual;

import dev.vibe.module.*;
import dev.vibe.module.Module;
import dev.vibe.setting.*;
import org.lwjgl.input.Keyboard;

public final class MotionBlurModule extends Module {
    public final NumberSetting strength=addSetting(new NumberSetting("Strength",.65,0,.95,.01));
    public final BooleanSetting turningOnly=addSetting(new BooleanSetting("Only While Turning",false));
    public final NumberSetting turnThreshold=addSetting(new NumberSetting("Turn Threshold",.3,0,5,.1,()->turningOnly.isEnabled()));
    public MotionBlurModule(){super("MotionBlur","Temporal world blur with a sharp HUD",Category.VISUAL,Keyboard.KEY_NONE);}
    /** The same persistence at 30, 60 and 144 FPS. Zero strength is a true bypass. */
    public static float historyWeight(double strength,double seconds){return strength<=0?0:(float)Math.pow(Math.min(.95,strength),Math.max(.001,Math.min(.25,seconds))*60);}
}
