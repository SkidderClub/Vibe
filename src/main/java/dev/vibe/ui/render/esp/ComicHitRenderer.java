package dev.vibe.ui.render.esp;

import dev.vibe.module.impl.visual.HitmarkerModule;
import dev.vibe.module.impl.visual.HitmarkerModule.ComicWord;
import dev.vibe.ui.NeverLoseFont;
import dev.vibe.ui.RenderUtils;
import java.awt.Font;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Short comic bursts anchored to the impact, with a pop, drift and fade. */
public final class ComicHitRenderer {
    private final NeverLoseFont font = new NeverLoseFont(Font.SANS_SERIF, Font.BOLD | Font.ITALIC, 28);
    private final FloatBuffer view = BufferUtils.createFloatBuffer(16), facing = BufferUtils.createFloatBuffer(16);

    public void render(HitmarkerModule module) {
        long now = System.currentTimeMillis();
        Iterable<ComicWord> words = module.comicWords(now);
        if (!words.iterator().hasNext()) return;
        RenderManager manager = Minecraft.getMinecraft().getRenderManager();
        view.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, view);
        facing.clear();
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) facing.put(col < 3 && row < 3 ? view.get(row * 4 + col) : col == row ? 1 : 0);
        }
        facing.flip();
        try (DamageEspRenderer.FontState ignored = new DamageEspRenderer.FontState(module.comicWalls.isEnabled())) {
            font.beginRun();
            try {
                for (ComicWord word : words) draw(module, word, manager, now);
            } finally { font.endRun(); }
        }
    }

    private void draw(HitmarkerModule module, ComicWord word, RenderManager manager, long now) {
        float age = Math.max(0, (now - word.created) / module.comicDuration.getFloat());
        float pop = Math.min(1, age * 8);
        float scale = (.55F + .65F * (1 - (1 - pop) * (1 - pop)) - .2F * age) * .033F * module.comicSize.getFloat();
        float fade = Math.min(1, (1 - age) * 2);
        int color = module.comicColor.getArgb();
        int alpha = Math.round((color >>> 24) * fade);
        if (alpha < 4) return;
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(word.x - manager.viewerPosX, word.y - manager.viewerPosY, word.z - manager.viewerPosZ);
            GL11.glMultMatrix(facing);
            GL11.glTranslated(word.spread * (1 + age), .2 + age * .55 + Math.abs(word.spread) * .45, 0);
            GL11.glRotatef(word.tilt, 0, 0, 1);
            GL11.glScalef(scale, -scale, scale);
            float x = -font.width(word.text) / 2F, y = -font.inkTop(word.text) - font.inkHeight(word.text) / 2F;
            int edge = RenderUtils.alpha(0xFF271625, alpha);
            font.draw(word.text, x + 1, y + 1.5F, edge);
            for (int i = 0; i < 4; i++) {
                double angle = i * Math.PI / 2;
                font.draw(word.text, x + (float) Math.cos(angle), y + (float) Math.sin(angle), edge);
            }
            font.draw(word.text, x, y, RenderUtils.alpha(color, alpha));
        } finally { GL11.glPopMatrix(); }
    }
}
