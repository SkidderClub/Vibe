package dev.vibe.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads glTF 2.0 (.gltf with external or embedded buffers) and binary .glb files, the formats
 * Sketchfab offers for every downloadable model. Node transforms and skins are applied so the
 * result is the model as it appears in a viewer, in metres with +Y up.
 */
final class GltfLoader {

    private static final int GLB_MAGIC = 0x46546C67;
    private static final int CHUNK_JSON = 0x4E4F534A;
    private static final int CHUNK_BIN = 0x004E4942;

    private final File directory;
    private final ModelLoadOptions options;
    private final JsonObject root;
    private final ByteBuffer glbBinary;
    private final Map<Integer, ByteBuffer> buffers = new HashMap<Integer, ByteBuffer>();
    private final Map<Integer, BufferedImage> images = new HashMap<Integer, BufferedImage>();
    private final List<MeshData.Material> materials = new ArrayList<MeshData.Material>();
    private final List<MaterialInfo> materialInfo = new ArrayList<MaterialInfo>();
    private int defaultMaterial = -1;
    private double[][] world;
    private int[] parents;

    private static final class MaterialInfo {
        int texCoord;
        double[] uvTransform;
    }

    private GltfLoader(File file, ModelLoadOptions options, JsonObject root, ByteBuffer glbBinary) {
        this.directory = file.getAbsoluteFile().getParentFile();
        this.options = options;
        this.root = root;
        this.glbBinary = glbBinary;
    }

    static MeshData load(File file, ModelLoadOptions options) throws IOException {
        byte[] bytes = ModelFiles.read(file, options.maxFileBytes);
        JsonObject root;
        ByteBuffer binary = null;
        if (bytes.length >= 12 && ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(0) == GLB_MAGIC) {
            ByteBuffer glb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            int length = Math.min(bytes.length, glb.getInt(8));
            String json = null;
            int offset = 12;
            while (offset + 8 <= length) {
                int chunkLength = glb.getInt(offset), chunkType = glb.getInt(offset + 4);
                int start = offset + 8;
                if (chunkLength < 0 || start + chunkLength > length) throw new IOException("Truncated GLB chunk");
                if (chunkType == CHUNK_JSON && json == null) {
                    json = new String(bytes, start, chunkLength, StandardCharsets.UTF_8);
                } else if (chunkType == CHUNK_BIN && binary == null) {
                    ByteBuffer slice = ByteBuffer.wrap(bytes, start, chunkLength).slice();
                    binary = slice.order(ByteOrder.LITTLE_ENDIAN);
                }
                offset = start + ((chunkLength + 3) & ~3);
            }
            if (json == null) throw new IOException("GLB file has no JSON chunk");
            root = parseJson(json);
        } else {
            root = parseJson(new String(bytes, StandardCharsets.UTF_8));
        }
        JsonObject asset = object(root, "asset");
        String version = asset == null ? "2.0" : string(asset, "version", "2.0");
        if (!version.startsWith("2")) throw new IOException("Only glTF 2.0 is supported (file is " + version + ")");
        return new GltfLoader(file, options, root, binary).read();
    }

    private static JsonObject parseJson(String json) throws IOException {
        try {
            JsonElement element = new JsonParser().parse(json.charAt(0) == '﻿' ? json.substring(1) : json);
            if (!element.isJsonObject()) throw new IOException("glTF root is not an object");
            return element.getAsJsonObject();
        } catch (RuntimeException error) {
            throw new IOException("Invalid glTF JSON: " + error.getMessage(), error);
        }
    }

    private MeshData read() throws IOException {
        JsonArray nodes = array(root, "nodes");
        int nodeCount = nodes == null ? 0 : nodes.size();
        computeWorldMatrices(nodes, nodeCount);
        parseMaterials();
        List<int[]> instances = meshInstances(nodes, nodeCount);
        checkTriangleBudget(instances);
        List<MeshData.Primitive> primitives = new ArrayList<MeshData.Primitive>();
        Map<BodyPart, float[]> pivots = new EnumMap<BodyPart, float[]>(BodyPart.class);
        JsonArray meshes = array(root, "meshes");
        for (int[] instance : instances) {
            int node = instance[0], meshIndex = instance[1];
            JsonObject mesh = element(meshes, meshIndex);
            JsonArray meshPrimitives = mesh == null ? null : array(mesh, "primitives");
            if (meshPrimitives == null) continue;
            JsonObject nodeObject = element(nodes, node);
            Skin skin = nodeObject != null && nodeObject.has("skin") ? skin(integer(nodeObject, "skin", -1), pivots) : null;
            for (JsonElement raw : meshPrimitives) {
                if (!raw.isJsonObject()) continue;
                MeshData.Primitive primitive = primitive(raw.getAsJsonObject(), world[node], skin);
                if (primitive != null && primitive.indices.length >= 3) primitives.add(primitive);
            }
        }
        if (primitives.isEmpty()) throw new IOException("The file contains no triangle geometry");
        return new MeshData(materials, primitives, pivots, true);
    }

