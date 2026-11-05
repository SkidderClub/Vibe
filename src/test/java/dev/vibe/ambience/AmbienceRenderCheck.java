package dev.vibe.ambience;

import dev.vibe.Vibe;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.visual.AmbienceModule;
import dev.vibe.path.PathTestFixture;
import dev.vibe.ui.ClientChangesRenderCheck;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.*;
import net.minecraft.world.WorldProviderSurface;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.util.glu.GLU;

/** Real shader, depth, animation and vanilla sky-buffer checks without opening a game window. */
public final class AmbienceRenderCheck {
    private static final int W=1280,H=720;
    public static void verify(Minecraft mc,ModuleManager modules,Path output)throws Exception{
        mc.displayWidth=W;mc.displayHeight=H;mc.gameSettings.guiScale=1;
        PathTestFixture.set(Vibe.class,Vibe.getInstance(),"minecraft",mc);
        AmbienceModule m=new AmbienceModule();modules.registerDynamic(m);m.setEnabled(true);
        SkyWorld world=PathTestFixture.allocate(SkyWorld.class);PathTestFixture.set(net.minecraft.world.World.class,world,"provider",new WorldProviderSurface());mc.theWorld=world;
        try(AmbienceRenderer renderer=new AmbienceRenderer()){
            frame(false);perspective();GL11.glRotatef(-90,1,0,0);int[] before=read();
            renderer.celestial(m.sun,false,0,2,1);int[] sun=read();check(difference(before,sun)>2000,"Custom sun shader did not draw");save(output,"ambience-sun.png",sun);
            frame(true);perspective();GL11.glRotatef(90,1,0,0);renderer.celestial(m.moon,true,0,2,1);int[] full=read();save(output,"ambience-moon-full.png",full);
            frame(true);perspective();GL11.glRotatef(90,1,0,0);renderer.celestial(m.moon,true,3,2,1);int[] crescent=read();save(output,"ambience-moon-crescent.png",crescent);
            check(difference(full,crescent)>1000,"Moon phases do not change its illuminated surface");
            frame(true);perspective();GL11.glRotatef(90,1,0,0);before=read();renderer.celestial(m.moon,true,0,2,0);check(Arrays.equals(before,read()),"Rain-faded moon remained visible");
            m.aurora.direction.setValue(180D);
            frame(true);perspective();GL11.glRotatef(-20,1,0,0);before=read();renderer.aurora(m.aurora,2,1);int[] aurora=read();
            check(difference(before,aurora)>5000,"Polar curtains are empty");ground();save(output,"ambience-polar-lights.png",read());
            frame(true);perspective();GL11.glRotatef(-20,1,0,0);renderer.aurora(m.aurora,20,1);check(difference(aurora,read())>2000,"Aurora curtains did not drift");
            AmbienceScene.Meteor star=null;Random starRandom=new Random(71);for(int i=0;i<100;i++){AmbienceScene.Meteor candidate=AmbienceScene.Meteor.create(0,starRandom,1);if(candidate.position(candidate.duration*.5).zCoord<-.6){star=candidate;break;}}
            check(star!=null,"No preview star");double time=star.duration*.5;
            frame(true);perspective();GL11.glRotatef(-20,1,0,0);before=read();renderer.meteors(Collections.singletonList(star),m.stars,time,1);check(difference(before,read())>60,"Shooting star tail is empty");save(output,"ambience-shooting-star.png",read());
            FireflySwarm swarm=new FireflySwarm();m.fireflies.count.setValue(12D);
            FireflySwarm.Space air=new FireflySwarm.Space(){public boolean available(double x,double y,double z){return true;}public List<AxisAlignedBB> collisions(AxisAlignedBB box){return Collections.emptyList();}};
            for(int i=0;i<100;i++)swarm.tick(0,0,-8,m.fireflies,air);
            int index=0;for(FireflySwarm.Fly fly:swarm.flies()){
                double dx=-2.8+(index%4)*1.8,dy=-1+(index/4)*1.2;index++;
                fly.x=fly.previousX=dx;fly.y=fly.previousY=dy;fly.z=fly.previousZ=-8;
                for(int i=0;i<FireflySwarm.HISTORY;i++){int at=fly.sample(i);fly.trailX[at]=dx-i*.045;fly.trailY[at]=dy+Math.sin(i*.15)*.18;fly.trailZ[at]=-8;}
                fly.age=80;
            }
            frame(true);perspective();before=read();renderer.fireflies(swarm,m.fireflies,.5F,0,0,0,1);check(difference(before,read())>70,"Glowing fireflies or trails missing");save(output,"ambience-fireflies.png",read());
            // A near wall must fully occlude the middle particle behind it.
            frame(true);perspective();wall();int[] blocked=read();renderer.fireflies(swarm,m.fireflies,.5F,0,0,0,1);int[] particles=read();
            for(int y=H/2-30;y<H/2+30;y++)for(int x=W/2-35;x<W/2+35;x++)check(blocked[y*W+x]==particles[y*W+x],"Fireflies rendered through a wall");
            check(difference(blocked,particles)>10,"Occlusion check hid every firefly");save(output,"ambience-fireflies-occluded.png",particles);
            // Exercise the actual installed hooks with a live vanilla Tessellator buffer.
            m.effects.setValue(new LinkedHashSet<>(Arrays.asList(AmbienceModule.SUN,AmbienceModule.MOON,AmbienceModule.AURORA)));
            frame(true);perspective();GL11.glRotatef(-90,1,0,0);quad(false);AmbienceRenderer.drawSun(Tessellator.getInstance(),.5F);
            quad(true);AmbienceRenderer.drawMoon(Tessellator.getInstance(),.5F);
            check(Tessellator.getInstance().getWorldRenderer().getVertexCount()==0,"Custom sky left vanilla's vertex buffer unfinished");
            check(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==0&&!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),"Ambience leaked shader or clipping state");
            check(GL11.glGetError()==0,"Ambience left an OpenGL error");
            frame(true);perspective();GL11.glRotatef(-20,1,0,0);
            for(int i=0;i<10;i++)renderer.aurora(m.aurora,i*.05,1);GL11.glFinish();long started=System.nanoTime();
            for(int i=0;i<60;i++)renderer.aurora(m.aurora,i*.05,1);GL11.glFinish();
            System.out.printf(Locale.ROOT,"Polar lights: %.2f ms/frame on %s%n",(System.nanoTime()-started)/60e6,GL11.glGetString(GL11.GL_RENDERER));
            System.out.println("Ambience pixels passed: detailed sun, moon phases, animated polar curtains, shooting star, firefly trails, wall depth and vanilla sky buffers.");
        }finally{mc.theWorld=null;AmbienceRenderer.INSTANCE.close();}
    }
    private static void frame(boolean night)throws Exception{ClientChangesRenderCheck.frame(W,H);GL11.glDepthMask(true);GL11.glClearDepth(1);GL11.glClearColor(night?.012F:.28F,night?.022F:.48F,night?.052F:.67F,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);}
    private static void perspective(){GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GLU.gluPerspective(70,W/(float)H,.1F,300);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();}
    private static void wall(){GL20.glUseProgram(0);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(true);GL11.glColor3f(.10F,.12F,.14F);GL11.glBegin(GL11.GL_QUADS);GL11.glVertex3d(-.7,-.7,-3);GL11.glVertex3d(.7,-.7,-3);GL11.glVertex3d(.7,.7,-3);GL11.glVertex3d(-.7,.7,-3);GL11.glEnd();}
    private static void ground(){GL20.glUseProgram(0);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_BLEND);GL11.glColor3f(.025F,.04F,.045F);GL11.glBegin(GL11.GL_QUAD_STRIP);for(int i=-15;i<=15;i++){double x=i*5,y=-8+(Math.floor(Math.sin(i*1.7)*3));GL11.glVertex3d(x,-100,-45);GL11.glVertex3d(x,y,-45);}GL11.glEnd();}
    private static void quad(boolean moon){WorldRenderer b=Tessellator.getInstance().getWorldRenderer();double y=moon?-100:100;b.begin(7,DefaultVertexFormats.POSITION_TEX);b.pos(-20,y,-20).tex(0,0).endVertex();b.pos(20,y,-20).tex(1,0).endVertex();b.pos(20,y,20).tex(1,1).endVertex();b.pos(-20,y,20).tex(0,1).endVertex();}
    private static int[] read(){ByteBuffer bytes=BufferUtils.createByteBuffer(W*H*4);GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);int[] image=new int[W*H];for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=((H-1-y)*W+x)*4;image[y*W+x]=(bytes.get(i)&255)<<16|(bytes.get(i+1)&255)<<8|bytes.get(i+2)&255;}return image;}
    private static int difference(int[] a,int[] b){int n=0;for(int i=0;i<a.length;i++)if(a[i]!=b[i])n++;return n;}
    private static void save(Path output,String name,int[] pixels)throws Exception{BufferedImage image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);image.setRGB(0,0,W,H,pixels,0,W);ImageIO.write(image,"png",output.resolve(name).toFile());}
    private static void check(boolean okay,String message){if(!okay)throw new AssertionError(message);}
    public static final class SkyWorld extends WorldClient {
        private SkyWorld(){super(null,null,0,null,null);}
        @Override public int getMoonPhase(){return 0;}
        @Override public float getRainStrength(float partial){return 0;}
        @Override public float getStarBrightness(float partial){return .5F;}
        @Override public float getCelestialAngle(float partial){return 0;}
    }
}
