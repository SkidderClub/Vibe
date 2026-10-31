package dev.vibe.ui.screen;

import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;

/** Confirmation shares the recorder's dark utility-editor style. */
public final class AIAuraResetGui extends GuiYesNo {
    private final GuiYesNoCallback callback;
    private int left,top,panelWidth;
    public AIAuraResetGui(GuiYesNoCallback callback){super(callback,"Reset AIAura data?","Delete all manual recordings and the trained neural model?",0);this.callback=callback;}
    @Override public void initGui(){
        panelWidth=Math.min(520,width-24);left=(width-panelWidth)/2;top=(height-160)/2;buttonList.clear();
        buttonList.add(new Button(0,left+14,top+116,panelWidth/2-21,"Reset data"));buttonList.add(new Button(1,left+panelWidth/2+7,top+116,panelWidth/2-21,"Cancel"));
    }
    @Override public void drawScreen(int x,int y,float partial){
        dev.vibe.ui.GuiRenderState.prepare(false);Gui.drawRect(0,0,width,height,0xFF0B0D10);SkeetEditorStyle.window(left,top,left+panelWidth,top+160,"Reset AIAura data?","");
        int line=top+40;for(String text:fontRendererObj.listFormattedStringToWidth("Delete all manual recordings and the trained neural model?",panelWidth-30)){fontRendererObj.drawString(text,left+15,line,SkeetEditorStyle.TEXT);line+=12;}
        fontRendererObj.drawString("This cannot be undone.",left+15,line+10,SkeetEditorStyle.MUTED);for(GuiButton button:buttonList)button.drawButton(mc,x,y);
    }
    @Override protected void keyTyped(char c,int key)throws IOException{if(key==org.lwjgl.input.Keyboard.KEY_ESCAPE)callback.confirmClicked(false,0);else super.keyTyped(c,key);}
    private static final class Button extends GuiButton {
        Button(int id,int x,int y,int width,String text){super(id,x,y,width,24,text);}
        @Override public void drawButton(Minecraft mc,int x,int y){hovered=x>=xPosition&&x<xPosition+width&&y>=yPosition&&y<yPosition+height;SkeetEditorStyle.button(xPosition,yPosition,xPosition+width,yPosition+height,displayString,hovered);}
    }
}
