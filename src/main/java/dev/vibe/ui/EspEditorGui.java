package dev.vibe.ui;

import com.mojang.authlib.GameProfile;
import dev.vibe.Vibe;
import dev.vibe.module.impl.BlurModule;
import dev.vibe.module.impl.EspEditorModule;
import dev.vibe.module.impl.EspModule;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.ModeSetting;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;

/** Counter-Strike-style ESP preview sharing the resolved in-game ESP profile. */
public final class EspEditorGui extends GuiScreen {
    private enum Kind { PLAYERS, FRIENDS, TARGETS }
    private enum PickerPart { SATURATION, HUE, ALPHA }
    private final EspEditorModule module;
    private final ParticlesRenderer particles = new ParticlesRenderer();
    private final List<ElementRect> elements = new ArrayList<ElementRect>();
    private EspModule esp;
    private Kind kind = Kind.PLAYERS;
    private String dragged;
    private boolean rotating;
    private float previewYaw;
    private int lastMouseX;
    private int left, top, panelWidth;
    private EntityOtherPlayerMP preview;
    private String previewName;
    private ColorSetting activeColor;
    private boolean pickerOpen;
    private PickerPart pickerPart;
    private float pickerHue, pickerSaturation, pickerBrightness;

    public EspEditorGui(EspEditorModule module) { this.module = module; }

    @Override public void initGui() {
        panelWidth = Math.max(700, Math.min(860, width - 18));
        left = Math.max(9, (width - panelWidth) / 2);
        top = Math.max(10, (height - 480) / 2);
        esp = Vibe.getInstance().getModuleManager().getModule(EspModule.class);
        setActiveColor(esp.getOutlineColor());
    }

    private EspModule.ProfileSettings profile() { return kind == Kind.FRIENDS ? esp.getFriendsProfile() : esp.getTargetsProfile(); }
    private EspModule.Style style() { return esp.getPreviewStyle(kind == Kind.PLAYERS ? 0 : kind == Kind.FRIENDS ? 1 : 2); }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        SkeetEditorStyle.backdrop(this, BlurModule.ESP_EDITOR, partialTicks);
        SkeetEditorStyle.window(left, top, left + panelWidth, top + 480, "Interactive ESP preview", "drag elements • drag model to rotate");
        drawTabs();
        drawPreview(mouseX, mouseY);
        drawSettings(mouseX, mouseY);
        if (dragged != null) drawDraggedElement(mouseX, mouseY);
        if (pickerOpen) drawPicker();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawTabs() {
        String[] labels = {"PLAYERS", "FRIENDS / TEAMS", "TARGETS"};
        for (int i = 0; i < labels.length; i++) {
            int x = left + 14 + i * 137;
            SkeetEditorStyle.row(x, top + 42, x + 128, top + 64, kind.ordinal() == i, false);
            fontRendererObj.drawStringWithShadow(labels[i], x + 8, top + 50, kind.ordinal() == i ? 0xFF101012 : SkeetEditorStyle.TEXT);
        }
    }