    // ---- scene graph ----

    private void computeWorldMatrices(JsonArray nodes, int count) {
        parents = new int[count];
        Arrays.fill(parents, -1);
        double[][] local = new double[count][];
        for (int index = 0; index < count; index++) {
            JsonObject node = element(nodes, index);
            local[index] = node == null ? Matrix.identity() : localMatrix(node);
            JsonArray children = node == null ? null : array(node, "children");
            if (children == null) continue;
            for (JsonElement child : children) {
                int childIndex = asInt(child, -1);
                if (childIndex >= 0 && childIndex < count && childIndex != index && parents[childIndex] < 0) parents[childIndex] = index;
            }
        }
        world = new double[count][];
        for (int index = 0; index < count; index++) resolveWorld(index, local);
    }

    private double[] resolveWorld(int index, double[][] local) {
        if (world[index] != null) return world[index];
        // Walk to the first resolved ancestor iteratively; a malformed cyclic graph is cut at the repeat.
        Deque<Integer> chain = new ArrayDeque<Integer>();
        Set<Integer> seen = new HashSet<Integer>();
        int current = index;
        while (current >= 0 && world[current] == null && seen.add(current)) {
            chain.push(current);
            current = parents[current];
        }
        double[] base = current >= 0 && world[current] != null ? world[current] : Matrix.identity();
        while (!chain.isEmpty()) {
            int node = chain.pop();
            base = Matrix.multiply(base, local[node]);
            world[node] = base;
        }
        return world[index];
    }

    private static double[] localMatrix(JsonObject node) {
        JsonArray matrix = array(node, "matrix");
        if (matrix != null && matrix.size() == 16) {
            double[] m = new double[16];
            for (int index = 0; index < 16; index++) m[index] = asDouble(matrix.get(index), index % 5 == 0 ? 1 : 0);
            return m;
        }
        double[] t = vector(node, "translation", new double[] {0, 0, 0});
        double[] r = vector(node, "rotation", new double[] {0, 0, 0, 1});
        double[] s = vector(node, "scale", new double[] {1, 1, 1});
        if (r.length < 4) r = new double[] {0, 0, 0, 1};
        return Matrix.fromTrs(t, r, s);
    }

    /** Pairs of (node, mesh) reachable from the default scene, in traversal order. */
    private List<int[]> meshInstances(JsonArray nodes, int count) {
        List<Integer> roots = new ArrayList<Integer>();
        JsonArray scenes = array(root, "scenes");
        JsonObject scene = element(scenes, integer(root, "scene", 0));
        JsonArray sceneNodes = scene == null ? null : array(scene, "nodes");
        if (sceneNodes != null) {
            for (JsonElement node : sceneNodes) {
                int index = asInt(node, -1);
                if (index >= 0 && index < count) roots.add(index);
            }
        } else {
            for (int index = 0; index < count; index++) if (parents[index] < 0) roots.add(index);
        }
        List<int[]> instances = new ArrayList<int[]>();
        Set<Integer> visited = new HashSet<Integer>();
        Deque<Integer> stack = new ArrayDeque<Integer>();
        for (int index = roots.size() - 1; index >= 0; index--) stack.push(roots.get(index));
        while (!stack.isEmpty()) {
            int index = stack.pop();
            if (!visited.add(index)) continue;
            JsonObject node = element(nodes, index);
            if (node == null) continue;
            int mesh = integer(node, "mesh", -1);
            if (mesh >= 0) instances.add(new int[] {index, mesh});
            JsonArray children = array(node, "children");
            if (children == null) continue;
            for (int child = children.size() - 1; child >= 0; child--) {
                int childIndex = asInt(children.get(child), -1);
                if (childIndex >= 0 && childIndex < count) stack.push(childIndex);
            }
        }
        // Files without nodes still describe meshes; show each once at the origin.
        if (count == 0) {
            JsonArray meshes = array(root, "meshes");
            world = new double[][] {Matrix.identity()};
            for (int index = 0; meshes != null && index < meshes.size(); index++) instances.add(new int[] {0, index});
        }
        return instances;
    }

    private void checkTriangleBudget(List<int[]> instances) throws IOException {
        JsonArray meshes = array(root, "meshes");
        JsonArray accessors = array(root, "accessors");
        long triangles = 0L;
        for (int[] instance : instances) {
            JsonObject mesh = element(meshes, instance[1]);
            JsonArray primitives = mesh == null ? null : array(mesh, "primitives");
            if (primitives == null) continue;
            for (JsonElement raw : primitives) {
                if (!raw.isJsonObject()) continue;
                JsonObject primitive = raw.getAsJsonObject();
                int accessor = primitive.has("indices") ? integer(primitive, "indices", -1)
                        : integer(object(primitive, "attributes"), "POSITION", -1);
                JsonObject data = element(accessors, accessor);
                if (data != null) triangles += Math.max(0, integer(data, "count", 0)) / 3;
            }
        }
        if (triangles > options.maxSourceTriangles) {
            throw new IOException("Model has " + triangles + " triangles; the limit is " + options.maxSourceTriangles);
        }
    }

