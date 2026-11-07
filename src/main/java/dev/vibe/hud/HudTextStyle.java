package dev.vibe.hud;

import dev.vibe.setting.*;
import dev.vibe.ui.RenderUtils;
import java.awt.Color;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.gui.FontRenderer;

/** Native font metrics, with the same per-letter colors and soft glow as the ArrayList. */
public final class HudTextStyle {
    public final List<Setting<?>> all=new ArrayList<>();
    public final ModeSetting mode;
    public final ColorSetting color,secondary;
    public final NumberSetting speed,saturation,glowRadius,glowStrength;
    public final BooleanSetting glow;
    public HudTextStyle(Function<Setting<?>,Setting<?>> register,String prefix,int initial,BooleanSupplier visible){
        mode=add(register,new ModeSetting(prefix+" Color Mode","Static",visible,"Static","Fade","Rainbow"));
        color=add(register,new ColorSetting(prefix+" Color",initial,visible));
        secondary=add(register,new ColorSetting(prefix+" Fade Color",0xFFA855F7,()->visible.getAsBoolean()&&mode.is("Fade")));
        speed=add(register,new NumberSetting(prefix+" Color Speed",1,.1,5,.1,()->visible.getAsBoolean()&&!mode.is("Static")));
        saturation=add(register,new NumberSetting(prefix+" Saturation",.75,0,1,.05,()->visible.getAsBoolean()&&mode.is("Rainbow")));
        glow=add(register,new BooleanSetting(prefix+" Text Glow",false,visible));
        glowRadius=add(register,new NumberSetting(prefix+" Glow Radius",1.5,.5,4,.5,()->visible.getAsBoolean()&&glow.isEnabled()));
        glowStrength=add(register,new NumberSetting(prefix+" Glow Strength",.35,.05,1,.05,()->visible.getAsBoolean()&&glow.isEnabled()));
    }
    private <T extends Setting<?>> T add(Function<Setting<?>,Setting<?>> register,T s){register.apply(s);all.add(s);return s;}
    public int colorAt(long now,int index){
        if(mode.is("Static"))return color.getArgb();
        double phase=Math.floorMod(now,600000L)*speed.getDouble()/6000+index*.065;
        if(mode.is("Rainbow"))return color.getArgb()&0xFF000000|Color.HSBtoRGB((float)(phase%1),saturation.getFloat(),1)&0xFFFFFF;
        return RenderUtils.blend(color.getArgb(),secondary.getArgb(),(float)(.5+.5*Math.sin(phase*Math.PI*2)));
    }
    public void draw(FontRenderer font,String text,float x,float y,float opacity,boolean shadow,long now){
        try(TextGlowScope ignored=TextGlowScope.begin(glow.isEnabled()&&font.getClass()==FontRenderer.class)){
        for(int pass=0;pass<3;pass++){
            if(pass==0&&!glow.isEnabled()||pass==1&&!shadow)continue;
            float cursor=x;
            for(int i=0;i<text.length();i++){
                String letter=text.substring(i,i+1);int base=colorAt(now,i),a=Math.round((base>>>24)*opacity);
                if(a>=4){
                    if(pass==0){int ga=Math.round(a*glowStrength.getFloat()*.18F);float r=Math.min(1.25F,glowRadius.getFloat()*.35F);
                        if(ga>=4)for(int n=0;n<4;n++){double angle=n*Math.PI/2;font.drawString(letter,cursor+(float)Math.cos(angle)*r,y+(float)Math.sin(angle)*r,ga<<24|base&0xFFFFFF,false);}
                    }else if(pass==1)font.drawString(letter,cursor+1,y+1,a<<24|((base&0xFCFCFC)>>2),false);
                    else font.drawString(letter,cursor,y,a<<24|base&0xFFFFFF,false);
                }
                cursor+=font.getStringWidth(letter);
            }
        }
        }
    }
}
