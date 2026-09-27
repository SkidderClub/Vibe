package dev.vibe.game.gta8;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

/** Static indexed triangle mesh in a VBO/IBO pair, drawn with generic vertex attributes. */
final class Gta8Mesh {
    int vbo, ibo, count;
    final double minX, minY, minZ, maxX, maxY, maxZ, cx, cy, cz, radius;

    private Gta8Mesh(Gta8MeshBuilder b) {
        count = b.indexCount;
        minX = b.minX; minY = b.minY; minZ = b.minZ; maxX = b.maxX; maxY = b.maxY; maxZ = b.maxZ;
        cx = (minX + maxX) / 2; cy = (minY + maxY) / 2; cz = (minZ + maxZ) / 2;
        radius = .5 * Math.sqrt((maxX - minX) * (maxX - minX) + (maxY - minY) * (maxY - minY) + (maxZ - minZ) * (maxZ - minZ));
        vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, b.vertexBuffer(), GL15.GL_STATIC_DRAW);
        ibo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ibo);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, b.indexBuffer(), GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    static Gta8Mesh upload(Gta8MeshBuilder builder) { return builder.isEmpty() ? null : new Gta8Mesh(builder); }

    static void enableAttributes() {
        for (int i = 0; i <= 4; i++) GL20.glEnableVertexAttribArray(i);
    }
    static void disableAttributes() {
        for (int i = 0; i <= 4; i++) GL20.glDisableVertexAttribArray(i);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }
    /** Attribute arrays must be enabled with {@link #enableAttributes()}. */
    void draw() {
        if (vbo == 0 || count == 0) return;
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ibo);
        int s = Gta8MeshBuilder.STRIDE;
        GL20.glVertexAttribPointer(Gta8Shader.POSITION, 3, GL11.GL_FLOAT, false, s, 0);
        GL20.glVertexAttribPointer(Gta8Shader.NORMAL, 3, GL11.GL_BYTE, true, s, 12);
        GL20.glVertexAttribPointer(Gta8Shader.COLOR, 4, GL11.GL_UNSIGNED_BYTE, true, s, 16);
        GL20.glVertexAttribPointer(Gta8Shader.UV, 2, GL11.GL_FLOAT, false, s, 20);
        GL20.glVertexAttribPointer(Gta8Shader.MATERIAL, 4, GL11.GL_UNSIGNED_BYTE, false, s, 28);
        GL11.glDrawElements(GL11.GL_TRIANGLES, count, GL11.GL_UNSIGNED_INT, 0);
    }
    void close() {
        if (vbo != 0) GL15.glDeleteBuffers(vbo);
        if (ibo != 0) GL15.glDeleteBuffers(ibo);
        vbo = ibo = count = 0;
    }
}
