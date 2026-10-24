package dev.vibe.hud;

import dev.vibe.setting.*;
import java.awt.Color;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.client.gui.FontRenderer;
import dev.vibe.ui.RenderUtils;

/** The custom domain keeps native text metrics while its color animates at draw time. */
public final class ScoreboardDomainSettings {
    public final List<Setting<?>> all=new ArrayList<Setting<?>>();
    public final ModeSetting mode;
    public final ColorSetting color,secondary;
    public final NumberSetting speed,saturation;
    public ScoreboardDomainSettings(Function<Setting<?>,Setting<?>> register,BooleanSupplier visible) {
        mode=add(register,new ModeSetting("Scoreboard Domain Color Mode","Static",visible,"Static","Fade","Rainbow"));
        color=add(register,new ColorSetting("Scoreboard Domain Color",0xFFFFFFFF,visible));
        secondary=add(register,new ColorSetting("Scoreboard Domain Fade Color",0xFFA855F7,()->visible.getAsBoolean()&&mode.is("Fade")));
        speed=add(register,new NumberSetting("Scoreboard Domain Speed",1,.1,5,.1,()->visible.getAsBoolean()&&!mode.is("Static")));
        saturation=add(register,new NumberSetting("Scoreboard Domain Saturation",.8,0,1,.05,()->visible.getAsBoolean()&&mode.is("Rainbow")));
    }
    private <T extends Setting<?>> T add(Function<Setting<?>,Setting<?>> register,T setting) {register.apply(setting);all.add(setting);return setting;}
    public int colorAt(long time,int index) {
        if(mode.is("Static"))return color.getArgb();
        double phase=Math.floorMod(time,600000L)*speed.getDouble()/6000+index*.065;
        if(mode.is("Rainbow"))return color.getArgb()&0xFF000000|Color.HSBtoRGB((float)(phase%1),saturation.getFloat(),1)&0xFFFFFF;
        return RenderUtils.blend(color.getArgb(),secondary.getArgb(),(float)(.5+.5*Math.sin(phase*Math.PI*2)));
    }
    public void draw(FontRenderer font,String domain,float x,float y,long now) {
        if(mode.is("Static")) {if((color.getArgb()>>>24)>=4)font.drawString(domain,x,y,color.getArgb(),false);return;}
        for(int i=0;i<domain.length();i++) {
            String letter=domain.substring(i,i+1);int c=colorAt(now,i);
            if((c>>>24)>=4)font.drawString(letter,x,y,c,false);
            x+=font.getStringWidth(letter);
        }
    }
}
