package dev.vibe.ui.render;

import dev.vibe.Vibe;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.meme.PhoneModule;
import dev.vibe.module.impl.visual.ItemGlowModule;
import dev.vibe.phone.PhoneRenderer;
import dev.vibe.ui.GuiRenderState;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.*;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.item.*;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Real item texture, VibeOS device/skin, framebuffer depth, alpha blending and timing on the local GPU. */
public final class ItemGlowRenderCheck {
    private static final Path OUT = Paths.get("build/item-glow-check");
    private static final int W = 1280, H = 720;
    private static Minecraft mc;
    private static Framebuffer target;
    private static ItemGlowModule glow;
    private static PhoneModule phone;
    private static PhoneRenderer device;
    private static int swordDraws;
    private static float phoneAngle = -3.5F;
    private static final ItemStack HELD = new ItemStack(new Item());

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUT);
        Pbuffer context = new Pbuffer(W, H, new PixelFormat(8, 24, 8), null, null); context.makeCurrent();
        try {
            setup();
            glow.setEnabled(false); frame(); scene(); int[] original = pixels(); float originalDepth = depth(1180, 270); save("original.png", original);
            if (originalDepth >= .99F) throw new AssertionError("The test sword did not write depth");
            glow.setEnabled(true); glow.fade.setEnabled(false);
            frame(); int before = swordDraws; scene(); int[] lit = pixels(); save("glow.png", lit); check();
            if (swordDraws != before + 1) throw new AssertionError("Held geometry was redrawn to generate the mask");
            if (Math.abs(originalDepth - depth(1180, 270)) > .000001) throw new AssertionError("ItemGlow changed the real depth buffer");
            verifyPixels(original, lit);
            Object renderer = field(ItemGlowRenderer.class, "INSTANCE").get(null);
            if (field(ItemGlowRenderer.class, "failed").getBoolean(renderer)) throw new AssertionError("ItemGlow silently used its fallback");
            int allocation = field(ItemGlowRenderer.class, "allocations").getInt(renderer);
            Object phoneEffect = field(PhoneItemGlow.class, "INSTANCE").get(null);
            if (field(PhoneItemGlow.class, "failed").getBoolean(phoneEffect)) throw new AssertionError("Phone glow silently used its fallback");
            int phoneAllocation = field(PhoneItemGlow.class, "allocations").getInt(phoneEffect);
            frame(); scene();
            if (allocation != field(ItemGlowRenderer.class, "allocations").getInt(renderer)) throw new AssertionError("GPU buffers reallocated on an unchanged frame");
            if (phoneAllocation != field(PhoneItemGlow.class, "allocations").getInt(phoneEffect)) throw new AssertionError("Phone buffers reallocated on an unchanged frame");
            // A zero-alpha chosen color must leave both the scene and translucent source pixels unchanged.
            glow.colorMode.setValue("Static"); glow.color.setValue(0x0073DCCF);
            frame(); scene(); int[] invisible = pixels();
            for (int i = 0; i < original.length; i++) if (difference(original[i], invisible[i]) > 1) throw new AssertionError("Color alpha was ignored");
            glow.color.setValue(0xFF73DCCF); glow.colorMode.setValue("Gradient"); glow.radius.setValue(24D);
            frame(); scene(); save("wide-halo.png", pixels()); check();
            for (String quality : Arrays.asList("Performance", "Balanced", "High")) {
                glow.quality.setValue(quality); frame(); scene(); check();
                if (field(ItemGlowRenderer.class, "blurWidth").getInt(renderer) > glow.blurLimit()) throw new AssertionError("Blur resolution exceeded its quality cap");
            }
            glow.radius.setValue(10D); glow.quality.setValue("Balanced");
            // The phone uses the same effect with its camera screen and clipping, not a replacement device.
            phone.open(PhoneModule.App.CAMERA); frame(); device.captureFramebuffer(phone); drawPhone(); check();
            phone.open(PhoneModule.App.FLAPPY); frame(); drawPhone(); save("flappy-phone.png", pixels()); check();
            phone.open(PhoneModule.App.HOME);
            glow.phone.setEnabled(false); frame(); scene(); save("held-only.png", pixels());
            glow.phone.setEnabled(true); glow.heldItem.setEnabled(false); frame(); scene();
            int[] phoneOnly = pixels(); save("phone-only.png", phoneOnly);
            int phoneHalo = 0;
            for (int y = 0; y < H; y++) for (int x = 0; x < 420; x++)
                if (difference(original[y * W + x], phoneOnly[y * W + x]) > 3) phoneHalo++;
            if (phoneHalo < 500) throw new AssertionError("The Phone did not receive its own halo");
            for (float angle : new float[]{-17, 12}) {
                phoneAngle = angle; glow.phone.setEnabled(false); frame(); scene(); int[] plain = pixels();
                glow.phone.setEnabled(true); frame(); scene(); verifyPixels(plain, pixels()); check();
            }
            phoneAngle = -3.5F;
            glow.heldItem.setEnabled(true);
            Framebuffer smaller = new Framebuffer(960, 540, true);
            try {
                target = smaller; mc.displayWidth = 960; mc.displayHeight = 540; field(Minecraft.class, "framebufferMc").set(mc, smaller);
                frame(); scene(); check();
                if (field(ItemGlowRenderer.class, "width").getInt(renderer) != 960) throw new AssertionError("Resize did not refresh the capture target");
            } finally { smaller.deleteFramebuffer(); target = mainTarget; mc.displayWidth = W; mc.displayHeight = H; field(Minecraft.class, "framebufferMc").set(mc, target); }
            frame(); scene(); check();
            verifyInnerModes(renderer,phoneEffect);
            String timing = benchmark(); Files.write(OUT.resolve("timings.txt"), timing.getBytes("UTF-8"));
            glow.setEnabled(false); frame(); scene(); check();
            if (field(ItemGlowRenderer.class, "capture").getInt(renderer) != 0) throw new AssertionError("Disabling the module did not free GPU targets");
            if (field(PhoneItemGlow.class, "horizontal").getInt(phoneEffect) != 0) throw new AssertionError("Disabling did not free the Phone glow targets");
            System.out.println("ItemGlow: single geometry draw, preserved scene/item/translucency/depth, real Phone and skin, camera/Flappy, source toggles, alpha, quality/resize, buffer reuse and cleanup passed. " + timing);
        } finally {
            ItemGlowRenderer.closeActive();
            if (device != null) device.close(); if (phone != null) phone.photos().close();
            if (mainTarget != null) mainTarget.deleteFramebuffer(); context.destroy();
        }
    }

    private static Framebuffer mainTarget;
    private static void setup() throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe"); Object unsafe = field(unsafeType, "theUnsafe").get(null);
        Method allocate = unsafeType.getMethod("allocateInstance", Class.class);
        mc = (Minecraft) allocate.invoke(unsafe, Minecraft.class); field(Minecraft.class, "theMinecraft").set(null, mc);
        mc.displayWidth = W; mc.displayHeight = H; mc.gameSettings = new GameSettings(); mc.gameSettings.guiScale = 2;
        mc.thePlayer = (Player) allocate.invoke(unsafe, Player.class);
        field(Minecraft.class, "mcDataDir").set(mc, OUT.resolve("fixture").toFile());
        IMetadataSerializer metadata = new IMetadataSerializer();
        metadata.registerMetadataSectionType(new TextureMetadataSectionSerializer(), TextureMetadataSection.class);
        field(Minecraft.class, "mcLanguageManager").set(mc, new net.minecraft.client.resources.LanguageManager(metadata, "en_US"));
        SimpleReloadableResourceManager resources = new SimpleReloadableResourceManager(metadata);
        resources.reloadResourcePack(new DefaultResourcePack(Collections.<String, File>emptyMap()));
        resources.reloadResourcePack(new FolderResourcePack(new File("src/main/resources")));
        field(Minecraft.class, "mcResourceManager").set(mc, resources); mc.renderEngine = new TextureManager(resources);
        // Fail if the real sprite is unavailable instead of silently testing the missing-texture square.
        resources.getResource(new ResourceLocation("textures/items/diamond_sword.png")).getInputStream().close();
        OpenGlHelper.initializeTextures(); target = mainTarget = new Framebuffer(W, H, true);
        field(Minecraft.class, "framebufferMc").set(mc, target);
        Vibe vibe = new Vibe(); field(Vibe.class, "instance").set(null, vibe);
        ModuleManager manager = (ModuleManager) allocate.invoke(unsafe, ModuleManager.class);
        field(ModuleManager.class, "modules").set(manager, new ArrayList<Module>()); field(Vibe.class, "moduleManager").set(vibe, manager);
        glow = new ItemGlowModule(); manager.registerDynamic(glow);
        phone = new PhoneModule(); manager.registerDynamic(phone); phone.setEnabled(true);
        field(PhoneModule.class, "openedAt").setLong(phone, System.currentTimeMillis() - 4000);
        field(PhoneModule.class, "appAt").setLong(phone, System.currentTimeMillis() - 4000);
        device = new PhoneRenderer();
        // Matched pixel comparisons must not straddle a real clock/date rollover.
        field(PhoneRenderer.class, "wallClock").set(device, (java.util.function.LongSupplier) () -> 1792872000000L);
    }
    private static void frame() {
        target.bindFramebuffer(true); GlStateManager.depthMask(true); GlStateManager.clearColor(.10F, .14F, .18F, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity();
        GL11.glOrtho(0, mc.displayWidth / 2D, mc.displayHeight / 2D, 0, -1000, 1000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity(); GuiRenderState.prepare(false);
        // A pre-existing HUD chip must not acquire an item halo.
        Gui.drawRect(260, 20, 350, 31, 0xFFEBEBEB);
    }
    private static void scene() { ItemGlowRenderer.held(ItemGlowRenderCheck::drawSword); drawPhone(); }
    private static void drawSword() {
        swordDraws++;
        GlStateManager.enableDepth(); GlStateManager.depthMask(true); GlStateManager.enableAlpha();
        GlStateManager.enableBlend(); GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0); GlStateManager.color(1, 1, 1, 1);
        mc.getTextureManager().bindTexture(new ResourceLocation("textures/items/diamond_sword.png"));
        Gui.drawScaledCustomSizeModalRect(405, 115, 0, 0, 16, 16, 210, 210, 16, 16);
        // A translucent source checks preservation of real alpha blending against the world color.
        GlStateManager.disableAlpha(); Gui.drawRect(460, 265, 570, 325, 0x606B9BAF);
        GlStateManager.enableAlpha();
    }
    private static void drawPhone() {
        GlStateManager.pushMatrix();
        try { GlStateManager.translate(36, 76, 0); GlStateManager.scale(.55F, .55F, .55F); GlStateManager.rotate(phoneAngle, 0, 0, 1); device.drawDevice(phone, true); }
        finally { GlStateManager.popMatrix(); }
    }
    private static void verifyPixels(int[] original, int[] lit) {
        int background = 0xFF1A242E, halo = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int at = y * W + x;
            if (difference(original[at], background) > 2 && difference(original[at], lit[at]) > 2)
                throw new AssertionError("The original item/phone/HUD pixel changed at " + x + "," + y);
            if (difference(original[at], background) <= 2 && difference(original[at], lit[at]) > 3) halo++;
        }
        if (halo < 500) throw new AssertionError("No visible halo outside the item and phone: " + halo);
        if (difference(original[60 * W + 500], lit[60 * W + 500]) > 1) throw new AssertionError("The existing HUD/background was included in the mask");
    }
    private static void verifyInnerModes(Object renderer,Object phoneEffect)throws Exception {
        field(PhoneModule.class,"appAt").setLong(phone,System.currentTimeMillis()-4000);
        glow.colorMode.setValue("Static");glow.color.setValue(0xFF73DCCF);glow.innerSpeed.setValue(0D);
        glow.setEnabled(false);frame();scene();int[] source=pixels();
        glow.setEnabled(true);glow.innerGlow.setValue(0D);frame();scene();int[] baseline=pixels();
        int heldAlloc=field(ItemGlowRenderer.class,"allocations").getInt(renderer),phoneAlloc=field(PhoneItemGlow.class,"allocations").getInt(phoneEffect);
        List<int[]> images=new ArrayList<int[]>();
        for(String mode:new String[]{"Tint","Soft","Rim","Pulse","Scan"}) {
            glow.innerGlow.setValue(.4D);glow.innerMode.setValue(mode);
            frame();int before=swordDraws;scene();int[] image=pixels();save("inner-"+mode.toLowerCase(java.util.Locale.ROOT)+".png",image);check();
            if(swordDraws!=before+1)throw new AssertionError("An inner glow mode redrew the held geometry");
            int held=0,phonePixels=0;
            for(int y=0;y<H;y++)for(int x=0;x<W;x++) {
                int at=y*W+x;
                if(difference(baseline[at],image[at])<=3)continue;
                if(difference(source[at],0xFF1A242E)<=2)throw new AssertionError(mode+" inner glow escaped the object silhouette");
                if(x<420)phonePixels++;if(x>800)held++;
            }
            if(held<100||phonePixels<100)throw new AssertionError(mode+" is not visible on both held items and Phone: "+held+", "+phonePixels);
            for(int[] previous:images)if(Arrays.equals(previous,image))throw new AssertionError("Inner modes have identical output");
            images.add(image);
            if(heldAlloc!=field(ItemGlowRenderer.class,"allocations").getInt(renderer)||phoneAlloc!=field(PhoneItemGlow.class,"allocations").getInt(phoneEffect))
                throw new AssertionError("Changing inner modes allocated new GPU targets");
        }
        long started=field(ItemGlowModule.class,"visualStarted").getLong(glow);
        glow.innerGlow.setValue(0D);frame();ItemGlowRenderer.held(()->Gui.drawRect(398,55,400,150,0xFF1D2A35));int[] plainThin=pixels();
        glow.innerMode.setValue("Soft");glow.innerGlow.setValue(.4D);frame();ItemGlowRenderer.held(()->Gui.drawRect(398,55,400,150,0xFF1D2A35));int[] softThin=pixels();
        int thinPixels=0;for(int i=0;i<softThin.length;i++)if(difference(plainThin[i],softThin[i])>3)thinPixels++;
        if(thinPixels<100)throw new AssertionError("Soft inner glow disappeared on a thin object");
        glow.innerMode.setValue("Pulse");glow.innerSpeed.setValue(1D);
        field(ItemGlowModule.class,"visualStarted").setLong(glow,System.nanoTime()-250000000L);frame();scene();int[] peak=pixels();
        field(ItemGlowModule.class,"visualStarted").setLong(glow,System.nanoTime()-750000000L);frame();scene();int[] trough=pixels();
        if(Arrays.equals(peak,trough))throw new AssertionError("Inner Pulse did not animate");
        field(ItemGlowModule.class,"visualStarted").setLong(glow,started);
        glow.innerGlow.setValue(0D);glow.innerMode.setValue("Tint");glow.innerSpeed.resetToDefault();glow.colorMode.setValue("Gradient");
        System.out.println("Inner glow: five distinct modes on real held-item and Phone pixels, confined coverage, pulse animation, one geometry draw and reused GPU buffers passed.");
    }
    private static int difference(int a, int b) { return Math.max(Math.abs((a >> 16 & 255) - (b >> 16 & 255)), Math.max(Math.abs((a >> 8 & 255) - (b >> 8 & 255)), Math.abs((a & 255) - (b & 255)))); }
    private static float depth(int x, int y) { FloatBuffer value = BufferUtils.createFloatBuffer(1); GL11.glReadPixels(x, H - 1 - y, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, value); return value.get(0); }
    private static int[] pixels() {
        ByteBuffer data = BufferUtils.createByteBuffer(W * H * 4); GL11.glReadPixels(0, 0, W, H, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
        int[] rgb = new int[W * H];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int at = ((H - 1 - y) * W + x) * 4;
            rgb[y * W + x] = 0xFF000000 | (data.get(at) & 255) << 16 | (data.get(at + 1) & 255) << 8 | data.get(at + 2) & 255;
        }
        return rgb;
    }
    private static void save(String name, int[] pixels) throws Exception {
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB); image.setRGB(0, 0, W, H, pixels, 0, W); ImageIO.write(image, "png", OUT.resolve(name).toFile());
    }
    private static void check() {
        if (GL11.glGetError() != GL11.GL_NO_ERROR || GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != 0
                || GL11.glGetInteger(0x8CA6) != target.framebufferObject || GL11.glIsEnabled(GL11.GL_SCISSOR_TEST))
            throw new AssertionError("ItemGlow leaked graphics state");
    }
    private static String benchmark() {
        double[] off = new double[12], on = new double[12], delta = new double[12];
        glow.setEnabled(true);
        for (int i = -6; i < 12; i++) {
            // Alternate matched pairs without destroying buffers or warming only the enabled case.
            for (int pass = 0; pass < 2; pass++) {
                boolean enabled = (i & 1) == 0 ? pass == 1 : pass == 0;
                glow.heldItem.setEnabled(enabled); glow.phone.setEnabled(enabled);
                frame(); GL11.glFinish(); long start = System.nanoTime(); scene(); GL11.glFinish();
                if (i >= 0) (enabled ? on : off)[i] = (System.nanoTime() - start) / 1.0e6;
            }
            if (i >= 0) delta[i] = on[i] - off[i];
        }
        glow.heldItem.setEnabled(true); glow.phone.setEnabled(true);
        Arrays.sort(off); Arrays.sort(on); Arrays.sort(delta);
        return String.format(java.util.Locale.ROOT, "1280x720, actual Phone + sword, Balanced, paired GPU-synchronized median: off %.2f ms, on %.2f ms, paired difference %.2f ms.", off[6], on[6], delta[6]);
    }
    private static Field field(Class<?> type, String name) throws Exception { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; }
    public static final class Player extends EntityPlayerSP {
        private Player() { super(null, null, null, null); }
        @Override public ItemStack getHeldItem() { return HELD; }
        @Override public ResourceLocation getLocationSkin() { return new ResourceLocation("textures/entity/steve.png"); }
        @Override public String getSkinType() { return "default"; }
        @Override public boolean isWearing(EnumPlayerModelParts part) { return true; }
        @Override public void playSound(String name, float volume, float pitch) { }
    }
}