    private void drawPreview(int mouseX, int mouseY) {
        int previewLeft = left + 24, previewTop = top + 82, previewRight = left + 420, previewBottom = top + 462;
        SkeetEditorStyle.panel(previewLeft, previewTop, previewRight, previewBottom, "Drag & drop elements");
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("Release a component on a side of the box to reposition it"), previewLeft + 14, previewTop + 30, SkeetEditorStyle.MUTED);
        int boxLeft = previewLeft + 142, boxTop = previewTop + 77, boxRight = boxLeft + 126, boxBottom = boxTop + 246;
        EspModule.Style style = style();
        drawAlphaRect(boxLeft, boxTop, boxRight, boxBottom, style.getFill());
        int shortSide = Math.min(boxRight - boxLeft, boxBottom - boxTop);
        int thickness = Math.max(1, Math.min(3, Math.round(shortSide * 0.018F)));
        int corner = Math.max(thickness, Math.min((shortSide - thickness) / 2, Math.round(shortSide * 0.24F)));
        RenderUtils.tacticalCorners(boxLeft, boxTop, boxRight, boxBottom, style.getOutline(), thickness, corner);
        previewName = kind == Kind.FRIENDS ? "Yoshiii" : kind == Kind.TARGETS ? "xHeist_" : "Steve";
        drawPreviewPlayer((boxLeft + boxRight) / 2, boxBottom - 10);
        ModeSetting position = positionSetting(dragged);
        String previous = position == null ? null : position.getValue();
        BooleanSetting defaults = kind == Kind.PLAYERS ? null : profile().getUsePlayerDefaults();
        boolean inherited = defaults != null && defaults.isEnabled();
        try {
            if (position != null) {
                position.setValue(snapSide(dragged, mouseX, mouseY, boxLeft, boxTop, boxRight, boxBottom));
                if (inherited) defaults.setValue(false);
            }
            drawLabels(boxLeft, boxTop, boxRight, boxBottom, style());
            if (dragged != null) for (ElementRect element : elements) {
                if (dragged.equals(element.name)) RenderUtils.tacticalCorners(element.x, element.y,
                        element.x + element.w, element.y + element.h, SkeetEditorStyle.accent(0), 1, 4);
            }
        } finally {
            if (position != null) position.setValue(previous);
            if (inherited) defaults.setValue(true);
        }
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.format("Click-drag model to rotate • %s", previewName), previewLeft + 14, previewBottom - 17, SkeetEditorStyle.MUTED);
    }

    private void drawAlphaRect(int left, int top, int right, int bottom, int color) {
        if ((color >>> 24) == 255) Gui.drawRect(left, top, right, bottom, color);
        else {
            org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_ENABLE_BIT
                    | org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_CURRENT_BIT);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
            org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
            org.lwjgl.opengl.GL11.glColor4f((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F, (color & 255) / 255.0F, (color >>> 24) / 255.0F);
            org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_QUADS);
            org.lwjgl.opengl.GL11.glVertex2i(left, bottom); org.lwjgl.opengl.GL11.glVertex2i(right, bottom); org.lwjgl.opengl.GL11.glVertex2i(right, top); org.lwjgl.opengl.GL11.glVertex2i(left, top);
            org.lwjgl.opengl.GL11.glEnd();
            org.lwjgl.opengl.GL11.glPopAttrib();
            // GL_COLOR_BUFFER_BIT does not restore the current tint. Never
            // allow a translucent ESP fill to tint the model or the world.
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void drawPreviewPlayer(int x, int y) {
        if (preview == null || !previewName.equals(preview.getName())) preview = createPreview(previewName);
        if (preview == null) return;
        GuiRenderState.prepare(true);
        float oldOffset = preview.renderYawOffset, oldYaw = preview.rotationYaw, oldPitch = preview.rotationPitch,
                oldHead = preview.rotationYawHead, oldPrevOffset = preview.prevRenderYawOffset,
                oldPrevYaw = preview.prevRotationYaw, oldPrevPitch = preview.prevRotationPitch,
                oldPrevHead = preview.prevRotationYawHead;
        RenderManager manager = mc.getRenderManager();
        float oldView = manager.playerViewY;
        boolean oldShadow = manager.isRenderShadow();
        boolean oldEntityShadows = mc.gameSettings.entityShadows;
        org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_ENABLE_BIT
                | org.lwjgl.opengl.GL11.GL_LIGHTING_BIT | org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT
                | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_CURRENT_BIT);
        GlStateManager.pushMatrix();
        try {
            // This is the same stable basis used by GuiInventory. The larger
            // scale intentionally fills the 2D ESP bounds instead of leaving
            // a tiny player inside a life-size preview box.
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableColorMaterial();
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.translate(x, y, 50.0F);
            GlStateManager.scale(-108.0F, 108.0F, 108.0F);
            GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
            RenderHelper.enableStandardItemLighting();
            preview.renderYawOffset = previewYaw;
            preview.rotationYaw = previewYaw;
            preview.rotationPitch = 0.0F;
            preview.rotationYawHead = previewYaw;
            preview.prevRenderYawOffset = previewYaw;
            preview.prevRotationYaw = previewYaw;
            preview.prevRotationPitch = 0.0F;
            preview.prevRotationYawHead = previewYaw;
            manager.setPlayerViewY(180.0F);
            // RenderManager controls the normal shadow, while this option is
            // also checked by OptiFine's entity render path.  Disable both
            // for the editor-only model so an opaque floor ellipse can never
            // bleed into the ESP preview.
            mc.gameSettings.entityShadows = false;
            manager.setRenderShadow(false);
            // The entity fields above are the sole yaw input. Passing a yaw
            // here as well is what caused skins to mirror at some angles.
            manager.renderEntityWithPosYaw(preview, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F);
        } finally {
            manager.setRenderShadow(oldShadow);
            mc.gameSettings.entityShadows = oldEntityShadows;
            manager.setPlayerViewY(oldView);
            preview.renderYawOffset = oldOffset;
            preview.rotationYaw = oldYaw;
            preview.rotationPitch = oldPitch;
            preview.rotationYawHead = oldHead;
            preview.prevRenderYawOffset = oldPrevOffset;
            preview.prevRotationYaw = oldPrevYaw;
            preview.prevRotationPitch = oldPrevPitch;
            preview.prevRotationYawHead = oldPrevHead;
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();
            org.lwjgl.opengl.GL11.glPopAttrib();
            GuiRenderState.prepare(false);
        }
    }

    private EntityOtherPlayerMP createPreview(final String name) {
        if (mc.theWorld == null) return null;
        final ResourceLocation fallback = DefaultPlayerSkin.getDefaultSkinLegacy();
        final ResourceLocation custom = new ResourceLocation("vibe", "esp_preview/" + name.toLowerCase(java.util.Locale.ROOT));
        try { mc.getTextureManager().loadTexture(custom, new ThreadDownloadImageData(null, "https://minotar.net/skin/" + name, fallback, new net.minecraft.client.renderer.ImageBufferDownload())); } catch (Throwable ignored) { }
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name);
        EntityOtherPlayerMP result = new EntityOtherPlayerMP(mc.theWorld, profile) {
            @Override public ResourceLocation getLocationSkin() { return custom; }
            @Override public boolean hasSkin() { return true; }
            // Keep the friends/teams reference model on the standard Steve
            // geometry even when a downloaded skin happens to advertise Alex.
            @Override public String getSkinType() { return "default"; }
        };
        // Fresh preview entities default to no enabled outer skin-part flags.
        // The normal player renderer therefore drew only the base layer. Set
        // vanilla's complete player-model mask so hat/jacket/sleeves/pants
        // render exactly like a live player skin.
        result.getDataWatcher().updateObject(10, Byte.valueOf((byte) 0x7F));
        return result;
    }

    private void drawLabels(int left, int top, int right, int bottom, EspModule.Style style) {
        elements.clear();
        List<Label> labels = new ArrayList<Label>();
        if (style.hasNames()) labels.add(new Label(previewName, style.getNamePosition(), RenderUtils.TEXT, "Name"));
        if (style.hasDistance() && !"Name".equalsIgnoreCase(style.getDistancePosition())) labels.add(new Label("12m", style.getDistancePosition(), RenderUtils.MUTED, "Distance"));
        if (style.hasHeld()) labels.add(new Label("Diamond Sword", style.getHeldPosition(), RenderUtils.MUTED, "Held item"));
        int topCount = 0, bottomCount = 0, leftCount = 0, rightCount = 0;
        int healthOffset = style.hasHealth() && "Left".equalsIgnoreCase(style.getHealthPosition()) ? style.getHealthWidth() + 5 : 0;
        int rightOffset = style.hasHealth() && "Right".equalsIgnoreCase(style.getHealthPosition()) ? style.getHealthWidth() + 5 : 0;
        for (Label label : labels) {
            boolean joined = label.name.equals("Name") && style.hasDistance() && "Name".equalsIgnoreCase(style.getDistancePosition());
            if (joined) label = new Label(label.text + "  12m", label.position, label.color, label.name);
            int w = fontRendererObj.getStringWidth(label.text), x, y;
            if ("Bottom".equalsIgnoreCase(label.position)) { x = (left + right - w) / 2; y = bottom + 3 + bottomCount++ * 12; }
            else if ("Left".equalsIgnoreCase(label.position)) { x = left - healthOffset - w - 4; y = top + 5 + leftCount++ * 12; }
            else if ("Right".equalsIgnoreCase(label.position)) { x = right + rightOffset + 4; y = top + 5 + rightCount++ * 12; }
            else { x = (left + right - w) / 2; y = top - 10 - topCount++ * 12; }
            if (joined) elements.add(new ElementRect("Distance", x + w - fontRendererObj.getStringWidth("12m") - 2, y - 3, fontRendererObj.getStringWidth("12m") + 5, 14));
            if (label.name.equals("Name") || label.name.equals("Distance") || label.name.equals("Held item")) elements.add(new ElementRect(label.name, x - 3, y - 3, w + 6, 14));
            fontRendererObj.drawStringWithShadow(label.text, x, y, label.color);
        }
        if (style.hasHealth()) {
            int barWidth = style.getHealthWidth(), x = "Right".equalsIgnoreCase(style.getHealthPosition()) ? right + 5 : left - 5 - barWidth + 2;
            if ("Right".equalsIgnoreCase(style.getHealthPosition())) x = right + 5;
            Gui.drawRect(x, top, x + barWidth, bottom, 0xA0000000);
            Gui.drawRect(x, top + Math.round((bottom - top) * 0.25F), x + barWidth, bottom, style.hasHealthFade() ? RenderUtils.blend(style.getHealthStart(), style.getHealthEnd(), 0.25F) : style.getHealthStart());
            elements.add(new ElementRect("Health bar", x - 3, top - 3, barWidth + 6, bottom - top + 6));
        }
    }

    private void drawSettings(int mouseX, int mouseY) {
        int x = left + 438, right = left + panelWidth - 14, y = top + 43;
        SkeetEditorStyle.panel(x, y, right, top + 462, "ESP settings");
        int cursor = y + 34;
        if (kind == Kind.PLAYERS) {
            drawModeRow("Color mode", esp.getPlayerColorMode(), x + 14, cursor); cursor += 24;
            cursor = drawColorRow("Outline", esp.getOutlineColor(), x + 14, cursor);
            cursor = drawColorRow("Fill", esp.getFillColor(), x + 14, cursor);
            cursor = drawColorRow("Health start", esp.getHealthStart(), x + 14, cursor);
            cursor = drawColorRow("Health end", esp.getHealthEnd(), x + 14, cursor);
            drawToggleRow("Color fade", esp.getColorFade(), x + 14, cursor); cursor += 22;
            drawToggleRow("Names", esp.getNames(), x + 14, cursor);
            drawToggleRow("Health bar", esp.getHealth(), x + 180, cursor); cursor += 22;
            drawToggleRow("Held item", esp.getHeldItem(), x + 14, cursor);
            drawToggleRow("Distance", esp.getDistance(), x + 180, cursor); cursor += 22;
            drawToggleRow("Depth backplate", esp.getDepthBackplate(), x + 14, cursor); cursor += 22;
            cursor = drawStepper("Health width", esp.getHealthBarWidth(), x + 14, cursor);
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("Drag labels on the preview to change their side."), x + 14, cursor + 4, RenderUtils.MUTED);
        } else {
            EspModule.ProfileSettings p = profile();
            drawToggleRow("Use player defaults", p.getUsePlayerDefaults(), x + 14, cursor); cursor += 24;
            drawModeRow("Color mode", p.getColorMode(), x + 14, cursor); cursor += 24;
            cursor = drawColorRow("Override color", p.getOverrideColor(), x + 14, cursor);
            if (!p.getUsePlayerDefaults().isEnabled()) {
                int colorY = cursor;
                drawColorRow("Outline", p.getOutline(), x + 14, colorY);
                drawColorRow("Fill", p.getFill(), x + 14, colorY + 26);
                drawColorRow("Health start", p.getHealthStart(), x + 210, colorY);
                drawColorRow("Health end", p.getHealthEnd(), x + 210, colorY + 26);
                cursor += 52;
                drawToggleRow("Names", p.getNames(), x + 14, cursor);
                drawToggleRow("Health", p.getHealth(), x + 180, cursor); cursor += 22;
                drawToggleRow("Distance", p.getDistance(), x + 14, cursor);
                drawToggleRow("Held item", p.getHeld(), x + 180, cursor); cursor += 22;
                drawToggleRow("Backplate", p.getDepth(), x + 14, cursor);
                drawToggleRow("Health fade", p.getHealthFade(), x + 180, cursor); cursor += 22;
                cursor = drawStepper("Health width", p.getHealthWidth(), x + 14, cursor);
                cursor = drawModeRow("Name position", p.getNamePosition(), x + 14, cursor);
                cursor = drawModeRow("Health position", p.getHealthPosition(), x + 14, cursor);
                cursor = drawModeRow("Distance position", p.getDistancePosition(), x + 14, cursor);
                cursor = drawModeRow("Held position", p.getHeldPosition(), x + 14, cursor);
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("Drag labels on the preview to change their side."), x + 14, cursor + 4, RenderUtils.MUTED);
            }
        }
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("Click a swatch to open the color picker."), x + 14, top + 440, RenderUtils.MUTED);
    }

    private int drawColorRow(String label, ColorSetting setting, int x, int y) { fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate(label), x, y + 6, SkeetEditorStyle.TEXT); SkeetEditorStyle.input(x + 150, y + 2, x + 194, y + 20); Gui.drawRect(x + 153, y + 5, x + 191, y + 17, setting.getArgb()); return y + 26; }
    private int drawModeRow(String label, ModeSetting setting, int x, int y) { fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate(label), x, y + 6, SkeetEditorStyle.TEXT); SkeetEditorStyle.row(x + 145, y + 2, x + 254, y + 20, false, false); fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate(setting.getValue()), x + 152, y + 6, SkeetEditorStyle.accent(0.1F)); return y + 24; }
    private void drawToggleRow(String label, BooleanSetting setting, int x, int y) { SkeetEditorStyle.row(x, y + 2, x + 130, y + 20, setting.isEnabled(), false); fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate(label), x + 7, y + 6, setting.isEnabled() ? 0xFF101012 : SkeetEditorStyle.MUTED); }
    private int drawStepper(String label, dev.vibe.setting.NumberSetting setting, int x, int y) {
        fontRendererObj.drawStringWithShadow(label, x, y + 6, RenderUtils.TEXT);
        SkeetEditorStyle.row(x + 145, y + 2, x + 165, y + 20, false, false);
        SkeetEditorStyle.input(x + 170, y + 2, x + 225, y + 20);
        SkeetEditorStyle.row(x + 230, y + 2, x + 250, y + 20, false, false);
        fontRendererObj.drawStringWithShadow("-", x + 152, y + 6, RenderUtils.TEXT);
        String value = setting.getDouble() == Math.rint(setting.getDouble()) ? Integer.toString(setting.getInt()) : String.format(java.util.Locale.ROOT, "%.2f", setting.getDouble());
        fontRendererObj.drawStringWithShadow(value, x + 178, y + 6, RenderUtils.TEXT);
        fontRendererObj.drawStringWithShadow("+", x + 237, y + 6, RenderUtils.TEXT);
        return y + 24;
    }

    private void drawPicker() {
        int x = left + panelWidth - 194, y = top + 224, size = 90;
        SkeetEditorStyle.panel(x - 4, y - 4, x + 132, y + 172, "Color");
        for (int row = 0; row < 10; row++) for (int col = 0; col < 10; col++) { int rgb = java.awt.Color.HSBtoRGB(pickerHue, col / 9.0F, 1.0F - row / 9.0F) | 0xFF000000; Gui.drawRect(x + 7 + col * size / 10, y + 20 + row * size / 10, x + 7 + (col + 1) * size / 10, y + 20 + (row + 1) * size / 10, rgb); }
        Gui.drawRect(x + 7 + Math.round(pickerSaturation * size) - 2, y + 20 + Math.round((1.0F - pickerBrightness) * size) - 2, x + 7 + Math.round(pickerSaturation * size) + 3, y + 20 + Math.round((1.0F - pickerBrightness) * size) + 3, 0xFFFFFFFF);
        int hueY = y + 116; for (int i = 0; i < size; i++) Gui.drawRect(x + 7 + i, hueY, x + 8 + i, hueY + 8, java.awt.Color.HSBtoRGB(i / (float) size, 0.9F, 1.0F) | 0xFF000000);
        Gui.drawRect(x + 7 + Math.round(pickerHue * size) - 1, hueY - 2, x + 9 + Math.round(pickerHue * size), hueY + 10, 0xFFFFFFFF);
        int alphaY = y + 132; RenderUtils.transparencyGrid(x + 7, alphaY, x + 97, alphaY + 8, 3); for (int i = 0; i < size; i++) Gui.drawRect(x + 7 + i, alphaY, x + 8 + i, alphaY + 8, RenderUtils.alpha(activeColor.getArgb() | 0xFF000000, i * 255 / size));
        Gui.drawRect(x + 7 + activeColor.getAlpha() * size / 255 - 1, alphaY - 2, x + 9 + activeColor.getAlpha() * size / 255, alphaY + 10, 0xFFFFFFFF);
        RenderUtils.roundedRect(x + 104, y + 20, x + 120, y + 110, 2.0F, activeColor.getArgb());
    }

    private void setActiveColor(ColorSetting setting) { if (setting == null) return; activeColor = setting; float[] hsv = java.awt.Color.RGBtoHSB(setting.getRed(), setting.getGreen(), setting.getBlue(), null); pickerHue = hsv[0]; pickerSaturation = hsv[1]; pickerBrightness = hsv[2]; }
    private void updatePicker(int mouseX, int mouseY) { int x = left + panelWidth - 194, y = top + 224, size = 90; if (pickerPart == PickerPart.SATURATION) { pickerSaturation = Math.max(0, Math.min(1, (mouseX - x - 7) / (float) size)); pickerBrightness = Math.max(0, Math.min(1, 1 - (mouseY - y - 20) / (float) size)); } else if (pickerPart == PickerPart.HUE) pickerHue = Math.max(0, Math.min(1, (mouseX - x - 7) / (float) size)); else activeColor.setRgba(activeColor.getRed(), activeColor.getGreen(), activeColor.getBlue(), Math.max(0, Math.min(255, (mouseX - x - 7) * 255 / size))); if (pickerPart != PickerPart.ALPHA) { int rgb = java.awt.Color.HSBtoRGB(pickerHue, pickerSaturation, pickerBrightness); activeColor.setRgba(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, activeColor.getAlpha()); } }

    @Override protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            for (int i = 0; i < 3; i++) if (hit(left + 14 + i * 137, top + 42, 128, 22, mouseX, mouseY)) { kind = Kind.values()[i]; pickerOpen = false; return; }
            int px = left + panelWidth - 194, py = top + 224;
            if (pickerOpen && hit(px + 7, py + 20, 90, 90, mouseX, mouseY)) { pickerPart = PickerPart.SATURATION; updatePicker(mouseX, mouseY); return; }
            if (pickerOpen && hit(px + 7, py + 114, 90, 11, mouseX, mouseY)) { pickerPart = PickerPart.HUE; updatePicker(mouseX, mouseY); return; }
            if (pickerOpen && hit(px + 7, py + 130, 90, 12, mouseX, mouseY)) { pickerPart = PickerPart.ALPHA; updatePicker(mouseX, mouseY); return; }
            if (pickerOpen) { pickerOpen = false; return; }
            for (ElementRect e : elements) if (e.contains(mouseX, mouseY)) { dragged = e.name; return; }
            int settingsX = left + 438, cursor = top + 77;
            if (kind == Kind.PLAYERS) {
                if (hit(settingsX + 14, cursor, 270, 22, mouseX, mouseY)) { esp.getPlayerColorMode().cycle(false); return; }
                ColorSetting[] colors = {esp.getOutlineColor(), esp.getFillColor(), esp.getHealthStart(), esp.getHealthEnd()}; cursor += 24;
                for (ColorSetting color : colors) { if (hit(settingsX + 140, cursor, 60, 22, mouseX, mouseY)) { setActiveColor(color); pickerOpen = true; return; } cursor += 26; }
                if (hit(settingsX + 14, cursor, 130, 22, mouseX, mouseY)) { esp.getColorFade().toggle(); return; } cursor += 22;
                if (hit(settingsX + 14, cursor, 130, 22, mouseX, mouseY)) { esp.getNames().toggle(); return; }
                if (hit(settingsX + 180, cursor, 130, 22, mouseX, mouseY)) { esp.getHealth().toggle(); return; } cursor += 22;
                if (hit(settingsX + 14, cursor, 130, 22, mouseX, mouseY)) { esp.getHeldItem().toggle(); return; }
                if (hit(settingsX + 180, cursor, 130, 22, mouseX, mouseY)) { esp.getDistance().toggle(); return; } cursor += 22;
                if (hit(settingsX + 14, cursor, 130, 22, mouseX, mouseY)) { esp.getDepthBackplate().toggle(); return; } cursor += 22;
                if (step(settingsX + 14, cursor, esp.getHealthBarWidth(), mouseX, mouseY)) return;
            } else {
                EspModule.ProfileSettings p = profile();
                if (hit(settingsX + 14, cursor, 150, 22, mouseX, mouseY)) { p.getUsePlayerDefaults().toggle(); return; } cursor += 24;
                if (hit(settingsX + 14, cursor, 270, 22, mouseX, mouseY)) { p.getColorMode().cycle(false); return; } cursor += 24;
                if (hit(settingsX + 140, cursor, 60, 22, mouseX, mouseY)) { setActiveColor(p.getOverrideColor()); pickerOpen = true; return; }
                cursor += 26;
                if (!p.getUsePlayerDefaults().isEnabled()) {
                    ColorSetting[] colors = {p.getOutline(), p.getFill()};
                    for (ColorSetting color : colors) { if (hit(settingsX + 140, cursor, 60, 22, mouseX, mouseY)) { setActiveColor(color); pickerOpen = true; return; } cursor += 26; }
                    ColorSetting[] rightColors = {p.getHealthStart(), p.getHealthEnd()};
                    int rightColorY = cursor - 52;
                    for (ColorSetting color : rightColors) { if (hit(settingsX + 350, rightColorY, 60, 22, mouseX, mouseY)) { setActiveColor(color); pickerOpen = true; return; } rightColorY += 26; }
                    cursor += 0;
                    if (hit(settingsX + 14, cursor, 130, 22, mouseX, mouseY)) { p.getNames().toggle(); return; }
                    if (hit(settingsX + 180, cursor, 130, 22, mouseX, mouseY)) { p.getHealth().toggle(); return; } cursor += 22;
                    if (hit(settingsX + 14, cursor, 130, 22, mouseX, mouseY)) { p.getDistance().toggle(); return; }
                    if (hit(settingsX + 180, cursor, 130, 22, mouseX, mouseY)) { p.getHeld().toggle(); return; } cursor += 22;
                    if (hit(settingsX + 14, cursor, 130, 22, mouseX, mouseY)) { p.getDepth().toggle(); return; }
                    if (hit(settingsX + 180, cursor, 130, 22, mouseX, mouseY)) { p.getHealthFade().toggle(); return; } cursor += 22;
                    if (step(settingsX + 14, cursor, p.getHealthWidth(), mouseX, mouseY)) return;
                    cursor += 24;
                    ModeSetting[] modes = {p.getNamePosition(), p.getHealthPosition(), p.getDistancePosition(), p.getHeldPosition()};
                    for (ModeSetting mode : modes) { if (hit(settingsX + 14, cursor, 280, 22, mouseX, mouseY)) { mode.cycle(false); return; } cursor += 24; }
                }
            }
            int boxLeft = left + 24 + 142, boxTop = top + 82 + 77, boxRight = boxLeft + 126, boxBottom = boxTop + 246;
            if (hit(boxLeft - 30, boxTop - 24, 190, 300, mouseX, mouseY)) { rotating = true; lastMouseX = mouseX; }
        } else if (mouseButton == 1) pickerOpen = false;
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override protected void mouseClickMove(int mouseX, int mouseY, int mouseButton, long timeSinceLastClick) { if (rotating && mouseButton == 0) { previewYaw += mouseX - lastMouseX; lastMouseX = mouseX; } if (pickerOpen && pickerPart != null) updatePicker(mouseX, mouseY); }
    @Override protected void mouseReleased(int mouseX, int mouseY, int state) { if (rotating) rotating = false; if (dragged != null) { int boxLeft = left + 24 + 142, boxTop = top + 82 + 77, boxRight = boxLeft + 126, boxBottom = boxTop + 246; drop(dragged, mouseX, mouseY, boxLeft, boxTop, boxRight, boxBottom); dragged = null; } pickerPart = null; super.mouseReleased(mouseX, mouseY, state); }

    private ModeSetting positionSetting(String element) {
        if (element == null) return null;
        EspModule.ProfileSettings p = kind == Kind.PLAYERS ? null : profile();
        if ("Name".equals(element)) return p == null ? esp.getNamePosition() : p.getNamePosition();
        if ("Health bar".equals(element)) return p == null ? esp.getHealthPosition() : p.getHealthPosition();
        if ("Distance".equals(element)) return p == null ? esp.getDistancePosition() : p.getDistancePosition();
        if ("Held item".equals(element)) return p == null ? esp.getHeldPosition() : p.getHeldPosition();
        return null;
    }

    private String snapSide(String element, int mx, int my, int l, int t, int r, int b) {
        if ("Health bar".equals(element)) return mx < (l + r) / 2 ? "Left" : "Right";
        // Compare distance to each finite edge, including inside the box.
        double dx = Math.max(l - mx, Math.max(0, mx - r));
        double dy = Math.max(t - my, Math.max(0, my - b));
        double[] distances = {Math.hypot(mx - l, dy), Math.hypot(mx - r, dy),
                Math.hypot(dx, my - t), Math.hypot(dx, my - b)};
        String[] sides = {"Left", "Right", "Top", "Bottom"};
        int best = 0;
        for (int i = 1; i < distances.length; i++) if (distances[i] < distances[best]) best = i;
        if ("Distance".equals(element)) for (ElementRect rect : elements)
            if ("Name".equals(rect.name) && rect.contains(mx, my)) return "Name";
        return sides[best];
    }

    private void drawDraggedElement(int mouseX, int mouseY) {
        String text = "Moving: " + dragged;
        int x = Math.min(width - fontRendererObj.getStringWidth(text) - 12, mouseX + 8);
        int y = Math.min(height - 20, mouseY + 14);
        Gui.drawRect(x - 4, y - 4, x + fontRendererObj.getStringWidth(text) + 5, y + 13, 0xE818181C);
        fontRendererObj.drawStringWithShadow(text, x, y, SkeetEditorStyle.accent(0));
    }

    private void drop(String element, int mx, int my, int l, int t, int r, int b) {
        ModeSetting setting = positionSetting(element);
        if (setting == null || !hit(left + 24, top + 82, 396, 380, mx, my)) return;
        if (kind != Kind.PLAYERS) profile().getUsePlayerDefaults().setValue(false);
        setting.setValue(snapSide(element, mx, my, l, t, r, b));
    }
    private boolean step(int x, int y, dev.vibe.setting.NumberSetting setting, int mouseX, int mouseY) {
        if (!hit(x + 145, y + 2, 20, 18, mouseX, mouseY) && !hit(x + 230, y + 2, 20, 18, mouseX, mouseY)) return false;
        double value = setting.getDouble() + (mouseX < x + 200 ? -setting.getIncrement() : setting.getIncrement());
        setting.setValue(Math.max(setting.getMinimum(), Math.min(setting.getMaximum(), value)));
        return true;
    }
    private boolean hit(int x, int y, int w, int h, int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException { if (keyCode == Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(null); return; } super.keyTyped(typedChar, keyCode); }
    @Override public void onGuiClosed() { if (module.isEnabled()) module.setEnabled(false); super.onGuiClosed(); }
    @Override public boolean doesGuiPauseGame() { return false; }

    private static final class Label { private final String text, position, name; private final int color; private Label(String text, String position, int color, String name) { this.text = text; this.position = position; this.color = color; this.name = name; } }
    private static final class ElementRect { private final String name; private final int x, y, w, h; private ElementRect(String name, int x, int y, int w, int h) { this.name = name; this.x = x; this.y = y; this.w = w; this.h = h; } private boolean contains(int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + h; } }
}
