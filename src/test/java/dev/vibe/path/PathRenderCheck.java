package dev.vibe.path;

import dev.vibe.module.impl.visual.PathModule;
import dev.vibe.ui.effect.EffectProgram;
import dev.vibe.ui.render.PathRenderer;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.*;
import java.util.Arrays;
import javax.imageio.ImageIO;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.init.Blocks;
import net.minecraft.util.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.util.glu.GLU;

/** Actual LWJGL driver checks for cached Path drawing, depth modes, color, styles and state safety. */
public final class PathRenderCheck {
    private static final int W = 960, H = 600;
    private static final Vec3 PLAYER = new Vec3(.5,64,.5), TARGET = new Vec3(6.5,64,.5), ZERO = new Vec3(0,0,0);
    private static PathTestFixture fixture;
    private static PathRenderer renderer;
    private static PathRoute route;
    private static Path output;

    public static void main(String[] args) throws Exception {
        output = Paths.get(args[0]); Files.createDirectories(output);
        Pbuffer buffer = new Pbuffer(W, H, new PixelFormat(8,24,8), null, null); buffer.makeCurrent();
        try (PathTestFixture state = new PathTestFixture()) {
            fixture = state; OpenGlHelper.initializeTextures(); renderer = new PathRenderer();
            fixture.addPlayer(2, "Target", TARGET.xCoord, TARGET.yCoord, TARGET.zCoord);
            for (int z = -2; z <= 2; z++) for (int y = 64; y <= 66; y++) fixture.block(3,y,z,Blocks.stone.getDefaultState());
            fixture.module.setEnabled(true); fixture.finishRoute(); route = fixture.module.getRoute();
            if (!route.complete) throw new AssertionError("Fixture did not find a real wall detour");
            colorsAndDepth(); stateRestoration(); cameraOrigin();
            fixture.module.getColor().setValue(0xFF44E88C); fixture.module.getGradient().setEnabled(false);
            fixture.module.getThroughWalls().setEnabled(true); fixture.module.getLineWidth().setValue(2D);
            scene("path-solid.png");
            fixture.module.getGradient().setEnabled(true); fixture.module.getColor().setValue(0xFF55FF88);
            fixture.module.getEndColor().setValue(0xFF42B5FF); fixture.module.getEndpointStyle().setValue("Box");
            scene("path-gradient.png");
            fixture.module.getLineStyle().setValue("Dashed"); scene("path-dashed.png");
            performance();
            if (GL11.glGetError() != GL11.GL_NO_ERROR) throw new AssertionError("Path rendering produced a GL error");
            System.out.println("Path GL checks passed: real wall detour, solid/dashed/gradient pixels, depth modes, alpha, state, camera origin and cached rendering.");
        } finally { buffer.destroy(); }
    }

    private static void topDown(double depth) {
        GL20.glUseProgram(0); GL13.glActiveTexture(GL13.GL_TEXTURE0);
        for (int unit=0;unit<3;unit++) { GL13.glActiveTexture(GL13.GL_TEXTURE0+unit); GL11.glDisable(GL11.GL_TEXTURE_2D); }
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glViewport(0,0,W,H); GL11.glColorMask(true,true,true,true); GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glClearColor(0,0,0,1); GL11.glClearDepth(depth); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(-2,10,-5,5,-100,100);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity(); GL11.glRotatef(90,1,0,0); GL11.glTranslated(0,-64,0);
    }

    private static void draw() { renderer.draw(route,fixture.module,PLAYER,TARGET,ZERO,0,false); }

    private static void colorsAndDepth() {
        PathModule module = fixture.module; module.getColor().setValue(0xFF00FF00); module.getEndpoint().setEnabled(false);
        topDown(.1); module.getThroughWalls().setEnabled(false); draw();
        if (colored(1) != 0) throw new AssertionError("Depth-tested path ignored occlusion");
        topDown(.1); module.getThroughWalls().setEnabled(true); draw();
        int solid = colored(1); if (solid < 200) throw new AssertionError("Through Walls path did not render");
        FloatBuffer depth = BufferUtils.createFloatBuffer(1);
        GL11.glReadPixels(W/2,H/2,1,1,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depth);
        if (Math.abs(depth.get(0)-.1) > .0001) throw new AssertionError("Path wrote into world depth");
        module.getLineStyle().setValue("Dashed"); topDown(1); draw();
        int dashed = colored(1); if (dashed < 50 || dashed >= solid * .85) throw new AssertionError("Dashed line has no gaps");
        module.getLineStyle().setValue("Solid"); module.getColor().setValue(0xFFFF0000);
        module.getGradient().setEnabled(true); module.getEndColor().setValue(0xFF0000FF); topDown(1); draw();
        if (colored(0) < 20 || colored(2) < 20) throw new AssertionError("Gradient endpoints were not colored independently");
        module.getGradient().setEnabled(false); module.getColor().setValue(0x0000FF00); topDown(1); draw();
        if (colored(1) != 0) throw new AssertionError("Zero-alpha line remained visible");
        module.getColor().setValue(0xFF00FF00); module.getEndpoint().setEnabled(true);
        PathRoute partial = PathRoute.build(new net.minecraft.util.BlockPos(0,64,0),
                java.util.Arrays.asList(new net.minecraft.util.BlockPos(1,64,0),new net.minecraft.util.BlockPos(2,64,0)),
                new PathTerrain(fixture.world,fixture.player),false);
        topDown(1); module.getPartial().setEnabled(false);
        renderer.draw(partial,module,PLAYER,TARGET,ZERO,0,false);
        if (colored(0)+colored(1)+colored(2)!=0) throw new AssertionError("Hidden partial route was drawn");
        topDown(1); module.getPartial().setEnabled(true);
        renderer.draw(partial,module,PLAYER,TARGET,ZERO,0,false);
        if (colored(0)<50) throw new AssertionError("Partial route lost its amber status color");
    }

