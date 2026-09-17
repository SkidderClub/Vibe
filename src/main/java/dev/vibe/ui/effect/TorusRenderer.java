/* Copyright (c) 2024. Schizoid. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * ModuleToggleableTorus.kt port, modified for Vibe/Forge 1.8.9 on 2026-09-09.
 * Retains the complete torus geometry, normals, cubic growth and reflection shader.
 */
package dev.vibe.ui.effect;

import dev.vibe.module.impl.HitmarkerModule;
import java.nio.IntBuffer;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class TorusRenderer implements AutoCloseable {
    private final SceneTexture scene = new SceneTexture();
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private EffectProgram reflection;
    private boolean failed;
    public boolean hasFailed() { return failed; }
    public void render(HitmarkerModule module) {
        List<HitmarkerModule.Marker> markers = module.getTorusMarkers();
        if (markers.isEmpty() || failed || !GLContext.getCapabilities().OpenGL20) return;
        boolean active = false;
        long now = System.currentTimeMillis();
        for (HitmarkerModule.Marker marker : markers) if (marker.torus && now - marker.created < module.torusLifetime.getInt()) { active = true; break; }
        if (!active) return;
        Minecraft mc = Minecraft.getMinecraft();
        try (EffectState state = new EffectState()) {
            if (reflection == null) reflection = new EffectProgram("Reflection.vert", "Reflection.frag");
            viewport.clear(); GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
            scene.capture(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3), false);
            reflection.bind(); FogRenderer.texture(0, scene.color); reflection.integer("Tex0", 0);
            reflection.scalar("Freq", module.torusNoise.getFloat()/100);
            // Reflection.vert supplies view-space positions and normals: camera is the origin.
            reflection.vec3("CamPos", 0, 0, 0);
            GL11.glDisable(GL11.GL_BLEND);
            if (module.torusDepth.isEnabled()) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
            int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE); GL11.glMatrixMode(GL11.GL_MODELVIEW);
            try {
                for (HitmarkerModule.Marker marker : markers) {
                    if (!marker.torus) continue;
                    float age = (System.currentTimeMillis()-marker.created)/module.torusLifetime.getFloat();
                    if (age < 0 || age >= 1) continue;
                    GL11.glPushMatrix();
                    try {
                        GL11.glTranslated(marker.x-mc.getRenderManager().viewerPosX, marker.y-mc.getRenderManager().viewerPosY,
                                marker.z-mc.getRenderManager().viewerPosZ);
                        GL11.glRotated(Math.toDegrees(Math.atan2(marker.lookX, marker.lookZ)), 0, 1, 0);
                        GL11.glRotated(-Math.toDegrees(Math.atan2(marker.lookY, Math.hypot(marker.lookX, marker.lookZ))), 1, 0, 0);
                        drawMesh(age, module.torusScale.getFloat());
                    } finally { GL11.glPopMatrix(); }
                }
            } finally { GL11.glMatrixMode(mode); }
        } catch (Exception e) { failed = true; close(); LogManager.getLogger("Vibe").warn("Torus renderer unavailable", e); }
    }
    /** Schizoid's 10 tube slices x 30 ring loops, including per-vertex surface normals. */
    public static void drawMesh(float age, float scale) {
        float outer = (.6f + 2*(1-(1-age)*(1-age)*(1-age)))*scale;
        float inner = (.4f - .4f*age*age*age)*scale;
        GL11.glBegin(GL11.GL_QUADS);
        for (int i=0; i<10; i++) {
            double theta = 2*Math.PI*i/10, nextTheta = 2*Math.PI*(i+1)/10;
            for (int j=0; j<30; j++) {
                double phi = 2*Math.PI*j/30, nextPhi = 2*Math.PI*(j+1)/30;
                vertex(outer, inner, theta, phi); vertex(outer, inner, nextTheta, phi);
                vertex(outer, inner, nextTheta, nextPhi); vertex(outer, inner, theta, nextPhi);
            }
        }
        GL11.glEnd();
    }
    private static void vertex(float outer, float inner, double theta, double phi) {
        double cos = Math.cos(theta), sin = Math.sin(theta), cp = Math.cos(phi), sp = Math.sin(phi);
        GL11.glNormal3d(cos*cp, cos*sp, sin);
        GL11.glVertex3d((outer+inner*cos)*cp, (outer+inner*cos)*sp, inner*sin);
    }
    public void close() { scene.close(); if (reflection != null) reflection.close(); reflection = null; }
}
