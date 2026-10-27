package dev.vibe.phone;

import dev.vibe.module.impl.meme.PhoneModule;
import dev.vibe.module.impl.meme.PhoneModule.Action;
import dev.vibe.module.impl.meme.PhoneModule.App;
import dev.vibe.ui.RenderUtils;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/** A skin-textured left hand and a rounded, animated phone, drawn after the normal HUD. */
public final class PhoneRenderer {
    public static final int WIDTH = 232, HEIGHT = 456;
    private final Map<PhoneContact, ResourceLocation> avatars = new EnumMap<PhoneContact, ResourceLocation>(PhoneContact.class);
    private static final ResourceLocation DISCORD_IMAGES = new ResourceLocation("vibe", "textures/phone/fake-discord-scams.gif");
    private final PhoneFont TEXT = new PhoneFont(Font.PLAIN, 10.5F);
    private final PhoneFont SMALL = new PhoneFont(Font.PLAIN, 9.25F);
    private final PhoneFont TITLE = new PhoneFont(Font.BOLD, 15.5F);
    private final PhoneFont CLOCK = new PhoneFont(Font.PLAIN, 34);
    private final PhoneFont KEY = new PhoneFont(Font.BOLD, 8.5F);
    private final PhoneCanvas canvas = new PhoneCanvas();
    private static final int WHITE = 0xFFF4F5FF, MUTED = 0xFF9EABC8, BLUE = 0xFF8ABEFF;
    private final Minecraft mc = Minecraft.getMinecraft();
    private java.util.function.LongSupplier wallClock = System::currentTimeMillis;
    private long previousFrame, previewAt;
    private float visibility;
    private DynamicTexture wallpaperTexture;
    private ResourceLocation wallpaperLocation;
    private int wallpaperKey;
    private int previewTexture, previewWidth, previewHeight;
    private final LinkedHashMap<Path, PhotoTexture> textures = new LinkedHashMap<Path, PhotoTexture>(12, .75F, true);
    private ModelRenderer arm, sleeve, slimArm, slimSleeve, thumb, thumbTip;
    private final PhoneHandPose handPose = new PhoneHandPose();
    private long previousHandFrame;
    private float smoothedYaw;

