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
    private static final ResourceLocation ESOUND = new ResourceLocation("vibe", "textures/phone/esound.jpg");
    private static final ResourceLocation DISCORD_IMAGES = new ResourceLocation("vibe", "textures/phone/fake-discord-scams.gif");
    private final PhoneFont TEXT = new PhoneFont(Font.PLAIN, 9.5F);
    private final PhoneFont SMALL = new PhoneFont(Font.PLAIN, 8.5F);
    private final PhoneFont TITLE = new PhoneFont(Font.BOLD, 14);
    private final PhoneFont CLOCK = new PhoneFont(Font.PLAIN, 32.25F);
    private final PhoneCanvas canvas = new PhoneCanvas();
    private static final int WHITE = 0xFFF4F5FF, MUTED = 0xFF9EABC8, BLUE = 0xFF8ABEFF;
    private final Minecraft mc = Minecraft.getMinecraft();
    private long previousFrame, previewAt;
    private float visibility;
    private DynamicTexture wallpaperTexture;
    private ResourceLocation wallpaperLocation;
    private int wallpaperKey;
    private int previewTexture, previewWidth, previewHeight;
    private final LinkedHashMap<Path, PhotoTexture> textures = new LinkedHashMap<Path, PhotoTexture>(12, .75F, true);
    private ModelRenderer arm, sleeve, slimArm, slimSleeve, thumb;
    private float smoothedYaw;

    /** Called before ALL HUD elements: the preview and PNG contain the world, never the phone UI. */
    public void captureWorld(PhoneModule phone) {
        if (phone == null || !phone.isEnabled() || phone.app() != App.CAMERA || mc.theWorld == null
                || mc.thePlayer == null || mc.currentScreen != null || mc.gameSettings.thirdPersonView != 0) return;
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
                && mc.currentScreen == null && mc.gameSettings.thirdPersonView == 0 && !mc.gameSettings.hideGUI;
        visibility += ((available ? 1 : 0) - visibility) * (float) (1 - Math.exp(-elapsed * 13));
        if (phone == null || visibility < .005F) {
            if (previewTexture != 0) releasePreview();
            canvas.close();
            return;
        }
        if (available) phone.advance(elapsed);
        float bottomMargin = phone.hints.isEnabled() ? 25 : 8;
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
        if (available && phone.hints.isEnabled()) {
            String help = phone.shortKey(Action.UP) + "/" + phone.shortKey(Action.DOWN) + "/" + phone.shortKey(Action.LEFT) + "/" + phone.shortKey(Action.RIGHT)
                    + " move   " + phone.shortKey(Action.SELECT) + " select   " + phone.shortKey(Action.HOME) + " home   " + phone.shortKey(Action.BACK) + " back";
            float x = 17, y = resolution.getScaledHeight() - 14;
            text(SMALL, help, x, y, 0xDDE6EAFF);
            restoreState();
        }
    }

    /** Same device renderer used by offscreen verification. */
    public void drawDevice(PhoneModule phone, boolean drawHand) {
        if (!canvas.draw(() -> drawContents(phone, drawHand))) drawContents(phone, drawHand);
    }

    private void drawContents(PhoneModule phone, boolean drawHand) {
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableBlend(); GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
        GlStateManager.disableCull(); GlStateManager.disableLighting();
        GlStateManager.disableDepth(); GlStateManager.depthMask(false);
        if (drawHand && mc.thePlayer != null) hand(false);
        round(-7, 8, WIDTH + 13, HEIGHT + 8, 40, 0x40000000);
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
            round(12, 47, 208, 365, 19, phone.app() == App.FLAPPY ? 0xFF132732 : 0xF20D1220);
        }
        GlStateManager.pushMatrix();
        float enter = Math.min(1, Math.max(0, (System.currentTimeMillis() - phone.appAt()) / 180F));
        float appScale = .97F + .03F * (1 - (1 - enter) * (1 - enter));
        GlStateManager.translate(116, 230, 0); GlStateManager.scale(appScale, appScale, 1); GlStateManager.translate(-116, -230, 0);
        switch (phone.app()) {
            case HOME: home(phone); break;
            case SETTINGS: settings(phone); break;
            case CAMERA: camera(phone); break;
            case FLAPPY: flappy(phone); break;
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
            round(18, 379, 196, 35, 13, 0xF22D3650);
            centered(SMALL, SMALL.fit(notification, 180), WIDTH / 2, 390, WHITE);
        }
        double boot = (System.currentTimeMillis() - phone.openedAt()) / 1000.0;
        if (boot >= 0 && boot < .90) boot(boot);
        float shade = (100 - phone.brightness.getFloat()) / 100;
        if (shade > 0) round(8, 9, 216, 437, 30, ((int) (shade * 205) << 24));
        if (drawHand && mc.thePlayer != null) hand(true);
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
        String time = new SimpleDateFormat("HH:mm").format(new Date());
        centered(CLOCK, time, WIDTH / 2F, 64, WHITE);
        centered(SMALL, new SimpleDateFormat("EEEE, d MMMM", Locale.ENGLISH).format(new Date()), WIDTH / 2, 110, 0xE6E0E6FF);
        round(24, 133, 184, 42, 14, 0x24405188);
        text(SMALL, "Your world. In your hand.", 35, 139, 0xE6D5E2FF);
        text(TITLE, "VibeOS", 35, 152, WHITE);
        for (int i = 0; i < PhoneModule.appCount(); i++) {
            int x = 31 + (i % 3) * 65, y = 190 + (i / 3) * 66;
            boolean focused = phone.homeSelection() == i;
            if (focused) round(x - 6, y - 5, 54, 64, 14, 0x3CFFFFFF);
            icon(i, x, y, 42);
            centered(SMALL, PhoneModule.appName(i), x + 21, y + 46, WHITE);
            if (focused) round(x + 13, y + 58, 16, 2, 1, 0xE8DDEAFF);
        }
        round(37, 395, 158, 23, 11, 0x26323D67);
        centered(SMALL, phone.shortKey(Action.SELECT) + " to open " + PhoneModule.appName(phone.homeSelection()), 116, 402, 0xE3F1F3FF);
    }
    private void status(PhoneModule phone) {
        text(SMALL, new SimpleDateFormat("HH:mm").format(new Date()), 25, 24, WHITE);
        round(86, 18, 61, 16, 8, 0xFF02040A);
        circle(135, 26, 3, 0xFF101D30); circle(135, 26, 1.3F, 0xFF284964);
        if (phone.app() == App.CAMERA) circle(155, 26, 2, 0xFF75E9A5);
        for (int i = 0; i < 3; i++) round(173 + i * 4, 29 - i * 3, 2, 3 + i * 3, 0, WHITE);
        outline(190, 22, 17, 9, 2, 1, 0xCCFFFFFF); round(192, 24, 12, 5, 1, WHITE); round(208, 25, 2, 3, 1, 0xAAFFFFFF);
    }
    private void heading(String title, String subtitle) {
        text(TITLE, title, 25, 61, WHITE);
        text(SMALL, subtitle, 25, 83, MUTED);
    }
    private void settings(PhoneModule phone) {
        heading("Settings", "Make this phone yours.");
        int selected = phone.settingSelection();
        int first = Math.max(0, Math.min(phone.settingsCount() - 7, selected - 5));
        for (int i = first; i < Math.min(first + 7, phone.settingsCount()); i++) {
            int y = 108 + (i - first) * 38;
            boolean active = selected == i;
            round(22, y, 188, 34, 9, active ? 0xFF283F66 : 0xFF192132);
            text(TEXT, phone.settingLabel(i), 31, y + 5, active ? WHITE : 0xFFCAD3E7);
            String value = phone.binding() == i - 8 && i >= 8 && i < 16 ? "Press a key..." : phone.settingValue(i);
            text(SMALL, value, 31, y + 20, active ? BLUE : MUTED);
            if (i == 0 || i == 2 || i == 3) {
                int color = i == 0 ? phone.phoneColor.getArgb() : i == 2 ? phone.backgroundTop.getArgb() : phone.backgroundBottom.getArgb();
                round(181, y + 9, 17, 17, 5, color | 0xFF000000);
            } else text(SMALL, i >= 8 && i < 16 ? "Key" : "<  >", 179, y + 14, active ? BLUE : MUTED);
        }
        round(214, 109, 2, 263, 1, 0xFF26314A);
        round(214, 109 + (int) (selected * 235F / 16), 2, 28, 1, BLUE);
        centered(SMALL, selected >= 8 && selected < 16 ? phone.shortKey(Action.SELECT) + " rebind  /  Esc cancel" : "Left / right to change", 116, 393, MUTED);
    }
    private void camera(PhoneModule phone) {
        heading("Camera", "Capture a moment in your world.");
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
        text(SMALL, phone.shortKey(Action.SELECT), 173, 372, MUTED);
        float flash = Math.max(0, 1 - (System.currentTimeMillis() - phone.flashAt()) / 220F);
        if (flash > 0) round(21, 107, 190, 220, 15, ((int) (flash * 220) << 24) | 0xFFFFFF);
        if (phone.photos().isSaving()) centered(SMALL, "Saving...", 116, 414, BLUE);
    }
    private void photos(PhoneModule phone) {
        List<PhonePhotoLibrary.Photo> photos = phone.photos().photos();
        heading("Photos", photos.size() + (photos.size() == 1 ? " memory, saved locally" : " memories, saved locally"));
        if (photos.isEmpty()) {
            icon(3, 86, 153, 60);
            centered(TITLE, "Your world, saved.", 116, 235, WHITE);
            centered(SMALL, "Take your first photo in Camera.", 116, 260, MUTED);
        } else if (phone.isPhotoOpen()) {
            // Draw one immutable album snapshot even if a background refresh finishes now.
            PhonePhotoLibrary.Photo photo = photos.get(Math.min(phone.photoSelection(), photos.size() - 1));
            drawPhoto(phone, photo, 21, 112, 190, 210, false);
            centered(TEXT, new SimpleDateFormat("d MMM yyyy  HH:mm").format(new Date(photo.taken)), 116, 339, WHITE);
            centered(SMALL, "PNG  /  " + Math.max(1, photo.bytes / 1024) + " KB", 116, 358, MUTED);
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
        centered(SMALL, phone.shortKey(Action.SELECT) + " view   " + phone.shortKey(Action.DELETE) + " delete", 116, 403, MUTED);
        if (phone.isConfirmDelete()) {
            round(13, 49, 206, 368, 20, 0xAB030613);
            round(27, 166, 178, 150, 18, 0xFF242D40);
            centered(TITLE, "Delete this photo?", 116, 187, WHITE);
            centered(SMALL, "This removes the saved PNG.", 116, 216, MUTED);
            round(40, 242, 152, 29, 9, 0xFFC74660);
            centered(TEXT, phone.shortKey(Action.SELECT) + "  Delete photo", 116, 251, WHITE);
            centered(SMALL, phone.shortKey(Action.BACK) + "  Keep it", 116, 287, 0xFFD2DCEE);
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
        heading("Internet", "A little window to the web.");
        round(22, 108, 188, 27, 12, 0xFF202B40);
        centered(SMALL, "youtube.com", 116, 117, 0xFFBAC8DF);
        round(77, 159, 78, 58, 17, 0xFFED3152);
        triangle(108, 174, 108, 202, 130, 188, WHITE);
        centered(TITLE, "@heisthacksjp", 116, 240, WHITE);
        centered(SMALL, "YouTube", 116, 266, MUTED);
        centered(SMALL, "Watch in your regular browser.", 116, 291, MUTED);
        round(31, 328, 170, 37, 12, 0xFF326ADB);
        centered(TEXT, phone.shortKey(Action.SELECT) + "  Open YouTube", 116, 341, WHITE);
        centered(SMALL, "Your game stays open.", 116, 384, MUTED);
    }
    private void flappy(PhoneModule phone) {
        heading("Flappy Bird", "Best " + phone.bestScore.getInt() + "  /  " + phone.shortKey(Action.SELECT) + " to flap");
        GlStateManager.pushMatrix(); GlStateManager.translate(20, 107, 0);
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
                centered(SMALL, phone.shortKey(Action.SELECT) + (phone.game().getPhase() == FlappyGame.Phase.OVER ? " to restart" : " to flap"), 96, 169, 0xFFE7D994);
                if (phone.game().getPhase() == FlappyGame.Phase.OVER) centered(SMALL, "Score " + phone.game().getScore(), 96, 185, MUTED);
            }
        } finally { GlStateManager.popMatrix(); }
        centered(SMALL, "Fly through the gaps. Stay curious.", 116, 384, MUTED);
    }
    private static void pipe(float x, float y, float height) {
        if (height <= 0) return;
        rect(x, y, 27, height, 0xFF3C7944); rect(x + 2, y, 22, height, 0xFF70BE69);
        rect(x + 4, y, 4, height, 0xFFA1DB7C);
        float cap = y == 0 ? height - 10 : y;
        rect(x - 2, cap, 31, 10, 0xFF3C7944); rect(x, cap + 2, 27, 6, 0xFF82C969);
    }
    private void contacts(PhoneModule phone) {
        heading("Contacts", phone.isContactOpen() ? "Contact details" : "1 contact");
        if (!phone.isContactOpen()) {
            round(22, 113, 188, 66, 14, 0xFF283F62);
            asset(ESOUND, 31, 126, 40, 40, 20);
            text(TEXT, PhoneConversation.CONTACT, 82, 126, WHITE);
            text(SMALL, PhoneConversation.NUMBER, 82, 146, MUTED);
            centered(SMALL, phone.shortKey(Action.SELECT) + " View contact", 116, 203, BLUE);
            centered(SMALL, "All your people, one little screen.", 116, 380, MUTED);
            return;
        }
        asset(ESOUND, 79, 112, 74, 74, 37);
        centered(TITLE, PhoneConversation.CONTACT, 116, 202, WHITE);
        centered(TEXT, PhoneConversation.NUMBER, 116, 227, MUTED);
        for (int i = 0; i < 2; i++) {
            int x = 30 + i * 90;
            round(x, 274, 82, 70, 14, phone.contactAction() == i ? 0xFF345D8D : 0xFF202D42);
            if (i == 0) receiver(x + 41, 296, .65F, 0xFF90E7B4);
            else { round(x + 29, 286, 25, 18, 7, BLUE); triangle(x + 31, 300, x + 31, 308, x + 39, 300, BLUE); }
            centered(TEXT, i == 0 ? "Call" : "Message", x + 41, 319, WHITE);
        }
        centered(SMALL, "Left / right to choose", 116, 365, MUTED);
        centered(SMALL, phone.shortKey(Action.SELECT) + " Select   " + phone.shortKey(Action.BACK) + " Contacts", 116, 391, MUTED);
    }
    private void telephone(PhoneModule phone) {
        if (phone.isCalling()) {
            centered(SMALL, "VibeOS telephone", 116, 70, MUTED);
            centered(TITLE, PhoneConversation.CONTACT, 116, 113, WHITE);
            centered(TEXT, PhoneConversation.NUMBER, 116, 141, MUTED);
            float pulse = (float) (.5 + .5 * Math.sin((System.currentTimeMillis() - phone.callStartedAt()) / 500D));
            circle(116, 204, 49 + pulse * 7, 0x1F68E5AD);
            circle(116, 204, 44 + pulse * 3, 0x2868E5AD);
            asset(ESOUND, 76, 164, 80, 80, 40);
            centered(TITLE, "Calling...", 116, 275, WHITE);
            centered(SMALL, "Waiting for ESound", 116, 301, MUTED);
            for (int i = 0; i < 3; i++) circle(105 + i * 11, 331, 2.5F,
                    ((System.currentTimeMillis() / 450) % 3) == i ? 0xFF84E6B1 : 0xFF334E4F);
            centered(SMALL, phone.shortKey(Action.HOME) + " Home", 116, 391, MUTED);
            return;
        }
        heading("Telephone", "Your contacts, a call away.");
        round(22, 113, 188, 67, 14, 0xFF213B3A);
        asset(ESOUND, 31, 126, 40, 40, 20);
        text(TEXT, PhoneConversation.CONTACT, 82, 126, WHITE);
        text(SMALL, PhoneConversation.NUMBER, 82, 146, MUTED);
        centered(SMALL, "Ready to call", 116, 218, MUTED);
        round(31, 254, 170, 42, 15, 0xFF2D8E61);
        centered(TEXT, phone.shortKey(Action.SELECT) + " Call ESound", 116, 268, WHITE);
        receiver(116, 344, .75F, 0xFF82D4A7);
        centered(SMALL, "ESound is your only contact.", 116, 391, MUTED);
    }
    private void messages(PhoneModule phone) {
        heading(PhoneConversation.CONTACT, PhoneConversation.NUMBER);
        asset(ESOUND, 178, 59, 28, 28, 14);
        List<PhoneConversation.Message> messages = phone.conversation().messages();
        if (messages.isEmpty()) {
            asset(ESOUND, 89, 155, 54, 54, 27);
            centered(TEXT, "Say hello to ESound", 116, 232, WHITE);
            centered(SMALL, "Send a message to get started.", 116, 255, MUTED);
        } else {
            try (PhoneClip ignored = new PhoneClip(21, 109, 190, 247)) {
                int bottom = 354;
                for (int index = messages.size() - 1 - phone.messageScroll(); index >= 0 && bottom > 109; index--) {
                    PhoneConversation.Message message = messages.get(index);
                    List<String> lines = wrap(message.text, 154);
                    int height = 24 + lines.size() * 12 + (message.attachment ? 107 : 0);
                    int y = bottom - height;
                    round(36, y, 172, height, 12, 0xFF285AA0);
                    for (int line = 0; line < lines.size(); line++) text(TEXT, lines.get(line), 45, y + 8 + line * 12, WHITE);
                    if (message.attachment) asset(DISCORD_IMAGES, 43, y + 11 + lines.size() * 12, 158, 101, 7);
                    String time = new SimpleDateFormat("HH:mm").format(new Date(message.sentAt));
                    text(SMALL, time, 198 - SMALL.width(time), bottom - 14, 0xFFD1E3FF);
                    bottom = y - 9;
                }
            }
        }
        round(24, 368, 184, 31, 13, 0xFF253C58);
        centered(TEXT, phone.shortKey(Action.SELECT) + " Send a message", 116, 377, WHITE);
        centered(SMALL, phone.shortKey(Action.UP) + " / " + phone.shortKey(Action.DOWN) + " Scroll  /  Preset messages", 116, 402, MUTED);
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
        line(color, 6, -10, -12, -12, -6, -7, 3, 3, 10, 10, 12, 14, 7);
        round(-15, -16, 10, 9, 3, color); round(8, 3, 10, 9, 3, color);
        GlStateManager.popMatrix();
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
    private void hand(boolean front) {
        if (arm == null) {
            ModelBase model = new ModelBase() { }; model.textureWidth = model.textureHeight = 64;
            arm = new ModelRenderer(model, 32, 48); arm.addBox(-2, 0, -2, 4, 12, 4);
            sleeve = new ModelRenderer(model, 48, 48); sleeve.addBox(-2, 0, -2, 4, 12, 4, .25F);
            slimArm = new ModelRenderer(model, 32, 48); slimArm.addBox(-1.5F, 0, -2, 3, 12, 4);
            slimSleeve = new ModelRenderer(model, 48, 48); slimSleeve.addBox(-1.5F, 0, -2, 3, 12, 4, .25F);
            thumb = new ModelRenderer(model, 34, 58); thumb.addBox(-1, -1, -1, 2, 4, 2);
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
                GlStateManager.translate(18, 344, 75); GlStateManager.rotate(-18, 0, 0, 1);
                GlStateManager.rotate(24, 0, 1, 0); GlStateManager.scale(165, 165, 165); thumb.render(.0625F);
            } else {
                GlStateManager.translate(53, 505, 35); GlStateManager.rotate(-19, 0, 0, 1);
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
        int[] colors = {0xFF626D81, 0xFF8395AD, 0xFFFFBC4B, 0xFFF5F1EF, 0xFF419DDF, 0xFF8E94AE, 0xFF35AF72, 0xFF36A98B};
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
                default: break;
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
        canvas.close(); TEXT.close(); SMALL.close(); TITLE.close(); CLOCK.close();
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
