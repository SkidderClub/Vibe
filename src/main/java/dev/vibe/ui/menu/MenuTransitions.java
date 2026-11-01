package dev.vibe.ui.menu;

import dev.vibe.ui.account.AccountManagerGui;
import dev.vibe.ui.server.ServerBrowserGui;
import dev.vibe.ui.effect.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.shader.Framebuffer;

/** The window has its own geometry; only controls are dissolved between menu layers. */
public final class MenuTransitions {
    public static final long DURATION=220000000L;
    public static final long RESIZE_DURATION=160000000L;
    private static final SceneTexture previous=new SceneTexture();
    private static GuiScreen lastScreen,destination;
    private static Framebuffer lastLayer;
    private static Rect lastWindow,from;
    private static long started;
    private MenuTransitions(){}
    public static final class Rect {
        public final float x,y,width,height;
        public Rect(float x,float y,float width,float height){this.x=x;this.y=y;this.width=width;this.height=height;}
        public Rect mix(Rect target,float amount){return new Rect(x+(target.x-x)*amount,y+(target.y-y)*amount,width+(target.width-width)*amount,height+(target.height-height)*amount);}
    }
    private static boolean menu(GuiScreen screen){return screen instanceof GuiMainMenu||screen instanceof ServerBrowserGui||screen instanceof AccountManagerGui||screen instanceof MenuThemesGui||screen instanceof WorldBrowserGui||screen instanceof LanguagePickerGui||screen instanceof LicensesGui;}
    public static void open(GuiScreen source,GuiScreen target){
        destination=null;
        Minecraft mc=Minecraft.getMinecraft();
        if(source==target||!menu(source)||!menu(target)||source!=lastScreen||lastWindow==null||lastLayer==null
                ||lastLayer.framebufferWidth!=mc.displayWidth||lastLayer.framebufferHeight!=mc.displayHeight)return;
        try(EffectState state=new EffectState()){
            lastLayer.bindFramebuffer(false);previous.capture(0,0,mc.displayWidth,mc.displayHeight,false);
            from=lastWindow;destination=target;started=0;
        }
    }
    public static float progress(long elapsed){float t=Math.max(0,Math.min(1,elapsed/(float)DURATION));return t*t*(3-2*t);}
    public static float opacity(long elapsed){return 1-progress(elapsed);}
    public static float resizeProgress(long elapsed){return progress((long)(elapsed*(DURATION/(double)RESIZE_DURATION)));}
    public static float contentProgress(long elapsed){return elapsed<=RESIZE_DURATION?0:progress((long)((elapsed-RESIZE_DURATION)*(DURATION/(double)(DURATION-RESIZE_DURATION))));}
    static Rect window(GuiScreen owner,Rect target,long now){
        if(owner!=destination)return target;
        if(started==0)started=now;
        if(now-started>=DURATION){destination=null;return target;}
        return from.mix(target,resizeProgress(now-started));
    }
    static float outgoing(GuiScreen owner,long now){return owner==destination?1-progress((long)((now-started)*3.15)):0;}
    static float incoming(GuiScreen owner,long now){return owner==destination?contentProgress(now-started):1;}
    static int previous(){return previous.color;}
    static void record(GuiScreen screen,Rect window,Framebuffer layer){lastScreen=screen;lastWindow=window;lastLayer=layer;}
}
