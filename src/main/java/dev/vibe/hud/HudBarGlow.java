package dev.vibe.hud;

import dev.vibe.ui.effect.EffectState;
import org.lwjgl.opengl.*;

/** Small local additive halos, with no fullscreen framebuffer or blur pass. */
public final class HudBarGlow {
    private HudBarGlow(){}
    public static boolean glass(String theme){return "LiquidGlass".equalsIgnoreCase(theme)||"Lavalamp".equalsIgnoreCase(theme);}
    private static EffectState begin(){boolean clip=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);EffectState state=new EffectState();if(clip)GL11.glEnable(GL11.GL_SCISSOR_TEST);GL20.glUseProgram(0);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glEnable(GL11.GL_BLEND);GL14.glBlendEquation(GL14.GL_FUNC_ADD);GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ZERO,GL11.GL_ONE);return state;}
    public static void line(float x,float y,float width,float height,int color,float strength){
        if(width<=0||height<=0||strength<=0||(color>>>24)==0)return;
        try(EffectState ignored=begin()){
            for(int layer=5;layer>=1;layer--){float spread=layer*1.15F,radius=(height+spread*2)/2;colour(color,strength*(.065F-layer*.008F));
                float left=x-spread,top=y-spread,w=width+spread*2,h=height+spread*2;radius=Math.min(radius,w/2);
                GL11.glBegin(GL11.GL_TRIANGLE_FAN);GL11.glVertex2f(left+w/2,top+h/2);
                for(int i=0;i<=36;i++){int corner=(i%36)/9;double a=Math.toRadians(-90+corner*90+(i%9)*90D/8);GL11.glVertex2f(left+(corner<2?w-radius:radius)+(float)Math.cos(a)*radius,top+(corner==0||corner==3?radius:h-radius)+(float)Math.sin(a)*radius);}GL11.glEnd();
            }
        }
    }
    public static void ring(float cx,float cy,float radius,float width,float fraction,int color,float strength){
        if(fraction<=0||strength<=0||(color>>>24)==0)return;fraction=Math.min(1,fraction);int steps=Math.max(2,(int)Math.ceil(96*fraction));
        try(EffectState ignored=begin()){
            for(int layer=5;layer>=1;layer--){float half=width/2+layer*1.15F;colour(color,strength*(.065F-layer*.008F));GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
                for(int i=0;i<=steps;i++){double a=-Math.PI/2+Math.PI*2*fraction*i/steps;float c=(float)Math.cos(a),s=(float)Math.sin(a);GL11.glVertex2f(cx+c*Math.max(0,radius-half),cy+s*Math.max(0,radius-half));GL11.glVertex2f(cx+c*(radius+half),cy+s*(radius+half));}GL11.glEnd();
            }
        }
    }
    private static void colour(int color,float alpha){GL11.glColor4f((color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,Math.min(1,(color>>>24)/255F*alpha));}
}
