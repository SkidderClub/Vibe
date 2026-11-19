package dev.vibe.ui.adjust;
import dev.vibe.ui.NeverLoseFont;
import java.awt.Font;
/** Adjust uses Tahoma throughout its compact controls and target HUD. */
public final class AdjustFonts {
    public static final NeverLoseFont LABEL=font(false,20);
    public static final NeverLoseFont TITLE=font(true,24);
    public static final NeverLoseFont SMALL=font(false,16);
    public static final NeverLoseFont HUD=font(false,12);
    public static final NeverLoseFont HUD_SMALL=font(false,10);
    private static NeverLoseFont font(boolean bold,int size){
        try(java.io.InputStream in=AdjustFonts.class.getResourceAsStream("/assets/vibe/adjust/"+(bold?"tahoma-bold.otf":"tahoma.ttf"))){
            if(in!=null)return new NeverLoseFont(Font.createFont(Font.TRUETYPE_FONT,in).deriveFont((float)size));
        }catch(java.io.IOException|java.awt.FontFormatException ignored){}
        return new NeverLoseFont("Tahoma",bold?Font.BOLD:Font.PLAIN,size);
    }
    private AdjustFonts(){}
}
