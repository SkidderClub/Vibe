package dev.vibe.ui;

import dev.vibe.Vibe;
import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** Extracts bundled fragment presets and draws one behind the Forge main menu. */
public final class MainMenuShaderManager {

    private static final String DEFAULT_PRESET = "prestige.frag";
    private static final String RESOURCE_PREFIX = "assets/vibe/shader/";
    private static final String PRESET_MANIFEST = "/assets/vibe/shader/presets.txt";
    private static final Pattern OUTPUT = Pattern.compile("(?m)^\\s*out\\s+vec4\\s+(\\w+)\\s*;");
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final File shaderDirectory = new File(minecraft.mcDataDir, "vibe/shader");
    private final File settingsFile = new File(shaderDirectory, "selection.cfg");
    private boolean initialized;
    private boolean enabled = true;
    private String selected = DEFAULT_PRESET;
    private String compiledPath = "";
    private long compiledModified;
    private int program;
    private String lastError = "";
    private Framebuffer renderTarget;
    private int targetWidth = -1;
    private int targetHeight = -1;
    private long lastShaderFrame;

    public void draw(int width, int height) {
        ensureInitialized();
        if (!enabled || width <= 0 || height <= 0 || !ensureProgram()) {
            drawFallback(width, height);
            return;
        }

        // GuiScreen uses scaled coordinates, whereas gl_FragCoord uses the real
        // framebuffer.  Passing the scaled resolution makes Shadertoy-style
        // presets sample outside their intended coordinate space on GUI scales
        // other than one.
        final int framebufferWidth = minecraft.displayWidth;
        final int framebufferHeight = minecraft.displayHeight;
        if (framebufferWidth <= 0 || framebufferHeight <= 0) {
            return;
        }

        // Fragment presets are the most expensive thing on the main menu.
        // Render them once to a capped off-screen target and linearly upscale
        // the result. This preserves motion/quality while avoiding a full 2K
        // or 4K fragment pass every menu frame.
        int renderWidth = framebufferWidth;
        int renderHeight = framebufferHeight;
        final int pixelBudget = 520000;
        long pixels = (long) framebufferWidth * (long) framebufferHeight;
        if (pixels > pixelBudget) {
            double scale = Math.sqrt(pixelBudget / (double) pixels);
            renderWidth = Math.max(320, (int) Math.round(framebufferWidth * scale));
            renderHeight = Math.max(180, (int) Math.round(framebufferHeight * scale));
        }
        ensureRenderTarget(renderWidth, renderHeight);

        // Thirty animated shader frames per second feel fluid in a menu and
        // halve the cost on high-refresh displays. The cached FBO is still
        // presented every GUI frame so button interaction remains responsive.
        boolean renderShader = lastShaderFrame == 0L || System.currentTimeMillis() - lastShaderFrame >= 33L;
        if (renderShader) renderShaderFrame(renderWidth, renderHeight, framebufferWidth, framebufferHeight);

        minecraft.getFramebuffer().bindFramebuffer(true);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        renderTarget.framebufferRenderExt(framebufferWidth, framebufferHeight, false);
        // framebufferRenderExt uses physical-pixel orthographic coordinates;
        // restore Minecraft's scaled GUI projection before menu buttons draw.
        minecraft.entityRenderer.setupOverlayRendering();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void renderShaderFrame(int renderWidth, int renderHeight, int framebufferWidth, int framebufferHeight) {
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        // LWJGL 2 validates glGetInteger buffers as though any GL query may
        // return a 4x4 matrix. Allocate 16 even though GL_VIEWPORT returns 4.
        IntBuffer previousViewport = BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT, previousViewport);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try {
            renderTarget.bindFramebuffer(true);
            GL11.glViewport(0, 0, renderWidth, renderHeight);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL20.glUseProgram(program);
            float time = (System.currentTimeMillis() % 3600000L) / 1000.0F;
            float mouseX = Mouse.isCreated() ? Mouse.getX() * renderWidth / framebufferWidth : 0.0F;
            float mouseY = Mouse.isCreated() ? (framebufferHeight - Mouse.getY()) * renderHeight / framebufferHeight : 0.0F;
            uniform("time", time);
            uniform("resolution", (float) renderWidth, (float) renderHeight);
            uniform("mouse", mouseX, mouseY);
            uniform("iTime", time);
            uniform("iResolution", (float) renderWidth, (float) renderHeight);
            uniform3("iResolution", (float) renderWidth, (float) renderHeight, 1.0F);
            uniform4("iMouse", mouseX, mouseY, 0.0F, 0.0F);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(-1.0F, -1.0F);
            GL11.glVertex2f(1.0F, -1.0F);
            GL11.glVertex2f(1.0F, 1.0F);
            GL11.glVertex2f(-1.0F, 1.0F);
            GL11.glEnd();
        } finally {
            GL20.glUseProgram(previousProgram);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            // The saved draw buffer belongs to the previous framebuffer. Restoring
            // it while the shader FBO is bound is invalid after target allocation.
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previousFramebuffer);
            GL11.glPopAttrib();
        }
        lastShaderFrame = System.currentTimeMillis();
    }

    public List<String> getPresets() {
        ensureInitialized();
        File[] files = shaderDirectory.listFiles();
        List<String> values = new ArrayList<String>();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".frag")) {
                    values.add(file.getName());
                }
            }
        }
        Collections.sort(values, String.CASE_INSENSITIVE_ORDER);
        return values;
    }

    public void select(String preset) {
        if (preset == null || !new File(shaderDirectory, preset).isFile()) {
            return;
        }
        selected = preset;
        deleteProgram();
        saveSettings();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        saveSettings();
    }

    public boolean isEnabled() { return enabled; }
    public String getSelected() { return selected; }
    public String getLastError() { return lastError; }
    public File getShaderDirectory() { ensureInitialized(); return shaderDirectory; }

    public boolean openFolder() {
        ensureInitialized();
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(shaderDirectory);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        if (!shaderDirectory.isDirectory()) {
            shaderDirectory.mkdirs();
        }
        extractBundledPresets();
        loadSettings();
        if (!new File(shaderDirectory, selected).isFile()) {
            selected = DEFAULT_PRESET;
        }
        if (!settingsFile.isFile()) saveSettings();
    }

    private void extractBundledPresets() {
        if (extractFromManifest()) {
            return;
        }
        try {
            URL location = Vibe.class.getProtectionDomain().getCodeSource().getLocation();
            File jar = new File(location.toURI());
            if (!jar.isFile()) {
                return;
            }
            JarFile archive = new JarFile(jar);
            try {
                Enumeration<JarEntry> entries = archive.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.isDirectory() || !entry.getName().startsWith(RESOURCE_PREFIX) || !entry.getName().endsWith(".frag")) {
                        continue;
                    }
                    File destination = new File(shaderDirectory, entry.getName().substring(RESOURCE_PREFIX.length()));
                    if (!destination.isFile()) {
                        copy(archive.getInputStream(entry), new FileOutputStream(destination));
                    }
                }
            } finally {
                archive.close();
            }
        } catch (Exception ignored) {
        }
    }

    /** Uses a generated resource manifest, which works from both a dev classpath and a remapped mod JAR. */
    private boolean extractFromManifest() {
        InputStream stream = MainMenuShaderManager.class.getResourceAsStream(PRESET_MANIFEST);
        if (stream == null) {
            return false;
        }
        int copied = 0;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            try {
                String name;
                while ((name = reader.readLine()) != null) {
                    name = name.trim();
                    if (name.isEmpty() || !name.endsWith(".frag") || !new File(name).getName().equals(name)) {
                        continue;
                    }
                    File destination = new File(shaderDirectory, name);
                    if (!destination.isFile()) {
                        InputStream shader = MainMenuShaderManager.class.getResourceAsStream("/" + RESOURCE_PREFIX + name);
                        if (shader != null) {
                            copy(shader, new FileOutputStream(destination));
                        }
                    }
                    copied++;
                }
            } finally {
                reader.close();
            }
            return copied > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean ensureProgram() {
        File source = new File(shaderDirectory, selected);
        if (!source.isFile()) {
            return false;
        }
        if (program != 0 && compiledPath.equals(source.getAbsolutePath()) && compiledModified == source.lastModified()) {
            return true;
        }
        deleteProgram();
        try {
            String fragment = compatibilitySource(read(source));
            int vertex = compile(GL20.GL_VERTEX_SHADER, "#version 120\nvoid main(){ gl_Position = gl_Vertex; }");
            int pixel = compile(GL20.GL_FRAGMENT_SHADER, fragment);
            int linked = GL20.glCreateProgram();
            GL20.glAttachShader(linked, vertex);
            GL20.glAttachShader(linked, pixel);
            GL20.glLinkProgram(linked);
            if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                String error = GL20.glGetProgramInfoLog(linked, 2048);
                GL20.glDeleteProgram(linked);
                GL20.glDeleteShader(vertex);
                GL20.glDeleteShader(pixel);
                throw new IllegalStateException(error);
            }
            GL20.glDeleteShader(vertex);
            GL20.glDeleteShader(pixel);
            program = linked;
            compiledPath = source.getAbsolutePath();
            compiledModified = source.lastModified();
            lastError = "";
            return true;
        } catch (Exception error) {
            lastError = error.getMessage() == null ? "Shader could not compile" : error.getMessage();
            return false;
        }
    }

    private int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String error = GL20.glGetShaderInfoLog(shader, 2048);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException(error);
        }
        return shader;
    }

    private String compatibilitySource(String source) {
        source = source.replaceAll("(?m)^\\s*#version[^\\r\\n]*", "#version 120");
        source = source.replaceAll("(?m)^\\s*#extension[^\\r\\n]*\\r?\\n", "");
        Matcher matcher = OUTPUT.matcher(source);
        if (matcher.find()) {
            String output = matcher.group(1);
            source = matcher.replaceFirst("");
            source = source.replaceAll("\\b" + Pattern.quote(output) + "\\b", "gl_FragColor");
        }
        return source.replace("texture(", "texture2D(");
    }

    private void uniform(String name, float value) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location >= 0) {
            GL20.glUniform1f(location, value);
        }
    }

    private void uniform(String name, float first, float second) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location >= 0) {
            GL20.glUniform2f(location, first, second);
        }
    }

    private void uniform3(String name, float first, float second, float third) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location >= 0) {
            GL20.glUniform3f(location, first, second, third);
        }
    }

    private void uniform4(String name, float first, float second, float third, float fourth) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location >= 0) {
            GL20.glUniform4f(location, first, second, third, fourth);
        }
    }

    private void drawFallback(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glColor3f(0.025F, 0.055F, 0.12F);
            GL11.glVertex2f(0.0F, 0.0F);
            GL11.glVertex2f(width, 0.0F);
            GL11.glColor3f(0.09F, 0.015F, 0.17F);
            GL11.glVertex2f(width, height);
            GL11.glVertex2f(0.0F, height);
            GL11.glEnd();
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopAttrib();
        }
    }

    private void deleteProgram() {
        if (program != 0) {
            GL20.glDeleteProgram(program);
            program = 0;
        }
        compiledPath = "";
        compiledModified = 0L;
        lastShaderFrame = 0L;
    }

    private void ensureRenderTarget(int width, int height) {
        if (renderTarget != null && targetWidth == width && targetHeight == height) return;
        if (renderTarget != null) renderTarget.deleteFramebuffer();
        renderTarget = new Framebuffer(width, height, false);
        // A newly allocated FBO has no cached shader frame, even within the 33ms cap.
        lastShaderFrame = 0L;
        renderTarget.setFramebufferFilter(GL11.GL_LINEAR);
        targetWidth = width;
        targetHeight = height;
    }

    private String read(File file) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        try {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        } finally {
            reader.close();
        }
    }

    private void loadSettings() {
        if (!settingsFile.isFile()) {
            return;
        }
        try {
            BufferedReader reader = new BufferedReader(new FileReader(settingsFile));
            try {
                String enabledLine = reader.readLine();
                String presetLine = reader.readLine();
                if (enabledLine != null) {
                    enabled = Boolean.parseBoolean(enabledLine);
                }
                if (presetLine != null && !presetLine.trim().isEmpty()) {
                    selected = presetLine.trim();
                }
            } finally {
                reader.close();
            }
        } catch (Exception ignored) {
        }
    }

    private void saveSettings() {
        ensureInitialized();
        try {
            Writer writer = new OutputStreamWriter(new FileOutputStream(settingsFile), StandardCharsets.UTF_8);
            try {
                writer.write(Boolean.toString(enabled));
                writer.write("\n");
                writer.write(selected);
                writer.write("\n");
            } finally {
                writer.close();
            }
        } catch (Exception ignored) {
        }
    }

    private void copy(InputStream source, FileOutputStream destination) throws Exception {
        try {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = source.read(buffer)) >= 0) {
                destination.write(buffer, 0, length);
            }
        } finally {
            source.close();
            destination.close();
        }
    }
}
