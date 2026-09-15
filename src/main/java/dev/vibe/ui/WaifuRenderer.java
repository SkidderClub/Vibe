package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.impl.WaifuModule;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.spi.IIORegistry;
import javax.imageio.stream.ImageInputStream;
import com.luciad.imageio.webp.WebPImageReaderSpi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiDispenser;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** Shared Waifu renderer for ClickGUI and supported vanilla screens. */
public final class WaifuRenderer {
    static {
        try {
            IIORegistry.getDefaultInstance().registerServiceProvider(new WebPImageReaderSpi());
            ImageIO.scanForPlugins();
        } catch (Throwable ignored) {
            // PNG/JPEG loading must continue even when native WebP support is unavailable.
        }
    }

    private static ResourceLocation texture;
    private static String loadedPath;
    private static long loadedModified;
    private static int loadedWidth;
    private static int loadedHeight;
    private static float offsetX;
    private static float offsetY;
    private static float velocityX;
    private static float velocityY;
    private static boolean dragging;
    private static float grabX;
    private static float grabY;
    private static long lastPhysics;

    private WaifuRenderer() {
    }

    public static void draw(GuiScreen screen) {
        WaifuModule module = Vibe.getInstance().getModuleManager().getModule(WaifuModule.class);
        if (module == null || !module.isEnabled() || !module.shouldRender(screen)) {
            return;
        }
        File selected = module.getSelectedFile();
        if (selected == null || "None".equalsIgnoreCase(module.getSelected().getValue())) {
            return;
        }
        String selectedPath = selected.getAbsolutePath();
        if (!selectedPath.equals(loadedPath) || selected.lastModified() != loadedModified) {
            texture = null;
            try {
                BufferedImage image = readImage(selected);
                if (image == null) {
                    loadedPath = null;
                    return;
                }
                texture = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation(
                        "vibe_waifu", new DynamicTexture(image));
                loadedPath = selectedPath;
                loadedModified = selected.lastModified();
                loadedWidth = image.getWidth();
                loadedHeight = image.getHeight();
            } catch (Exception ignored) {
                loadedPath = null;
                loadedModified = 0L;
                return;
            }
        }
        if (texture == null) return;
        int drawWidth = Math.max(1, Math.round(loadedWidth * module.getScale().getFloat()));
        int drawHeight = Math.max(1, Math.round(loadedHeight * module.getScale().getFloat()));
        updatePhysics(screen, module, drawWidth, drawHeight);
        int left = Math.round(screen.width - drawWidth - 8 + offsetX);
        int top = Math.round(screen.height - drawHeight - 8 + offsetY);
        GuiRenderState.prepare(false);
        try {
            // GUI renderers before this one (particles, blur, item lighting)
            // may leave a tint active. Dynamic textures must always start
            // neutral so the source image is never recoloured.
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
            Gui.drawModalRectWithCustomSizedTexture(left, top, 0, 0, drawWidth, drawHeight, drawWidth, drawHeight);
        } finally {
            GuiRenderState.prepare(false);
        }
    }

    public static void handleMouse(GuiScreen screen) {
        WaifuModule module = Vibe.getInstance().getModuleManager().getModule(WaifuModule.class);
        if (module == null || !module.isEnabled() || !module.getGravity().isEnabled() || !module.shouldRender(screen)
                || texture == null || !Mouse.getEventButtonState()) return;
        int button = Mouse.getEventButton();
        if (button != 0) return;
        int drawWidth = Math.max(1, Math.round(loadedWidth * module.getScale().getFloat()));
        int drawHeight = Math.max(1, Math.round(loadedHeight * module.getScale().getFloat()));
        int mouseX = Mouse.getEventX() * screen.width / Minecraft.getMinecraft().displayWidth;
        int mouseY = screen.height - Mouse.getEventY() * screen.height / Minecraft.getMinecraft().displayHeight - 1;
        int left = Math.round(screen.width - drawWidth - 8 + offsetX);
        int top = Math.round(screen.height - drawHeight - 8 + offsetY);
        if (mouseX >= left && mouseX < left + drawWidth && mouseY >= top && mouseY < top + drawHeight) {
            dragging = true;
            grabX = mouseX - left;
            grabY = mouseY - top;
            velocityX = 0.0F;
            velocityY = 0.0F;
        }
    }

    private static void updatePhysics(GuiScreen screen, WaifuModule module, int drawWidth, int drawHeight) {
        long now = System.currentTimeMillis();
        float delta = lastPhysics == 0L ? 1.0F : Math.min(3.0F, (now - lastPhysics) / 16.67F);
        lastPhysics = now;
        if (!module.getGravity().isEnabled()) {
            offsetX = 0.0F;
            offsetY = 0.0F;
            velocityX = 0.0F;
            velocityY = 0.0F;
            dragging = false;
            return;
        }
        int mouseX = Mouse.getX() * screen.width / Minecraft.getMinecraft().displayWidth;
        int mouseY = screen.height - Mouse.getY() * screen.height / Minecraft.getMinecraft().displayHeight - 1;
        if (dragging && Mouse.isButtonDown(0)) {
            float targetX = mouseX - grabX - (screen.width - drawWidth - 8);
            float targetY = mouseY - grabY - (screen.height - drawHeight - 8);
            // A mouse drag accelerates the image toward the cursor instead
            // of replacing its velocity. Releasing it therefore preserves a
            // natural horizontal throw as well as the vertical fall.
            velocityX += (targetX - offsetX) * 0.18F * delta;
            velocityY += (targetY - offsetY) * 0.18F * delta;
            velocityX = Math.max(-24.0F, Math.min(24.0F, velocityX));
            velocityY = Math.max(-24.0F, Math.min(24.0F, velocityY));
            offsetX += velocityX * delta;
            offsetY += velocityY * delta;
        } else {
            dragging = false;
            velocityY += 0.52F * delta;
            // Subtle horizontal acceleration prevents a released image from
            // becoming permanently vertical while still leaving thrown
            // momentum dominant.
            velocityX += (float) Math.sin(now / 640.0D) * 0.018F * delta;
            offsetX += velocityX * delta;
            offsetY += velocityY * delta;
            velocityX *= 0.998F;
        }
        // Offsets are relative to the bottom-right resting point. Deriving
        // these from the actual texture size keeps all four image edges on
        // screen; the old bounds used screen-only values and allowed it to
        // fall below the bottom edge.
        float minX = drawWidth + 8.0F - screen.width;
        float maxX = 8.0F;
        float minY = drawHeight + 8.0F - screen.height;
        float maxY = 8.0F;
        if (offsetX < minX || offsetX > maxX) {
            offsetX = Math.max(minX, Math.min(maxX, offsetX));
            velocityX *= -0.68F;
        }
        if (offsetY < minY || offsetY > maxY) {
            offsetY = Math.max(minY, Math.min(maxY, offsetY));
            velocityY *= -0.52F;
        }
    }

    /** Explicitly creates the bundled reader for WebP instead of relying on ImageIO discovery. */
    private static BufferedImage readImage(File file) throws Exception {
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        if (!name.endsWith(".webp")) {
            return ImageIO.read(file);
        }
        ImageReader reader = null;
        ImageInputStream stream = null;
        try {
            stream = ImageIO.createImageInputStream(file);
            if (stream == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReadersBySuffix("webp");
            reader = readers.hasNext() ? readers.next() : new WebPImageReaderSpi().createReaderInstance();
            reader.setInput(stream, true, true);
            return reader.read(0);
        } finally {
            if (reader != null) {
                reader.dispose();
            }
            if (stream != null) {
                stream.close();
            }
        }
    }
}
