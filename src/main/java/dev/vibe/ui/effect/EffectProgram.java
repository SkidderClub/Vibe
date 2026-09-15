package dev.vibe.ui.effect;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** Small GLSL 1.20 loader; resources are loaded from the mod JAR, including includes. */
public final class EffectProgram implements AutoCloseable {
    private int program;
    private final Map<String, Integer> uniforms = new HashMap<String, Integer>();
    public EffectProgram(String vertex, String fragment) throws IOException {
        int vs = 0, fs = 0;
        try {
            vs = compile(GL20.GL_VERTEX_SHADER, read(vertex, 0));
            fs = compile(GL20.GL_FRAGMENT_SHADER, read(fragment, 0));
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vs); GL20.glAttachShader(program, fs); GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
                throw new IOException(GL20.glGetProgramInfoLog(program, 8192));
        } catch (IOException e) { close(); throw e;
        } finally { if (vs != 0) GL20.glDeleteShader(vs); if (fs != 0) GL20.glDeleteShader(fs); }
    }
    private static int compile(int type, String source) throws IOException {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source); GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String error = GL20.glGetShaderInfoLog(shader, 8192); GL20.glDeleteShader(shader); throw new IOException(error);
        }
        return shader;
    }
    private static String read(String name, int depth) throws IOException {
        if (depth > 8 || name.contains("..")) throw new IOException("Invalid shader include");
        InputStream stream = EffectProgram.class.getResourceAsStream("/assets/vibe/shaders/schizoid/" + name);
        if (stream == null) throw new FileNotFoundException(name);
        StringBuilder source = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("#include \"")) {
                    String include = line.substring(line.indexOf('"') + 1, line.lastIndexOf('"'));
                    source.append(read("include/" + include, depth + 1));
                } else source.append(line).append('\n');
            }
        }
        return source.toString();
    }
    public void bind() { GL20.glUseProgram(program); }
    private int location(String name) {
        Integer value = uniforms.get(name);
        if (value == null) { value = GL20.glGetUniformLocation(program, name); uniforms.put(name, value); }
        return value;
    }
    public void integer(String name, int value) { GL20.glUniform1i(location(name), value); }
    public void scalar(String name, float value) { GL20.glUniform1f(location(name), value); }
    public void vec2(String name, float x, float y) { GL20.glUniform2f(location(name), x, y); }
    public void vec3(String name, float x, float y, float z) { GL20.glUniform3f(location(name), x, y, z); }
    public void close() { if (program != 0) GL20.glDeleteProgram(program); program = 0; uniforms.clear(); }
}
