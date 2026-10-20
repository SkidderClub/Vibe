package dev.vibe.launcher.skin;

import dev.vibe.launcher.core.AppLog;
import dev.vibe.launcher.core.FileUtil;
import dev.vibe.launcher.core.Http;
import dev.vibe.launcher.core.Json;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/**
 * Resolves Minecraft skins and capes through Mojang's public profile API, with
 * a disk cache. Offline accounts are looked up by name, like Vibe's own skin
 * heads. Without a profile the vanilla Steve/Alex skin is used.
 */
public final class SkinService {
    public static final class Skin {
        public final BufferedImage texture, cape;
        public final boolean slim, fallback;
        Skin(BufferedImage texture, boolean slim, BufferedImage cape, boolean fallback) {
            this.texture = texture; this.slim = slim; this.cape = cape; this.fallback = fallback;
        }
    }

    public interface Callback { void loaded(Skin skin); }

    private static final long CACHE_MILLIS = 6L * 60L * 60L * 1000L;
    private final Path cache;
    private final AppLog log;
    private final Map<String, Skin> memory = new ConcurrentHashMap<String, Skin>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Vibe skins");
        thread.setDaemon(true);
        return thread;
    });
    private volatile BufferedImage steve, alex;

    public SkinService(Path cache, AppLog log) {
        this.cache = cache;
        this.log = log;
    }

    /** Immediately available skin for the account: cached, or the default until loading finishes. */
    public Skin current(String uuid, String name) {
        Skin known = memory.get(key(uuid, name));
        return known != null ? known : defaultSkin(uuid);
    }

    public void load(final String uuid, final String name, final boolean microsoft, final Callback callback) {
        final String key = key(uuid, name);
        Skin known = memory.get(key);
        if (known != null) {
            callback.loaded(known);
            return;
        }
        executor.execute(new Runnable() {
            @Override public void run() {
                final Skin skin = resolve(key, uuid, name, microsoft);
                memory.put(key, skin);
                SwingUtilities.invokeLater(new Runnable() { @Override public void run() { callback.loaded(skin); } });
            }
        });
    }

    private Skin resolve(String key, String uuid, String name, boolean microsoft) {
        Path png = cache.resolve(key + ".png"), capePng = cache.resolve(key + ".cape.png"), meta = cache.resolve(key + ".properties");
        Properties properties = FileUtil.readProperties(meta);
        long fetched = 0;
        try { fetched = Long.parseLong(properties.getProperty("fetched", "0")); } catch (NumberFormatException ignored) { /* stale */ }
        boolean fresh = System.currentTimeMillis() - fetched < CACHE_MILLIS;
        if (!fresh) {
            try {
                fetch(uuid, name, microsoft, png, capePng, meta);
                properties = FileUtil.readProperties(meta);
            } catch (Exception error) {
                log.info("Skin lookup for " + (name.isEmpty() ? uuid : name) + " failed: " + error.getMessage());
            }
        }
        if ("none".equals(properties.getProperty("skin")) || !Files.isRegularFile(png)) return defaultSkin(uuid);
        try {
            BufferedImage texture = normalize(ImageIO.read(png.toFile()));
            BufferedImage cape = Files.isRegularFile(capePng) ? toArgb(ImageIO.read(capePng.toFile())) : null;
            if (texture == null) return defaultSkin(uuid);
            return new Skin(texture, "true".equals(properties.getProperty("slim")), cape, false);
        } catch (IOException error) {
            return defaultSkin(uuid);
        }
    }

    private void fetch(String uuid, String name, boolean microsoft, Path png, Path capePng, Path meta) throws IOException {
        Files.createDirectories(cache);
        String id = microsoft ? uuid.replace("-", "") : null;
        if (id == null && name.matches("[A-Za-z0-9_]{1,16}")) {
            // Offline profiles show the skin of the Mojang account with the same name, as in Vibe.
            try {
                String body = Http.getText("https://api.mojang.com/users/profiles/minecraft/" + name, 64 * 1024).trim();
                if (!body.isEmpty()) id = Json.string(Json.object(Json.parse(body)), "id");
            } catch (Http.StatusException missing) {
                if (missing.status != 204 && missing.status != 404) throw missing;
            }
        }
        Properties result = new Properties();
        result.setProperty("fetched", Long.toString(System.currentTimeMillis()));
        result.setProperty("skin", "none");
        if (id != null && id.matches("[0-9a-fA-F]{32}")) {
            Map<String, Object> profile = Json.object(Json.parse(Http.getText("https://sessionserver.mojang.com/session/minecraft/profile/" + id, 256 * 1024)));
            for (Object item : Json.array(profile, "properties")) {
                Map<String, Object> property = Json.object(item);
                if (!"textures".equals(Json.string(property, "name"))) continue;
                String decoded = new String(Base64.getDecoder().decode(Json.string(property, "value")), StandardCharsets.UTF_8);
                Map<String, Object> textures = Json.object(Json.object(Json.parse(decoded)), "textures");
                Map<String, Object> skin = Json.object(textures, "SKIN");
                String skinUrl = secure(Json.string(skin, "url"));
                if (!skinUrl.isEmpty()) {
                    Http.download(skinUrl, png, 1024 * 1024, null);
                    result.setProperty("skin", "yes");
                    result.setProperty("slim", Boolean.toString("slim".equals(Json.string(Json.object(skin, "metadata"), "model"))));
                }
                String capeUrl = secure(Json.string(Json.object(textures, "CAPE"), "url"));
                if (!capeUrl.isEmpty()) Http.download(capeUrl, capePng, 1024 * 1024, null);
                else Files.deleteIfExists(capePng);
            }
        }
        FileUtil.writeProperties(meta, result, "Skin cache");
    }

    private static String secure(String url) {
        if (url == null || url.isEmpty()) return "";
        return url.startsWith("http://textures.minecraft.net/") ? "https://" + url.substring(7) : url.startsWith("https://") ? url : "";
    }

    private static String key(String uuid, String name) {
        String value = uuid == null || uuid.isEmpty() ? "name-" + name : uuid;
        return value.replaceAll("[^A-Za-z0-9_-]", "_").toLowerCase(java.util.Locale.ROOT);
    }

    // ---- default skins -----------------------------------------------------

    /** Steve or Alex, chosen from the UUID exactly as Minecraft 1.8.9 does. */
    public Skin defaultSkin(String uuid) {
        boolean slim = false;
        try { slim = uuid != null && !uuid.isEmpty() && (UUID.fromString(uuid).hashCode() & 1) == 1; } catch (IllegalArgumentException ignored) { /* Steve */ }
        loadVanillaSkins();
        BufferedImage texture = slim ? alex : steve;
        if (texture == null) return new Skin(DefaultSkin.paint(), false, null, true);
        return new Skin(texture, slim, null, true);
    }

    private synchronized void loadVanillaSkins() {
        if (steve != null) return;
        Path jar = vanillaClientJar();
        if (jar == null) return;
        try {
            ZipFile zip = new ZipFile(jar.toFile());
            try {
                steve = readEntry(zip, "assets/minecraft/textures/entity/steve.png");
                alex = readEntry(zip, "assets/minecraft/textures/entity/alex.png");
                if (alex == null) alex = steve;
            } finally {
                zip.close();
            }
        } catch (IOException ignored) {
            steve = null;
        }
    }

    private static BufferedImage readEntry(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) return null;
        InputStream input = zip.getInputStream(entry);
        try {
            byte[] data = new byte[(int) Math.min(entry.getSize() > 0 ? entry.getSize() : 65536, 1024 * 1024)];
            int total = 0, count;
            while (total < data.length && (count = input.read(data, total, data.length - total)) > 0) total += count;
            return normalize(ImageIO.read(new ByteArrayInputStream(data, 0, total)));
        } finally {
            input.close();
        }
    }

    /** The Minecraft 1.8.9 client JAR that Unimined caches during Vibe's first build. */
    private static Path vanillaClientJar() {
        String gradleHome = System.getenv("GRADLE_USER_HOME");
        Path base = gradleHome == null || gradleHome.isEmpty() ? Paths.get(System.getProperty("user.home"), ".gradle") : Paths.get(gradleHome);
        Path jar = base.resolve("caches/unimined/net/minecraft/minecraft/1.8.9/minecraft-1.8.9-client.jar");
        return Files.isRegularFile(jar) ? jar : null;
    }

    // ---- texture handling --------------------------------------------------

    /** Converts legacy 64x32 skins to the 64x64 layout the way Minecraft 1.8 does. */
    static BufferedImage normalize(BufferedImage source) {
        if (source == null || source.getWidth() != 64 || (source.getHeight() != 64 && source.getHeight() != 32)) return null;
        boolean legacy = source.getHeight() == 32;
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.drawImage(source, 0, 0, null);
        if (legacy) {
            g.drawImage(image, 24, 48, 20, 52, 4, 16, 8, 20, null);
            g.drawImage(image, 28, 48, 24, 52, 8, 16, 12, 20, null);
            g.drawImage(image, 20, 52, 16, 64, 8, 20, 12, 32, null);
            g.drawImage(image, 24, 52, 20, 64, 4, 20, 8, 32, null);
            g.drawImage(image, 28, 52, 24, 64, 0, 20, 4, 32, null);
            g.drawImage(image, 32, 52, 28, 64, 12, 20, 16, 32, null);
            g.drawImage(image, 40, 48, 36, 52, 44, 16, 48, 20, null);
            g.drawImage(image, 44, 48, 40, 52, 48, 16, 52, 20, null);
            g.drawImage(image, 36, 52, 32, 64, 48, 20, 52, 32, null);
            g.drawImage(image, 40, 52, 36, 64, 44, 20, 48, 32, null);
            g.drawImage(image, 44, 52, 40, 64, 40, 20, 44, 32, null);
            g.drawImage(image, 48, 52, 44, 64, 52, 20, 56, 32, null);
        }
        g.dispose();
        // Base layers are always opaque; a fully opaque legacy hat layer is treated as empty.
        opaque(image, 0, 0, 32, 16);
        opaque(image, 0, 16, 64, 32);
        opaque(image, 16, 48, 48, 64);
        if (legacy) clearIfOpaque(image, 32, 0, 64, 16);
        return image;
    }

    private static void opaque(BufferedImage image, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) image.setRGB(x, y, image.getRGB(x, y) | 0xFF000000);
    }

    private static void clearIfOpaque(BufferedImage image, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) if ((image.getRGB(x, y) >>> 24) < 128) return;
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) image.setRGB(x, y, 0);
    }

    private static BufferedImage toArgb(BufferedImage source) {
        if (source == null) return null;
        BufferedImage image = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return image;
    }

    /** The 8x8 face with its hat layer, scaled without smoothing. */
    public static BufferedImage face(Skin skin, int size) {
        BufferedImage face = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = face.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(skin.texture, 0, 0, size, size, 8, 8, 16, 16, null);
        g.drawImage(skin.texture, 0, 0, size, size, 40, 8, 48, 16, null);
        g.dispose();
        return face;
    }
}
