package dev.vibe.branding;

import dev.vibe.core.startup.*;
import dev.vibe.hud.*;
import dev.vibe.module.impl.client.HudModule;
import dev.vibe.path.PathTestFixture;
import dev.vibe.ui.effect.EffectProgram;
import dev.vibe.ui.menu.MenuPalette;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Real offscreen pixels for artwork, selective tint, flipping, animation and early loading. */
public final class GhostBrandingRenderCheck {
    private static final int W = 960, H = 600;
    public static void main(String[] args) throws Exception {
        Path out = Paths.get(args[0]); Files.createDirectories(out);
        Pbuffer buffer = new Pbuffer(W, H, new PixelFormat(8, 24, 8), null, null); buffer.makeCurrent();
        try (GhostArt art = new GhostArt(); GhostLoadingRenderer loading = new GhostLoadingRenderer()) {
            frame(W, H); draw(art, false, false, false, false, 0); BufferedImage original = read(W, H);
            ImageIO.write(original, "png", out.resolve("ghost-static.png").toFile());
            frame(W, H); draw(art, false, false, false, false, 1700);
            check(differences(original, read(W, H)) == 0, "Static mode changed with the animation clock");
            frame(W, H); draw(art, false, false, false, true, 0); BufferedImage tint = read(W, H);
            ImageIO.write(tint, "png", out.resolve("ghost-tinted.png").toFile());
            int white = 0, pink = 0, black = 0;
            for (int y = 20; y < 276; y++) for (int x = 20; x < 276; x++) {
                int a = original.getRGB(x, y), b = tint.getRGB(x, y), r = a >> 16 & 255, g = a >> 8 & 255, blue = a & 255;
                if (r > 225 && g > 225 && blue > 225) { white++; check((b & 255) > (b >> 16 & 255) * 1.7, "White body tint missing"); }
                if (r > 150 && r > g * 1.55 && blue >= g * .9) { pink++; check(distance(a, b) < 4, "Tint altered the pink details"); }
                if (r < 18 && g < 18 && blue < 18) { black++; check(distance(a, b) < 4, "Tint altered black face/outline"); }
            }
            check(white > 5000 && pink > 50 && black > 100, "Artwork details were absent");
            frame(W, H); draw(art, false, true, false, false, 0); BufferedImage flipped = read(W, H);
            long delta = 0; for (int y = 20; y < 276; y++) for (int x = 20; x < 276; x++) delta += distance(original.getRGB(x, y), flipped.getRGB(295 - x, y));
            check(delta / (256D * 256) < 2, "Horizontal flip changed the image");
            frame(W, H); draw(art, false, false, true, false, 0); flipped = read(W, H);
            delta = 0; for (int y = 20; y < 276; y++) for (int x = 20; x < 276; x++) delta += distance(original.getRGB(x, y), flipped.getRGB(x, 295 - y));
            check(delta / (256D * 256) < 2, "Vertical flip changed the image");
            frame(W, H); draw(art, true, false, false, false, 0); BufferedImage first = read(W, H);
            frame(W, H); draw(art, true, false, false, false, 500); BufferedImage second = read(W, H);
            check(differences(first, second) > 500, "Sprite animation did not change poses");
            ImageIO.write(second, "png", out.resolve("ghost-animated.png").toFile());
            verifyState(art);
            // Exercise the same cached CPU tint used when a driver cannot compile GLSL.
            GhostArt legacy = new GhostArt();
            try { java.lang.reflect.Field failed = GhostArt.class.getDeclaredField("shaderFailed"); failed.setAccessible(true); failed.setBoolean(legacy, true);
                frame(W, H); draw(legacy, false, false, false, true, 0); BufferedImage fallback = read(W, H);
                long error = 0; for (int y = 20; y < 276; y++) for (int x = 20; x < 276; x++) error += distance(tint.getRGB(x, y), fallback.getRGB(x, y));
                check(error / (256D * 256) < 4, "Legacy tint differs from the shader");
            } finally { legacy.close(); }
            List<GhostStartup.Stage> stages = Arrays.asList(new GhostStartup.Stage("Loading Minecraft", "Initializing client", 4, 7),
                    new GhostStartup.Stage("Loading mods", "Vibe", 12, 20), new GhostStartup.Stage("Stitching textures", "Preparing world textures", 31, 64));
            for (int[] size : new int[][]{{960,600},{427,240},{320,200}}) {
                frame(size[0], size[1]); loading.draw(size[0], size[1], stages, 500);
                BufferedImage screenshot = read(size[0], size[1]); ImageIO.write(screenshot, "png", out.resolve("startup-" + size[0] + ".png").toFile());
                check(differences(screenshot, new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_RGB)) > size[0] * size[1] / 2, "Empty startup frame");
            }
            frame(W, H); loading.draw(W, H, Collections.<GhostStartup.Stage>emptyList(), 500);
            ImageIO.write(read(W, H), "png", out.resolve("startup-preparing.png").toFile());
            frame(W, H); loading.draw(W, H, stages, 3500); BufferedImage lava = read(W, H);
            ImageIO.write(lava, "png", out.resolve("startup-lava-3s.png").toFile());
            frame(W, H); loading.draw(W, H, stages, 4100); BufferedImage moved = read(W, H);
            int animatedWax = 0;
            for (int y = 145; y < 185; y++) for (int x = 180; x < 780; x++)
                if (distance(lava.getRGB(x, y), moved.getRGB(x, y)) > 10) animatedWax++;
            check(animatedWax > 400, "The startup wax pools are not animated");
            int ocean = lava.getRGB(480, 450);
            check((ocean & 255) > (ocean >> 16 & 255) + 35, "Ocean menu colours did not reach the startup wax");
            try (GhostLoadingRenderer rose = new GhostLoadingRenderer(MenuPalette.ROSE)) {
                frame(W, H); rose.draw(W, H, stages, 500);
                BufferedImage screenshot = read(W, H); ImageIO.write(screenshot, "png", out.resolve("startup-rose.png").toFile());
                int pinkPool = screenshot.getRGB(480, 450);
                check((pinkPool >> 16 & 255) > (pinkPool >> 8 & 255) + 25, "Saved menu palette was ignored by startup glass");
            }
            verifyLoadingState(loading);
            verifyProgressBar(loading, out);
            for (int i = 0; i < 80; i++) loading.draw(W, H, Collections.singletonList(
                    new GhostStartup.Stage("Stitching textures", "assets/vibe/textures/resource-" + i + ".png", i, 80)), 600 + i * 16);
            java.lang.reflect.Field cache = GhostLoadingRenderer.class.getDeclaredField("textCache"); cache.setAccessible(true);
            check(((Map<?, ?>)cache.get(loading)).size() <= 24, "Changing Forge messages retained unbounded GPU text textures");
            frame(320, 200); loading.draw(320, 200, Collections.singletonList(new GhostStartup.Stage(
                    "A very long Forge loading stage with enough words to fill the window",
                    "assets/vibe/textures/a/very/long/path/which/needs/to/be/truncated/without/overlapping/the/count.png", 5, 25)), 2100);
            ImageIO.write(read(320, 200), "png", out.resolve("startup-long-message.png").toFile());
            try (PathTestFixture f = new PathTestFixture()) {
                f.mc.displayWidth = W; f.mc.displayHeight = H; f.mc.gameSettings.guiScale = 1; OpenGlHelper.initializeTextures();
                PathTestFixture.set(net.minecraft.client.Minecraft.class, f.mc, "mcLanguageManager",
                        new net.minecraft.client.resources.LanguageManager(new net.minecraft.client.resources.data.IMetadataSerializer(), "en_US"));
                PathTestFixture.set(net.minecraft.client.Minecraft.class, f.mc, "mcDataDir", out.toFile());
                HudModule hud = new HudModule(); f.modules.registerDynamic(hud); hud.ghosts.mode.setValue("Static");
                HudManager manager = new HudManager(out.resolve("hud-fixture").toFile()); HudManager.HudElement ghost = manager.getElement(HudManager.GHOSTS);
                ghost.setScale(1.5F); frame(W, H); manager.drawPreview(ghost, null);
                check(ghost.getWidth() == 120 && ghost.getHeight() == 120, "Editor resize bounds differ from rendered ghost");
                ImageIO.write(read(W, H), "png", out.resolve("hud-ghost-scaled.png").toFile());
            }
            check(GL11.glGetError() == 0, "Ghost rendering left a GL error");
            System.out.println("Ghost pixels passed: still-image mode, animation, flips, selective tint, GL state, scaled HUD preview, startup lava motion/palettes at 960/427/320 pixels, empty/long stages and bounded text textures.");
        } finally { buffer.destroy(); }
    }
    private static void verifyProgressBar(GhostLoadingRenderer loading, Path out) throws Exception {
        java.lang.reflect.Field barField=GhostLoadingRenderer.class.getDeclaredField("bar");barField.setAccessible(true);
        Object bar=barField.get(loading);
        java.lang.reflect.Field failed=bar.getClass().getDeclaredField("failed");failed.setAccessible(true);
        check(!failed.getBoolean(bar), "Liquid progress shader silently fell back");
        BufferedImage[] stages=new BufferedImage[3];int[] amounts={0,50,100};
        for(int i=0;i<amounts.length;i++){
            frame(W,H);loading.draw(W,H,Collections.singletonList(new GhostStartup.Stage("Loading resources "+amounts[i],"Vibe",amounts[i],100)),6500);
            stages[i]=read(W,H);ImageIO.write(stages[i],"png",out.resolve("startup-progress-"+amounts[i]+".png").toFile());
        }
        check(distance(stages[0].getRGB(340,382),stages[1].getRGB(340,382))>80,"Measured half progress did not fill the bar");
        check(distance(stages[0].getRGB(700,382),stages[1].getRGB(700,382))<8,"Lava falsely filled past measured progress");
        check(distance(stages[0].getRGB(700,382),stages[2].getRGB(700,382))>80,"Completed progress did not reach the end");
        check(distance(stages[0].getRGB(340,374),stages[1].getRGB(340,374))>12,"The liquid bar has no external glow");
        try(GhostLoadingRenderer animated=new GhostLoadingRenderer()){
            Object animatedBar=barField.get(animated);java.lang.reflect.Field motionField=animatedBar.getClass().getDeclaredField("motion");motionField.setAccessible(true);
            Object motion=motionField.get(animatedBar);java.lang.reflect.Field dropsField=motion.getClass().getDeclaredField("drops"),formsField=motion.getClass().getDeclaredField("forms"),necksField=motion.getClass().getDeclaredField("necks");
            dropsField.setAccessible(true);formsField.setAccessible(true);necksField.setAccessible(true);
            Set<Integer> roots=new HashSet<>();float smallest=Float.MAX_VALUE,largest=0,minShape=1,maxShape=-1,deepest=0;
            BufferedImage before=null,after=null;
            for(int time=0;time<=6800;time+=50){
                frame(W,H);animated.draw(W,H,Collections.singletonList(new GhostStartup.Stage("Loading resources","Vibe",50,100)),time);
                float[] d=(float[])dropsField.get(motion),f=(float[])formsField.get(motion),n=(float[])necksField.get(motion);
                for(int i=0;i<d.length;i+=4)if(d[i+2]>1){roots.add(Math.round(n[i]*10));smallest=Math.min(smallest,n[i+3]);largest=Math.max(largest,n[i+3]);minShape=Math.min(minShape,f[i+2]);maxShape=Math.max(maxShape,f[i+2]);if(d[i+1]<78)deepest=Math.max(deepest,d[i+1]-9);}
                if(time==6500)before=read(W,H);if(time==6800)after=read(W,H);
                if(time==3000||time==4500)ImageIO.write(read(W,H),"png",out.resolve("startup-progress-drips-"+time+".png").toFile());
            }
            ImageIO.write(after,"png",out.resolve("startup-progress-drips.png").toFile());
            int moving=0;for(int y=390;y<449;y++)for(int x=208;x<480;x++)if(distance(before.getRGB(x,y),after.getRGB(x,y))>24)moving++;
            check(moving>25,"Wax drops below the progress bar are not moving");
            check(roots.size()>8,"Wax emissions are still stuck to fixed evenly spaced roots");
            check(largest/smallest>2,"Wax droplets have insufficient size variation");
            check(maxShape-minShape>.2F,"Wax droplets have insufficient shape variation");
            check(deepest>45,"Wax drops disappear before reaching the lower pool");
        }
        try(GhostLoadingRenderer fallback=new GhostLoadingRenderer()){
            Object legacyBar=barField.get(fallback);failed.setBoolean(legacyBar,true);
            frame(320,200);fallback.draw(320,200,Collections.singletonList(new GhostStartup.Stage("Loading resources","Vibe",50,100)),1700);
            ImageIO.write(read(320,200),"png",out.resolve("startup-progress-legacy.png").toFile());
        }
        check(GL11.glGetError()==0,"Liquid progress rendering left an OpenGL error");
    }
    private static void verifyLoadingState(GhostLoadingRenderer loading) throws Exception {
        int texture = GL11.glGenTextures(), mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try (EffectProgram caller = new EffectProgram("fullscreen.vert", "MenuGlass.frag")) {
            caller.bind(); int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            GL13.glActiveTexture(GL13.GL_TEXTURE2); GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_SCISSOR_TEST); GL11.glScissor(11, 12, 30, 40);
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            loading.draw(W, H, Collections.<GhostStartup.Stage>emptyList(), 4300);
            check(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) == program, "Startup shader replaced the caller's program");
            check(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) == GL13.GL_TEXTURE2
                    && GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == texture, "Startup glass leaked texture state");
            check(GL11.glIsEnabled(GL11.GL_DEPTH_TEST) && !GL11.glIsEnabled(GL11.GL_BLEND), "Startup glass leaked GL enables");
            check(GL11.glGetInteger(GL11.GL_MATRIX_MODE) == GL11.GL_TEXTURE, "Startup glass changed the caller's matrix mode");
            java.nio.IntBuffer clip = BufferUtils.createIntBuffer(16); GL11.glGetInteger(GL11.GL_SCISSOR_BOX, clip);
            check(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST) && clip.get(0) == 11 && clip.get(3) == 40, "Startup glass leaked its clip rectangle");
        } finally {
            GL20.glUseProgram(0); GL11.glMatrixMode(mode); GL11.glPopAttrib(); GL11.glDeleteTextures(texture);
        }
    }
    private static void verifyState(GhostArt art) {
        int texture = GL11.glGenTextures(); GL13.glActiveTexture(GL13.GL_TEXTURE2); GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture); GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_BLEND); GL11.glColor4f(.2F,.3F,.4F,.5F);
        art.draw(20,20,80,80,true,false,false,true,0xFF4080FF,1,700,12);
        check(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) == GL13.GL_TEXTURE2, "Active texture unit leaked");
        check(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == texture && GL11.glIsEnabled(GL11.GL_TEXTURE_2D), "Caller texture changed");
        check(GL11.glIsEnabled(GL11.GL_DEPTH_TEST) && !GL11.glIsEnabled(GL11.GL_BLEND), "Caller GL enables changed");
        java.nio.FloatBuffer color = BufferUtils.createFloatBuffer(16); GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);
        check(Math.abs(color.get(0) - .2F) < .001 && Math.abs(color.get(3) - .5F) < .001, "Caller color changed");
        GL11.glDeleteTextures(texture); GL11.glDisable(GL11.GL_TEXTURE_2D); GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }
    private static void draw(GhostArt art, boolean animated, boolean flipX, boolean flipY, boolean tint, long time) { art.draw(20,20,256,256,animated,flipX,flipY,tint,0xFF4080FF,1,time,12); }
    private static void frame(int w, int h) { GL11.glViewport(0,0,w,h); GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glClearColor(12/255F,14/255F,20/255F,1); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT); GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(0,w,h,0,-1000,1000); GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity(); }
    private static BufferedImage read(int w,int h) { ByteBuffer pixels = BufferUtils.createByteBuffer(w*h*4); GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels); BufferedImage result = new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB); for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=((h-1-y)*w+x)*4;result.setRGB(x,y,(pixels.get(i)&255)<<16|(pixels.get(i+1)&255)<<8|pixels.get(i+2)&255);}return result; }
    private static int distance(int a,int b) { return Math.abs((a>>16&255)-(b>>16&255))+Math.abs((a>>8&255)-(b>>8&255))+Math.abs((a&255)-(b&255)); }
    private static int differences(BufferedImage a,BufferedImage b) { int n=0;for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)if(distance(a.getRGB(x,y),b.getRGB(x,y))>10)n++;return n; }
    private static void check(boolean good,String message) { if(!good)throw new AssertionError(message); }
}