    // ---- skins ----

    private static final class Skin {
        double[][] jointMatrices;
        byte[] jointParts;
        boolean humanoid;
    }

    private Skin skin(int index, Map<BodyPart, float[]> pivots) throws IOException {
        JsonObject skinObject = element(array(root, "skins"), index);
        JsonArray joints = skinObject == null ? null : array(skinObject, "joints");
        if (joints == null || joints.size() == 0 || world.length == 0) return null;
        int count = joints.size();
        float[] inverseBind = skinObject.has("inverseBindMatrices")
                ? floats(integer(skinObject, "inverseBindMatrices", -1), 16) : null;
        JsonArray nodes = array(root, "nodes");
        Skin skin = new Skin();
        skin.jointMatrices = new double[count][];
        skin.jointParts = new byte[count];
        Map<Integer, BodyPart> nodeParts = new HashMap<Integer, BodyPart>();
        Set<BodyPart> found = EnumSet.noneOf(BodyPart.class);
        int[] jointNodes = new int[count];
        for (int joint = 0; joint < count; joint++) {
            int node = asInt(joints.get(joint), -1);
            jointNodes[joint] = node;
            double[] jointWorld = node >= 0 && node < world.length ? world[node] : Matrix.identity();
            double[] ibm = Matrix.identity();
            if (inverseBind != null && inverseBind.length >= (joint + 1) * 16) {
                for (int k = 0; k < 16; k++) ibm[k] = inverseBind[joint * 16 + k];
            }
            skin.jointMatrices[joint] = Matrix.multiply(jointWorld, ibm);
            BodyPart part = partOfNode(node, nodes, nodeParts);
            skin.jointParts[joint] = (byte) part.ordinal();
            found.add(part);
        }
        boolean limbs = found.contains(BodyPart.LEFT_ARM) && found.contains(BodyPart.RIGHT_ARM)
                && found.contains(BodyPart.LEFT_LEG) && found.contains(BodyPart.RIGHT_LEG);
        int headNode = -1;
        if (limbs && !found.contains(BodyPart.HEAD)) {
            // Rigs such as Cesium's end in neck joints; the highest torso joint then carries the head.
            double highest = -Double.MAX_VALUE;
            for (int joint = 0; joint < count; joint++) {
                int node = jointNodes[joint];
                if (skin.jointParts[joint] != BodyPart.BODY.ordinal() || node < 0 || node >= world.length) continue;
                if (world[node][13] > highest) {
                    highest = world[node][13];
                    headNode = node;
                }
            }
            if (headNode >= 0) {
                for (int joint = 0; joint < count; joint++) {
                    if (skin.jointParts[joint] == BodyPart.BODY.ordinal() && descends(jointNodes[joint], headNode)) {
                        skin.jointParts[joint] = (byte) BodyPart.HEAD.ordinal();
                    }
                }
                found.add(BodyPart.HEAD);
            }
        }
        skin.humanoid = limbs && found.contains(BodyPart.HEAD);
        if (skin.humanoid && pivots.isEmpty()) {
            // The pivot of each limb is its top-most joint, e.g. the upper arm rather than the hand.
            for (int joint = 0; joint < count; joint++) {
                int node = jointNodes[joint];
                if (node < 0 || node >= world.length) continue;
                BodyPart part = BodyPart.values()[skin.jointParts[joint]];
                if (part == BodyPart.BODY || pivots.containsKey(part)) continue;
                int parent = parents[node];
                BodyPart parentPart = parent < 0 ? null
                        : descends(parent, headNode) ? BodyPart.HEAD : partOfNode(parent, nodes, nodeParts);
                if (parentPart == part) continue;
                // A promoted neck joint turns around its parent, the base of the neck.
                int pivotNode = node == headNode && parent >= 0 ? parent : node;
                pivots.put(part, new float[] {(float) world[pivotNode][12], (float) world[pivotNode][13], (float) world[pivotNode][14]});
            }
        }
        return skin;
    }

    /** True when {@code node} is {@code ancestor} or lies below it. */
    private boolean descends(int node, int ancestor) {
        if (ancestor < 0) return false;
        Set<Integer> seen = new HashSet<Integer>();
        for (int current = node; current >= 0 && seen.add(current); current = parents[current]) {
            if (current == ancestor) return true;
        }
        return false;
    }

