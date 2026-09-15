package dev.vibe.ui;

import dev.vibe.language.LanguageManager;
import imgui.ImFont;
import imgui.ImFontAtlas;
import imgui.ImFontConfig;
import imgui.ImFontGlyph;
import imgui.type.ImInt;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

/** Packs Minecraft's bitmap glyphs into ImGui's atlas, including localized text. */
final class AugustusMinecraftFont {
    private AugustusMinecraftFont() { }

    static ByteBuffer build(ImFontAtlas atlas, ImInt width, ImInt height) throws IOException {
        atlas.clear();
        ImFontConfig config = new ImFontConfig();
        ImFont font;
        try {
            config.setSizePixels(16);
            config.setOversampleH(1);
            config.setOversampleV(1);
            config.setPixelSnapH(true);
            config.setName("Minecraft");
            font = atlas.addFontDefault(config);
        } finally { config.destroy(); }

        BufferedImage ascii = readImage("textures/font/ascii.png");
        byte[] sizes = new byte[65536];
        try (DataInputStream input = new DataInputStream(open("font/glyph_sizes.bin"))) {
            input.readFully(sizes);
        }
        TreeSet<Character> characters = new TreeSet<Character>();
        for (char c = 32; c < 256; c++) characters.add(c);
        for (char c = 0x400; c < 0x530; c++) characters.add(c);
        for (char c : LanguageManager.interfaceGlyphs().toCharArray()) if (c >= 32) characters.add(c);

        Map<Integer, BufferedImage> pages = new HashMap<Integer, BufferedImage>();
        Map<Character, BufferedImage> glyphs = new LinkedHashMap<Character, BufferedImage>();
        for (char c : characters) {
            BufferedImage glyph;
            float advance;
            if (c < 127) {
                int cell = ascii.getWidth() / 16;
                int sourceX = (c % 16) * cell, sourceY = (c / 16) * cell;
                int ink = cell;
                while (ink > 0 && emptyColumn(ascii, sourceX + ink - 1, sourceY, cell)) ink--;
                int pixels = Math.max(1, Math.round(ink * 16F / cell));
                glyph = new BufferedImage(pixels, 16, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < 16; y++) for (int x = 0; x < pixels; x++) {
                    glyph.setRGB(x, y, ascii.getRGB(sourceX + Math.min(cell - 1, x * cell / 16), sourceY + y * cell / 16));
                }
                advance = c == ' ' ? 8 : pixels + 2;
            } else {
                int size = sizes[c] & 255;
                if (size == 0) continue;
                int page = c / 256;
                if (!pages.containsKey(page)) {
                    try { pages.put(page, readImage(String.format(java.util.Locale.ROOT, "textures/font/unicode_page_%02x.png", page))); }
                    catch (IOException missingPage) { pages.put(page, null); }
                }
                BufferedImage source = pages.get(page);
                if (source == null) continue;
                int start = size >>> 4, end = (size & 15) + 1;
                if (end <= start) continue;
                int cell = source.getWidth() / 16;
                glyph = new BufferedImage(end - start, 16, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < 16; y++) for (int x = 0; x < end - start; x++) {
                    glyph.setRGB(x, y, source.getRGB((c % 16) * cell + (start + x) * cell / 16,
                            ((c % 256) / 16) * cell + y * cell / 16));
                }
                advance = end - start + 2;
            }
            atlas.addCustomRectFontGlyph(font, (short) c, glyph.getWidth(), glyph.getHeight(), advance);
            glyphs.put(c, glyph);
        }

        ByteBuffer pixels = atlas.getTexDataAsRGBA32(width, height);
        for (Map.Entry<Character, BufferedImage> entry : glyphs.entrySet()) {
            ImFontGlyph packed = font.findGlyphNoFallback(entry.getKey());
            int left = Math.round(packed.getU0() * width.get()), top = Math.round(packed.getV0() * height.get());
            BufferedImage glyph = entry.getValue();
            for (int y = 0; y < glyph.getHeight(); y++) for (int x = 0; x < glyph.getWidth(); x++) {
                int argb = glyph.getRGB(x, y), offset = ((top + y) * width.get() + left + x) * 4;
                pixels.put(offset, (byte) 255);
                pixels.put(offset + 1, (byte) 255);
                pixels.put(offset + 2, (byte) 255);
                pixels.put(offset + 3, (byte) (argb >>> 24));
            }
        }
        return pixels;
    }

    private static boolean emptyColumn(BufferedImage image, int x, int top, int height) {
        for (int y = top; y < top + height; y++) if ((image.getRGB(x, y) >>> 24) > 16) return false;
        return true;
    }

    private static BufferedImage readImage(String path) throws IOException {
        try (InputStream input = open(path)) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) throw new IOException("Invalid Minecraft font texture: " + path);
            return image;
        }
    }

    private static InputStream open(String path) throws IOException {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.getResourceManager() != null) {
            return mc.getResourceManager().getResource(new ResourceLocation(path)).getInputStream();
        }
        InputStream input = AugustusMinecraftFont.class.getResourceAsStream("/assets/minecraft/" + path);
        if (input == null) throw new IOException("Missing Minecraft font resource: " + path);
        return input;
    }
}
