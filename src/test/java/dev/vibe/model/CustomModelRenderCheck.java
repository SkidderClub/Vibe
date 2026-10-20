package dev.vibe.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vibe.ui.render.CustomModelRenderer;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.Pbuffer;
import org.lwjgl.opengl.PixelFormat;
import org.lwjgl.util.glu.GLU;

/**
 * Renders custom models offscreen with the client's own drawing code: held knives in vanilla's
 * first-person sword transform next to the diamond sword sprite, and characters in several
 * ModelBiped poses next to the vanilla body. Synthetic models are always checked; real files can
 * be added with {@code -Dvibe.models=sword:path/knife.glb;player:path/hero.gltf} and the triangle budget
 * changed with {@code -Dvibe.budget=10000}.
 */
public final class CustomModelRenderCheck {

    private static final int PANEL = 320, PANELS = 4;
    private static int spriteTexture, whiteTexture;

    public static void main(String[] args) throws Exception {
        Path output = Paths.get(args.length > 0 ? args[0] : "build/custom-model-render-check");
        Files.createDirectories(output);
        Pbuffer buffer = new Pbuffer(PANEL * PANELS, PANEL, new PixelFormat(8, 24, 0), null, null);
        buffer.makeCurrent();
        try {
            OpenGlHelper.initializeTextures();
            spriteTexture = texture(ImageIO.read(resource("/assets/minecraft/textures/items/diamond_sword.png")), false);
            BufferedImage white = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            white.setRGB(0, 0, 0xFFC8C8C8);
            whiteTexture = texture(white, false);
            float[][] firstPerson = displayTransform("firstperson");
            List<String[]> models = new ArrayList<String[]>();
            models.add(new String[] {"sword", "synthetic-knife"});
            models.add(new String[] {"player", "synthetic-tpose"});
            models.add(new String[] {"player", "synthetic-apose"});
            String extra = System.getProperty("vibe.models", "");
            for (String entry : extra.split(";")) {
                int colon = entry.indexOf(':');
                if (colon > 0) models.add(new String[] {entry.substring(0, colon), entry.substring(colon + 1)});
            }
            for (String[] model : models) {
                boolean sword = model[0].equals("sword");
                long start = System.nanoTime();
                MeshData mesh = mesh(model[1]);
                long loaded = System.nanoTime();
                int budget = Integer.getInteger("vibe.budget", 60000);
                PreparedModel prepared = sword ? SwordFitter.prepare(mesh, budget) : PlayerFitter.prepare(mesh, budget, 0, true);
                long fitted = System.nanoTime();
                CompiledModel compiled = CompiledModel.compile(prepared);
                long uploaded = System.nanoTime();
                String name = new File(model[1]).getName().replaceAll("[^A-Za-z0-9_.-]", "_");
                GL11.glClearColor(0.18F, 0.2F, 0.24F, 1.0F);
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
                if (sword) swordPanels(compiled, firstPerson);
                else playerPanels(compiled);
                assertState();
                int error = GL11.glGetError();
                if (error != GL11.GL_NO_ERROR) throw new AssertionError("OpenGL error " + error + " for " + name);
                save(output.resolve(model[0] + "-" + name + ".png"));
                System.out.printf("%s %s: %d of %d triangles, load %.1f ms, fit %.1f ms, upload %.1f ms, %s%n", model[0], name,
                        compiled.getTriangles(), compiled.getSourceTriangles(), (loaded - start) / 1e6, (fitted - loaded) / 1e6,
                        (uploaded - fitted) / 1e6, compiled.getNotes());
                compiled.delete();
            }
            System.out.println("Custom model rendering OK; screenshots in " + output.toAbsolutePath());
        } finally {
            buffer.destroy();
        }
    }

    private static MeshData mesh(String source) throws Exception {
        if (source.equals("synthetic-knife")) {
            MeshData mesh = TestMeshes.knife().build(false);
            mesh.transform(Matrix.fromTrs(new double[] {1, 2, 3}, new double[] {0.3, -0.2, 0.6, 0.71}, new double[] {4, 4, 4}));
            return mesh;
        }
        if (source.startsWith("synthetic-")) {
            MeshData mesh = TestMeshes.humanoid(source.endsWith("tpose"), false).build(false);
            mesh.transform(Matrix.rotation(90, 0, 1, 0));
            return mesh;
        }
        File file = new File(source);
        return ModelLoader.load(file.isDirectory() ? ModelFiles.findModelFile(file) : file, ModelLoadOptions.defaults());
    }

    // ---- swords ----

