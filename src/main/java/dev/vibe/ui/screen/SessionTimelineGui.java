package dev.vibe.ui.screen;

import dev.vibe.Vibe;
import dev.vibe.hud.SessionJournal;
import dev.vibe.module.impl.client.StatisticsModule;
import dev.vibe.ui.GuiClip;
import java.util.*;
import java.io.IOException;
import net.minecraft.client.gui.*;
import org.lwjgl.input.Mouse;

public final class SessionTimelineGui extends GuiScreen {
    private final GuiScreen parent;private int scroll,left,top,right,bottom;private List<SessionJournal.Entry> entries=Collections.emptyList();
    public SessionTimelineGui(GuiScreen parent){this.parent=parent;}
    @Override public void drawScreen(int mx,int my,float partial){drawDefaultBackground();left=Math.max(8,(width-520)/2);right=Math.min(width-8,left+520);top=25;bottom=height-25;SkeetEditorStyle.window(left,top,right,bottom,"Session timeline","Select an entry to inspect account statistics");entries=SessionJournal.INSTANCE.entries();int max=Math.max(0,entries.size()*30-(bottom-top-52));scroll=Math.min(scroll,max);try(GuiClip c=new GuiClip(left+5,top+29,right-left-10,bottom-top-38)){for(int i=0;i<entries.size();i++){int y=top+32+i*30-scroll;if(y+27<top+29||y>bottom-8)continue;SessionJournal.Entry e=entries.get(i);Gui.drawRect(left+9,y,right-9,y+26,mx>=left+9&&mx<right-9&&my>=y&&my<y+26?0xFF303339:0xFF1B1E23);String time=new java.text.SimpleDateFormat("HH:mm:ss").format(new Date(e.wallTime));fontRendererObj.drawStringWithShadow(time,left+16,y+8,0xFF99A7B9);fontRendererObj.drawString(mc.fontRendererObj.trimStringToWidth(e.kind+" · "+e.label,right-left-100),left+84,y+8,0xFFE8EAF0);}}if(entries.isEmpty())drawCenteredString(fontRendererObj,"No events recorded in this session",width/2,top+60,0xFF9CA6B4);}
    @Override public void handleMouseInput()throws IOException{super.handleMouseInput();int wheel=Mouse.getEventDWheel();if(wheel!=0)scroll=Math.max(0,scroll+(wheel<0?30:-30));}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{if(button==0&&x>=left+9&&x<right-9&&y>=top+29&&y<bottom-8){int i=(y-top-32+scroll)/30;if(i>=0&&i<entries.size()){StatisticsModule module=Vibe.getInstance().getModuleManager().getModule(StatisticsModule.class);if(module!=null)mc.displayGuiScreen(new StatisticsGui(module).forAccount(entries.get(i).account));}}}
    @Override protected void keyTyped(char c,int key)throws IOException{if(key==1)mc.displayGuiScreen(parent);else super.keyTyped(c,key);}
}
