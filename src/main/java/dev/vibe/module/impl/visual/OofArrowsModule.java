package dev.vibe.module.impl.visual;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.*;
import java.util.Arrays;
import org.lwjgl.input.Keyboard;

public final class OofArrowsModule extends Module {
    public final NumberSetting radius=addSetting(new NumberSetting("Radius",90,20,300,1));
    public final ModeSetting placement=addSetting(new ModeSetting("Placement","Oval","Circle","Oval"));
    public final ModeSetting style=addSetting(new ModeSetting("Arrow Style","Arrow","Arrow","Triangle","Chevron"));
    public final NumberSetting size=addSetting(new NumberSetting("Size",11,5,24,.5));
    public final NumberSetting width=addSetting(new NumberSetting("Width",.8,.35,1.5,.05));
    public final MultiSelectSetting entities=addSetting(new MultiSelectSetting("Entities",Arrays.asList("Players","Hostile mobs","Animals"),Arrays.asList("Players")));
    public final BooleanSetting invisibleOnly=addSetting(new BooleanSetting("Invisible Only",false));
    public final BooleanSetting occludedOnly=addSetting(new BooleanSetting("Occluded Only",false));
    public final BooleanSetting ignoreFriends=addSetting(new BooleanSetting("Ignore Friends",true));
    public final NumberSetting range=addSetting(new NumberSetting("Range",128,8,512,4));
    public final NumberSetting limit=addSetting(new NumberSetting("Arrow Limit",24,1,64,1));
    public final ModeSetting colorMode=addSetting(new ModeSetting("Color Mode","Static","Static","Fade","Rainbow"));
    public final ColorSetting color=addSetting(new ColorSetting("Color",0xFFAA89FF));
    public final ColorSetting secondary=addSetting(new ColorSetting("Fade Color",0xFF50DBD2,()->colorMode.is("Fade")));
    public final NumberSetting speed=addSetting(new NumberSetting("Color Speed",1,.1,5,.1,()->!colorMode.is("Static")));
    public final NumberSetting saturation=addSetting(new NumberSetting("Rainbow Saturation",.8,0,1,.05,()->colorMode.is("Rainbow")));
    public final BooleanSetting distanceFade=addSetting(new BooleanSetting("Distance Fade",true));
    public final NumberSetting minimumOpacity=addSetting(new NumberSetting("Minimum Opacity",.25,.05,1,.05,()->distanceFade.isEnabled()));
    public final BooleanSetting glow=addSetting(new BooleanSetting("Glow",true));
    public final NumberSetting glowRadius=addSetting(new NumberSetting("Glow Radius",5,1,12,.5,()->glow.isEnabled()));
    public final NumberSetting glowStrength=addSetting(new NumberSetting("Glow Strength",.5,.05,1,.05,()->glow.isEnabled()));
    public final BooleanSetting outline=addSetting(new BooleanSetting("Outline",true));
    public final ColorSetting outlineColor=addSetting(new ColorSetting("Outline Color",0xAA10121C,()->outline.isEnabled()));
    public final BooleanSetting names=addSetting(new BooleanSetting("Names",false));
    public final BooleanSetting distances=addSetting(new BooleanSetting("Distances",false));
    public OofArrowsModule() {super("OOF Arrows","Point toward entities outside the camera view",Category.VISUAL,Keyboard.KEY_NONE);}
}
