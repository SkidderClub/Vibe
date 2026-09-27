package dev.vibe.game.gta8;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

/**
 * Deferred-free HDR forward renderer: cascaded sun shadows, analytic sky and fog, procedural materials,
 * water, particles, bloom, ACES tone mapping and FXAA. All Minecraft GL state is restored afterwards.
 */
public final class Gta8Renderer {
    public static final int LOW = 0, MEDIUM = 1, HIGH = 2, ULTRA = 3;
    public int quality = HIGH;
    public double renderScale = 1;
    public boolean bloomEnabled = true, fxaaEnabled = true;
    public String failure;
    public double lastFrameMillis;
    public int drawnChunks, drawCalls;

    private Gta8World world;
    private Gta8CityMesher mesher;
    private Thread builder;
    private volatile boolean built;
    private volatile Throwable buildError;
    private boolean uploaded;
    private Gta8Shader worldShader, skinnedShader, shadowShader, shadowSkinned, skyShader, waterShader, particleShader, rainShader, downShader, upShader, compositeShader, fxaaShader;
    private final Gta8Framebuffer scene = new Gta8Framebuffer(), shadow = new Gta8Framebuffer(), ldr = new Gta8Framebuffer();
    private final Gta8Framebuffer[] bloom = new Gta8Framebuffer[6];
    final Gta8Textures textures = new Gta8Textures();
    final Gta8Atmosphere atmosphere = new Gta8Atmosphere();
    final Gta8ActorRenderer actors = new Gta8ActorRenderer(this);
    private Gta8Mesh waterNear, waterFar, rainMesh, glassMesh;
    private int glassRevision = -1;
    private boolean hdr;
    // Matrices (column-major).
    final float[] view = new float[16], projection = new float[16], viewProjection = new float[16], inverseViewProjection = new float[16];
    private final float[] lightView = new float[16], lightProjection = new float[16], temp = new float[16];
    final float[][] shadowViewProjection = {new float[16], new float[16]};
    private final float[][] shadowLookup = {new float[16], new float[16]};
    private final double[][] frustum = new double[6][4];
    private final double[][] cascadeCenter = new double[2][3];
    private final double[] cascadeRadius = new double[2];
    private int shadowSize;
    private final FloatBuffer lightPos = BufferUtils.createFloatBuffer(64), lightColor = BufferUtils.createFloatBuffer(64), lightDir = BufferUtils.createFloatBuffer(64);
    private final FloatBuffer halves = BufferUtils.createFloatBuffer(11);
    /** Ambient occluders under cars and people: centre + half length, then forward xz + half width + height. */
    private final FloatBuffer occA = BufferUtils.createFloatBuffer(64), occB = BufferUtils.createFloatBuffer(64);
    private int occCount;
    int lightCount;
    final List<double[]> dynamicLights = new ArrayList<double[]>();
    Gta8Camera camera;
    double time;
    private long lastNanos;
    private int renderWidth, renderHeight;
    public float wasted, hurt;

    // ------------------------------------------------------------------ lifecycle
    /** Starts building meshes on a worker thread; GL work happens on the first render call. */
    public void prepare(Gta8World world) {
        this.world = world;
        mesher = new Gta8CityMesher(world);
        builder = new Thread(new Runnable() {
            @Override public void run() {
                try { mesher.build(); built = true; } catch (Throwable t) { buildError = t; }
            }
        }, "GTA8 city builder");
        builder.setDaemon(true);
        builder.setPriority(Thread.NORM_PRIORITY - 1);
        builder.start();
    }
    /** Synchronous variant for tests and offscreen checks. */
    public void prepareNow(Gta8World world) {
        this.world = world;
        mesher = new Gta8CityMesher(world);
        mesher.build();
        built = true;
    }
    public double progress() { return mesher == null ? 0 : uploaded ? 1 : Math.min(.99, mesher.progress); }
    public boolean ready() { return uploaded; }
    public static String unsupportedReason() {
        if (!OpenGlHelper.shadersSupported) return "GTA8 needs OpenGL 2.1 shaders";
        if (!OpenGlHelper.framebufferSupported) return "GTA8 needs framebuffer objects";
        if (!GLContext.getCapabilities().OpenGL15) return "GTA8 needs vertex buffer objects";
        return null;
    }

