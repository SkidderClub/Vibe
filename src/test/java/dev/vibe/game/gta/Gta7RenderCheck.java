package dev.vibe.game.gta;
import dev.vibe.Vibe;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.*;
import dev.vibe.ui.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.settings.*;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.util.*;
import org.lwjgl.*;
import org.lwjgl.opengl.*;


/** Opt-in native GL regression fixture; run with Gradle's verifyGta7Rendering task. */
public class Gta7RenderCheck {
    static Minecraft mc;
    static Gta7Game game;
    static Gta7Renderer renderer;
    static Gta7Gui gui;
    static EspModule esp;
    static Path root;
    static Path saveFile;
    static void set(Class<?> c,Object o,String n,Object v)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    static void call(String name,Class<?>[] types,Object...args)throws Exception{Method m=Gta7Gui.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(gui,args);}
    public static void main(String[] args)throws Exception{ root=Paths.get(args.length==0?"build/gta7-render-check":args[0]);Files.createDirectories(root);
        Files.deleteIfExists(root.resolve("region-east.png"));Files.deleteIfExists(root.resolve("region-trade_center.png"));
        saveFile=Files.createTempDirectory(root,"profile-").resolve("progress.json");
        Pbuffer buffer=new Pbuffer(1280,720,new PixelFormat(8,24,8),null,null);
        buffer.makeCurrent();
        try{
            Class<?> unsafeType=Class.forName("sun.misc.Unsafe"); Field uf=unsafeType.getDeclaredField("theUnsafe");uf.setAccessible(true);Object unsafe=uf.get(null);
            mc=(Minecraft)unsafeType.getMethod("allocateInstance",Class.class).invoke(unsafe,Minecraft.class);set(Minecraft.class,null,"theMinecraft",mc);
            mc.displayWidth=1280;mc.displayHeight=720;mc.gameSettings=new GameSettings();mc.gameSettings.guiScale=2;mc.gameSettings.fovSetting=78;
            set(Minecraft.class,mc,"mcDataDir",root.toFile());
            IMetadataSerializer meta=new IMetadataSerializer(); set(Minecraft.class,mc,"mcLanguageManager",new LanguageManager(meta,"en_US"));
            meta.registerMetadataSectionType(new TextureMetadataSectionSerializer(),TextureMetadataSection.class);
            meta.registerMetadataSectionType(new FontMetadataSectionSerializer(),FontMetadataSection.class);
            SimpleReloadableResourceManager resources=new SimpleReloadableResourceManager(meta);
            resources.reloadResourcePack(new DefaultResourcePack(Collections.<String,File>emptyMap()));
            set(Minecraft.class,mc,"mcResourceManager",resources);
            mc.renderEngine=new TextureManager(resources);
            mc.fontRendererObj=new FontRenderer(mc.gameSettings,new ResourceLocation("textures/font/ascii.png"),mc.renderEngine,false);
            mc.fontRendererObj.onResourceManagerReload(resources);
            OpenGlHelper.initializeTextures();
            Vibe vibe=new Vibe();set(Vibe.class,null,"instance",vibe);
            ModuleManager modules=(ModuleManager)unsafeType.getMethod("allocateInstance",Class.class).invoke(unsafe,ModuleManager.class);
            esp=new EspModule();Gta7Module gta=new Gta7Module();
            FogModule fog=new FogModule();CustomCrosshairModule crosshair=new CustomCrosshairModule();
            set(ModuleManager.class,modules,"modules",new ArrayList<dev.vibe.module.Module>(Arrays.asList(esp,gta,fog,crosshair)));
            set(Vibe.class,vibe,"moduleManager",modules);
            game=new Gta7Game(new Gta7World(),Gta7Progress.load(saveFile));
            renderer=new Gta7Renderer(mc);gui=new Gta7Gui(gta);gui.mc=mc;
            set(GuiScreen.class,gui,"fontRendererObj",mc.fontRendererObj);
            set(Gta7Gui.class,gui,"game",game);set(Gta7Gui.class,gui,"renderer",renderer);set(Gta7Gui.class,gui,"esp",esp);
            game.time=8;
            performanceCheck();
            game.respawn();game.time=8;
            renderStateChecks();
            fog.setEnabled(true);fog.tint.setValue("Rainbow");crosshair.setEnabled(true);
            frame("effects-fog-crosshair",false,false);
            mc.gameSettings.thirdPersonView=2;frame("effects-crosshair-independent-camera",false,false);mc.gameSettings.thirdPersonView=0;
            fog.method.setValue("Gaussian");frame("effects-fog-gaussian",false,false);
            fog.setEnabled(false);crosshair.setEnabled(false);
            frame("01-park-knife",false,false);
            game.x=72;game.z=71;game.yaw=70;game.pitch=-6;game.weapon=1;
            frame("02-street-ak",false,false);
            game.x=12.5;game.z=11;game.yaw=140;game.pitch=7;
            frame("03-cafe-interior",false,false);
            game.x=72;game.z=72;game.yaw=0;game.pitch=0;
            game.npcs.clear();game.npcs.add(new Gta7Game.Npc(72,64,true,0));game.npcs.add(new Gta7Game.Npc(74,58,true,1));game.npcs.get(0).health=63;
            esp.setEnabled(true);esp.getModes().setValue(new HashSet<String>(Arrays.asList("2D","3D")));
            frame("04-police-esp",false,false);
            game.health=java.math.BigDecimal.ZERO;game.dead=true;game.progress.award(java.math.BigInteger.valueOf(325));
            frame("05-upgrades",true,false);
            mc.displayWidth=640;mc.displayHeight=480;
            frame("06-small-upgrades",true,false);
            game.dead=false;game.health=game.progress.maxHealth();
            frame("07-small-paused",false,true);
            mc.displayWidth=1280;mc.displayHeight=720;game.dead=false;
            game.weapon=1;game.flash=.05;game.recoil=.6;
            frame("08-aim-fire",false,false);
            game.flash=0;game.recoil=0;
            boolean framebufferEnabled=mc.gameSettings.fboEnable;
            mc.gameSettings.fboEnable=false;
            frame("09-without-framebuffer",false,false);
            game.pitch=85;
            frame("10-looking-down",false,false);
            mc.gameSettings.fboEnable=framebufferEnabled;game.pitch=-85;
            frame("11-looking-up",false,false);
            game.x=12.5;game.z=5;game.yaw=180;game.pitch=0;frame("18-door-sign",false,false);
            game.x=72;game.z=72;game.yaw=0;
            game.pitch=0;game.health=java.math.BigDecimal.valueOf(10);game.hurtFlash=.2;
            frame("12-critical-health",false,false);
            game.health=game.progress.maxHealth();game.hurtFlash=0;game.dayTime=300;
            frame("13-midnight",false,false);game.dayTime=0;
            game.dead=true;set(Gta7Gui.class,gui,"shopTab",1);frame("14-weapon-skins",true,false);
            set(Gta7Gui.class,gui,"shopTab",0);game.dead=false;frame("15-pause-atlas",false,true);
            esp.setEnabled(false);
            for(Gta7World.Spawn resident:game.world.residents)game.npcs.add(new Gta7Game.Npc(resident.x,resident.z,resident.kind,0));
            for(Gta7Regions.Region region:Gta7Regions.Region.values()) {
                if(region==Gta7Regions.Region.CITY)continue;
                game.x=region.x();game.z=region.z()+65;game.y=8;game.yaw=0;game.pitch=5;
                if(region==Gta7Regions.Region.EMOJI){game.x=-90;game.z=-8;game.y=20;game.pitch=-10.5;}
                frame("region-"+region.name().toLowerCase(java.util.Locale.ROOT),false,false);
            }
            game.npcs.clear();game.x=-90;game.z=-8;game.y=33.38;game.yaw=0;game.pitch=0;game.dead=true;
            frame("29-emoji-facade",false,false);
            game.x=-30;game.z=-15;game.y=12;game.yaw=-38.66;game.pitch=-13;frame("30-emoji-volume",false,false);game.dead=false;
            game.npcs.clear();game.x=270;game.z=100;game.y=.14;game.yaw=0;game.pitch=0;
            Gta7Game.Npc goblin=new Gta7Game.Npc(270,96,Gta7Game.Kind.GOBLIN,0);goblin.yaw=180;game.npcs.add(goblin);
            frame("24-goblin-closeup",false,false);goblin.hurt=.2;frame("25-goblin-hurt",false,false);goblin.health=0;goblin.death=.4;frame("26-goblin-fallen",false,false);
            game.x=-90;game.z=-107;game.yaw=180;game.pitch=0;game.npcs.clear();frame("27-emoji-entrance",false,false);
            game.x=-90;game.z=-97.7;game.useElevator(false,true);frame("28-emoji-interior",false,false);
            game.x=72;game.z=70;game.y=.14;game.yaw=0;game.pitch=0;game.npcs.clear();
            game.dayTime=155;frame("19-sunset-street",false,false);
            game.x=-90;game.z=110;game.yaw=0;game.dayTime=300;frame("20-forest-lights",false,false);
            game.x=85;game.z=306;game.yaw=0;game.dayTime=280;frame("21-frontier-camp",false,false);
            game.x=12.5;game.z=13.5;game.y=.14;game.yaw=110;game.pitch=4;frame("22-night-interior",false,false);
            game.x=9.6;game.z=6;game.yaw=180;game.pitch=0;game.weapon=1;game.ammo=java.math.BigInteger.TEN;game.attackCooldown=0;game.dayTime=0;game.attack();
            frame("23-glass-impact",false,false);game.particles.clear();game.tracers.clear();game.flash=game.recoil=0;
            game.x=72;game.z=70;game.y=.14;game.yaw=0;game.pitch=0;game.npcs.clear();
            Gta7Game.Npc officer=new Gta7Game.Npc(72,67,true,0);officer.yaw=180;officer.hurt=.2;game.npcs.add(officer);
            frame("16-officer-hurt",false,false);officer.health=0;officer.death=.4;
            frame("17-officer-fallen",false,false);
            game.dead=false;game.equip(1);
            mc.gameSettings.keyBindsHotbar[0].setKeyCode(org.lwjgl.input.Keyboard.KEY_K);
            mc.gameSettings.keyBindsHotbar[1].setKeyCode(-97);
            call("bindingPressed",new Class[]{int.class},org.lwjgl.input.Keyboard.KEY_1);
            if(game.weapon!=1)throw new AssertionError("Old hotbar key still selects a weapon");
            call("bindingPressed",new Class[]{int.class},org.lwjgl.input.Keyboard.KEY_K);
            if(game.weapon!=0)throw new AssertionError("Rebound keyboard hotbar slot failed");
            call("bindingPressed",new Class[]{int.class},-97);
            if(game.weapon!=1)throw new AssertionError("Mouse-bound hotbar slot failed");
            game.ammo=java.math.BigInteger.valueOf(7);call("bindingPressed",new Class[]{int.class},org.lwjgl.input.Keyboard.KEY_R);
            if(game.reload<=0)throw new AssertionError("Reload binding failed");
            game.x=75.5;game.health=java.math.BigDecimal.valueOf(63);
            game.progress.award(java.math.BigInteger.valueOf(500));game.progress.purchase(Gta7Progress.Upgrade.KNIFE);
            set(Gta7Gui.class,gui,"paused",true);gui.initGui();set(Gta7Gui.class,gui,"paused",false);
            if(game.x!=75.5||game.ammo.intValue()!=7||game.health.intValue()!=63)throw new AssertionError("Resize reset the run");
            int oldLevel=game.progress.level(Gta7Progress.Upgrade.KNIFE).intValue();
            game.hurt(1000);
            call("mouseClicked",new Class[]{int.class,int.class,int.class},gui.width/2-260,((gui.height-320)/2)+104,0);
            if(game.progress.level(Gta7Progress.Upgrade.KNIFE).intValue()!=oldLevel+1)throw new AssertionError("Death-screen upgrade click failed");
            java.math.BigInteger xp=game.progress.xp();
            gui.onGuiClosed();
            if(!Gta7Progress.load(saveFile).xp().equals(xp))throw new AssertionError("Close did not save the XP bank");
            System.out.println("Rebound keys, mouse hotbar, reload, resize, death purchase and close/save checks passed");
            renderer.close();
            System.out.println("GTA7 offscreen Minecraft OpenGL review passed; renderer: "+GL11.glGetString(GL11.GL_RENDERER));
        }finally{buffer.destroy();}
    }
    static void performanceCheck() throws Exception {
        game.x=72;game.z=72;game.y=.14;game.yaw=0;game.pitch=0;game.dead=true;
        game.npcs.clear();
        for(int i=0;i<80;i++)game.npcs.add(new Gta7Game.Npc(66+(i%8)*1.6,40+(i/8)*2.4,i%3==0,i%8));
        for(int i=0;i<30;i++){renderer.render(game,esp,false);GL11.glFinish();}
        double[] frames=new double[90];
        for(int i=0;i<frames.length;i++){long start=System.nanoTime();renderer.render(game,esp,false);GL11.glFinish();frames[i]=(System.nanoTime()-start)/1e6;}
        Arrays.sort(frames);
        double checksum=0;
        for(int round=0;round<3;round++)for(int i=0;i<2000;i++){double a=i*2.39996;checksum+=game.world.ray(72,1.76,72,Math.sin(a),0,Math.cos(a),115);}
        long rayStart=System.nanoTime();
        for(int i=0;i<12000;i++){double a=i*2.39996;checksum+=game.world.ray(72,1.76,72,Math.sin(a),0,Math.cos(a),115);}
        double rayMs=(System.nanoTime()-rayStart)/1e6;
        long boxes=0;for(Gta7World.Chunk c:game.world.chunks)boxes+=c.boxes.size();
        String report=String.format(java.util.Locale.ROOT,"crowd_frame_median_ms=%.3f%ncrowd_frame_p95_ms=%.3f%nrays_12000_ms=%.3f%nworld_boxes=%d%nworld_windows=%d%nworld_props=%d%nray_checksum=%.3f%n",frames[45],frames[85],rayMs,boxes,game.world.windows.size(),game.world.props.size(),checksum);
        Files.write(root.resolve("performance.txt"),report.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        System.out.println(report);
    }
    static void renderStateChecks() throws Exception {
        game.dead=true;game.x=72;game.z=72;game.yaw=0;game.pitch=0;
        // Render into an existing framebuffer, as Minecraft and shader mods do.
        net.minecraft.client.shader.Framebuffer destination=new net.minecraft.client.shader.Framebuffer(1280,720,true);
        destination.bindFramebuffer(true);
        GL11.glDisable(GL11.GL_STENCIL_TEST);GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK,GL11.GL_FILL);GL11.glDepthRange(0,1);
        GL11.glShadeModel(GL11.GL_SMOOTH);GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        renderer.render(game,esp,false);byte[] baseline=readFrame();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_TEXTURE);GL11.glPushMatrix();GL11.glTranslated(.2,.4,0);GL11.glScaled(1.5,.7,1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glEnable(GL11.GL_STENCIL_TEST);GL11.glStencilFunc(GL11.GL_NEVER,0,255);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);GL11.glPolygonOffset(80,10000);
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK,GL11.GL_LINE);GL11.glDepthRange(.2,.6);
        GL11.glShadeModel(GL11.GL_FLAT);GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(20,30,10,10);
        GL14.glBlendEquation(GL14.GL_FUNC_REVERSE_SUBTRACT);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,GL11.GL_TEXTURE_ENV_MODE,GL11.GL_REPLACE);
        GL11.glEnable(GL11.GL_LIGHTING);GL11.glEnable(GL11.GL_LIGHT7);GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        GL11.glColorMaterial(GL11.GL_FRONT_AND_BACK,GL11.GL_EMISSION);
        FloatBuffer hostileLight=BufferUtils.createFloatBuffer(4);hostileLight.put(new float[]{1,0,1,1}).flip();
        GL11.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT,hostileLight);
        GL11.glMaterial(GL11.GL_FRONT_AND_BACK,GL11.GL_SPECULAR,hostileLight);
        GL11.glLightf(GL11.GL_LIGHT0,GL11.GL_SPOT_CUTOFF,12);
        GL11.glLightf(GL11.GL_LIGHT0,GL11.GL_LINEAR_ATTENUATION,4);
        FloatBuffer before=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_TEXTURE_MATRIX,before);
        renderer.render(game,esp,false);
        FloatBuffer after=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_TEXTURE_MATRIX,after);
        if(!before.equals(after))throw new AssertionError("Texture matrix leaked");
        if(GL11.glGetInteger(0x8CA6)!=destination.framebufferObject)throw new AssertionError("Framebuffer binding leaked");
        if(!GL11.glIsEnabled(GL11.GL_STENCIL_TEST)||!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST))throw new AssertionError("Render flags leaked");
        if(!GL11.glIsEnabled(GL11.GL_LIGHT7)||GL11.glGetInteger(GL11.GL_COLOR_MATERIAL_PARAMETER)!=GL11.GL_EMISSION)throw new AssertionError("Lighting state leaked");
        byte[] contaminated=readFrame();
        if(!Arrays.equals(baseline,contaminated))throw new AssertionError("Inherited OpenGL state changed GTA7 pixels");
        GL11.glMatrixMode(GL11.GL_TEXTURE);GL11.glPopMatrix();GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopAttrib();
        game.npcs.clear();game.npcs.add(new Gta7Game.Npc(72,64,true,0));
        esp.setEnabled(true);esp.getModes().setValue(new HashSet<String>(Arrays.asList("2D","3D")));
        renderer.render(game,esp,false);
        Field espField=Gta7Renderer.class.getDeclaredField("policeEsp");espField.setAccessible(true);Object localEsp=espField.get(renderer);
        Field boxesField=EspRenderer.class.getDeclaredField("screenBoxes");boxesField.setAccessible(true);List<?> boxes=(List<?>)boxesField.get(localEsp);
        if(boxes.size()!=1)throw new AssertionError("Police bounds were not captured");
        Field boundsField=boxes.get(0).getClass().getDeclaredField("bounds");boundsField.setAccessible(true);Object bounds=boundsField.get(boxes.get(0));
        Field left=bounds.getClass().getDeclaredField("left"),right=bounds.getClass().getDeclaredField("right");left.setAccessible(true);right.setAccessible(true);
        if(left.getFloat(bounds)>=320||right.getFloat(bounds)<=320)throw new AssertionError("Supersampling displaced ESP from the NPC");
        esp.setEnabled(false);destination.unbindFramebuffer();destination.deleteFramebuffer();
        game.respawn();game.time=8;
        if(GL11.glGetError()!=0)throw new AssertionError("OpenGL error in state regression check");
        System.out.println("Pixel-identical rendering under changed GL state; framebuffer restoration and supersampled ESP passed");
    }
    static byte[] readFrame() {
        GL11.glFinish();ByteBuffer buffer=BufferUtils.createByteBuffer(1280*720*4);
        GL11.glReadPixels(0,0,1280,720,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,buffer);
        byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);return bytes;
    }
    static void frame(String name,boolean death,boolean paused)throws Exception{
        gui.width=mc.displayWidth/2;gui.height=mc.displayHeight/2;
        GL11.glViewport(0,0,mc.displayWidth,mc.displayHeight);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,gui.width,gui.height,0,1000,3000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();GL11.glTranslated(0,0,-2000);
        GlStateManager.enableTexture2D();GlStateManager.enableAlpha();GlStateManager.alphaFunc(GL11.GL_GREATER,.1f);GlStateManager.enableDepth();GlStateManager.color(1,1,1,1);
        FloatBuffer projection=BufferUtils.createFloatBuffer(16),view=BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX,projection);GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,view);
        int texture=GL11.glGenTextures();GlStateManager.bindTexture(texture);
        if(name.contains("aim")) {
            // Complete the aim transition deterministically for the comparison image.
            set(Gta7Renderer.class,renderer,"aimBlend",1.0);
        }
        renderer.render(game,esp,name.contains("aim"));
        FloatBuffer restored=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX,restored);
        for(int i=0;i<16;i++)if(projection.get(i)!=restored.get(i))throw new AssertionError("Projection not restored");
        restored.clear();GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,restored);
        for(int i=0;i<16;i++)if(view.get(i)!=restored.get(i))throw new AssertionError("Modelview not restored");
        if(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)!=texture)throw new AssertionError("Texture binding not restored");
        GlStateManager.deleteTexture(texture);
        int error=GL11.glGetError();if(error!=0)throw new AssertionError("GL after city "+name+": "+error);
        GlStateManager.disableDepth();GlStateManager.depthMask(false);renderer.overlay();call("atmosphere",new Class[0]);
        set(Gta7Gui.class,gui,"paused",paused);call("hud",new Class[]{boolean.class},name.contains("aim"));
        if(death||paused)call("menu",new Class[]{int.class,int.class},0,0);
        GlStateManager.depthMask(true);GlStateManager.enableDepth();GL11.glFinish();
        error=GL11.glGetError();if(error!=0)throw new AssertionError("GL after HUD "+name+": "+error);
        ByteBuffer pixels=BufferUtils.createByteBuffer(mc.displayWidth*mc.displayHeight*4);
        GL11.glReadPixels(0,0,mc.displayWidth,mc.displayHeight,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        BufferedImage image=new BufferedImage(mc.displayWidth,mc.displayHeight,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<mc.displayHeight;y++)for(int x=0;x<mc.displayWidth;x++){int i=(y*mc.displayWidth+x)*4;image.setRGB(x,mc.displayHeight-1-y,((pixels.get(i)&255)<<16)|((pixels.get(i+1)&255)<<8)|(pixels.get(i+2)&255));}
        ImageIO.write(image,"png",root.resolve(name+".png").toFile());
        System.out.println("Captured "+name);
    }
}







