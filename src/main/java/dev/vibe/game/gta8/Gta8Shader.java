package dev.vibe.game.gta8;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** GLSL 1.20 program from {@code assets/vibe/shaders/gta8}, with includes, defines and cached uniforms. */
final class Gta8Shader {
    static final int POSITION = 0, NORMAL = 1, COLOR = 2, UV = 3, MATERIAL = 4;
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private int program;
    private final String name;
    private final Map<String, Integer> uniforms = new HashMap<String, Integer>();

    Gta8Shader(String vertex, String fragment, String defines) throws IOException {
        name = vertex + "/" + fragment;
        int vs = 0, fs = 0;
        try {
            vs = compile(GL20.GL_VERTEX_SHADER, source(vertex, defines), vertex);
            fs = compile(GL20.GL_FRAGMENT_SHADER, source(fragment, defines), fragment);
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vs); GL20.glAttachShader(program, fs);
            GL20.glBindAttribLocation(program, POSITION, "aPos");
            GL20.glBindAttribLocation(program, NORMAL, "aNormal");
            GL20.glBindAttribLocation(program, COLOR, "aColor");
            GL20.glBindAttribLocation(program, UV, "aUV");
            GL20.glBindAttribLocation(program, MATERIAL, "aMat");
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
                throw new IOException("Link failed for " + name + ": " + GL20.glGetProgramInfoLog(program, 8192));
        } catch (IOException e) {
            close(); throw e;
        } finally {
            if (vs != 0) GL20.glDeleteShader(vs);
            if (fs != 0) GL20.glDeleteShader(fs);
        }
    }

    private static String source(String file, String defines) throws IOException {
        StringBuilder out = new StringBuilder("#version 120\n");
        if (defines != null) for (String d : defines.split(",")) if (!d.trim().isEmpty()) out.append("#define ").append(d.trim().replace('=', ' ')).append('\n');
        out.append("#line 1\n");
        append(out, file, 0);
        return out.toString();
    }
    private static void append(StringBuilder out, String file, int depth) throws IOException {
        if (depth > 6 || file.contains("..")) throw new IOException("Invalid shader include " + file);
        InputStream stream = Gta8Shader.class.getResourceAsStream("/assets/vibe/shaders/gta8/" + file);
        if (stream == null) throw new FileNotFoundException("gta8/" + file);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String t = line.trim();
                if (t.startsWith("#include \"")) append(out, t.substring(t.indexOf('"') + 1, t.lastIndexOf('"')), depth + 1);
                else out.append(line).append('\n');
            }
        }
    }
    private static int compile(int type, String source, String file) throws IOException {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader, 16384);
            GL20.glDeleteShader(shader);
            throw new IOException("Compile failed for " + file + ": " + log);
        }
        return shader;
    }

    void bind() { GL20.glUseProgram(program); }
    int location(String uniform) {
        Integer value = uniforms.get(uniform);
        if (value == null) { value = GL20.glGetUniformLocation(program, uniform); uniforms.put(uniform, value); }
        return value;
    }
    boolean has(String uniform) { return location(uniform) >= 0; }
    void set(String uniform, int value) { int l = location(uniform); if (l >= 0) GL20.glUniform1i(l, value); }
    void set(String uniform, float value) { int l = location(uniform); if (l >= 0) GL20.glUniform1f(l, value); }
    void set(String uniform, double value) { set(uniform, (float) value); }
    void set(String uniform, float x, float y) { int l = location(uniform); if (l >= 0) GL20.glUniform2f(l, x, y); }
    void set(String uniform, float x, float y, float z) { int l = location(uniform); if (l >= 0) GL20.glUniform3f(l, x, y, z); }
    void set(String uniform, double x, double y, double z) { set(uniform, (float) x, (float) y, (float) z); }
    void set(String uniform, float x, float y, float z, float w) { int l = location(uniform); if (l >= 0) GL20.glUniform4f(l, x, y, z, w); }
    void rgb(String uniform, int rgb, float scale) { set(uniform, (rgb >> 16 & 255) / 255f * scale, (rgb >> 8 & 255) / 255f * scale, (rgb & 255) / 255f * scale); }
    void matrix(String uniform, float[] m) {
        int l = location(uniform); if (l < 0) return;
        MATRIX.clear(); MATRIX.put(m).flip();
        GL20.glUniformMatrix4(l, false, MATRIX);
    }
    void matrices(String uniform, FloatBuffer data) { int l = location(uniform); if (l >= 0) GL20.glUniformMatrix4(l, false, data); }
    void vec4s(String uniform, FloatBuffer data) { int l = location(uniform); if (l >= 0) GL20.glUniform4(l, data); }
    void floats(String uniform, FloatBuffer data) { int l = location(uniform); if (l >= 0) GL20.glUniform1(l, data); }
    void close() { if (program != 0) GL20.glDeleteProgram(program); program = 0; uniforms.clear(); }
}
