package dev.vibe.hitmarker;

import dev.vibe.setting.*;
import java.util.*;
import java.util.function.*;

/** Particle controls are independent of the short-lived crosshair/world marker. */
public final class HitParticleSettings {
    public final List<Setting<?>> all=new ArrayList<>();
    public final NumberSetting count,limit,delay,lifetime,size,speed,lift,gravity,bounce,friction,drag,glowRadius,glowStrength;
    public final ColorSetting color,endColor;
    public final ModeSetting colorMode,easing;
    public final BooleanSetting glow,fade,throughWalls;
    public HitParticleSettings(Function<Setting<?>,Setting<?>> register,BooleanSupplier visible){
        count=add(register,new NumberSetting("Particle Count",12,1,40,1,visible));limit=add(register,new NumberSetting("Particle Maximum",256,32,512,16,visible));
        delay=add(register,new NumberSetting("Particle Delay (ms)",60,0,500,10,visible));lifetime=add(register,new NumberSetting("Particle Lifetime (ms)",1800,250,5000,50,visible));
        size=add(register,new NumberSetting("Particle Size",.04,.015,.12,.005,visible));speed=add(register,new NumberSetting("Particle Spread",1.2,0,5,.1,visible));
        lift=add(register,new NumberSetting("Particle Upward Impulse",.6,0,4,.1,visible));gravity=add(register,new NumberSetting("Particle Gravity",9.8,0,30,.2,visible));
        bounce=add(register,new NumberSetting("Particle Bounce",.6,0,.9,.02,visible));friction=add(register,new NumberSetting("Particle Ground Friction",.4,0,1,.05,visible));
        drag=add(register,new NumberSetting("Particle Air Drag",.4,0,5,.1,visible));
        colorMode=add(register,new ModeSetting("Particle Color Mode","Fade",visible,"Static","Fade","Rainbow"));
        color=add(register,new ColorSetting("Particle Color",0xFF67DFFF,visible));endColor=add(register,new ColorSetting("Particle End Color",0xFFA98AFF,()->visible.getAsBoolean()&&colorMode.is("Fade")));
        glow=add(register,new BooleanSetting("Particle Glow",true,visible));glowRadius=add(register,new NumberSetting("Particle Glow Radius",3.5,1.5,6,.25,()->visible.getAsBoolean()&&glow.isEnabled()));
        glowStrength=add(register,new NumberSetting("Particle Glow Strength",.8,.1,2,.1,()->visible.getAsBoolean()&&glow.isEnabled()));
        fade=add(register,new BooleanSetting("Particle Fade",true,visible));easing=add(register,new ModeSetting("Particle Fade Easing","Smooth",()->visible.getAsBoolean()&&fade.isEnabled(),"Linear","Smooth","Ease Out"));
        throughWalls=add(register,new BooleanSetting("Particle Through Walls",false,visible));
    }
    private <T extends Setting<?>> T add(Function<Setting<?>,Setting<?>> register,T setting){register.apply(setting);all.add(setting);return setting;}
}