    private static void swordPanels(CompiledModel compiled, float[][] firstPerson) {
        // Panel 0: item space seen from the front, the sprite behind the model.
        viewport(0);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.loadIdentity();
        GL11.glOrtho(-0.15, 1.15, -0.15, 1.15, -10, 10);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.loadIdentity();
        GlStateManager.translate(0.5F, 0.5F, 0.0F);
        GlStateManager.scale(2.0F, 2.0F, 2.0F);
        lights();
        sprite(0.35F);
        CustomModelRenderer.drawSword(compiled, 1.0F, new float[3], new float[3], false, false, false);
        // Panels 1 and 2: vanilla's first-person sword transform with the sprite and with the model.
        for (int panel = 1; panel <= 2; panel++) {
            viewport(panel);
            firstPersonProjection();
            // The game's wide screen shows the hand at the lower right; this square panel moves the camera there.
            GlStateManager.translate(-0.42F, 0.36F, -0.15F);
            GlStateManager.translate(0.56F, -0.52F, -0.72F);
            GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.scale(0.4F, 0.4F, 0.4F);
            GlStateManager.scale(2.0F, 2.0F, 2.0F);
            apply(firstPerson);
            lights();
            if (panel == 1) sprite(1.0F);
            else CustomModelRenderer.drawSword(compiled, 1.0F, new float[3], new float[3], false, false, false);
        }
        // Panel 3: the model from the side, with the blocking-style X rotation of AnimationsModule settings at 0.
        viewport(3);
        firstPersonProjection();
        GlStateManager.translate(0.0F, -0.1F, -1.6F);
        GlStateManager.rotate(70.0F, 0.0F, 1.0F, 0.0F);
        lights();
        sprite(0.35F);
        CustomModelRenderer.drawSword(compiled, 1.0F, new float[3], new float[3], false, false, false);
    }

