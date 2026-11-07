package dev.vibe.hud;

import dev.vibe.Vibe;
import dev.vibe.module.impl.client.NameProtectModule;
import net.minecraft.client.gui.FontRenderer;

/** Draw-time decoration also updates names in old chat messages without rewriting their contents. */
public final class NameProtectRenderer {
    public static final int UNHANDLED=Integer.MIN_VALUE;
    private static final ThreadLocal<Boolean> DRAWING=new ThreadLocal<>();
    private NameProtectRenderer(){}
    public static int draw(Object renderer,String text,float x,float y,int color,boolean shadow){return drawAt(renderer,text,x,y,color,shadow,System.currentTimeMillis());}
    public static int drawAt(Object renderer,String text,float x,float y,int color,boolean shadow,long now){
        if(Boolean.TRUE.equals(DRAWING.get())||!(renderer instanceof FontRenderer)||text==null||Vibe.getInstance()==null||Vibe.getInstance().getModuleManager()==null)return UNHANDLED;
        NameProtectModule protect=Vibe.getInstance().getModuleManager().getModule(NameProtectModule.class);
        if(protect==null||!protect.stylesOwnName())return UNHANDLED;
        String name=protect.getPlainGamertag();if(name.isEmpty()||!text.contains(name))return UNHANDLED;
        FontRenderer font=(FontRenderer)renderer;int alpha=(color&0xFC000000)==0?255:color>>>24;
        DRAWING.set(true);
        try(TextGlowScope ignored=TextGlowScope.begin(protect.nameStyle.glow.isEnabled()&&font.getClass()==FontRenderer.class)){
            int from=0,index;
            while((index=text.indexOf(name,from))>=0){
                String before=text.substring(from,index);String format=FontRenderer.getFormatFromString(text.substring(0,from));
                font.drawString(format+before,x,y,color,shadow);x+=font.getStringWidth(format+before);
                String styles=FontRenderer.getFormatFromString(text.substring(0,index)).replaceAll("(?i)§[0-9a-fr]","");
                HudTextStyle s=protect.nameStyle;
                for(int pass=0;pass<3;pass++){
                    if(pass==0&&!s.glow.isEnabled()||pass==1&&!shadow)continue;
                    float cursor=x;
                    for(int i=0;i<name.length();i++){
                        String letter=styles+name.substring(i,i+1);int c=protect.overridesOwnColor()?s.colorAt(now,i):color|0xFF000000;
                        int a=protect.overridesOwnColor()?Math.round((c>>>24)*alpha/255F):alpha;if(a<4){cursor+=font.getStringWidth(letter);continue;}
                        if(pass==0){int ga=Math.round(a*s.glowStrength.getFloat()*.18F);float radius=Math.min(1.25F,s.glowRadius.getFloat()*.35F);
                            if(ga>=4)for(int n=0;n<4;n++){double angle=n*Math.PI/2;font.drawString(letter,cursor+(float)Math.cos(angle)*radius,y+(float)Math.sin(angle)*radius,ga<<24|c&0xFFFFFF,false);}
                        }else if(pass==1)font.drawString(letter,cursor+1,y+1,a<<24|((c&0xFCFCFC)>>2),false);
                        else font.drawString(letter,cursor,y,a<<24|c&0xFFFFFF,false);
                        cursor+=font.getStringWidth(letter);
                    }
                }
                x+=font.getStringWidth(styles+name);from=index+name.length();
            }
            return font.drawString(FontRenderer.getFormatFromString(text.substring(0,from))+text.substring(from),x,y,color,shadow);
        }finally{DRAWING.remove();}
    }
    /** Vibe's smooth GUI font uses the same decoration without changing its glyph advances. */
    public static boolean drawCustom(dev.vibe.ui.NeverLoseFont font,String text,float x,float y,int color){
        if(Boolean.TRUE.equals(DRAWING.get())||text==null||Vibe.getInstance()==null||Vibe.getInstance().getModuleManager()==null)return false;
        NameProtectModule protect=Vibe.getInstance().getModuleManager().getModule(NameProtectModule.class);
        if(protect==null||!protect.stylesOwnName())return false;String name=protect.getPlainGamertag();if(name.isEmpty()||!text.contains(name))return false;
        long now=System.currentTimeMillis();HudTextStyle s=protect.nameStyle;DRAWING.set(true);
        try(TextGlowScope ignored=TextGlowScope.begin(s.glow.isEnabled())){
            int from=0,index;
            while((index=text.indexOf(name,from))>=0){String before=text.substring(from,index);font.draw(before,x,y,color);x+=font.width(before);
                for(int pass=0;pass<2;pass++){
                    if(pass==0&&!s.glow.isEnabled())continue;float cursor=x;
                    for(int i=0;i<name.length();i++){String letter=name.substring(i,i+1);int c=protect.overridesOwnColor()?s.colorAt(now,i):color;
                        int a=Math.round((c>>>24)*(color>>>24)/255F);
                        if(pass==0){int ga=Math.round(a*s.glowStrength.getFloat()*.18F);float r=Math.min(1.25F,s.glowRadius.getFloat()*.35F);
                            if(ga>=1)for(int n=0;n<4;n++){double angle=n*Math.PI/2;font.draw(letter,cursor+(float)Math.cos(angle)*r,y+(float)Math.sin(angle)*r,ga<<24|c&0xFFFFFF);}
                        }else if(a>0)font.draw(letter,cursor,y,a<<24|c&0xFFFFFF);
                        cursor+=font.width(letter);
                    }
                }
                x+=font.width(name);from=index+name.length();
            }
            font.draw(text.substring(from),x,y,color);return true;
        }finally{DRAWING.remove();}
    }
}
