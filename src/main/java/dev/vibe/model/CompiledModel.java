package dev.vibe.model;

import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/**
 * A {@link PreparedModel} uploaded to the GPU: one display list per level of detail, part and
 * material, plus mipmapped textures. Drawing a part is a texture bind and a list call, so a
 * 60k triangle character costs the CPU about as much as a vanilla player.
 *
 * <p>Display lists contain geometry only. Textures, colour and blending are set outside them
 * through {@link GlStateManager} so Minecraft's cached GL state stays truthful.
 */
public final class CompiledModel {

    private static int whiteTexture;
    private static FloatBuffer scratch;

    private final int[] textures;
    private final float[][] colors;
    private final int[] alphaModes;
    private final float[] alphaCutoffs;
    /** [lod][part][batch] display list ids, material indices and vertex colour flags. */
    private final int[][][] lists;
    private final int[][][] listMaterials;
    private final boolean[][][] listColors;
    private final float[][] pivots;
    private final float[] anchors;
    private final int triangles;
    private final int sourceTriangles;
    private final String notes;
    private boolean deleted;

    private CompiledModel(PreparedModel prepared) {
        // Uploads happen mid-frame, inside a render hook; leave the caller's texture bound.
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int materialCount = prepared.materials.size();
        textures = new int[materialCount];
        colors = new float[materialCount][];
        alphaModes = new int[materialCount];
        alphaCutoffs = new float[materialCount];
        boolean[] used = new boolean[materialCount];
        for (PreparedModel.Lod lod : prepared.lods) {
            for (PreparedModel.Batch[] batches : lod.parts) for (PreparedModel.Batch batch : batches) used[batch.material] = true;
        }
        // Materials often share one image; upload each image once.
        Map<BufferedImage, Integer> uploaded = new IdentityHashMap<BufferedImage, Integer>();
        for (int material = 0; material < materialCount; material++) {
            MeshData.Material source = prepared.materials.get(material);
            colors[material] = source.color;
            alphaModes[material] = source.alphaMode;
            alphaCutoffs[material] = source.alphaCutoff;
            if (!used[material] || source.texture == null) continue;
            Integer texture = uploaded.get(source.texture);
            if (texture == null) {
                texture = upload(source.texture, source.repeat);
                uploaded.put(source.texture, texture);
            }
            textures[material] = texture;
        }
        lists = new int[prepared.lods.length][][];
        listMaterials = new int[prepared.lods.length][][];
        listColors = new boolean[prepared.lods.length][][];
        for (int lod = 0; lod < prepared.lods.length; lod++) {
            PreparedModel.Batch[][] parts = prepared.lods[lod].parts;
            lists[lod] = new int[parts.length][];
            listMaterials[lod] = new int[parts.length][];
            listColors[lod] = new boolean[parts.length][];
            for (int part = 0; part < parts.length; part++) {
                PreparedModel.Batch[] batches = sortOpaqueFirst(parts[part]);
                lists[lod][part] = new int[batches.length];
                listMaterials[lod][part] = new int[batches.length];
                listColors[lod][part] = new boolean[batches.length];
                for (int batch = 0; batch < batches.length; batch++) {
                    lists[lod][part][batch] = compile(batches[batch]);
                    listMaterials[lod][part][batch] = batches[batch].material;
                    listColors[lod][part][batch] = batches[batch].vertexColors;
                }
            }
        }
        scratch = null;
        GlStateManager.bindTexture(previousTexture);
        pivots = prepared.pivots;
        anchors = prepared.anchors;
        triangles = prepared.lods.length == 0 ? 0 : prepared.lods[0].triangles;
        sourceTriangles = prepared.sourceTriangles;
        notes = prepared.notes;
    }

    /** Must run on the render thread. */
    public static CompiledModel compile(PreparedModel prepared) {
        return new CompiledModel(prepared);
    }

    private PreparedModel.Batch[] sortOpaqueFirst(PreparedModel.Batch[] batches) {
        PreparedModel.Batch[] sorted = batches.clone();
        java.util.Arrays.sort(sorted, (a, b) -> Integer.compare(alphaModes[a.material], alphaModes[b.material]));
        return sorted;
    }

    public int lodCount() {
        return lists.length;
    }

    public float[][] getPivots() {
        return pivots;
    }

    public float[] getAnchors() {
        return anchors;
    }

    public int getTriangles() {
        return triangles;
    }

    public int getSourceTriangles() {
        return sourceTriangles;
    }

    public String getNotes() {
        return notes;
    }

    public boolean hasPart(int lod, int part) {
        return lod < lists.length && part < lists[lod].length && lists[lod][part].length > 0;
    }