    /** Called before ALL HUD elements: the preview and PNG contain the world, never the phone UI. */
    public void captureWorld(PhoneModule phone) {
        if (phone == null || !phone.isEnabled() || phone.app() != App.CAMERA || mc.theWorld == null
                || mc.thePlayer == null || mc.currentScreen != null
                || !phone.allowsContext(false, mc.gameSettings.thirdPersonView != 0, mc.gameSettings.hideGUI)) return;
        captureFramebuffer(phone);
    }
    public void captureFramebuffer(PhoneModule phone) {
        long now = System.currentTimeMillis();
        if (now - previewAt < 33 && !phone.wantsCapture()) return;
        int width = mc.displayWidth, height = mc.displayHeight;
        if (width <= 0 || height <= 0 || (long) width * height > 16000000) {
            if (phone.wantsCapture()) phone.captureFailed("Resolution too large for Camera");
            return;
        }
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            if (previewTexture == 0) {
                previewTexture = GL11.glGenTextures();
                GlStateManager.bindTexture(previewTexture);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            } else GlStateManager.bindTexture(previewTexture);
            if (previewWidth != width || previewHeight != height) {
                GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, 0, 0, width, height, 0);
                previewWidth = width; previewHeight = height;
            } else GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
            previewAt = now;
            if (phone.wantsCapture()) {
                ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
                GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                int[] row = new int[width];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int offset = ((height - y - 1) * width + x) * 4;
                        row[x] = ((pixels.get(offset) & 255) << 16) | ((pixels.get(offset + 1) & 255) << 8) | (pixels.get(offset + 2) & 255);
                    }
                    image.setRGB(0, y, width, 1, row, 0, width);
                }
                if (phone.photos().save(image)) phone.captured(); else phone.captureFailed("Camera is still saving");
            }
        } catch (RuntimeException failure) { phone.captureFailed("Camera could not capture this frame"); }
        finally { GlStateManager.bindTexture(previous); }
    }

    public void render(PhoneModule phone, ScaledResolution resolution) {
        long now = System.nanoTime();
        double elapsed = previousFrame == 0 ? 0 : Math.min(.05, (now - previousFrame) / 1.0e9);
        previousFrame = now;
        boolean available = phone != null && phone.isEnabled() && mc.thePlayer != null && mc.theWorld != null
                && phone.allowsContext(mc.currentScreen != null, mc.gameSettings.thirdPersonView != 0, mc.gameSettings.hideGUI);
        visibility += ((available ? 1 : 0) - visibility) * (float) (1 - Math.exp(-elapsed * 13));
        if (phone == null || visibility < .005F) {
            if (previewTexture != 0) releasePreview();
            canvas.close();
            return;
        }
        if (available && mc.currentScreen == null) phone.advance(elapsed);
        float bottomMargin = phone.hints.isEnabled() ? 32 : 8;
        float maximumHeight = resolution.getScaledHeight() - bottomMargin - 17;
        float scale = Math.min(maximumHeight * .8F / HEIGHT, resolution.getScaledWidth() * .32F / WIDTH) * phone.size.getFloat();
        scale = Math.max(.15F, Math.min(scale, maximumHeight / HEIGHT));
        float travel = 1 - visibility;
        float bob = available ? (float) Math.sin(mc.thePlayer.ticksExisted * .09) * .8F : 0;
        float yaw = mc.thePlayer == null ? 0 : mc.thePlayer.rotationYaw;
        smoothedYaw += (yaw - smoothedYaw) * (float) (1 - Math.exp(-elapsed * 10));
        float sway = Math.max(-2, Math.min(2, (yaw - smoothedYaw) * .03F));
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(22 + sway, resolution.getScaledHeight() - bottomMargin - HEIGHT * scale + travel * HEIGHT * scale + bob, 0);
            GlStateManager.scale(scale, scale, scale);
            GlStateManager.rotate(-3.5F - travel * 17, 0, 0, 1);
            drawDevice(phone, true);
        } finally { GlStateManager.popMatrix(); restoreState(); }
        if (available && phone.hints.isEnabled() && mc.currentScreen == null) {
            float width = Math.min(480, resolution.getScaledWidth() - 34);
            keyHints(17 + width / 2, resolution.getScaledHeight() - 24, width, WHITE,
                    phone.shortKey(Action.UP), "", phone.shortKey(Action.DOWN), "", phone.shortKey(Action.LEFT), "", phone.shortKey(Action.RIGHT), "Move",
                    phone.shortKey(Action.SELECT), "Select", phone.shortKey(Action.HOME), "Home", phone.shortKey(Action.BACK), "Back");
            restoreState();
        }
    }

    public boolean isActive() { return visibility >= .005F; }

    /** Same device renderer used by offscreen verification. */
    public void drawDevice(PhoneModule phone, boolean drawHand) {
        if (!canvas.draw(() -> drawContents(phone, drawHand))) drawContents(phone, drawHand);
    }

    private void drawContents(PhoneModule phone, boolean drawHand) {
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableBlend(); GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
        GlStateManager.disableCull(); GlStateManager.disableLighting();
        GlStateManager.disableDepth(); GlStateManager.depthMask(false);
        if (drawHand && mc.thePlayer != null) { updateHand(phone); hand(false); }
        int finish = phone.phoneColor.getArgb() | 0xFF000000;
        round(-1, -1, WIDTH + 2, HEIGHT + 2, 38, RenderUtils.blend(finish, 0xFFFFFFFF, .55F));
        round(0, 0, WIDTH, HEIGHT, 37, finish);
        round(2, 2, WIDTH - 4, HEIGHT - 4, 35, RenderUtils.blend(finish, 0xFF000000, .25F));
        round(4, 4, WIDTH - 8, HEIGHT - 8, 33, 0xFF07080C);
        round(-3, 86, 3, 23, 1, finish); round(-3, 128, 3, 38, 1, finish); round(-3, 174, 3, 38, 1, finish);
        round(WIDTH, 138, 3, 57, 1, finish);
        wallpaper(phone);
        GlStateManager.bindTexture(wallpaperTexture.getGlTextureId());
        texturedRound(8, 9, 216, 437, 30, 0, 0, 1, 1);
        if (phone.app() != App.HOME) {
            // Apps own the whole display, including the status and navigation areas.
            round(8, 9, 216, 437, 30, phone.app() == App.FLAPPY ? 0xFF132732 : 0xFA101927);
        }
        GlStateManager.pushMatrix();
        float enter = Math.min(1, Math.max(0, (System.currentTimeMillis() - phone.appAt()) / 180F));
        float appScale = .99F + .01F * (1 - (1 - enter) * (1 - enter));
        GlStateManager.translate(116, 230, 0); GlStateManager.scale(appScale, appScale, 1); GlStateManager.translate(-116, -230, 0);
        switch (phone.app()) {
            case HOME: home(phone); break;
            case SETTINGS: settings(phone); break;
            case CAMERA: camera(phone); break;
            case FLAPPY: flappy(phone); break;
            case TETRIS: tetris(phone); break;
            case PHOTOS: photos(phone); break;
            case INTERNET: internet(phone); break;
            case CONTACTS: contacts(phone); break;
            case TELEPHONE: telephone(phone); break;
            case MESSAGES: messages(phone); break;
            default: break;
        }
        GlStateManager.popMatrix();
        status(phone);
        round(82, 431, 68, 4, 2, 0xDDF5F5FF);
        String notification = phone.notification();
        if (!notification.isEmpty()) {
            // A system banner leaves the navigation and current action unobstructed.
            round(18, 43, 196, 35, 13, 0xFF2D3650);
            centered(SMALL, SMALL.fit(notification, 180), WIDTH / 2, 54, WHITE);
        }
        double boot = (System.currentTimeMillis() - phone.openedAt()) / 1000.0;
        if (boot >= 0 && boot < .90) boot(boot);
        float shade = (100 - phone.brightness.getFloat()) / 100;
        if (shade > 0) round(8, 9, 216, 437, 30, ((int) (shade * 205) << 24));
        if (drawHand && mc.thePlayer != null && !phone.hideThumb.isEnabled()) hand(true);
        GlStateManager.color(1, 1, 1, 1);
    }

    private void wallpaper(PhoneModule phone) {
        int key = Objects.hash(phone.backgroundTop.getArgb(), phone.backgroundBottom.getArgb(), phone.wallpaper.getValue());
        if (wallpaperTexture != null && key == wallpaperKey) return;
        int width = 512, height = 1024;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int first = phone.backgroundTop.getArgb(), second = phone.backgroundBottom.getArgb();
        boolean aurora = phone.wallpaper.is("Aurora"), sunset = phone.wallpaper.is("Sunset"), minimal = phone.wallpaper.is("Minimal");
        double[] glowX = new double[width];
        for (int column = 0; column < width; column++) {
            double distance = ((column + .5) * 240 / width - 205) / 190;
            glowX[column] = Math.exp(-distance * distance);
        }
        for (int row = 0; row < height; row++) {
            float y = (row + .5F) * 480 / height;
            double distanceY = (y - 260) / 185D, glowY = Math.exp(-distanceY * distanceY);
            double waveX = 80 + 80 * Math.sin(y * .008);
            for (int column = 0; column < width; column++) {
                float x = (column + .5F) * 240 / width;
                float t = Math.max(0, Math.min(1, y / 480F + (x / 240F - .5F) * .2F));
                int color = RenderUtils.blend(first, second, t);
                double glow = glowX[column] * glowY;
                if (aurora) glow *= .6 + .4 * Math.sin(x * .022 + y * .013);
                if (sunset) color = RenderUtils.blend(color, 0xFFFF8A64, (float) glow * .7F);
                else if (!minimal) color = RenderUtils.blend(color, 0xFF92BBFF, (float) glow * .65F);
                float coverage = (float) Math.max(0, Math.min(1, (1.8 - Math.abs(x - waveX)) * 2 + .5));
                if (!minimal && coverage > 0) color = RenderUtils.blend(color, 0xFFBED1FF, .15F * coverage);
                image.setRGB(column, row, color | 0xFF000000);
            }
        }
        if (wallpaperTexture != null) mc.getTextureManager().deleteTexture(wallpaperLocation);
        wallpaperTexture = new DynamicTexture(image);
        wallpaperTexture.setBlurMipmap(true, false);
        wallpaperLocation = mc.getTextureManager().getDynamicTextureLocation("vibeos_wallpaper", wallpaperTexture);
        wallpaperKey = key;
    }

    private void home(PhoneModule phone) {
        Date now = new Date(wallClock.getAsLong());
        String time = new SimpleDateFormat("HH:mm").format(now);
        centered(CLOCK, time, WIDTH / 2F, 64, WHITE);
        centered(SMALL, new SimpleDateFormat("EEEE, d MMMM", Locale.ENGLISH).format(now), WIDTH / 2, 110, 0xE6E0E6FF);
        round(24, 133, 184, 42, 14, 0x24405188);
        text(SMALL, "Apps and shortcuts", 35, 137, 0xE6D5E2FF);
        text(TITLE, "VibeOS", 35, 151, WHITE);
        for (int i = 0; i < PhoneModule.appCount(); i++) {
            int x = 26 + (i % 3) * 66, y = 181 + (i / 3) * 68;
            boolean focused = phone.homeSelection() == i;
            if (focused) round(x - 5, y - 5, 58, 67, 15, 0x3CFFFFFF);
            icon(i, x, y, 48);
            if (i == 7 && phone.unreadMessages() > 0) {
                String count = phone.unreadMessages() > 9 ? "9+" : String.valueOf(phone.unreadMessages());
                round(x + 34, y - 4, 20, 17, 8, 0xFFF06078);
                centered(KEY, count, x + 44, y - 2, WHITE);
            }
            centered(SMALL, PhoneModule.appName(i), x + 24, y + 49, WHITE);
            if (focused) round(x + 16, y + 62, 16, 2, 1, 0xE8DDEAFF);
        }
        keyHints(116, 400, 192, WHITE, phone.shortKey(Action.SELECT), "Open " + PhoneModule.appName(phone.homeSelection()));
    }
    private void status(PhoneModule phone) {
        text(SMALL, new SimpleDateFormat("HH:mm").format(new Date(wallClock.getAsLong())), 25, 24, WHITE);
        round(86, 18, 61, 16, 8, 0xFF02040A);
        circle(135, 26, 3, 0xFF101D30); circle(135, 26, 1.3F, 0xFF284964);
        if (phone.app() == App.CAMERA) circle(155, 26, 2, 0xFF75E9A5);
        for (int i = 0; i < 3; i++) round(173 + i * 4, 29 - i * 3, 2, 3 + i * 3, 0, WHITE);
        outline(190, 22, 17, 9, 2, 1, 0xCCFFFFFF); round(192, 24, 12, 5, 1, WHITE); round(208, 25, 2, 3, 1, 0xAAFFFFFF);
    }
    private void heading(String title, String subtitle) {
        text(TITLE, title, 22, 59, WHITE);
        text(SMALL, SMALL.fit(subtitle, 192), 22, 83, MUTED);
    }
    private void settings(PhoneModule phone) {
        heading("Settings", "Display, appearance and controls");
        int selected = phone.settingSelection();
        int first = Math.max(0, Math.min(phone.settingsCount() - 7, selected - 5));
        for (int i = first; i < Math.min(first + 7, phone.settingsCount()); i++) {
            int y = 108 + (i - first) * 38;
            boolean active = selected == i;
            round(18, y, 194, 36, 9, active ? 0xFF283F66 : 0xFF192537);
            text(TEXT, phone.settingLabel(i), 27, y + 3, active ? WHITE : 0xFFCAD3E7);
            String value = phone.binding() == i - 8 && i >= 8 && i < 16 ? "Press a key..." : phone.settingValue(i);
            if (i >= 8 && i < 16 && phone.binding() != i - 8) keycap(value, 27, y + 17, active ? WHITE : MUTED);
            else text(SMALL, value, 27, y + 19, active ? BLUE : MUTED);
            if (i == 0 || i == 2 || i == 3) {
                int color = i == 0 ? phone.phoneColor.getArgb() : i == 2 ? phone.backgroundTop.getArgb() : phone.backgroundBottom.getArgb();
                round(181, y + 9, 17, 17, 5, color | 0xFF000000);
            } else text(SMALL, i >= 8 && i < 16 ? "Edit" : "<  >", 178, y + 13, active ? BLUE : MUTED);
        }
        round(214, 109, 2, 263, 1, 0xFF26314A);
        round(216, 109 + (int) (selected * 235F / (phone.settingsCount() - 1)), 2, 28, 1, BLUE);
        if (phone.binding() >= 0) keyHints(116, 398, 194, MUTED, "Esc", "Cancel");
        else if (selected >= 8 && selected < 16) keyHints(116, 398, 194, MUTED, phone.shortKey(Action.SELECT), "Rebind");
        else keyHints(116, 398, 194, MUTED, phone.shortKey(Action.LEFT), "", phone.shortKey(Action.RIGHT), "Change");
    }
    private void camera(PhoneModule phone) {
        heading("Camera", "World camera");
        round(21, 107, 190, 220, 15, 0xFF030608);
        if (previewTexture != 0) {
            GlStateManager.bindTexture(previewTexture);
            float targetAspect = 190F / 220, imageAspect = previewWidth / (float) previewHeight;
            float crop = Math.min(1, targetAspect / imageAspect);
            texturedRound(21, 107, 190, 220, 15, (1 - crop) / 2, 1, (1 + crop) / 2, 0);
            for (int i = 1; i <= 2; i++) {
                line(0x407FFFFF, .6F, 21 + i * 190 / 3F, 109, 21 + i * 190 / 3F, 325);
                line(0x407FFFFF, .6F, 23, 107 + i * 220 / 3F, 209, 107 + i * 220 / 3F);
            }
            line(0xC4FFF4C6, 1, 102, 209, 102, 199, 112, 199);
            line(0xC4FFF4C6, 1, 130, 209, 130, 199, 120, 199);
            line(0xC4FFF4C6, 1, 102, 226, 102, 236, 112, 236);
            line(0xC4FFF4C6, 1, 130, 226, 130, 236, 120, 236);
        } else centered(SMALL, "Viewfinder available in a world", 116, 211, MUTED);
        round(77, 307, 78, 15, 7, 0x8003080D); centered(SMALL, "1x   World", 116, 309, WHITE);
        centered(SMALL, "Photo", 116, 338, 0xFFFFD879);
        circle(116, 377, 25, WHITE); circle(116, 377, 21, 0xFF121927); circle(116, 377, 18, WHITE);
        icon(3, 35, 362, 27);
        keycap(phone.shortKey(Action.SELECT), 174, 369, WHITE);
        float flash = Math.max(0, 1 - (System.currentTimeMillis() - phone.flashAt()) / 220F);
        if (flash > 0) round(21, 107, 190, 220, 15, ((int) (flash * 220) << 24) | 0xFFFFFF);
        if (phone.photos().isSaving()) centered(SMALL, "Saving...", 116, 414, BLUE);
    }
    private void photos(PhoneModule phone) {
        List<PhonePhotoLibrary.Photo> photos = phone.photos().photos();
        heading("Photos", photos.size() + (photos.size() == 1 ? " memory, saved locally" : " memories, saved locally"));
        if (photos.isEmpty()) {
            icon(3, 86, 153, 60);
            centered(TITLE, "No photos yet", 116, 235, WHITE);
            centered(SMALL, "Open Camera to take a photo.", 116, 260, MUTED);
        } else if (phone.isPhotoOpen()) {
            // Draw one immutable album snapshot even if a background refresh finishes now.
            PhonePhotoLibrary.Photo photo = photos.get(Math.min(phone.photoSelection(), photos.size() - 1));
            drawPhoto(phone, photo, 21, 112, 190, 210, false);
            centered(TEXT, new SimpleDateFormat("d MMM yyyy  HH:mm").format(new Date(photo.taken)), 116, 339, WHITE);
            centered(SMALL, "PNG image · " + Math.max(1, photo.bytes / 1024) + " KB", 116, 358, MUTED);
            centered(SMALL, (phone.photoSelection() + 1) + " of " + photos.size(), 116, 382, BLUE);
        } else {
            int first = Math.max(0, (phone.photoSelection() / 2 - 2) * 2);
            for (int i = first; i < Math.min(photos.size(), first + 6); i++) {
                int x = 23 + (i % 2) * 95, y = 111 + ((i - first) / 2) * 88;
                drawPhoto(phone, photos.get(i), x, y, 88, 71, true);
                if (i == phone.photoSelection()) outline(x - 2, y - 2, 92, 75, 11, 1.5F, BLUE);
                text(SMALL, new SimpleDateFormat("MMM d, HH:mm").format(new Date(photos.get(i).taken)), x + 2, y + 76, MUTED);
            }
        }
        keyHints(116, 400, 194, MUTED, phone.shortKey(phone.isPhotoOpen() ? Action.BACK : Action.SELECT), phone.isPhotoOpen() ? "Album" : "View",
                phone.shortKey(Action.DELETE), "Delete");
        if (phone.isConfirmDelete()) {
            round(13, 49, 206, 368, 20, 0xAB030613);
            round(27, 166, 178, 150, 18, 0xFF242D40);
            centered(TITLE, "Delete this photo?", 116, 187, WHITE);
            centered(SMALL, "This removes the saved PNG.", 116, 216, MUTED);
            round(40, 242, 152, 29, 9, 0xFFC74660);
            keyHints(116, 247, 142, WHITE, phone.shortKey(Action.SELECT), "Delete photo");
            keyHints(116, 283, 142, 0xFFD2DCEE, phone.shortKey(Action.BACK), "Keep photo");
        }
    }
    private void drawPhoto(PhoneModule phone, PhonePhotoLibrary.Photo photo, int x, int y, int width, int height, boolean crop) {
        round(x, y, width, height, 9, 0xFF202B3F);
        if (photo == null) return;
        PhotoTexture texture = textures.get(photo.file);
        if (texture == null) {
            BufferedImage image = phone.photos().image(photo);
            if (image == null) { centered(SMALL, "Loading...", x + width / 2F, y + height / 2F - 4, MUTED); return; }
            DynamicTexture dynamic = new DynamicTexture(image);
            dynamic.setBlurMipmap(true, false);
            texture = new PhotoTexture(dynamic, mc.getTextureManager().getDynamicTextureLocation("vibeos_photo", dynamic), image.getWidth(), image.getHeight());
            textures.put(photo.file, texture);
            while (textures.size() > 8) {
                Path old = textures.keySet().iterator().next();
                mc.getTextureManager().deleteTexture(textures.remove(old).location);
            }
        }
        GlStateManager.bindTexture(texture.texture.getGlTextureId());
        float aspect = texture.width / (float) texture.height, box = width / (float) height;
        if (crop) {
            float u = Math.min(1, box / aspect), v = Math.min(1, aspect / box);
            texturedRound(x, y, width, height, 9, (1 - u) / 2, (1 - v) / 2, (1 + u) / 2, (1 + v) / 2);
        } else {
            int w = width, h = Math.round(width / aspect);
            if (h > height) { h = height; w = Math.round(height * aspect); }
            texturedRound(x + (width - w) / 2F, y + (height - h) / 2F, w, h, 3, 0, 0, 1, 1);
        }
    }
    private void internet(PhoneModule phone) {
        heading("Internet", "Your saved YouTube channel");
        round(22, 108, 188, 27, 12, 0xFF202B40);
        centered(SMALL, "youtube.com", 116, 117, 0xFFBAC8DF);
        round(77, 159, 78, 58, 17, 0xFFED3152);
        triangle(108, 174, 108, 202, 130, 188, WHITE);
        centered(TITLE, "@heisthacksjp", 116, 240, WHITE);
        centered(SMALL, "YouTube", 116, 266, MUTED);
        centered(SMALL, "Opens in your browser", 116, 291, MUTED);
        round(31, 328, 170, 37, 12, 0xFF326ADB);
        keyHints(116, 338, 154, WHITE, phone.shortKey(Action.SELECT), "Open YouTube");
        keyHints(116, 390, 194, MUTED, phone.shortKey(Action.HOME), "Home");
    }
    private void tetris(PhoneModule phone) {
        TetrisGame game=phone.tetris();
        heading("Tetris", "Personal best: "+phone.tetrisBest.getInt());
        round(155,60,59,19,8,game.autoplay()?0xFF235F55:0xFF2B3650);
        centered(SMALL,game.autoplay()?"Auto on":"Manual",184.5F,64,game.autoplay()?0xFF9AF5CA:MUTED);
        round(17,107,136,266,9,0xFF3B4D6B);round(18,108,134,264,8,0xFF0B1425);
        round(157,110,57,62,9,0xFF1C2B43);text(SMALL,"Score",164,118,MUTED);
        centered(TEXT,TEXT.fit(String.valueOf(game.score()),45),185.5F,139,WHITE);
        round(157,179,57,70,9,0xFF1C2B43);text(SMALL,"Level",164,188,MUTED);text(TEXT,String.valueOf(game.level()),207-TEXT.width(String.valueOf(game.level())),188,WHITE);
        text(SMALL,"Lines",164,222,MUTED);text(TEXT,String.valueOf(game.lines()),207-TEXT.width(String.valueOf(game.lines())),222,WHITE);
        round(157,256,57,117,9,0xFF1C2B43);text(SMALL,"Next",164,264,MUTED);
        try(PhoneClip ignored=new PhoneClip(20,110,130,260)){
            solid(WHITE);GL11.glBegin(GL11.GL_QUADS);
            try{
                for(int row=0;row<TetrisGame.HEIGHT;row++)for(int col=0;col<TetrisGame.WIDTH;col++){
                    blockQuad(20+col*13,110+row*13,12.5F,12.5F,(row+col)%2==0?0xFF17253B:0xFF19283F);
                    int value=game.cell(col,row);
                    if(value!=0){int shade=TetrisGame.COLORS[value-1];
                        if((game.clearMask()&(1<<row))!=0)shade=RenderUtils.blend(shade,WHITE,.4F+.6F*(float)Math.sin(game.clearProgress()*Math.PI));
                        tetrisCell(20+col*13,110+row*13,13,shade,false);
                    }
                }
                if(game.phase()==TetrisGame.Phase.READY||game.phase()==TetrisGame.Phase.PLAYING){
                    int ghost=game.ghostY(),shade=TetrisGame.COLORS[game.type()];
                    for(int i=0;i<4;i++){
                        int cx=game.x()+TetrisGame.cellX(game.type(),game.rotation(),i),cy=TetrisGame.cellY(game.type(),game.rotation(),i);
                        tetrisCell(20+cx*13,110+(ghost+cy)*13,13,shade,true);
                        tetrisCell(20+cx*13,110+(game.y()+cy)*13,13,shade,false);
                    }
                }
            }finally{GL11.glEnd();endSolid();}
        }
        solid(WHITE);GL11.glBegin(GL11.GL_QUADS);
        try{for(int preview=0;preview<3;preview++)for(int cell=0;cell<4;cell++){
            int type=game.next(preview);tetrisCell(168+TetrisGame.cellX(type,0,cell)*8,285+preview*28+TetrisGame.cellY(type,0,cell)*8,8,TetrisGame.COLORS[type],false);
        }}finally{GL11.glEnd();endSolid();}
        if(game.phase()==TetrisGame.Phase.READY||game.phase()==TetrisGame.Phase.OVER){
            round(25,205,120,79,11,0xEF15253D);
            centered(TEXT,game.phase()==TetrisGame.Phase.OVER?"Game over":"Ready to stack",85,216,WHITE);
            centered(SMALL,game.phase()==TetrisGame.Phase.OVER?"Score "+game.score():"Make room for more",85,235,MUTED);
            keyHints(85,255,107,BLUE,phone.shortKey(game.phase()==TetrisGame.Phase.OVER?Action.DELETE:Action.UP),game.phase()==TetrisGame.Phase.OVER?"New game":"Play");
        }
        if(game.phase()==TetrisGame.Phase.CLEARING){
            String[] names={"","Single","Double","Triple","Tetris"};
            round(48,222,76,25,10,0xEB244166);centered(TEXT,names[game.cleared()],86,228,WHITE);
        }
        keyHints(116,380,196,MUTED,phone.shortKey(Action.LEFT),"",phone.shortKey(Action.RIGHT),"Move",phone.shortKey(Action.UP),"Rotate",phone.shortKey(Action.DOWN),"Soft drop");
        keyHints(116,404,196,MUTED,phone.shortKey(Action.DELETE),"Hard drop",phone.shortKey(Action.SELECT),"Autoplay",phone.shortKey(Action.HOME),"Home");
    }
    /** Cells from the board, live piece, ghost and previews are batched into two draw calls. */
    private static void tetrisCell(float x,float y,float size,int shade,boolean ghost){
        float edge=Math.max(.7F,size*.07F);
        if(ghost){int tint=0x65000000|(shade&0xFFFFFF);
            blockQuad(x+1,y+1,size-2,edge,tint);blockQuad(x+1,y+size-1-edge,size-2,edge,tint);
            blockQuad(x+1,y+1,edge,size-2,tint);blockQuad(x+size-1-edge,y+1,edge,size-2,tint);return;
        }
        blockQuad(x+.6F,y+.6F,size-1.2F,size-1.2F,RenderUtils.blend(shade,0xFF000000,.3F));
        blockQuad(x+1,y+1,size-2,size-3,shade);
        blockQuad(x+1,y+1,size-2,edge,RenderUtils.blend(shade,WHITE,.3F));
    }
    private static void blockQuad(float x,float y,float width,float height,int shade){
        color(shade);GL11.glVertex2f(x,y);GL11.glVertex2f(x,y+height);GL11.glVertex2f(x+width,y+height);GL11.glVertex2f(x+width,y);
    }
    private void flappy(PhoneModule phone) {
        heading("Flappy Bird", "Personal best: " + phone.bestScore.getInt());
        GlStateManager.pushMatrix(); GlStateManager.translate(16, 107, 0); GlStateManager.scale(200F / 192, 200F / 192, 1);
        try (PhoneClip ignored = new PhoneClip(0, 0, FlappyGame.WIDTH, FlappyGame.HEIGHT)) {
            for (int y = 0; y < 252; y++) rect(0, y, 192, 1, RenderUtils.blend(0xFF71C8D7, 0xFFC6EADA, y / 252F));
            circle(37, 50, 14, 0xAFFFFFFF); circle(53, 48, 19, 0xAFFFFFFF); circle(71, 53, 13, 0xAFFFFFFF);
            circle(139, 103, 12, 0x73FFFFFF); circle(154, 99, 17, 0x73FFFFFF); circle(171, 104, 12, 0x73FFFFFF);
            for (FlappyGame.Pipe pipe : phone.game().getPipes()) {
                if (pipe.x > 194 || pipe.x < -30) continue;
                float top = pipe.gapY - FlappyGame.GAP / 2, bottom = pipe.gapY + FlappyGame.GAP / 2;
                pipe(pipe.x, 0, top); pipe(pipe.x, bottom, 232 - bottom);
            }
            rect(0, 232, 192, 20, 0xFFB9D963); rect(0, 232, 192, 3, 0xFF668F40); rect(0, 239, 192, 13, 0xFFDFCF91);
            for (int x = 0; x < 192; x += 11) line(0xFF8FBD53, 2, x, 235, x + 5, 239);
            float birdY = phone.game().getBirdY();
            GlStateManager.pushMatrix(); GlStateManager.translate(46, birdY, 0);
            GlStateManager.rotate(Math.max(-22, Math.min(65, phone.game().getVelocity() * .15F)), 0, 0, 1);
            circle(0, 0, 9, 0xFF9B652C); circle(0, -1, 8, 0xFFFFD45D);
            circle(-4, 1, 4.5F, 0xFFFFEEA2); circle(4, -4, 4.5F, WHITE); circle(6, -4, 2, 0xFF1B2331);
            round(4, 2, 11, 4, 1, 0xFFF57945); GlStateManager.popMatrix();
            centered(TITLE, String.valueOf(phone.game().getScore()), 96, 16, 0xFF344654);
            if (phone.game().getPhase() != FlappyGame.Phase.PLAYING) {
                round(16, 125, 160, 78, 13, 0xEC1A2B3D);
                centered(TITLE, phone.game().getPhase() == FlappyGame.Phase.OVER ? "One more try?" : "Ready to fly?", 96, 139, WHITE);
                keyHints(96, 164, 144, 0xFFE7D994, phone.shortKey(Action.SELECT), phone.game().getPhase() == FlappyGame.Phase.OVER ? "Try again" : "Start playing");
                if (phone.game().getPhase() == FlappyGame.Phase.OVER) centered(SMALL, "Score " + phone.game().getScore(), 96, 185, MUTED);
            }
        } finally { GlStateManager.popMatrix(); }
        keyHints(116, 390, 194, MUTED, phone.shortKey(Action.SELECT), "Flap", phone.shortKey(Action.HOME), "Home");
    }
    private static void pipe(float x, float y, float height) {
        if (height <= 0) return;
        rect(x, y, 27, height, 0xFF3C7944); rect(x + 2, y, 22, height, 0xFF70BE69);
        rect(x + 4, y, 4, height, 0xFFA1DB7C);
        float cap = y == 0 ? height - 10 : y;
        rect(x - 2, cap, 31, 10, 0xFF3C7944); rect(x, cap + 2, 27, 6, 0xFF82C969);
    }
    private void contacts(PhoneModule phone) {
        heading("Contacts", "7 contacts · Fictional chats");
        contactRows(phone, false, 38);
        keyHints(116, 379, 194, MUTED, phone.shortKey(Action.UP), "", phone.shortKey(Action.DOWN), "Browse",
                phone.shortKey(Action.LEFT), "", phone.shortKey(Action.RIGHT), "Action");
        boolean blocked = phone.conversation().isBlocked();
        for (int i = 0; i < 2; i++) {
            int x = 22 + i * 96;
            boolean selected = phone.contactAction() == i;
            round(x, 402, 92, 22, 8, selected ? (i == 0 && blocked ? 0xFF493140 : 0xFF345D8D) : 0xFF202D42);
            if (i == 0) receiver(x + 13, 413, .36F, blocked ? MUTED : 0xFF90E7B4);
            else { round(x + 6, 408, 15, 10, 4, BLUE); triangle(x + 8, 416, x + 8, 420, x + 13, 416, BLUE); }
            if (selected) keycap(phone.shortKey(Action.SELECT), x + 66, 404, WHITE);
            text(SMALL, i == 0 ? blocked ? "Blocked" : "Call" : "Message", x + 26, 408, selected ? WHITE : MUTED);
        }
    }
    /** Every contact stays visible; the messenger rows include the latest message and unread state. */
    private void contactRows(PhoneModule phone, boolean messenger, int step) {
        PhoneContact[] contacts = PhoneContact.values();
        for (int i = 0; i < contacts.length; i++) {
            PhoneContact item = contacts[i];
            PhoneConversation chat = phone.conversation(item);
            int y = 109 + i * step;
            boolean selected = i == phone.contactSelection();
            round(22, y, 188, step - 3, 11, selected ? phone.app() == App.TELEPHONE ? 0xFF28594D : 0xFF304D76 : 0x9A18283F);
            avatar(item, 28, y + 4, step - 11);
            text(TEXT, TEXT.fit(item.name, messenger ? 112 : 126), 65, y + 4, WHITE);
            List<PhoneConversation.Message> history = chat.messages();
            PhoneConversation.Message last = history.isEmpty() ? null : history.get(history.size() - 1);
            String preview = item.number;
            int previewColor = MUTED;
            if (chat.isBlocked()) { preview = "Blocked"; previewColor = 0xFFF49CA5; }
            else if (messenger) {
                if (chat.isTyping()) { preview = "Typing..."; previewColor = BLUE; }
                else preview = last == null ? "Start a conversation" : (last.incoming ? "" : "You: ") + last.text;
            }
            int unread = chat.unreadCount();
            text(SMALL, SMALL.fit(preview, unread > 0 ? 119 : 137), 65, y + 19, previewColor);
            if (messenger && last != null) {
                String time = new SimpleDateFormat("HH:mm").format(new Date(last.sentAt));
                text(KEY, time, 205 - KEY.width(time), y + 5, MUTED);
            }
            if (unread > 0) {
                int badgeY = y + (messenger ? 26 : 11);
                circle(197, badgeY, 7, BLUE);
                centered(KEY, unread > 9 ? "9+" : Integer.toString(unread), 197, badgeY - 5, 0xFF152840);
            }
        }
    }
    private void telephone(PhoneModule phone) {
        PhoneContact contact = phone.callContact();
        if (phone.isCalling()) {
            centered(SMALL, "VibeOS telephone", 116, 70, MUTED);
            centered(TITLE, contact.name, 116, 113, WHITE);
            centered(TEXT, contact.number, 116, 140, MUTED);
            float pulse = (float) (.5 + .5 * Math.sin((System.currentTimeMillis() - phone.callStartedAt()) / 500D));
            circle(116, 222, 56 + pulse * 7, 0x1F68E5AD);
            circle(116, 222, 50 + pulse * 3, 0x2868E5AD);
            avatar(contact, 72, 178, 88);
            centered(TITLE, "Calling...", 116, 299, WHITE);
            centered(SMALL, "Ringing", 116, 328, MUTED);
            for (int i = 0; i < 3; i++) circle(105 + i * 11, 361, 2.5F,
                    ((System.currentTimeMillis() / 450) % 3) == i ? 0xFF84E6B1 : 0xFF334E4F);
            keyHints(116, 393, 194, MUTED, phone.shortKey(Action.HOME), "Home");
            return;
        }
        heading("Telephone", phone.hasCallEnded() ? "Call ended · Choose a contact" : "Choose a contact to call");
        contactRows(phone, false, 40);
        keyHints(116, 407, 194, MUTED, phone.shortKey(Action.UP), "", phone.shortKey(Action.DOWN), "Browse",
                phone.shortKey(Action.SELECT), "Call");
    }
    private void messages(PhoneModule phone) {
        if (!phone.isMessageOpen()) {
            int unread = phone.unreadMessages();
            heading("Messages", unread == 0 ? "7 conversations" : unread + " unread · 7 conversations");
            contactRows(phone, true, 40);
            keyHints(116, 407, 194, BLUE, phone.shortKey(Action.UP), "", phone.shortKey(Action.DOWN), "Browse",
                    phone.shortKey(Action.SELECT), "Open chat");
            return;
        }
        PhoneContact contact = phone.selectedContact();
        PhoneConversation chat = phone.conversation();
        heading(contact.name, contact == PhoneContact.TRUMP ? "Fictional parody · 911" : contact.number);
        avatar(contact, 178, 59, 28);
        List<PhoneConversation.Message> messages = phone.conversation().messages();
        boolean typing = phone.conversation().isTyping() && phone.messageScroll() == 0;
        if (messages.isEmpty() && !typing) {
            avatar(contact, 89, 155, 54);
            centered(TEXT, "Say hello to " + contact.name, 116, 232, WHITE);
            centered(SMALL, "Send a message to get started.", 116, 255, MUTED);
        } else {
            try (PhoneClip ignored = new PhoneClip(21, 109, 190, 247)) {
                int bottom = 354;
                if (typing) {
                    avatar(contact, 22, 326, 22);
                    round(49, 326, 55, 25, 12, 0xFF2D3A50);
                    for (int i = 0; i < 3; i++) {
                        double phase = phone.conversation().typingElapsed() / 180D - i * .8;
                        float bounce = (float) Math.max(0, Math.sin(phase)) * 3;
                        circle(63 + i * 13, 339 - bounce, 2.5F, 0xFFABBED8);
                    }
                    bottom = 317;
                }
                for (int index = messages.size() - 1 - phone.messageScroll(); index >= 0 && bottom > 109; index--) {
                    PhoneConversation.Message message = messages.get(index);
                    List<String> lines = wrap(message.text, 154);
                    int height = 24 + lines.size() * 12 + (message.attachment ? 107 : 0);
                    int y = bottom - height;
                    int left = message.incoming ? 22 : 38;
                    round(left, y, 172, height, 12, message.incoming ? 0xFF2D3A50 : 0xFF285AA0);
                    for (int line = 0; line < lines.size(); line++) text(TEXT, lines.get(line), left + 9, y + 8 + line * 12, WHITE);
                    if (message.attachment) asset(DISCORD_IMAGES, left + 7, y + 11 + lines.size() * 12, 158, 101, 7);
                    String time = new SimpleDateFormat("HH:mm").format(new Date(message.sentAt));
                    text(SMALL, time, left + 162 - SMALL.width(time), bottom - 14, message.incoming ? 0xFFABBED8 : 0xFFD1E3FF);
                    bottom = y - 9;
                }
            }
        }
        if (typing) text(SMALL, "Typing...", 22, 99, BLUE);
        if (chat.isBlocked()) {
            round(24, 360, 184, 40, 13, 0xFF493140);
            centered(TEXT, "You are blocked", 116, 365, 0xFFF49CA5);
            centered(SMALL, "Re-enable Phone to reset", 116, 383, MUTED);
            keyHints(116, 407, 194, MUTED, phone.shortKey(Action.UP), "", phone.shortKey(Action.DOWN), "History",
                    phone.shortKey(Action.BACK), "Chats");
        } else {
            round(24, 368, 184, 31, 13, 0xFF253C58);
            keyHints(116, 375, 174, WHITE, phone.shortKey(Action.SELECT), "Send a message");
            keyHints(116, 405, 194, MUTED, phone.shortKey(Action.UP), "", phone.shortKey(Action.DOWN), "History",
                    phone.shortKey(Action.BACK), "Chats");
        }
    }
    private void avatar(PhoneContact contact, int x, int y, int size) {
        if (contact.avatar == null) {
            circle(x + size / 2F, y + size / 2F, size / 2F, contact.color);
            centered(size < 32 ? TEXT : TITLE, contact.name.substring(0, 1), x + size / 2F, y + size / 2F - (size < 32 ? 6 : 9), WHITE);
        } else {
            ResourceLocation location = avatars.get(contact);
            if (location == null) { location = new ResourceLocation("vibe", "textures/phone/" + contact.avatar); avatars.put(contact, location); }
            asset(location, x, y, size, size, size / 2);
        }
    }
    private List<String> wrap(String value, float width) {
        List<String> lines = new ArrayList<String>();
        String line = "";
        for (String word : value.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && TEXT.width(next) > width) { lines.add(line); line = word; }
            else line = next;
        }
        if (!line.isEmpty()) lines.add(line);
        return lines;
    }
    private void asset(ResourceLocation location, int x, int y, int width, int height, int radius) {
        mc.getTextureManager().bindTexture(location);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        texturedRound(x, y, width, height, radius, 0, 0, 1, 1);
    }
    private static void receiver(float x, float y, float scale, int color) {
        GlStateManager.pushMatrix(); GlStateManager.translate(x, y, 0); GlStateManager.scale(scale, scale, 1);
        try {
            // A continuous curved handle, with rounded earpieces aligned to its ends.
            solid(color); GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
            for (int i = 0; i <= 32; i++) {
                float t = i / 32F, u = 1 - t;
                float px = -10 * u * u * u - 36 * u * u * t + 10 * t * t * t;
                float py = -10 * u * u * u + 36 * u * t * t + 10 * t * t * t;
                float dx = -6 * u * u + 72 * u * t + 30 * t * t;
                float dy = 30 * u * u + 72 * u * t - 6 * t * t;
                float length = (float) Math.sqrt(dx * dx + dy * dy);
                float nx = -dy / length * 3, ny = dx / length * 3;
                GL11.glVertex2f(px + nx, py + ny); GL11.glVertex2f(px - nx, py - ny);
            }
            GL11.glEnd(); endSolid();
            GlStateManager.pushMatrix(); GlStateManager.translate(-10, -10, 0); GlStateManager.rotate(-24, 0, 0, 1);
            round(-5, -7, 10, 14, 3.5F, color); GlStateManager.popMatrix();
            GlStateManager.pushMatrix(); GlStateManager.translate(10, 10, 0); GlStateManager.rotate(24, 0, 0, 1);
            round(-7, -5, 14, 10, 3.5F, color); GlStateManager.popMatrix();
        } finally { GlStateManager.popMatrix(); }
    }
    private void boot(double seconds) {
        round(8, 9, 216, 437, 30, 0xFF111427);
        circle(116, 188, 35, 0xFF3F437C);
        line(0xFFD8E6FF, 6, 99, 179, 116, 202, 133, 179);
        centered(TITLE, "VibeOS", 116, 242, WHITE);
        centered(SMALL, "A world in your pocket.", 116, 270, MUTED);
        round(64, 315, 104, 3, 1, 0xFF29304A);
        round(64, 315, (int) (104 * Math.min(1, seconds / .8)), 3, 1, 0xFFAEC3FF);
    }
    private void updateHand(PhoneModule phone) {
        long now = System.nanoTime();
        double elapsed = previousHandFrame == 0 ? 1D / 60 : Math.min(.05, (now - previousHandFrame) / 1e9);
        previousHandFrame = now;
        float x = 48, y = 338;
        switch (phone.app()) {
            case HOME: x = 50 + phone.homeSelection() % 3 * 66; y = 205 + phone.homeSelection() / 3 * 68; break;
            case SETTINGS:
                int first = Math.max(0, Math.min(phone.settingsCount() - 7, phone.settingSelection() - 5));
                x = 108; y = 126 + (phone.settingSelection() - first) * 38; break;
            case CAMERA: x = 116; y = 377; break;
            case FLAPPY: x = 78; y = 256; break;
            case TETRIS: x = phone.lastAction()==Action.SELECT?126:72; y = 408; break;
            case PHOTOS:
                x = phone.isPhotoOpen() ? 116 : 67 + phone.photoSelection() % 2 * 95;
                y = phone.isPhotoOpen() ? 380 : 146 + Math.min(2, phone.photoSelection() / 2) * 88; break;
            case CONTACTS:
                x = 106; y = 127 + phone.contactSelection() * 38;
                if (phone.lastAction() == Action.LEFT || phone.lastAction() == Action.RIGHT || phone.lastAction() == Action.SELECT) {
                    x = 68 + phone.contactAction() * 96; y = 413;
                }
                break;
            case TELEPHONE: x = 116; y = phone.isCalling() ? 398 : 127 + phone.contactSelection() * 40; break;
            case MESSAGES: x = 116; y = phone.isMessageOpen() ? 384 : 127 + phone.contactSelection() * 40; break;
            case INTERNET: x = 116; y = 347; break;
            default: break;
        }
        if (phone.lastAction() == Action.HOME) { x = 116; y = 433; }
        double age = phone.interactionAt() == 0 || phone.hideThumb.isEnabled() ? 10
                : (System.currentTimeMillis() - phone.interactionAt()) / 1000D;
        boolean press = phone.lastAction() == Action.SELECT || phone.lastAction() == Action.HOME
                || phone.lastAction() == Action.BACK || phone.lastAction() == Action.DELETE;
        handPose.advance(x, y, age, press, elapsed);
    }
    private void hand(boolean front) {
        if (arm == null) {
            ModelBase model = new ModelBase() { }; model.textureWidth = model.textureHeight = 64;
            arm = new ModelRenderer(model, 32, 48); arm.addBox(-2, 0, -2, 4, 12, 4);
            sleeve = new ModelRenderer(model, 48, 48); sleeve.addBox(-2, 0, -2, 4, 12, 4, .25F);
            slimArm = new ModelRenderer(model, 32, 48); slimArm.addBox(-1.5F, 0, -2, 3, 12, 4);
            slimSleeve = new ModelRenderer(model, 48, 48); slimSleeve.addBox(-1.5F, 0, -2, 3, 12, 4, .25F);
            thumb = new ModelRenderer(model, 34, 56); thumb.addBox(-1, -2, -1, 2, 2, 2);
            thumbTip = new ModelRenderer(model, 34, 58); thumbTip.addBox(-1, -2, -1, 2, 2, 2);
            thumbTip.setRotationPoint(0, -2, 0); thumb.addChild(thumbTip);
        }
        GlStateManager.pushMatrix();
        try {
            GlStateManager.color(1, 1, 1, 1);
            mc.getTextureManager().bindTexture(mc.thePlayer.getLocationSkin());
            GlStateManager.disableCull(); GlStateManager.enableAlpha(); GlStateManager.alphaFunc(GL11.GL_GREATER, .1F);
            GlStateManager.enableDepth(); GlStateManager.depthMask(true);
            GlStateManager.enableRescaleNormal();
            RenderHelper.enableGUIStandardItemLighting();
            if (front) {
                // Negative local Y puts the fingertip above its base. Rotate it
                // inward toward the screen, then flex the distal joint to tap.
                GlStateManager.translate(handPose.gripX, handPose.gripY, 75);
                GlStateManager.rotate(handPose.angle, 0, 0, 1);
                GlStateManager.rotate(24, 0, 1, 0); GlStateManager.scale(175, 175, 175);
                thumbTip.rotateAngleX = handPose.curl; thumb.render(.0625F);
            } else {
                GlStateManager.translate(handPose.gripX + 47, handPose.gripY + 161, 35); GlStateManager.rotate(-19, 0, 0, 1);
                GlStateManager.rotate(-12, 0, 1, 0); GlStateManager.scale(235, -235, 235);
                boolean slim = "slim".equals(mc.thePlayer.getSkinType());
                (slim ? slimArm : arm).render(.0625F);
                if (mc.thePlayer.isWearing(EnumPlayerModelParts.LEFT_SLEEVE)) (slim ? slimSleeve : sleeve).render(.0625F);
            }
        } finally {
            RenderHelper.disableStandardItemLighting(); GlStateManager.disableDepth(); GlStateManager.depthMask(false);
            GlStateManager.disableRescaleNormal();
            GlStateManager.popMatrix(); GlStateManager.color(1, 1, 1, 1);
        }
    }
    private static void icon(int index, float x, float y, int size) {
        int[] colors = {0xFF626D81, 0xFF8395AD, 0xFFFFBC4B, 0xFFF5F1EF, 0xFF419DDF, 0xFF8E94AE, 0xFF35AF72, 0xFF36A98B,0xFF514475};
        round((int)x, (int)y, size, size, size * .25F, colors[index]);
        GlStateManager.pushMatrix(); GlStateManager.translate(x + size / 2F, y + size / 2F, 0); GlStateManager.scale(size / 49F, size / 49F, 1);
        try {
            switch (index) {
                case 0:
                    circle(0, 0, 13, 0xFFC5CEDD); circle(0, 0, 9, 0xFF626D81); circle(0, 0, 4, WHITE);
                    for (int i = 0; i < 8; i++) { GlStateManager.pushMatrix(); GlStateManager.rotate(i * 45, 0, 0, 1); rect(-3, -17, 6, 7, 0xFFDCE3EE); GlStateManager.popMatrix(); }
                    break;
                case 1:
                    round(-16, -10, 32, 23, 6, 0xFF202C40); round(-10, -15, 13, 7, 3, 0xFF202C40);
                    circle(0, 1, 8, 0xFFC9D5E7); circle(0, 1, 5.5F, 0xFF283E5D); circle(-2, -1, 2, 0xFF4E89B4); circle(11, -6, 1.5F, WHITE); break;
                case 2:
                    circle(-1, 1, 12, 0xFFFFE99F); circle(5, -3, 6, WHITE); circle(7, -3, 2, 0xFF27354A);
                    round(6, 4, 13, 5, 2, 0xFFEB7746); circle(-8, 4, 5, 0xFFFFD161); break;
                case 3:
                    int[] petals = {0xFFE992BC, 0xFFE8747F, 0xFFF6B26B, 0xFFEED47F, 0xFF92C99A, 0xFF7ECACC, 0xFF8EA8DC, 0xFFAD94CA};
                    for (int i = 0; i < 8; i++) { double a = Math.PI * 2 * i / 8; circle((float)Math.cos(a) * 9, (float)Math.sin(a) * 9, 7, petals[i]); }
                    circle(0, 0, 6, 0xFFFDF9EB); break;
                case 4:
                    circle(0, 0, 16, WHITE); circle(0, 0, 13, 0xFF4899D5);
                    line(WHITE, 1.5F, -14, -4, 14, -4); line(WHITE, 1.5F, -14, 5, 14, 5);
                    line(WHITE, 1.5F, 0, -15, -6, -8, -8, 0, -6, 8, 0, 15, 6, 8, 8, 0, 6, -8, 0, -15); break;
                case 5:
                    circle(0, -7, 7, WHITE); round(-12, 3, 24, 14, 7, WHITE); break;
                case 6: receiver(0, 0, 1, WHITE); break;
                case 7:
                    round(-16, -12, 32, 23, 9, WHITE); triangle(-12, 5, -12, 17, -1, 7, WHITE);
                    for (int i = -1; i <= 1; i++) circle(i * 8, -1, 2, 0xFF36A98B);
                    break;
                case 8:
                    rect(-15,-3,9,9,0xFF62D8E9);rect(-5,-3,9,9,0xFF62D8E9);rect(5,-3,9,9,0xFF62D8E9);rect(-5,-13,9,9,0xFF62D8E9);
                    rect(-15,7,9,9,0xFFF1CE76);rect(-5,7,9,9,0xFFBA91EF);rect(5,7,9,9,0xFFBA91EF);break;
                default: break;
            }
        } finally { GlStateManager.popMatrix(); }
    }
    private float keyWidth(String key) { return Math.max(16, KEY.width(key) + 10); }
    private void keycap(String key, float x, float y, int foreground) {
        int width = (int) Math.ceil(keyWidth(key));
        round(Math.round(x), Math.round(y), width, 17, 4, 0xCC71849E);
        round(Math.round(x) + 1, Math.round(y) + 1, width - 2, 14, 3, 0xFF28384E);
        centered(KEY, key, x + width / 2F, y + 2, foreground);
    }
    /** Alternating key/label pairs; every key gets the same visible keycap. */
    private void keyHints(float centerX, float y, float available, int foreground, String... pairs) {
        float width = 0;
        for (int i = 0; i < pairs.length; i += 2)
            width += keyWidth(pairs[i]) + (pairs[i + 1].isEmpty() ? 0 : 5 + SMALL.width(pairs[i + 1])) + (i == 0 ? 0 : 10);
        float scale = Math.min(1, available / Math.max(1, width));
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(centerX - width * scale / 2, y, 0); GlStateManager.scale(scale, scale, 1);
            float x = 0;
            for (int i = 0; i < pairs.length; i += 2) {
                if (i > 0) x += 10;
                keycap(pairs[i], x, 0, WHITE); x += keyWidth(pairs[i]);
                if (!pairs[i + 1].isEmpty()) { x += 5; text(SMALL, pairs[i + 1], x, 2, foreground); x += SMALL.width(pairs[i + 1]); }
            }
        } finally { GlStateManager.popMatrix(); }
    }
    private static void text(PhoneFont font, String value, float x, float y, int color) { font.draw(value, x, y, color); }
    private static void centered(PhoneFont font, String value, float x, float y, int color) { font.draw(value, x - font.width(value) / 2, y, color); }
    private static void solid(int color) {
        GlStateManager.disableTexture2D(); GlStateManager.enableBlend(); GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771); color(color);
    }
    private static void endSolid() { GlStateManager.enableTexture2D(); GlStateManager.color(1, 1, 1, 1); }
    private static float cornerX(float x, float w, float r, int c, double angle) {
        return x + (c == 0 || c == 3 ? w - r : r) + (float) Math.cos(angle) * r;
    }
    private static float cornerY(float y, float h, float r, int c, double angle) {
        return y + (c < 2 ? h - r : r) + (float) Math.sin(angle) * r;
    }
    private static void round(int x, int y, int w, int h, float radius, int color) {
        if (w <= 0 || h <= 0) return;
        float r = Math.max(0, Math.min(radius, Math.min(w, h) / 2F));
        int steps = Math.max(8, (int) Math.ceil(r * 1.5));
        solid(color); GL11.glBegin(GL11.GL_TRIANGLE_FAN); GL11.glVertex2f(x + w / 2F, y + h / 2F);
        for (int c = 0; c < 4; c++) for (int i = 0; i <= steps; i++) {
            double angle = Math.PI / 2 * (c + i / (double) steps);
            GL11.glVertex2f(cornerX(x, w, r, c, angle), cornerY(y, h, r, c, angle));
        }
        GL11.glVertex2f(x + w, y + h - r); GL11.glEnd(); endSolid();
    }
    private static void outline(int x, int y, int w, int h, float radius, float width, int color) {
        float r = Math.min(radius, Math.min(w, h) / 2F), inner = Math.max(0, r - width);
        int steps = Math.max(8, (int) Math.ceil(r * 1.5));
        solid(color); GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int c = 0; c <= 4; c++) for (int i = 0; i <= (c == 4 ? 0 : steps); i++) {
            double angle = Math.PI / 2 * (c % 4 + i / (double) steps);
            GL11.glVertex2f(cornerX(x, w, r, c % 4, angle), cornerY(y, h, r, c % 4, angle));
            GL11.glVertex2f(cornerX(x + width, w - 2 * width, inner, c % 4, angle), cornerY(y + width, h - 2 * width, inner, c % 4, angle));
        }
        GL11.glEnd(); endSolid();
    }
    private static void rect(float x, float y, float w, float h, int color) {
        solid(color);
        GL11.glBegin(GL11.GL_QUADS); GL11.glVertex2f(x, y); GL11.glVertex2f(x, y + h); GL11.glVertex2f(x + w, y + h); GL11.glVertex2f(x + w, y); GL11.glEnd();
        endSolid();
    }
    private static void circle(float x, float y, float radius, int color) {
        solid(color);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN); GL11.glVertex2f(x, y);
        int steps = Math.max(32, (int) Math.ceil(radius * 6));
        for (int i = 0; i <= steps; i++) { double a = i * Math.PI * 2 / steps; GL11.glVertex2d(x + Math.cos(a) * radius, y + Math.sin(a) * radius); }
        GL11.glEnd(); endSolid();
    }
    private static void triangle(float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        solid(color); GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(x1, y1); GL11.glVertex2f(x2, y2); GL11.glVertex2f(x3, y3); GL11.glEnd();
        endSolid();
    }
    private static void line(int color, float width, float... coordinates) {
        solid(color); GL11.glBegin(GL11.GL_QUADS);
        for (int i = 2; i < coordinates.length; i += 2) {
            float x0 = coordinates[i - 2], y0 = coordinates[i - 1], x1 = coordinates[i], y1 = coordinates[i + 1];
            float length = (float) Math.hypot(x1 - x0, y1 - y0);
            if (length == 0) continue;
            float dx = (y1 - y0) * width / (2 * length), dy = (x0 - x1) * width / (2 * length);
            GL11.glVertex2f(x0 + dx, y0 + dy); GL11.glVertex2f(x0 - dx, y0 - dy);
            GL11.glVertex2f(x1 - dx, y1 - dy); GL11.glVertex2f(x1 + dx, y1 + dy);
        }
        GL11.glEnd(); endSolid();
    }
    private static void color(int color) { GlStateManager.color((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, (color >>> 24) / 255F); }
    private static void texturedRound(float x, float y, float width, float height, float radius, float u0, float v0, float u1, float v1) {
        GlStateManager.enableTexture2D(); GlStateManager.enableBlend(); GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771); GlStateManager.color(1, 1, 1, 1);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glTexCoord2f((u0 + u1) / 2, (v0 + v1) / 2); GL11.glVertex2f(x + width / 2, y + height / 2);
        int steps = Math.max(8, (int) Math.ceil(radius * 1.5));
        for (int corner = 0; corner <= 4; corner++) {
            int c = corner % 4;
            float cx = c == 0 || c == 3 ? x + width - radius : x + radius;
            float cy = c < 2 ? y + height - radius : y + radius;
            for (int i = 0; i <= (corner == 4 ? 0 : steps); i++) {
                double a = Math.PI / 2 * (c + i / (double) steps);
                float px = cx + (float) Math.cos(a) * radius, py = cy + (float) Math.sin(a) * radius;
                GL11.glTexCoord2f(u0 + (px - x) / width * (u1 - u0), v0 + (py - y) / height * (v1 - v0)); GL11.glVertex2f(px, py);
            }
        }
        GL11.glEnd();
    }
    private static void restoreState() {
        GlStateManager.enableTexture2D(); GlStateManager.enableBlend(); GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableAlpha(); GlStateManager.alphaFunc(GL11.GL_GREATER, .1F); GlStateManager.disableDepth();
        GlStateManager.depthMask(true); GlStateManager.color(1, 1, 1, 1);
    }
    private void releasePreview() { GlStateManager.deleteTexture(previewTexture); previewTexture = previewWidth = previewHeight = 0; }
    public void close() {
        visibility = 0; previousFrame = 0;
        canvas.close(); TEXT.close(); SMALL.close(); TITLE.close(); CLOCK.close(); KEY.close();
        handPose.reset(); previousHandFrame = 0;
        if (previewTexture != 0) releasePreview();
        if (wallpaperLocation != null) mc.getTextureManager().deleteTexture(wallpaperLocation);
        wallpaperTexture = null;
        for (PhotoTexture texture : textures.values()) mc.getTextureManager().deleteTexture(texture.location);
        textures.clear();
    }
    private static final class PhotoTexture {
        final DynamicTexture texture; final ResourceLocation location; final int width, height;
        PhotoTexture(DynamicTexture texture, ResourceLocation location, int width, int height) {
            this.texture = texture; this.location = location; this.width = width; this.height = height;
        }
    }
}
