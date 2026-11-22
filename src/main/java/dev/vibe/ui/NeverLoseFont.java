package dev.vibe.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import dev.vibe.language.LanguageManager;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;

/** Lazy glyph pages, rasterized at twice the GUI resolution. No per-label textures. */
public final class NeverLoseFont {
    public static final NeverLoseFont REGULAR = new NeverLoseFont(Font.PLAIN);
    public static final NeverLoseFont BOLD = new NeverLoseFont(Font.BOLD);
    private final int CELL, SIZE;
    private final Font font;
    private Font activeFont;
    private FontMetrics metrics;
    private String activeScript = "";
    private final Map<Integer, Page> pages = new HashMap<Integer, Page>();
    private final Map<Character,float[]> inkMetrics=new HashMap<Character,float[]>();
    /** Bundled Noto CJK is the only face guaranteed to exist on Android launchers. */
    private static Font bundledCjk;
    private static boolean bundledCjkLoaded;
    private static java.util.List<Font> systemFaces;
    private static final String[] SYSTEM_FACES = {
            "Noto Sans CJK SC", "Noto Sans SC", "Microsoft YaHei", "PingFang SC", "Hiragino Sans GB",
            "Yu Gothic", "Meiryo", "Malgun Gothic",
            "Noto Sans Devanagari", "Nirmala UI", "Mangal",
            "Noto Sans Bengali", "Vrinda",
            "Noto Sans Gurmukhi", "Raavi",
            "Noto Sans Gujarati", "Shruti",
            "Noto Sans Tamil", "Latha",
            "Noto Sans Telugu", "Gautami",
            "Noto Sans Kannada", "Tunga",
            "Noto Sans Malayalam", "Kartika",
            "Noto Sans Sinhala", "Iskoola Pota",
            "Noto Sans Thai", "Leelawadee UI", "Leelawadee",
            "Noto Sans Arabic", "Arabic Typesetting",
            "Noto Sans Hebrew",
            "Noto Sans Ethiopic", "Nyala",
            "Noto Sans Myanmar", "Myanmar Text",
            "Noto Sans Khmer", "Khmer UI",
            "Noto Sans Lao", "Lao UI"
    };
    /** advance() per character, by high byte; NaN marks a value not computed for the current pages yet. */
    private final float[][] advances = new float[256][];
    private boolean inRun, runAlphaKnown, runAlpha;

    private NeverLoseFont(int style) {
        this(style, 20);
    }

    public NeverLoseFont(int style, int rasterSize) {
        this(Font.SANS_SERIF, style, rasterSize);
    }

    public NeverLoseFont(String family, int style, int rasterSize) {
        this(new Font(family, style, rasterSize));
    }

    public NeverLoseFont(Font face) {
        font = face;
        CELL = face.getSize() > 26 ? 64 : 32; SIZE = CELL * 16;
        activeFont = font;
        metrics = metrics(font);
    }

    private static FontMetrics metrics(Font target) {
        BufferedImage sample = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sample.createGraphics();
        g.setFont(target);
        FontMetrics result = g.getFontMetrics();
        g.dispose();
        return result;
    }

    public float width(String text) {
        prepareScript();
        text = LanguageManager.scriptFontText(text);
        float width = 0;
        for (int i = 0; i < text.length(); i++) width += advance(text.charAt(i));
        return width;
    }
    public float inkTop(String text){return inkBounds(text)[0];}
    public float inkHeight(String text){float[] bounds=inkBounds(text);return Math.max(1,bounds[1]-bounds[0]);}
    private float[] inkBounds(String text){
        prepareScript();
        text = LanguageManager.scriptFontText(text);
        float top=Float.POSITIVE_INFINITY,bottom=Float.NEGATIVE_INFINITY;
        for(int i=0;i<text.length();i++){
            char ch=text.charAt(i);if(Character.isWhitespace(ch)||Character.isISOControl(ch))continue;
            float[] bounds=inkMetrics.get(ch);
            if(bounds==null){
                Font selected=fontFor(ch);
                java.awt.geom.Rectangle2D ink=selected.createGlyphVector(new java.awt.font.FontRenderContext(null,true,false),new char[]{ch}).getVisualBounds();
                bounds=new float[]{(metrics.getAscent()+(float)ink.getMinY())*.5F,(metrics.getAscent()+(float)ink.getMaxY())*.5F};inkMetrics.put(ch,bounds);
            }
            top=Math.min(top,bounds[0]);bottom=Math.max(bottom,bounds[1]);
        }
        return top==Float.POSITIVE_INFINITY?new float[]{0,1}:new float[]{top,bottom};
    }
    public void close(){clearPages();}

