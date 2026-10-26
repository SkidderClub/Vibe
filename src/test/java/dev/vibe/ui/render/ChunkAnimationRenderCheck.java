package dev.vibe.ui.render;

import dev.vibe.core.ChunkAnimationTransformer;
import dev.vibe.module.impl.visual.ChunkAnimationModule;
import dev.vibe.path.PathTestFixture;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.lang.reflect.Method;
import java.util.Arrays;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.chunk.*;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Real transformed vanilla display-list and VBO containers drawing a cached terrain mesh. */
public final class ChunkAnimationRenderCheck {
    private static final int W=320,H=240;
    public static void main(String[] args)throws Exception{
        Path output=Paths.get(args[0]);Files.createDirectories(output);
        Pbuffer context=new Pbuffer(W,H,new PixelFormat(8,24,8),null,null);context.makeCurrent();
        try{
            Minecraft mc=PathTestFixture.allocate(Minecraft.class);PathTestFixture.set(Minecraft.class,null,"theMinecraft",mc);mc.gameSettings=new GameSettings();OpenGlHelper.initializeTextures();
            ChunkAnimationModule module=new ChunkAnimationModule();
            PreviewChunk chunk=PathTestFixture.allocate(PreviewChunk.class);chunk.geometry=new Geometry();chunk.buffer=new CountedBuffer();
            ByteBuffer vertices=BufferUtils.createByteBuffer(4*28).order(ByteOrder.nativeOrder());
            for(float[] vertex:new float[][]{{2,2},{14,2},{14,14},{2,14}}){vertices.putFloat(vertex[0]).putFloat(vertex[1]).putFloat(0).putInt(0xFF77EE99).putFloat(0).putFloat(0).putShort((short)0).putShort((short)0);}
            vertices.flip();chunk.buffer.bufferData(vertices);
            chunk.list=GL11.glGenLists(1);GL11.glNewList(chunk.list,GL11.GL_COMPILE);GL11.glColor4f(153/255F,238/255F,119/255F,1);GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex3f(2,2,0);GL11.glVertex3f(14,2,0);GL11.glVertex3f(14,14,0);GL11.glVertex3f(2,14,0);GL11.glEnd();GL11.glEndList();
            ClassLoader loader=new TerrainLoader();
            try{
                for(String backend:new String[]{"RenderList","VboRenderList"}){
                    Object renderer=Class.forName("net.minecraft.client.renderer."+backend,true,loader).newInstance();
                    Method initialize=renderer.getClass().getMethod("initialize",double.class,double.class,double.class);
                    Method add=renderer.getClass().getMethod("addRenderChunk",RenderChunk.class,EnumWorldBlockLayer.class);
                    Method render=renderer.getClass().getMethod("renderChunkLayer",EnumWorldBlockLayer.class);
                    for(String animation:module.animation.getModes()){
                        module.animation.setValue(animation);ChunkAnimationHooks.bind(module);
                        byte[] settled=null;int firstCount=0,lastCount=0;double firstY=0,lastY=0;
                        for(int step=0;step<3;step++){
                            ChunkAnimationHooks.beginFrame(1000000000L+step*300000000L,true,module);chunk.start=1000000000L;
                            frame(backend.equals("VboRenderList"));chunk.draws=0;chunk.buffer.draws=0;
                            FloatBuffer before=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,before);
                            initialize.invoke(renderer,0D,0D,0D);add.invoke(renderer,chunk,EnumWorldBlockLayer.SOLID);render.invoke(renderer,EnumWorldBlockLayer.SOLID);
                            if(chunk.draws+chunk.buffer.draws!=1)throw new AssertionError("Animation added terrain draws");
                            FloatBuffer after=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,after);
                            for(int i=0;i<16;i++)if(before.get(i)!=after.get(i))throw new AssertionError("Chunk animation leaked its matrix");
                            byte[] pixels=pixels();int count=0;double centroid=0;
                            for(int y=0;y<H;y++)for(int x=0;x<W;x++)if((pixels[(y*W+x)*4]&255)>50){count++;centroid+=y;}
                            if(step==0){firstCount=count;firstY=count==0?0:centroid/count;}
                            if(step==2){lastCount=count;lastY=centroid/count;settled=pixels;if(chunk.start!=-1)throw new AssertionError("Finished chunk retained active state");}
                            if(step==1)image(output.resolve(backend+"-"+animation.toLowerCase()+".png"),pixels);
                        }
                        if(lastCount==0)throw new AssertionError("Terrain disappeared");
                        if(animation.equals("Rise")&&lastY-firstY<40)throw new AssertionError("Rise did not move terrain");
                        if(animation.equals("Drop")&&firstY-lastY<40)throw new AssertionError("Drop did not move terrain");
                        if(animation.equals("Grow")&&firstCount>=lastCount)throw new AssertionError("Grow did not change terrain size");
                        frame(backend.equals("VboRenderList"));chunk.start=-1;initialize.invoke(renderer,0D,0D,0D);add.invoke(renderer,chunk,EnumWorldBlockLayer.SOLID);render.invoke(renderer,EnumWorldBlockLayer.SOLID);
                        if(!Arrays.equals(settled,pixels()))throw new AssertionError("Finished animation differs from vanilla terrain");
                    }
                }
            }finally{chunk.buffer.deleteGlBuffers();GL11.glDeleteLists(chunk.list,1);ChunkAnimationHooks.bind(null);}
            if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new AssertionError("Chunk animation GL error");
            System.out.println("Chunk animations: real transformed VBO/display-list draws, four modes, one draw per mesh, exact settled pixels and matrix restoration passed.");
        }finally{context.destroy();}
    }
    private static void frame(boolean vbo){
        GL11.glViewport(0,0,W,H);GL20.glUseProgram(0);GlStateManager.disableTexture2D();GlStateManager.disableLighting();GlStateManager.disableFog();GlStateManager.disableBlend();GlStateManager.disableDepth();GlStateManager.disableAlpha();
        GL11.glDisable(GL11.GL_CULL_FACE);GL11.glClearColor(0,0,0,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(-16,48,-24,40,-100,100);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();
        if(vbo){GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);}
        else{GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);}
    }
    private static byte[] pixels(){ByteBuffer buffer=BufferUtils.createByteBuffer(W*H*4);GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,buffer);byte[] data=new byte[buffer.capacity()];buffer.get(data);return data;}
    private static void image(Path file,byte[] data)throws IOException{BufferedImage image=new BufferedImage(W,H,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=((H-1-y)*W+x)*4;image.setRGB(x,y,0xFF000000|(data[i]&255)<<16|(data[i+1]&255)<<8|(data[i+2]&255));}ImageIO.write(image,"png",file.toFile());}
    private static final class TerrainLoader extends ClassLoader{
        TerrainLoader(){super(ChunkAnimationRenderCheck.class.getClassLoader());}
        @Override protected synchronized Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
            if(!name.equals("net.minecraft.client.renderer.ChunkRenderContainer")&&!name.equals("net.minecraft.client.renderer.RenderList")&&!name.equals("net.minecraft.client.renderer.VboRenderList"))return super.loadClass(name,resolve);
            Class<?> found=findLoadedClass(name);if(found==null)try(InputStream input=getParent().getResourceAsStream(name.replace('.','/')+".class");ByteArrayOutputStream output=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int length;while((length=input.read(buffer))!=-1)output.write(buffer,0,length);
                byte[] bytes=new ChunkAnimationTransformer().transform(name,name,output.toByteArray());found=defineClass(name,bytes,0,bytes.length);
            }catch(IOException error){throw new ClassNotFoundException(name,error);}if(resolve)resolveClass(found);return found;
        }
    }
    public static final class PreviewChunk extends ListedRenderChunk implements ChunkAnimationState{
        long start;int list,draws;CountedBuffer buffer;CompiledChunk geometry;
        private PreviewChunk(){super(null,null,BlockPos.ORIGIN,0);}
        public long vibe$getAnimationStart(){return start;}public void vibe$setAnimationStart(long value){start=value;}
        @Override public BlockPos getPosition(){return BlockPos.ORIGIN;}
        @Override public CompiledChunk getCompiledChunk(){return geometry;}
        @Override public int getDisplayList(EnumWorldBlockLayer layer,CompiledChunk compiled){draws++;return list;}
        @Override public VertexBuffer getVertexBufferByLayer(int layer){return buffer;}
        @Override public void multModelviewMatrix(){ }
    }
    private static final class Geometry extends CompiledChunk{Geometry(){setLayerUsed(EnumWorldBlockLayer.SOLID);}}
    private static final class CountedBuffer extends VertexBuffer{
        int draws;CountedBuffer(){super(DefaultVertexFormats.BLOCK);}
        @Override public void drawArrays(int mode){draws++;super.drawArrays(mode);}
    }
}