    private void initialise() throws Exception {
        String reason = unsupportedReason();
        if (reason != null) throw new IllegalStateException(reason);
        hdr = GLContext.getCapabilities().OpenGL30 || GLContext.getCapabilities().GL_ARB_texture_float;
        worldShader = new Gta8Shader("world.vsh", "world.fsh", "");
        skinnedShader = new Gta8Shader("world.vsh", "world.fsh", "SKINNED");
        shadowShader = new Gta8Shader("shadow.vsh", "shadow.fsh", "");
        shadowSkinned = new Gta8Shader("shadow.vsh", "shadow.fsh", "SKINNED");
        skyShader = new Gta8Shader("post.vsh", "sky.fsh", "");
        waterShader = new Gta8Shader("water.vsh", "water.fsh", "");
        particleShader = new Gta8Shader("particle.vsh", "particle.fsh", "");
        rainShader = new Gta8Shader("rain.vsh", "rain.fsh", "");
        downShader = new Gta8Shader("post.vsh", "bloom_down.fsh", "");
        upShader = new Gta8Shader("post.vsh", "bloom_up.fsh", "");
        compositeShader = new Gta8Shader("post.vsh", "composite.fsh", "");
        fxaaShader = new Gta8Shader("post.vsh", "fxaa.fsh", "");
        textures.create(world, mesher.atlas);
        mesher.upload();
        waterNear = waterGrid(240, 4);
        waterFar = waterRing();
        rainMesh = rainStreaks(6000);
        actors.create();
        for (int i = 0; i < bloom.length; i++) bloom[i] = new Gta8Framebuffer();
        uploaded = true;
    }

    public void close() {
        if (mesher != null) mesher.close();
        for (Gta8Shader s : new Gta8Shader[]{worldShader, skinnedShader, shadowShader, shadowSkinned, skyShader, waterShader, particleShader, rainShader, downShader, upShader, compositeShader, fxaaShader})
            if (s != null) s.close();
        for (Gta8Mesh m : new Gta8Mesh[]{waterNear, waterFar, rainMesh, glassMesh}) if (m != null) m.close();
        scene.close(); shadow.close(); ldr.close();
        for (Gta8Framebuffer f : bloom) if (f != null) f.close();
        textures.close();
        actors.close();
        uploaded = false;
    }

