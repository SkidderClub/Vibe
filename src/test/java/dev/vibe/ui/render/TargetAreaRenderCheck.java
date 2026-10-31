package dev.vibe.ui.render;

import dev.vibe.module.impl.combat.TargetsModule;
import dev.vibe.path.PathTestFixture;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.*;
import java.util.Arrays;
import javax.imageio.ImageIO;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.BlockPos;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Real driver check: cuboid pixels, settings, inverted color, camera origin and state restoration. */
public final class TargetAreaRenderCheck {
    private static final int SIZE=512;
    public static void main(String[] args) throws Exception {
        Path output=Paths.get(args[0]);Files.createDirectories(output);
        Pbuffer buffer=new Pbuffer(SIZE,SIZE,new PixelFormat(8,24,8),null,null);buffer.makeCurrent();
        try(PathTestFixture fixture=new PathTestFixture()) {
            OpenGlHelper.initializeTextures();TargetsModule module=fixture.filters;TargetAreaRenderer renderer=new TargetAreaRenderer();
            module.setCorner(1,new BlockPos(1,66,1));module.setCorner(2,new BlockPos(-2,64,-2));
            module.targetArea.setEnabled(true);frame();byte[] blank=pixels();renderer.draw(module,0,0,0);
            require(Arrays.equals(blank,pixels()),"Display-off setting drew a cuboid");
            module.displayTargetArea.setEnabled(true);renderer.draw(module,0,0,0);byte[] normal=pixels();
            require(!Arrays.equals(blank,normal),"Enabled selection did not draw");
            require(total(normal,2)>total(normal,0)*1.5,"Normal area should have a blue highlight");
            save(normal,output.resolve("target-area.png"));
            frame();GL11.glTranslated(20,7,-13);renderer.draw(module,20,7,-13);
            require(Arrays.equals(normal,pixels()),"Camera-relative origin displaced the cuboid");
            module.invertArea.setEnabled(true);frame();renderer.draw(module,0,0,0);byte[] inverted=pixels();
            require(total(inverted,0)>total(inverted,2)*1.5,"Inverted area should have an amber highlight");
            save(inverted,output.resolve("target-area-inverted.png"));
            module.targetArea.setEnabled(false);frame();renderer.draw(module,0,0,0);
            require(Arrays.equals(blank,pixels()),"Disabled target area remained visible");module.targetArea.setEnabled(true);
            frame();GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_GEQUAL);GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_LIGHTING);GL11.glEnable(GL11.GL_FOG);GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_BLEND);GL11.glLineWidth(3);GL11.glColor4f(.2F,.3F,.4F,.5F);
            GL13.glActiveTexture(GL13.GL_TEXTURE2);GL11.glEnable(GL11.GL_TEXTURE_2D);
            FloatBuffer matrix=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,matrix);float[] before=new float[16];matrix.get(before);
            renderer.draw(module,0,0,0);
            require(GL11.glIsEnabled(GL11.GL_DEPTH_TEST)&&GL11.glIsEnabled(GL11.GL_LIGHTING)&&GL11.glIsEnabled(GL11.GL_FOG)
                    &&GL11.glIsEnabled(GL11.GL_CULL_FACE)&&!GL11.glIsEnabled(GL11.GL_BLEND)&&GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
                    &&GL11.glGetInteger(GL11.GL_DEPTH_FUNC)==GL11.GL_GEQUAL&&GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)==GL13.GL_TEXTURE2
                    &&GL11.glIsEnabled(GL11.GL_TEXTURE_2D)&&Math.abs(GL11.glGetFloat(GL11.GL_LINE_WIDTH)-3)<.01,"Cuboid leaked GL state");
            matrix.clear();GL11.glGetFloat(GL11.GL_CURRENT_COLOR,matrix);require(Math.abs(matrix.get(0)-.2F)<.001&&Math.abs(matrix.get(3)-.5F)<.001,"Cuboid leaked color");
            matrix.clear();GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,matrix);float[] after=new float[16];matrix.get(after);
            require(Arrays.equals(before,after),"Cuboid changed the world camera matrix");
            int reads=fixture.world.reads;for(int i=0;i<100;i++)renderer.draw(module,0,0,0);
            require(reads==fixture.world.reads,"Cuboid renderer queried world blocks");
            require(GL11.glGetError()==GL11.GL_NO_ERROR,"Target area produced an OpenGL error");
            System.out.println("Target area GL checks passed: normal/inverted cuboid pixels, settings, camera origin, render-state restoration and no terrain queries.");
        } finally {buffer.destroy();}
    }
    private static void frame() {
        GL20.glUseProgram(0);GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glViewport(0,0,SIZE,SIZE);
        GL11.glColorMask(true,true,true,true);GL11.glDepthMask(true);GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glClearColor(0,0,0,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(-4,4,-4,4,-100,100);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();GL11.glRotatef(25,1,0,0);GL11.glRotatef(35,0,1,0);GL11.glTranslated(0,-65,0);
    }
    private static byte[] pixels() {
        ByteBuffer buffer=BufferUtils.createByteBuffer(SIZE*SIZE*4);GL11.glReadPixels(0,0,SIZE,SIZE,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,buffer);
        byte[] result=new byte[buffer.capacity()];buffer.get(result);return result;
    }
    private static long total(byte[] pixels,int channel){long total=0;for(int i=channel;i<pixels.length;i+=4)total+=pixels[i]&255;return total;}
    private static void save(byte[] pixels,Path file) throws Exception {
        BufferedImage image=new BufferedImage(SIZE,SIZE,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){int i=(y*SIZE+x)*4;image.setRGB(x,SIZE-1-y,((pixels[i]&255)<<16)|((pixels[i+1]&255)<<8)|(pixels[i+2]&255));}
        ImageIO.write(image,"png",file.toFile());
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
