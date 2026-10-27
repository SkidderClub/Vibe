package dev.vibe.ui.render.esp;

import dev.vibe.Vibe;
import dev.vibe.module.impl.movement.FreecamModule;
import dev.vibe.module.impl.visual.DamageESPModule;
import dev.vibe.module.impl.visual.DamageESPModule.Marker;
import dev.vibe.ui.NeverLoseFont;
import java.awt.Font;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Cached glyph atlases and bounded billboard draws; glow needs no framebuffer or scene blur. */
public final class DamageEspRenderer implements AutoCloseable {
    private final Minecraft mc;
    private final NeverLoseFont[] fonts = new NeverLoseFont[4];
    private final FloatBuffer view = BufferUtils.createFloatBuffer(16), billboard = BufferUtils.createFloatBuffer(16);
    public DamageEspRenderer() { this(Minecraft.getMinecraft()); }
    public DamageEspRenderer(Minecraft mc) { this.mc = mc; }

    public void render(RenderWorldLastEvent event) {
        DamageESPModule module = Vibe.getInstance().getModuleManager().getModule(DamageESPModule.class);
        if (module == null || !module.isEnabled() || mc.thePlayer == null || module.markers().isEmpty()) return;
        RenderManager manager = mc.getRenderManager();
        render(module, manager.viewerPosX, manager.viewerPosY, manager.viewerPosZ,
                FreecamModule.terrainXHook(mc.thePlayer, manager.viewerPosX),
                FreecamModule.terrainYHook(mc.thePlayer, manager.viewerPosY),
                FreecamModule.terrainZHook(mc.thePlayer, manager.viewerPosZ), System.nanoTime() / 1000000L);
    }

