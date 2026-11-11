package dev.vibe.ui.render;

import dev.vibe.hitmarker.*;
import dev.vibe.module.impl.visual.HitmarkerModule;
import dev.vibe.ui.effect.EffectState;
import java.nio.*;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** One cached radial texture, one glow batch and one core batch; no framebuffer or scene blur. */
public final class HitParticleRenderer implements AutoCloseable {
    private final FloatBuffer view=BufferUtils.createFloatBuffer(16);
    private int texture;
    public void render(HitmarkerModule module,float partial){
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.theWorld==null||!module.particles.isWorld(mc.theWorld))return;
        RenderManager manager=mc.getRenderManager();
        render(module.particles.particles(),module.particleSettings,manager.viewerPosX,manager.viewerPosY,manager.viewerPosZ,partial);
    }
    public void render(List<HitParticles.Particle> particles,HitParticleSettings settings,double originX,double originY,double originZ,float partial){
        if(particles.isEmpty())return;partial=Math.max(0,Math.min(1,partial));
        int matrixMode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);view.clear();GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,view);
        try(EffectState ignored=new EffectState()){
            GL20.glUseProgram(0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_BLEND);GL11.glDepthMask(false);
            if(settings.throughWalls.isEnabled())GL11.glDisable(GL11.GL_DEPTH_TEST);else GL11.glEnable(GL11.GL_DEPTH_TEST);
            // Multitexture/lightmap state must not darken the emissive sprite.
            for(int unit=1;unit<3;unit++){GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glDisable(GL11.GL_TEXTURE_2D);}
            GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glEnable(GL11.GL_TEXTURE_2D);
            if(texture==0)texture=sprite();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL11.GL_TEXTURE_ENV_MODE,GL11.GL_MODULATE);
            if(settings.glow.isEnabled()){
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);draw(particles,settings,originX,originY,originZ,partial,true);
            }
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);draw(particles,settings,originX,originY,originZ,partial,false);
            boolean materials=false;for(HitParticles.Particle p:particles)if(p.material!=null){materials=true;break;}
            if(materials){GL11.glBindTexture(GL11.GL_TEXTURE_2D,Minecraft.getMinecraft().getTextureManager().getTexture(net.minecraft.client.renderer.texture.TextureMap.locationBlocksTexture).getGlTextureId());drawMaterials(particles,settings,originX,originY,originZ,partial);}
        }finally{GL11.glMatrixMode(matrixMode);}
    }
    private void draw(List<HitParticles.Particle> particles,HitParticleSettings settings,double ox,double oy,double oz,float partial,boolean glow){
        GL11.glBegin(GL11.GL_QUADS);
        for(HitParticles.Particle p:particles){
            if(!glow&&p.material!=null)continue;
            float opacity=HitParticles.opacity(p,settings,partial);if(opacity<=0)continue;
            int color=HitParticles.color(p,settings,partial);float r=(color>>16&255)/255F,g=(color>>8&255)/255F,b=(color&255)/255F;
            double radius=p.radius*(glow?settings.glowRadius.getDouble():1);
            float alpha=(color>>>24)/255F*opacity*(glow?settings.glowStrength.getFloat()*.35F:1);
            if(!glow){r+=.55F*(1-r);g+=.55F*(1-g);b+=.55F*(1-b);}
            GL11.glColor4f(r,g,b,Math.min(1,alpha));
            double x=p.previousX+(p.x-p.previousX)*partial-ox,y=p.previousY+(p.y-p.previousY)*partial-oy,z=p.previousZ+(p.z-p.previousZ)*partial-oz;
            corner(x,y,z,-radius,-radius,0,1);corner(x,y,z,radius,-radius,1,1);corner(x,y,z,radius,radius,1,0);corner(x,y,z,-radius,radius,0,0);
        }
        GL11.glEnd();
    }
    private void corner(double x,double y,double z,double right,double up,float u,float v){
        GL11.glTexCoord2f(u,v);GL11.glVertex3d(x+right*view.get(0)+up*view.get(1),y+right*view.get(4)+up*view.get(5),z+right*view.get(8)+up*view.get(9));
    }
    private void drawMaterials(List<HitParticles.Particle> particles,HitParticleSettings settings,double ox,double oy,double oz,float partial){
        GL11.glBegin(GL11.GL_QUADS);for(HitParticles.Particle p:particles){if(p.material==null)continue;float opacity=HitParticles.opacity(p,settings,partial);int color=HitParticles.color(p,settings,partial);GL11.glColor4f((color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,(color>>>24)/255F*opacity);
            double x=p.previousX+(p.x-p.previousX)*partial-ox,y=p.previousY+(p.y-p.previousY)*partial-oy,z=p.previousZ+(p.z-p.previousZ)*partial-oz,r=p.radius;float u=p.material.getInterpolatedU(p.seed*12),v=p.material.getInterpolatedV((p.seed*29%1)*12),U=p.material.getInterpolatedU(p.seed*12+4),V=p.material.getInterpolatedV((p.seed*29%1)*12+4);
            corner(x,y,z,-r,-r,u,V);corner(x,y,z,r,-r,U,V);corner(x,y,z,r,r,U,v);corner(x,y,z,-r,r,u,v);
        }GL11.glEnd();
    }
    private static int sprite(){
        int size=64;ByteBuffer data=BufferUtils.createByteBuffer(size*size*4);
        for(int y=0;y<size;y++)for(int x=0;x<size;x++){
            double dx=(x+.5-size/2D)/(size/2D),dy=(y+.5-size/2D)/(size/2D),rr=dx*dx+dy*dy;
            int alpha=(int)Math.round(255*Math.pow(Math.max(0,1-rr),3));data.put((byte)255).put((byte)255).put((byte)255).put((byte)alpha);
        }
        data.flip();int texture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
        try{GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,1);GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH,0);GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS,0);GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS,0);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,size,size,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);
        }finally{GL11.glPopClientAttrib();}return texture;
    }
    @Override public void close(){if(texture!=0){GL11.glDeleteTextures(texture);texture=0;}}
}
