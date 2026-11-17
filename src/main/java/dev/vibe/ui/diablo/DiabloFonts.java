package dev.vibe.ui.diablo;

import dev.vibe.ui.NeverLoseFont;
import dev.vibe.ui.ScrollingText;
import java.awt.Font;
import java.io.InputStream;

public final class DiabloFonts {
    private static final java.util.Map<NeverLoseFont,Integer> HEIGHTS=new java.util.IdentityHashMap<>();
    public static final NeverLoseFont LABEL=load("sfregular.ttf",18),TITLE=load("sfregular.ttf",24),ICON=load("guiicons.ttf",24),CHECK=load("checkmark.ttf",24);
    private DiabloFonts(){}
    private static NeverLoseFont load(String name,int size){
        try(InputStream stream=DiabloFonts.class.getResourceAsStream("/assets/vibe/diablo/"+name)){
            if(stream==null)throw new IllegalStateException("Missing Diablo font: "+name);
            Font face=Font.createFont(Font.TRUETYPE_FONT,stream).deriveFont((float)size);
            NeverLoseFont font=new NeverLoseFont(face);
            java.awt.Graphics2D graphics=new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_ARGB).createGraphics();
            graphics.setFont(face);int height=graphics.getFontMetrics().getStringBounds("Ag",graphics).getBounds().height;graphics.dispose();
            HEIGHTS.put(font,(height-8)/2);return font;
        }catch(Exception e){throw new IllegalStateException("Cannot load Diablo font "+name,e);}
    }
    public static int height(NeverLoseFont font){return HEIGHTS.get(font);}
    public static void text(NeverLoseFont font,String text,float x,float y,float width,int color){
        ScrollingText.draw(font,text,x+1,y-2.5F,width,(color&0xFF000000)|((color&0xFCFCFC)>>2),ScrollingText.Alignment.LEFT);
        ScrollingText.draw(font,text,x,y-3,width,color,ScrollingText.Alignment.LEFT);
    }
}
