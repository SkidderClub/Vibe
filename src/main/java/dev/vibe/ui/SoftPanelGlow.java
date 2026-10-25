package dev.vibe.ui;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Cached Gaussian distance masks: soft, centered halos without stacked rectangle outlines. */
public final class SoftPanelGlow {
    private static final Map<Integer,ResourceLocation> MASKS=new HashMap<>();
    private SoftPanelGlow() { }
    public static void draw(int x,int y,int w,int h,int radius,int spread,int color) {
        if(w<=0||h<=0||(color>>>24)==0)return;
        radius=Math.max(1,Math.min(16,radius));spread=Math.max(2,Math.min(20,spread));
        int key=radius*32+spread,cap=radius+spread;
        ResourceLocation mask=MASKS.get(key);Minecraft mc=Minecraft.getMinecraft();
        if(mask==null){
            int size=cap*6;BufferedImage image=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
            double center=size/2D,half=center-spread*2,r=radius*2,sigma=spread*2/2.8;
            for(int py=0;py<size;py++)for(int px=0;px<size;px++){
                double qx=Math.abs(px+.5-center)-(half-r),qy=Math.abs(py+.5-center)-(half-r);
                double d=Math.hypot(Math.max(qx,0),Math.max(qy,0))+Math.min(Math.max(qx,qy),0)-r;
                int a=(int)Math.round(255*Math.exp(-Math.max(0,d)*Math.max(0,d)/(2*sigma*sigma)));
                image.setRGB(px,py,a<<24|0xFFFFFF);
            }
            DynamicTexture texture=new DynamicTexture(image);texture.setBlurMipmap(true,false);
            mask=mc.getTextureManager().getDynamicTextureLocation("vibe_soft_panel_"+key,texture);MASKS.put(key,mask);
        }
        GlStateManager.enableTexture2D();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
        boolean alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST);GlStateManager.disableAlpha();mc.getTextureManager().bindTexture(mask);
        GlStateManager.color((color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,(color>>>24)/255F);
        int outerW=w+spread*2,outerH=h+spread*2,c=Math.min(cap,Math.min(outerW,outerH)/2);
        int[] xs={x-spread,x-spread+c,x+w+spread-c,x+w+spread},ys={y-spread,y-spread+c,y+h+spread-c,y+h+spread};
        GL11.glBegin(GL11.GL_QUADS);
        for(int row=0;row<3;row++)for(int col=0;col<3;col++){
            float u=col/3F,v=row/3F;GL11.glTexCoord2f(u,v);GL11.glVertex2f(xs[col],ys[row]);
            GL11.glTexCoord2f(u,v+1/3F);GL11.glVertex2f(xs[col],ys[row+1]);
            GL11.glTexCoord2f(u+1/3F,v+1/3F);GL11.glVertex2f(xs[col+1],ys[row+1]);
            GL11.glTexCoord2f(u+1/3F,v);GL11.glVertex2f(xs[col+1],ys[row]);
        }
        GL11.glEnd();GlStateManager.color(1,1,1,1);if(alpha)GlStateManager.enableAlpha();
    }
}
