package dev.vibe.ambience;

import dev.vibe.setting.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Independent controls for local world decorations. */
public final class AmbienceSettings {
    private AmbienceSettings(){}
    private static <T extends Setting<?>> T add(Consumer<Setting<?>> register,T value){register.accept(value);return value;}
    public static final class Fireflies {
        public final NumberSetting count,radius,speed,size,glow,trail;
        public final ColorSetting color,trailColor;
        public final BooleanSetting nightOnly;
        public Fireflies(Consumer<Setting<?>> r,BooleanSupplier v){
            count=add(r,new NumberSetting("Firefly Count",32,4,96,1,v));
            radius=add(r,new NumberSetting("Firefly Range",14,4,32,1,v));
            speed=add(r,new NumberSetting("Firefly Speed",1,.2,3,.05,v));
            size=add(r,new NumberSetting("Firefly Size",.045,.02,.12,.005,v));
            color=add(r,new ColorSetting("Firefly Color",0xFFFFE692,v));
            glow=add(r,new NumberSetting("Firefly Glow",.8,0,2,.05,v));
            trail=add(r,new NumberSetting("Firefly Trail",.9,0,2,.05,v));
            trailColor=add(r,new ColorSetting("Firefly Trail Color",0xFFB6E88C,v));
            nightOnly=add(r,new BooleanSetting("Fireflies Night Only",true,v));
        }
    }
    public static final class Celestial {
        public final NumberSetting size,brightness,glow,softness;
        public final ColorSetting color,glowColor;
        public final ModeSetting phase;
        public Celestial(Consumer<Setting<?>> r,BooleanSupplier v,boolean moon){
            String n=moon?"Moon":"Sun";
            size=add(r,new NumberSetting(n+" Size",moon?1.2:1,.25,3,.05,v));
            color=add(r,new ColorSetting(n+" Color",moon?0xFFD4E3F5:0xFFFFE6BC,v));
            brightness=add(r,new NumberSetting(n+" Brightness",1,.1,2,.05,v));
            glowColor=add(r,new ColorSetting(n+" Glow Color",moon?0xFF82A9DC:0xFFFFB15B,v));
            glow=add(r,new NumberSetting(n+" Glow",moon?.4:.7,0,2,.05,v));
            softness=add(r,new NumberSetting(n+" Halo Size",1,.3,2,.05,v));
            phase=moon?add(r,new ModeSetting("Moon Phase","World",v,"World","Full","Half","Crescent")):null;
        }
    }
    public static final class ShootingStars {
        public final NumberSetting interval,speed,length,width,glow;
        public final ColorSetting color;
        public final BooleanSetting nightOnly;
        public ShootingStars(Consumer<Setting<?>> r,BooleanSupplier v){
            interval=add(r,new NumberSetting("Star Interval",7,1,30,.5,v));
            speed=add(r,new NumberSetting("Star Speed",1,.3,3,.05,v));
            length=add(r,new NumberSetting("Star Tail Length",16,3,35,1,v));
            width=add(r,new NumberSetting("Star Width",.12,.04,.5,.01,v));
            glow=add(r,new NumberSetting("Star Glow",.8,0,2,.05,v));
            color=add(r,new ColorSetting("Star Color",0xFFC7E8FF,v));
            nightOnly=add(r,new BooleanSetting("Shooting Stars Night Only",true,v));
        }
    }
    public static final class Aurora {
        public final ColorSetting lower,upper;
        public final NumberSetting brightness,speed,height,direction;
        public final ModeSetting quality;
        public final BooleanSetting nightOnly;
        public Aurora(Consumer<Setting<?>> r,BooleanSupplier v){
            lower=add(r,new ColorSetting("Polar Lower Color",0xFF70EBC2,v));
            upper=add(r,new ColorSetting("Polar Upper Color",0xFF9383DB,v));
            brightness=add(r,new NumberSetting("Polar Brightness",.55,.05,1.5,.05,v));
            speed=add(r,new NumberSetting("Polar Speed",.35,.05,2,.05,v));
            height=add(r,new NumberSetting("Polar Height",1,.4,1.6,.05,v));
            direction=add(r,new NumberSetting("Polar Direction",0,0,360,1,v));
            quality=add(r,new ModeSetting("Polar Quality","Medium",v,"Low","Medium","High"));
            nightOnly=add(r,new BooleanSetting("Polar Lights Night Only",true,v));
        }
    }
}
