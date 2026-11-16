package dev.vibe.ui.novoline;

import dev.vibe.ui.NeverLoseFont;
import dev.vibe.ui.ScrollingText;
import java.awt.Font;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;

/** The actual supplied SF and Styles Icons faces. Textures remain lazy until first draw. */
public final class NovolineFonts {
    public static final NeverLoseFont LABEL=face("SF.ttf",18), HEADER=face("SF.ttf",20), ARRAY=face("SF.ttf",21), ICON=face("stylesicons.ttf",24), NOTICE=face("stylesicons.ttf",50);
    private static FontRenderer pixel;
    private NovolineFonts(){}
    private static NeverLoseFont face(String name,int size){
        try(InputStream stream=NovolineFonts.class.getResourceAsStream("/assets/vibe/novoline/"+name)){
            if(stream==null)throw new IllegalStateException("Missing Novoline font: "+name);
            return new NeverLoseFont(Font.createFont(Font.TRUETYPE_FONT,stream).deriveFont((float)size));
        }catch(Exception failure){throw new IllegalStateException("Cannot load Novoline font: "+name,failure);}
    }
    public static FontRenderer pixel(){
        if(pixel==null){Minecraft mc=Minecraft.getMinecraft();pixel=new FontRenderer(mc.gameSettings,new ResourceLocation("vibe","novoline/pixel.png"),mc.getTextureManager(),false);pixel.onResourceManagerReload(mc.getResourceManager());}
        return pixel;
    }
    public static void text(NeverLoseFont font,String value,float x,float y,float width,int color){
        // Source SF draws its top at y-3; use an equivalent baseline with Vibe's filtered atlas.
        ScrollingText.draw(font,value,x+.5F,y+.5F-3,width,(color&0xFF000000)|((color&0xFCFCFC)>>2),ScrollingText.Alignment.LEFT);
        ScrollingText.draw(font,value,x,y-3,width,color,ScrollingText.Alignment.LEFT);
    }
}