    private BodyPart partOfNode(int node, JsonArray nodes, Map<Integer, BodyPart> cache) {
        if (node < 0 || node >= parents.length) return BodyPart.BODY;
        BodyPart cached = cache.get(node);
        if (cached != null) return cached;
        List<Integer> chain = new ArrayList<Integer>();
        BodyPart result = null;
        int current = node;
        Set<Integer> seen = new HashSet<Integer>();
        while (current >= 0 && seen.add(current)) {
            BodyPart known = cache.get(current);
            if (known != null) {
                result = known;
                break;
            }
            chain.add(current);
            JsonObject object = element(nodes, current);
            BodyPart own = object == null ? null : BodyPart.classifyJoint(string(object, "name", ""));
            if (own != null) {
                result = own;
                break;
            }
            current = parents[current];
        }
        if (result == null) result = BodyPart.BODY;
        // Every unnamed descendant inherits the classified ancestor, e.g. "ball_l" below "foot_l".
        for (Integer index : chain) {
            JsonObject object = element(nodes, index);
            BodyPart own = object == null ? null : BodyPart.classifyJoint(string(object, "name", ""));
            cache.put(index, own != null ? own : result);
        }
        return cache.containsKey(node) ? cache.get(node) : result;
    }

    // ---- primitives ----

    private MeshData.Primitive primitive(JsonObject primitive, double[] nodeWorld, Skin skin) throws IOException {
        int mode = integer(primitive, "mode", 4);
        if (mode != 4 && mode != 5 && mode != 6) return null;
        JsonObject attributes = object(primitive, "attributes");
        if (attributes == null || !attributes.has("POSITION")) return null;
        float[] positions = floats(integer(attributes, "POSITION", -1), 3);
        int vertexCount = positions.length / 3;
        float[] normals = attributes.has("NORMAL") ? floats(integer(attributes, "NORMAL", -1), 3) : null;
        if (normals != null && normals.length != positions.length) normals = null;
        int material = materialIndex(integer(primitive, "material", -1));
        MaterialInfo info = material < materialInfo.size() ? materialInfo.get(material) : null;
        String uvName = "TEXCOORD_" + (info == null ? 0 : info.texCoord);
        float[] uvs = attributes.has(uvName) ? floats(integer(attributes, uvName, -1), 2) : null;
        if (uvs != null && uvs.length != vertexCount * 2) uvs = null;
        if (uvs != null && info != null && info.uvTransform != null) applyUvTransform(uvs, info.uvTransform);
        float[] colors = attributes.has("COLOR_0") ? colors(integer(attributes, "COLOR_0", -1), vertexCount) : null;
        int[] indices = primitive.has("indices") ? ints(integer(primitive, "indices", -1)) : sequence(vertexCount);
        for (int index : indices) {
            if (index < 0 || index >= vertexCount) throw new IOException("Vertex index out of range");
        }
        indices = triangles(indices, mode);
        byte[] parts = null;
        if (skin != null && attributes.has("JOINTS_0") && attributes.has("WEIGHTS_0")) {
            float[] joints = floats(integer(attributes, "JOINTS_0", -1), 4);
            float[] weights = floats(integer(attributes, "WEIGHTS_0", -1), 4);
            if (joints.length == vertexCount * 4 && weights.length == vertexCount * 4) {
                parts = applySkin(positions, normals, joints, weights, skin, nodeWorld);
                if (!skin.humanoid) parts = null;
            } else {
                transform(positions, normals, nodeWorld);
            }
        } else {
            transform(positions, normals, nodeWorld);
        }
        return new MeshData.Primitive(material, positions, normals, uvs, colors, indices, parts);
    }

