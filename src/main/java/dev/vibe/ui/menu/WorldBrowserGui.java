package dev.vibe.ui.menu;

import dev.vibe.ui.GuiClip;
import dev.vibe.ui.GuiRenderState;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.util.*;
import net.minecraft.client.gui.*;
import net.minecraft.world.storage.SaveFormatComparator;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Glass presentation over Minecraft's world list and its original world-management actions. */
public final class WorldBrowserGui extends GuiSelectWorld {
    private static final Field SAVES=find(List.class),SELECTED=find(int.class);
    private final MainMenuShaderManager shaders;
    private final Map<GuiButton,VibeMenuButton> visuals=new IdentityHashMap<GuiButton,VibeMenuButton>();
    private final DateFormat dates=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT);
    private int left,top,panelWidth,panelHeight,listTop,listBottom,scroll,dragOffset;
    private int lastSelected=-1;
    private long lastClick;
    private boolean dragging;
    private static final int ROW=48;

    public WorldBrowserGui(GuiScreen parent,MainMenuShaderManager shaders){super(parent);this.shaders=shaders;}
    private static Field find(Class<?> type){
        for(Field field:GuiSelectWorld.class.getDeclaredFields())if(field.getType()==type){field.setAccessible(true);return field;}
        throw new IllegalStateException("Minecraft world-selection field unavailable: "+type.getSimpleName());
    }
    @SuppressWarnings("unchecked") private List<SaveFormatComparator> saves(){try{List<SaveFormatComparator> value=(List<SaveFormatComparator>)SAVES.get(this);return value==null?Collections.<SaveFormatComparator>emptyList():value;}catch(IllegalAccessException failure){throw new IllegalStateException(failure);}}
    private int selected(){try{return SELECTED.getInt(this);}catch(IllegalAccessException failure){throw new IllegalStateException(failure);}}
    private void select(int index){
        index=Math.max(-1,Math.min(saves().size()-1,index));
        try{SELECTED.setInt(this,index);}catch(IllegalAccessException failure){throw new IllegalStateException(failure);}
        for(GuiButton button:buttonList)if(button.id==1||button.id==2||button.id==4||button.id==5)button.enabled=index>=0;
    }
    @Override public void initGui(){
        super.initGui();visuals.clear();
        left=width<360?4:24;top=8;panelWidth=width-left*2;panelHeight=height-12;
        listTop=top+53;listBottom=Math.max(listTop+20,height-70);scroll=Math.min(scroll,maxScroll());select(selected());
    }
    private int maxScroll(){return Math.max(0,saves().size()*ROW-(listBottom-listTop));}
    @Override public void drawScreen(int mouseX,int mouseY,float partialTicks){
        if(shaders!=null)shaders.draw(width,height);else drawDefaultBackground();GuiRenderState.prepare(false);MenuGlass.begin(this,shaders,width,height);
        try{
            AccountScreenStyle.window(left,top,panelWidth,panelHeight);
            AccountScreenStyle.title("Singleplayer",left+16,top+11);
            AccountScreenStyle.text("Your worlds",left+16,top+35,AccountScreenStyle.MUTED);
            int x=left+16,w=panelWidth-32;List<SaveFormatComparator> worlds=saves();scroll=Math.max(0,Math.min(scroll,maxScroll()));
            try(GuiClip clip=new GuiClip(x,listTop,w,listBottom-listTop)){
                int first=Math.max(0,scroll/ROW),last=Math.min(worlds.size(),(scroll+listBottom-listTop)/ROW+1);
                for(int index=first;index<last;index++){
                    SaveFormatComparator world=worlds.get(index);int y=listTop+index*ROW-scroll;
                    boolean hover=mouseX>=x&&mouseX<x+w-8&&mouseY>=Math.max(y,listTop)&&mouseY<Math.min(y+ROW-5,listBottom);
                    AccountScreenStyle.panel(x,y,w-8,ROW-5,index==selected()?AccountScreenStyle.TINT:hover?AccountScreenStyle.HOVER:AccountScreenStyle.SURFACE,index==selected()?AccountScreenStyle.ACCENT:AccountScreenStyle.BORDER);
                    String name=world.getDisplayName().isEmpty()?world.getFileName():world.getDisplayName();
                    AccountScreenStyle.BOLD_FONT.draw(AccountScreenStyle.BOLD_FONT.fit(name,w-32),x+12,y+5,AccountScreenStyle.TEXT);
                    String detail=world.getFileName()+" · "+dates.format(new Date(world.getLastTimePlayed()));
                    AccountScreenStyle.SMALL_FONT.draw(AccountScreenStyle.SMALL_FONT.fit(detail,w-32),x+12,y+20,AccountScreenStyle.MUTED);
                    String mode=world.requiresConversion()?"Conversion required":world.isHardcoreModeEnabled()?"Hardcore":world.getEnumGameType()==null?"World":net.minecraft.client.resources.I18n.format("gameMode."+world.getEnumGameType().getName());
                    if(world.getCheatsEnabled())mode+=" · Cheats";
                    AccountScreenStyle.SMALL_FONT.draw(AccountScreenStyle.SMALL_FONT.fit(mode,w-32),x+12,y+32,AccountScreenStyle.MUTED);
                }
            }
            if(worlds.isEmpty())AccountScreenStyle.BODY_FONT.centered("Create a world to get started",x,listTop,w,Math.min(90,listBottom-listTop),AccountScreenStyle.MUTED);
            if(maxScroll()>0){
                int track=listBottom-listTop,thumb=Math.max(18,track*track/(worlds.size()*ROW)),thumbY=listTop+scroll*(track-thumb)/maxScroll();
                MenuScrollbar.draw(x+w-4,listTop,3,track,thumbY,thumb);
            }
            for(GuiButton original:buttonList){
                if(original.getClass()!=GuiButton.class){original.drawButton(mc,mouseX,mouseY);continue;}
                VibeMenuButton button=visuals.get(original);if(button==null){button=new VibeMenuButton(original);visuals.put(original,button);}
                button.enabled=original.enabled;button.visible=original.visible;button.displayString=original.displayString;button.drawButton(mc,mouseX,mouseY);
            }
        }finally{MenuGlass.end();}
    }
    @Override protected void mouseClicked(int x,int y,int button) throws IOException {
        if(button==0&&y>=listTop&&y<listBottom&&x>=left+16&&x<left+panelWidth-12){
            if(x>=left+panelWidth-23&&maxScroll()>0){dragging=true;dragOffset=0;drag(y);return;}
            int index=(y-listTop+scroll)/ROW;
            if(index<saves().size()){
                long now=System.currentTimeMillis();boolean twice=index==lastSelected&&now-lastClick<250;select(index);lastSelected=index;lastClick=now;
                if(twice)func_146615_e(index);
            }
            return;
        }
        super.mouseClicked(x,y,button);
    }
    private void drag(int y){int track=listBottom-listTop,thumb=Math.max(18,track*track/Math.max(1,saves().size()*ROW));scroll=Math.max(0,Math.min(maxScroll(),(y-listTop-thumb/2-dragOffset)*maxScroll()/Math.max(1,track-thumb)));}
    @Override public void handleMouseInput() throws IOException {
        int x=Mouse.getEventX()*width/Math.max(1,mc.displayWidth),y=height-Mouse.getEventY()*height/Math.max(1,mc.displayHeight)-1;
        int button=Mouse.getEventButton();if(Mouse.getEventButtonState())mouseClicked(x,y,button);else if(button>=0){mouseReleased(x,y,button);dragging=false;}
        if(dragging&&Mouse.isButtonDown(0))drag(y);
        int wheel=Mouse.getEventDWheel();if(wheel!=0)scroll=Math.max(0,Math.min(maxScroll(),scroll+(wheel>0?-ROW:ROW)));
    }
    @Override protected void keyTyped(char character,int key) throws IOException {
        if(key==Keyboard.KEY_ESCAPE){mc.displayGuiScreen(parentScreen);return;}
        if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER){if(selected()>=0)func_146615_e(selected());return;}
        if(key==Keyboard.KEY_UP||key==Keyboard.KEY_DOWN){select(selected()+(key==Keyboard.KEY_UP?-1:1));int position=selected()*ROW;if(position<scroll)scroll=Math.max(0,position);if(position+ROW>scroll+listBottom-listTop)scroll=Math.min(maxScroll(),position+ROW-listBottom+listTop);return;}
        super.keyTyped(character,key);
    }
}
