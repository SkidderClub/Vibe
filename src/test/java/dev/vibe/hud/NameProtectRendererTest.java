package dev.vibe.hud;

import dev.vibe.module.impl.client.NameProtectModule;
import dev.vibe.path.PathTestFixture;
import dev.vibe.setting.*;
import java.util.*;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.EnumChatFormatting;
import org.junit.Test;
import static org.junit.Assert.*;

public class NameProtectRendererTest {
    @Test public void colorsOnlyTheOwnNameAndPreservesNeighborsMetricsAndExistingFormatting()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            NameProtectModule protect=module(f);Font font=PathTestFixture.allocate(Font.class);font.calls=new ArrayList<>();
            protect.nameStyle.color.setValue(0xFF13E7A8);
            String text="§cBefore §lAlias§r after";
            int end=NameProtectRenderer.drawAt(font,text,12,8,0xFFE5E5E5,false,1000);
            assertEquals(12+font.getStringWidth("§cBefore ")+font.getStringWidth("§lAlias")+font.getStringWidth("§r after"),end);
            long letters=font.calls.stream().filter(c->(c.color&0xFFFFFF)==0x13E7A8).count();assertEquals(5,letters);
            for(Call c:font.calls)if((c.color&0xFFFFFF)==0x13E7A8){assertTrue(c.text.startsWith("§l"));assertEquals(3,c.text.length());}
            assertTrue(font.calls.stream().anyMatch(c->c.text.contains("Before")&&c.color==0xFFE5E5E5));
            assertTrue(font.calls.stream().anyMatch(c->c.text.contains("after")&&c.text.contains("§r")));
            assertEquals(NameProtectRenderer.UNHANDLED,NameProtectRenderer.drawAt(font,"Other player",0,0,-1,false,1000));
        }
    }
    @Test public void rainbowFadeAndGlowAnimateAtDrawTimeWithoutChangingTheChatText()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            NameProtectModule protect=module(f);Font font=PathTestFixture.allocate(Font.class);font.calls=new ArrayList<>();
            protect.nameStyle.mode.setValue("Rainbow");String text="<Alias> hello";
            NameProtectRenderer.drawAt(font,text,0,0,0xFFEEEEEE,false,1000);
            List<Integer> first=new ArrayList<>();for(Call c:font.calls)if(c.text.length()==1)first.add(c.color);
            assertTrue(new HashSet<>(first).size()>1);font.calls.clear();protect.nameStyle.glow.setEnabled(true);
            NameProtectRenderer.drawAt(font,text,0,0,0xFFEEEEEE,false,3000);
            assertEquals(27,font.calls.size());assertEquals("<Alias> hello",text);
            assertTrue(font.calls.stream().anyMatch(c->(c.color>>>24)<255));
            protect.nameStyle.mode.setValue("Fade");assertNotEquals(protect.nameStyle.colorAt(0,0),protect.nameStyle.colorAt(1500,0));
        }
    }
    private static NameProtectModule module(PathTestFixture f){
        NameProtectModule m=new NameProtectModule();f.modules.registerDynamic(m);
        for(Setting<?> s:m.getSettings()){if(s.getRawName().equals("Protected Name"))((StringSetting)s).setValue("Alias");if(s.getRawName().equals("Override Color"))((BooleanSetting)s).setEnabled(true);}return m;
    }
    public static class Font extends FontRenderer{
        List<Call> calls;
        private Font(){super(null,null,null,false);}
        @Override public int getStringWidth(String text){int width=0;boolean bold=false;for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c=='§'&&i+1<text.length()){char code=Character.toLowerCase(text.charAt(++i));if(code=='l')bold=true;else if("0123456789abcdefr".indexOf(code)>=0)bold=false;}else width+=bold?7:6;}return width;}
        @Override public int drawString(String text,float x,float y,int color,boolean shadow){calls.add(new Call(text,color));return Math.round(x)+getStringWidth(text);}
    }
    private static class Call{final String text;final int color;Call(String text,int color){this.text=text;this.color=color;}}
}
