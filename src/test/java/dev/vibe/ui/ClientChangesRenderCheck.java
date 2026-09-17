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
            testMusicCover();
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
        dev.vibe.hud.HudManager manager = instance(dev.vibe.hud.HudManager.class);
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
        BufferedImage result=ImageIO.read(OUTPUT.resolve("hud-styles.png").toFile());
        int minimum=255,maximum=0;
        for(int row=335;row<378;row++)for(int col=220;col<450;col++) {
            int value=(result.getRGB(col,row)>>16)&255; minimum=Math.min(minimum,value); maximum=Math.max(maximum,value);
        }
        if(maximum-minimum>10)throw new AssertionError("Glass copy is transparent or unblurred: "+(maximum-minimum));
        // At GUI scale 2 the sampled texture region must still match physical screen coordinates.
        mc.gameSettings.guiScale=2;
        frame(960,540);
        net.minecraft.client.gui.Gui.drawRect(0,0,480,540,0xFFDC2020);
        net.minecraft.client.gui.Gui.drawRect(480,0,960,540,0xFF2020DC);
        KawaseBlur.drawRoundedRegion(80,80,400,130,6,4,0);
        save("glass-scale2.png");
        result=ImageIO.read(OUTPUT.resolve("glass-scale2.png").toFile());
        if (((result.getRGB(680,210))&255)<150 || ((result.getRGB(300,210)>>16)&255)<150)
            throw new AssertionError("Scaled glass samples the wrong portion of the screen");
        mc.gameSettings.guiScale=1;
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
