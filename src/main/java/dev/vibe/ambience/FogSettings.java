/* Copyright (c) 2024. Schizoid. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * Fog controls ported for Vibe/Forge 1.8.9; moved into Ambience on 2026-11-04.
 * See LICENSES/SCHIZOID.md for the pinned source and changes.
 */
package dev.vibe.ambience;

import dev.vibe.setting.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Depth fog controls, registered directly on Ambience for profiles and every ClickGUI. */
public final class FogSettings {
    public final BooleanSetting blur, sky, vanilla;
    public final ModeSetting method, tint;
    public final NumberSetting strength, start, end, opacity, tintOpacity, saturation, brightness, speed, fadeSpeed;
    public final ColorSetting color, secondaryColor;
    public FogSettings(Consumer<Setting<?>> register, BooleanSupplier visible) {
        blur=add(register,new BooleanSetting("Blur",true,visible));
        method=add(register,new ModeSetting("Blur Method","Kawase",()->visible.getAsBoolean()&&blur.isEnabled(),"Kawase","Gaussian"));
        strength=add(register,new NumberSetting("Blur Strength",6,1,20,1,()->visible.getAsBoolean()&&blur.isEnabled()));
        start=add(register,new NumberSetting("Start Distance",12,0,256,1,visible));
        end=add(register,new NumberSetting("End Distance",64,1,512,1,visible));
        opacity=add(register,new NumberSetting("Fog Opacity",100,0,100,1,visible));
        tint=add(register,new ModeSetting("Color Mode","Scene",visible,"Scene","Love","Red","Green","Blue","Yellow","Gray","Purple","LightBlue","Custom","China","Bavaria","Catton Candy"));
        color=add(register,new ColorSetting("Primary Color",0xFFB8CBD6,()->visible.getAsBoolean()&&tint.is("Custom")));
        secondaryColor=add(register,new ColorSetting("Secondary Color",0xFFFF99CC,()->visible.getAsBoolean()&&tint.is("Custom")));
        tintOpacity=add(register,new NumberSetting("Color Opacity",30,0,100,1,()->visible.getAsBoolean()&&!tint.is("Scene")));
        saturation=add(register,new NumberSetting("Rainbow Saturation",70,0,100,1,()->visible.getAsBoolean()&&tint.is("Love")));
        brightness=add(register,new NumberSetting("Rainbow Brightness",100,0,100,1,()->visible.getAsBoolean()&&tint.is("Love")));
        speed=add(register,new NumberSetting("Rainbow Speed",1,0,5,.05,()->visible.getAsBoolean()&&tint.is("Love")));
        fadeSpeed=add(register,new NumberSetting("Fade Speed",1,0,5,.05,()->visible.getAsBoolean()&&twoTone()));
        sky=add(register,new BooleanSetting("Affect Sky",true,visible));
        vanilla=add(register,new BooleanSetting("Keep Vanilla Fog",false,visible));
    }
    private static <T extends Setting<?>> T add(Consumer<Setting<?>> register,T setting){register.accept(setting);return setting;}
    public float endDistance(){return Math.max(start.getFloat()+.1F,end.getFloat());}
    public boolean twoTone(){return !tint.is("Scene")&&!tint.is("Love");}
    public int primaryColor(){return dev.vibe.ui.VisualPalette.primary(tint.getValue(),color.getArgb());}
    public int secondaryColor(){return dev.vibe.ui.VisualPalette.secondary(tint.getValue(),primaryColor(),secondaryColor.getArgb());}
}
