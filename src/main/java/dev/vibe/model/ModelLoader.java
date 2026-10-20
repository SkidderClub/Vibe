package dev.vibe.model;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/** Reads any supported model file into a {@link MeshData}. */
public final class ModelLoader {

    private ModelLoader() {
    }

    /** @param file a .glb, .gltf or .obj file (archives and folders are resolved by the caller) */
    public static MeshData load(File file, ModelLoadOptions options) throws IOException {
        if (file == null || !file.isFile()) throw new IOException("Model file not found");
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".obj")) return ObjLoader.load(file, options);
        if (name.endsWith(".gltf") || name.endsWith(".glb")) return GltfLoader.load(file, options);
        throw new IOException("Unsupported model format: " + file.getName());
    }
}
