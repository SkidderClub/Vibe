package dev.vibe.ui;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/** Resolution-independent national flags, drawn in their own proportions. */
final class LanguageFlags {
    private LanguageFlags() { }
    static void draw(String language, int x, int y) {
        int w = 24, h = "English".equals(language) ? 12 : 16;
        y += (16 - h) / 2;
        try (GuiClip clip = new GuiClip(x, y, w, h)) {
            boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
            boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            GlStateManager.pushMatrix();
            try {
                GlStateManager.translate(x, y, 0); GlStateManager.scale(w / 30F, h / 20F, 1);
                if ("English".equals(language)) {
                    Gui.drawRect(0, 0, 30, 20, 0xFF012169);
                    polygon(0xFFFFFFFF, 0,0, 3,0, 30,18, 30,20, 27,20, 0,2);
                    polygon(0xFFFFFFFF, 27,0, 30,0, 30,2, 3,20, 0,20, 0,18);
                    polygon(0xFFC8102E, 0,0, 1.5F,0, 15,9, 15,10);
                    polygon(0xFFC8102E, 15,10, 16.5F,10, 30,19, 30,20);
                    polygon(0xFFC8102E, 28.5F,0, 30,0, 15,10, 13.5F,10);
                    polygon(0xFFC8102E, 0,20, 0,19, 13.5F,10, 15,10);
                    Gui.drawRect(12,0,18,20,0xFFFFFFFF); Gui.drawRect(0,7,30,13,0xFFFFFFFF);
                    Gui.drawRect(13,0,17,20,0xFFC8102E); Gui.drawRect(0,8,30,12,0xFFC8102E);
                } else if ("Chinese".equals(language)) {
                    Gui.drawRect(0,0,30,20,0xFFEE1C25);
                    star(5,5,3,-Math.PI / 2);
                    float[][] small = {{10,2},{12,4},{12,7},{10,9}};
                    for (float[] p : small) star(p[0],p[1],1,Math.atan2(5-p[1],5-p[0]));
                } else if ("Russian".equals(language)) {
                    polygon(0xFFFFFFFF,0,0,30,0,30,20F/3,0,20F/3);
                    polygon(0xFF0039A6,0,20F/3,30,20F/3,30,40F/3,0,40F/3);
                    polygon(0xFFD52B1E,0,40F/3,30,40F/3,30,20,0,20);
                } else if ("Japanese".equals(language)) {
                    Gui.drawRect(0,0,30,20,0xFFFFFFFF);
                    float[] points = new float[128];
                    for (int i=0;i<64;i++) { double angle=i*Math.PI/32; points[i*2]=15+(float)Math.cos(angle)*6; points[i*2+1]=10+(float)Math.sin(angle)*6; }
                    polygon(0xFFBC002D,points);
                } else {
                    Gui.drawRect(0,0,30,20,0xFFFFFFFF);
                    for (int row=-2;row<5;row++) for(int col=-3;col<7;col++) {
                        if ((row & 1) != 0) continue;
                        float cy=row*8, cx=col*8+row*4+cy*.25F;
                        polygon(0xFF0098D4,cx-2,cy-8,cx+4,cy,cx+2,cy+8,cx-4,cy);
                    }
                }
            } finally {
                GlStateManager.popMatrix();
                // Gui.drawRect changes Minecraft's blend cache. A raw glPopAttrib
                // would restore only the driver, leaving the final framebuffer
                // blit blending the menu's transparent pixels over white.
                if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
                if (texture) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
                GlStateManager.color(1,1,1,1);
            }
        }
    }
    private static void star(float x,float y,float radius,double angle) {
        GlStateManager.disableTexture2D(); GlStateManager.color(1, .87F, 0, 1);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN); GL11.glVertex2f(x,y);
        for(int i=0;i<=10;i++) { double a=angle+i*Math.PI/5; float r=i%2==0?radius:radius*.382F; GL11.glVertex2d(x+Math.cos(a)*r,y+Math.sin(a)*r); }
        GL11.glEnd(); GlStateManager.enableTexture2D();
    }
    private static void polygon(int color,float... points) {
        GlStateManager.disableTexture2D();
        GlStateManager.color((color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,1);
        GL11.glBegin(GL11.GL_POLYGON);
        for(int i=0;i<points.length;i+=2) GL11.glVertex2f(points[i],points[i+1]);
        GL11.glEnd(); GlStateManager.enableTexture2D();
    }
}