    public String fit(String text, float available) {
        prepareScript();
        text = LanguageManager.scriptFontText(text);
        if (width(text) <= available) return text;
        float used = width("...");
        if (used > available) return "";
        int end = 0;
        while (end < text.length() && used + advance(text.charAt(end)) <= available) used += advance(text.charAt(end++));
        return text.substring(0, end) + "...";
    }

    private float advance(char c) {
        float[] row = advances[c >> 8];
        if (row == null) { row = new float[256]; java.util.Arrays.fill(row, Float.NaN); advances[c >> 8] = row; }
        float value = row[c & 255];
        if (value != value) {
            value = activeFont.canDisplay(c) ? metrics.charWidth(c) * .5F : page(c >> 8).advances[c & 255];
            row[c & 255] = value;
        }
        return value;
    }

    /**
     * Starts a run of draw calls with no other alpha-test changes in between. draw() leaves the
     * alpha test as it found it, so the first draw's driver query answers for the whole run.
     */
    public void beginRun() { inRun = true; runAlphaKnown = false; }
    public void endRun() { inRun = false; runAlphaKnown = false; }

    public void draw(String text, float x, float y, int color) {
        if(dev.vibe.hud.NameProtectRenderer.drawCustom(this,text,x,y,color))return;
        prepareScript();
        text = LanguageManager.scriptFontText(text);
        if (text.isEmpty()) return;
        GlStateManager.enableTexture2D(); GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        boolean alpha;
        if (inRun && runAlphaKnown) alpha = runAlpha;
        else {
            alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
            if (inRun) { runAlpha = alpha; runAlphaKnown = true; }
        }
        GlStateManager.disableAlpha();
        GlStateManager.color((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, (color >>> 24) / 255F);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Page page = page(c >> 8);
            GlStateManager.bindTexture(page.texture.getGlTextureId());
            int index = c & 255;
            float u = (index % 16) / 16F, v = (index / 16) / 16F;
            float left = x - 1, top = y - 1;
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(u, v); GL11.glVertex2f(left, top);
            GL11.glTexCoord2f(u, v + 1 / 16F); GL11.glVertex2f(left, top + CELL / 2F);
            GL11.glTexCoord2f(u + 1 / 16F, v + 1 / 16F); GL11.glVertex2f(left + CELL / 2F, top + CELL / 2F);
            GL11.glTexCoord2f(u + 1 / 16F, v); GL11.glVertex2f(left + CELL / 2F, top);
            GL11.glEnd();
            x += page.advances[index];
        }
        GlStateManager.color(1, 1, 1, 1);
        if (alpha) GlStateManager.enableAlpha();
    }

    private Page page(int number) {
        Page page = pages.get(number);
        if (page != null) return page;
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        float[] advances = new float[256];
        for (int i = 0; i < 256; i++) {
            char c = (char) ((number << 8) | i);
            Font selected = fontFor(c);
            g.setFont(selected);
            advances[i] = g.getFontMetrics().charWidth(c) * .5F;
            int cellX = (i % 16) * CELL, cellY = (i / 16) * CELL;
            // Keep a transparent gutter on every side. Descenders must not spill
            // into the next cell, and linear filtering must only sample padding
            // at cell boundaries (not a fragment of the neighbouring glyph).
            g.setClip(cellX + 1, cellY + 1, CELL - 2, CELL - 2);
            if (!Character.isISOControl(c)) g.drawString(String.valueOf(c), cellX + 2, cellY + 2 + metrics.getAscent());
        }
        g.dispose();
        DynamicTexture texture = new DynamicTexture(image);
        texture.setBlurMipmap(true, false);
        page = new Page(texture, advances); pages.put(number, page);
        return page;
    }

