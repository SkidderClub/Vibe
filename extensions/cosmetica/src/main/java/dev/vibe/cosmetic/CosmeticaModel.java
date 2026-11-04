package dev.vibe.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.lwjgl.opengl.GL11;

/** Bakes Cosmetica block models into attachment-local quads for the 1.8.9 renderer. */
public final class CosmeticaModel {
    // Authored face UVs use Minecraft's 0..16 space, regardless of PNG resolution
    // or the Blockbench texture_size recorded in the model.
    private static final float UNIT = 1.0F / 16.0F;

    private final float[] vertices;

    private CosmeticaModel(float[] vertices) {
        this.vertices = vertices;
    }

    public static CosmeticaModel parse(String source) {
        JsonObject root = new JsonParser().parse(source).getAsJsonObject();
        JsonArray elements = root.getAsJsonArray("elements");
        if (elements == null) throw new IllegalArgumentException("Missing Cosmetica model elements");

        List<float[]> quads = new ArrayList<>();
        for (JsonElement raw : elements) {
            JsonObject element = raw.getAsJsonObject();
            float[] from = vector(element.getAsJsonArray("from"), 3);
            float[] to = vector(element.getAsJsonArray("to"), 3);
            Rotation rotation = Rotation.parse(element.getAsJsonObject("rotation"));
            JsonObject faces = element.getAsJsonObject("faces");
            for (Map.Entry<String, JsonElement> face : faces.entrySet()) {
                Direction direction = Direction.valueOf(face.getKey().toUpperCase(Locale.ROOT));
                quads.add(direction.bake(from, to, rotation, face.getValue().getAsJsonObject()));
            }
        }
        if (quads.isEmpty()) throw new IllegalArgumentException("Cosmetica model has no renderable faces");

        float[] vertices = new float[quads.size() * 20];
        for (int i = 0; i < quads.size(); i++) System.arraycopy(quads.get(i), 0, vertices, i * 20, 20);
        return new CosmeticaModel(vertices);
    }

    public void render(int frame, int frames) {
        int count = Math.max(1, frames);
        float frameHeight = 1.0F / count;
        float frameOffset = Math.floorMod(frame, count) * frameHeight;
        GL11.glBegin(GL11.GL_QUADS);
        try {
            for (int i = 0; i < vertices.length; i += 5) {
                GL11.glTexCoord2f(vertices[i + 3], frameOffset + vertices[i + 4] * frameHeight);
                GL11.glVertex3f(vertices[i], vertices[i + 1], vertices[i + 2]);
            }
        } finally {
            GL11.glEnd();
        }
    }

    float[] bakedVertices() {
        return vertices.clone();
    }

    private enum Direction {
        NORTH(6, 4, 0, 2),
        SOUTH(3, 1, 5, 7),
        EAST(7, 5, 4, 6),
        WEST(2, 0, 1, 3),
        UP(2, 3, 7, 6),
        DOWN(1, 0, 4, 5);

        // Vertex order matches Cosmetica Core's FaceInfo. Bits choose the
        // maximum coordinate for x (4), y (2) and z (1).
        private final int[] corners;

        Direction(int... corners) {
            this.corners = corners;
        }

        private float[] bake(float[] from, float[] to, Rotation rotation, JsonObject face) {
            JsonArray uvArray = face.getAsJsonArray("uv");
            float[] uv = uvArray == null ? defaultUv(from, to) : vector(uvArray, 4);
            int degrees = face.has("rotation") ? face.get("rotation").getAsInt() : 0;
            int turns = Math.floorMod(degrees / 90, 4);
            float[] quad = new float[20];
            for (int i = 0; i < 4; i++) {
                int corner = corners[i];
                float[] point = {(corner & 4) == 0 ? from[0] : to[0],
                        (corner & 2) == 0 ? from[1] : to[1],
                        (corner & 1) == 0 ? from[2] : to[2]};
                if (rotation != null) rotation.apply(point);
                int offset = i * 5;
                quad[offset] = (point[0] - 8.0F) * UNIT;
                quad[offset + 1] = (point[1] - 4.0F) * UNIT;
                quad[offset + 2] = (point[2] - 8.0F) * UNIT;
                int uvCorner = (i + turns) & 3;
                quad[offset + 3] = uv[uvCorner < 2 ? 0 : 2] * UNIT;
                quad[offset + 4] = uv[uvCorner == 1 || uvCorner == 2 ? 3 : 1] * UNIT;
            }
            return quad;
        }

