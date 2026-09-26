package dev.vibe.ui;

import dev.vibe.language.LanguageManager;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/** Resolution-independent national flags, drawn in their own proportions. */
public final class LanguageFlags {
    private LanguageFlags() { }
    public static void draw(String language, int x, int y) {
        language = LanguageManager.normalizeLanguage(language);
        int w = 24, h = "English".equals(language) ? 12 : 16;
        y += (16 - h) / 2;
        try (GuiClip clip = new GuiClip(x, y, w, h)) {
            boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
            boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            GlStateManager.pushMatrix();
            try {
                GlStateManager.translate(x, y, 0); GlStateManager.scale(w / 30F, h / 20F, 1);
                drawFlag(language);
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
    private static void drawFlag(String language) {
        if ("English".equals(language)) {
            Gui.drawRect(0, 0, 30, 20, 0xFF012169);
            polygon(0xFFFFFFFF, 0,0, 3,0, 30,18, 30,20, 27,20, 0,2);
            polygon(0xFFFFFFFF, 27,0, 30,0, 30,2, 3,20, 0,20, 0,18);
            polygon(0xFFC8102E, 0,0, 1.5F,0, 15,9, 15,10); polygon(0xFFC8102E, 15,10, 16.5F,10, 30,19, 30,20);
            polygon(0xFFC8102E, 28.5F,0, 30,0, 15,10, 13.5F,10); polygon(0xFFC8102E, 0,20, 0,19, 13.5F,10, 15,10);
            Gui.drawRect(12,0,18,20,0xFFFFFFFF); Gui.drawRect(0,7,30,13,0xFFFFFFFF); Gui.drawRect(13,0,17,20,0xFFC8102E); Gui.drawRect(0,8,30,12,0xFFC8102E);
        } else if ("Chinese".equals(language)) {
            fill(0xFFEE1C25); star(0xFFFFDE00, 5,5,3,-Math.PI / 2);
            float[][] small = {{10,2},{12,4},{12,7},{10,9}};
            for (float[] p : small) star(0xFFFFDE00, p[0],p[1],1,Math.atan2(5-p[1],5-p[0]));
        } else if ("Russian".equals(language)) horizontal(0xFFFFFFFF, 0xFF0039A6, 0xFFD52B1E);
        else if ("Japanese".equals(language)) { fill(0xFFFFFFFF); circle(0xFFBC002D, 15, 10, 6); }
        else if ("Finnish".equals(language)) cross(0xFFFFFFFF, 0xFF003580, 9, 0, 13, 20, 0, 8, 30, 12);
        else if ("Swedish".equals(language)) cross(0xFF006AA7, 0xFFFECC00, 8, 0, 12, 20, 0, 8, 30, 12);
        else if ("Greek".equals(language)) { horizontal(0xFF0D5EAF,0xFFFFFFFF,0xFF0D5EAF,0xFFFFFFFF,0xFF0D5EAF,0xFFFFFFFF,0xFF0D5EAF,0xFFFFFFFF,0xFF0D5EAF); Gui.drawRect(0,0,12,11,0xFF0D5EAF); Gui.drawRect(4,0,7,11,0xFFFFFFFF); Gui.drawRect(0,4,12,7,0xFFFFFFFF); }
        else if ("Spanish".equals(language)) horizontal(0xFFAA151B,0xFFF1BF00,0xFFF1BF00,0xFFAA151B);
        else if ("German".equals(language)) horizontal(0xFF111111,0xFFDD0000,0xFFFFCE00);
        else if ("French".equals(language) || "Italian".equals(language) || "Romanian".equals(language)) {
            if ("French".equals(language)) vertical(0xFF002395,0xFFFFFFFF,0xFFED2939);
            else if ("Italian".equals(language)) vertical(0xFF009246,0xFFFFFFFF,0xFFCE2B37);
            else vertical(0xFF002B7F,0xFFFCD116,0xFFCE1126);
        } else if ("Enchantment Table".equals(language) || "Aurebesh".equals(language)) {
            fill("Aurebesh".equals(language) ? 0xFF111725 : 0xFF201640);
            Gui.drawRect(3,4,27,6,0xFF38D7DE); Gui.drawRect(6,9,24,11,0xFFB36BFF); Gui.drawRect(9,14,21,16,0xFF38D7DE);
            if ("Aurebesh".equals(language)) star(0xFFE8C96A, 15, 10, 4, -Math.PI / 2);
        } else if ("Portuguese".equals(language)) { vertical(0xFF006600,0xFF006600,0xFFFF0000); circle(0xFFFFD700, 13,10,5); circle(0xFF004B87,13,10,2); }
        else if ("Ukrainian".equals(language)) horizontal(0xFF0057B7,0xFFFFD700);
        else if ("Hindi".equals(language) || "Marathi".equals(language) || "Telugu".equals(language) || "Gujarati".equals(language) || "Tamil".equals(language)) { horizontal(0xFFFF9933,0xFFFFFFFF,0xFF138808); circle(0xFF000080,15,10,2); }
        else if ("Standard Arabic".equals(language)) { fill(0xFF006C35); Gui.drawRect(6,9,24,11,0xFFFFFFFF); }
        else if ("Bengali".equals(language)) { fill(0xFF006A4E); circle(0xFFF42A41, 16,10,6); }
        else if ("Indonesian".equals(language) || "Javanese".equals(language) || "Polish".equals(language)) horizontal(0xFFCE1126, 0xFFFFFFFF);
        else if ("Urdu".equals(language)) { fill(0xFF115740); Gui.drawRect(0,0,7,20,0xFFFFFFFF); circle(0xFFFFFFFF,18,10,5); circle(0xFF115740,20,9,5); star(0xFFFFFFFF,22,6,1.8F,-Math.PI/2); }
        else if ("Nigerian Pidgin".equals(language) || "Hausa".equals(language)) vertical(0xFF008751,0xFFFFFFFF,0xFF008751);
        else if ("Egyptian Arabic".equals(language)) { horizontal(0xFFCE1126,0xFFFFFFFF,0xFF000000); circle(0xFFC09300,15,10,1.5F); }
        else if ("Vietnamese".equals(language)) { fill(0xFFDA251D); star(0xFFFFFF00,15,10,6,-Math.PI/2); }
        else if ("Swahili".equals(language)) { horizontal(0xFF000000,0xFFCE1126,0xFF006600); Gui.drawRect(0,6,30,8,0xFFFFFFFF); Gui.drawRect(0,12,30,14,0xFFFFFFFF); }
        else if ("Turkish".equals(language)) { fill(0xFFE30A17); circle(0xFFFFFFFF,13,10,5); circle(0xFFE30A17,15,9,5); star(0xFFFFFFFF,20,10,2,-Math.PI/2); }
        else if ("Western Punjabi".equals(language)) { vertical(0xFFFF8C00,0xFFFF8C00,0xFF008751); circle(0xFFFFFFFF,11,10,3); }
        else if ("Tagalog".equals(language)) { horizontal(0xFF0038A8,0xFFCE1126); polygon(0xFFFFFFFF,0,0,13,10,0,20); star(0xFFFCD116,5,10,2,-Math.PI/2); }
        else if ("Iranian Persian".equals(language)) { horizontal(0xFF239F40,0xFFFFFFFF,0xFFDA0000); Gui.drawRect(13,7,17,13,0xFFDA0000); }
        else if ("Korean".equals(language)) { fill(0xFFFFFFFF); circle(0xFFCD2E3A,15,9,4); circle(0xFF0047A0,15,12,4); }
        else if ("Amharic".equals(language)) { horizontal(0xFF078930,0xFFFCDD09,0xFFDA121A); circle(0xFF0F47AF,15,10,3); }
        else if ("Thai".equals(language)) horizontal(0xFFA51931,0xFFFFFFFF,0xFF2D2A4A,0xFF2D2A4A,0xFFFFFFFF,0xFFA51931);
        else if ("Dutch".equals(language)) horizontal(0xFFAE1C28,0xFFFFFFFF,0xFF21468B);
        else if ("Nepali".equals(language)) { fill(0xFFFFFFFF); polygon(0xFF003893,1,0,18,7,1,9,18,18,1,20); polygon(0xFFDC143C,3,2,14,7,3,10,14,17,3,18); star(0xFFFFFFFF,7,7,2,-Math.PI/2); }
        else if ("Czech".equals(language)) { horizontal(0xFFFFFFFF,0xFFD7141A); polygon(0xFF11457E,0,0,12,10,0,20); }
        else if ("Zulu".equals(language)) { horizontal(0xFF007A4D,0xFFFFFFFF,0xFFDE3831,0xFFFFFFFF,0xFF007A4D); polygon(0xFF000000,0,0,14,10,0,20); }
        else { // Bavarian
            fill(0xFFFFFFFF);
            for (int row=-2;row<5;row++) for(int col=-3;col<7;col++) if ((row & 1) == 0) {
                float cy=row*8, cx=col*8+row*4+cy*.25F;
                polygon(0xFF0098D4,cx-2,cy-8,cx+4,cy,cx+2,cy+8,cx-4,cy);
            }
        }
    }

    private static void fill(int color) { Gui.drawRect(0, 0, 30, 20, color); }
    private static void horizontal(int... colors) { for (int index=0; index<colors.length; index++) Gui.drawRect(0, index * 20 / colors.length, 30, (index + 1) * 20 / colors.length, colors[index]); }
    private static void vertical(int... colors) { for (int index=0; index<colors.length; index++) Gui.drawRect(index * 30 / colors.length, 0, (index + 1) * 30 / colors.length, 20, colors[index]); }
    private static void cross(int base, int mark, int x1, int y1, int x2, int y2, int hx1, int hy1, int hx2, int hy2) { fill(base); Gui.drawRect(x1,y1,x2,y2,mark); Gui.drawRect(hx1,hy1,hx2,hy2,mark); }
    private static void circle(int color, float x, float y, float radius) {
        float[] points = new float[66];
        for (int i=0;i<=32;i++) { double angle=i*Math.PI/16; points[i*2]=x+(float)Math.cos(angle)*radius; points[i*2+1]=y+(float)Math.sin(angle)*radius; }
        polygon(color, points);
    }
    private static void star(int color, float x,float y,float radius,double angle) {
        GlStateManager.disableTexture2D(); GlStateManager.color((color>>16&255)/255F, (color>>8&255)/255F, (color&255)/255F, 1);
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
