package dev.vibe.hud;

import dev.vibe.setting.*;
import java.util.*;
import java.util.function.Function;

public final class GhostSettings {
    public final List<Setting<?>> all=new ArrayList<>();
    public final ModeSetting mode;
    public final BooleanSetting flipX,flipY,override;
    public final ColorSetting color;
    public final NumberSetting size,speed,opacity;
    public GhostSettings(Function<Setting<?>,Setting<?>> register){
        mode=add(register,new ModeSetting("Ghosts Mode","Animated","Static","Animated"));
        size=add(register,new NumberSetting("Ghosts Size",80,24,240,1));
        speed=add(register,new NumberSetting("Ghosts Animation Speed",12,1,30,1,()->mode.is("Animated")));
        flipX=add(register,new BooleanSetting("Ghosts Flip Horizontally",false));flipY=add(register,new BooleanSetting("Ghosts Flip Vertically",false));
        override=add(register,new BooleanSetting("Ghosts Color Override",false));color=add(register,new ColorSetting("Ghosts Color",0xFF9ECFFF,()->override.isEnabled()));
        opacity=add(register,new NumberSetting("Ghosts Opacity",1,.1,1,.05));
    }
    private <T extends Setting<?>> T add(Function<Setting<?>,Setting<?>> register,T setting){register.apply(setting);all.add(setting);return setting;}
}
