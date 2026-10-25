package dev.vibe.hud;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.minecraft.client.gui.FontRenderer;

public final class ClockRenderer {
    private ClockRenderer() { }
    public static String time(ClockSettings s,ZonedDateTime now) {
        return now.format(DateTimeFormatter.ofPattern(s.twelveHour.isEnabled()
                ?(s.seconds.isEnabled()?"h:mm:ss a":"h:mm a"):(s.seconds.isEnabled()?"HH:mm:ss":"HH:mm"),Locale.ROOT));
    }
    public static String date(ZonedDateTime now) { return now.format(DateTimeFormatter.ofPattern("EEE, d MMM",Locale.getDefault())); }
    public static int height(ClockSettings s) {
        return s.diameter.getInt()+(s.digital.isEnabled()?15:0)+(s.date.isEnabled()?13:0);
    }
    public static void draw(ClockSettings s,FontRenderer font,int x,int y,ZonedDateTime now) {
        int d=s.diameter.getInt();float cx=x+d/2F,cy=y+d/2F,r=d/2F;
        HudShapes.round(x-2,y+1,d+4,d+4,r+2,0x23000000);
        HudShapes.round(x-1,y-1,d+2,d+2,r+1,0x44899DB9);
        HudShapes.round(x,y,d,d,r,s.face.getArgb());
        for(int i=0;i<60;i++) {
            double a=i*Math.PI/30;float out=r-5,in=out-(i%5==0?5:2);
            HudShapes.line(cx+(float)Math.sin(a)*in,cy-(float)Math.cos(a)*in,
                    cx+(float)Math.sin(a)*out,cy-(float)Math.cos(a)*out,i%5==0?1.4F:.6F,s.marks.getArgb());
        }
        if(s.numbers.isEnabled())for(int n:new int[]{12,3,6,9}) {
            double a=n*Math.PI/6;String label=Integer.toString(n);
            float nr=r-17;
            font.drawString(label,cx+(float)Math.sin(a)*nr-font.getStringWidth(label)/2F,
                    cy-(float)Math.cos(a)*nr-font.FONT_HEIGHT/2F,s.text.getArgb(),false);
        }
        double seconds=now.getSecond()+(s.smooth.isEnabled()?now.getNano()/1e9:0);
        double minutes=now.getMinute()+seconds/60,hours=(now.getHour()%12)+minutes/60;
        hand(cx,cy,r*.45F,hours*Math.PI/6,2.8F,s.hands.getArgb());
        hand(cx,cy,r*.67F,minutes*Math.PI/30,1.9F,s.hands.getArgb());
        if(s.seconds.isEnabled()) {
            double angle=seconds*Math.PI/30;
            HudShapes.line(cx-(float)Math.sin(angle)*r*.13F,cy+(float)Math.cos(angle)*r*.13F,
                    cx+(float)Math.sin(angle)*r*.76F,cy-(float)Math.cos(angle)*r*.76F,.85F,s.secondHand.getArgb());
        }
        HudShapes.round(cx-2.3F,cy-2.3F,4.6F,4.6F,2.3F,s.seconds.isEnabled()?s.secondHand.getArgb():s.hands.getArgb());
        int below=y+d+5;
        if(s.digital.isEnabled()) { center(font,time(s,now),cx,below,s.text.getArgb());below+=13; }
        if(s.date.isEnabled())center(font,date(now),cx,below,s.marks.getArgb());
    }
    private static void hand(float x,float y,float length,double angle,float width,int color) {
        HudShapes.line(x,y,x+(float)Math.sin(angle)*length,y-(float)Math.cos(angle)*length,width,color);
    }
    private static void center(FontRenderer font,String text,float x,int y,int color) {
        font.drawStringWithShadow(text,x-font.getStringWidth(text)/2F,y,color);
    }
}
