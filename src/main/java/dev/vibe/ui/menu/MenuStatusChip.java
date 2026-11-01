package dev.vibe.ui.menu;

import dev.vibe.ui.MenuRoundedRenderer;
import dev.vibe.ui.RenderUtils;

/** Shared, scale-aware status pills for accounts and server history. */
public final class MenuStatusChip {
    private static final MenuFont FONT=new MenuFont(java.awt.Font.PLAIN,9);
    private MenuStatusChip() { }
    public static int width(String label){return (int)Math.ceil(FONT.width(label))+22;}
    public static void draw(int x,int y,int width,int height,String label,int color){
        int radius=height/2;
        int border=RenderUtils.blend(AccountScreenStyle.BORDER,color,.38F);
        int fill=RenderUtils.blend(AccountScreenStyle.SURFACE,color,.10F);
        MenuRoundedRenderer.verticalGradient(x,y,width,height,radius,border,AccountScreenStyle.BORDER);
        MenuRoundedRenderer.verticalGradient(x+1,y+1,width-2,height-2,radius-1,fill,AccountScreenStyle.SURFACE);
        int dotY=y+(height-4)/2;
        MenuRoundedRenderer.rect(x+4,dotY-2,8,8,4,(color&0xFFFFFF)|0x24000000);
        MenuRoundedRenderer.rect(x+6,dotY,4,4,2,color);
        FONT.middle(FONT.fit(label,width-22),x+15,y,height,RenderUtils.blend(AccountScreenStyle.TEXT,color,.35F));
    }
}
