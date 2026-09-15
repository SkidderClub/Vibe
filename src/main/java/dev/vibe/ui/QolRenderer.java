package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.impl.QolModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;

/** Replaces only vanilla's dim menu backdrop with the current world frame. */
public final class QolRenderer {

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private Framebuffer worldFrame;
    private int width = -1;
    private int height = -1;
    private boolean captured;

    /**
     * Save the current world frame before GuiScreen draws its dark gradient.
     * RenderGameOverlay's ALL event still runs while a GUI is open, and it is
     * dispatched before the screen is painted.  Capturing there every frame
     * keeps inventory/container backgrounds live instead of freezing them on
     * the frame from which the GUI was opened.
     */
    public void capture() {
        QolModule module = Vibe.getInstance().getModuleManager().getModule(QolModule.class);
        if (minecraft.displayWidth <= 0 || minecraft.displayHeight <= 0 || module == null || !module.isEnabled()
                || !module.getFeatures().isSelected("Hide Black Background")) {
            captured = false;
            return;
        }
        try {
            ensureFramebuffer();
            // A failed/late copy must never expose a white clear buffer.
            // Transparent black is the safe initial state while the current
            // world frame is copied below.
            GlStateManager.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
            worldFrame.framebufferClear();
            // Framebuffer#framebufferClear returns to the default target.
            // Bind the snapshot again before drawing the saved world image.
            worldFrame.bindFramebuffer(true);
            // A copied background is an opaque replacement, never another
            // translucent GUI layer. Leaving blend enabled mixed the old
            // dim-pass black into the snapshot and later tinted Waifu and
            // particle draws gray on some inventory frames.
            GlStateManager.disableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            minecraft.getFramebuffer().framebufferRenderExt(worldFrame.framebufferWidth, worldFrame.framebufferHeight, false);
            captured = true;
        } catch (Exception ignored) {
            captured = false;
        } finally {
            restoreGuiProjection();
        }
    }

    /** Called after vanilla has drawn its dim background but before content. */
    public void redrawBehind(GuiScreen screen) {
        QolModule module = Vibe.getInstance().getModuleManager().getModule(QolModule.class);
        if (!captured || worldFrame == null || module == null || !module.isEnabled()
                || !module.getFeatures().isSelected("Hide Black Background") || !shouldHide(module, screen)) {
            return;
        }
        try {
            // Copy the saved world as an exact opaque backdrop; later GUI
            // content is drawn only after normal alpha blending is restored.
            minecraft.getFramebuffer().bindFramebuffer(true);
            GlStateManager.disableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            worldFrame.framebufferRenderExt(minecraft.displayWidth, minecraft.displayHeight, false);
        } catch (Exception ignored) {
            // Vanilla's dim background remains the safe fallback.
        } finally {
            // framebufferRenderExt changes the orthographic projection to the
            // physical framebuffer size.  GuiScreen uses scaled coordinates,
            // so restore its projection before vanilla draws buttons/text.
            restoreGuiProjection();
        }
    }

    private boolean shouldHide(QolModule module, GuiScreen screen) {
        if (screen == null || screen instanceof VibeClickGui) {
            return false;
        }
        if (screen instanceof net.minecraft.client.gui.inventory.GuiInventory) {
            return module.getGuiTargets().isSelected("Inventory");
        }
        if (screen instanceof net.minecraft.client.gui.GuiIngameMenu) {
            return module.getGuiTargets().isSelected("ESC");
        }
        // Disconnect/kick messages must retain vanilla's opaque background.
        // Replaying a cached world frame there made the player see a frozen
        // world behind an otherwise invisible kick screen.
        if (screen instanceof net.minecraft.client.gui.GuiDisconnected) {
            return false;
        }
        // Do not affect Vibe setup/shader screens or chat.  Other screens in
        // the vanilla GUI package are inventory, chest, merchant and similar
        // client menus selected by this option.
        return module.getGuiTargets().isSelected("Other Vanilla GUIs")
                && screen.getClass().getName().startsWith("net.minecraft.client.gui.")
                && !(screen instanceof net.minecraft.client.gui.GuiChat);
    }

    private void ensureFramebuffer() {
        if (worldFrame != null && width == minecraft.displayWidth && height == minecraft.displayHeight) {
            return;
        }
        if (worldFrame != null) {
            worldFrame.deleteFramebuffer();
        }
        width = minecraft.displayWidth;
        height = minecraft.displayHeight;
        worldFrame = new Framebuffer(width, height, false);
        captured = false;
    }

    private void restoreGuiProjection() {
        minecraft.getFramebuffer().bindFramebuffer(true);
        minecraft.entityRenderer.setupOverlayRendering();
        GuiRenderState.prepare(true);
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
