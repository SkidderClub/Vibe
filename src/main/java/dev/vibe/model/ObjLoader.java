package dev.vibe.model;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Wavefront OBJ with MTL materials (Kd, d/Tr, map_Kd). Polygons are fanned into triangles. */
final class ObjLoader {

    private final File directory;
    private final ModelLoadOptions options;
    private final FloatList positions = new FloatList();
    private final FloatList vertexColors = new FloatList();
    private final FloatList texCoords = new FloatList();
    private final FloatList normals = new FloatList();
    private final Map<String, Group> groups = new LinkedHashMap<String, Group>();
    private final Map<String, MaterialSource> library = new HashMap<String, MaterialSource>();
    private final Map<String, BufferedImage> textures = new HashMap<String, BufferedImage>();
    private boolean anyVertexColor;
    private int triangles;

    private static final class MaterialSource {
        float[] color = {1, 1, 1, 1};
        String texture;
        File base;
    }

    /** Corners of one material: original position index plus resolved attributes. */
    private static final class Group {
        final IntList position = new IntList();
        final IntList texCoord = new IntList();
        final IntList normal = new IntList();
    }

    private ObjLoader(File file, ModelLoadOptions options) {
        this.directory = file.getAbsoluteFile().getParentFile();
        this.options = options;
    }

    static MeshData load(File file, ModelLoadOptions options) throws IOException {
        ObjLoader loader = new ObjLoader(file, options);
        loader.parse(ModelFiles.read(file, options.maxFileBytes));
        return loader.build();
    }

