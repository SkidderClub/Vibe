package dev.vibe.ui.menu;

import dev.vibe.language.LanguageManager;
import java.awt.*;
import java.awt.font.*;
import java.awt.image.BufferedImage;
import java.util.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.*;

/** Whole shaped lines rasterized at the current physical GUI scale; no fixed-size glyph cells. */
public final class MenuFont {
    public static final MenuFont BODY=new MenuFont(Font.PLAIN,12),SMALL=new MenuFont(Font.PLAIN,10),BOLD=new MenuFont(Font.BOLD,12),TITLE=new MenuFont(Font.BOLD,18);
    private static final FontRenderContext CONTEXT = new FontRenderContext(null, true, true);
    private final int style;
    private final float size;
    private final LinkedHashMap<String,Label> labels = new LinkedHashMap<String,Label>(64,.75F,true);
    private MenuFont bold;

    public MenuFont(int style,float size){this.style=style;this.size=size;}
    private float scale(){return Math.max(1,Math.round(MenuGlass.scale()));}
    public float width(String text){return label(clean(text),scale()).advance;}
    public float inkHeight(String text){return label(clean(text),scale()).height;}
    public float inkTop(String text){return 0;}
    private static String clean(String text){return LanguageManager.scriptFontText(text==null?"":text).replaceAll("\u00a7.","");}
    public String fit(String text,float width){
        if(width(text)<=width)return text;
        if(width("…")>width)return "";
        StringBuilder result=new StringBuilder();
        for(int i=0;i<text.length();){
            int count=Character.charCount(text.codePointAt(i));
            if(text.charAt(i)=='\u00a7'&&i+1<text.length()){result.append(text,i,i+2);i+=2;continue;}
            String next=result.toString()+text.substring(i,i+count);
            if(width(next+"…")>width)break;
            result.append(text,i,i+count);i+=count;
        }
        return result+"…";
    }
    /** Reflow complete documents without inserting ellipses or dropping words. */
    public java.util.List<String> wrap(String text,float available){
        java.util.List<String> result=new ArrayList<String>();
        while(!text.isEmpty()&&width(text)>available){
            int low=1,high=text.length();while(low<high){int middle=(low+high+1)/2;if(width(text.substring(0,middle))<=available)low=middle;else high=middle-1;}
            int cut=low;if(cut<text.length()&&Character.isHighSurrogate(text.charAt(cut-1)))cut=Math.max(1,cut-1);
            int space=text.lastIndexOf(' ',cut);if(space>0)cut=space;result.add(text.substring(0,cut));text=text.substring(cut);while(text.startsWith(" "))text=text.substring(1);
        }
        result.add(text);return result;
    }
    public void centered(String text,float x,float y,float width,float height,int color){
        Label label=label(clean(text),scale());
        draw(text,x+(width-label.inkWidth)*.5F-label.bearing,y+(height-label.height)*.5F,color);
    }
    public void middle(String text,float x,float y,float height,int color){draw(text,x,y+(height-inkHeight(text))*.5F,color);}
    public void centerX(String text,float center,float y,int color){Label label=label(clean(text),scale());draw(text,center-label.inkWidth*.5F-label.bearing,y,color);}
    public void draw(String text,float x,float y,int color){
        text=LanguageManager.scriptFontText(text==null?"":text);
        if(text.indexOf('\u00a7')<0){plain(text,x,y,color);return;}
        int active=color;boolean strong=false;StringBuilder run=new StringBuilder();
        for(int i=0;i<=text.length();i++){
            if(i==text.length()||text.charAt(i)=='\u00a7'&&i+1<text.length()){
                MenuFont face=strong?bold():this;String value=run.toString();face.plain(value,x,y,active);x+=face.width(value);run.setLength(0);
                if(i==text.length())break;
                char code=Character.toLowerCase(text.charAt(++i));int index="0123456789abcdef".indexOf(code);
                if(index>=0){int shade=(index&8)!=0?85:0;int r=((index&4)!=0?170:0)+shade,g=((index&2)!=0?170:0)+shade,b=((index&1)!=0?170:0)+shade;if(index==6)r+=85;active=(color&0xFF000000)|(r<<16)|(g<<8)|b;strong=false;}
                else if(code=='l')strong=true;else if(code=='r'){active=color;strong=false;}
            }else run.append(text.charAt(i));
        }
    }
    private MenuFont bold(){if((style&Font.BOLD)!=0)return this;if(bold==null)bold=new MenuFont(Font.BOLD,size);return bold;}
    private void plain(String text,float x,float y,int color){
        if(text.isEmpty())return;
        float scale=scale();Label label=label(text,scale);if(label.texture==null)label.upload();
        boolean alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        GlStateManager.enableTexture2D();GlStateManager.enableBlend();GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.bindTexture(label.texture.getGlTextureId());GlStateManager.color((color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,(color>>>24)/255F);
        float left=(Math.round((x+label.bearing)*scale)-2)/scale,top=(Math.round(y*scale)-2)/scale;
        float right=left+label.pixelWidth/scale,bottom=top+label.pixelHeight/scale;
        GL11.glBegin(GL11.GL_QUADS);GL11.glTexCoord2f(0,0);GL11.glVertex2f(left,top);GL11.glTexCoord2f(0,1);GL11.glVertex2f(left,bottom);
        GL11.glTexCoord2f(1,1);GL11.glVertex2f(right,bottom);GL11.glTexCoord2f(1,0);GL11.glVertex2f(right,top);GL11.glEnd();
        GlStateManager.color(1,1,1,1);if(alpha)GlStateManager.enableAlpha();
    }
    private Label label(String text,float scale){
        String key=scale+":"+text;Label label=labels.get(key);
        if(label==null){label=new Label(text,scale,new Font("Segoe UI",style,12).deriveFont(size*scale));labels.put(key,label);
            if(labels.size()>384){Iterator<Label> iterator=labels.values().iterator();Label oldest=iterator.next();if(oldest.texture!=null)oldest.texture.deleteGlTexture();iterator.remove();}}
        return label;
    }
    private static final class Label {
        final TextLayout layout;final Rectangle ink;final float advance,bearing,inkWidth,height;
        final int pixelWidth,pixelHeight;DynamicTexture texture;
        Label(String text,float scale,Font font){
            if(font.canDisplayUpTo(text)>=0)font=new Font("Dialog",font.getStyle(),12).deriveFont(font.getSize2D());
            layout=new TextLayout(text.isEmpty()?" ":text,font,CONTEXT);ink=layout.getPixelBounds(CONTEXT,0,0);
            advance=text.isEmpty()?0:layout.getAdvance()/scale;bearing=ink.x/scale;inkWidth=ink.width/scale;height=ink.height/scale;
            pixelWidth=Math.max(1,ink.width+4);pixelHeight=Math.max(1,ink.height+4);
        }
        void upload(){
            BufferedImage image=new BufferedImage(pixelWidth,pixelHeight,BufferedImage.TYPE_INT_ARGB);Graphics2D graphics=image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);graphics.setColor(Color.WHITE);
            layout.draw(graphics,2-ink.x,2-ink.y);graphics.dispose();
            int[] pixels=image.getRGB(0,0,pixelWidth,pixelHeight,null,0,pixelWidth);for(int i=0;i<pixels.length;i++)pixels[i]|=0x00FFFFFF;
            image.setRGB(0,0,pixelWidth,pixelHeight,pixels,0,pixelWidth);texture=new DynamicTexture(image);texture.setBlurMipmap(true,false);
            GlStateManager.bindTexture(texture.getGlTextureId());GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
        }
    }
}
