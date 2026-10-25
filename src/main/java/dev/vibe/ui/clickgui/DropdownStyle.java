package dev.vibe.ui.clickgui;

import dev.vibe.module.impl.client.ClickGuiModule;
import dev.vibe.ui.MenuRoundedRenderer;
import dev.vibe.ui.NeverLoseFont;
import dev.vibe.ui.RenderUtils;
import java.awt.Color;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/** Reference palettes, real antialiased corners and continuous, time-based accent fades. */
final class DropdownStyle {
    static final NeverLoseFont LABEL = new NeverLoseFont(java.awt.Font.PLAIN, 22);
    static final NeverLoseFont HEADER = new NeverLoseFont(java.awt.Font.BOLD, 23);
    final boolean packet, novo, suizide, light;
    final int text, muted, body, row, headerHeight, rowHeight, radius;
    final ClickGuiModule settings;
    final long now;
    DropdownStyle(ClickGuiModule settings, long now) {
        this.settings = settings; this.now = now;
        packet = settings.getTheme().is("PacketV3"); novo = settings.getTheme().is("Novoline"); suizide = settings.getTheme().is("SuizideX");
        light = suizide && !settings.getSuizideDarkMode().isEnabled();
        text = light ? 0xFF20272B : 0xFFF0F1F5; muted = light ? 0xFF5B686E : 0xFFB0B0BA;
        body = light ? 0xEBEDF5F0 : novo ? 0xEC191820 : suizide ? 0xF20C0C11 : 0xEF1B1B1D;
        row = light ? 0xF4F0F3F3 : suizide ? 0xEF202027 : body;
        headerHeight = novo ? 23 : 25; rowHeight = novo ? 18 : 22; radius = novo ? 0 : packet ? 10 : 9;
    }
    int accent(float x, int y) { return accent(settings, x, y, now); }
    static int accent(ClickGuiModule settings, float x, int y, long millis) {
        if (settings.getTheme().is("Novoline")) return settings.getNovolineColor().getArgb();
        if (settings.getTheme().is("SuizideX")) return RenderUtils.blend(settings.getSuizideColor().getArgb(), settings.getSuizideFadeColor().getArgb(), x);
        if (settings.getPacketAccent().is("Static")) return settings.getPacketColor().getArgb();
        double phase = millis / 10000.0 * settings.getPacketFadeSpeed().getDouble();
        if (settings.getPacketAccent().is("Rainbow")) return Color.HSBtoRGB((float) ((phase + x * .12 + y * .0004) % 1), .72F, 1);
        float blend = (float) (.5 - .5 * Math.cos(phase * Math.PI * 2 + x * Math.PI + y * .002));
        return RenderUtils.blend(settings.getPacketColor().getArgb(), settings.getPacketFadeColor().getArgb(), blend);
    }
    static void rect(int x, int y, int w, int h, int radius, int color) { MenuRoundedRenderer.rect(x,y,w,h,radius,color); }
    static void gradient(int x, int y, int w, int h, int left, int right) {
        if (w <= 0 || h <= 0) return;
        GlStateManager.disableTexture2D(); GlStateManager.enableBlend(); GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770,771,1,0); GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glBegin(GL11.GL_QUADS); color(left); GL11.glVertex2f(x,y+h); GL11.glVertex2f(x,y);
        color(right); GL11.glVertex2f(x+w,y); GL11.glVertex2f(x+w,y+h); GL11.glEnd();
        GL11.glShadeModel(GL11.GL_FLAT); GlStateManager.enableAlpha(); GlStateManager.enableTexture2D(); GlStateManager.resetColor(); GlStateManager.color(1,1,1,1);
    }
    /** Same shared filtered nine-slice mask as the menu, shaded by vertex colors. */
    static void roundedGradient(int x,int y,int w,int h,int radius,int left,int right) {
        MenuRoundedRenderer.horizontalGradient(x,y,w,h,radius,left,right);
    }
    static void verticalGradient(int x,int y,int w,int h,int top,int bottom){
        GlStateManager.disableTexture2D();GlStateManager.enableBlend();GlStateManager.disableAlpha();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glBegin(GL11.GL_QUADS);color(top);GL11.glVertex2f(x,y);GL11.glVertex2f(x+w,y);color(bottom);GL11.glVertex2f(x+w,y+h);GL11.glVertex2f(x,y+h);GL11.glEnd();
        GL11.glShadeModel(GL11.GL_FLAT);GlStateManager.enableAlpha();GlStateManager.enableTexture2D();GlStateManager.resetColor();GlStateManager.color(1,1,1,1);
    }
    private static void color(int c){GL11.glColor4f((c>>16&255)/255F,(c>>8&255)/255F,(c&255)/255F,(c>>>24)/255F);}
    static void label(String s,float x,float y,float width,int color){LABEL.draw(LABEL.fit(s,Math.max(1,width)),x,y,color);}
    static void center(String s,int x,int y,int w,int color){String fitted=LABEL.fit(s,w-12);LABEL.draw(fitted,Math.round(x+(w-LABEL.width(fitted))/2),y,color);}
}
