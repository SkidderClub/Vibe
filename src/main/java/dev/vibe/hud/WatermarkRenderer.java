package dev.vibe.hud;

import dev.vibe.Vibe;
import dev.vibe.module.impl.client.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.EnumChatFormatting;

/** Tags are separate runs, so a name color never leaks into a separator or another value. */
final class WatermarkRenderer {
    private long second=Long.MIN_VALUE;
    private String date="",time="";
    List<String> tags(HudModule hud,boolean preview){
        Minecraft mc=Minecraft.getMinecraft();List<String> tags=new ArrayList<>();
        long now=System.currentTimeMillis();
        if(second!=now/1000){second=now/1000;LocalDateTime local=LocalDateTime.now();date=local.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));time=local.format(DateTimeFormatter.ofPattern("HH:mm:ss"));}
        if(hud.getWatermarkDetails().isSelected("Version"))tags.add("v"+Vibe.VERSION);
        if(hud.getWatermarkDetails().isSelected("FPS"))tags.add(Minecraft.getDebugFPS()+" fps");
        if(hud.getWatermarkDetails().isSelected("TPS")){
            Object channel=mc.getNetHandler()==null||mc.getNetHandler().getNetworkManager()==null?null:mc.getNetHandler().getNetworkManager().channel();
            double tps=preview||mc.isSingleplayer()?20:ServerTickRate.value(channel,System.nanoTime()/1000000);
            tags.add(Double.isNaN(tps)?"-- tps":String.format(Locale.ROOT,"%.1f tps",tps));
        }
        if(hud.getWatermarkDetails().isSelected("Speed")){
            double speed=mc.thePlayer==null?0:Math.hypot(mc.thePlayer.posX-mc.thePlayer.prevPosX,mc.thePlayer.posZ-mc.thePlayer.prevPosZ)*20;
            tags.add(String.format(Locale.ROOT,"%.1f b/s",preview?4.3:speed));
        }
        if(hud.getWatermarkDetails().isSelected("Coordinates"))tags.add(mc.thePlayer==null?"0, 64, 0":(int)Math.floor(mc.thePlayer.posX)+", "+(int)Math.floor(mc.thePlayer.posY)+", "+(int)Math.floor(mc.thePlayer.posZ));
        if(hud.getWatermarkDetails().isSelected("Date"))tags.add(date);
        if(hud.getWatermarkDetails().isSelected("Time"))tags.add(time);
        if(hud.getWatermarkDetails().isSelected("Username")){
            NameProtectModule protect=Vibe.getInstance().getModuleManager().getModule(NameProtectModule.class);
            String name=mc.getSession()==null?"Player":mc.getSession().getUsername();
            if(protect!=null&&protect.isEnabled())name=protect.getDisplayName(name);
            tags.add(EnumChatFormatting.getTextWithoutFormattingCodes(name));
        }
        return tags;
    }
    Layout layout(HudModule hud,FontRenderer font,List<String> tags,int available){
        Layout result=new Layout();int max=Math.max(30,available),x=0,y=0;String separator=hud.watermark.separatorText();
        String name=font.trimStringToWidth(hud.getWatermarkText().getValue(),max);
        result.pieces.add(new Piece(name,0,0,0));x=font.getStringWidth(name);
        for(String value:tags){String tag=font.trimStringToWidth(value,max);int sw=font.getStringWidth(separator),tw=font.getStringWidth(tag);
            if(x+sw+tw>max){result.width=Math.max(result.width,x);x=0;y+=12;}
            else{result.pieces.add(new Piece(separator,x,y,2));x+=sw;}
            result.pieces.add(new Piece(tag,x,y,1));x+=tw;
        }
        result.width=Math.max(result.width,x);result.height=26+y;return result;
    }
    void draw(HudModule hud,FontRenderer font,Layout layout,float x,float y){
        WatermarkSettings s=hud.watermark;long now=System.currentTimeMillis();
        for(Piece p:layout.pieces){
            if(p.kind==0)s.client.draw(font,p.text,x+p.x,y+p.y,1,s.shadow.isEnabled(),now);
            else{int color=p.kind==1?s.tags.getArgb():s.separatorColor.getArgb();if((color>>>24)>=4)font.drawString(p.text,x+p.x,y+p.y,color,s.shadow.isEnabled());}
        }
    }
    static final class Layout{final List<Piece> pieces=new ArrayList<>();int width,height;}
    private static final class Piece{final String text;final int x,y,kind;Piece(String text,int x,int y,int kind){this.text=text;this.x=x;this.y=y;this.kind=kind;}}
}
