package dev.vibe.hud;

import dev.vibe.setting.*;
import java.util.*;
import java.util.function.Function;

public final class WatermarkSettings {
    public final HudTextStyle client;
    public final ColorSetting tags,separatorColor;
    public final ModeSetting separator;
    public final StringSetting customSeparator;
    public final BooleanSetting shadow;
    public final List<Setting<?>> all=new ArrayList<>();
    public WatermarkSettings(Function<Setting<?>,Setting<?>> register){
        client=new HudTextStyle(register,"Watermark Client Name",0xFF2DE2C2,()->true);all.addAll(client.all);
        tags=new ColorSetting("Watermark Tags Color",0xFFE5EDF5);separatorColor=new ColorSetting("Watermark Separator Color",0xFF778A9E);
        separator=new ModeSetting("Watermark Separator","Bar","Bar","Dot","Bullet","Dash","Colon","Diamond","None","Custom");
        customSeparator=new StringSetting("Watermark Custom Separator","·",8,()->separator.is("Custom"));shadow=new BooleanSetting("Watermark Text Shadow",true);
        Collections.addAll(all,tags,separatorColor,separator,customSeparator,shadow);
        for(Setting<?> s:Arrays.<Setting<?>>asList(tags,separatorColor,separator,customSeparator,shadow))register.apply(s);
    }
    public String separatorText(){
        switch(separator.getValue()){
            case "Dot":return " . ";case "Bullet":return " • ";case "Dash":return " - ";case "Colon":return " : ";case "Diamond":return " ◇ ";
            case "None":return "  ";case "Custom":return " "+customSeparator.getValue()+" ";default:return " | ";
        }
    }
}
