package dev.vibe.ui;

import com.mojang.authlib.GameProfile;
import dev.vibe.Vibe;
import dev.vibe.game.meme.ConnectFourState;
import dev.vibe.game.meme.GameType;
import dev.vibe.game.meme.TicTacToeState;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.*;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Offscreen checks for scaled input, clipped player lists, game pieces and impact shapes. */
public final class ClientChangesRenderCheck {
    private static final Path OUTPUT = Paths.get("build/client-changes-check");
    private static Object unsafe;
    private static Method allocate;
    private static Minecraft mc;
    private static void set(Class<?> type, Object object, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(object,value);
    }
    private static Object get(Class<?> type, Object object, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static <T> T instance(Class<T> type) throws Exception { return type.cast(allocate.invoke(unsafe,type)); }
    private static int integer(MemeGameGui gui, String field) throws Exception { return (Integer)get(MemeGameGui.class,gui,field); }
    private static int scaled(MemeGameGui gui, int coordinate) throws Exception { return Math.round(coordinate * (Float)get(MemeGameGui.class,gui,"uiScale")); }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT);
        Pbuffer buffer = new Pbuffer(1280,720,new PixelFormat(8,24,8),null,null); buffer.makeCurrent();
        try {
            Class<?> type = Class.forName("sun.misc.Unsafe"); Field field = type.getDeclaredField("theUnsafe"); field.setAccessible(true);
            unsafe = field.get(null); allocate = type.getMethod("allocateInstance",Class.class);
            mc = instance(Minecraft.class); set(Minecraft.class,null,"theMinecraft",mc);
            mc.gameSettings = new GameSettings(); mc.gameSettings.guiScale = 1;
            set(Minecraft.class,mc,"mcDataDir",OUTPUT.toFile());
            IMetadataSerializer meta = new IMetadataSerializer();
            meta.registerMetadataSectionType(new TextureMetadataSectionSerializer(),TextureMetadataSection.class);
            meta.registerMetadataSectionType(new FontMetadataSectionSerializer(),FontMetadataSection.class);
            set(Minecraft.class,mc,"mcLanguageManager",new net.minecraft.client.resources.LanguageManager(meta,"en_US"));
            SimpleReloadableResourceManager resources = new SimpleReloadableResourceManager(meta);
            resources.reloadResourcePack(new DefaultResourcePack(Collections.<String,File>emptyMap()));
            resources.reloadResourcePack(new FolderResourcePack(new File("src/main/resources")));
            set(Minecraft.class,mc,"mcResourceManager",resources); mc.renderEngine = new TextureManager(resources);
            OpenGlHelper.initializeTextures();
            net.minecraft.client.shader.ShaderLinkHelper.setNewStaticShaderLinkHelper();
            mc.entityRenderer = instance(EntityRenderer.class); set(EntityRenderer.class,mc.entityRenderer,"mc",mc);
            mc.fontRendererObj = new FontRenderer(mc.gameSettings,new ResourceLocation("textures/font/ascii.png"),mc.renderEngine,false);
            mc.fontRendererObj.onResourceManagerReload(resources);
            Vibe vibe = new Vibe(); set(Vibe.class,null,"instance",vibe);
            ModuleManager modules = instance(ModuleManager.class);
            set(ModuleManager.class,modules,"modules",new ArrayList<Module>()); set(Vibe.class,vibe,"moduleManager",modules);
            if (args.length>0 && args[0].equals("hud-editor")) {
                HudModule hud = new HudModule();
                set(ModuleManager.class,modules,"modules",new ArrayList<Module>(Collections.<Module>singletonList(hud)));
                mc.displayWidth=1280; mc.displayHeight=720;
                frame(1280,720);
                dev.vibe.hud.HudManager manager = new dev.vibe.hud.HudManager(OUTPUT.resolve("hud-editor-fixture").toFile());
                dev.vibe.hud.HudEditorGui editor = new dev.vibe.hud.HudEditorGui(manager);
                editor.setWorldAndResolution(mc,1280,720);
                editor.drawScreen(630,330,0);
                save("hud-editor.png");
                mc.displayWidth=683; mc.displayHeight=384;
                frame(683,384);
                editor.setWorldAndResolution(mc,683,384);
                editor.drawScreen(320,190,0);
                save("hud-editor-compact.png");
                dev.vibe.hud.HudManager.HudElement anchored = manager.getElement(dev.vibe.hud.HudManager.ARMOR);
                anchored.setScale(1.5F);
                net.minecraft.client.gui.ScaledResolution resolution = new net.minecraft.client.gui.ScaledResolution(mc);
                int rawLeft = anchored.left(resolution,82), rawTop = anchored.top(resolution,28);
                Method scaled = dev.vibe.hud.HudManager.class.getDeclaredMethod("renderScaled",
                        dev.vibe.hud.HudManager.HudElement.class,net.minecraft.client.gui.ScaledResolution.class,Runnable.class);
                scaled.setAccessible(true);
                scaled.invoke(manager,anchored,resolution,(Runnable)() -> anchored.setBounds(rawLeft,rawTop,82,28));
                if (anchored.getLeft()+anchored.getWidth()!=resolution.getScaledWidth()-9
                        || anchored.getTop()+anchored.getHeight()!=resolution.getScaledHeight()-9)
                    throw new AssertionError("Scaled right/bottom anchors moved");
                if (GL11.glGetError()!=GL11.GL_NO_ERROR) throw new AssertionError("HUD editor GL error");
                System.out.println("HUD editor screenshot and GL state passed.");
                return;
            }
            if (args.length>0 && args[0].equals("liquid-glass")) {
                mc.displayWidth=960;mc.displayHeight=540;
                testHud(modules);testLiquidGlass();
                System.out.println("LiquidGlass transparency, bevel refraction, blur, GUI scaling and GL state passed.");
                return;
            }
            if (args.length>0 && args[0].equals("pit-media-array")) {
                mc.displayWidth=960;mc.displayHeight=540;
                testMusicCover();testArrayList(modules);testPitTextInput(modules,vibe);
                System.out.println("Music covers, ArrayList typography/alpha and Skeet/Futuristic PitBot input passed.");
                return;
            }
            mc.thePlayer = instance(FixturePlayer.class);
            NetHandlerPlayClient network = instance(NetHandlerPlayClient.class);
            set(EntityPlayerSP.class,mc.thePlayer,"sendQueue",network);
            Map<UUID,NetworkPlayerInfo> players = new HashMap<UUID,NetworkPlayerInfo>();
            set(NetHandlerPlayClient.class,network,"playerInfoMap",players);
            for (int i=0; i<101; i++) {
                UUID id = new UUID(0,i+1); String name = String.format(java.util.Locale.ROOT,"Player%03d",i);
                players.put(id,new NetworkPlayerInfo(new GameProfile(id,name)));
            }
            ChessModule chess = new ChessModule();
            for (int[] dimensions : new int[][]{{960,540},{400,300}}) {
                mc.displayWidth=dimensions[0]; mc.displayHeight=dimensions[1];
                MemeGameGui gui = new MemeGameGui(chess); gui.setWorldAndResolution(mc,dimensions[0],dimensions[1]);
                prepareHeads(players);
                render(gui,"lobby-"+dimensions[0]+".png");
                int x = integer(gui,"listRight")-5, bottom = integer(gui,"listBottom")-5;
                gui.mouseClicked(scaled(gui,x),scaled(gui,bottom),0);
                gui.mouseClickMove(scaled(gui,x),scaled(gui,bottom+1000),0,1); gui.mouseReleased(0,0,0);
                if (integer(gui,"playerScroll") < 1000) throw new AssertionError("Player list cannot reach the last row");
                prepareHeads(players);
                render(gui,"lobby-end-"+dimensions[0]+".png");
                // Select the last player in the scrolled viewport, exercising transformed mouse coordinates.
                int rowX = integer(gui,"listLeft")+30;
                gui.mouseClicked(scaled(gui,rowX),scaled(gui,bottom-16),0);
                if (!"Player100".equals(get(MemeGameGui.class,gui,"selectedPlayer"))) throw new AssertionError("Last player not clickable");
                set(MemeGameModule.class,chess,"pendingOpponent","Player100");
                render(gui,"invite-"+dimensions[0]+".png");
                x=integer(gui,"contentRight")-5;
                gui.mouseClicked(scaled(gui,x),scaled(gui,integer(gui,"listBottom")-40),0);
                gui.mouseClickMove(scaled(gui,x),scaled(gui,bottom+1000),0,1); gui.mouseReleased(0,0,0);
                render(gui,"invite-end-"+dimensions[0]+".png");
                set(MemeGameModule.class,chess,"pendingOpponent",null);
            }
            mc.displayWidth=960; mc.displayHeight=540;
            chess.startRobot(true);
            MemeGameGui gui = new MemeGameGui(chess); gui.setWorldAndResolution(mc,960,540); render(gui,"chess.png");
            MemeGameModule tic = new MemeGameModule("Tic", "", GameType.TIC_TAC_TOE, new TicTacToeState()){};
            tic.startRobot(true); tic.getGame().move("a3"); tic.getGame().move("b2");
            gui = new MemeGameGui(tic); gui.setWorldAndResolution(mc,960,540); render(gui,"tic-tac-toe.png");
            MemeGameModule four = new MemeGameModule("Four", "", GameType.CONNECT_FOUR, new ConnectFourState()){};
            four.startRobot(true); gui = new MemeGameGui(four); gui.setWorldAndResolution(mc,960,540);
            int x=integer(gui,"left")+24, y=integer(gui,"top")+45+240;
            gui.mouseClicked(x,y,0);
            if (((ConnectFourState)four.getGame()).get(5,0) != 'R') throw new AssertionError("Lower column click was ignored");
            render(gui,"connect-four.png");
            testImpacts();
            testHud(modules);
            testLiquidGlass();
            testMusicCover();
            testArrayList(modules);
            testPitTextInput(modules,vibe);
            testMoveRequests();
            System.out.println("Scaled lobby, 101 players, scroll input, board assets, expanded columns and all impact modes passed.");
        } finally { buffer.destroy(); }
    }

    @SuppressWarnings("unchecked") private static void prepareHeads(Map<UUID,NetworkPlayerInfo> players) throws Exception {
        // Offline fixture heads: never query external skin services from a rendering check.
        Map<UUID,Object> heads = (Map<UUID,Object>)get(SkinHeads.class,null,"HEADS");
        Class<?> headType = Class.forName("dev.vibe.ui.SkinHeads$Head");
        Constructor<?> ctor = headType.getDeclaredConstructor(ResourceLocation.class); ctor.setAccessible(true);
        for (UUID id : players.keySet()) {
            Object head = ctor.newInstance(new ResourceLocation("textures/entity/steve.png"));
            set(headType,head,"loading",true); heads.put(id,head);
        }
        // The real cache retains 64 entries; rendering the last viewport only needs its visible entries.
        ((java.util.concurrent.ThreadPoolExecutor)get(SkinHeads.class,null,"DOWNLOADS")).shutdownNow();
    }

    private static void testImpacts() throws Exception {
        TrajectoriesModule module = new TrajectoriesModule(); TrajectoriesRenderer renderer = new TrajectoriesRenderer();
        Method draw = TrajectoriesRenderer.class.getDeclaredMethod("drawImpact",TrajectoriesModule.class,net.minecraft.util.EnumFacing.class,double.class,double.class,double.class);
        draw.setAccessible(true);
        frame(960,540);
        net.minecraft.client.renderer.GlStateManager.disableTexture2D();
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(770,771,1,0);
        String[] modes={"Basic","Square","Cube","Circle","Dot","Cross"};
        for (int i=0; i<modes.length; i++) {
            module.getImpactMode().setValue(modes[i]); GL11.glPushMatrix(); GL11.glTranslatef(80+i*155,260,0); GL11.glScalef(130,130,130);
            draw.invoke(renderer,module,net.minecraft.util.EnumFacing.NORTH,0D,0D,0D); GL11.glPopMatrix();
        }
        save("impacts.png");
        if (GL11.glGetError()!=GL11.GL_NO_ERROR) throw new AssertionError("Impact GL error");
    }

    private static void testHud(ModuleManager modules) throws Exception {
        HudModule hud = new HudModule(); BlurModule blur = new BlurModule();
        set(Module.class,blur,"enabled",true);
        set(ModuleManager.class,modules,"modules",new ArrayList<Module>(Arrays.asList(hud,blur)));
        dev.vibe.hud.HudManager manager = new dev.vibe.hud.HudManager(OUTPUT.resolve("hud-fixture").toFile());
        Method enabled = manager.getClass().getDeclaredMethod("blurEnabled",String.class); enabled.setAccessible(true);
        hud.getMode().setValue("Skeet");
        for(String element:new String[]{"arraylist","scoreboard","stalker"})
            if (!(Boolean)enabled.invoke(manager,element)) throw new AssertionError("Skeet suppresses unthemed blur: "+element);
        if ((Boolean)enabled.invoke(manager,"watermark")) throw new AssertionError("Opaque Skeet surface received blur");
        frame(960,540);
        for(int y=0;y<540;y+=20)for(int x=0;x<960;x+=20)
            net.minecraft.client.gui.Gui.drawRect(x,y,x+20,y+20,((x+y)/20%2==0)?0xFF3D637D:0xFF273D55);
        Method surface=manager.getClass().getDeclaredMethod("drawHudSurface",HudModule.class,int.class,int.class,int.class,int.class); surface.setAccessible(true);
        int y=80;
        for(String mode:new String[]{"Vibe","Skeet","LiquidGlass"}) {
            hud.getMode().setValue(mode); surface.invoke(manager,hud,100,y,500,y+70);
            mc.fontRendererObj.drawStringWithShadow(mode,120,y+30,0xFFFFFFFF); y+=120;
        }
        save("hud-styles.png");
        if ((Boolean)get(KawaseBlur.class,null,"roundedCompositeUnavailable")) throw new AssertionError("Glass blur unavailable");
        if ((Boolean)enabled.invoke(manager,"scoreboard")) throw new AssertionError("LiquidGlass scoreboard received a second rectangular blur");
        // Compare actual edge energy with blur off/on while excluding the rim.
        hud.getLiquidGlassRefraction().setValue(0D);
        hud.getLiquidGlassOpacity().setValue(1D);
        hud.getLiquidGlassBlur().setEnabled(false);
        double sharp=glassEdgeEnergy(manager,hud,surface);
        hud.getLiquidGlassBlur().setEnabled(true);
        hud.getLiquidGlassBlurStrength().setValue(8D);
        double soft=glassEdgeEnergy(manager,hud,surface);
        if(sharp<1 || soft>=sharp*.75)throw new AssertionError("Glass blur did not soften edges: "+sharp+" -> "+soft);
        // At GUI scale 2 the sampled texture region must still match physical screen coordinates.
        mc.gameSettings.guiScale=2;
        frame(960,540);
        net.minecraft.client.gui.Gui.drawRect(0,0,480,540,0xFFDC2020);
        net.minecraft.client.gui.Gui.drawRect(480,0,960,540,0xFF2020DC);
        KawaseBlur.drawRoundedRegion(80,80,400,130,6,4,0);
        save("glass-scale2.png");
        BufferedImage result=ImageIO.read(OUTPUT.resolve("glass-scale2.png").toFile());
        if (((result.getRGB(680,210))&255)<150 || ((result.getRGB(300,210)>>16)&255)<150)
            throw new AssertionError("Scaled glass samples the wrong portion of the screen");
        mc.gameSettings.guiScale=1;
    }

    private static double glassEdgeEnergy(dev.vibe.hud.HudManager manager,HudModule hud,Method surface) throws Exception {
        frame(960,540);
        for(int y=0;y<540;y+=20)for(int x=0;x<960;x+=20)
            net.minecraft.client.gui.Gui.drawRect(x,y,x+20,y+20,((x+y)/20%2==0)?0xFF3D637D:0xFF273D55);
        surface.invoke(manager,hud,100,320,500,390);
        ByteBuffer pixels=BufferUtils.createByteBuffer(200*30*4);
        GL11.glReadPixels(220,180,200,30,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        double energy=0;
        for(int y=0;y<30;y++)for(int x=1;x<200;x++) {
            int at=(y*200+x)*4;
            int difference=(pixels.get(at)&255)-(pixels.get(at-4)&255);
            energy+=difference*difference;
        }
        return energy/(199*30);
    }

    private static void testLiquidGlass() throws Exception {
        try (dev.vibe.ui.effect.LiquidGlassRenderer glass = new dev.vibe.ui.effect.LiquidGlassRenderer()) {
            for (int scale : new int[]{1,2}) {
                mc.gameSettings.guiScale=scale;
                frame(960,540);
                net.minecraft.client.gui.Gui.drawRect(0,0,960,540,0xFF404040);
                if (!glass.draw(40,40,240,120,12,0,4,1)) throw new AssertionError("LiquidGlass shader failed");
                save("glass-geometry-scale"+scale+".png");
                BufferedImage solid=ImageIO.read(OUTPUT.resolve("glass-geometry-scale"+scale+".png").toFile());
                if (Math.abs((solid.getRGB(140*scale,80*scale)&255)-64)>2)
                    throw new AssertionError("Clear glass washes out the center at scale "+scale);
                if ((solid.getRGB(40*scale,40*scale)&0xFFFFFF)!=0x404040)
                    throw new AssertionError("Glass paints outside the rounded corner");
                if ((solid.getRGB(140*scale,40*scale)&255)<85)
                    throw new AssertionError("Glass has no polished top rim");
                if (GL11.glIsEnabled(GL11.GL_SCISSOR_TEST) || GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=0)
                    throw new AssertionError("Glass leaked shader/clipping state");

                // Tint makes the silhouette measurable independently of the light direction.
                frame(960,540);
                net.minecraft.client.gui.Gui.drawRect(0,0,960,540,0xFF808080);
                glass.draw(40,40,240,120,12,0,0,1,0xFFFF0000);
                save("glass-corners-scale"+scale+".png");
                BufferedImage corners=ImageIO.read(OUTPUT.resolve("glass-corners-scale"+scale+".png").toFile());
                for(int a=0;a<12;a++)for(int b=0;b<12;b++) {
                    double distance=Math.hypot(12-a-.5/scale,12-b-.5/scale)-12;
                    if(Math.abs(distance)<1.5)continue; // Ignore the antialias fringe.
                    int pixel=corners.getRGB((40+a)*scale,(40+b)*scale);
                    boolean tinted=((pixel>>16)&255)-(pixel&255)>2;
                    if(tinted!=(distance<0))throw new AssertionError("Glass corner is stretched at scale "+scale+": "+a+", "+b);
                }
                frame(960,540);
                net.minecraft.client.gui.Gui.drawRect(0,0,480,540,0xFFDC2020);
                net.minecraft.client.gui.Gui.drawRect(480,0,960,540,0xFF2020DC);
                glass.draw(100/scale,100/scale,800/scale,220/scale,12,0,4,1);
                save("glass-sampling-scale"+scale+".png");
                BufferedImage sampled=ImageIO.read(OUTPUT.resolve("glass-sampling-scale"+scale+".png").toFile());
                if((sampled.getRGB(300,160)&0xFFFFFF)!=0xDC2020 || (sampled.getRGB(680,160)&0xFFFFFF)!=0x2020DC)
                    throw new AssertionError("LiquidGlass samples the wrong screen region at scale "+scale);
            }
            mc.gameSettings.guiScale=1;
            BufferedImage[] refracted=new BufferedImage[2];
            for(int index=0;index<2;index++) {
                frame(960,540);
                for(int y=0;y<540;y+=4)net.minecraft.client.gui.Gui.drawRect(0,y,960,y+4,(y/4%2==0)?0xFFCCAA66:0xFF302010);
                glass.draw(80,80,420,200,12,0,index*6,1);
                save("glass-refraction-"+index+".png");
                refracted[index]=ImageIO.read(OUTPUT.resolve("glass-refraction-"+index+".png").toFile());
            }
            double center=0,edge=0;
            for(int x=110;x<390;x++)for(int y=80;y<200;y++) {
                int difference=Math.abs((refracted[0].getRGB(x,y)&255)-(refracted[1].getRGB(x,y)&255));
                if(y>=100&&y<180)center+=difference;
                else edge+=difference;
            }
            if(center>1 || edge/(280*40)<5)throw new AssertionError("Refraction must bend the rim and keep the center stable: "+center+", "+edge);

            // A textured, warm scene makes refraction visible without a running world.
            frame(960,540);
            for(int y=0;y<540;y++) {
                float t=y/540F;
                int color=RenderUtils.blend(0xFF443026,0xFFE9A562,t);
                net.minecraft.client.gui.Gui.drawRect(0,y,960,y+1,color);
            }
            mc.renderEngine.bindTexture(new ResourceLocation("textures/blocks/planks_oak.png"));
            net.minecraft.client.renderer.GlStateManager.color(1,1,1,1);
            for(int x=0;x<960;x+=96)net.minecraft.client.gui.Gui.drawModalRectWithCustomSizedTexture(x,350,0,0,96,190,48,48);
            for(int x:new int[]{170,420,710}) {
                mc.renderEngine.bindTexture(new ResourceLocation("textures/blocks/log_oak.png"));
                net.minecraft.client.gui.Gui.drawModalRectWithCustomSizedTexture(x,0,0,0,85,480,64,64);
            }
            glass.draw(22,20,110,50,10);glass.draw(124,20,194,50,10);glass.draw(208,20,300,50,10);
            glass.draw(22,76,256,210,12);
            glass.draw(22,244,256,424,12);
            glass.draw(625,410,930,500,12);
            mc.fontRendererObj.drawStringWithShadow("VIBE",36,31,0xFFCC44FF);
            mc.fontRendererObj.drawStringWithShadow("19:16",139,31,0xFFFFFFFF);
            mc.fontRendererObj.drawStringWithShadow("94 FPS",225,31,0xFFFFFFFF);
            mc.fontRendererObj.drawStringWithShadow("SESSION",38,92,0xFFCC44FF);
            mc.fontRendererObj.drawStringWithShadow("Kills                          0",38,127,0xFFFFFFFF);
            mc.fontRendererObj.drawStringWithShadow("K/D                          0.0",38,153,0xFFFFFFFF);
            mc.fontRendererObj.drawStringWithShadow("Playtime                      3m",38,179,0xFFFFFFFF);
            RenderUtils.roundedRect(28,254,250,280,5,0xFFBA00EF);
            int y=263;
            for(String category:new String[]{"COMBAT","MOVEMENT","VISUAL","PLAYER","WORLD"}) {
                mc.fontRendererObj.drawStringWithShadow(category,39,y,0xFFFFFFFF);y+=32;
            }
            mc.fontRendererObj.drawStringWithShadow("Player",690,427,0xFFFFFFFF);
            net.minecraft.client.gui.Gui.drawRect(690,447,910,452,0xAA163725);
            net.minecraft.client.gui.Gui.drawRect(690,447,840,452,0xFF63FF51);
            mc.fontRendererObj.drawStringWithShadow("10.7 HP",690,468,0xFFFFFFFF);
            save("liquid-glass-preview.png");
            if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new AssertionError("LiquidGlass GL error");
        } finally {mc.gameSettings.guiScale=1;}
    }

    private static void testMusicCover() throws Exception {
        MusicModule module=new MusicModule(); MusicHudRenderer renderer=new MusicHudRenderer();
        BufferedImage artwork=new BufferedImage(320,180,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<180;y++)for(int x=0;x<320;x++)artwork.setRGB(x,y,x<160?0xFFF04466:0xFF408AF0);
        module.radio.setEnabled(false);
        frame(960,540);
        renderer.draw(module,new dev.vibe.media.MediaTrack("Video with a thumbnail","Creator","Browser","Playing",true,false,2000,60000,artwork),100,100,false);
        save("music-thumbnail.png");
        if (!Boolean.TRUE.equals(get(MusicHudRenderer.class,renderer,"cachedThumbnail")))throw new AssertionError("System cover did not replace fallback");
        module.radio.setEnabled(true);
        frame(960,540);
        renderer.draw(module,new dev.vibe.media.MediaTrack("Radio station","","Radio","Playing",true,true,0,0,null),100,100,false);
        save("music-radio.png");
        if (Boolean.TRUE.equals(get(MusicHudRenderer.class,renderer,"cachedThumbnail")))throw new AssertionError("Radio retained video thumbnail");
        renderer.close();
    }

    private static void testArrayList(ModuleManager modules) throws Exception {
        HudModule hud=new HudModule();
        set(ModuleManager.class,modules,"modules",new ArrayList<Module>(Arrays.asList(hud)));
        Constructor<dev.vibe.hud.HudManager.HudElement> ctor=dev.vibe.hud.HudManager.HudElement.class
                .getDeclaredConstructor(String.class,int.class,int.class,boolean.class,boolean.class);
        ctor.setAccessible(true);
        dev.vibe.hud.HudManager.HudElement element=ctor.newInstance("arraylist",12,12,true,false);
        ArrayListRenderer renderer=new ArrayListRenderer();
        hud.array.font.setValue("Minecraft");hud.array.horizontal.setEnabled(true);
        hud.array.textGlow.setEnabled(true);hud.array.textGlowStrength.setValue(.05);
        hud.array.rowHeight.setValue(9D);hud.array.padding.setValue(0D);hud.array.outlineWidth.setValue(3D);
        hud.array.scale.setValue(2D);
        frame(960,540);
        renderer.draw(hud,element,new net.minecraft.client.gui.ScaledResolution(mc),true);
        save("arraylist.png");
        if(element.getHeight()<90)throw new AssertionError("ArrayList rows overlap outline/text at large scale");
        // Vanilla forces alpha 0..3 to 255. Faint glow must not make opaque glyph copies.
        Method text=ArrayListRenderer.class.getDeclaredMethod("text",String.class,float.class,float.class,int.class,dev.vibe.hud.ArrayListSettings.class);
        text.setAccessible(true);
        frame(960,540);GL11.glClearColor(0,0,0,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        text.invoke(renderer,"Inventory Manager",30F,30F,0x0200FFFF,hud.array);
        ByteBuffer pixels=BufferUtils.createByteBuffer(960*540*4);
        GL11.glReadPixels(0,0,960,540,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        for(int i=0;i<pixels.capacity();i+=4)
            if(pixels.get(i)!=0||pixels.get(i+1)!=0||pixels.get(i+2)!=0)throw new AssertionError("Faint text became opaque");
        if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new AssertionError("ArrayList GL error");
    }

    private static void testPitTextInput(ModuleManager modules,Vibe vibe) throws Exception {
        HypixelModule hypixel=new HypixelModule();ClickGuiModule theme=new ClickGuiModule();
        hypixel.getModes().setValue(new HashSet<String>(Arrays.asList(HypixelModule.PIT_BOT)));
        set(ModuleManager.class,modules,"modules",new ArrayList<Module>(Arrays.asList(hypixel,theme)));
        set(Vibe.class,vibe,"config",new dev.vibe.config.VibeConfig(OUTPUT.resolve("text-config").toFile()));
        // Synthetic key state keeps the check offscreen; GuiTextField uses the
        // vanilla modifier polling API even for ordinary character events.
        boolean keyboardCreated=org.lwjgl.input.Keyboard.isCreated();
        set(org.lwjgl.input.Keyboard.class,null,"created",true);
        try {
            for(String name:new String[]{"Skeet","Futuristic"}) {
                theme.getTheme().setValue(name);hypixel.getPitTarget().setValue("");
                VibeClickGui gui=new VibeClickGui();gui.setWorldAndResolution(mc,960,540);
                Method draw=VibeClickGui.class.getDeclaredMethod("drawSettingAt",dev.vibe.setting.Setting.class,int.class,int.class,int.class,int.class,boolean.class,boolean.class);
                draw.setAccessible(true);
                frame(960,540);
                draw.invoke(gui,hypixel.getPitTarget(),500,740,160,0xFF00CCFF,name.equals("Futuristic"),name.equals("Skeet"));
                gui.mouseClicked(625,168,0);
                for(char ch:"Player_123".toCharArray())gui.keyTyped(ch,0);
                if(!hypixel.getPitTarget().getValue().equals("Player_123"))throw new AssertionError(name+" cannot type PitBot name");
                gui.keyTyped('\0',org.lwjgl.input.Keyboard.KEY_LEFT);
                gui.keyTyped('\0',org.lwjgl.input.Keyboard.KEY_BACK);
                if(!hypixel.getPitTarget().getValue().equals("Player_13"))throw new AssertionError(name+" cursor/backspace failed");
                gui.keyTyped('\0',org.lwjgl.input.Keyboard.KEY_END);
                for(int i=0;i<30;i++)gui.keyTyped('x',0);
                if(hypixel.getPitTarget().getValue().length()!=16)throw new AssertionError(name+" ignores player-name limit");
                gui.keyTyped('\0',org.lwjgl.input.Keyboard.KEY_RETURN);
                if(get(VibeClickGui.class,gui,"editing")!=null)throw new AssertionError(name+" did not finish editing");
            }
        } finally { set(org.lwjgl.input.Keyboard.class,null,"created",keyboardCreated); }
    }

    private static void testMoveRequests() throws Exception {
        MemeGameModule[] games={new ChessModule(),
                new MemeGameModule("Tic","",GameType.TIC_TAC_TOE,new TicTacToeState()){},
                new MemeGameModule("Four","",GameType.CONNECT_FOUR,new ConnectFourState()){}};
        String[] moves={"e4","a3","1"};
        for(int i=0;i<games.length;i++) {
            MemeGameModule game=games[i]; game.getPreferences().setChatDelay(0); game.getPreferences().setCheckMessage(false);
            if(!game.request("Player100"))throw new AssertionError("Invitation failed");
            game.receiveChat("<Player100> @LocalPlayer yes lets play a game of "+game.getType().getDisplayName()+"!");
            if(game.requestMove())throw new AssertionError("Requested move on our turn");
            if(!game.play(moves[i])||!game.requestMove())throw new AssertionError("Request was not available on their turn");
            if(!"@Player100 please make your next move".equals(FixturePlayer.lastChat))throw new AssertionError("Incorrect reminder text");
            game.receiveChat("<Intruder> @LocalPlayer please make your next move");
            if(game.isMyTurn())throw new AssertionError("Unrelated sender rolled back a move");
            int sounds=FixturePlayer.sounds;
            game.receiveChat("<Player100> @LocalPlayer please make your next move");
            if(!game.isMyTurn()||FixturePlayer.sounds!=sounds+1)throw new AssertionError("Reminder failed to restore turn/play sound");
            if(!game.play(moves[i]))throw new AssertionError("Restored move cannot be replayed");
        }
    }
    private static void frame(int width,int height) throws Exception {
        Framebuffer target = mc.getFramebuffer();
        if (target==null || target.framebufferWidth!=width || target.framebufferHeight!=height) {
            if (target!=null) target.deleteFramebuffer(); target=new Framebuffer(width,height,true); set(Minecraft.class,mc,"framebufferMc",target);
        }
        target.bindFramebuffer(true); GL11.glDepthMask(true); GL11.glClearColor(.08F,.1F,.14F,1); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(0,width,height,0,-1000,1000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity(); GuiRenderState.prepare(false);
    }
    private static void render(GuiScreen gui,String file) throws Exception {
        frame(mc.displayWidth,mc.displayHeight); gui.drawScreen(-1,-1,0);
        if (GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) throw new AssertionError("GUI leaked clipping state");
        if (GL11.glGetError()!=GL11.GL_NO_ERROR) throw new AssertionError("GUI GL error: "+file);
        save(file);
    }
    private static void save(String file) throws Exception {
        int w=mc.displayWidth,h=mc.displayHeight; ByteBuffer pixels=BufferUtils.createByteBuffer(w*h*4);
        GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int at=((h-1-y)*w+x)*4;image.setRGB(x,y,0xFF000000|(pixels.get(at)&255)<<16|(pixels.get(at+1)&255)<<8|(pixels.get(at+2)&255));}
        ImageIO.write(image,"png",OUTPUT.resolve(file).toFile());
    }
    public static final class FixturePlayer extends EntityPlayerSP {
        static String lastChat;
        static int sounds;
        private FixturePlayer(){super(null,null,null,null);}
        @Override public String getName(){return "LocalPlayer";}
        @Override public void playSound(String sound,float volume,float pitch){sounds++;}
        @Override public void sendChatMessage(String message){lastChat=message;}
    }
}
