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
    public static final class BlockCounter extends OverlaySettings {
        public final BooleanSetting block,scaffold,eagle,warning,bar,animate,label;
        public final ModeSetting calculate,easing,barMode;
        public final BooleanSetting compact,percentage;
        public final NumberSetting yellowAt,redAt,duration,width,barThickness,ringSize;
        public final ColorSetting yellow,red,barEmpty,barColor;
        public BlockCounter(Function<Setting<?>,Setting<?>> r){super(r,"Block Counter");
            block=bool("Display Block",true);scaffold=bool("While Scaffold",true);eagle=bool("While Eagle",false);
            calculate=add(new ModeSetting("Block Counter Calculate","Total","Slot","Total"));
            label=bool("Show Label",true);warning=bool("Warning",true);yellowAt=number("Yellow Below",32,1,576,1);redAt=number("Red Below",8,0,576,1);
            yellow=color("Warning Yellow",0xFFFFD46A);red=color("Warning Red",0xFFFF6476);
            bar=bool("Depletion Bar",true);barEmpty=color("Empty Bar",0x60344557);width=number("Minimum Width",96,24,240,1);
            compact=bool("Compact",true);barMode=add(new ModeSetting("Block Counter Bar Style","Line","Line","Circle"));
            percentage=bool("Show Percentage",false);barThickness=number("Bar Thickness",2,1,6,.5);ringSize=number("Circle Size",22,16,40,1);barColor=color("Bar Color",0xFF79BFFF);
            animate=bool("Animation",true);duration=number("Animation Duration (ms)",220,50,800,10);
            easing=add(new ModeSetting("Block Counter Easing","Smoothstep","Linear","Smoothstep","Cubic"));
        }
    }
    public static final class DynamicIsland extends OverlaySettings {
        public final MultiSelectSetting transforms;
        public final BooleanSetting logo,animate;
        public final StringSetting title;
        public final ModeSetting easing,preview;
        public final NumberSetting idleWidth,idleHeight,duration;
        public DynamicIsland(Function<Setting<?>,Setting<?>> r){super(r,"Dynamic Island");
            transforms=add(new MultiSelectSetting("Dynamic Island Views",Arrays.asList("Tab","Notification","TargetHud"),Arrays.asList("Tab","Notification","TargetHud")));
            logo=bool("Logo",true);title=add(new StringSetting("Dynamic Island Text","Vibe",32,()->true));
            idleWidth=number("Idle Width",108,70,240,1);idleHeight=number("Idle Height",28,22,48,1);
            animate=bool("Animation",true);duration=number("Transition (ms)",260,80,600,10);easing=add(new ModeSetting("Dynamic Island Easing","Smoothstep","Linear","Smoothstep","Cubic"));
            preview=add(new ModeSetting("Dynamic Island Editor Preview","Idle","Idle","Tab","Notification","TargetHud"));radius.setValue(14D);
        }
    }
    public static final class Hotbar extends OverlaySettings {
        public final ModeSetting selectionStyle,selectionEase;
        public final NumberSetting selectionWidth,selectionRadius,selectionPadding,selectionSpeed,selectionGlowStrength;
        public final BooleanSetting selectionGlow;
        public final ColorSetting selectionFill;
        public final NumberSetting slotSize,gap;
        public final BooleanSetting numbers,durability,counts,animate,experience,itemName;
        public final ColorSetting selected,empty,common,uncommon,rare,epic,experienceColor;
        public Hotbar(Function<Setting<?>,Setting<?>> r){super(r,"Hotbar");slotSize=super.number("Slot Size",24,20,40,1);gap=super.number("Slot Gap",3,0,8,1);
            numbers=super.bool("Show Key Labels",false);durability=super.bool("Durability",true);counts=super.bool("Item Counts",true);animate=super.bool("Animate Selection",true);
            experience=super.bool("Experience Bar",true);itemName=super.bool("Selected Item Name",true);experienceColor=super.color("Experience Color",0xFF8DD34F);
            selected=super.color("Selected Slot",0xFF91E2FF);empty=super.color("Empty Slot",0x70232B38);
            selectionStyle=add(new ModeSetting("Hotbar Selection Style","Outline","Outline","Fill","Underline","Fill and Outline","Native","None"));
            selectionEase=add(new ModeSetting("Hotbar Selection Easing","Exponential","Exponential","Smoothstep","Cubic","Linear"));
            selectionWidth=number("Selection Thickness",1.4,.5,4,.1);selectionRadius=number("Selection Corners",3,0,12,1);selectionPadding=number("Selection Padding",0,-3,4,.5);
            selectionSpeed=number("Selection Speed",22,4,50,1);selectionGlow=bool("Selection Glow",false);selectionGlowStrength=number("Selection Glow Strength",.35,.05,1,.05);selectionFill=color("Selection Fill",0x3379BFFF);
            common=super.color("Common Rarity",0xFF8F9AA8);uncommon=super.color("Uncommon Rarity",0xFF62BD65);rare=super.color("Rare Rarity",0xFF4C9DFF);epic=super.color("Epic Rarity",0xFFBC70ED);}
    }
    public static final class Effects extends OverlaySettings{
        public final BooleanSetting icons,names,timers,amplifier,bar,potionColors;
        public final NumberSetting width,rowHeight,gap,barHeight;
        public final ModeSetting sort;
        public Effects(Function<Setting<?>,Setting<?>> r){super(r,"Effects");icons=bool("Icons",true);names=bool("Names",true);timers=bool("Timers",true);amplifier=bool("Amplifier",true);bar=bool("Timer Bar",true);potionColors=bool("Effect Colors",true);
            width=number("Width",154,96,300,1);rowHeight=number("Row Height",30,24,48,1);gap=number("Gap",4,0,12,1);barHeight=number("Bar Height",2,1,5,.5);sort=add(new ModeSetting("Effects Sort","Time","Time","Name","Vanilla"));}
    }
    public static final class Keystrokes extends OverlaySettings{
        public final BooleanSetting leftClick,rightClick,space,animation;
        public final NumberSetting size,gap,speed,textScale;
        public final ColorSetting pressed,pressedText;
        public Keystrokes(Function<Setting<?>,Setting<?>> r){super(r,"Keystrokes");
            leftClick=bool("Left Click",true);rightClick=bool("Right Click",true);space=bool("Space",true);animation=bool("Animation",true);
            size=number("Key Size",24,18,42,1);gap=number("Key Gap",3,0,8,1);speed=number("Animation Speed",18,4,40,1);textScale=number("Text Scale",1,.6,1.5,.05);
            pressed=color("Pressed Background",0xA579BFFF);pressedText=color("Pressed Text",0xFFFFFFFF);
        }
    }
    public static final class Containers extends OverlaySettings{
        public final MultiSelectSetting screens;
        public final ColorSetting slots,slotBorder;
        public final BooleanSetting slotOutline,centerInventory;
        public Containers(Function<Setting<?>,Setting<?>> r){super(r,"Custom GUIs");
            screens=add(new MultiSelectSetting("Custom GUIs Screens",Arrays.asList("Inventory","Chests","Crafting","Furnaces","Dispensers","Hoppers","Brewing","Anvils","Enchanting","Trading","Beacons"),Arrays.asList("Inventory","Chests","Crafting","Furnaces","Dispensers","Hoppers","Brewing","Anvils","Enchanting","Trading","Beacons")));
            slots=color("Slot Background",0x24232D3B);slotBorder=color("Slot Border",0x404A657B);slotOutline=bool("Slot Outlines",true);
            centerInventory=bool("Center Inventory with Effects",true);
        }
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
