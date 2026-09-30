package dev.vibe.game.gta8;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.settings.GameSettings;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.Pbuffer;
import org.lwjgl.opengl.PixelFormat;

/** Opt-in native OpenGL fixture for GTA8; run with Gradle's verifyGta8Rendering task. Writes PNGs and timings. */
public class Gta8RenderCheck {
    static final int W = 1280, H = 720;
    static Path root;
    static Gta8Renderer renderer;
    static Gta8Game game;
    static Minecraft minecraft;
    static dev.vibe.module.impl.meme.Gta8Module gtaModule;
    static dev.vibe.ui.game.Gta8Gui gui;

    static void set(Class<?> c, Object o, String n, Object v) throws Exception { Field f = c.getDeclaredField(n); f.setAccessible(true); f.set(o, v); }

    public static void main(String[] args) throws Exception {
        root = Paths.get(args.length == 0 ? "build/gta8-render-check" : args[0]);
        Files.createDirectories(root);
        Pbuffer buffer = new Pbuffer(W, H, new PixelFormat(8, 24, 8), null, null);
        buffer.makeCurrent();
        try {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field uf = unsafeType.getDeclaredField("theUnsafe"); uf.setAccessible(true);
            Object unsafe = uf.get(null);
            Minecraft mc = (Minecraft) unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, Minecraft.class);
            set(Minecraft.class, null, "theMinecraft", mc);
            mc.displayWidth = W; mc.displayHeight = H; mc.gameSettings = new GameSettings();
            OpenGlHelper.initializeTextures();
            set(Minecraft.class, mc, "mcDataDir", root.toFile());
            net.minecraft.client.resources.data.IMetadataSerializer meta = new net.minecraft.client.resources.data.IMetadataSerializer();
            set(Minecraft.class, mc, "mcLanguageManager", new net.minecraft.client.resources.LanguageManager(meta, "en_US"));
            meta.registerMetadataSectionType(new net.minecraft.client.resources.data.TextureMetadataSectionSerializer(), net.minecraft.client.resources.data.TextureMetadataSection.class);
            meta.registerMetadataSectionType(new net.minecraft.client.resources.data.FontMetadataSectionSerializer(), net.minecraft.client.resources.data.FontMetadataSection.class);
            net.minecraft.client.resources.SimpleReloadableResourceManager resources = new net.minecraft.client.resources.SimpleReloadableResourceManager(meta);
            resources.reloadResourcePack(new net.minecraft.client.resources.DefaultResourcePack(Collections.<String, File>emptyMap()));
            set(Minecraft.class, mc, "mcResourceManager", resources);
            mc.renderEngine = new net.minecraft.client.renderer.texture.TextureManager(resources);
            mc.fontRendererObj = new net.minecraft.client.gui.FontRenderer(mc.gameSettings, new net.minecraft.util.ResourceLocation("textures/font/ascii.png"), mc.renderEngine, false);
            mc.fontRendererObj.onResourceManagerReload(resources);
            dev.vibe.Vibe vibe = new dev.vibe.Vibe();
            set(dev.vibe.Vibe.class, null, "instance", vibe);
            dev.vibe.module.ModuleManager modules = (dev.vibe.module.ModuleManager) unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, dev.vibe.module.ModuleManager.class);
            gtaModule = new dev.vibe.module.impl.meme.Gta8Module();
            set(dev.vibe.module.ModuleManager.class, modules, "modules", new java.util.ArrayList<dev.vibe.module.Module>(java.util.Arrays.asList(gtaModule)));
            set(dev.vibe.Vibe.class, vibe, "moduleManager", modules);
            minecraft = mc;
            System.out.println("Renderer: " + GL11.glGetString(GL11.GL_RENDERER) + " / " + GL11.glGetString(GL11.GL_VERSION));
            long t0 = System.nanoTime();
            Gta8World world = new Gta8World();
            long t1 = System.nanoTime();
            renderer = new Gta8Renderer();
            renderer.prepareNow(world);
            long t2 = System.nanoTime();
            game = new Gta8Game(world, new Gta8Progress(Files.createTempDirectory(root, "profile-").resolve("progress.json")));
            String only = System.getProperty("gta8.only", "");
            System.out.printf("World %.0f ms, meshes %.0f ms%n", (t1 - t0) / 1e6, (t2 - t1) / 1e6);
            shots(only);
            if (renderer.failure != null) throw new AssertionError("Renderer failed: " + renderer.failure);
            renderer.close();
            System.out.println("GTA8 offscreen render check passed");
        } finally { buffer.destroy(); }
    }

    static void shots(String only) throws Exception {
        Gta8World w = game.world;
        double sx = w.spawnX, sz = w.spawnZ;
        shot(only, "01-street-morning", 9.5, Gta8Weather.Type.CLEAR, sx, 1.7, sz, 180, 2);
        shot(only, "02-downtown-noon", 12.5, Gta8Weather.Type.CLEAR, 10, 1.7, -60, 20, -8);
        shot(only, "03-avenue-view", 15, Gta8Weather.Type.CLEAR, -3, 1.8, 200, 0, 0);
        shot(only, "04-aerial-city", 16.5, Gta8Weather.Type.CLEAR, -300, 160, 420, 35, 18);
        shot(only, "05-sunset-beach", 18.6, Gta8Weather.Type.CLEAR, 60, 3, 505, 250, 4);
        shot(only, "06-night-downtown", 22.5, Gta8Weather.Type.CLEAR, 5, 1.8, 90, 0, -2);
        shot(only, "07-rain-street", 14, Gta8Weather.Type.RAIN, 100, 1.7, 30, 90, 3);
        shot(only, "08-harbour", 11, Gta8Weather.Type.CLOUDY, 560, 6, 200, 20, 2);
        shot(only, "09-suburb", 10, Gta8Weather.Type.CLEAR, -385, 1.7, -130, 90, 3);
        shot(only, "10-hills-sign", 17.5, Gta8Weather.Type.SUNNY_HAZE, -100, 3, -470, 180, -12);
        shot(only, "11-night-rain", 23, Gta8Weather.Type.THUNDER, -3, 1.8, 120, 0, 1);
        shot(only, "12-fog-morning", 7, Gta8Weather.Type.FOG, 0, 1.8, -20, 0, 0);
        actors(only);
        interfaceShots(only);
        gallery(only);
        // Performance: a typical street view, warmed up.
        game.weather.set(Gta8Weather.Type.CLEAR); game.weather.hours = 13;
        game.camera.set(10, 1.7, -60, 20, -2);
        for (int i = 0; i < 20; i++) { renderer.render(game, W, H); GL11.glFinish(); }
        double[] frames = new double[60];
        for (int i = 0; i < frames.length; i++) { long s = System.nanoTime(); renderer.render(game, W, H); GL11.glFinish(); frames[i] = (System.nanoTime() - s) / 1e6; }
        java.util.Arrays.sort(frames);
        String report = String.format(java.util.Locale.ROOT, "frame_median_ms=%.3f%nframe_p95_ms=%.3f%nchunks=%d%n", frames[30], frames[57], renderer.drawnChunks);
        Files.write(root.resolve("performance.txt"), report.getBytes("UTF-8"));
        System.out.print(report);
    }

    /** Populated scenes: the simulation runs first so traffic and pedestrians spawn around the camera. */
    static void actors(String only) throws Exception {
        if (!only.isEmpty() && !only.startsWith("a")) return;
        Gta8Game.Input idle = new Gta8Game.Input();
        game.weather.set(Gta8Weather.Type.CLEAR); game.weather.hours = 11;
        for (int i = 0; i < 60 * 25; i++) game.advance(1 / 60.0, idle);
        Gta8Ped p = game.player;
        live(only, "a1-third-person", p.x, p.z, false, 0);
        game.firstPerson = true;
        live(only, "a2-first-person", p.x, p.z, false, 0);
        game.firstPerson = false;
        idle.aim = true;
        for (int i = 0; i < 20; i++) game.advance(1 / 60.0, idle);
        live(only, "a3-aiming", p.x, p.z, false, 0);
        idle.aim = false;
        for (int i = 0; i < 20; i++) game.advance(1 / 60.0, idle);
        // A street full of traffic seen from the sidewalk.
        Gta8Vehicle near = null;
        double best = 1e9;
        for (Gta8Vehicle v : game.vehicles) { double d = Math.hypot(v.x - p.x, v.z - p.z); if (v.ai != null && d < best) { best = d; near = v; } }
        if (near != null) {
            game.camera.set(near.worldX(4.5, 6), near.y + 1.6, near.worldZ(4.5, 6), near.yaw + 215, 6);
            capture(only, "a4-traffic-closeup", false);
        }
        game.weather.set(Gta8Weather.Type.RAIN); game.weather.hours = 22.5;
        game.police.crime(Gta8Police.Crime.KILL_COP, p.x, p.z);
        for (int i = 0; i < 60 * 14; i++) game.advance(1 / 60.0, idle);
        live(only, "a5-night-police", p.x, p.z, false, 0);
        Gta8Vehicle boom = null;
        for (Gta8Vehicle v : game.vehicles) if (Math.hypot(v.x - p.x, v.z - p.z) < 60 && v != p.vehicle) { boom = v; break; }
        if (boom != null) {
            game.explode(boom);
            for (int i = 0; i < 12; i++) game.advance(1 / 60.0, idle);
            game.camera.set(boom.x + 14, boom.y + 4, boom.z + 14, 225, 12);
            capture(only, "a6-explosion", false);
        }
    }
    /** Close-ups of every vehicle model and a line-up of pedestrians on an empty plaza. */
    static void gallery(String only) throws Exception {
        if (!only.isEmpty() && !only.startsWith("g")) return;
        game.vehicles.clear();
        game.peds.clear();
        game.peds.add(game.player);
        game.police.clear();
        game.missions.cancel();
        game.weather.set(Gta8Weather.Type.CLEAR); game.weather.hours = 15.5;
        game.density = -1;
        // Vibe Plaza (block 4,4) is open paving north-west of the centre.
        double baseX = -60, baseZ = -60;
        Gta8Vehicle.Model[] models = Gta8Vehicle.Model.values();
        int[] paints = {0xB02A22, 0x2C4A7A, 0x1C1D20, 0xE8E8E6, 0xC8B89A, 0xE8B81C, 0x16181C, 0x4A5A36, 0x8E9296};
        for (int i = 0; i < models.length; i++) {
            Gta8Vehicle v = new Gta8Vehicle(models[i], baseX - 6 + (i % 3) * 13, baseZ - 6 + (i / 3) * 13, 60, paints[i]);
            v.y = game.world.groundHeight(v.x, v.z); v.dynamic = true; v.parked = true; v.remember();
            game.vehicles.add(v);
        }
        game.player.x = baseX + 40; game.player.z = baseZ + 40; game.player.remember();
        for (int i = 0; i < 8; i++) {
            Gta8Ped p = game.createCivilian(baseX + 30 + i * 1.3, baseZ + 20);
            p.state = Gta8Ped.State.IDLE; p.waitTimer = 999; p.yaw = 180 + (i - 4) * 8;
            p.remember();
            game.peds.add(p);
        }
        Gta8Ped cop = game.createCop(baseX + 30 + 8 * 1.3, baseZ + 20); cop.yaw = 180; cop.remember(); game.peds.add(cop);
        Gta8Ped walker = game.createCivilian(baseX + 30 + 9 * 1.3, baseZ + 20); walker.moveSpeed = 1.4; walker.phase = 1; walker.yaw = 90; walker.remember(); game.peds.add(walker);
        Gta8Ped runner = game.createCivilian(baseX + 30 + 10.6, baseZ + 20); runner.moveSpeed = 5.5; runner.phase = 2; runner.yaw = 90; runner.remember(); game.peds.add(runner);
        Gta8Ped aimer = game.createCop(baseX + 30 + 12, baseZ + 20); aimer.aim = 1; aimer.weapon = Gta8Weapon.RIFLE; aimer.yaw = 120; aimer.remember(); game.peds.add(aimer);
        game.alpha = 1;
        for (int i = 0; i < models.length; i++) {
            Gta8Vehicle v = game.vehicles.get(i);
            double camX = v.worldX(3.3, 4.5), camZ = v.worldZ(3.3, 4.5);
            game.camera.set(camX, v.y + 2.3, camZ, Math.toDegrees(Math.atan2(v.x - camX, -(v.z - camZ))), 17);
            game.camera.fov = 55;
            capture(only, "g-car-" + v.model.name().toLowerCase(java.util.Locale.ROOT), false);
        }
        game.camera.set(baseX + 30 + 6.5, 1.75, baseZ + 20 + 5.2, 0, 4);
        game.camera.fov = 60;
        capture(only, "g-people", false);
        game.camera.set(baseX + 30 + 2, 1.7, baseZ + 20 + 1.6, 0, 8);
        game.camera.fov = 45;
        capture(only, "g-faces", false);
    }

    /** HUD and menus, drawn through the real Gta8Gui methods like in the game. */
    static void interfaceShots(String only) throws Exception {
        if (!only.isEmpty() && !only.startsWith("h")) return;
        gui = new dev.vibe.ui.game.Gta8Gui(gtaModule);
        gui.mc = minecraft;
        set(net.minecraft.client.gui.GuiScreen.class, gui, "fontRendererObj", minecraft.fontRendererObj);
        gui.width = W / 2; gui.height = H / 2;
        Class<?> g = dev.vibe.ui.game.Gta8Gui.class;
        set(g, gui, "world", game.world); set(g, gui, "game", game); set(g, gui, "renderer", renderer);
        set(g, gui, "mapImage", Gta8MapImage.render(game.world));
        Class<?> fontType = Class.forName("dev.vibe.ui.NeverLoseFont");
        java.lang.reflect.Constructor<?> ctor = fontType.getDeclaredConstructor(String.class, int.class, int.class);
        ctor.setAccessible(true);
        set(g, gui, "font", ctor.newInstance(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 20));
        set(g, gui, "bold", ctor.newInstance(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 24));
        set(g, gui, "mapTexture", new net.minecraft.client.renderer.texture.DynamicTexture((java.awt.image.BufferedImage) field(g, gui, "mapImage")));
        game.weather.set(Gta8Weather.Type.CLEAR); game.weather.hours = 16;
        game.police.clear();
        game.missions.start(game.missions.offers().get(0));
        game.waypoint = true; game.waypointX = 200; game.waypointZ = 150;
        game.police.crime(Gta8Police.Crime.KILL_COP, game.player.x, game.player.z);
        Gta8Game.Input idle = new Gta8Game.Input();
        for (int i = 0; i < 60 * 3; i++) game.advance(1 / 60.0, idle);
        game.updateCamera(0);
        guiFrame(only, "h1-hud", "hud");
        set(g, gui, "paused", true);
        set(g, gui, "mapX", game.player.x); set(g, gui, "mapZ", game.player.z); set(g, gui, "mapZoom", 1.6);
        guiFrame(only, "h2-pause-map", "pauseMenu");
        set(g, gui, "tab", 1); guiFrame(only, "h3-jobs", "pauseMenu");
        set(g, gui, "tab", 3); guiFrame(only, "h4-settings", "pauseMenu");
        set(g, gui, "paused", false);
        game.shop = game.world.nearestPoi(Gta8World.Poi.GUN_SHOP, 0, 0);
        guiFrame(only, "h5-gun-shop", "shop");
        game.shop = null;
    }
    static Object field(Class<?> c, Object o, String n) throws Exception { Field f = c.getDeclaredField(n); f.setAccessible(true); return f.get(o); }
    static void guiFrame(String only, String name, String method) throws Exception {
        if (!only.isEmpty() && !name.contains(only) && !only.equals("h")) return;
        renderer.render(game, W, H);
        GL11.glViewport(0, 0, W, H);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(0, gui.width, gui.height, 0, 1000, 3000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity(); GL11.glTranslated(0, 0, -2000);
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        net.minecraft.client.renderer.GlStateManager.disableDepth();
        net.minecraft.client.renderer.GlStateManager.enableTexture2D();
        java.lang.reflect.Method m = method.equals("hud") ? dev.vibe.ui.game.Gta8Gui.class.getDeclaredMethod("hud") : dev.vibe.ui.game.Gta8Gui.class.getDeclaredMethod(method, int.class, int.class);
        m.setAccessible(true);
        if (method.equals("hud")) m.invoke(gui); else m.invoke(gui, 0, 0);
        net.minecraft.client.renderer.GlStateManager.enableDepth();
        GL11.glFinish();
        int error = GL11.glGetError();
        if (error != 0) throw new AssertionError("GL error " + error + " in " + name);
        save(name);
    }
    static void live(String only, String name, double x, double z, boolean unused, double pitch) throws Exception {
        game.updateCamera(0);
        capture(only, name, false);
    }
    static void capture(String only, String name, boolean unused) throws Exception {
        if (!only.isEmpty() && !name.contains(only)) return;
        renderer.render(game, W, H);
        GL11.glFinish();
        if (renderer.failure != null) throw new AssertionError(renderer.failure);
        int error = GL11.glGetError();
        if (error != 0) throw new AssertionError("GL error " + error + " in " + name);
        save(name);
    }
    static void save(String name) throws Exception {
        ByteBuffer pixels = BufferUtils.createByteBuffer(W * H * 4);
        GL11.glReadPixels(0, 0, W, H, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int yy = 0; yy < H; yy++) for (int xx = 0; xx < W; xx++) {
            int i = (yy * W + xx) * 4;
            image.setRGB(xx, H - 1 - yy, ((pixels.get(i) & 255) << 16) | ((pixels.get(i + 1) & 255) << 8) | (pixels.get(i + 2) & 255));
        }
        ImageIO.write(image, "png", root.resolve(name + ".png").toFile());
        System.out.printf("Captured %s (%.1f ms)%n", name, renderer.lastFrameMillis);
    }

    static void shot(String only, String name, double hours, Gta8Weather.Type weather, double x, double y, double z, double yaw, double pitch) throws Exception {
        if (!only.isEmpty() && !name.contains(only)) return;
        game.weather.set(weather);
        game.weather.hours = hours;
        game.time = 100 + hours * 10;
        game.camera.set(x, y, z, yaw, pitch);
        game.camera.fov = 70;
        game.cameraIndoors = false;
        renderer.render(game, W, H);
        GL11.glFinish();
        int error = GL11.glGetError();
        if (error != 0) throw new AssertionError("GL error " + error + " in " + name);
        if (renderer.failure != null) throw new AssertionError(renderer.failure);
        ByteBuffer pixels = BufferUtils.createByteBuffer(W * H * 4);
        GL11.glReadPixels(0, 0, W, H, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int yy = 0; yy < H; yy++) for (int xx = 0; xx < W; xx++) {
            int i = (yy * W + xx) * 4;
            image.setRGB(xx, H - 1 - yy, ((pixels.get(i) & 255) << 16) | ((pixels.get(i + 1) & 255) << 8) | (pixels.get(i + 2) & 255));
        }
        ImageIO.write(image, "png", root.resolve(name + ".png").toFile());
        System.out.printf("Captured %s (%.1f ms)%n", name, renderer.lastFrameMillis);
    }
}
