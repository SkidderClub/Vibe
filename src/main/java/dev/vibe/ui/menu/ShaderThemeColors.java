package dev.vibe.ui.menu;

import java.awt.Color;
import java.nio.ByteBuffer;

/** Extracts the colourful light in a thumbnail, rather than its mostly black background. */
final class ShaderThemeColors {
    private ShaderThemeColors(){}
    static int sample(ByteBuffer pixels){
        double[] weights=new double[24],red=new double[24],green=new double[24],blue=new double[24];float[] hsv=new float[3];
        for(int i=0;i+3<pixels.limit();i+=4){int r=pixels.get(i)&255,g=pixels.get(i+1)&255,b=pixels.get(i+2)&255;Color.RGBtoHSB(r,g,b,hsv);double weight=hsv[1]*hsv[1]*hsv[2]*hsv[2];if(weight<.015)continue;int bucket=Math.min(23,(int)(hsv[0]*24));weights[bucket]+=weight;red[bucket]+=r*weight;green[bucket]+=g*weight;blue[bucket]+=b*weight;}
        int best=0;for(int i=1;i<weights.length;i++)if(weights[i]>weights[best])best=i;if(weights[best]<.01)return MenuPalette.GRAPHITE.accent;
        Color.RGBtoHSB((int)(red[best]/weights[best]),(int)(green[best]/weights[best]),(int)(blue[best]/weights[best]),hsv);return Color.HSBtoRGB(hsv[0],Math.max(.35F,Math.min(.7F,hsv[1])),1);
    }
    static int fallback(String shader){String s=shader.toLowerCase(java.util.Locale.ROOT);if(s.contains("red")||s.contains("blood")||s.contains("heart"))return MenuPalette.ROSE.accent;if(s.contains("green")||s.contains("matrix")||s.contains("polar"))return MenuPalette.MINT.accent;if(s.contains("sun")||s.contains("sand")||s.contains("fire")||s.contains("lava"))return MenuPalette.AMBER.accent;if(s.contains("purple")||s.contains("lila")||s.contains("galaxy"))return MenuPalette.LAVENDER.accent;return MenuPalette.OCEAN.accent;}
}