    // ------------------------------------------------------------------ frame
    public void render(Gta8Game game, int displayWidth, int displayHeight) {
        if (failure != null) return;
        if (buildError != null) { failure = "City build failed: " + buildError; return; }
        if (!built) return;
        long start = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(.1, (start - lastNanos) / 1e9);
        lastNanos = start;
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int destination = GL11.glGetInteger(0x8CA6);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int[] unitBindings = new int[8];
        for (int unit = 1; unit < 8; unit++) { GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit); unitBindings[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D); }
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT | GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        try {
            if (!uploaded) {
                try { initialise(); } catch (Exception e) { failure = e.getMessage(); return; }
            }
            prepareState();
            this.camera = game.camera;
            this.time = game.time;
            this.weatherRef = game.weather;
            atmosphere.update(game.weather.hours, game.weather);
            textures.updateSignals(world, game.time);
            actors.update(game, dt);
            renderWidth = Math.max(1, (int) Math.round(displayWidth * renderScale));
            renderHeight = Math.max(1, (int) Math.round(displayHeight * renderScale));
            matrices(game, renderWidth, renderHeight);
            collectLights(game);
            refreshBrokenProps();
            if (shadowsOn()) renderShadows(game);
            if (!scene.ensure(renderWidth, renderHeight, hdr ? Gta8Framebuffer.RGBA16F : Gta8Framebuffer.RGBA8, true, false)) { failure = "Scene framebuffer unavailable"; return; }
            scene.bind();
            GL11.glDepthMask(true);
            GL11.glClearColor(0, 0, 0, 1);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            sky(game);
            opaque(game);
            water(game);
            transparent(game);
            actors.effects(game);
            rain(game);
            actors.viewModel(game);
            post(game, destination, displayWidth, displayHeight);
            int error = GL11.glGetError();
            if (error != 0 && failure == null) System.err.println("GTA8 GL error " + error);
        } finally {
            Gta8Mesh.disableAttributes();
            GL20.glUseProgram(0);
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, destination);
            for (int unit = 7; unit >= 1; unit--) { GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit); GL11.glBindTexture(GL11.GL_TEXTURE_2D, unitBindings[unit]); }
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glPopClientAttrib();
            GL11.glPopAttrib();
            GlStateManager.bindTexture(0);
            GlStateManager.bindTexture(boundTexture);
            GlStateManager.setActiveTexture(activeTexture);
            OpenGlHelper.setActiveTexture(activeTexture);
            GL20.glUseProgram(program);
            GlStateManager.resetColor();
        }
        lastFrameMillis = (System.nanoTime() - start) / 1e6;
    }

    private void prepareState() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glDisable(GL11.GL_STENCIL_TEST); GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_FOG); GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL); GL11.glDisable(GL11.GL_TEXTURE_2D);
        for (int i = 0; i < 6; i++) GL11.glDisable(GL11.GL_CLIP_PLANE0 + i);
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthRange(0, 1);
        GL11.glFrontFace(GL11.GL_CCW);
        GL11.glCullFace(GL11.GL_BACK);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        if (GLContext.getCapabilities().OpenGL14) GL14.glBlendEquation(GL14.GL_FUNC_ADD);
    }

    private boolean shadowsOn() { return quality >= MEDIUM; }
    double mainDistance() { return quality == LOW ? 700 : quality == MEDIUM ? 1100 : quality == HIGH ? 1600 : 2200; }
    double detailDistance() { return quality == LOW ? 170 : quality == MEDIUM ? 250 : quality == HIGH ? 360 : 480; }
    double foliageDistance() { return quality == LOW ? 320 : quality == MEDIUM ? 560 : quality == HIGH ? 850 : 1200; }

    private void matrices(Gta8Game game, int w, int h) {
        Gta8Camera c = game.camera;
        double fx = c.forwardX(), fy = c.forwardY(), fz = c.forwardZ();
        double upx = -Math.sin(Math.toRadians(c.yaw)) * Math.sin(Math.toRadians(c.pitch)) * 0, upy = 1, upz = 0;
        if (Math.abs(fy) > .99) { upx = Math.sin(Math.toRadians(c.yaw)); upy = 0; upz = -Math.cos(Math.toRadians(c.yaw)); }
        Gta8Math.lookDirection(view, c.x, c.y, c.z, fx, fy, fz, upx, upy, upz);
        if (c.roll != 0) Gta8Math.multiply(Gta8Math.rotationZ(temp, c.roll), view, view);
        Gta8Math.perspective(projection, c.fov, w / (double) h, .15, 7000);
        Gta8Math.multiply(projection, view, viewProjection);
        Gta8Math.invert(viewProjection, inverseViewProjection);
        float[] m = viewProjection;
        double[][] rows = {{m[0], m[4], m[8], m[12]}, {m[1], m[5], m[9], m[13]}, {m[2], m[6], m[10], m[14]}, {m[3], m[7], m[11], m[15]}};
        for (int i = 0; i < 6; i++) {
            double[] r = rows[i / 2];
            double s = i % 2 == 0 ? 1 : -1;
            double a = rows[3][0] + s * r[0], b = rows[3][1] + s * r[1], cc = rows[3][2] + s * r[2], d = rows[3][3] + s * r[3];
            double l = Math.sqrt(a * a + b * b + cc * cc);
            frustum[i][0] = a / l; frustum[i][1] = b / l; frustum[i][2] = cc / l; frustum[i][3] = d / l;
        }
    }
    boolean visible(double x, double y, double z, double radius) {
        for (double[] p : frustum) if (p[0] * x + p[1] * y + p[2] * z + p[3] < -radius) return false;
        return true;
    }

    // ------------------------------------------------------------------ lights
    private void collectLights(Gta8Game game) {
        dynamicLights.clear();
        double cx = camera.x, cy = camera.y, cz = camera.z;
        double night = atmosphere.night;
        for (Gta8World.Light l : world.lights) {
            if (l.kind == Gta8World.Light.STREET || l.kind == Gta8World.Light.BEACON || l.radius <= 0) continue;
            if (l.kind != Gta8World.Light.INTERIOR && night < .05) continue;
            double dx = l.x - cx, dy = l.y - cy, dz = l.z - cz, d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > 140 * 140) continue;
            double k = l.kind == Gta8World.Light.INTERIOR ? 3.2 : l.kind == Gta8World.Light.NEON ? 4.5 * night : l.kind == Gta8World.Light.FLOOD ? 9 * night : 5 * night;
            dynamicLights.add(new double[]{l.x, l.y, l.z, l.radius, (l.color >> 16 & 255) / 255.0 * k, (l.color >> 8 & 255) / 255.0 * k, (l.color & 255) / 255.0 * k, -2, 0, -1, 0, 0});
        }
        actors.lights(game, dynamicLights);
        // Keep the 16 lights that contribute most to the view.
        final double[] score = new double[dynamicLights.size()];
        for (int i = 0; i < score.length; i++) {
            double[] l = dynamicLights.get(i);
            double dx = l[0] - cx, dy = l[1] - cy, dz = l[2] - cz, d2 = dx * dx + dy * dy + dz * dz;
            double bright = l[4] + l[5] + l[6];
            boolean inView = visible(l[0], l[1], l[2], l[3]);
            score[i] = bright * l[3] / (d2 + 25) * (inView ? 1 : .15);
        }
        Integer[] order = new Integer[score.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, new java.util.Comparator<Integer>() { @Override public int compare(Integer a, Integer b) { return Double.compare(score[b], score[a]); } });
        lightPos.clear(); lightColor.clear(); lightDir.clear();
        lightCount = Math.min(16, order.length);
        for (int i = 0; i < 16; i++) {
            if (i < lightCount) {
                double[] l = dynamicLights.get(order[i]);
                lightPos.put((float) l[0]).put((float) l[1]).put((float) l[2]).put((float) l[3]);
                lightColor.put((float) l[4]).put((float) l[5]).put((float) l[6]).put((float) l[7]);
                lightDir.put((float) l[8]).put((float) l[9]).put((float) l[10]).put((float) l[11]);
            } else { for (int k = 0; k < 4; k++) { lightPos.put(0); lightColor.put(0); lightDir.put(0); } }
        }
        lightPos.flip(); lightColor.flip(); lightDir.flip();
        collectOccluders(game);
    }
    /** The 16 nearest cars and people darken the sky light on the ground beneath them. */
    private void collectOccluders(Gta8Game game) {
        final java.util.List<double[]> list = new java.util.ArrayList<double[]>();
        double a = game.alpha;
        for (Gta8Vehicle v : game.vehicles) {
            double x = Gta8Math.lerp(v.prevX, v.x, a), z = Gta8Math.lerp(v.prevZ, v.z, a), d = Math.hypot(x - camera.x, z - camera.z);
            if (d > 80) continue;
            double yaw = Math.toRadians(v.yaw);
            list.add(new double[]{d, x, Gta8Math.lerp(v.prevY, v.y, a), z, v.halfLength() + .05, Math.sin(yaw), -Math.cos(yaw), v.halfWidth() + .05, v.model.height});
        }
        for (Gta8Ped p : game.peds) {
            if (p.inVehicle() || p.dead) continue;
            double x = Gta8Math.lerp(p.prevX, p.x, a), z = Gta8Math.lerp(p.prevZ, p.z, a), d = Math.hypot(x - camera.x, z - camera.z);
            if (d > 30) continue;
            list.add(new double[]{d + 8, x, Gta8Math.lerp(p.prevY, p.y, a), z, .12, 1, 0, .12, 1.8});
        }
        java.util.Collections.sort(list, new java.util.Comparator<double[]>() { @Override public int compare(double[] p, double[] q) { return Double.compare(p[0], q[0]); } });
        occA.clear(); occB.clear();
        occCount = Math.min(16, list.size());
        for (int i = 0; i < 16; i++) {
            if (i < occCount) {
                double[] o = list.get(i);
                occA.put((float) o[1]).put((float) o[2]).put((float) o[3]).put((float) o[4]);
                occB.put((float) o[5]).put((float) o[6]).put((float) o[7]).put((float) o[8]);
            } else for (int k = 0; k < 4; k++) { occA.put(0); occB.put(0); }
        }
        occA.flip(); occB.flip();
    }

    /** Uniforms shared by every world-space shader. */
    void common(Gta8Shader s) {
        Gta8Atmosphere a = atmosphere;
        s.set("uCameraPos", camera.x, camera.y, camera.z);
        s.set("uSunDir", a.lightDir[0], a.lightDir[1], a.lightDir[2]);
        s.set("uSkySunDir", a.sunDir[0], a.sunDir[1], a.sunDir[2]);
        s.set("uMoonDir", a.moonDir[0], a.moonDir[1], a.moonDir[2]);
        s.set("uSunColor", a.lightColor[0], a.lightColor[1], a.lightColor[2]);
        s.set("uSkyAmbient", a.skyAmbient[0], a.skyAmbient[1], a.skyAmbient[2]);
        s.set("uGroundAmbient", a.groundAmbient[0], a.groundAmbient[1], a.groundAmbient[2]);
        s.set("uBetaR", a.betaR[0], a.betaR[1], a.betaR[2]);
        s.set("uBetaM", a.betaM[0], a.betaM[1], a.betaM[2]);
        s.set("uSunE", (float) a.sunE);
        s.set("uMoonE", (float) a.moonE);
        s.set("uTime", (float) (time % 3600));
        s.set("uNight", (float) a.night);
        s.set("uCloud", (float) a.cloud);
        s.set("uFogDensity", (float) a.fog);
        s.set("uWet", (float) weather().wet);
        s.set("uRain", (float) weather().rain);
        s.set("uLightning", (float) weather().lightning);
        s.set("uNoise", 0);
    }
    private Gta8Weather weatherRef;
    Gta8Weather weather() { return weatherRef; }

    void lighting(Gta8Shader s) {
        s.set("uShadowMap", 1); s.set("uSigns", 2); s.set("uFoliage", 3); s.set("uSignals", 4); s.set("uHeight", 5);
        s.matrix("uShadowMat0", shadowLookup[0]);
        s.matrix("uShadowMat1", shadowLookup[1]);
        s.set("uShadowParams", (float) (cascadeRadius[0] * .9), (float) (cascadeRadius[1] * .98), (float) (cascadeRadius[0] * 2 / Math.max(1, shadowSize)), (float) (cascadeRadius[1] * 2 / Math.max(1, shadowSize)));
        s.set("uShadowOn", shadowsOn() ? 1f : 0f);
        s.set("uShadowSoft", quality >= HIGH ? 1f : .8f);
        s.set("uShadowTexel", .5f / Math.max(1, shadowSize), 1f / Math.max(1, shadowSize));
        s.vec4s("uLightPos", lightPos); s.vec4s("uLightColor", lightColor); s.vec4s("uLightDir", lightDir);
        s.set("uLightCount", lightCount);
        s.vec4s("uOccA", occA); s.vec4s("uOccB", occB);
        s.set("uOccCount", occCount);
        halves.clear(); for (int i = 0; i < 11; i++) halves.put((float) world.nsHalf(i)); halves.flip(); s.floats("uNsHalf", halves);
        halves.clear(); for (int i = 0; i < 11; i++) halves.put((float) world.ewHalf(i)); halves.flip(); s.floats("uEwHalf", halves);
        double street = atmosphere.night * 21;
        s.set("uStreetColor", (float) (street * 1.0), (float) (street * .86), (float) (street * .68));
        s.set("uIndoorLight", .85f, .86f, .84f);
    }
    void bindTextures() {
        GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures.noise);
        GL13.glActiveTexture(GL13.GL_TEXTURE1); GL11.glBindTexture(GL11.GL_TEXTURE_2D, shadow.depth);
        GL13.glActiveTexture(GL13.GL_TEXTURE2); GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures.signs);
        GL13.glActiveTexture(GL13.GL_TEXTURE3); GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures.foliage);
        GL13.glActiveTexture(GL13.GL_TEXTURE4); GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures.signals);
        GL13.glActiveTexture(GL13.GL_TEXTURE5); GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures.height);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }
    Gta8Shader worldShader() { return worldShader; }
    Gta8Shader skinnedShader() { return skinnedShader; }
    Gta8Shader particleShader() { return particleShader; }
    Gta8Shader shadowSkinnedShader() { return shadowSkinned; }
    Gta8Shader shadowShader() { return shadowShader; }

    // ------------------------------------------------------------------ passes
    private void renderShadows(Gta8Game game) {
        shadowSize = quality >= ULTRA ? 3072 : quality >= HIGH ? 2048 : 1024;
        if (!shadow.ensure(shadowSize * 2, shadowSize, 0, true, true)) { quality = LOW; return; }
        shadow.bind();
        GL11.glDepthMask(true);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(1.6f, 3f);
        GL11.glColorMask(false, false, false, false);
        cascadeRadius[0] = quality >= ULTRA ? 42 : 32;
        cascadeRadius[1] = quality >= ULTRA ? 260 : quality >= HIGH ? 190 : 140;
        double[] L = atmosphere.lightDir;
        double upx = 0, upy = 1, upz = 0;
        if (Math.abs(L[1]) > .97) { upx = 0; upy = 0; upz = 1; }
        Gta8Math.lookDirection(lightView, 0, 0, 0, -L[0], -L[1], -L[2], upx, upy, upz);
        double[] ls = new double[3];
        Gta8Mesh.enableAttributes();
        for (int c = 0; c < 2; c++) {
            double r = cascadeRadius[c], ahead = c == 0 ? .5 : .55;
            double cx = camera.x + camera.forwardX() * r * ahead, cy = camera.y + camera.forwardY() * r * ahead * .5, cz = camera.z + camera.forwardZ() * r * ahead;
            cascadeCenter[c][0] = cx; cascadeCenter[c][1] = cy; cascadeCenter[c][2] = cz;
            Gta8Math.transform(lightView, cx, cy, cz, ls);
            double texel = r * 2 / shadowSize;
            double sx = Math.floor(ls[0] / texel) * texel, sy = Math.floor(ls[1] / texel) * texel;
            Gta8Math.ortho(lightProjection, sx - r, sx + r, sy - r, sy + r, -ls[2] - 900, -ls[2] + 700);
            Gta8Math.multiply(lightProjection, lightView, shadowViewProjection[c]);
            float[] bias = Gta8Math.identity(temp);
            bias[0] = .25f; bias[5] = .5f; bias[10] = .5f; bias[12] = .25f + .5f * c; bias[13] = .5f; bias[14] = .5f;
            Gta8Math.multiply(bias, shadowViewProjection[c], shadowLookup[c]);
            GL11.glViewport(c * shadowSize, 0, shadowSize, shadowSize);
            shadowShader.bind();
            shadowShader.matrix("uViewProj", shadowViewProjection[c]);
            shadowShader.matrix("uModel", Gta8Math.identity(temp));
            shadowShader.set("uTime", (float) (time % 3600));
            shadowShader.set("uFoliage", 3);
            GL13.glActiveTexture(GL13.GL_TEXTURE3); GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures.foliage); GL13.glActiveTexture(GL13.GL_TEXTURE0);
            for (Gta8CityMesher.Chunk ch : mesher.active) {
                Gta8Math.transform(lightView, ch.centerX, ch.centerY, ch.centerZ, ls);
                double lx = ls[0] - sx, ly = ls[1] - sy;
                if (Math.abs(lx) > r + ch.radius || Math.abs(ly) > r + ch.radius) continue;
                if (ch.mainMesh != null) ch.mainMesh.draw();
                double dist = Math.sqrt(ch.distanceSquared(camera.x, camera.y, camera.z));
                if (ch.detailMesh != null && dist < Math.min(detailDistance(), r * 1.6)) ch.detailMesh.draw();
                if (ch.foliageMesh != null && dist < foliageDistance()) ch.foliageMesh.draw();
            }
            actors.shadows(game, c, shadowViewProjection[c], r, sx, sy, lightView);
        }
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glColorMask(true, true, true, true);
    }

    private void sky(Gta8Game game) {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        skyShader.bind();
        common(skyShader);
        skyShader.matrix("uInvViewProj", inverseViewProjection);
        skyShader.set("uWind", (float) (weather().windX * .0009 + .0004), (float) (weather().windZ * .0009 + .0002));
        bindTextures();
        fullscreen();
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
    }

    private void opaque(Gta8Game game) {
        Gta8Shader s = worldShader;
        s.bind();
        common(s);
        lighting(s);
        s.matrix("uViewProj", viewProjection);
        s.matrix("uModel", Gta8Math.identity(temp));
        s.set("uTransparent", 0f);
        s.set("uDamage", 0f);
        bindTextures();
        Gta8Mesh.enableAttributes();
        GL11.glEnable(GL11.GL_CULL_FACE);
        drawnChunks = 0;
        double main = mainDistance(), detail = detailDistance(), foliage = foliageDistance();
        List<Gta8CityMesher.Chunk> foliageChunks = new ArrayList<Gta8CityMesher.Chunk>();
        for (Gta8CityMesher.Chunk ch : mesher.active) {
            double d2 = ch.distanceSquared(camera.x, camera.y, camera.z);
            if (d2 > main * main || !visible(ch.centerX, ch.centerY, ch.centerZ, ch.radius)) continue;
            drawnChunks++;
            if (ch.mainMesh != null) ch.mainMesh.draw();
            if (ch.detailMesh != null && d2 < detail * detail) ch.detailMesh.draw();
            if (ch.foliageMesh != null && d2 < foliage * foliage) foliageChunks.add(ch);
        }
        if (mesher.horizonMesh != null) mesher.horizonMesh.draw();
        actors.opaque(game);
        s.bind();
        s.matrix("uModel", Gta8Math.identity(temp));
        s.set("uDamage", 0f);
        GL11.glDisable(GL11.GL_CULL_FACE);
        for (Gta8CityMesher.Chunk ch : foliageChunks) ch.foliageMesh.draw();
        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    private void water(Gta8Game game) {
        waterShader.bind();
        common(waterShader);
        lighting(waterShader);
        waterShader.matrix("uViewProj", viewProjection);
        waterShader.set("uHeightRect", (float) -Gta8Textures.HEIGHT_EXTENT, (float) -Gta8Textures.HEIGHT_EXTENT, (float) (Gta8Textures.HEIGHT_EXTENT * 2), (float) (Gta8Textures.HEIGHT_EXTENT * 2));
        waterShader.set("uHeightRange", (float) Gta8Textures.HEIGHT_MIN, (float) Gta8Textures.HEIGHT_SPAN);
        double snap = 8;
        waterShader.set("uOffset", (float) (Math.floor(camera.x / snap) * snap), (float) (Math.floor(camera.z / snap) * snap));
        bindTextures();
        GL11.glDisable(GL11.GL_CULL_FACE);
        waterNear.draw();
        waterFar.draw();
        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    private void transparent(Gta8Game game) {
        if (glassMesh == null || glassRevision != world.brokenRevision) {
            if (glassMesh != null) glassMesh.close();
            Gta8MeshBuilder b = new Gta8MeshBuilder();
            for (Gta8World.Prop p : world.props) if (p.type == Gta8World.Prop.GLASS && !p.broken) {
                b.push(); b.translate(p.x, p.y, p.z); b.yaw(p.yaw);
                b.material(Gta8Materials.GLASSPANE).color(0xFFFFFF).ao(1);
                double hw = p.sx / 2;
                b.quad(hw, 0, 0, -hw, 0, 0, -hw, p.sy, 0, hw, p.sy, 0, 0, 0, 1, 0, 1, 1, 0, 1);
                b.quad(-hw, 0, 0, hw, 0, 0, hw, p.sy, 0, -hw, p.sy, 0, 0, 0, 1, 0, 1, 1, 0, 1);
                b.pop();
            }
            glassMesh = Gta8Mesh.upload(b);
            glassRevision = world.brokenRevision;
        }
        if (glassMesh == null) return;
        Gta8Shader s = worldShader;
        s.bind();
        s.set("uTransparent", 1f);
        s.matrix("uModel", Gta8Math.identity(temp));
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        glassMesh.draw();
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
        s.set("uTransparent", 0f);
    }

    private void rain(Gta8Game game) {
        double rain = weather().rain;
        if (rain < .02 || game.cameraIndoors) return;
        rainShader.bind();
        common(rainShader);
        rainShader.matrix("uViewProj", viewProjection);
        rainShader.set("uBox", 34f, 22f, 34f);
        rainShader.set("uFall", (float) weather().windX * 1.4f, -11f, (float) weather().windZ * 1.4f);
        rainShader.set("uIntensity", (float) Math.min(1, rain * 1.3));
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        rainMesh.draw();
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    private void post(Gta8Game game, int destination, int displayWidth, int displayHeight) {
        Gta8Mesh.disableAttributes();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);
        int bloomTexture = 0;
        if (bloomEnabled && quality >= MEDIUM) {
            int w = renderWidth, h = renderHeight, levels = 0;
            for (int i = 0; i < bloom.length; i++) {
                w = Math.max(1, w / 2); h = Math.max(1, h / 2);
                if (!bloom[i].ensure(w, h, hdr ? Gta8Framebuffer.RGBA16F : Gta8Framebuffer.RGBA8, false, false)) break;
                levels++;
                if (w < 8 || h < 8) break;
            }
            downShader.bind();
            downShader.set("uTex", 6);
            downShader.set("uThreshold", 1.1f);
            int source = scene.color, sw = renderWidth, sh = renderHeight;
            for (int i = 0; i < levels; i++) {
                bloom[i].bind();
                GL13.glActiveTexture(GL13.GL_TEXTURE6); GL11.glBindTexture(GL11.GL_TEXTURE_2D, source);
                downShader.set("uTexel", 1f / sw, 1f / sh);
                downShader.set("uPrefilter", i == 0 ? 1f : 0f);
                fullscreen();
                source = bloom[i].color; sw = bloom[i].width; sh = bloom[i].height;
            }
            upShader.bind();
            upShader.set("uTex", 6);
            upShader.set("uRadius", 1f);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
            for (int i = levels - 1; i > 0; i--) {
                bloom[i - 1].bind();
                GL13.glActiveTexture(GL13.GL_TEXTURE6); GL11.glBindTexture(GL11.GL_TEXTURE_2D, bloom[i].color);
                upShader.set("uTexel", 1f / bloom[i].width, 1f / bloom[i].height);
                fullscreen();
            }
            GL11.glDisable(GL11.GL_BLEND);
            bloomTexture = levels > 0 ? bloom[0].color : 0;
        }
        if (!ldr.ensure(renderWidth, renderHeight, Gta8Framebuffer.RGBA8, false, false)) { failure = "Post framebuffer unavailable"; return; }
        ldr.bind();
        compositeShader.bind();
        compositeShader.set("uScene", 6);
        compositeShader.set("uBloom", 7);
        GL13.glActiveTexture(GL13.GL_TEXTURE6); GL11.glBindTexture(GL11.GL_TEXTURE_2D, scene.color);
        GL13.glActiveTexture(GL13.GL_TEXTURE7); GL11.glBindTexture(GL11.GL_TEXTURE_2D, bloomTexture);
        compositeShader.set("uExposure", (float) atmosphere.exposure);
        compositeShader.set("uBloomStrength", bloomTexture == 0 ? 0f : .055f + .05f * (float) atmosphere.night);
        compositeShader.set("uSaturation", 1.08f);
        compositeShader.set("uContrast", 1.04f);
        compositeShader.set("uVignette", .28f);
        compositeShader.set("uGrain", .012f);
        compositeShader.set("uWasted", wasted);
        compositeShader.set("uHurt", hurt);
        compositeShader.set("uTime", (float) (time % 1000));
        double golden = Gta8Math.smooth(.35, .05, Math.abs(atmosphere.sunDir[1] - .08)) * (1 - atmosphere.cloud);
        compositeShader.set("uGrade", (float) (1 + .06 * golden), 1f, (float) (1 - .07 * golden + .05 * atmosphere.night));
        compositeShader.set("uResolution", renderWidth, renderHeight);
        compositeShader.set("uHdr", hdr ? 1f : 0f);
        fullscreen();
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, destination);
        GL11.glViewport(0, 0, displayWidth, displayHeight);
        fxaaShader.bind();
        fxaaShader.set("uTex", 6);
        fxaaShader.set("uTexel", 1f / renderWidth, 1f / renderHeight);
        fxaaShader.set("uEnabled", fxaaEnabled ? 1f : 0f);
        fxaaShader.set("uSharpen", renderScale < .99 ? 1.2f : .6f);
        GL13.glActiveTexture(GL13.GL_TEXTURE6); GL11.glBindTexture(GL11.GL_TEXTURE_2D, ldr.color);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        fullscreen();
    }

    static void fullscreen() {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(-1, -1); GL11.glVertex2f(1, -1); GL11.glVertex2f(1, 1); GL11.glVertex2f(-1, 1);
        GL11.glEnd();
    }

    private void refreshBrokenProps() {
        for (Gta8CityMesher.Chunk c : mesher.active) if (c.propRevision != world.brokenRevision) {
            boolean affected = false;
            for (Gta8World.Prop p : c.props) if (p.broken) { affected = true; break; }
            if (affected || c.propRevision >= 0) {
                if (affected) mesher.refreshProps(c); else c.propRevision = world.brokenRevision;
            }
        }
    }

    // ------------------------------------------------------------------ static helper meshes
    private static Gta8Mesh waterGrid(double half, double cell) {
        Gta8MeshBuilder b = new Gta8MeshBuilder();
        int n = (int) (half * 2 / cell);
        for (int j = 0; j <= n; j++) for (int i = 0; i <= n; i++) b.vertex(-half + i * cell, Gta8World.WATER, -half + j * cell, 0, 1, 0, 0, 0);
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) { int a = j * (n + 1) + i; b.quadIndices(a + n + 1, a + n + 2, a + 1, a); }
        return Gta8Mesh.upload(b);
    }
    private static Gta8Mesh waterRing() {
        Gta8MeshBuilder b = new Gta8MeshBuilder();
        double in = 240, out = 9000, y = Gta8World.WATER;
        double[][] rects = {{-out, -out, out, -in}, {-out, in, out, out}, {-out, -in, -in, in}, {in, -in, out, in}};
        for (double[] r : rects) b.quad(r[0], y, r[3], r[2], y, r[3], r[2], y, r[1], r[0], y, r[1], 0, 0, 1, 0, 1, 1, 0, 1);
        return Gta8Mesh.upload(b);
    }
    private static Gta8Mesh rainStreaks(int count) {
        Gta8MeshBuilder b = new Gta8MeshBuilder();
        java.util.Random r = new java.util.Random(9);
        for (int i = 0; i < count; i++) {
            double x = r.nextDouble(), y = r.nextDouble(), z = r.nextDouble();
            int a = b.vertex(x, y, z, 0, 1, 0, -1, 0), c = b.vertex(x, y, z, 0, 1, 0, 1, 0);
            int d = b.vertex(x, y, z, 0, 1, 0, 1, 1), e = b.vertex(x, y, z, 0, 1, 0, -1, 1);
            b.quadIndices(a, c, d, e);
        }
        return Gta8Mesh.upload(b);
    }

    void setWeather(Gta8Weather weather) { this.weatherRef = weather; }
    Gta8World world() { return world; }
    Gta8Textures.SignAtlas atlas() { return mesher.atlas; }
    int renderWidth() { return renderWidth; }
    int renderHeight() { return renderHeight; }
    static FloatBuffer floats(int n) { return ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer(); }
    static void unbindBuffers() { GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0); GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0); }
}