    public void render(DamageESPModule module, double originX, double originY, double originZ,
            double cameraX, double cameraY, double cameraZ, long now) {
        if (!module.isEnabled() || module.markers().isEmpty()) return;
        NeverLoseFont font = font(module.font.getValue());
        int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); view.clear(); GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, view);
        billboard.clear();
        // Transpose the current view rotation, including Freecam's yaw/pitch/roll.
        for (int col = 0; col < 4; col++) for (int row = 0; row < 4; row++)
            billboard.put(col < 3 && row < 3 ? view.get(row * 4 + col) : col == row ? 1 : 0);
        billboard.flip();
        try (FontState ignored = new FontState(module.throughWalls.isEnabled())) {
            if (font != null) font.beginRun();
            try {
                for (Marker marker : module.markers()) {
                    double t = module.progress(marker, now), opacity = module.opacity(t);
                    if (t >= 1 || opacity < .01) continue;
                    double dx = marker.x - cameraX, dy = marker.y - cameraY, dz = marker.z - cameraZ;
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (distance > module.range.getDouble() || distance < .15) continue;
                    float scale = module.scale.getFloat() * .035F;
                    if (module.distanceScale.isEnabled()) scale *= Math.max(1, Math.min(4, distance / 12));
                    if (module.pop.isEnabled()) scale *= .75F + .25F * (1 - Math.pow(1 - Math.min(1, t * 8), 3));
                    GL11.glPushMatrix();
                    try {
                        GL11.glTranslated(marker.x + marker.driftX * module.drift.getDouble() * t - originX,
                                marker.y + module.rise(t) - originY,
                                marker.z + marker.driftZ * module.drift.getDouble() * t - originZ);
                        GL11.glMultMatrix(billboard); GL11.glScalef(scale, -scale, scale);
                        String text = module.text(marker);
                        float x = -(font == null ? mc.fontRendererObj.getStringWidth(text) : font.width(text)) / 2F;
                        float y = font == null ? -mc.fontRendererObj.FONT_HEIGHT / 2F : -font.inkTop(text) - font.inkHeight(text) / 2;
                        int color = module.color(marker, t, now);
                        if (module.glow.isEnabled()) {
                            int glow = module.glowMatchesText.isEnabled() ? color : module.glowColor.getArgb();
                            int soft = alpha(glow, opacity * module.glowStrength.getDouble() * .22);
                            ring(font, text, x, y, module.glowRadius.getFloat(), soft, 8);
                        }
                        if (module.shadow.isEnabled()) draw(font, text, x + .8F, y + .8F, alpha(0xB0000000, opacity));
                        if (module.outline.isEnabled()) ring(font, text, x, y, module.outlineWidth.getFloat(), alpha(module.outlineColor.getArgb(), opacity), 4);
                        draw(font, text, x, y, alpha(color, opacity));
                    } finally { GL11.glPopMatrix(); }
                }
            } finally { if (font != null) font.endRun(); }
        } finally { GL11.glMatrixMode(matrixMode); }
    }
    private void ring(NeverLoseFont font, String text, float x, float y, float radius, int color, int count) {
        if ((color >>> 24) == 0) return;
        for (int i = 0; i < count; i++) {
            double angle = i * Math.PI * 2 / count;
            draw(font, text, x + (float) Math.cos(angle) * radius, y + (float) Math.sin(angle) * radius, color);
        }
    }
    private void draw(NeverLoseFont font, String text, float x, float y, int color) {
        if (font != null) font.draw(text, x, y, color);
        else mc.fontRendererObj.drawString(text, x, y, color, false);
    }
    private NeverLoseFont font(String name) {
        if (name.equals("Minecraft")) return null;
        int index = name.equals("Bold") ? 1 : name.equals("Monospace") ? 2 : name.equals("Serif") ? 3 : 0;
        if (fonts[index] == null) fonts[index] = new NeverLoseFont(index == 2 ? Font.MONOSPACED : index == 3 ? Font.SERIF : Font.SANS_SERIF,
                index == 1 ? Font.BOLD : Font.PLAIN, 24);
        return fonts[index];
    }
    private static int alpha(int color, double opacity) { return color & 0xFFFFFF | (int) Math.round((color >>> 24) * opacity) << 24; }
    @Override public void close() { for (int i = 0; i < fonts.length; i++) if (fonts[i] != null) { fonts[i].close(); fonts[i] = null; } }

    /** Restore driver state AND the Minecraft caches changed by both font renderers. */
    private static final class FontState implements AutoCloseable {
        final int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        final int source = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), destination = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        final int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), destinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        final int alphaFunction = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        final float alphaReference = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        final FloatBuffer color = BufferUtils.createFloatBuffer(16);
        final int texture; final boolean textured;
        FontState(boolean throughWalls) {
            GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color); GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE1); GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
            texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D); textured = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE1); GL11.glDisable(GL11.GL_TEXTURE_2D);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
            GlStateManager.disableTexture2D(); GlStateManager.enableTexture2D();
            GlStateManager.disableBlend(); GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.enableAlpha(); GlStateManager.disableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, .001F);
            GL20.glUseProgram(0); GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_CULL_FACE); GL11.glDepthMask(false);
            if (throughWalls) GL11.glDisable(GL11.GL_DEPTH_TEST); else GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        }
        @Override public void close() {
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
            GlStateManager.bindTexture(0); GlStateManager.bindTexture(texture);
            GlStateManager.disableTexture2D(); GlStateManager.enableTexture2D(); if (!textured) GlStateManager.disableTexture2D();
            GlStateManager.disableBlend(); GlStateManager.enableBlend(); if (!blend) GlStateManager.disableBlend();
            GlStateManager.tryBlendFuncSeparate(1, 0, 1, 0); GlStateManager.tryBlendFuncSeparate(source, destination, sourceAlpha, destinationAlpha);
            GlStateManager.disableAlpha(); GlStateManager.enableAlpha(); if (!alpha) GlStateManager.disableAlpha();
            GlStateManager.alphaFunc(alphaFunction, alphaReference);
            GlStateManager.color(-1, -1, -1, -1); GlStateManager.color(color.get(0), color.get(1), color.get(2), color.get(3));
            GlStateManager.setActiveTexture(active); GL20.glUseProgram(program);
            GL11.glPopClientAttrib(); GL11.glPopAttrib();
        }
    }
}
