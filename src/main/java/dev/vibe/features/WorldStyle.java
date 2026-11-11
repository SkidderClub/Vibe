package dev.vibe.features;

import dev.vibe.setting.*;
import dev.vibe.ui.RenderUtils;
import java.awt.Color;
import java.util.function.Consumer;

public final class WorldStyle {
    public final ModeSetting mode;public final ColorSetting primary,secondary;
    public final NumberSetting speed,width;public final BooleanSetting fade,glow;
    public WorldStyle(Consumer<Setting<?>> add){this(add,()->true,true);}
    public WorldStyle(Consumer<Setting<?>> add,java.util.function.BooleanSupplier visible,boolean lines){
        mode=new ModeSetting("Color Mode","Static",visible,"Static","Rainbow","Fade");primary=new ColorSetting("Color",0xBB64DFFF,visible);secondary=new ColorSetting("Fade Color",0xBBA078FF,()->visible.getAsBoolean()&&mode.is("Fade"));
        speed=new NumberSetting("Color Speed",1,.1,5,.1,()->visible.getAsBoolean()&&!mode.is("Static"));width=new NumberSetting("Line Width",1.5,.5,4,.1,()->visible.getAsBoolean()&&lines);
        fade=new BooleanSetting("Distance Fade",lines,()->visible.getAsBoolean()&&lines);glow=new BooleanSetting("Glow",true,()->visible.getAsBoolean()&&lines);
        add.accept(mode);add.accept(primary);add.accept(secondary);add.accept(speed);if(lines){add.accept(width);add.accept(fade);add.accept(glow);}
    }
    public int color(double distance,double range,double offset){long now=System.nanoTime()/1000000;double t=(now*.0002*speed.getDouble()+offset)%1;int c=primary.getArgb();
        if(mode.is("Rainbow"))c=(c&0xFF000000)|(Color.HSBtoRGB((float)t,.65F,1)&0xFFFFFF);
        else if(mode.is("Fade"))c=RenderUtils.blend(c,secondary.getArgb(),(float)(.5-.5*Math.cos(t*Math.PI*2)));
        double a=fade.isEnabled()?Math.max(0,Math.min(1,(range-distance)/Math.max(1,range*.35))):1;
        return (c&0xFFFFFF)|((int)((c>>>24)*a)<<24);
    }
}
