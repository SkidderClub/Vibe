package dev.vibe.ui.effect;

import java.nio.ByteBuffer;
import org.lwjgl.opengl.*;

/** Copies the bound render target; works with Minecraft renderbuffers and GTA7 supersampling. */
public final class SceneTexture implements AutoCloseable {
    public int color, depth, width, height;
    public void capture(int x, int y, int w, int h, boolean withDepth) {
        if (color == 0 || width != w || height != h || (withDepth && depth == 0)) {
            close(); width = w; height = h;
            color = allocate(w, h, false);
            if (withDepth) depth = allocate(w, h, true);
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, color);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, x, y, w, h);
        if (withDepth) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, depth);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, x, y, w, h);
        }
    }
    public static int allocate(int w, int h, boolean depth) {
        int texture = GL11.glGenTextures(); GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, depth ? GL11.GL_NEAREST : GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, depth ? GL11.GL_NEAREST : GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, depth ? GL14.GL_DEPTH_COMPONENT24 : GL11.GL_RGBA8, w, h, 0,
                depth ? GL11.GL_DEPTH_COMPONENT : GL11.GL_RGBA, depth ? GL11.GL_UNSIGNED_INT : GL11.GL_UNSIGNED_BYTE, (ByteBuffer)null);
        return texture;
    }
    public void close() {
        if (color != 0) GL11.glDeleteTextures(color); if (depth != 0) GL11.glDeleteTextures(depth);
        color = depth = width = height = 0;
    }
}
