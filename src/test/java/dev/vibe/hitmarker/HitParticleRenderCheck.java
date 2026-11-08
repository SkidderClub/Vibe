package dev.vibe.hitmarker;

import dev.vibe.path.PathTestFixture;
import dev.vibe.ui.render.HitParticleRenderer;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.*;
import java.util.Random;
import javax.imageio.ImageIO;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Offscreen GPU checks for additive glow, depth, interpolation, roll and exact driver state. */
public final class HitParticleRenderCheck {
    private static final int W=800,H=600;
    public static void main(String[] args)throws Exception{
        Path output=Paths.get(args[0]);Files.createDirectories(output);
        Pbuffer buffer=new Pbuffer(W,H,new PixelFormat(8,24,8),null,null);buffer.makeCurrent();
        try(PathTestFixture f=HitParticlesTest.fixture();HitParticleRenderer renderer=new HitParticleRenderer()){
            OpenGlHelper.initializeTextures();HitParticleSettings s=new HitParticleSettings(setting->setting,()->true);
            s.count.setValue(24D);s.delay.setValue(0D);s.lifetime.setValue(3000D);s.size.setValue(.065D);s.speed.setValue(1.5D);
            HitParticles particles=new HitParticles(new Random(5));particles.emit(f.world,s,new Vec3(.5,65.5,.5),new Vec3(0,0,1),1000);
            for(int i=0;i<8;i++)particles.tick(f.world,s);
            scene(1);s.glow.setEnabled(false);renderer.render(particles.particles(),s,.5,64.7,.5,.5F);int plain=brightness();save(output.resolve("particle-cores.png"));
            scene(1);s.glow.setEnabled(true);renderer.render(particles.particles(),s,.5,64.7,.5,.5F);int glow=brightness();save(output.resolve("particle-glow.png"));
            check(plain>1000&&glow>plain,"Glow is absent or particles were not rendered");
            scene(0);renderer.render(particles.particles(),s,.5,64.7,.5,.5F);check(brightness()==0,"Particles ignored opaque world depth");
            s.throughWalls.setEnabled(true);renderer.render(particles.particles(),s,.5,64.7,.5,.5F);check(brightness()>1000,"Through Walls did not render");s.throughWalls.setEnabled(false);
            scene(1);GL11.glRotatef(32,0,0,1);GL11.glRotatef(25,0,1,0);renderer.render(particles.particles(),s,.5,64.7,.5,.5F);save(output.resolve("particle-camera-roll.png"));
            for(int i=0;i<20;i++)particles.tick(f.world,s);scene(1);renderer.render(particles.particles(),s,.5,64.2,.5,1);save(output.resolve("particles-settled.png"));
            HitParticles.Particle p=particles.particles().get(0);for(HitParticles.Particle particle:particles.particles())particle.age=(int)(particle.lifetime*.95);
            scene(1);renderer.render(particles.particles(),s,.5,64.2,.5,1);int faded=brightness();s.fade.setEnabled(false);scene(1);renderer.render(particles.particles(),s,.5,64.2,.5,1);check(faded<brightness()/2,"End-of-life fade did not reduce light");
            scene(1);GL13.glActiveTexture(GL13.GL_TEXTURE1);GL11.glEnable(GL11.GL_TEXTURE_2D);GL13.glActiveTexture(GL13.GL_TEXTURE2);
            GL11.glEnable(GL11.GL_ALPHA_TEST);GL11.glAlphaFunc(GL11.GL_GREATER,.4F);GL11.glDepthMask(true);GL11.glDisable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_ONE,GL11.GL_ZERO);
            GL11.glColor4f(.2F,.3F,.4F,.5F);GL11.glMatrixMode(GL11.GL_PROJECTION);
            renderer.render(particles.particles(),s,.5,64.2,.5,.3F);
            check(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)==GL13.GL_TEXTURE2,"Active texture leaked");
            check(GL11.glGetInteger(GL11.GL_MATRIX_MODE)==GL11.GL_PROJECTION,"Matrix mode leaked");
            check(GL11.glIsEnabled(GL11.GL_ALPHA_TEST)&&Math.abs(GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF)-.4F)<.001,"Alpha state leaked");
            check(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)&&!GL11.glIsEnabled(GL11.GL_BLEND),"Depth/blend state leaked");
            FloatBuffer color=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_CURRENT_COLOR,color);check(Math.abs(color.get(1)-.3F)<.001,"Color leaked");
            s.limit.setValue(512D);s.count.setValue(40D);particles.clear();for(int i=0;i<14;i++)particles.emit(f.world,s,new Vec3(.5,65.5,.5),new Vec3(0,0,1),2000+i);
            check(particles.particles().size()==512,"Burst cap failed");
            scene(1);long start=System.nanoTime();for(int i=0;i<120;i++)renderer.render(particles.particles(),s,.5,64.7,.5,.5F);GL11.glFinish();
            System.out.printf(java.util.Locale.ROOT,"HitMarker Particle: 512 particles, %.2f ms/warm frame on %s%n",(System.nanoTime()-start)/120e6,GL11.glGetString(GL11.GL_RENDERER));
            check(GL11.glGetError()==0,"Particle rendering left a GL error");
            System.out.println("HitMarker particle GPU checks passed: glow, depth, fade, roll, settling, bounded bursts and driver state.");
        }finally{buffer.destroy();}
    }
    private static void scene(double depth){GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glViewport(0,0,W,H);GL11.glDepthMask(true);GL11.glClearColor(0,0,0,1);GL11.glClearDepth(depth);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(-2,2,-.6,2.4,-8,8);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();}
    private static ByteBuffer pixels(){ByteBuffer b=BufferUtils.createByteBuffer(W*H*4);GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);return b;}
    private static int brightness(){ByteBuffer b=pixels();int total=0;for(int i=0;i<b.limit();i+=4)total+=(b.get(i)&255)+(b.get(i+1)&255)+(b.get(i+2)&255);return total;}
    private static void save(Path file)throws Exception{ByteBuffer p=pixels();BufferedImage image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=((H-1-y)*W+x)*4;image.setRGB(x,y,(p.get(i)&255)<<16|(p.get(i+1)&255)<<8|p.get(i+2)&255);}ImageIO.write(image,"png",file.toFile());}
    private static void check(boolean test,String message){if(!test)throw new AssertionError(message);}
}
