package dev.vibe.ui;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ImageBufferDownload;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Resolves the actual profile on a worker, then uploads its square face on the render thread. */
final class SkinHeads {
    private static final SkinProfileResolver PROFILES = new SkinProfileResolver();
    private static final ThreadPoolExecutor DOWNLOADS = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(64), task -> {
                Thread thread = new Thread(task, "Vibe skin preview");
                thread.setDaemon(true);
                return thread;
            });
    private static final Map<UUID, Head> HEADS = new LinkedHashMap<UUID, Head>(64, 0.75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<UUID, Head> eldest) {
            if (size() <= 64) return false;
            if (eldest.getValue().loaded) Minecraft.getMinecraft().getTextureManager().deleteTexture(eldest.getValue().texture);
            return true;
        }
    };

    private SkinHeads() { }

    static void draw(UUID id, String name, int x, int y, int size) {
        Minecraft mc = Minecraft.getMinecraft();
        if (id == null) id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Head head = HEADS.get(id);
        if (head == null) {
            head = new Head(DefaultPlayerSkin.getDefaultSkin(id));
            HEADS.put(id, head);
        }
        if (!head.loading && System.currentTimeMillis() >= head.retryAt) request(mc, id, name, head);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color(1, 1, 1, 1);
        mc.getTextureManager().bindTexture(head.texture);
        int minFilter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
        int magFilter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
        try {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            Gui.drawScaledCustomSizeModalRect(x, y, 8, 8, 8, 8, size, size, 64, 64);
            Gui.drawScaledCustomSizeModalRect(x, y, 40, 8, 8, 8, size, size, 64, 64);
        } finally {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, minFilter);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, magFilter);
        }
    }

    private static void request(Minecraft mc, UUID id, String name, Head head) {
        head.loading = true;
        try {
            DOWNLOADS.execute(() -> {
                BufferedImage image = null;
                long retryDelay = TimeUnit.MINUTES.toMillis(5);
                try {
                    String url = PROFILES.resolve(id, name);
                    if (url != null) {
                        byte[] data = SkinProfileResolver.download(url);
                        if (data == null) throw new IOException("Skin texture unavailable");
                        BufferedImage downloaded = ImageIO.read(new ByteArrayInputStream(data));
                        if (downloaded == null || downloaded.getWidth() != 64
                                || (downloaded.getHeight() != 32 && downloaded.getHeight() != 64))
                            throw new IOException("Invalid skin dimensions");
                        image = new ImageBufferDownload().parseUserSkin(downloaded);
                    }
                } catch (IOException | RuntimeException ignored) { retryDelay = TimeUnit.SECONDS.toMillis(30); }
                final BufferedImage ready = image;
                final long delay = retryDelay;
                mc.addScheduledTask(() -> {
                    if (HEADS.get(id) != head) return;
                    try {
                        if (ready != null) {
                            ResourceLocation texture = mc.getTextureManager().getDynamicTextureLocation("vibe_profile_head", new DynamicTexture(ready));
                            if (head.loaded) mc.getTextureManager().deleteTexture(head.texture);
                            head.texture = texture;
                            head.loaded = true;
                        }
                    } finally {
                        head.retryAt = System.currentTimeMillis() + delay;
                        head.loading = false;
                    }
                });
            });
        } catch (RejectedExecutionException busy) {
            head.retryAt = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);
            head.loading = false;
        }
    }

    private static final class Head {
        ResourceLocation texture;
        boolean loaded, loading;
        long retryAt;
        Head(ResourceLocation texture) { this.texture = texture; }
    }
}
