package dev.vibe.core.startup;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;
import org.lwjgl.opengl.*;

/** Resource-pack-independent artwork shared by the early splash and HUD. No Minecraft initialization. */
public final class GhostArt implements AutoCloseable {
    public static final String ROOT="/assets/vibe/branding/";
    public static final int FRAMES=15,COLUMNS=4,CELL=256;
    private int still,animated,program;
    private int tintedStill,tintedAnimated,tintedColor;
    private boolean hasTint;
    private boolean shaderFailed;
    public static BufferedImage image(String name)throws IOException{try(InputStream in=GhostArt.class.getResourceAsStream(ROOT+name)){if(in==null)throw new FileNotFoundException(name);BufferedImage result=ImageIO.read(in);if(result==null)throw new IOException("Invalid ghost image: "+name);return result;}}
    public static ByteBuffer rgba(BufferedImage image){ByteBuffer data=ByteBuffer.allocateDirect(image.getWidth()*image.getHeight()*4);for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int p=image.getRGB(x,y);data.put((byte)(p>>16)).put((byte)(p>>8)).put((byte)p).put((byte)(p>>>24));}data.flip();return data;}
    public static int upload(BufferedImage image){int id=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,image.getWidth(),image.getHeight(),0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,rgba(image));return id;}
    public static int frame(long millis,double fps){return (int)Math.floorMod((long)Math.floor(millis*Math.max(1,Math.min(30,fps))/1000),(long)FRAMES);}
    public void draw(float x,float y,float width,float height,boolean animate,boolean flipX,boolean flipY,boolean override,int color,float opacity,long millis,double fps){
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),oldProgram=GLContext.getCapabilities().OpenGL20?GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM):0;GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL13.glActiveTexture(GL13.GL_TEXTURE0);int oldTexture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try{
            for(int i=1;i<Math.min(3,GL11.glGetInteger(GL13.GL_MAX_TEXTURE_UNITS));i++){GL13.glActiveTexture(GL13.GL_TEXTURE0+i);GL11.glDisable(GL11.GL_TEXTURE_2D);}GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL11.GL_TEXTURE_ENV_MODE,GL11.GL_MODULATE);
            if(still==0)still=upload(image("ghost-static.png"));if(animate&&animated==0)animated=upload(image("ghost-animation.png"));GL11.glBindTexture(GL11.GL_TEXTURE_2D,animate?animated:still);
            if(GLContext.getCapabilities().OpenGL20){if(override&&!shaderFailed&&program==0)try{program=shader();}catch(RuntimeException e){shaderFailed=true;}GL20.glUseProgram(override&&program!=0?program:0);}
            if(override&&program==0)GL11.glBindTexture(GL11.GL_TEXTURE_2D,fallbackTint(animate,color));
            if(override&&program!=0){GL20.glUniform1i(GL20.glGetUniformLocation(program,"art"),0);GL20.glUniform3f(GL20.glGetUniformLocation(program,"tint"),(color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F);GL20.glUniform1f(GL20.glGetUniformLocation(program,"opacity"),opacity);}else GL11.glColor4f(1,1,1,opacity);
            int index=animate?frame(millis,fps):0;float u=animate?(index%COLUMNS)/4F:0,v=animate?(index/COLUMNS)/4F:0,span=animate?.25F:1;
            float u0=flipX?u+span:u,u1=flipX?u:u+span,v0=flipY?v+span:v,v1=flipY?v:v+span;
            GL11.glBegin(GL11.GL_QUADS);GL11.glTexCoord2f(u0,v0);GL11.glVertex2f(x,y);GL11.glTexCoord2f(u0,v1);GL11.glVertex2f(x,y+height);GL11.glTexCoord2f(u1,v1);GL11.glVertex2f(x+width,y+height);GL11.glTexCoord2f(u1,v0);GL11.glVertex2f(x+width,y);GL11.glEnd();
        }catch(IOException e){throw new IllegalStateException("Ghost artwork could not be loaded",e);}finally{if(GLContext.getCapabilities().OpenGL20)GL20.glUseProgram(oldProgram);GL11.glBindTexture(GL11.GL_TEXTURE_2D,oldTexture);GL13.glActiveTexture(active);GL11.glPopAttrib();}
    }
    private static int shader(){
        String vertex="#version 120\nvarying vec2 uv;void main(){gl_Position=ftransform();uv=gl_MultiTexCoord0.xy;}";
        String fragment="#version 120\nuniform sampler2D art;uniform vec3 tint;uniform float opacity;varying vec2 uv;void main(){vec4 c=texture2D(art,uv);float hi=max(c.r,max(c.g,c.b));float lo=min(c.r,min(c.g,c.b));float sat=(hi-lo)/max(hi,0.001);float light=dot(c.rgb,vec3(0.22,0.72,0.06));float mask=(1.0-smoothstep(0.17,0.32,sat))*smoothstep(0.08,0.30,light);vec3 shade=tint*(0.18+0.82*light);shade=mix(shade,vec3(1.0),smoothstep(0.88,1.0,light)*0.12);gl_FragColor=vec4(mix(c.rgb,shade,mask),c.a*opacity);}";
        int v=compile(GL20.GL_VERTEX_SHADER,vertex),f=0,p=0;try{f=compile(GL20.GL_FRAGMENT_SHADER,fragment);p=GL20.glCreateProgram();GL20.glAttachShader(p,v);GL20.glAttachShader(p,f);GL20.glLinkProgram(p);if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(p,2048));return p;}catch(RuntimeException e){if(p!=0)GL20.glDeleteProgram(p);throw e;}finally{GL20.glDeleteShader(v);if(f!=0)GL20.glDeleteShader(f);}
    }
    private static int compile(int kind,String source){int s=GL20.glCreateShader(kind);GL20.glShaderSource(s,source);GL20.glCompileShader(s);if(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)==0){String error=GL20.glGetShaderInfoLog(s,2048);GL20.glDeleteShader(s);throw new IllegalStateException(error);}return s;}
    private int fallbackTint(boolean animation,int color)throws IOException{
        if(!hasTint||tintedColor!=color){if(tintedStill!=0)GL11.glDeleteTextures(tintedStill);if(tintedAnimated!=0)GL11.glDeleteTextures(tintedAnimated);tintedStill=tintedAnimated=0;tintedColor=color;hasTint=true;}
        if(animation){if(tintedAnimated==0)tintedAnimated=upload(recolor(image("ghost-animation.png"),color));return tintedAnimated;}
        if(tintedStill==0)tintedStill=upload(recolor(image("ghost-static.png"),color));return tintedStill;
    }
    /** Same selective tint as the shader, cached once per color on legacy GL drivers. */
    public static BufferedImage recolor(BufferedImage source,int color){
        BufferedImage result=new BufferedImage(source.getWidth(),source.getHeight(),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<source.getHeight();y++)for(int x=0;x<source.getWidth();x++){
            int pixel=source.getRGB(x,y);float r=(pixel>>16&255)/255F,g=(pixel>>8&255)/255F,b=(pixel&255)/255F;
            float hi=Math.max(r,Math.max(g,b)),lo=Math.min(r,Math.min(g,b)),light=.22F*r+.72F*g+.06F*b;
            float mask=(1-smooth(.17F,.32F,(hi-lo)/Math.max(hi,.001F)))*smooth(.08F,.30F,light),highlight=smooth(.88F,1,light)*.12F;
            int rgb=0;float[] original={r,g,b};
            for(int c=0;c<3;c++){int shift=16-c*8;float shade=((color>>shift&255)/255F)*(.18F+.82F*light);shade=shade*(1-highlight)+highlight;rgb|=Math.round(255*(original[c]*(1-mask)+shade*mask))<<shift;}
            result.setRGB(x,y,(pixel&0xFF000000)|rgb);
        }
        return result;
    }
    private static float smooth(float low,float high,float value){float t=Math.max(0,Math.min(1,(value-low)/(high-low)));return t*t*(3-2*t);}
    @Override public void close(){if(still!=0)GL11.glDeleteTextures(still);if(animated!=0)GL11.glDeleteTextures(animated);if(tintedStill!=0)GL11.glDeleteTextures(tintedStill);if(tintedAnimated!=0)GL11.glDeleteTextures(tintedAnimated);if(program!=0)GL20.glDeleteProgram(program);still=animated=program=tintedStill=tintedAnimated=0;}
}