    private static void transform(float[] positions, float[] normals, double[] matrix) {
        double[] normalMatrix = Matrix.normalMatrix(matrix);
        for (int index = 0; index < positions.length; index += 3) {
            double x = positions[index], y = positions[index + 1], z = positions[index + 2];
            positions[index] = (float) (matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12]);
            positions[index + 1] = (float) (matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13]);
            positions[index + 2] = (float) (matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14]);
            if (normals != null) {
                double nx = normals[index], ny = normals[index + 1], nz = normals[index + 2];
                normals[index] = (float) (normalMatrix[0] * nx + normalMatrix[3] * ny + normalMatrix[6] * nz);
                normals[index + 1] = (float) (normalMatrix[1] * nx + normalMatrix[4] * ny + normalMatrix[7] * nz);
                normals[index + 2] = (float) (normalMatrix[2] * nx + normalMatrix[5] * ny + normalMatrix[8] * nz);
            }
        }
    }

    /** Poses vertices with their rest-pose joint matrices and returns the dominant joint's body part per vertex. */
    private static byte[] applySkin(float[] positions, float[] normals, float[] joints, float[] weights, Skin skin,
            double[] fallback) {
        int vertexCount = positions.length / 3;
        byte[] parts = new byte[vertexCount];
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            double px = positions[vertex * 3], py = positions[vertex * 3 + 1], pz = positions[vertex * 3 + 2];
            double nx = normals == null ? 0 : normals[vertex * 3], ny = normals == null ? 0 : normals[vertex * 3 + 1];
            double nz = normals == null ? 0 : normals[vertex * 3 + 2];
            double ox = 0, oy = 0, oz = 0, onx = 0, ony = 0, onz = 0, total = 0, best = -1;
            int bestJoint = -1;
            for (int k = 0; k < 4; k++) {
                double weight = weights[vertex * 4 + k];
                int joint = (int) joints[vertex * 4 + k];
                if (weight <= 0 || joint < 0 || joint >= skin.jointMatrices.length) continue;
                double[] m = skin.jointMatrices[joint];
                ox += weight * (m[0] * px + m[4] * py + m[8] * pz + m[12]);
                oy += weight * (m[1] * px + m[5] * py + m[9] * pz + m[13]);
                oz += weight * (m[2] * px + m[6] * py + m[10] * pz + m[14]);
                onx += weight * (m[0] * nx + m[4] * ny + m[8] * nz);
                ony += weight * (m[1] * nx + m[5] * ny + m[9] * nz);
                onz += weight * (m[2] * nx + m[6] * ny + m[10] * nz);
                total += weight;
                if (weight > best) {
                    best = weight;
                    bestJoint = joint;
                }
            }
            if (total <= 1.0E-6D) {
                double[] m = fallback;
                ox = m[0] * px + m[4] * py + m[8] * pz + m[12];
                oy = m[1] * px + m[5] * py + m[9] * pz + m[13];
                oz = m[2] * px + m[6] * py + m[10] * pz + m[14];
                onx = m[0] * nx + m[4] * ny + m[8] * nz;
                ony = m[1] * nx + m[5] * ny + m[9] * nz;
                onz = m[2] * nx + m[6] * ny + m[10] * nz;
                total = 1.0D;
            }
            positions[vertex * 3] = (float) (ox / total);
            positions[vertex * 3 + 1] = (float) (oy / total);
            positions[vertex * 3 + 2] = (float) (oz / total);
            if (normals != null) {
                double length = Math.sqrt(onx * onx + ony * ony + onz * onz);
                if (length > 1.0E-12D) {
                    normals[vertex * 3] = (float) (onx / length);
                    normals[vertex * 3 + 1] = (float) (ony / length);
                    normals[vertex * 3 + 2] = (float) (onz / length);
                }
            }
            parts[vertex] = bestJoint < 0 ? (byte) BodyPart.BODY.ordinal() : skin.jointParts[bestJoint];
        }
        return parts;
    }

    private static int[] sequence(int count) {
        int[] indices = new int[count];
        for (int index = 0; index < count; index++) indices[index] = index;
        return indices;
    }

    /** Converts strips (5) and fans (6) to a plain triangle list. */
    static int[] triangles(int[] indices, int mode) {
        if (mode == 4) return indices.length % 3 == 0 ? indices : Arrays.copyOf(indices, indices.length - indices.length % 3);
        if (indices.length < 3) return new int[0];
        int[] result = new int[(indices.length - 2) * 3];
        for (int index = 0; index + 2 < indices.length; index++) {
            int a, b, c;
            if (mode == 6) {
                a = indices[0];
                b = indices[index + 1];
                c = indices[index + 2];
            } else if ((index & 1) == 0) {
                a = indices[index];
                b = indices[index + 1];
                c = indices[index + 2];
            } else {
                a = indices[index + 1];
                b = indices[index];
                c = indices[index + 2];
            }
            result[index * 3] = a;
            result[index * 3 + 1] = b;
            result[index * 3 + 2] = c;
        }
        return result;
    }

    private static void applyUvTransform(float[] uvs, double[] transform) {
        double offsetU = transform[0], offsetV = transform[1], rotation = transform[2], scaleU = transform[3], scaleV = transform[4];
        double cos = Math.cos(rotation), sin = Math.sin(rotation);
        for (int index = 0; index + 1 < uvs.length; index += 2) {
            double u = uvs[index] * scaleU, v = uvs[index + 1] * scaleV;
            uvs[index] = (float) (cos * u + sin * v + offsetU);
            uvs[index + 1] = (float) (-sin * u + cos * v + offsetV);
        }
    }

    private float[] colors(int accessor, int vertexCount) throws IOException {
        JsonObject data = element(array(root, "accessors"), accessor);
        int components = data != null && "VEC3".equals(string(data, "type", "VEC4")) ? 3 : 4;
        float[] raw = floats(accessor, components);
        if (raw.length != vertexCount * components) return null;
        if (components == 4) return raw;
        float[] rgba = new float[vertexCount * 4];
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            rgba[vertex * 4] = raw[vertex * 3];
            rgba[vertex * 4 + 1] = raw[vertex * 3 + 1];
            rgba[vertex * 4 + 2] = raw[vertex * 3 + 2];
            rgba[vertex * 4 + 3] = 1.0F;
        }
        return rgba;
    }

    // ---- materials and textures ----

    private void parseMaterials() {
        JsonArray list = array(root, "materials");
        for (int index = 0; list != null && index < list.size(); index++) {
            JsonObject material = element(list, index);
            if (material == null) material = new JsonObject();
            float[] color = {1, 1, 1, 1};
            JsonObject texture = null;
            JsonObject pbr = object(material, "pbrMetallicRoughness");
            if (pbr != null) {
                color = rgba(pbr, "baseColorFactor", color);
                texture = object(pbr, "baseColorTexture");
            }
            JsonObject extensions = object(material, "extensions");
            JsonObject specular = extensions == null ? null : object(extensions, "KHR_materials_pbrSpecularGlossiness");
            if (specular != null && texture == null) {
                color = rgba(specular, "diffuseFactor", color);
                texture = object(specular, "diffuseTexture");
            }
            // Some converted scans only carry their colour in the emissive slot.
            if (texture == null && object(material, "emissiveTexture") != null) texture = object(material, "emissiveTexture");
            MaterialInfo info = new MaterialInfo();
            BufferedImage image = null;
            boolean repeat = true;
            if (texture != null) {
                info.texCoord = integer(texture, "texCoord", 0);
                JsonObject textureExtensions = object(texture, "extensions");
                JsonObject transform = textureExtensions == null ? null : object(textureExtensions, "KHR_texture_transform");
                if (transform != null) {
                    double[] offset = vector(transform, "offset", new double[] {0, 0});
                    double[] scale = vector(transform, "scale", new double[] {1, 1});
                    info.uvTransform = new double[] {offset.length > 0 ? offset[0] : 0, offset.length > 1 ? offset[1] : 0,
                            number(transform, "rotation", 0), scale.length > 0 ? scale[0] : 1, scale.length > 1 ? scale[1] : 1};
                    if (transform.has("texCoord")) info.texCoord = integer(transform, "texCoord", info.texCoord);
                }
                int textureIndex = integer(texture, "index", -1);
                image = textureImage(textureIndex);
                repeat = repeats(textureIndex);
            }
            String alphaMode = string(material, "alphaMode", "OPAQUE");
            int mode = "BLEND".equals(alphaMode) ? MeshData.Material.BLEND
                    : "MASK".equals(alphaMode) ? MeshData.Material.MASK : MeshData.Material.OPAQUE;
            float cutoff = (float) number(material, "alphaCutoff", 0.5D);
            materials.add(new MeshData.Material(string(material, "name", "material" + index), color, image, mode, cutoff, repeat));
            materialInfo.add(info);
        }
    }

    private int materialIndex(int index) {
        if (index >= 0 && index < materials.size()) return index;
        if (defaultMaterial < 0) {
            defaultMaterial = materials.size();
            materials.add(new MeshData.Material("default", null, null, MeshData.Material.OPAQUE, 0.5F, true));
            materialInfo.add(new MaterialInfo());
        }
        return defaultMaterial;
    }

    private boolean repeats(int textureIndex) {
        JsonObject texture = element(array(root, "textures"), textureIndex);
        JsonObject sampler = texture == null ? null : element(array(root, "samplers"), integer(texture, "sampler", -1));
        return sampler == null || integer(sampler, "wrapS", 10497) != 33071;
    }

    private BufferedImage textureImage(int textureIndex) {
        JsonObject texture = element(array(root, "textures"), textureIndex);
        if (texture == null) return null;
        int source = integer(texture, "source", -1);
        JsonObject extensions = object(texture, "extensions");
        JsonObject webp = extensions == null ? null : object(extensions, "EXT_texture_webp");
        if (webp != null && webp.has("source")) source = integer(webp, "source", source);
        if (source < 0) return null;
        if (images.containsKey(source)) return images.get(source);
        BufferedImage image = null;
        try {
            image = ModelImages.decode(imageBytes(source), options.maxTextureSize);
        } catch (Exception ignored) {
            // A broken texture leaves the material with its base colour instead of failing the model.
        }
        images.put(source, image);
        return image;
    }

    private byte[] imageBytes(int index) throws IOException {
        JsonObject image = element(array(root, "images"), index);
        if (image == null) return null;
        if (image.has("bufferView")) {
            ByteBuffer view = bufferView(integer(image, "bufferView", -1));
            byte[] bytes = new byte[view.remaining()];
            view.get(bytes);
            return bytes;
        }
        String uri = string(image, "uri", null);
        return uri == null ? null : readUri(uri);
    }

    // ---- buffers and accessors ----

    private byte[] readUri(String uri) throws IOException {
        if (uri.startsWith("data:")) {
            int comma = uri.indexOf(',');
            if (comma < 0 || !uri.substring(0, comma).endsWith(";base64")) throw new IOException("Unsupported data URI");
            return Base64.getDecoder().decode(uri.substring(comma + 1).trim());
        }
        String path = percentDecode(uri);
        if (path.contains("://")) throw new IOException("Remote glTF resources are not loaded: " + uri);
        File file = ModelFiles.resolveRelative(directory, path);
        if (file == null) throw new IOException("Missing glTF resource " + path);
        return ModelFiles.read(file, options.maxFileBytes);
    }

    static String percentDecode(String value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int index = 0;
        while (index < value.length()) {
            int c = value.codePointAt(index);
            if (c == '%' && index + 2 < value.length()) {
                int hi = Character.digit(value.charAt(index + 1), 16), lo = Character.digit(value.charAt(index + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    bytes.write(hi * 16 + lo);
                    index += 3;
                    continue;
                }
            }
            byte[] encoded = new String(Character.toChars(c)).getBytes(StandardCharsets.UTF_8);
            bytes.write(encoded, 0, encoded.length);
            index += Character.charCount(c);
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    private ByteBuffer buffer(int index) throws IOException {
        ByteBuffer cached = buffers.get(index);
        if (cached != null) return cached;
        JsonObject buffer = element(array(root, "buffers"), index);
        if (buffer == null) throw new IOException("Missing buffer " + index);
        String uri = string(buffer, "uri", null);
        ByteBuffer data;
        if (uri == null) {
            if (glbBinary == null) throw new IOException("Buffer " + index + " has no data");
            data = glbBinary;
        } else {
            data = ByteBuffer.wrap(readUri(uri)).order(ByteOrder.LITTLE_ENDIAN);
        }
        buffers.put(index, data);
        return data;
    }

    /** A little-endian slice covering exactly one buffer view. */
    private ByteBuffer bufferView(int index) throws IOException {
        JsonObject view = element(array(root, "bufferViews"), index);
        if (view == null) throw new IOException("Missing buffer view " + index);
        ByteBuffer buffer = buffer(integer(view, "buffer", 0));
        int offset = integer(view, "byteOffset", 0), length = integer(view, "byteLength", 0);
        if (offset < 0 || length < 0 || (long) offset + length > buffer.capacity()) throw new IOException("Buffer view out of range");
        ByteBuffer duplicate = buffer.duplicate();
        duplicate.position(offset);
        duplicate.limit(offset + length);
        return duplicate.slice().order(ByteOrder.LITTLE_ENDIAN);
    }

    private float[] floats(int accessorIndex, int expectedComponents) throws IOException {
        JsonObject accessor = element(array(root, "accessors"), accessorIndex);
        if (accessor == null) throw new IOException("Missing accessor " + accessorIndex);
        int components = components(string(accessor, "type", "SCALAR"));
        int count = integer(accessor, "count", 0);
        if (count < 0 || (long) count * components > Integer.MAX_VALUE / 2) throw new IOException("Accessor too large");
        int componentType = integer(accessor, "componentType", 5126);
        boolean normalized = accessor.has("normalized") && accessor.get("normalized").getAsBoolean();
        float[] values = new float[count * components];
        if (accessor.has("bufferView")) {
            JsonObject view = element(array(root, "bufferViews"), integer(accessor, "bufferView", -1));
            ByteBuffer data = bufferView(integer(accessor, "bufferView", -1));
            int size = componentSize(componentType);
            int elementSize = size * components;
            int stride = view != null && integer(view, "byteStride", 0) > 0 ? integer(view, "byteStride", 0) : elementSize;
            int start = integer(accessor, "byteOffset", 0);
            if (count > 0 && (start < 0 || (long) start + (long) stride * (count - 1) + elementSize > data.capacity())) {
                throw new IOException("Accessor " + accessorIndex + " exceeds its buffer view");
            }
            for (int element = 0; element < count; element++) {
                int base = start + element * stride;
                for (int component = 0; component < components; component++) {
                    values[element * components + component] = component(data, base + component * size, componentType, normalized);
                }
            }
        }
        JsonObject sparse = object(accessor, "sparse");
        if (sparse != null) applySparse(sparse, values, components, componentType, normalized);
        if (expectedComponents > 0 && components != expectedComponents) {
            return reshape(values, count, components, expectedComponents);
        }
        return values;
    }

    private void applySparse(JsonObject sparse, float[] values, int components, int componentType, boolean normalized)
            throws IOException {
        int count = integer(sparse, "count", 0);
        JsonObject indices = object(sparse, "indices"), replacement = object(sparse, "values");
        if (count <= 0 || indices == null || replacement == null) return;
        ByteBuffer indexData = bufferView(integer(indices, "bufferView", -1));
        ByteBuffer valueData = bufferView(integer(replacement, "bufferView", -1));
        int indexType = integer(indices, "componentType", 5125), indexSize = componentSize(indexType);
        int indexOffset = integer(indices, "byteOffset", 0), valueOffset = integer(replacement, "byteOffset", 0);
        int size = componentSize(componentType);
        for (int entry = 0; entry < count; entry++) {
            int position = indexOffset + entry * indexSize;
            if (position + indexSize > indexData.capacity()) break;
            int target = (int) component(indexData, position, indexType, false);
            if (target < 0 || (target + 1) * components > values.length) continue;
            for (int component = 0; component < components; component++) {
                int at = valueOffset + (entry * components + component) * size;
                if (at + size > valueData.capacity()) return;
                values[target * components + component] = component(valueData, at, componentType, normalized);
            }
        }
    }

    private static float[] reshape(float[] values, int count, int from, int to) {
        float[] result = new float[count * to];
        for (int element = 0; element < count; element++) {
            for (int component = 0; component < to; component++) {
                result[element * to + component] = component < from ? values[element * from + component] : (component == 3 ? 1.0F : 0.0F);
            }
        }
        return result;
    }

    private int[] ints(int accessorIndex) throws IOException {
        float[] values = floats(accessorIndex, 0);
        int[] result = new int[values.length];
        for (int index = 0; index < values.length; index++) result[index] = (int) values[index];
        JsonObject accessor = element(array(root, "accessors"), accessorIndex);
        // 32-bit indices above 2^24 lose precision as floats; re-read those exactly.
        if (accessor != null && integer(accessor, "componentType", 0) == 5125 && accessor.has("bufferView") && values.length > 0) {
            JsonObject view = element(array(root, "bufferViews"), integer(accessor, "bufferView", -1));
            ByteBuffer data = bufferView(integer(accessor, "bufferView", -1));
            int stride = view != null && integer(view, "byteStride", 0) > 0 ? integer(view, "byteStride", 0) : 4;
            int start = integer(accessor, "byteOffset", 0);
            for (int index = 0; index < result.length; index++) result[index] = data.getInt(start + index * stride);
        }
        return result;
    }

    private static float component(ByteBuffer data, int position, int type, boolean normalized) {
        switch (type) {
            case 5120: {
                byte value = data.get(position);
                return normalized ? Math.max(value / 127.0F, -1.0F) : value;
            }
            case 5121: {
                int value = data.get(position) & 0xFF;
                return normalized ? value / 255.0F : value;
            }
            case 5122: {
                short value = data.getShort(position);
                return normalized ? Math.max(value / 32767.0F, -1.0F) : value;
            }
            case 5123: {
                int value = data.getShort(position) & 0xFFFF;
                return normalized ? value / 65535.0F : value;
            }
            case 5125:
                return (float) (data.getInt(position) & 0xFFFFFFFFL);
            default:
                return data.getFloat(position);
        }
    }

    private static int componentSize(int type) {
        switch (type) {
            case 5120:
            case 5121:
                return 1;
            case 5122:
            case 5123:
                return 2;
            default:
                return 4;
        }
    }

    private static int components(String type) {
        if ("VEC2".equals(type)) return 2;
        if ("VEC3".equals(type)) return 3;
        if ("VEC4".equals(type) || "MAT2".equals(type)) return 4;
        if ("MAT3".equals(type)) return 9;
        if ("MAT4".equals(type)) return 16;
        return 1;
    }

    // ---- JSON helpers ----

    static JsonObject object(JsonObject parent, String key) {
        if (parent == null) return null;
        JsonElement value = parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    static JsonArray array(JsonObject parent, String key) {
        if (parent == null) return null;
        JsonElement value = parent.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    static JsonObject element(JsonArray array, int index) {
        if (array == null || index < 0 || index >= array.size()) return null;
        JsonElement value = array.get(index);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    static int integer(JsonObject parent, String key, int fallback) {
        if (parent == null) return fallback;
        return asInt(parent.get(key), fallback);
    }

    static double number(JsonObject parent, String key, double fallback) {
        if (parent == null) return fallback;
        return asDouble(parent.get(key), fallback);
    }

    static String string(JsonObject parent, String key, String fallback) {
        JsonElement value = parent == null ? null : parent.get(key);
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    static int asInt(JsonElement value, int fallback) {
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    static double asDouble(JsonElement value, double fallback) {
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsDouble() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    static double[] vector(JsonObject parent, String key, double[] fallback) {
        JsonArray array = array(parent, key);
        if (array == null) return fallback;
        double[] result = new double[array.size()];
        for (int index = 0; index < result.length; index++) {
            result[index] = asDouble(array.get(index), index < fallback.length ? fallback[index] : 0);
        }
        return result.length >= fallback.length ? result : fallback;
    }

    private static float[] rgba(JsonObject parent, String key, float[] fallback) {
        double[] values = vector(parent, key, new double[] {fallback[0], fallback[1], fallback[2], fallback[3]});
        return new float[] {(float) values[0], (float) values[1], (float) values[2], values.length > 3 ? (float) values[3] : 1.0F};
    }
}