    /** The vanilla sprite as a quad in renderItem's space (scale 0.5, translate -0.5, unit square at z = 0.5). */
    private static void sprite(float alpha) {
        GlStateManager.pushMatrix();
        GlStateManager.scale(0.5F, 0.5F, 0.5F);
        GlStateManager.translate(-0.5F, -0.5F, -0.5F);
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(spriteTexture);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
        GlStateManager.disableLighting();
        GlStateManager.depthMask(alpha >= 1.0F);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex3f(0, 0, 0.5F);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex3f(1, 0, 0.5F);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex3f(1, 1, 0.5F);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex3f(0, 1, 0.5F);
        GL11.glEnd();
        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    /** Reads the diamond sword display transform: {translation, rotation, scale}. */
    private static float[][] displayTransform(String view) throws Exception {
        JsonObject root = new JsonParser().parse(new InputStreamReader(resource("/assets/minecraft/models/item/diamond_sword.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject display = root.getAsJsonObject("display").getAsJsonObject(view);
        return new float[][] {vector(display, "translation", 0.0625F), vector(display, "rotation", 1.0F), vector(display, "scale", 1.0F)};
    }

    private static float[] vector(JsonObject object, String key, float factor) {
        JsonArray array = object.getAsJsonArray(key);
        return new float[] {array.get(0).getAsFloat() * factor, array.get(1).getAsFloat() * factor, array.get(2).getAsFloat() * factor};
    }

    /** ItemCameraTransforms.applyTransform. */
    private static void apply(float[][] transform) {
        GlStateManager.translate(transform[0][0], transform[0][1], transform[0][2]);
        GlStateManager.rotate(transform[1][1], 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(transform[1][0], 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(transform[1][2], 0.0F, 0.0F, 1.0F);
        GlStateManager.scale(transform[2][0], transform[2][1], transform[2][2]);
    }

    // ---- players ----

    private static void playerPanels(CompiledModel compiled) {
        ModelBiped biped = new ModelBiped(0.0F);
        for (int panel = 0; panel < PANELS; panel++) {
            viewport(panel);
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.loadIdentity();
            GLU.gluPerspective(40.0F, 1.0F, 0.05F, 50.0F);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.loadIdentity();
            GlStateManager.translate(0.0F, -0.95F, -5.2F);
            GlStateManager.rotate(12.0F, 1.0F, 0.0F, 0.0F);
            lights();
            biped.isSneak = panel == 2;
            biped.heldItemRight = panel == 2 ? 1 : 0;
            biped.aimedBow = panel == 3;
            biped.swingProgress = panel == 2 ? 0.3F : 0.0F;
            float swing = panel == 1 ? 2.2F : 0.0F, amount = panel == 1 ? 1.0F : 0.0F;
            float headYaw = panel == 3 ? 35.0F : 0.0F, headPitch = panel == 3 ? -15.0F : 0.0F;
            float bodyYaw = panel == 0 ? 0.0F : panel == 1 ? 70.0F : panel == 2 ? -40.0F : 160.0F;
            for (int side = 0; side < 2; side++) {
                biped.setRotationAngles(swing, amount, 10.0F, headYaw, headPitch, 0.0625F, null);
                GlStateManager.pushMatrix();
                // RendererLivingEntity's transform for a player standing at x = -0.7 (vanilla) or 0.7 (custom).
                GlStateManager.translate(side == 0 ? -0.7F : 0.7F, 0.0F, 0.0F);
                GlStateManager.rotate(180.0F - bodyYaw, 0.0F, 1.0F, 0.0F);
                GlStateManager.enableRescaleNormal();
                GlStateManager.scale(-1.0F, -1.0F, 1.0F);
                GlStateManager.scale(0.9375F, 0.9375F, 0.9375F);
                GlStateManager.translate(0.0F, -1.5078125F, 0.0F);
                GlStateManager.enableTexture2D();
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                if (side == 0) {
                    GlStateManager.bindTexture(whiteTexture);
                    if (biped.isSneak) GlStateManager.translate(0.0F, 0.2F, 0.0F);
                    biped.bipedHead.render(0.0625F);
                    biped.bipedBody.render(0.0625F);
                    biped.bipedRightArm.render(0.0625F);
                    biped.bipedLeftArm.render(0.0625F);
                    biped.bipedRightLeg.render(0.0625F);
                    biped.bipedLeftLeg.render(0.0625F);
                } else {
                    CustomModelRenderer.drawPlayer(compiled, biped, 0, 0.0625F, biped.isSneak, true);
                }
                GlStateManager.popMatrix();
            }
        }
    }

    // ---- GL helpers ----

    private static void viewport(int panel) {
        GL11.glViewport(panel * PANEL, 0, PANEL, PANEL);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(panel * PANEL, 0, PANEL, PANEL);
        GL11.glClearColor(0.16F + panel * 0.02F, 0.19F, 0.24F, 1.0F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.enableCull();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.disableBlend();
    }

    private static void firstPersonProjection() {
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.loadIdentity();
        GLU.gluPerspective(70.0F, 1.0F, 0.05F, 10.0F);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.loadIdentity();
    }

    private static void lights() {
        GlStateManager.enableLighting();
        RenderHelper.enableStandardItemLighting();
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableColorMaterial();
    }

    /** CustomModelRenderer must hand back the cull, blend, alpha and shading state it found. */
    private static void assertState() {
        if (!GL11.glIsEnabled(GL11.GL_CULL_FACE)) throw new AssertionError("Culling was not restored");
        if (GL11.glIsEnabled(GL11.GL_BLEND)) throw new AssertionError("Blending leaked");
        if (!GL11.glIsEnabled(GL11.GL_ALPHA_TEST)) throw new AssertionError("Alpha test was not restored");
        if (GL11.glGetInteger(GL11.GL_SHADE_MODEL) != GL11.GL_FLAT) throw new AssertionError("Shade model leaked");
        if (Math.abs(GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF) - 0.1F) > 1e-4F) throw new AssertionError("Alpha reference leaked");
    }

    private static int texture(BufferedImage image, boolean linear) {
        int width = image.getWidth(), height = image.getHeight();
        IntBuffer pixels = BufferUtils.createIntBuffer(width * height);
        pixels.put(image.getRGB(0, 0, width, height, null, 0, width)).flip();
        int id = GL11.glGenTextures();
        GlStateManager.bindTexture(id);
        int filter = linear ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, org.lwjgl.opengl.GL12.GL_BGRA,
                org.lwjgl.opengl.GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
        return id;
    }

    private static InputStream resource(String path) {
        InputStream stream = CustomModelRenderCheck.class.getResourceAsStream(path);
        if (stream == null) throw new IllegalStateException("Missing resource " + path);
        return stream;
    }

    private static void save(Path file) throws Exception {
        int width = PANEL * PANELS, height = PANEL;
        GL11.glViewport(0, 0, width, height);
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = ((height - 1 - y) * width + x) * 4;
                image.setRGB(x, y, (pixels.get(index) & 255) << 16 | (pixels.get(index + 1) & 255) << 8 | (pixels.get(index + 2) & 255));
            }
        }
        ImageIO.write(image, "png", file.toFile());
    }
}