    /**
     * Android launchers ship no CJK fallback for logical fonts, so a missing
     * glyph would otherwise draw as an empty box. Try the bundled Noto CJK
     * first, then installed script faces, and finally the platform dialog face.
     */
    public static Font forText(Font base, String text) {
        if (text == null || text.isEmpty() || base.canDisplayUpTo(text) < 0) return base;
        Font cjk = bundledCjk(base);
        if (cjk != null && cjk.canDisplayUpTo(text) < 0) return cjk;
        for (Font face : systemFaces()) {
            Font candidate = face.deriveFont(base.getStyle(), base.getSize2D());
            if (candidate.canDisplayUpTo(text) < 0) return candidate;
        }
        Font dialog = dialogFace(base);
        if (dialog.canDisplayUpTo(text) < 0) return dialog;
        return cjk != null ? cjk : dialog;
    }

    private Font fontFor(char c) {
        if (activeFont.canDisplay(c)) return activeFont;
        Font cjk = bundledCjk(activeFont);
        if (cjk != null && cjk.canDisplay(c)) return cjk;
        for (Font face : systemFaces()) {
            Font candidate = face.deriveFont(activeFont.getStyle(), activeFont.getSize2D());
            if (candidate.canDisplay(c)) return candidate;
        }
        Font dialog = dialogFace(activeFont);
        return dialog.canDisplay(c) ? dialog : cjk != null ? cjk : activeFont;
    }

    private static synchronized Font bundledCjk(Font base) {
        if (!bundledCjkLoaded) {
            bundledCjkLoaded = true;
            try (InputStream input = NeverLoseFont.class.getResourceAsStream("/assets/vibe/fonts/NotoSansCJKsc-Regular.otf")) {
                if (input != null) bundledCjk = Font.createFont(Font.TRUETYPE_FONT, input);
            } catch (Exception ignored) { /* Script faces below still cover Latin and Cyrillic. */ }
        }
        return bundledCjk == null ? null : bundledCjk.deriveFont(base.getStyle(), base.getSize2D());
    }

    private static synchronized java.util.List<Font> systemFaces() {
        if (systemFaces == null) {
            java.util.List<Font> faces = new java.util.ArrayList<Font>();
            java.util.Set<String> seen = new java.util.HashSet<String>();
            for (String family : SYSTEM_FACES) {
                Font candidate = new Font(family, Font.PLAIN, 12);
                // A missing family silently maps to Dialog; skip those so the chain stays predictable.
                if (!family.equalsIgnoreCase(candidate.getFamily())) continue;
                if (seen.add(candidate.getFamily().toLowerCase(java.util.Locale.ROOT))) faces.add(candidate);
            }
            systemFaces = java.util.Collections.unmodifiableList(faces);
        }
        return systemFaces;
    }

    private static Font dialogFace(Font base) {
        return new Font(Font.DIALOG, base.getStyle(), 12).deriveFont(base.getSize2D());
    }

    private void prepareScript() {
        String language = LanguageManager.selectedLanguage();
        String next = LanguageManager.usesScriptFont(language) ? language : "";
        if (next.equals(activeScript)) return;
        clearPages();
        inkMetrics.clear();
        activeScript = next;
        activeFont = next.isEmpty() ? font : loadScriptFont(next);
        metrics = metrics(activeFont);
    }

    private Font loadScriptFont(String language) {
        String resource = "Enchantment Table".equals(language)
                ? "/assets/vibe/fonts/StandardGalactic-Regular.ttf"
                : "/assets/vibe/fonts/Aurebesh-Rodian.otf";
        try (InputStream input = NeverLoseFont.class.getResourceAsStream(resource)) {
            if (input != null) return Font.createFont(Font.TRUETYPE_FONT, input)
                    .deriveFont(font.getStyle(), font.getSize2D());
        } catch (Exception ignored) { /* Unicode fallback remains usable if a bundled font cannot load. */ }
        return font;
    }

    private void clearPages() {
        for (Page page : pages.values()) page.texture.deleteGlTexture();
        pages.clear();
        java.util.Arrays.fill(advances, null);
    }

    private static final class Page {
        final DynamicTexture texture;
        final float[] advances;
        Page(DynamicTexture texture, float[] advances) { this.texture = texture; this.advances = advances; }
    }
}
