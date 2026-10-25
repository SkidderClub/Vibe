package dev.vibe.hud;

import dev.vibe.setting.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class TargetHudSettings {
    public final List<Setting<?>> all=new ArrayList<Setting<?>>();
    public final ModeSetting mode;
    public final ColorSetting health, healthEnd, background, text, particleColor;
    public final BooleanSetting gradient, absorption, ping, smoothing, damageTint, particles, animations;
    public final NumberSetting linger, width, radius, particleCount, particleSize, particleLifetime, maxTargets, animationDuration, gap;

    public TargetHudSettings(Function<Setting<?>,Setting<?>> register) {
        mode=add(register,new ModeSetting("Target HUD Mode","Novoline","Novoline","Tenacity","Zeroday","Rise","LiquidGlass"));
        linger=add(register,new NumberSetting("Target HUD Keep Visible",3,0,10,.25));
        maxTargets=add(register,new NumberSetting("Target HUD Max Targets",4,1,8,1));
        gap=add(register,new NumberSetting("Target HUD Card Spacing",6,0,20,1));
        animations=add(register,new BooleanSetting("Target HUD Animations",true));
        animationDuration=add(register,new NumberSetting("Target HUD Animation Duration",220,100,500,10,()->animations.isEnabled()));
        width=add(register,new NumberSetting("Target HUD Width",154,130,260,2));
        health=add(register,new ColorSetting("Target HUD Health Color",0xFFEC78DD));
        gradient=add(register,new BooleanSetting("Target HUD Gradient",true,()->!mode.is("Novoline")&&!mode.is("Zeroday")));
        healthEnd=add(register,new ColorSetting("Target HUD Gradient End",0xFF678FFF,()->gradient.isEnabled()&&!mode.is("Novoline")&&!mode.is("Zeroday")));
        background=add(register,new ColorSetting("Target HUD Background",0xB51A1C23,()->!mode.is("LiquidGlass")));
        text=add(register,new ColorSetting("Target HUD Text",0xFFF4F5FA));
        radius=add(register,new NumberSetting("Target HUD Corners",10,0,22,1,()->!mode.is("Novoline")&&!mode.is("Zeroday")));
        absorption=add(register,new BooleanSetting("Target HUD Absorption",true));
        ping=add(register,new BooleanSetting("Target HUD Ping",true,()->mode.is("Zeroday")||mode.is("LiquidGlass")));
        smoothing=add(register,new BooleanSetting("Target HUD Smooth Health",true));
        damageTint=add(register,new BooleanSetting("Target HUD Damage Tint",true));
        particles=add(register,new BooleanSetting("Target HUD Particles",true,this::supportsParticles));
        particleColor=add(register,new ColorSetting("Target HUD Particle Color",0xFFB9AEFF,()->supportsParticles()&&particles.isEnabled()));
        particleCount=add(register,new NumberSetting("Target HUD Particle Count",14,4,32,1,()->supportsParticles()&&particles.isEnabled()));
        particleSize=add(register,new NumberSetting("Target HUD Particle Size",2,1,4,.25,()->supportsParticles()&&particles.isEnabled()));
        particleLifetime=add(register,new NumberSetting("Target HUD Particle Lifetime",750,250,1500,50,()->supportsParticles()&&particles.isEnabled()));
    }
    public boolean supportsParticles() {return mode.is("Rise")||mode.is("LiquidGlass");}
    private <T extends Setting<?>> T add(Function<Setting<?>,Setting<?>> register,T setting) {
        register.apply(setting);all.add(setting);return setting;
    }
}