        private float[] defaultUv(float[] from, float[] to) {
            switch (this) {
                case DOWN: return new float[]{from[0], 16 - to[2], to[0], 16 - from[2]};
                case UP: return new float[]{from[0], from[2], to[0], to[2]};
                case NORTH: return new float[]{16 - to[0], 16 - to[1], 16 - from[0], 16 - from[1]};
                case SOUTH: return new float[]{from[0], 16 - to[1], to[0], 16 - from[1]};
                case WEST: return new float[]{from[2], 16 - to[1], to[2], 16 - from[1]};
                case EAST: return new float[]{16 - to[2], 16 - to[1], 16 - from[2], 16 - from[1]};
                default: throw new AssertionError(this);
            }
        }
    }

    private static final class Rotation {
        private final float[] origin;
        private final float[] sine = new float[3];
        private final float[] cosine = new float[3];
        private final float[] rescale = {1, 1, 1};

        private Rotation(float[] origin, float[] angles, int rescaleAxis) {
            this.origin = origin;
            for (int axis = 0; axis < 3; axis++) {
                double radians = Math.toRadians(angles[axis]);
                sine[axis] = (float) Math.sin(radians);
                cosine[axis] = (float) Math.cos(radians);
            }
            if (rescaleAxis >= 0 && cosine[rescaleAxis] > 0.001F) {
                for (int axis = 0; axis < 3; axis++) {
                    if (axis != rescaleAxis) rescale[axis] = 1.0F / cosine[rescaleAxis];
                }
            }
        }

        private static Rotation parse(JsonObject raw) {
            if (raw == null) return null;
            float[] origin = vector(raw.getAsJsonArray("origin"), 3);
            float[] angles = new float[3];
            int rescaleAxis = -1;
            if (raw.has("angle")) {
                String axisName = raw.get("axis").getAsString().toLowerCase(Locale.ROOT);
                int axis = axisName.length() == 1 ? "xyz".indexOf(axisName) : -1;
                if (axis < 0) throw new IllegalArgumentException("Unknown rotation axis " + axisName);
                angles[axis] = number(raw.get("angle"));
                if (raw.has("rescale") && raw.get("rescale").getAsBoolean()) rescaleAxis = axis;
            } else {
                angles[0] = number(raw.get("x"));
                angles[1] = number(raw.get("y"));
                angles[2] = number(raw.get("z"));
            }
            return new Rotation(origin, angles, rescaleAxis);
        }

        private void apply(float[] point) {
            for (int i = 0; i < 3; i++) point[i] -= origin[i];
            // Cosmetica 26.3 applies authored X, then Y, then Z.
            rotate(point, 1, 2, 0);
            rotate(point, 2, 0, 1);
            rotate(point, 0, 1, 2);
            for (int i = 0; i < 3; i++) point[i] = point[i] * rescale[i] + origin[i];
        }

        private void rotate(float[] point, int a, int b, int axis) {
            float first = point[a];
            point[a] = first * cosine[axis] - point[b] * sine[axis];
            point[b] = first * sine[axis] + point[b] * cosine[axis];
        }
    }

    private static float[] vector(JsonArray values, int size) {
        if (values == null || values.size() != size) throw new IllegalArgumentException("Invalid model vector");
        float[] result = new float[size];
        for (int i = 0; i < size; i++) result[i] = number(values.get(i));
        return result;
    }

    private static float number(JsonElement value) {
        float result = value.getAsFloat();
        if (!Float.isFinite(result)) throw new IllegalArgumentException("Non-finite model coordinate");
        return result;
    }
}
