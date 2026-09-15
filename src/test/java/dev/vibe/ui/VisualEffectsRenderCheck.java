package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.*;
import dev.vibe.ui.effect.*;
import dev.vibe.media.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.*;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Real driver checks for shader compilation, depth isolation, feedback, state restoration and HUD pixels. */
public final class VisualEffectsRenderCheck {
    static final int W=640,H=360;
    static void set(Class<?> type,Object instance,String name,Object value)throws Exception {Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(instance,value);}
    public static void main(String[] args)throws Exception {
        Path output=Paths.get("build/visual-effects-check");Files.createDirectories(output);
        Pbuffer buffer=new Pbuffer(W,H,new PixelFormat(8,24,8),null,null);buffer.makeCurrent();
        try {
            Class<?> type=Class.forName("sun.misc.Unsafe");Field uf=type.getDeclaredField("theUnsafe");uf.setAccessible(true);Object unsafe=uf.get(null);
            Method allocate=type.getMethod("allocateInstance",Class.class);
            Minecraft mc=(Minecraft)allocate.invoke(unsafe,Minecraft.class);set(Minecraft.class,null,"theMinecraft",mc);
            mc.displayWidth=W;mc.displayHeight=H;mc.gameSettings=new GameSettings();mc.gameSettings.guiScale=1;
            set(Minecraft.class,mc,"mcDataDir",output.toFile());
            IMetadataSerializer meta=new IMetadataSerializer();
            meta.registerMetadataSectionType(new TextureMetadataSectionSerializer(),TextureMetadataSection.class);
            meta.registerMetadataSectionType(new FontMetadataSectionSerializer(),FontMetadataSection.class);
            set(Minecraft.class,mc,"mcLanguageManager",new LanguageManager(meta,"en_US"));
            SimpleReloadableResourceManager resources=new SimpleReloadableResourceManager(meta);
            resources.reloadResourcePack(new DefaultResourcePack(Collections.<String,File>emptyMap()));
            set(Minecraft.class,mc,"mcResourceManager",resources);mc.renderEngine=new TextureManager(resources);
            OpenGlHelper.initializeTextures();
            mc.fontRendererObj=new FontRenderer(mc.gameSettings,new ResourceLocation("textures/font/ascii.png"),mc.renderEngine,false);
            mc.fontRendererObj.onResourceManagerReload(resources);
            Vibe vibe=new Vibe();set(Vibe.class,null,"instance",vibe);
            ModuleManager manager=(ModuleManager)allocate.invoke(unsafe,ModuleManager.class);
            FogModule fog=new FogModule();CustomCrosshairModule crosshair=new CustomCrosshairModule();MusicModule music=new MusicModule();
            set(ModuleManager.class,manager,"modules",new ArrayList<dev.vibe.module.Module>(Arrays.asList(fog,crosshair,music)));
            set(Vibe.class,vibe,"moduleManager",manager);fog.setEnabled(true);
            FogRenderer renderer=new FogRenderer();
            pattern();byte[] original=pixels();fog.blur.setValue(false);renderer.render(fog,.05f,100,0,0);
            if(renderer.hasFailed())throw new AssertionError("Fog shader compilation failed");
            sameRegion(original,pixels(),0,W,"Scene-only fog must preserve pixels");
            fog.tint.setValue("Custom");fog.color.setValue(0xFFFF0000);fog.tintOpacity.setValue(100D);fog.start.setValue(10D);fog.end.setValue(30D);fog.sky.setValue(false);
            pattern();GL11.glEnable(GL11.GL_FOG);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(true);GL13.glActiveTexture(GL13.GL_TEXTURE2);
            int bound=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,bound);
            renderer.render(fog,.05f,100,0,0);byte[] tinted=pixels();
            if(!GL11.glIsEnabled(GL11.GL_FOG)||!GL11.glIsEnabled(GL11.GL_DEPTH_TEST)||!GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK))throw new AssertionError("Fog leaked enable/depth state");
            if(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)!=GL13.GL_TEXTURE2||GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)!=bound)throw new AssertionError("Fog leaked texture state");
            GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glDeleteTextures(bound);
            sameRegion(original,tinted,0,W/3,"Near objects changed");sameRegion(original,tinted,W*2/3,W,"Excluded sky changed");
            int sample=(H/2*W+W/2)*4;if((tinted[sample]&255)<245||(tinted[sample+1]&255)>10)throw new AssertionError("Far tint absent");
            image(output.resolve("fog-depth.png"),tinted);
            fog.tint.setValue("Scene");fog.blur.setValue(true);
            for(String method:new String[]{"Kawase","Gaussian"}) {
                fog.method.setValue(method);pattern();renderer.render(fog,.05f,100,0,0);byte[] blurred=pixels();
                if(renderer.hasFailed())throw new AssertionError(method+" failed");
                sameRegion(original,blurred,0,W/3,"Blur changed near objects");
                if(variance(blurred,W/3+30,W*2/3-30)>=variance(original,W/3+30,W*2/3-30)*.8)throw new AssertionError(method+" did not blur far geometry");
                image(output.resolve("fog-"+method.toLowerCase(java.util.Locale.ROOT)+".png"),blurred);
            }
            fog.tint.setValue("Rainbow");pattern();renderer.render(fog,.05f,100,25,15);image(output.resolve("fog-rainbow.png"),pixels());
            // Reflection uses the exact packaged shader and torus mesh, without a Minecraft world.
            pattern();try(EffectState state=new EffectState();SceneTexture scene=new SceneTexture();EffectProgram reflection=new EffectProgram("Reflection.vert","Reflection.frag")) {
                scene.capture(0,0,W,H,false);GL11.glDisable(GL11.GL_DEPTH_TEST);reflection.bind();FogRenderer.texture(0,scene.color);
                reflection.integer("Tex0",0);reflection.scalar("Freq",.5f);reflection.vec3("CamPos",0,0,0);
                GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(-4,4,-2.25,2.25,-10,10);
                GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();GL11.glTranslatef(0,0,-2);
                TorusRenderer.drawMesh(.25f,1);image(output.resolve("torus.png"),pixels());
            }
            gui();crosshair.setEnabled(true);mc.gameSettings.thirdPersonView=2;
            if(!new CustomCrosshairRenderer().renderGta7(W,H,.8f))throw new AssertionError("GTA7 crosshair was gated by Minecraft camera");
            byte[] cross=pixels();int visible=0;for(int i=0;i<cross.length;i+=4)if((cross[i]&255)>30)visible++;
            if(visible<10)throw new AssertionError("GTA7 crosshair empty");
            BufferedImage cover=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<64;y++)for(int x=0;x<64;x++)cover.setRGB(x,y,0xFF000000|((x*4)<<16)|((y*4)<<8)|140);
            MediaTrack track=new MediaTrack("A title that scrolls through the media display", "Vibe / Test artist", "Test", "Paused", false,false,42000,180000,cover);
            gui();MusicHudRenderer hud=new MusicHudRenderer();hud.draw(music,track,60,45,false);
            AudioSpectrum spectrum=new AudioSpectrum();float[] wave=new float[AudioSpectrum.SIZE];
            for(int i=0;i<wave.length;i++)wave[i]=(float)(.4*Math.sin(2*Math.PI*5*i/wave.length)+.2*Math.sin(2*Math.PI*80*i/wave.length));
            spectrum.accept(wave,wave.length,48000);music.visualizer.setValue(true);music.gain.setValue(5D);
            MusicVisualizer visualizer=new MusicVisualizer();
            for(int i=0;i<12;i++)visualizer.draw(music,spectrum.frame(),W,H);
            image(output.resolve("music-hud-waves.png"),pixels());hud.close();
            musicCards(output,music,cover);renderer.close();
            int error=GL11.glGetError();if(error!=GL11.GL_NO_ERROR)throw new AssertionError("OpenGL error "+error);
            System.out.println("Visual effects OK: depth masks, sky exclusion, Kawase/Gaussian, reflection, state restoration, GTA7 crosshair and media HUD.");
        } finally{buffer.destroy();}
    }
    static void musicCards(Path output,MusicModule music,BufferedImage cover)throws Exception {
        MusicHudRenderer hud=new MusicHudRenderer();
        MediaTrack missing=new MediaTrack("ovoline@intent vs pandaware hvh (uncut)","0simp","Chrome","Paused",false,false,0,0,null);
        MediaTrack album=new MediaTrack("Midnight City","M83 / Hurry Up, We're Dreaming","Spotify","Playing",true,false,42000,244000,cover);
        gui();
        NeverLoseFont.REGULAR.draw("NO COVER / PAUSED",28,18,0xFF8FA5B4);
        hud.draw(music,missing,28,38,false);
        NeverLoseFont.REGULAR.draw("ALBUM / PLAYING",340,18,0xFF8FA5B4);
        hud.draw(music,album,340,38,false);
        NeverLoseFont.REGULAR.draw("NARROW / 125% SCALE",28,126,0xFF8FA5B4);
        music.hudWidth.setValue(180D);music.hudScale.setValue(1.25D);music.scroll.setValue(false);
        hud.draw(music,album,28,146,false);
        music.hudWidth.setValue(270D);music.hudScale.setValue(1D);music.scroll.setValue(true);
        NeverLoseFont.REGULAR.draw("LIVE RADIO",340,126,0xFF8FA5B4);
        hud.draw(music,new MediaTrack("Groove Salad","SomaFM","Radio","Streaming",true,true,0,0,null),340,146,false);
        NeverLoseFont.REGULAR.draw("COVER / BACKDROP / PROGRESS OFF",28,244,0xFF8FA5B4);
        music.cover.setValue(false);music.coverBackground.setValue(false);music.progress.setValue(false);
        hud.draw(music,album,28,264,false);
        music.cover.setValue(true);music.coverBackground.setValue(true);music.progress.setValue(true);
        NeverLoseFont.REGULAR.draw("WAITING FOR MEDIA",340,244,0xFF8FA5B4);
        hud.draw(music,MediaTrack.idle("Open your music player"),340,264,false);
        image(output.resolve("music-hud-states.png"),pixels());

        // Exercise the actual scroll transform while enclosed by an editor clip.
        gui();byte[] before=pixels();
        hud.draw(music,missing,28,38,false);
        set(MusicHudRenderer.class,hud,"changedAt",System.currentTimeMillis()-3000L);
        gui();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(120,270,60,40);
        GlStateManager.enableDepth();GlStateManager.depthMask(true);GlStateManager.enableAlpha();GlStateManager.disableBlend();
        hud.draw(music,missing,28,38,false);
        if(!GL11.glIsEnabled(GL11.GL_DEPTH_TEST)||!GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
                ||!GL11.glIsEnabled(GL11.GL_ALPHA_TEST)||GL11.glIsEnabled(GL11.GL_BLEND))
            throw new AssertionError("Music HUD leaked depth, alpha or blend state");
        IntBuffer clip=BufferUtils.createIntBuffer(16);GL11.glGetInteger(GL11.GL_SCISSOR_BOX,clip);
        if(!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)||clip.get(0)!=120||clip.get(1)!=270||clip.get(2)!=60||clip.get(3)!=40)
            throw new AssertionError("Music HUD replaced the enclosing editor clip");
        byte[] clipped=pixels();
        sameRegion(before,clipped,0,120,"Music HUD escaped left clip");
        sameRegion(before,clipped,180,W,"Music HUD escaped right clip");
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        hud.close();
    }
    static double depth(double distance){return ((100+.05-2*100*.05/distance)/(100-.05)+1)/2;}
    static void pattern(){
        GL20.glUseProgram(0);GL11.glViewport(0,0,W,H);GL11.glDisable(GL11.GL_FOG);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(true);
        GL11.glColorMask(true,true,true,true);GL11.glEnable(GL11.GL_SCISSOR_TEST);
        for(int x=0;x<W;x++){GL11.glScissor(x,0,1,H);float c=x%12<6?.15f:.85f;GL11.glClearColor(c,c,c,1);GL11.glClearDepth(x<W/3?depth(1):x<W*2/3?depth(60):1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);}
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
    static void gui(){GL11.glViewport(0,0,W,H);GL11.glClearColor(.04f,.055f,.08f,1);GL11.glDepthMask(true);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,W,H,0,-100,100);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();GuiRenderState.prepare(false);}
    static byte[] pixels(){ByteBuffer bytes=BufferUtils.createByteBuffer(W*H*4);GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);byte[] result=new byte[bytes.capacity()];bytes.get(result);return result;}
    static void sameRegion(byte[] a,byte[] b,int left,int right,String label){for(int y=2;y<H-2;y++)for(int x=left;x<right;x++)for(int c=0;c<3;c++)if(Math.abs((a[(y*W+x)*4+c]&255)-(b[(y*W+x)*4+c]&255))>1)throw new AssertionError(label+" at "+x+","+y);}
    static double variance(byte[] data,int left,int right){double sum=0,squared=0;int count=0;for(int x=left;x<right;x++){double n=data[(H/2*W+x)*4]&255;sum+=n;squared+=n*n;count++;}return squared/count-Math.pow(sum/count,2);}
    static void image(Path path,byte[] pixels)throws Exception{BufferedImage image=new BufferedImage(W,H,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=((H-1-y)*W+x)*4;image.setRGB(x,y,0xFF000000|((pixels[i]&255)<<16)|((pixels[i+1]&255)<<8)|(pixels[i+2]&255));}ImageIO.write(image,"png",path.toFile());}
}