    private void parse(byte[] data) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8));
        Group group = group("");
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            String[] parts = line.split("\\s+");
            String keyword = parts[0];
            if ("v".equals(keyword) && parts.length >= 4) {
                positions.add(parse(parts[1]), parse(parts[2]), parse(parts[3]));
                if (parts.length >= 7) {
                    anyVertexColor = true;
                    vertexColors.add(parse(parts[4]), parse(parts[5]), parse(parts[6]));
                } else {
                    vertexColors.add(1, 1, 1);
                }
            } else if ("vt".equals(keyword) && parts.length >= 2) {
                // OBJ puts v = 0 at the bottom of the image; the renderer expects the top.
                texCoords.add(parse(parts[1]), 1.0F - (parts.length >= 3 ? parse(parts[2]) : 0.0F));
            } else if ("vn".equals(keyword) && parts.length >= 4) {
                normals.add(parse(parts[1]), parse(parts[2]), parse(parts[3]));
            } else if ("f".equals(keyword) && parts.length >= 4) {
                face(group, parts);
            } else if ("usemtl".equals(keyword)) {
                group = group(line.substring(keyword.length()).trim());
            } else if ("mtllib".equals(keyword)) {
                String names = line.substring(keyword.length()).trim();
                File libraryFile = ModelFiles.resolveRelative(directory, names);
                if (libraryFile != null) materials(libraryFile);
                else for (int index = 1; index < parts.length; index++) {
                    File single = ModelFiles.resolveRelative(directory, parts[index]);
                    if (single != null) materials(single);
                }
            }
        }
    }

    private Group group(String material) {
        Group group = groups.get(material);
        if (group == null) {
            group = new Group();
            groups.put(material, group);
        }
        return group;
    }

    private void face(Group group, String[] parts) throws IOException {
        int corners = parts.length - 1;
        int[] position = new int[corners], texCoord = new int[corners], normal = new int[corners];
        for (int corner = 0; corner < corners; corner++) {
            String[] indices = parts[corner + 1].split("/", -1);
            position[corner] = index(indices[0], positions.size() / 3);
            texCoord[corner] = indices.length > 1 && !indices[1].isEmpty() ? index(indices[1], texCoords.size() / 2) : -1;
            normal[corner] = indices.length > 2 && !indices[2].isEmpty() ? index(indices[2], normals.size() / 3) : -1;
            if (position[corner] < 0) return;
        }
        for (int corner = 1; corner + 1 < corners; corner++) {
            if (++triangles > options.maxSourceTriangles) {
                throw new IOException("Model has more than " + options.maxSourceTriangles + " triangles");
            }
            corner(group, position, texCoord, normal, 0);
            corner(group, position, texCoord, normal, corner);
            corner(group, position, texCoord, normal, corner + 1);
        }
    }

    private static void corner(Group group, int[] position, int[] texCoord, int[] normal, int k) {
        group.position.add(position[k]);
        group.texCoord.add(texCoord[k]);
        group.normal.add(normal[k]);
    }

    /** Converts a 1-based or negative (relative) OBJ index to a 0-based one, -1 when invalid. */
    private static int index(String text, int count) {
        try {
            int value = Integer.parseInt(text);
            int resolved = value < 0 ? count + value : value - 1;
            return resolved >= 0 && resolved < count ? resolved : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static float parse(String text) {
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException ignored) {
            return 0.0F;
        }
    }

    private void materials(File file) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new ByteArrayInputStream(ModelFiles.read(file, options.maxFileBytes)), StandardCharsets.UTF_8));
            MaterialSource current = null;
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] parts = line.split("\\s+");
                String keyword = parts[0].toLowerCase(Locale.ROOT);
                if ("newmtl".equals(keyword)) {
                    current = new MaterialSource();
                    current.base = file.getAbsoluteFile().getParentFile();
                    library.put(line.substring(parts[0].length()).trim(), current);
                } else if (current == null) {
                    continue;
                } else if ("kd".equals(keyword) && parts.length >= 4) {
                    current.color[0] = parse(parts[1]);
                    current.color[1] = parse(parts[2]);
                    current.color[2] = parse(parts[3]);
                } else if ("d".equals(keyword) && parts.length >= 2) {
                    current.color[3] = parse(parts[1]);
                } else if ("tr".equals(keyword) && parts.length >= 2) {
                    current.color[3] = 1.0F - parse(parts[1]);
                } else if ("map_kd".equals(keyword) && parts.length >= 2) {
                    current.texture = texturePath(parts);
                }
            }
        } catch (IOException ignored) {
            // Without its MTL the model still renders untextured.
        }
    }

    /** Skips MTL texture options such as "-s 1 1 1" and returns the remaining file name. */
    static String texturePath(String[] parts) {
        int index = 1;
        while (index < parts.length && parts[index].startsWith("-") && parts[index].length() > 1
                && !Character.isDigit(parts[index].charAt(1))) {
            String option = parts[index].toLowerCase(Locale.ROOT);
            index++;
            int arguments = option.equals("-o") || option.equals("-s") || option.equals("-t") ? 3
                    : option.equals("-mm") ? 2 : 1;
            for (int argument = 0; argument < arguments && index < parts.length - 1; argument++) {
                if (option.equals("-o") || option.equals("-s") || option.equals("-t") || option.equals("-mm")) {
                    if (!isNumber(parts[index])) break;
                }
                index++;
            }
        }
        StringBuilder path = new StringBuilder();
        for (; index < parts.length; index++) {
            if (path.length() > 0) path.append(' ');
            path.append(parts[index]);
        }
        return path.toString();
    }

    private static boolean isNumber(String text) {
        try {
            Float.parseFloat(text);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private BufferedImage texture(MaterialSource source) {
        if (source.texture == null || source.texture.isEmpty()) return null;
        String key = source.base + "|" + source.texture;
        if (textures.containsKey(key)) return textures.get(key);
        BufferedImage image = null;
        try {
            File file = ModelFiles.resolveRelative(source.base, source.texture);
            if (file != null) image = ModelImages.decode(ModelFiles.read(file, options.maxFileBytes), options.maxTextureSize);
        } catch (Exception ignored) {
            // Fall back to the diffuse colour.
        }
        textures.put(key, image);
        return image;
    }

    private MeshData build() throws IOException {
        int positionCount = positions.size() / 3;
        // Smooth normals per original position for faces exported without "vn".
        float[] fallbackNormals = new float[positionCount * 3];
        for (Group group : groups.values()) {
            for (int corner = 0; corner + 2 < group.position.size(); corner += 3) {
                int a = group.position.get(corner) * 3, b = group.position.get(corner + 1) * 3, c = group.position.get(corner + 2) * 3;
                float ux = positions.get(b) - positions.get(a), uy = positions.get(b + 1) - positions.get(a + 1);
                float uz = positions.get(b + 2) - positions.get(a + 2);
                float vx = positions.get(c) - positions.get(a), vy = positions.get(c + 1) - positions.get(a + 1);
                float vz = positions.get(c + 2) - positions.get(a + 2);
                float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
                accumulate(fallbackNormals, a, nx, ny, nz);
                accumulate(fallbackNormals, b, nx, ny, nz);
                accumulate(fallbackNormals, c, nx, ny, nz);
            }
        }
        List<MeshData.Material> materials = new ArrayList<MeshData.Material>();
        List<MeshData.Primitive> primitives = new ArrayList<MeshData.Primitive>();
        for (Map.Entry<String, Group> entry : groups.entrySet()) {
            Group group = entry.getValue();
            int count = group.position.size();
            if (count < 3) continue;
            MaterialSource source = library.get(entry.getKey());
            if (source == null) source = new MaterialSource();
            // OBJ has no alpha mode; cut out transparent texels like vanilla does, blend when "d" asks for it.
            int alphaMode = source.color[3] < 0.99F ? MeshData.Material.BLEND : MeshData.Material.MASK;
            materials.add(new MeshData.Material(entry.getKey(), source.color.clone(), texture(source), alphaMode, 0.1F, true));
            float[] p = new float[count * 3], n = new float[count * 3], uv = null, colors = anyVertexColor ? new float[count * 4] : null;
            int[] indices = new int[count];
            for (int corner = 0; corner < count; corner++) {
                int position = group.position.get(corner);
                p[corner * 3] = positions.get(position * 3);
                p[corner * 3 + 1] = positions.get(position * 3 + 1);
                p[corner * 3 + 2] = positions.get(position * 3 + 2);
                int normal = group.normal.get(corner);
                float nx, ny, nz;
                if (normal >= 0) {
                    nx = normals.get(normal * 3);
                    ny = normals.get(normal * 3 + 1);
                    nz = normals.get(normal * 3 + 2);
                } else {
                    nx = fallbackNormals[position * 3];
                    ny = fallbackNormals[position * 3 + 1];
                    nz = fallbackNormals[position * 3 + 2];
                }
                float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (length < 1.0E-12F) {
                    ny = 1.0F;
                    length = 1.0F;
                }
                n[corner * 3] = nx / length;
                n[corner * 3 + 1] = ny / length;
                n[corner * 3 + 2] = nz / length;
                int texCoord = group.texCoord.get(corner);
                if (texCoord >= 0) {
                    if (uv == null) uv = new float[count * 2];
                    uv[corner * 2] = texCoords.get(texCoord * 2);
                    uv[corner * 2 + 1] = texCoords.get(texCoord * 2 + 1);
                }
                if (colors != null) {
                    colors[corner * 4] = vertexColors.get(position * 3);
                    colors[corner * 4 + 1] = vertexColors.get(position * 3 + 1);
                    colors[corner * 4 + 2] = vertexColors.get(position * 3 + 2);
                    colors[corner * 4 + 3] = 1.0F;
                }
                indices[corner] = corner;
            }
            primitives.add(new MeshData.Primitive(materials.size() - 1, p, n, uv, colors, indices, null));
        }
        if (primitives.isEmpty()) throw new IOException("The OBJ file contains no faces");
        return new MeshData(materials, primitives, null, false);
    }

    private static void accumulate(float[] normals, int index, float x, float y, float z) {
        normals[index] += x;
        normals[index + 1] += y;
        normals[index + 2] += z;
    }

    /** Growable float array without boxing. */
    static final class FloatList {
        private float[] values = new float[1024];
        private int size;

        void add(float a, float b) {
            ensure(2);
            values[size++] = a;
            values[size++] = b;
        }

        void add(float a, float b, float c) {
            ensure(3);
            values[size++] = a;
            values[size++] = b;
            values[size++] = c;
        }

        float get(int index) {
            return values[index];
        }

        int size() {
            return size;
        }

        private void ensure(int extra) {
            if (size + extra > values.length) values = java.util.Arrays.copyOf(values, Math.max(values.length * 2, size + extra));
        }
    }

    /** Growable int array without boxing. */
    static final class IntList {
        private int[] values = new int[1024];
        private int size;

        void add(int value) {
            if (size == values.length) values = java.util.Arrays.copyOf(values, values.length * 2);
            values[size++] = value;
        }

        int get(int index) {
            return values[index];
        }

        int size() {
            return size;
        }
    }
}
