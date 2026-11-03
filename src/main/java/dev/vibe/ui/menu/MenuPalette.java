package dev.vibe.ui.menu;

import java.io.InputStream;
import java.nio.file.*;
import java.util.Properties;

/** Immutable colours, readable by the loading screen before Minecraft or Gson exists. */
public final class MenuPalette {
    public static final MenuPalette LAVENDER=new MenuPalette("LAVENDER","Lavender",0xFFB4A0FF,0xFF111218,0xFF191B24,0xFFA8A4BA,0xFF8751DF,0xFFD7BCFF);
    public static final MenuPalette OCEAN=new MenuPalette("OCEAN","Ocean",0xFF79BFFF,0xFF10151C,0xFF18222D,0xFFA0B4C8,0xFF087DBB,0xFF91E2FF);
    public static final MenuPalette MINT=new MenuPalette("MINT","Mint",0xFF7DDDC3,0xFF101817,0xFF182522,0xFFA0BCB1,0xFF18A67C,0xFFA3F5D0);
    public static final MenuPalette ROSE=new MenuPalette("ROSE","Rose",0xFFF0A0BA,0xFF191216,0xFF281C23,0xFFC0A7B3,0xFFCC4F82,0xFFFFB8D1);
    public static final MenuPalette AMBER=new MenuPalette("AMBER","Amber",0xFFE8BF7A,0xFF191611,0xFF272219,0xFFC1B29C,0xFFC18623,0xFFFFD58C);
    public static final MenuPalette GRAPHITE=new MenuPalette("GRAPHITE","Graphite",0xFFBEC5D0,0xFF131416,0xFF202226,0xFFB0B5BD,0xFF64707D,0xFFCAD1D9);
    private static final MenuPalette[] VALUES={LAVENDER,OCEAN,MINT,ROSE,AMBER,GRAPHITE};
    public final String label;
    public final int accent,background,surface,muted,lava,light;
    private final String key,mode;private final int first,second;private final double speed;
    private MenuPalette(String key,String label,int accent,int background,int surface,int muted,int lava,int light){this(key,label,accent,background,surface,muted,lava,light,"Solid",accent,accent,1);}
    private MenuPalette(String key,String label,int accent,int background,int surface,int muted,int lava,int light,String mode,int first,int second,double speed){this.key=key;this.label=label;this.accent=accent;this.background=background;this.surface=surface;this.muted=muted;this.lava=lava;this.light=light;this.mode=mode;this.first=first;this.second=second;this.speed=speed;}
    public String name(){return key;}public int ordinal(){for(int i=0;i<VALUES.length;i++)if(VALUES[i]==this)return i;return -1;}
    public static MenuPalette[] values(){return VALUES.clone();}
    public static MenuPalette valueOf(String name){for(MenuPalette p:VALUES)if(p.key.equals(name))return p;throw new IllegalArgumentException("Unknown palette");}
    public boolean animated(){return !mode.equals("Solid");}
    public static MenuPalette custom(String label,int first,int second,String mode,double speed){
        int accent=first|0xFF000000;return new MenuPalette("CUSTOM",label,accent,blend(0xFF101218,accent,.06F),blend(0xFF181D25,accent,.10F),blend(0xFFAFB6C4,accent,.15F),blend(accent,0xFF000000,.32F),blend(accent,0xFFFFFFFF,.30F),mode,first,second,Math.max(.1,Math.min(3,speed)));
    }
    public MenuPalette sample(long millis){
        if(!animated())return this;double phase=millis/12000D*speed;int color;
        if(mode.equals("Rainbow"))color=java.awt.Color.HSBtoRGB((float)(phase-Math.floor(phase)),.60F,1);
        else color=blend(first,second,(float)(.5-.5*Math.cos(phase*Math.PI*2)));
        return custom(label,color,color,"Solid",speed);
    }
    private static int blend(int a,int b,float f){return 0xFF000000|Math.round((a>>16&255)*(1-f)+(b>>16&255)*f)<<16|Math.round((a>>8&255)*(1-f)+(b>>8&255)*f)<<8|Math.round((a&255)*(1-f)+(b&255)*f);}
    public static MenuPalette read(Path gameDirectory){
        try(InputStream input=Files.newInputStream(gameDirectory.resolve("vibe/menu.properties"))){Properties p=new Properties();p.load(input);
            if(p.containsKey("color1"))return custom(p.getProperty("name","Custom"),parse(p.getProperty("color1")),parse(p.getProperty("color2",p.getProperty("color1"))),p.getProperty("colorMode","Solid"),Double.parseDouble(p.getProperty("colorSpeed","1")));
            return valueOf(p.getProperty("theme","OCEAN"));
        }catch(Exception invalid){return OCEAN;}
    }
    static int parse(String value){return (int)Long.parseLong(value.replace("#",""),16)|0xFF000000;}
}