    /**
     * Draws one part with its materials. {@code tint} multiplies every material colour; pass
     * {@code glint} to draw only the geometry for the enchantment overlay pass. Changes blend,
     * alpha test and the bound texture; {@link dev.vibe.ui.render.CustomModelRenderer} restores them.
     */
    public void drawPart(int lod, int part, float[] tint, boolean glint) {
        if (deleted || lod >= lists.length || part >= lists[lod].length) return;
        int[] ids = lists[lod][part];
        for (int batch = 0; batch < ids.length; batch++) {
            int material = listMaterials[lod][part][batch];
            if (glint) {
                GL11.glCallList(ids[batch]);
                continue;
            }
            int texture = textures[material];
            GlStateManager.bindTexture(texture != 0 ? texture : white());
            float[] color = colors[material];
            int mode = alphaModes[material];
            float alpha = mode == MeshData.Material.OPAQUE ? tint[3] : color[3] * tint[3];
            GlStateManager.color(color[0] * tint[0], color[1] * tint[1], color[2] * tint[2], alpha);
            if (mode == MeshData.Material.BLEND || alpha < 0.999F) {
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
                GlStateManager.enableAlpha();
                GlStateManager.alphaFunc(GL11.GL_GREATER, 0.003F);
            } else if (mode == MeshData.Material.MASK) {
                GlStateManager.disableBlend();
                GlStateManager.enableAlpha();
                GlStateManager.alphaFunc(GL11.GL_GREATER, alphaCutoffs[material]);
            } else {
                // glTF says opaque materials ignore texture alpha, which often holds unrelated data.
                GlStateManager.disableBlend();
                GlStateManager.disableAlpha();
            }
            GL11.glCallList(ids[batch]);
            // A list with per-vertex colours changes the current colour behind GlStateManager's back.
            if (listColors[lod][part][batch]) GlStateManager.resetColor();
        }
    }

    /** Frees textures and display lists; must run on the render thread. */
    public void delete() {
        if (deleted) return;
        deleted = true;
        for (int[][] lod : lists) for (int[] part : lod) for (int list : part) GLAllocation.deleteDisplayLists(list);
        Set<Integer> unique = new HashSet<Integer>();
        for (int texture : textures) if (texture != 0 && unique.add(texture)) GlStateManager.deleteTexture(texture);
    }

    private static int compile(PreparedModel.Batch batch) {
        float[] data = batch.vertices;
        if (scratch == null || scratch.capacity() < data.length) scratch = BufferUtils.createFloatBuffer(Math.max(data.length, 1 << 16));
        scratch.clear();
        scratch.put(data);
        scratch.flip();
        int stride = PreparedModel.STRIDE * 4;
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        scratch.position(0);
        GL11.glVertexPointer(3, stride, scratch);
        scratch.position(3);
        GL11.glNormalPointer(stride, scratch);
        scratch.position(6);
        GL11.glTexCoordPointer(2, stride, scratch);
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        if (batch.vertexColors) {
            scratch.position(8);
            GL11.glColorPointer(4, stride, scratch);
            GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
        }
        int list = GLAllocation.generateDisplayLists(1);
        // glDrawArrays copies the client arrays into the list at compile time.
        GL11.glNewList(list, GL11.GL_COMPILE);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, batch.vertexCount());
        GL11.glEndList();
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        if (batch.vertexColors) GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
        return list;
    }

    private static int upload(BufferedImage image, boolean repeat) {
        int width = image.getWidth(), height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        IntBuffer buffer = BufferUtils.createIntBuffer(pixels.length);
        buffer.put(pixels);
        buffer.flip();
        int texture = GL11.glGenTextures();
        GlStateManager.bindTexture(texture);
        ContextCapabilities capabilities = GLContext.getCapabilities();
        boolean generate30 = capabilities.OpenGL30;
        boolean generate14 = !generate30 && capabilities.OpenGL14;
        int wrap = repeat ? GL11.GL_REPEAT : GL12.GL_CLAMP_TO_EDGE;
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrap);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrap);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
                generate30 || generate14 ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_LINEAR);
        if (generate14) GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_GENERATE_MIPMAP, GL11.GL_TRUE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, generate30 || generate14 ? 1000 : 0);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL12.GL_BGRA,
                GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
        if (generate30) GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        return texture;
    }

    /** A 1x1 white texture for untextured materials, so texturing never has to be toggled. */
    private static int white() {
        if (whiteTexture == 0) {
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, 0xFFFFFFFF);
            whiteTexture = upload(image, true);
        }
        return whiteTexture;
    }
}
