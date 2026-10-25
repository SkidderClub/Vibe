package dev.vibe.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** One antialiased nine-slice mask keeps circular corners smooth at every GUI scale. */
public final class MenuRoundedRenderer {
    private static ResourceLocation mask;

    private MenuRoundedRenderer() { }

    public static void rect(int x, int y, int width, int height, int radius, int color) {
        horizontalGradient(x,y,width,height,radius,color,color);
    }

    public static void horizontalGradient(int x,int y,int width,int height,int radius,int color,int rightColor) {
        gradient(x,y,width,height,radius,color,rightColor,false);
    }
    public static void verticalGradient(int x,int y,int width,int height,int radius,int color,int bottomColor) {
        gradient(x,y,width,height,radius,color,bottomColor,true);
    }
    private static void gradient(int x,int y,int width,int height,int radius,int color,int rightColor,boolean vertical) {
        if (width <= 0 || height <= 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mask == null) {
            BufferedImage image = new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRoundRect(0, 0, 96, 96, 64, 64);
            graphics.dispose();
            DynamicTexture texture=new DynamicTexture(image);texture.setBlurMipmap(true,false);
            mask = mc.getTextureManager().getDynamicTextureLocation("vibe_menu_rounding", texture);
        }
        int r = Math.min(radius, Math.min(width, height) / 2);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        boolean alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        GlStateManager.disableAlpha();
        mc.getTextureManager().bindTexture(mask);
        GlStateManager.color((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F,
                (color & 255) / 255F, (color >>> 24) / 255F);
        int[] xs = {x, x + r, x + width - r, x + width};
        int[] ys = {y, y + r, y + height - r, y + height};
        boolean gradient=color!=rightColor;
        if(gradient)GL11.glShadeModel(GL11.GL_SMOOTH);
        // Nine slices share one texture and draw call, including gradient corners.
        GL11.glBegin(GL11.GL_QUADS);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                    float u=column/3F,v=row/3F;
                    int left=gradient?RenderUtils.blend(color,rightColor,vertical?(ys[row]-y)/(float)height:(xs[column]-x)/(float)width):color;
                    int right=gradient?RenderUtils.blend(color,rightColor,vertical?(ys[row+1]-y)/(float)height:(xs[column+1]-x)/(float)width):color;
                    vertex(xs[column],ys[row],u,v,left);vertex(xs[column],ys[row+1],u,v+1/3F,vertical?right:left);
                    vertex(xs[column+1],ys[row+1],u+1/3F,v+1/3F,right);vertex(xs[column+1],ys[row],u+1/3F,v,vertical?left:right);
            }
        }
        GL11.glEnd();
        if(gradient)GL11.glShadeModel(GL11.GL_FLAT);
        GlStateManager.resetColor();
        GlStateManager.color(1, 1, 1, 1);
        if (alphaTest) GlStateManager.enableAlpha();
    }
    private static void vertex(int x,int y,float u,float v,int c){
        GL11.glColor4f((c>>16&255)/255F,(c>>8&255)/255F,(c&255)/255F,(c>>>24)/255F);GL11.glTexCoord2f(u,v);GL11.glVertex2f(x,y);
    }
}
