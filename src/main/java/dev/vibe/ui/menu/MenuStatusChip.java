package dev.vibe.ui.menu;

import dev.vibe.ui.MenuRoundedRenderer;
import dev.vibe.ui.RenderUtils;

/** Quiet, consistently centred labels; status tags do not contain another animated glass panel. */
public final class MenuStatusChip {
    private static final MenuFont FONT=new MenuFont(java.awt.Font.PLAIN,10);
    private MenuStatusChip() { }
    public static int width(String label){return (int)Math.ceil(FONT.width(label))+16;}
    public static void draw(int x,int y,int width,int height,String label,int color){
        int radius=Math.min(4,height/2);
        MenuRoundedRenderer.rect(x,y,width,height,radius,RenderUtils.alpha(RenderUtils.blend(AccountScreenStyle.BORDER,color,.35F),160));
        MenuRoundedRenderer.rect(x+1,y+1,width-2,height-2,Math.max(1,radius-1),RenderUtils.alpha(RenderUtils.blend(AccountScreenStyle.SURFACE,color,.09F),235));
        FONT.centered(FONT.fit(label,width-12),x,y,width,height,RenderUtils.blend(AccountScreenStyle.TEXT,color,.28F));
    }
}
