package dev.vibe.ui.menu;

import net.minecraft.client.gui.*;

/** Keeps Minecraft's editing/clipboard/key handling, with the same measured type as the menus. */
public final class MenuTextField extends GuiTextField {
    private final int height;
    private int first,blink;
    private Integer paletteText,paletteAccent;
    public void palette(int text,int accent){paletteText=text;paletteAccent=accent;}
    public MenuTextField(int id,FontRenderer font,int x,int y,int width,int height){super(id,font,x,y,width,height);this.height=height;}
    @Override public void updateCursorCounter(){super.updateCursorCounter();blink++;}
    @Override public void drawTextBox(){
        if(!getVisible())return;
        MenuFont font=AccountScreenStyle.BODY_FONT;String text=getText();int cursor=getCursorPosition();first=Math.min(first,cursor);
        while(first<cursor&&font.width(text.substring(first,cursor))>getWidth()-3)first++;
        String visible=font.fit(text.substring(first),getWidth());if(visible.endsWith("…"))visible=visible.substring(0,visible.length()-1);
        float top=yPosition+(height-font.inkHeight("Ag"))*.5F;
        int end=Math.min(text.length(),first+visible.length());
        int a=Math.max(first,Math.min(cursor,getSelectionEnd())),b=Math.min(end,Math.max(cursor,getSelectionEnd()));
        if(a<b){int left=xPosition+(int)font.width(text.substring(first,a)),right=xPosition+(int)Math.ceil(font.width(text.substring(first,b)));dev.vibe.ui.MenuRoundedRenderer.rect(left,yPosition,right-left,height,0,0x554D92DD);}
        font.draw(visible,xPosition,top,paletteText==null?AccountScreenStyle.TEXT:paletteText);
        if(isFocused()&&(blink/6)%2==0){int caret=xPosition+Math.round(font.width(text.substring(first,cursor)));Gui.drawRect(caret,yPosition+1,caret+1,yPosition+height-1,paletteAccent==null?AccountScreenStyle.ACCENT:paletteAccent);}
    }
    @Override public void mouseClicked(int x,int y,int button){
        boolean inside=x>=xPosition&&x<xPosition+getWidth()&&y>=yPosition&&y<yPosition+height;setFocused(inside);
        if(!inside||button!=0)return;
        String text=getText();int position=Math.min(first,text.length());
        while(position<text.length()){
            float before=AccountScreenStyle.BODY_FONT.width(text.substring(first,position)),after=AccountScreenStyle.BODY_FONT.width(text.substring(first,position+1));
            if(x-xPosition<(before+after)*.5F)break;position++;
        }
        setCursorPosition(position);blink=0;
    }
}
