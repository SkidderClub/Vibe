package dev.vibe.hud;

import dev.vibe.setting.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class TargetHudSettings {
    public final List<Setting<?>> all=new ArrayList<Setting<?>>();
    public final ModeSetting mode, source;
    public final ColorSetting health, healthEnd, background, text, particleColor;
    public final BooleanSetting gradient, absorption, ping, smoothing, damageTint, particles;
    public final NumberSetting linger, width, radius, particleCount, particleSize, particleLifetime;

    public TargetHudSettings(Function<Setting<?>,Setting<?>> register) {
        mode=add(register,new ModeSetting("Target HUD Mode","Novoline","Novoline","Tenacity","Zeroday","Rise","LiquidGlass"));
        source=add(register,new ModeSetting("Target HUD Source","Both","Combat","Crosshair","Both"));
        linger=add(register,new NumberSetting("Target HUD Keep Visible",3,0,10,.25));
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
        particles=add(register,new BooleanSetting("Target HUD Particles",true,()->mode.is("Rise")));
        particleColor=add(register,new ColorSetting("Target HUD Particle Color",0xFFB9AEFF,()->mode.is("Rise")&&particles.isEnabled()));
        particleCount=add(register,new NumberSetting("Target HUD Particle Count",14,4,32,1,()->mode.is("Rise")&&particles.isEnabled()));
        particleSize=add(register,new NumberSetting("Target HUD Particle Size",2,1,4,.25,()->mode.is("Rise")&&particles.isEnabled()));
        particleLifetime=add(register,new NumberSetting("Target HUD Particle Lifetime",750,250,1500,50,()->mode.is("Rise")&&particles.isEnabled()));
    }
    private <T extends Setting<?>> T add(Function<Setting<?>,Setting<?>> register,T setting) {
        register.apply(setting);all.add(setting);return setting;
    }
}