    private static void stateRestoration() throws Exception {
        topDown(1);
        try (EffectProgram program = new EffectProgram("Chams.vert","Chams.frag")) {
            program.bind(); int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthFunc(GL11.GL_GEQUAL); GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_FOG); GL11.glEnable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_BLEND);
            GL11.glLineWidth(4); GL13.glActiveTexture(GL13.GL_TEXTURE2); GL11.glEnable(GL11.GL_TEXTURE_2D);
            float[] before = matrix(); draw();
            if (!GL11.glIsEnabled(GL11.GL_DEPTH_TEST) || !GL11.glIsEnabled(GL11.GL_FOG)
                    || !GL11.glIsEnabled(GL11.GL_LIGHTING) || GL11.glIsEnabled(GL11.GL_BLEND)
                    || !GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) || GL11.glGetInteger(GL11.GL_DEPTH_FUNC)!=GL11.GL_GEQUAL
                    || GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=previousProgram
                    || GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)!=GL13.GL_TEXTURE2
                    || !GL11.glIsEnabled(GL11.GL_TEXTURE_2D) || Math.abs(GL11.glGetFloat(GL11.GL_LINE_WIDTH)-4)>.01)
                throw new AssertionError("Path leaked render state");
            if (!Arrays.equals(before,matrix())) throw new AssertionError("Path changed the camera matrix");
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }

    private static void cameraOrigin() {
        topDown(1); draw(); byte[] first = pixels();
        topDown(1); GL11.glTranslated(20,7,-13);
        renderer.draw(route,fixture.module,PLAYER,TARGET,new Vec3(20,7,-13),0,false);
        if (!Arrays.equals(first,pixels())) throw new AssertionError("Player-relative Freecam origin displaced the path");
    }

    private static void scene(String name) throws Exception {
        topDown(1);
        GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_BLEND); GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthFunc(GL11.GL_LESS);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GLU.gluPerspective(65,(float)W/H,.05F,100);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity(); GLU.gluLookAt(-3,69,-8,3,64,1,0,1,0);
        GL11.glClearColor(.10F,.14F,.18F,1); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        for (int x=-4;x<=10;x++) for (int z=-6;z<=6;z++) {
            float shade = ((x+z)&1)==0 ? .27F : .32F; GL11.glColor3f(shade,shade+.04F,shade+.06F);
            GL11.glBegin(GL11.GL_QUADS); GL11.glVertex3d(x,63.995,z);GL11.glVertex3d(x+1,63.995,z);
            GL11.glVertex3d(x+1,63.995,z+1);GL11.glVertex3d(x,63.995,z+1);GL11.glEnd();
        }
        box(3,64,-2,4,67,3,.5F,.53F,.58F);
        box(TARGET.xCoord-.3,64,TARGET.zCoord-.3,TARGET.xCoord+.3,65.8,TARGET.zCoord+.3,.85F,.58F,.25F);
        draw(); save(name);
    }

    private static void box(double x,double y,double z,double a,double b,double c,float r,float g,float blue) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor3f(r,g,blue); quad(x,y,z,a,y,z,a,b,z,x,b,z); quad(x,y,c,x,b,c,a,b,c,a,y,c);
        GL11.glColor3f(r*.8F,g*.8F,blue*.8F); quad(x,y,z,x,b,z,x,b,c,x,y,c);quad(a,y,z,a,y,c,a,b,c,a,b,z);
        GL11.glColor3f(r*1.1F,g*1.1F,blue*1.1F);quad(x,b,z,a,b,z,a,b,c,x,b,c);GL11.glEnd();
    }
    private static void quad(double... p) { for (int i=0;i<p.length;i+=3)GL11.glVertex3d(p[i],p[i+1],p[i+2]); }

    private static void performance() throws Exception {
        fixture.module.getLineStyle().setValue("Solid"); topDown(1);
        for (int i=0;i<30;i++) draw();
        long[] times = new long[200]; int reads = fixture.world.reads;
        for (int i=0;i<times.length;i++) { long start=System.nanoTime(); draw(); times[i]=System.nanoTime()-start; }
        if (fixture.world.reads!=reads) throw new AssertionError("Path renderer queried terrain");
        Arrays.sort(times);
        String report = String.format(java.util.Locale.ROOT,"Cached solid path draw CPU median %.3f ms, p95 %.3f ms; %d simplified vertices; zero world reads.%n",
                times[times.length/2]/1e6,times[(int)(times.length*.95)]/1e6,route.points.size());
        Files.write(output.resolve("timings.txt"),report.getBytes(java.nio.charset.StandardCharsets.UTF_8)); System.out.print(report);
    }
    private static int colored(int channel) {
        byte[] data=pixels(); int count=0;
        for(int i=0;i<data.length;i+=4) if((data[i+channel]&255)>70) count++;
        return count;
    }
    private static byte[] pixels() {
        ByteBuffer data=BufferUtils.createByteBuffer(W*H*4); GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);
        byte[] result=new byte[data.capacity()];data.get(result);return result;
    }
    private static float[] matrix() {
        FloatBuffer values=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,values);float[] result=new float[16];values.get(result);return result;
    }
    private static void save(String name) throws Exception {
        byte[] data=pixels();BufferedImage image=new BufferedImage(W,H,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=(y*W+x)*4;image.setRGB(x,H-y-1,0xFF000000|(data[i]&255)<<16|(data[i+1]&255)<<8|data[i+2]&255);}
        ImageIO.write(image,"png",output.resolve(name).toFile());
    }
}
