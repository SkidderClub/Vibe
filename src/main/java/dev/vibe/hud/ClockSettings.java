package dev.vibe.hud;

import dev.vibe.setting.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class ClockSettings {
    public final List<Setting<?>> all = new ArrayList<Setting<?>>();
    public final ModeSetting display;
    public final BooleanSetting seconds, smooth, numbers, digital, date, twelveHour;
    public final NumberSetting diameter;
    public final ColorSetting face, marks, hands, secondHand, text;

    public ClockSettings(Function<Setting<?>, Setting<?>> register) {
        display=add(register,new ModeSetting("Clock Display","Digital","Digital","Analog"));
        diameter=add(register,new NumberSetting("Clock Diameter",88,56,160,2,()->display.is("Analog")));
        seconds=add(register,new BooleanSetting("Clock Seconds",true));
        smooth=add(register,new BooleanSetting("Clock Smooth Seconds",true,()->display.is("Analog")&&seconds.isEnabled()));
        numbers=add(register,new BooleanSetting("Clock Numbers",true,()->display.is("Analog")));
        digital=add(register,new BooleanSetting("Clock Digital Readout",true,()->display.is("Analog")));
        date=add(register,new BooleanSetting("Clock Date",false));
        twelveHour=add(register,new BooleanSetting("Clock 12 Hour",false));
        face=add(register,new ColorSetting("Clock Face",0xE9151E2C,()->display.is("Analog")));
        marks=add(register,new ColorSetting("Clock Markers",0xFFA7B7CA,()->display.is("Analog")||date.isEnabled()));
        hands=add(register,new ColorSetting("Clock Hands",0xFFF3F7FF,()->display.is("Analog")));
        secondHand=add(register,new ColorSetting("Clock Second Hand",0xFF5FE1CD,()->display.is("Analog")&&seconds.isEnabled()));
        text=add(register,new ColorSetting("Clock Text",0xFFF3F7FF));
    }
    private <T extends Setting<?>> T add(Function<Setting<?>,Setting<?>> register,T setting) {
        register.apply(setting);all.add(setting);return setting;
    }
}
