package dev.vibe.phone;

import dev.vibe.Vibe;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.meme.PhoneModule;
import dev.vibe.ui.GuiRenderState;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.*;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Real OpenGL and PNG round-trip; no user world, profile, or browser is opened. */
public final class PhoneRenderCheck {
    private static final Path OUTPUT = Paths.get("build/phone-render-check");
    private static Object unsafe;
    private static Method allocate;
    private static Minecraft mc;
    private static Framebuffer target;
    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT);
        Pbuffer buffer = new Pbuffer(1100, 600, new PixelFormat(8, 24, 8), null, null); buffer.makeCurrent();
        try {
            Class<?> type = Class.forName("sun.misc.Unsafe"); Field f = type.getDeclaredField("theUnsafe"); f.setAccessible(true);
            unsafe = f.get(null); allocate = type.getMethod("allocateInstance", Class.class);
            mc = create(Minecraft.class); set(Minecraft.class, null, "theMinecraft", mc);
            mc.gameSettings = new GameSettings(); mc.gameSettings.guiScale = 1;
            mc.displayWidth = 1100; mc.displayHeight = 600;
            set(Minecraft.class, mc, "mcDataDir", OUTPUT.resolve("fixture-profile").toFile());
            mc.thePlayer = create(Player.class);
            IMetadataSerializer metadata = new IMetadataSerializer();
            metadata.registerMetadataSectionType(new TextureMetadataSectionSerializer(), TextureMetadataSection.class);
            metadata.registerMetadataSectionType(new FontMetadataSectionSerializer(), FontMetadataSection.class);
            set(Minecraft.class, mc, "mcLanguageManager", new net.minecraft.client.resources.LanguageManager(metadata, "en_US"));
            SimpleReloadableResourceManager resources = new SimpleReloadableResourceManager(metadata);
            resources.reloadResourcePack(new DefaultResourcePack(Collections.<String, File>emptyMap()));
            resources.reloadResourcePack(new FolderResourcePack(new File("src/main/resources")));
            set(Minecraft.class, mc, "mcResourceManager", resources); mc.renderEngine = new TextureManager(resources);
            OpenGlHelper.initializeTextures();
            Vibe vibe = new Vibe(); set(Vibe.class, null, "instance", vibe);
            ModuleManager manager = create(ModuleManager.class); set(ModuleManager.class, manager, "modules", new ArrayList<Module>());
            set(Vibe.class, vibe, "moduleManager", manager);
            target = new Framebuffer(1100, 600, true); set(Minecraft.class, mc, "framebufferMc", target);
            PhoneModule phone = new PhoneModule(); phone.setEnabled(true);
            set(PhoneModule.class, phone, "callAudio", new CallFixture());
            set(PhoneModule.class, phone, "openedAt", System.currentTimeMillis() - 2000);
            PhoneRenderer renderer = new PhoneRenderer();
            try {
                frame();
                Gui.drawRect(0, 0, 1100, 300, 0xFFBC4359); Gui.drawRect(0, 300, 1100, 600, 0xFF3480B0);
                phone.open(PhoneModule.App.CAMERA); phone.act(PhoneModule.Action.SELECT);
                renderer.captureFramebuffer(phone); phone.photos().awaitIdle();
                if (phone.photos().photos().isEmpty()) throw new AssertionError("Camera did not save a PNG");
                BufferedImage capture = ImageIO.read(phone.photos().photos().get(0).file.toFile());
                if (capture.getWidth() != 1100 || capture.getHeight() != 600 || capture.getRGB(20, 20) != 0xFFBC4359
                        || capture.getRGB(20, 580) != 0xFF3480B0) throw new AssertionError("Camera color/orientation/framebuffer mismatch");
                phone.advance(0);
                set(PhoneModule.class, phone, "notificationUntil", 0L);
                set(PhoneModule.class, phone, "interactionAt", 0L);
                frame();
                ByteBuffer beforeHand = pixels();
                Method hand = PhoneRenderer.class.getDeclaredMethod("hand", boolean.class); hand.setAccessible(true);
                net.minecraft.client.renderer.GlStateManager.pushMatrix(); net.minecraft.client.renderer.GlStateManager.translate(65, 42, 0);
                net.minecraft.client.renderer.GlStateManager.enableCull();
                hand.invoke(renderer, false); net.minecraft.client.renderer.GlStateManager.popMatrix();
                ByteBuffer afterHand = pixels();
                int changed = 0;
                for (int i = 0; i < afterHand.capacity(); i += 4) if (beforeHand.get(i) != afterHand.get(i)
                        || beforeHand.get(i + 1) != afterHand.get(i + 1) || beforeHand.get(i + 2) != afterHand.get(i + 2)) changed++;
                if (changed < 500) throw new AssertionError("Skin hand did not render: " + changed + " pixels");
                save("hand-isolated.png");
                verifyNoShadow(renderer, phone);
                for (PhoneModule.App app : PhoneModule.App.values()) {
                    phone.open(app); phone.advance(0);
                    set(PhoneModule.class, phone, "appAt", System.currentTimeMillis() - 2000);
                    if (app == PhoneModule.App.PHOTOS) {
                        phone.photos().awaitIdle(); phone.photos().image(phone.photos().photos().get(0)); phone.photos().awaitIdle();
                    }
                    if (app == PhoneModule.App.MESSAGES) phone.conversation().sendPreset(3);
                    frame(); draw(renderer, phone, 1); save(app.name().toLowerCase(java.util.Locale.ROOT) + ".png");
                    if (app == PhoneModule.App.HOME) savePortrait();
                    check();
                }
                phone.open(PhoneModule.App.CONTACTS); phone.act(PhoneModule.Action.SELECT);
                set(PhoneModule.class, phone, "appAt", System.currentTimeMillis() - 2000);
                frame(); draw(renderer, phone, 1); save("contact-details.png"); check();
                phone.act(PhoneModule.Action.SELECT);
                set(PhoneModule.class, phone, "appAt", System.currentTimeMillis() - 2000);
                frame(); draw(renderer, phone, 1); save("calling.png"); check();
                verifyThumb(renderer, phone);
                verifyFlappyClipping(renderer, phone);
                verifyReplies(renderer, phone);
                phone.open(PhoneModule.App.PHOTOS); phone.act(PhoneModule.Action.DELETE);
                frame(); draw(renderer, phone, 1); save("delete-confirmation.png"); check();
                phone.open(PhoneModule.App.HOME); frame(); draw(renderer, phone, .67F); save("compact.png"); check();
                frame(); drawTilted(renderer, phone, .67F, -17); save("tilted-compact.png"); check();
                verifyCoverage(renderer, phone);
                Framebuffer large = new Framebuffer(700, 1000, true);
                try {
                    large.bindFramebuffer(true); GL11.glDepthMask(true); GL11.glClearColor(.04F, .065F, .105F, 1);
                    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
                    GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(0, 700, 1000, 0, -1000, 1000);
                    GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity(); GuiRenderState.prepare(false);
                    drawTilted(renderer, phone, 1.75F, -3.5F); save("tilted-large.png", 700, 1000); check();
                } finally { target.bindFramebuffer(true); large.deleteFramebuffer(); }
                Player.slim = true; frame(); draw(renderer, phone, 1); save("slim-skin.png"); check(); Player.slim = false;
                set(PhoneModule.class, phone, "openedAt", System.currentTimeMillis() - 350);
                frame(); draw(renderer, phone, 1); save("boot.png"); check();
                System.out.println("VibeOS: all apps, contacts, calling, message attachment, Flappy clipping, skin hand, boot, tilted views, deletion dialog, GL state and camera PNG round-trip passed.");
            } finally { renderer.close(); phone.stopCommunications(); phone.photos().close(); }
        } finally { if (target != null) target.deleteFramebuffer(); buffer.destroy(); }
    }
    private static void draw(PhoneRenderer renderer, PhoneModule phone, float scale) {
        drawTilted(renderer, phone, scale, 0);
    }
    private static void drawTilted(PhoneRenderer renderer, PhoneModule phone, float scale, float angle) {
        drawTilted(renderer, phone, scale, angle, true);
    }
    private static void drawTilted(PhoneRenderer renderer, PhoneModule phone, float scale, float angle, boolean hand) {
        int destination = GL11.glGetInteger(0x8CA6);
        GlStateManagerBridge.push(65, 42, scale);
        try { GL11.glRotatef(angle, 0, 0, 1); renderer.drawDevice(phone, hand); }
        finally { net.minecraft.client.renderer.GlStateManager.popMatrix(); }
        if (GL11.glGetInteger(0x8CA6) != destination)
            throw new AssertionError("Phone lost the caller's framebuffer");
    }
    private static void verifyCoverage(PhoneRenderer renderer, PhoneModule phone) throws Exception {
        Method direct = PhoneRenderer.class.getDeclaredMethod("drawContents", PhoneModule.class, boolean.class); direct.setAccessible(true);
        int[] coverage = new int[2];
        for (int mode = 0; mode < 2; mode++) {
            frame(); GL11.glClearColor(0, 0, 0, 0); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            GlStateManagerBridge.push(65, 42, 1); GL11.glRotatef(-7.5F, 0, 0, 1);
            try {
                if (mode == 0) direct.invoke(renderer, phone, false); else renderer.drawDevice(phone, false);
            } finally { net.minecraft.client.renderer.GlStateManager.popMatrix(); }
            ByteBuffer image = pixels();
            for (int y = 150; y < 350; y++) {
                int edge = 0;
                for (int x = 200; x < 420; x++) if ((image.get(((599 - y) * 1100 + x) * 4 + 3) & 255) >= 128) edge = x;
                for (int x = Math.max(200, edge - 1); x <= edge + 2; x++) {
                    int alpha = image.get(((599 - y) * 1100 + x) * 4 + 3) & 255;
                    if (alpha > 80 && alpha < 240) { coverage[mode]++; break; }
                }
            }
            check();
            if (GL11.glGetInteger(0x8CA6) != target.framebufferObject) throw new AssertionError("Framebuffer was not restored");
            java.nio.IntBuffer viewport = BufferUtils.createIntBuffer(16); GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
            if (viewport.get(0) != 0 || viewport.get(1) != 0 || viewport.get(2) != 1100 || viewport.get(3) != 600)
                throw new AssertionError("Viewport was not restored");
        }
        if (coverage[1] < 120 || coverage[1] < coverage[0] + 80)
            throw new AssertionError("Tilted edges are not filtered: direct=" + coverage[0] + ", filtered=" + coverage[1]);
        System.out.println("Tilted edge coverage: " + coverage[0] + " direct rows, " + coverage[1] + " filtered rows.");
    }
    private static void verifyFlappyClipping(PhoneRenderer renderer, PhoneModule phone) throws Exception {
        phone.open(PhoneModule.App.FLAPPY);
        set(PhoneModule.class, phone, "appAt", System.currentTimeMillis() - 2000);
        FlappyGame game = phone.game();
        float[] positions = new float[game.getPipes().size()];
        for (int i = 0; i < positions.length; i++) { positions[i] = game.getPipes().get(i).x; game.getPipes().get(i).x = 500; }
        float previousY = game.getBirdY(); FlappyGame.Phase previousPhase = game.getPhase();
        try {
            set(FlappyGame.class, game, "phase", FlappyGame.Phase.PLAYING);
            frame(); drawTilted(renderer, phone, 1, 0, false); ByteBuffer baseline = pixels();
            game.getPipes().get(0).x = -12; game.getPipes().get(1).x = 180;
            for (float birdY : new float[]{-10, FlappyGame.HEIGHT + 8}) {
                set(FlappyGame.class, game, "birdY", birdY);
                frame(); drawTilted(renderer, phone, 1, 0, false); ByteBuffer image = pixels();
                int outside = 0;
                for (int y = 120; y < 420; y++) for (int x = 60; x < 310; x++) {
                    // Allow the final antialias filter's footprint at the playfield edge.
                    if (x >= 78 && x <= 284 && y >= 146 && y <= 415) continue;
                    int at = ((599 - y) * 1100 + x) * 4;
                    for (int channel = 0; channel < 3; channel++)
                        if (Math.abs((baseline.get(at + channel) & 255) - (image.get(at + channel) & 255)) > 3) { outside++; break; }
                }
                if (outside != 0) throw new AssertionError("Flappy drew outside its playfield: " + outside + " pixels");
                check();
            }
            set(FlappyGame.class, game, "birdY", previousY);
            frame(); drawTilted(renderer, phone, 1, -3.5F); save("flappy-boundaries.png"); check();
            System.out.println("Flappy playfield: partial left/right pipes and top/bottom bird stay clipped.");
        } finally {
            for (int i = 0; i < positions.length; i++) game.getPipes().get(i).x = positions[i];
            set(FlappyGame.class, game, "birdY", previousY); set(FlappyGame.class, game, "phase", previousPhase);
        }
    }
    private static void verifyNoShadow(PhoneRenderer renderer, PhoneModule phone) throws Exception {
        frame(); GL11.glClearColor(0, 0, 0, 0); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        drawTilted(renderer, phone, 1, 0, false);
        ByteBuffer image = pixels();
        for (int y = 130; y < 420; y++) if ((image.get(((599 - y) * 1100 + 59) * 4 + 3) & 255) > 4)
            throw new AssertionError("Phone still draws its offset background shadow");
        check();
    }
    private static void verifyThumb(PhoneRenderer renderer, PhoneModule phone) throws Exception {
        Field field = PhoneRenderer.class.getDeclaredField("handPose"); field.setAccessible(true);
        PhoneHandPose pose = (PhoneHandPose) field.get(renderer);
        phone.open(PhoneModule.App.HOME); set(PhoneModule.class, phone, "appAt", System.currentTimeMillis() - 2000);
        set(PhoneModule.class, phone, "interactionAt", 0L); pose.reset();
        frame(); draw(renderer, phone, 1); ByteBuffer shown = pixels(); save("thumb-rest.png");
        phone.hideThumb.setEnabled(true); pose.reset();
        frame(); draw(renderer, phone, 1); ByteBuffer hidden = pixels(); save("thumb-hidden.png");
        int changed = 0;
        for (int y = 300; y < 425; y++) for (int x = 60; x < 145; x++) {
            int at = ((599 - y) * 1100 + x) * 4;
            for (int c = 0; c < 3; c++) if (Math.abs((shown.get(at + c) & 255) - (hidden.get(at + c) & 255)) > 8) { changed++; break; }
        }
        if (changed < 100) throw new AssertionError("Hide Thumb did not remove the visible thumb: " + changed);
        phone.hideThumb.setEnabled(false); phone.act(PhoneModule.Action.RIGHT);
        set(PhoneModule.class, phone, "interactionAt", System.currentTimeMillis() - 120);
        for (int i = 0; i < 12; i++) pose.advance(116, 205, .12, false, .05);
        frame(); draw(renderer, phone, 1); save("thumb-navigation.png"); check();
        phone.act(PhoneModule.Action.SELECT); set(PhoneModule.class, phone, "appAt", System.currentTimeMillis() - 2000);
        set(PhoneModule.class, phone, "interactionAt", System.currentTimeMillis() - 120);
        for (int i = 0; i < 12; i++) pose.advance(116, 377, .12, true, .05);
        frame(); draw(renderer, phone, 1); save("thumb-tap.png"); check();
        phone.open(PhoneModule.App.SETTINGS);
        for (int i = 0; i < 8; i++) phone.act(PhoneModule.Action.DOWN);
        phone.act(PhoneModule.Action.SELECT); phone.hideThumb.setEnabled(true);
        set(PhoneModule.class, phone, "appAt", System.currentTimeMillis() - 2000);
        frame(); draw(renderer, phone, 1); save("settings-rebind.png"); check();
        phone.hideThumb.setEnabled(false); phone.cancelBinding();
        set(PhoneModule.class, phone, "notificationUntil", 0L); set(PhoneModule.class, phone, "interactionAt", 0L); pose.reset();
        System.out.println("Phone shadow removed; visible/hidden thumb pixels, navigation, tap and boxed rebinding controls passed.");
    }
    private static void verifyReplies(PhoneRenderer renderer, PhoneModule phone) throws Exception {
        phone.stopCommunications();
        Path path = OUTPUT.resolve("fixture-profile/reply-preview.json"); Files.deleteIfExists(path);
        java.util.concurrent.atomic.AtomicLong time = new java.util.concurrent.atomic.AtomicLong(1000);
        PhoneConversation conversation = new PhoneConversation(path, time::get, new Random(17));
        set(PhoneModule.class, phone, "conversation", conversation);
        phone.open(PhoneModule.App.MESSAGES); conversation.sendPreset(2); time.addAndGet(1600);
        set(PhoneModule.class, phone, "appAt", System.currentTimeMillis() - 2000);
        frame(); draw(renderer, phone, 1); ByteBuffer first = pixels(); save("messages-typing.png");
        time.addAndGet(180);
        frame(); draw(renderer, phone, 1); ByteBuffer second = pixels(); save("messages-typing-next.png");
        int changed = 0;
        for (int y = 365; y < 398; y++) for (int x = 110; x < 175; x++) {
            int at = ((599 - y) * 1100 + x) * 4;
            for (int c = 0; c < 3; c++) if (Math.abs((first.get(at + c) & 255) - (second.get(at + c) & 255)) > 8) { changed++; break; }
        }
        if (changed < 15) throw new AssertionError("Typing dots did not animate: " + changed);
        time.addAndGet(10000); phone.updateCommunications(false);
        if (phone.unreadMessages() != 1 || !conversation.messages().get(1).incoming) throw new AssertionError("Missing incoming reply");
        set(PhoneModule.class, phone, "notificationUntil", 0L);
        frame(); draw(renderer, phone, 1); save("messages-reply.png"); check();
        phone.open(PhoneModule.App.HOME); set(PhoneModule.class, phone, "appAt", System.currentTimeMillis() - 2000);
        frame(); draw(renderer, phone, 1); save("messages-unread.png"); check();
        phone.open(PhoneModule.App.MESSAGES); if (phone.unreadMessages() != 0) throw new AssertionError("Messages stayed unread after opening");
        phone.open(PhoneModule.App.TELEPHONE); phone.act(PhoneModule.Action.SELECT);
        Field audio = PhoneModule.class.getDeclaredField("callAudio"); audio.setAccessible(true);
        ((CallFixture) audio.get(phone)).finished = true; phone.updateCommunications(false);
        if (phone.isCalling() || !phone.hasCallEnded()) throw new AssertionError("Call did not end after playback");
        set(PhoneModule.class, phone, "appAt", System.currentTimeMillis() - 2000);
        set(PhoneModule.class, phone, "notificationUntil", 0L);
        frame(); draw(renderer, phone, 1); save("call-ended.png"); check();
        phone.act(PhoneModule.Action.SELECT); if (!phone.isCalling()) throw new AssertionError("Cannot call again");
        phone.stopCommunications(); set(PhoneModule.class, phone, "interactionAt", 0L);
        System.out.println("Phone replies: animated typing pixels, two-way bubbles, unread badge/read state, ended call and redial passed.");
    }
    private static final class CallFixture implements PhoneCallPlayer {
        boolean finished;
        @Override public void start(float volume) { finished = false; }
        @Override public void setVolume(float volume) { }
        @Override public boolean isFinished() { return finished; }
        @Override public String error() { return ""; }
        @Override public void close() { }
    }
    private static void frame() {
        target.bindFramebuffer(true); GL11.glDepthMask(true); GL11.glClearColor(.04F, .065F, .105F, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(0, 1100, 600, 0, -1000, 1000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity(); GuiRenderState.prepare(false);
        for (int x = 0; x < 1100; x += 40) Gui.drawRect(x, 0, x + 1, 600, 0x101D375A);
        for (int y = 0; y < 600; y += 40) Gui.drawRect(0, y, 1100, y + 1, 0x101D375A);
    }
    private static void check() {
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR) throw new AssertionError("Phone GL error " + error);
        if (GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != 0 || GL11.glIsEnabled(GL11.GL_SCISSOR_TEST))
            throw new AssertionError("Phone leaked a shader or clipping state");
        for (int i = 0; i < 4; i++) if (GL11.glIsEnabled(GL11.GL_CLIP_PLANE0 + i))
            throw new AssertionError("Phone leaked a clip plane");
    }
    private static void save(String name) throws Exception {
        save(name, 1100, 600);
    }
    private static void save(String name, int width, int height) throws Exception {
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int at = ((height - y - 1) * width + x) * 4;
            image.setRGB(x, y, 0xFF000000 | (pixels.get(at) & 255) << 16 | (pixels.get(at + 1) & 255) << 8 | pixels.get(at + 2) & 255);
        }
        ImageIO.write(image, "png", OUTPUT.resolve(name).toFile());
    }
    private static ByteBuffer pixels() {
        ByteBuffer pixels = BufferUtils.createByteBuffer(1100 * 600 * 4);
        GL11.glReadPixels(0, 0, 1100, 600, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        return pixels;
    }
    private static void savePortrait() throws Exception {
        ByteBuffer pixels = BufferUtils.createByteBuffer(340 * 600 * 4);
        GL11.glReadPixels(0, 0, 340, 600, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(340, 600, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 600; y++) for (int x = 0; x < 340; x++) {
            int at = ((599 - y) * 340 + x) * 4;
            image.setRGB(x, y, 0xFF000000 | (pixels.get(at) & 255) << 16 | (pixels.get(at + 1) & 255) << 8 | pixels.get(at + 2) & 255);
        }
        ImageIO.write(image, "png", OUTPUT.resolve("preview.png").toFile());
    }
    private static <T> T create(Class<T> type) throws Exception { return type.cast(allocate.invoke(unsafe, type)); }
    private static void set(Class<?> type, Object object, String name, Object value) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(object, value); }
    public static final class Player extends EntityPlayerSP {
        static boolean slim;
        private Player() { super(null, null, null, null); }
        @Override public ResourceLocation getLocationSkin() { return new ResourceLocation(slim ? "textures/entity/alex.png" : "textures/entity/steve.png"); }
        @Override public String getSkinType() { return slim ? "slim" : "default"; }
        @Override public boolean isWearing(EnumPlayerModelParts part) { return true; }
        @Override public void playSound(String name, float volume, float pitch) { }
    }
    private static final class GlStateManagerBridge {
        static void push(float x, float y, float scale) {
            net.minecraft.client.renderer.GlStateManager.pushMatrix();
            net.minecraft.client.renderer.GlStateManager.translate(x, y, 0);
            net.minecraft.client.renderer.GlStateManager.scale(scale, scale, scale);
        }
    }
}
