/* Gothaj RoundedUtil's counter paths, with lazy Forge resource loading.
 * Original GLSL is retained under assets/vibe/scaffold. */
package dev.vibe.ui.scaffold;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

public final class CounterRender {
    private static int round, gradient;
    private CounterRender() { }

    public static double roundToHalf(double d) { return (double) Math.round(d * 2.0) / 2.0; }
    public static void color(int color) { color(color, (color >>> 24) / 255.0F); }
    public static void color(int color, float alpha) {
        GlStateManager.color((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F, (color & 255) / 255.0F, alpha);
    }
    public static void resetColor() { GlStateManager.color(1, 1, 1, 1); }

    public static void drawRound(float x, float y, float width, float height, float radius, Color color) {
        if (round == 0) round = program("roundedRect.frag");
        int previous = begin(round, x, y, width, height, radius);
        try {
            GL20.glUniform1i(GL20.glGetUniformLocation(round, "blur"), 0);
            uniformColor(round, "color", color);
            quads(x - 1, y - 1, width + 2, height + 2);
        } finally { end(previous); }
    }

    public static void drawGradientHorizontal(float x, float y, float width, float height, float radius, Color left, Color right) {
        if (gradient == 0) gradient = program("roundRectGradient.frag");
        int previous = begin(gradient, x, y, width, height, radius);
        try {
            uniformColor(gradient, "color1", left); uniformColor(gradient, "color2", left);
            uniformColor(gradient, "color3", right); uniformColor(gradient, "color4", right);
            quads(x - 1, y - 1, width + 2, height + 2);
        } finally { end(previous); }
    }

    private static int begin(int program, float x, float y, float width, float height, float radius) {
        int previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        resetColor();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
        GL20.glUseProgram(program);
        Minecraft mc = Minecraft.getMinecraft();
        int factor = new ScaledResolution(mc).getScaleFactor();
        GL20.glUniform2f(GL20.glGetUniformLocation(program, "location"), x * factor, mc.displayHeight - height * factor - y * factor);
        GL20.glUniform2f(GL20.glGetUniformLocation(program, "rectSize"), width * factor, height * factor);
        GL20.glUniform1f(GL20.glGetUniformLocation(program, "radius"), radius * factor);
        return previous;
    }

    private static void end(int previous) {
        GL20.glUseProgram(previous);
        GlStateManager.disableBlend();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        resetColor();
    }

    private static void uniformColor(int program, String uniform, Color color) {
        GL20.glUniform4f(GL20.glGetUniformLocation(program, uniform), color.getRed() / 255.0F,
                color.getGreen() / 255.0F, color.getBlue() / 255.0F, color.getAlpha() / 255.0F);
    }

    private static void quads(float x, float y, float width, float height) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0); GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(0, 1); GL11.glVertex2f(x, y + height);
        GL11.glTexCoord2f(1, 1); GL11.glVertex2f(x + width, y + height);
        GL11.glTexCoord2f(1, 0); GL11.glVertex2f(x + width, y);
        GL11.glEnd();
    }

    private static int program(String fragment) {
        int vertex = compile(GL20.GL_VERTEX_SHADER, "vertex.vert"), pixel = 0, program = 0;
        try {
            pixel = compile(GL20.GL_FRAGMENT_SHADER, fragment);
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertex); GL20.glAttachShader(program, pixel); GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0)
                throw new IllegalStateException(GL20.glGetProgramInfoLog(program, 4096));
            return program;
        } catch (RuntimeException error) {
            if (program != 0) GL20.glDeleteProgram(program);
            throw error;
        } finally { GL20.glDeleteShader(vertex); if (pixel != 0) GL20.glDeleteShader(pixel); }
    }

    private static int compile(int type, String resource) {
        String source;
        try (InputStream in = CounterRender.class.getResourceAsStream("/assets/vibe/scaffold/" + resource);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048]; int count;
            while ((count = in.read(buffer)) != -1) bytes.write(buffer, 0, count);
            source = new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception error) { throw new IllegalStateException(resource, error); }
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source); GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String error = GL20.glGetShaderInfoLog(shader, 4096); GL20.glDeleteShader(shader);
            throw new IllegalStateException(resource + ": " + error);
        }
        return shader;
    }
}
