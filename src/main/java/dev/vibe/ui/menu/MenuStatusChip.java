package dev.vibe.ui.menu;

import dev.vibe.ui.MenuRoundedRenderer;
import dev.vibe.ui.RenderUtils;

/** Status labels share the menu buttons' glass, lava palette and centered font metrics. */
public final class MenuStatusChip {
    private static final MenuFont FONT=new MenuFont(java.awt.Font.PLAIN,10);
    private MenuStatusChip() { }
    public static int width(String label){return (int)Math.ceil(FONT.width(label))+16;}
    public static void draw(int x,int y,int width,int height,String label,int color){
        AccountScreenStyle.buttonPanel(x,y,width,height,RenderUtils.blend(AccountScreenStyle.SURFACE,color,.07F),RenderUtils.blend(AccountScreenStyle.BORDER,color,.35F),.12F,x+width/2,y+height/2);
        FONT.centered(FONT.fit(label,width-12),x,y,width,height,RenderUtils.blend(AccountScreenStyle.TEXT,color,.28F));
    }
}
