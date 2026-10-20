package dev.vibe.model;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.spi.IIORegistry;
import javax.imageio.stream.ImageInputStream;

/** Decodes model textures (PNG, JPEG, WebP) straight to a bounded size, so 4K textures never reach the GPU. */
final class ModelImages {

    static {
        try {
            IIORegistry.getDefaultInstance().registerServiceProvider(new com.luciad.imageio.webp.WebPImageReaderSpi());
        } catch (Throwable ignored) {
            // PNG and JPEG textures keep working without the native WebP decoder.
        }
    }

    private ModelImages() {
    }

    /** Returns an ARGB image no larger than {@code maxSize} on either side, or null if undecodable. */
    static BufferedImage decode(byte[] data, int maxSize) throws IOException {
        if (data == null || data.length == 0) return null;
        ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data));
        if (input == null) return null;
        try {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                ImageReadParam param = reader.getDefaultReadParam();
                // Subsampling while decoding keeps a 8K texture from allocating hundreds of MB.
                int step = 1;
                while (Math.max(width, height) / (step * 2) >= maxSize) step *= 2;
                if (step > 1) param.setSourceSubsampling(step, step, 0, 0);
                BufferedImage image = reader.read(0, param);
                return fit(image, maxSize);
            } finally {
                reader.dispose();
            }
        } finally {
            input.close();
        }
    }

    /** Scales down with bilinear halving steps and converts to INT_ARGB. */
    static BufferedImage fit(BufferedImage image, int maxSize) {
        if (image == null) return null;
        int width = image.getWidth(), height = image.getHeight();
        BufferedImage current = image;
        while (Math.max(width, height) > maxSize) {
            int nextWidth = Math.max(1, Math.max(width / 2, (int) ((long) width * maxSize / Math.max(width, height))));
            int nextHeight = Math.max(1, Math.max(height / 2, (int) ((long) height * maxSize / Math.max(width, height))));
            current = resize(current, nextWidth, nextHeight);
            width = nextWidth;
            height = nextHeight;
        }
        if (current.getType() == BufferedImage.TYPE_INT_ARGB) return current;
        return resize(current, width, height);
    }

    private static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }
}
