package dev.vibe.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Synthetic models built from boxes, so loader and fitter tests need no third-party assets. */
public final class TestMeshes {

    private TestMeshes() {
    }

    /** Collects boxes into one indexed primitive; an optional part per box simulates skin weights. */
    public static final class Builder {
        private final List<Float> positions = new ArrayList<Float>();
        private final List<Float> normals = new ArrayList<Float>();
        private final List<Float> uvs = new ArrayList<Float>();
        private final List<Integer> indices = new ArrayList<Integer>();
        private final List<Byte> parts = new ArrayList<Byte>();
        private final List<Integer> joints = new ArrayList<Integer>();

        public Builder box(float x0, float y0, float z0, float x1, float y1, float z1) {
            return box(x0, y0, z0, x1, y1, z1, null, 0);
        }

        public Builder box(float x0, float y0, float z0, float x1, float y1, float z1, BodyPart part, int joint) {
            float[][] corners = {
                    {x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0},
                    {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
            int[][] faces = {{0, 3, 2, 1}, {4, 5, 6, 7}, {0, 1, 5, 4}, {3, 7, 6, 2}, {0, 4, 7, 3}, {1, 2, 6, 5}};
            float[][] faceNormals = {{0, 0, -1}, {0, 0, 1}, {0, -1, 0}, {0, 1, 0}, {-1, 0, 0}, {1, 0, 0}};
            for (int face = 0; face < 6; face++) {
                int base = positions.size() / 3;
                for (int corner = 0; corner < 4; corner++) {
                    float[] point = corners[faces[face][corner]];
                    positions.add(point[0]);
                    positions.add(point[1]);
                    positions.add(point[2]);
                    normals.add(faceNormals[face][0]);
                    normals.add(faceNormals[face][1]);
                    normals.add(faceNormals[face][2]);
                    uvs.add(corner == 1 || corner == 2 ? 1.0F : 0.0F);
                    uvs.add(corner >= 2 ? 1.0F : 0.0F);
                    parts.add(part == null ? (byte) -1 : (byte) part.ordinal());
                    joints.add(joint);
                }
                indices.addAll(Arrays.asList(base, base + 1, base + 2, base, base + 2, base + 3));
            }
            return this;
        }

        /** Splits every box face into a grid, to make dense meshes for simplifier tests. */
        public Builder grid(int size) {
            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    int base = positions.size() / 3;
                    float[][] quad = {{x, 0, z}, {x + 1, 0, z}, {x + 1, 0, z + 1}, {x, 0, z + 1}};
                    for (float[] point : quad) {
                        positions.add(point[0] / size);
                        positions.add((float) Math.sin(point[0] * 0.7F) * 0.05F + (float) Math.cos(point[2] * 0.5F) * 0.05F);
                        positions.add(point[2] / size);
                        normals.add(0.0F);
                        normals.add(1.0F);
                        normals.add(0.0F);
                        uvs.add(point[0] / size);
                        uvs.add(point[2] / size);
                        parts.add((byte) -1);
                        joints.add(0);
                    }
                    indices.addAll(Arrays.asList(base, base + 2, base + 1, base, base + 3, base + 2));
                }
            }
            return this;
        }

        float[] positions() {
            return floats(positions);
        }

        public MeshData build(boolean withParts) {
            byte[] partArray = null;
            if (withParts) {
                partArray = new byte[parts.size()];
                for (int index = 0; index < partArray.length; index++) partArray[index] = parts.get(index);
            }
            int[] indexArray = new int[indices.size()];
            for (int index = 0; index < indexArray.length; index++) indexArray[index] = indices.get(index);
            MeshData.Primitive primitive = new MeshData.Primitive(0, floats(positions), floats(normals), floats(uvs), null,
                    indexArray, partArray);
            MeshData.Material material = new MeshData.Material("test", new float[] {0.8F, 0.8F, 0.8F, 1.0F}, null,
                    MeshData.Material.OPAQUE, 0.5F, true);
            return new MeshData(Collections.singletonList(material), Collections.singletonList(primitive), null, true);
        }

