package dev.vibe.extension;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vibe.BuildInfo;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Metadata is inspected without executing any extension code. */
public final class ExtensionDescriptor {
    public static final int API_VERSION = 1;
    public static final String PATH = "META-INF/vibe-extension.json";
    public final String id, name, version, entrypoint;
    private ExtensionDescriptor(String id, String name, String version, String entrypoint) {
        this.id = id; this.name = name; this.version = version; this.entrypoint = entrypoint;
    }
    static ExtensionDescriptor read(JarFile jar) throws IOException {
        JarEntry entry = jar.getJarEntry(PATH);
        if (entry == null) return null;
        if (entry.getSize() < 0 || entry.getSize() > 16384) throw new IOException("Extension descriptor exceeds 16 KiB");
        try (InputStream in = jar.getInputStream(entry)) {
            JsonElement parsed = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new IOException("Extension descriptor must be an object");
            JsonObject json = parsed.getAsJsonObject();
            if (!json.has("apiVersion") || !json.get("apiVersion").isJsonPrimitive()
                    || !json.getAsJsonPrimitive("apiVersion").isNumber()
                    || !String.valueOf(API_VERSION).equals(json.get("apiVersion").getAsString()))
                throw new IOException("Unsupported extension API; this Vibe supports API " + API_VERSION);
            String id = string(json, "id"), name = string(json, "name"), version = string(json, "version");
            String main = string(json, "entrypoint");
            if (!id.matches("[a-z][a-z0-9_-]{1,63}")) throw new IOException("Invalid extension id: " + id);
            if (!main.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+"))
                throw new IOException("Invalid extension entrypoint");
            if (json.has("vibeVersion") && !BuildInfo.VERSION.equals(string(json, "vibeVersion")))
                throw new IOException("Requires Vibe " + string(json, "vibeVersion") + "; running " + BuildInfo.VERSION);
            if (jar.getJarEntry(main.replace('.', '/') + ".class") == null)
                throw new IOException("Entrypoint class is missing from this JAR: " + main);
            return new ExtensionDescriptor(id, name, version, main);
        } catch (RuntimeException error) { throw new IOException("Invalid extension descriptor: " + error.getMessage(), error); }
    }
    private static String string(JsonObject object, String key) throws IOException {
        if (!object.has(key) || !object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isString())
            throw new IOException("Missing string field: " + key);
        String value = object.get(key).getAsString().trim();
        if (value.isEmpty() || value.length() > 255) throw new IOException("Invalid field: " + key);
        return value;
    }
}
