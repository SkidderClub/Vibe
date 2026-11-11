package dev.vibe.ui.render;

import dev.vibe.Vibe;
import dev.vibe.module.impl.movement.TeleportModule;
import dev.vibe.teleport.PortalSprite;
import dev.vibe.ui.effect.EffectState;
import java.awt.image.BufferedImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.*;

/** Opening/closing, counter-rotating atlas layers; one cached texture for every portal. */
public final class TeleportPortalRenderer implements AutoCloseable {
    private final ResourceLocation location=new ResourceLocation("vibe:teleport/portal-atlas");private DynamicTexture texture;private int width,height;private boolean failed;
    public void render(){Minecraft mc=Minecraft.getMinecraft();TeleportModule m=Vibe.getInstance().getModuleManager().getModule(TeleportModule.class);if(m==null||!m.effects.isEnabled()||mc.theWorld==null||mc.getRenderManager()==null||m.portals().isEmpty())return;
        render(m,mc.getRenderManager().viewerPosX,mc.getRenderManager().viewerPosY,mc.getRenderManager().viewerPosZ,System.nanoTime()/1000000);
    }
    public void render(TeleportModule m,double ox,double oy,double oz,long now){
        if(m.portals().isEmpty()||!prepare())return;int mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);GL11.glMatrixMode(GL11.GL_MODELVIEW);
        try(EffectState ignored=new EffectState()){
            GL20.glUseProgram(0);for(int i=1;i<=2;i++){GL13.glActiveTexture(GL13.GL_TEXTURE0+i);GL11.glDisable(GL11.GL_TEXTURE_2D);}GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture.getGlTextureId());GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL11.GL_TEXTURE_ENV_MODE,GL11.GL_MODULATE);
            GL11.glEnable(GL11.GL_BLEND);GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD,GL14.GL_FUNC_ADD);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);
            for(TeleportModule.Portal p:m.portals()){
                float t=p.progress(now);if(t>=1)continue;float openness=smooth(t/.18F)*(1-smooth((t-.72F)/.28F));if(openness<.001)continue;
                double size=m.size.getDouble()*openness;GL11.glPushMatrix();try{GL11.glTranslated(p.x-ox,p.y+(p.floor?0:1.1*m.size.getDouble())-oy,p.z-oz);GL11.glRotatef(-p.yaw,0,1,0);if(p.floor){GL11.glRotatef(-90,1,0,0);GL11.glScaled(p.span*.78*openness,p.span*openness,1);}else GL11.glScaled(1.8*size,2.3*size,1);
                    tint=p.color;
                    GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);layer(0,t*250,.76F*openness,1);layer(1,-t*190,.44F*openness,.95F);
                    if(m.glow.getDouble()>0){GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ZERO,GL11.GL_ONE);layer(2,-t*130,.3F*m.glow.getFloat()*openness,1.08F);layer(0,t*250,.17F*m.glow.getFloat()*openness,1.04F);}
                    GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_LINE_SMOOTH);for(int pass=0;pass<2;pass++){GL11.glLineWidth(pass==0?6:1.6F);GL11.glColor4f((tint>>16&255)/255F,(tint>>8&255)/255F,(tint&255)/255F,openness*(pass==0?.18F:.9F));GL11.glBegin(GL11.GL_LINE_LOOP);for(int point=0;point<72;point++){double angle=point*Math.PI/36,r=.46+Math.sin(angle*5+t*20)*.004;GL11.glVertex3d(Math.cos(angle)*r,Math.sin(angle)*r,.002);}GL11.glEnd();}GL11.glEnable(GL11.GL_TEXTURE_2D);
                }finally{GL11.glPopMatrix();}
            }
        }finally{GL11.glMatrixMode(mode);GlStateManager.resetColor();}
    }
    private int tint=0xFFFFFFFF;
    private void layer(int index,float angle,float alpha,float scale){
        int[] r=PortalSprite.REGIONS[index];float u=(r[0]+.5F)/width,v=(r[1]+.5F)/height,U=(r[0]+r[2]-.5F)/width,V=(r[1]+r[3]-.5F)/height;
        GL11.glPushMatrix();GL11.glRotatef(angle,0,0,1);GL11.glScalef(scale,scale,1);GL11.glColor4f((tint>>16&255)/255F,(tint>>8&255)/255F,(tint&255)/255F,alpha);GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u,V);GL11.glVertex3f(-.5F,-.5F,0);GL11.glTexCoord2f(U,V);GL11.glVertex3f(.5F,-.5F,0);GL11.glTexCoord2f(U,v);GL11.glVertex3f(.5F,.5F,0);GL11.glTexCoord2f(u,v);GL11.glVertex3f(-.5F,.5F,0);GL11.glEnd();GL11.glPopMatrix();
    }
    private boolean prepare(){if(texture!=null)return true;if(failed)return false;int prior=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try{BufferedImage image=PortalSprite.read();width=image.getWidth();height=image.getHeight();for(int y=0;y<height;y++)for(int x=0;x<width;x++){int c=image.getRGB(x,y),light=Math.max(c>>16&255,Math.max(c>>8&255,c&255));image.setRGB(x,y,(c&0xFF000000)|(light<<16)|(light<<8)|light);}texture=new DynamicTexture(image);texture.setBlurMipmap(true,false);Minecraft.getMinecraft().getTextureManager().loadTexture(location,texture);return true;}
        catch(Exception|LinkageError e){failed=true;org.apache.logging.log4j.LogManager.getLogger("Vibe").warn("Portal atlas unavailable",e);return false;}finally{GlStateManager.bindTexture(prior);}
    }
    private static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    @Override public void close(){if(texture!=null)Minecraft.getMinecraft().getTextureManager().deleteTexture(location);texture=null;failed=false;}
}