        /** Writes a binary glTF with one node rotated by {@code rootRotation} (x, y, z, w quaternion). */
        public void writeGlb(File file, double[] rootRotation, String[] jointNames) throws IOException {
            boolean skinned = jointNames != null;
            ByteArrayOutputStream binary = new ByteArrayOutputStream();
            int vertices = positions.size() / 3;
            int positionOffset = append(binary, floatBytes(floats(positions)));
            int normalOffset = append(binary, floatBytes(floats(normals)));
            int uvOffset = append(binary, floatBytes(floats(uvs)));
            int[] indexArray = new int[indices.size()];
            for (int index = 0; index < indexArray.length; index++) indexArray[index] = indices.get(index);
            ByteBuffer indexBytes = ByteBuffer.allocate(indexArray.length * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (int value : indexArray) indexBytes.putInt(value);
            int indexOffset = append(binary, indexBytes.array());
            int jointOffset = 0, weightOffset = 0, inverseOffset = 0;
            if (skinned) {
                byte[] jointBytes = new byte[vertices * 4];
                float[] weights = new float[vertices * 4];
                for (int vertex = 0; vertex < vertices; vertex++) {
                    jointBytes[vertex * 4] = (byte) (int) joints.get(vertex);
                    weights[vertex * 4] = 1.0F;
                }
                jointOffset = append(binary, jointBytes);
                weightOffset = append(binary, floatBytes(weights));
                float[] inverse = new float[jointNames.length * 16];
                for (int joint = 0; joint < jointNames.length; joint++) {
                    inverse[joint * 16] = inverse[joint * 16 + 5] = inverse[joint * 16 + 10] = inverse[joint * 16 + 15] = 1.0F;
                }
                inverseOffset = append(binary, floatBytes(inverse));
            }
            JsonObject root = new JsonObject();
            JsonObject asset = new JsonObject();
            asset.addProperty("version", "2.0");
            root.add("asset", asset);
            JsonArray views = new JsonArray(), accessors = new JsonArray();
            view(views, positionOffset, vertices * 12);
            view(views, normalOffset, vertices * 12);
            view(views, uvOffset, vertices * 8);
            view(views, indexOffset, indexArray.length * 4);
            accessor(accessors, 0, 5126, vertices, "VEC3");
            accessor(accessors, 1, 5126, vertices, "VEC3");
            accessor(accessors, 2, 5126, vertices, "VEC2");
            accessor(accessors, 3, 5125, indexArray.length, "SCALAR");
            if (skinned) {
                view(views, jointOffset, vertices * 4);
                view(views, weightOffset, vertices * 16);
                view(views, inverseOffset, jointNames.length * 64);
                accessor(accessors, 4, 5121, vertices, "VEC4");
                accessor(accessors, 5, 5126, vertices, "VEC4");
                accessor(accessors, 6, 5126, jointNames.length, "MAT4");
            }
            root.add("bufferViews", views);
            root.add("accessors", accessors);
            JsonObject attributes = new JsonObject();
            attributes.addProperty("POSITION", 0);
            attributes.addProperty("NORMAL", 1);
            attributes.addProperty("TEXCOORD_0", 2);
            if (skinned) {
                attributes.addProperty("JOINTS_0", 4);
                attributes.addProperty("WEIGHTS_0", 5);
            }
            JsonObject primitive = new JsonObject();
            primitive.add("attributes", attributes);
            primitive.addProperty("indices", 3);
            JsonArray primitives = new JsonArray();
            primitives.add(primitive);
            JsonObject mesh = new JsonObject();
            mesh.add("primitives", primitives);
            JsonArray meshes = new JsonArray();
            meshes.add(mesh);
            root.add("meshes", meshes);
            JsonArray nodes = new JsonArray();
            JsonObject meshNode = new JsonObject();
            meshNode.addProperty("mesh", 0);
            JsonArray rotation = new JsonArray();
            for (double value : rootRotation) rotation.add(new com.google.gson.JsonPrimitive(value));
            JsonObject sceneRoot = new JsonObject();
            sceneRoot.add("rotation", rotation);
            JsonArray children = new JsonArray();
            children.add(new com.google.gson.JsonPrimitive(1));
            sceneRoot.add("children", children);
            nodes.add(sceneRoot);
            nodes.add(meshNode);
            if (skinned) {
                meshNode.addProperty("skin", 0);
                JsonArray jointList = new JsonArray();
                // Joints form a chain under the scene root; each sits at the origin so the bind pose is the identity.
                for (int joint = 0; joint < jointNames.length; joint++) {
                    JsonObject node = new JsonObject();
                    node.addProperty("name", jointNames[joint]);
                    nodes.add(node);
                    jointList.add(new com.google.gson.JsonPrimitive(2 + joint));
                    children.add(new com.google.gson.JsonPrimitive(2 + joint));
                }
                JsonObject skin = new JsonObject();
                skin.add("joints", jointList);
                skin.addProperty("inverseBindMatrices", 6);
                JsonArray skins = new JsonArray();
                skins.add(skin);
                root.add("skins", skins);
            }
            root.add("nodes", nodes);
            JsonObject scene = new JsonObject();
            JsonArray sceneNodes = new JsonArray();
            sceneNodes.add(new com.google.gson.JsonPrimitive(0));
            scene.add("nodes", sceneNodes);
            JsonArray scenes = new JsonArray();
            scenes.add(scene);
            root.add("scenes", scenes);
            JsonObject buffer = new JsonObject();
            buffer.addProperty("byteLength", binary.size());
            JsonArray buffers = new JsonArray();
            buffers.add(buffer);
            root.add("buffers", buffers);
            writeGlbFile(file, root.toString().getBytes(StandardCharsets.UTF_8), binary.toByteArray());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * A 30 cm knife along +Y with the blade flat in the XY plane: a thick handle at the bottom, a guard,
     * and a thin tapering blade on top. Returned as a builder so callers can transform or export it.
     */
    public static Builder knife() {
        Builder builder = new Builder();
        builder.box(-0.012F, 0.00F, -0.010F, 0.012F, 0.11F, 0.010F);
        builder.box(-0.030F, 0.11F, -0.012F, 0.030F, 0.12F, 0.012F);
        builder.box(-0.016F, 0.12F, -0.002F, 0.016F, 0.22F, 0.002F);
        builder.box(-0.010F, 0.22F, -0.002F, 0.012F, 0.27F, 0.002F);
        builder.box(-0.004F, 0.27F, -0.0015F, 0.008F, 0.30F, 0.0015F);
        return builder;
    }

    /**
     * A 1.8 m box character facing +Z with toes forward. In T-pose the arms point sideways at
     * shoulder height, otherwise they hang down beside the torso with a gap.
     */
    public static Builder humanoid(boolean tPose, boolean withParts) {
        Builder b = new Builder();
        b.box(-0.12F, 1.52F, -0.12F, 0.12F, 1.80F, 0.12F, BodyPart.HEAD, 1);
        b.box(-0.05F, 1.46F, -0.05F, 0.05F, 1.52F, 0.05F, BodyPart.BODY, 0);
        b.box(-0.20F, 0.86F, -0.10F, 0.20F, 1.46F, 0.10F, BodyPart.BODY, 0);
        if (tPose) {
            b.box(-0.82F, 1.30F, -0.06F, -0.21F, 1.42F, 0.06F, BodyPart.RIGHT_ARM, 2);
            b.box(0.21F, 1.30F, -0.06F, 0.82F, 1.42F, 0.06F, BodyPart.LEFT_ARM, 3);
        } else {
            b.box(-0.36F, 0.80F, -0.06F, -0.24F, 1.44F, 0.06F, BodyPart.RIGHT_ARM, 2);
            b.box(0.24F, 0.80F, -0.06F, 0.36F, 1.44F, 0.06F, BodyPart.LEFT_ARM, 3);
        }
        b.box(-0.18F, 0.06F, -0.08F, -0.03F, 0.86F, 0.08F, BodyPart.RIGHT_LEG, 4);
        b.box(0.03F, 0.06F, -0.08F, 0.18F, 0.86F, 0.08F, BodyPart.LEFT_LEG, 5);
        b.box(-0.18F, 0.00F, -0.08F, -0.03F, 0.06F, 0.22F, BodyPart.RIGHT_LEG, 4);
        b.box(0.03F, 0.00F, -0.08F, 0.18F, 0.06F, 0.22F, BodyPart.LEFT_LEG, 5);
        return b;
    }

    public static final String[] MIXAMO_JOINTS = {
            "mixamorig:Spine", "mixamorig:Head", "mixamorig:RightArm", "mixamorig:LeftArm", "mixamorig:RightUpLeg", "mixamorig:LeftUpLeg"};

    static float[] floats(List<Float> values) {
        float[] result = new float[values.size()];
        for (int index = 0; index < result.length; index++) result[index] = values.get(index);
        return result;
    }

    private static byte[] floatBytes(float[] values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) buffer.putFloat(value);
        return buffer.array();
    }

    private static int append(ByteArrayOutputStream output, byte[] data) {
        while (output.size() % 4 != 0) output.write(0);
        int offset = output.size();
        output.write(data, 0, data.length);
        return offset;
    }

    private static void view(JsonArray views, int offset, int length) {
        JsonObject view = new JsonObject();
        view.addProperty("buffer", 0);
        view.addProperty("byteOffset", offset);
        view.addProperty("byteLength", length);
        views.add(view);
    }

    private static void accessor(JsonArray accessors, int view, int componentType, int count, String type) {
        JsonObject accessor = new JsonObject();
        accessor.addProperty("bufferView", view);
        accessor.addProperty("componentType", componentType);
        accessor.addProperty("count", count);
        accessor.addProperty("type", type);
        accessors.add(accessor);
    }

    private static void writeGlbFile(File file, byte[] json, byte[] binary) throws IOException {
        int jsonLength = (json.length + 3) & ~3, binaryLength = (binary.length + 3) & ~3;
        ByteBuffer glb = ByteBuffer.allocate(12 + 8 + jsonLength + 8 + binaryLength).order(ByteOrder.LITTLE_ENDIAN);
        glb.putInt(0x46546C67).putInt(2).putInt(glb.capacity());
        glb.putInt(jsonLength).putInt(0x4E4F534A).put(json);
        for (int pad = json.length; pad < jsonLength; pad++) glb.put((byte) ' ');
        glb.putInt(binaryLength).putInt(0x004E4942).put(binary);
        for (int pad = binary.length; pad < binaryLength; pad++) glb.put((byte) 0);
        Files.write(file.toPath(), glb.array());
    }
}
