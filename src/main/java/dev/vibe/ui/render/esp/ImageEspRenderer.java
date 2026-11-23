package dev.vibe.ui.render.esp;

import dev.vibe.Vibe;
import dev.vibe.core.startup.GhostArt;
import dev.vibe.module.impl.visual.EspModule.ImageSettings;
import dev.vibe.ui.effect.EffectState;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

public final class ImageEspRenderer {
    public static final ImageEspRenderer INSTANCE = new ImageEspRenderer();
    private static final String GHOST = "";
    private static final long MAX_FILE_BYTES = 8 * 1024 * 1024L;
    private final FloatBuffer view = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer facing = BufferUtils.createFloatBuffer(16);
    private final Map<String, Texture> textures = new LinkedHashMap<>(8, .75F, true);
    private final Map<String, Long> failures = new LinkedHashMap<>();

    private ImageEspRenderer() { }

    public static File directory() {
        Vibe vibe = Vibe.getInstance();
        return vibe == null || vibe.getConfig() == null ? null : new File(vibe.getConfig().getDirectory().getParentFile(), "esp-images");
    }

    public void drawWorld(List<Sprite> sprites) {
        if (sprites.isEmpty()) return;
        view.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, view);
        facing.clear();
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) facing.put(col < 3 && row < 3 ? view.get(row * 4 + col) : col == row ? 1 : 0);
        }
        facing.flip();
        try (EffectState ignored = new EffectState()) {
            setup();
            for (Sprite sprite : sprites) {
                if (sprite.settings.throughWalls.isEnabled()) GL11.glDisable(GL11.GL_DEPTH_TEST);
                else GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glPushMatrix();
                try {
                    GL11.glTranslated(sprite.x, sprite.y + sprite.settings.offset.getDouble(), sprite.z);
                    GL11.glMultMatrix(facing);
                    GL11.glScalef(1, -1, 1);
                    draw(sprite.settings, sprite.settings.size.getFloat(), sprite.opacity);
                } finally { GL11.glPopMatrix(); }
            }
        }
    }

    public void preview(ImageSettings settings, float x, float y, float scale) {
        boolean clipped = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        try (EffectState ignored = new EffectState()) {
            setup();
            if (clipped) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glPushMatrix();
            try {
                GL11.glTranslatef(x, y - settings.offset.getFloat() * scale, 0);
                draw(settings, settings.size.getFloat() * scale, 1);
            } finally { GL11.glPopMatrix(); }
        }
    }

    private static void setup() {
        GL20.glUseProgram(0);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
    }

    private void draw(ImageSettings settings, float size, float opacity) {
        Texture texture = texture(settings.file.getValue().trim());
        if (texture == null) return;
        float w = size * Math.min(1, texture.aspect), h = size / Math.max(1, texture.aspect);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.id);
        GL11.glColor4f(1, 1, 1, opacity * settings.opacity.getFloat() / 100F);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0); GL11.glVertex2f(-w / 2, -h);
        GL11.glTexCoord2f(1, 0); GL11.glVertex2f(w / 2, -h);
        GL11.glTexCoord2f(1, 1); GL11.glVertex2f(w / 2, 0);
        GL11.glTexCoord2f(0, 1); GL11.glVertex2f(-w / 2, 0);
        GL11.glEnd();
    }

    private Texture texture(String name) {
        Texture cached = textures.get(name);
        if (cached != null) return cached;
        long now = System.nanoTime();
        Long lastFailure = failures.get(name);
        if (lastFailure != null && now - lastFailure < 2_000_000_000L) return name.isEmpty() ? null : texture(GHOST);
        try {
            BufferedImage image = name.isEmpty() ? GhostArt.image("ghost-static.png") : readLocal(name);
            if (image == null) throw new IOException("Image unavailable");
            Texture texture = new Texture(GhostArt.upload(image), image.getWidth() / (float) image.getHeight());
            if (textures.size() >= 6) {
                String oldest = textures.keySet().iterator().next();
                GL11.glDeleteTextures(textures.remove(oldest).id);
            }
            textures.put(name, texture);
            failures.remove(name);
            return texture;
        } catch (IOException | RuntimeException unavailable) {
            if (failures.size() >= 8) failures.remove(failures.keySet().iterator().next());
            failures.put(name, now);
            return name.isEmpty() ? null : texture(GHOST);
        }
    }

    private static BufferedImage readLocal(String name) throws IOException {
        File root = directory();
        String lower = name.toLowerCase(Locale.ROOT);
        if (root == null || name.contains("/") || name.contains("\\") || name.contains(":")
                || !(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg"))) return null;
        File image = new File(root, name).getCanonicalFile();
        if (!root.getCanonicalFile().equals(image.getParentFile()) || !image.isFile() || image.length() > MAX_FILE_BYTES) return null;
        try (ImageInputStream stream = new javax.imageio.stream.FileImageInputStream(image)) {
            java.util.Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                if (reader.getWidth(0) > 1024 || reader.getHeight(0) > 1024) return null;
                return reader.read(0);
            } finally { reader.dispose(); }
        }
    }

    public static final class Sprite {
        final double x, y, z;
        final float opacity;
        final ImageSettings settings;
        public Sprite(double x, double y, double z, float opacity, ImageSettings settings) {
            this.x = x; this.y = y; this.z = z; this.opacity = opacity; this.settings = settings;
        }
    }

    private static final class Texture {
        final int id;
        final float aspect;
        Texture(int id, float aspect) { this.id = id; this.aspect = aspect; }
    }
}
