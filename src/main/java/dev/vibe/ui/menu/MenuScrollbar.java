package dev.vibe.ui.menu;

import dev.vibe.ui.MenuRoundedRenderer;
import dev.vibe.ui.RenderUtils;

/** A quiet glass track with a narrow luminous wax-coloured thumb. */
public final class MenuScrollbar {
    private MenuScrollbar(){}
    public static void draw(int x,int top,int width,int height,int thumbTop,int thumbHeight){
        MenuRoundedRenderer.rect(x,top,width,height,width/2,RenderUtils.alpha(AccountScreenStyle.ACCENT,22));
        MenuRoundedRenderer.rect(x-2,thumbTop-1,width+4,thumbHeight+2,(width+4)/2,RenderUtils.alpha(AccountScreenStyle.ACCENT,24));
        MenuRoundedRenderer.verticalGradient(x,thumbTop,width,thumbHeight,width/2,RenderUtils.blend(AccountScreenStyle.ACCENT,0xFFFFFFFF,.45F),AccountScreenStyle.ACCENT);
        MenuRoundedRenderer.rect(x,thumbTop,width/2,thumbHeight,width/4,0x50FFFFFF);
    }
}
