package dev.vibe.hud;

import dev.vibe.setting.*;
import java.util.*;
import java.util.function.Function;

/** Independent, persisted settings for the native HUD replacements. */
public abstract class OverlaySettings {
    public final List<Setting<?>> all=new ArrayList<>();
    private final Function<Setting<?>,Setting<?>> register;
    private final String prefix;
    public final ColorSetting background,accent,text;
    public final NumberSetting radius;
    public final BooleanSetting shadow,border;
    private OverlaySettings(Function<Setting<?>,Setting<?>> register,String prefix){
        this.register=register;this.prefix=prefix;
        background=color("Background",0xC4141B27);accent=color("Accent",0xFF79BFFF);text=color("Text",0xFFF0F3F9);
        radius=number("Corner Radius",6,0,16,1);shadow=bool("Text Shadow",true);border=bool("Border",true);
    }
    protected <T extends Setting<?>> T add(T value){register.apply(value);all.add(value);return value;}
    protected NumberSetting number(String name,double value,double min,double max,double step){return add(new NumberSetting(prefix+" "+name,value,min,max,step));}
    protected ColorSetting color(String name,int value){return add(new ColorSetting(prefix+" "+name,value));}
    protected BooleanSetting bool(String name,boolean value){return add(new BooleanSetting(prefix+" "+name,value));}
    public static final class Chat extends OverlaySettings {
        public final NumberSetting width,height,lineHeight,fadeSeconds,opacity;
        public final BooleanSetting fade,keepColors,scrollbar;
        public Chat(Function<Setting<?>,Setting<?>> r){super(r,"Chat");width=super.number("Width",320,120,640,1);height=super.number("Height",150,40,360,1);
            lineHeight=super.number("Line Height",11,9,20,1);fadeSeconds=super.number("Fade Seconds",10,2,60,1);opacity=super.number("Opacity",1,.1,1,.05);
            fade=super.bool("Fade Messages",true);keepColors=super.bool("Keep Message Colors",true);scrollbar=super.bool("Scrollbar",true);}
    }
    public static final class Hotbar extends OverlaySettings {
        public final NumberSetting slotSize,gap;
        public final BooleanSetting numbers,durability,counts,animate,experience,itemName;
        public final ColorSetting selected,empty,common,uncommon,rare,epic,experienceColor;
        public Hotbar(Function<Setting<?>,Setting<?>> r){super(r,"Hotbar");slotSize=super.number("Slot Size",24,20,40,1);gap=super.number("Slot Gap",3,0,8,1);
            numbers=super.bool("Show Key Labels",false);durability=super.bool("Durability",true);counts=super.bool("Item Counts",true);animate=super.bool("Animate Selection",true);
            experience=super.bool("Experience Bar",true);itemName=super.bool("Selected Item Name",true);experienceColor=super.color("Experience Color",0xFF8DD34F);
            selected=super.color("Selected Slot",0xFF91E2FF);empty=super.color("Empty Slot",0x70232B38);
            common=super.color("Common Rarity",0xFF8F9AA8);uncommon=super.color("Uncommon Rarity",0xFF62BD65);rare=super.color("Rare Rarity",0xFF4C9DFF);epic=super.color("Epic Rarity",0xFFBC70ED);}
    }
    public static final class Health extends OverlaySettings {
        public final ModeSetting mode;
        public final NumberSetting width,height;
        public final BooleanSetting numbers,absorption,armor,smooth;
        public final NumberSetting speed;
        public final ColorSetting high,low,shield,empty;
        public Health(Function<Setting<?>,Setting<?>> r){super(r,"Custom Health");mode=super.add(new ModeSetting("Custom Health Mode","Fancy","Fancy","Recolor","Fortnite"));
            width=super.number("Width",182,90,360,1);height=super.number("Bar Height",8,5,20,1);numbers=super.bool("Show Values",false);absorption=super.bool("Absorption",true);armor=super.bool("Armor",true);
            smooth=super.bool("Smooth Bars",true);speed=super.number("Animation Speed",12,2,30,1);
            high=super.color("Full Health",0xFF6BE58A);low=super.color("Low Health",0xFFFF5968);shield=super.color("Absorption Color",0xFF65B6FF);empty=super.color("Empty Bar",0x90333B47);}
    }
    public static final class Tab extends OverlaySettings {
        public final BooleanSetting toggle,heads,ping,header,footer,serverScores;
        public final NumberSetting columns,rows,rowHeight,width;
        public final StringSetting title;
        public Tab(Function<Setting<?>,Setting<?>> r){super(r,"Tab");toggle=super.bool("Toggle With Key",false);heads=super.bool("Player Heads",true);ping=super.bool("Ping",true);
            header=super.bool("Server Header",true);footer=super.bool("Server Footer",true);serverScores=super.bool("Scoreboard Values",true);
            columns=super.number("Max Columns",4,1,4,1);rows=super.number("Rows",20,5,40,1);rowHeight=super.number("Row Height",16,12,28,1);width=super.number("Column Width",170,100,260,1);
            title=super.add(new StringSetting("Tab Heading","",48,null));}
    }
}
