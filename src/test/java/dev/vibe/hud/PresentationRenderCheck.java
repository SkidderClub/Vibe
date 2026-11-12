package dev.vibe.hud;

import dev.vibe.event.ReconnectEvents;
import dev.vibe.module.impl.client.HudModule;
import dev.vibe.module.impl.meme.EmoteWheelModule;
import dev.vibe.ui.ClientChangesRenderCheck;
import dev.vibe.ui.screen.EmoteWheelGui;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.Path;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public final class PresentationRenderCheck {
    public static void verify(Minecraft mc,HudModule hud,Path output)throws Exception{
        dev.vibe.camera.DroneCollisionsTest.registerBlocks();
        SessionJournal.Entry[] entries={entry("You","Opponent",Items.diamond_sword),entry("Teammate","Opponent",Items.bow),entry("Opponent","You",Items.iron_sword)};
        backdrop();int before=pixel(60,60);
        for(int i=0;i<entries.length;i++)KillFeedRenderer.draw("CounterStrike",hud.dashboards.get("killfeed"),entries[i],100,120+i*25,330,1,true);
        if(red(100,120,330,20)<30||red(100,145,330,20)>0||red(100,170,330,20)<30)throw new AssertionError("CS local involvement border pixels incorrect");
        for(int i=0;i<entries.length;i++)KillFeedRenderer.draw("Fortnite",hud.dashboards.get("killfeed"),entries[i],620,120+i*18,440,1,true);
        if(pixel(60,60)!=before)throw new AssertionError("Kill feed overwrote the background");save(output.resolve("killfeeds-cs-fortnite.png"));
        EmoteWheelGui wheel=new EmoteWheelGui(new EmoteWheelModule());wheel.setWorldAndResolution(mc,1280,720);backdrop();int corner=pixel(20,20);wheel.drawScreen(640,245,1);if(pixel(20,20)!=corner)throw new AssertionError("Emote wheel replaced its background");save(output.resolve("emote-wheel.png"));
        wheel.setWorldAndResolution(mc,320,240);ClientChangesRenderCheck.frame(320,240);wheel.drawScreen(160,45,1);if(GL11.glGetError()!=0)throw new AssertionError("Small emote wheel GL error");
        backdrop();GuiGameOver death=new GuiGameOver();death.width=1280;death.height=720;ReconnectEvents events=new ReconnectEvents();List<GuiButton> buttons=new ArrayList<>();events.init(new GuiScreenEvent.InitGuiEvent.Post(death,buttons));int unchanged=pixel(20,20),buttonBefore=pixel(1230,18);
        events.draw(new GuiScreenEvent.DrawScreenEvent.Post(death,1230,18,1));if(pixel(1230,18)==buttonBefore)throw new AssertionError("Explore glass control was not composited");if(pixel(20,20)!=unchanged)throw new AssertionError("Explore changed the rest of the death screen");save(output.resolve("death-explore-glass.png"));
        if(GL11.glGetError()!=0)throw new AssertionError("Presentation GL error");
    }
    private static SessionJournal.Entry entry(String actor,String victim,net.minecraft.item.Item item){return new SessionJournal.Entry("Kill",actor,victim,null,null,new ItemStack(item),true,"", "You");}
    private static void backdrop()throws Exception{ClientChangesRenderCheck.frame(1280,720);for(int y=0;y<720;y+=24)for(int x=0;x<1280;x+=24)Gui.drawRect(x,y,x+24,y+24,((x/24+y/24)&1)==0?0xFF354B55:0xFF293B41);}
    private static int pixel(int x,int y){ByteBuffer p=BufferUtils.createByteBuffer(4);GL11.glReadPixels(x,719-y,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,p);return (p.get(0)&255)<<16|(p.get(1)&255)<<8|(p.get(2)&255);}
    private static int red(int x,int y,int w,int h){ByteBuffer p=BufferUtils.createByteBuffer(w*h*4);GL11.glReadPixels(x,720-y-h,w,h,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,p);int n=0;for(int i=0;i<w*h;i++){int r=p.get(i*4)&255,g=p.get(i*4+1)&255,b=p.get(i*4+2)&255;if(r>120&&r>g*2&&r>b*2)n++;}return n;}
    private static void save(Path path)throws Exception{ByteBuffer p=BufferUtils.createByteBuffer(1280*720*4);GL11.glReadPixels(0,0,1280,720,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,p);BufferedImage image=new BufferedImage(1280,720,BufferedImage.TYPE_INT_RGB);for(int y=0;y<720;y++)for(int x=0;x<1280;x++){int i=((719-y)*1280+x)*4;image.setRGB(x,y,(p.get(i)&255)<<16|(p.get(i+1)&255)<<8|(p.get(i+2)&255));}ImageIO.write(image,"png",path.toFile());}
}
