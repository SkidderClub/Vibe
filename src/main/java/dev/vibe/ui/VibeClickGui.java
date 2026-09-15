package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.impl.ClickGuiModule;
import dev.vibe.language.LanguageManager;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.RangeSetting;
import dev.vibe.setting.Setting;
import dev.vibe.setting.StringSetting;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/** Modular ClickGUI with Futuristic, Skeet, NeverLose, Sigma, Augustus and Xanax layouts. */
public final class VibeClickGui extends GuiScreen {

    private static final int PANEL_WIDTH = 176;
    private static final int COLOR_POPUP_WIDTH = 116;
    private static final int COLOR_POPUP_HEIGHT = 145;
    private static final int SKEET_WIDTH = 380;
    private static final int SKEET_HEIGHT = 355;
    private static final int SKEET_MIN_WIDTH = 300;
    private static final int SKEET_MIN_HEIGHT = 220;
    private static final int SKEET_SIDEBAR_WIDTH = 50;
    private static final Map<Category, int[]> SAVED_PANELS = new EnumMap<Category, int[]>(Category.class);
    private static final Set<String> SAVED_EXPANDED = new HashSet<String>();
    private static final Set<String> SAVED_OPEN_SETTINGS = new HashSet<String>();
    private static int SAVED_SKEET_X = Integer.MIN_VALUE;
    private static int SAVED_SKEET_Y = Integer.MIN_VALUE;
    private static int SAVED_SKEET_SCROLL;
    private static int SAVED_SKEET_WIDTH = SKEET_WIDTH;
    private static int SAVED_SKEET_HEIGHT = SKEET_HEIGHT;
    private static Category SAVED_SKEET_CATEGORY = Category.COMBAT;
    private final Map<Category, Panel> panels = new EnumMap<Category, Panel>(Category.class);
    private final Set<Module> expandedModules = Collections.newSetFromMap(new IdentityHashMap<Module, Boolean>());
    private final Set<MultiSelectSetting> openSelections = Collections.newSetFromMap(new IdentityHashMap<MultiSelectSetting, Boolean>());
    private final List<Target> targets = new ArrayList<Target>();
    private final ParticlesRenderer clickGuiParticles = new ParticlesRenderer();
    private final NeverLoseWorkspace neverLose = new NeverLoseWorkspace();
    private final SigmaWorkspace sigma = new SigmaWorkspace();
    private final XanaxWorkspace xanax = new XanaxWorkspace();
    private Setting<?> editing;
    private String editBuffer = "";
    private Panel dragging;
    private Target draggingSlider;
    private ColorPopup colorPopup;
    private ColorPopupPart draggingColor;
    private Module bindingModule;
    private Category skeetCategory = Category.COMBAT;
    private int skeetX = Integer.MIN_VALUE;
    private int skeetY = Integer.MIN_VALUE;
    private int skeetScroll;
    private int skeetMaxScroll;
    private int skeetWidth = SKEET_WIDTH;
    private int skeetHeight = SKEET_HEIGHT;
    private boolean draggingSkeet;
    private boolean resizingSkeet;
    private int skeetDragOffsetX;
    private int skeetDragOffsetY;
    private int dragOffsetX;
    private int dragOffsetY;

    @Override
    public void initGui() {
        int columns = width >= 1000 ? Category.values().length : (width >= 650 ? 3 : 2);
        int index = 0;
        for (Category category : Category.values()) {
            Panel panel = panels.get(category);
            if (panel == null) {
                int[] saved = SAVED_PANELS.get(category);
                int x = saved == null ? 12 + (index % columns) * (PANEL_WIDTH + 10) : saved[0];
                int y = saved == null ? 42 + (index / columns) * 230 : saved[1];
                panels.put(category, new Panel(category, x, y));
            }
            index++;
        }
        for (Module module : Vibe.getInstance().getModuleManager().getModules()) {
            if (SAVED_EXPANDED.contains(module.getId())) {
                expandedModules.add(module);
            }
            for (Setting<?> setting : module.getSettings()) {
                if (setting instanceof MultiSelectSetting && SAVED_OPEN_SETTINGS.contains(settingKey(module, setting))) {
                    openSelections.add((MultiSelectSetting) setting);
                }
            }
        }
        if (skeetX == Integer.MIN_VALUE) {
            skeetWidth = SAVED_SKEET_WIDTH;
            skeetHeight = SAVED_SKEET_HEIGHT;
            skeetX = SAVED_SKEET_X == Integer.MIN_VALUE ? Math.max(8, (width - skeetWidth) / 2) : SAVED_SKEET_X;
            skeetY = SAVED_SKEET_Y == Integer.MIN_VALUE ? Math.max(40, (height - skeetHeight) / 2) : SAVED_SKEET_Y;
            skeetScroll = SAVED_SKEET_SCROLL;
            skeetCategory = SAVED_SKEET_CATEGORY == null ? Category.COMBAT : SAVED_SKEET_CATEGORY;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (isXanaxTheme()) {
            xanax.draw(width, height, mouseX, mouseY);
        } else if (isNeverLoseTheme()) {
            drawNeverLose(mouseX, mouseY, partialTicks);
        } else if (isSigmaTheme()) {
            drawSigma(mouseX, mouseY, partialTicks);
        } else if (isAugustusTheme()) {
            drawAugustus(mouseX, mouseY, partialTicks);
        } else if (isFuturisticTheme()) {
            drawFuturistic(mouseX, mouseY, partialTicks);
        } else {
            drawSkeet(mouseX, mouseY, partialTicks);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        WaifuRenderer.draw(this);
    }

    private void drawFuturistic(int mouseX, int mouseY, float partialTicks) {
        dev.vibe.module.impl.BlurModule blur = Vibe.getInstance().getModuleManager().getModule(dev.vibe.module.impl.BlurModule.class);
        if (blur != null && blur.isEnabled()
                && blur.getElements().isSelected(dev.vibe.module.impl.BlurModule.CLICK_GUI)) {
            KawaseBlur.drawBackdrop(width, height, blur.getStrength().getInt(), partialTicks);
        } else {
            Gui.drawRect(0, 0, width, height, 0xD00A1020);
        }
        drawFuturisticGrid();
        // Draw beneath panels and setting controls, never on top of them.
        clickGuiParticles.draw(this);

        targets.clear();
        int accent = guiAccent(0.0F);
        RenderUtils.roundedRect(10, 6, width - 10, 38, 10.0F, 0xFF0A1020);
        RenderUtils.roundedOutline(10, 6, width - 10, 38, 10.0F, 1.0F, 0xFF26334A);
        drawInformationHeader(accent);

        for (Category category : Category.values()) {
            Panel panel = panels.get(category);
            if (panel != null) {
                drawPanel(panel, mouseX, mouseY, true);
            }
        }
        if (draggingSlider != null) {
            updateSlider(draggingSlider, mouseX);
        }
        if (draggingColor != null && colorPopup != null) {
            updateColorPopup(mouseX, mouseY);
        }
        if (colorPopup != null) {
            drawColorPopup(colorPopup);
        }
    }

    private void drawInformationHeader(int accent) {
        String user = Vibe.getInstance().getIdentity() != null && Vibe.getInstance().getIdentity().isConfigured()
                ? Vibe.getInstance().getIdentity().getGamertag() : mc.getSession().getUsername();
        String packName = mc.gameSettings.resourcePacks.isEmpty() ? "Default" : mc.gameSettings.resourcePacks.get(0);
        String time = new SimpleDateFormat("dd MMM  HH:mm").format(new Date());
        String headline = Vibe.NAME + " v" + Vibe.VERSION + "  •  " + user + "  •  " + Minecraft.getDebugFPS() + " FPS";
        String detail = ellipsize(packName, 20) + "  •  " + time + "  •  LMB toggle  •  RMB settings  •  MMB bind";
        fontRendererObj.drawStringWithShadow(headline, (width - fontRendererObj.getStringWidth(headline)) / 2, 10, RenderUtils.TEXT);
        fontRendererObj.drawStringWithShadow(detail, (width - fontRendererObj.getStringWidth(detail)) / 2, 21, RenderUtils.MUTED);
    }

    private void drawFuturisticGrid() {
        int accent = guiAccent(0.55F);
        for (int x = 0; x < width; x += 36) {
            Gui.drawRect(x, 0, x + 1, height, 0x1100FFFF);
        }
        for (int y = 0; y < height; y += 36) {
            Gui.drawRect(0, y, width, y + 1, 0x1100FFFF);
        }
        int scanY = 42 + (int) ((System.currentTimeMillis() / 18L) % Math.max(1, height - 42));
        Gui.drawRect(0, scanY, width, scanY + 1, RenderUtils.alpha(accent, 42));
    }

    private void drawSigma(int mouseX, int mouseY, float partialTicks) {
        drawSkeetBackdrop(partialTicks);
        clickGuiParticles.draw(this);
        sigma.draw(width, height, mouseX, mouseY);
    }

    /**
     * Compact category workspace inspired by the provided Koks layout.  It is
     * intentionally rendered with Vibe's own Module/Setting model so every
     * existing control (colour popups, range sliders, bind capture and config
     * persistence) behaves exactly as it does in the other themes.
     */
    private void drawSkeet(int mouseX, int mouseY, float partialTicks) {
        drawSkeetBackdrop(partialTicks);
        // Draw particles beneath the workspace and setting controls.
        clickGuiParticles.draw(this);
        targets.clear();
        int boardWidth = boardWidth();
        int boardHeight = boardHeight();
        skeetX = Math.max(8, Math.min(width - boardWidth - 8, skeetX));
        skeetY = Math.max(32, Math.min(height - boardHeight - 8, skeetY));
        int right = skeetX + boardWidth;
        int bottom = skeetY + boardHeight;

        // Koks' original compact 350x300 workspace: a 50px icon rail, a
        // flat charcoal work surface and a thin animated accent at the top.
        Gui.drawRect(skeetX, skeetY - 5, right, bottom, 0xFF161616);
        Gui.drawRect(skeetX, skeetY, skeetX + SKEET_SIDEBAR_WIDTH, bottom, 0xFF0C0C0C);
        Gui.drawRect(skeetX + SKEET_SIDEBAR_WIDTH, skeetY, skeetX + SKEET_SIDEBAR_WIDTH + 1, bottom, 0xFF28272A);
        drawSkeetAccentLine(skeetX + 1, skeetY - 3, right - 1, guiAccent(0.0F));
        drawSkeetBorder(skeetX, skeetY - 5, right, bottom, 0xFF28272A);
        // Small, dedicated resize grip.  The first module row remains free
        // for clicks, while the bottom-right corner is clearly available.
        for (int step = 0; step < 3; step++) {
            int shade = 0xFF59606B;
            Gui.drawRect(right - 5 - step * 3, bottom - 2, right - 3 - step * 3, bottom, shade);
            Gui.drawRect(right - 2, bottom - 5 - step * 3, right, bottom - 3 - step * 3, shade);
        }

        int sidebarRight = skeetX + SKEET_SIDEBAR_WIDTH;
        int categoryY = skeetY + 3;
        int categoryStep = Math.min(50, (boardHeight - 6) / Category.values().length);
        enableScissor(skeetX + 1, skeetY + 1, sidebarRight - 1, bottom - 1);
        try {
            for (Category category : Category.values()) {
                boolean selected = category == skeetCategory;
                boolean hovered = contains(mouseX, mouseY, skeetX + 3, categoryY, sidebarRight - 3, categoryY + categoryStep - 5);
                if (selected) {
                    Gui.drawRect(skeetX + 3, categoryY - 3, sidebarRight - 3, categoryY + categoryStep - 5, 0xFF161616);
                    Gui.drawRect(skeetX + 3, categoryY - 3, sidebarRight - 3, categoryY - 2, 0xFF28272A);
                    Gui.drawRect(skeetX + 3, categoryY + categoryStep - 6, sidebarRight - 3, categoryY + categoryStep - 5, 0xFF28272A);
                } else if (hovered) {
                    Gui.drawRect(skeetX + 3, categoryY, sidebarRight - 3, categoryY + categoryStep - 5, 0xFF131315);
                }
                GlStateManager.pushMatrix();
                GlStateManager.translate(skeetX + 9, categoryY + 2, 0);
                float iconScale = Math.min(1F, (categoryStep - 15) / 32F);
                GlStateManager.scale(iconScale, iconScale, 1);
                drawSkeetCategoryIcon(category, 0, 0, !selected);
                GlStateManager.popMatrix();
                if (selected) {
                    String label = category.getLabel();
                    fontRendererObj.drawStringWithShadow(label, skeetX + (SKEET_SIDEBAR_WIDTH - fontRendererObj.getStringWidth(label)) / 2,
                            categoryY + categoryStep - 14, RenderUtils.TEXT);
                }
                targets.add(new Target(TargetType.CATEGORY, skeetX + 3, categoryY, sidebarRight - 3, categoryY + categoryStep - 5,
                        null, null, category.ordinal()));
                categoryY += categoryStep;
            }
        } finally {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }

        int contentLeft = sidebarRight + 8;
        int contentRight = right - 6;
        int contentTop = skeetY + 5;
        int contentBottom = bottom - 5;
        int cardWidth = Math.max(100, (contentRight - contentLeft - 5) / 2);
        int firstX = contentLeft;
        int secondX = contentLeft + cardWidth + 5;
        int[] cardY = {contentTop - skeetScroll, contentTop - skeetScroll};
        int index = 0;
        enableScissor(contentLeft, contentTop, contentRight, contentBottom);
        try {
            for (Module module : Vibe.getInstance().getModuleManager().getModules(skeetCategory)) {
                int column = index++ & 1;
                int cardLeft = column == 0 ? firstX : secondX;
                cardY[column] = drawSkeetModule(module, cardLeft, cardY[column], cardWidth, mouseX, mouseY,
                        guiAccent(skeetCategory.ordinal() * 0.20F + column * 0.13F));
            }
        } finally {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
        int contentHeight = Math.max(cardY[0], cardY[1]) - (contentTop - skeetScroll);
        int maxScroll = Math.max(0, contentHeight - (contentBottom - contentTop));
        skeetMaxScroll = maxScroll;
        skeetScroll = Math.max(0, Math.min(maxScroll, skeetScroll));
        if (maxScroll > 0) {
            int trackTop = contentTop;
            int trackBottom = contentBottom;
            int handle = Math.max(16, Math.round((contentBottom - contentTop) * (contentBottom - contentTop) / (float) Math.max(1, contentHeight)));
            int handleTop = trackTop + Math.round((trackBottom - trackTop - handle) * skeetScroll / (float) Math.max(1, maxScroll));
            Gui.drawRect(contentRight - 3, trackTop, contentRight - 1, trackBottom, 0xFF292A31);
            Gui.drawRect(contentRight - 3, handleTop, contentRight - 1, handleTop + handle, guiAccent(0.18F));
        }
        if (draggingSlider != null) {
            updateSlider(draggingSlider, mouseX);
        }
        if (draggingColor != null && colorPopup != null) {
            updateColorPopup(mouseX, mouseY);
        }
        if (colorPopup != null) {
            drawColorPopup(colorPopup);
        }
    }

    private void drawSkeetBackdrop(float partialTicks) {
        dev.vibe.module.impl.BlurModule blur = Vibe.getInstance().getModuleManager().getModule(dev.vibe.module.impl.BlurModule.class);
        if (blur != null && blur.isEnabled() && blur.getElements().isSelected(dev.vibe.module.impl.BlurModule.CLICK_GUI)) {
            KawaseBlur.drawBackdrop(width, height, blur.getStrength().getInt(), partialTicks);
        } else {
            Gui.drawRect(0, 0, width, height, 0xD90A0A0D);
        }
    }

    private void drawAugustus(int mouseX, int mouseY, float partialTicks) {
        ClickGuiModule clickGui = Vibe.getInstance().getModuleManager().getModule(ClickGuiModule.class);
        int backgroundAlpha = clickGui == null ? 200 : clickGui.getAugustusBackgroundAlpha().getInt();
        Gui.drawRect(0, 0, width, height, RenderUtils.alpha(0xFF000000, Math.round(backgroundAlpha * 128 / 255F)));
        if (!AugustusImGui.draw()) {
            // Keep the click GUI usable even on a platform for which no ImGui
            // native can be loaded from the shared client JAR.
            if (clickGui != null) {
                clickGui.getTheme().setValue("NeverLose");
            }
            drawNeverLose(mouseX, mouseY, partialTicks);
        }
    }

    private void drawNeverLose(int mouseX, int mouseY, float partialTicks) {
        drawSkeetBackdrop(partialTicks);
        clickGuiParticles.draw(this);
        neverLose.draw(width, height, mouseX, mouseY);
    }

    private void drawSkeetAccentLine(int left, int top, int right, int base) {
        int length = Math.max(1, right - left);
        for (int x = left; x < right; x += 3) {
            float phase = (x - left) / (float) length;
            int color = RenderUtils.blend(base, guiAccent(phase + 0.55F), phase);
            Gui.drawRect(x, top, Math.min(right, x + 3), top + 2, color);
        }
    }

    private int drawSkeetModule(Module module, int left, int top, int width, int mouseX, int mouseY, int accent) {
        int settingsHeight = expandedModules.contains(module) ? visibleSettingsHeight(module) : 0;
        int height = 15 + settingsHeight;
        int right = left + width;
        boolean hovered = contains(mouseX, mouseY, left, top, right, top + 15);
        Gui.drawRect(left, top, right, top + height, 0xFF161616);
        drawSkeetBorder(left, top, right, top + height, module.isEnabled() ? RenderUtils.alpha(accent, 190) : 0xFF28272A);
        if (hovered) {
            Gui.drawRect(left + 1, top + 1, right - 1, top + 14, 0xFF1B1B1E);
        }
        fontRendererObj.drawStringWithShadow(module.getName(), left + 3, top + 3,
                module.isEnabled() ? RenderUtils.TEXT : 0xFFC1C3C9);
        boolean hasSettings = hasVisibleSettings(module);
        String key = module.getKey() == Keyboard.KEY_NONE ? "" : Keyboard.getKeyName(module.getKey());
        String suffix = (key.isEmpty() ? "" : "[" + key + "] ") + (hasSettings ? (expandedModules.contains(module) ? "-" : "+") : "");
        String hint = bindingModule == module ? "PRESS KEY" : suffix;
        fontRendererObj.drawStringWithShadow(hint, right - 3 - fontRendererObj.getStringWidth(hint), top + 3,
                bindingModule == module ? accent : RenderUtils.MUTED);
        targets.add(new Target(TargetType.MODULE, left, top, right, top + 15, module, null, -1));
        int y = top + 17;
        if (expandedModules.contains(module)) {
            for (Setting<?> setting : module.getSettings()) {
                if (setting.isVisible()) {
                    y = drawSettingAt(setting, left + 4, right - 4, y, accent, false, true);
                }
            }
        }
        return top + height + 5;
    }

    private int visibleSettingsHeight(Module module) {
        int result = 0;
        for (Setting<?> setting : module.getSettings()) {
            if (!setting.isVisible()) {
                continue;
            }
            result += 18;
            if (setting instanceof MultiSelectSetting && openSelections.contains(setting)) {
                result += ((MultiSelectSetting) setting).getOptions().size() * 13;
            }
        }
        return result;
    }

    private void drawSkeetBorder(int left, int top, int right, int bottom, int color) {
        Gui.drawRect(left, top, right, top + 1, color);
        Gui.drawRect(left, bottom - 1, right, bottom, color);
        Gui.drawRect(left, top, left + 1, bottom, color);
        Gui.drawRect(right - 1, top, right, bottom, color);
    }

    private void drawSkeetCategoryIcon(Category category, int x, int y, boolean grayed) {
        // Koks' original assets are 512px transparent PNGs.  The source GUI
        // sampled them as if they were 32px textures, leaving a solid tile or
        // a tiny top-left fragment.  Draw the full texture area explicitly.
        // Texture resource names are stable identifiers, not translated UI
        // labels.  Using getLabel() made Skeet try to load paths such as a
        // Chinese category name whenever Language was enabled.
        String icon = category == Category.VISUAL ? "visuals" : category == Category.CLIENT ? "utilities"
                : category == Category.SCRIPTS ? "scripts" : category == Category.MEME ? "meme"
                : category.name().toLowerCase(java.util.Locale.ROOT);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.enableTexture2D();
            GlStateManager.color(grayed ? 0.55F : 1.0F, grayed ? 0.55F : 1.0F, grayed ? 0.60F : 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(new ResourceLocation("minecraft", "client/icons/" + icon + ".png"));
            Gui.drawScaledCustomSizeModalRect(x, y, 0.0F, 0.0F, 512, 512, 32, 32, 512.0F, 512.0F);
        } finally {
            GL11.glPopAttrib();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void line(int fromX, int fromY, int toX, int toY) {
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2i(fromX, fromY);
        GL11.glVertex2i(toX, toY);
        GL11.glEnd();
    }

    private void enableScissor(int left, int top, int right, int bottom) {
        ScaledResolution resolution = new ScaledResolution(mc);
        int factor = resolution.getScaleFactor();
        int x = Math.max(0, left * factor);
        int y = Math.max(0, mc.displayHeight - bottom * factor);
        int scissorWidth = Math.max(0, Math.min(mc.displayWidth - x, (right - left) * factor));
        int scissorHeight = Math.max(0, Math.min(mc.displayHeight - y, (bottom - top) * factor));
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x, y, scissorWidth, scissorHeight);
    }

    private void drawPanel(Panel panel, int mouseX, int mouseY, boolean futuristic) {
        int height = panelHeight(panel.category);
        panel.height = height;
        int accent = guiAccent(panel.category.ordinal() * 0.19F);
        if (futuristic) {
            // Solid, neutral layered surfaces stay readable over a strong
            // Kawase pass. Decorative coloured rails are intentionally absent
            // from the header and panel chrome.
            RenderUtils.roundedRect(panel.x - 2, panel.y - 2, panel.x + PANEL_WIDTH + 2, panel.y + height + 2, 11.0F, 0xD5060B14);
            Gui.drawRect(panel.x, panel.y + 9, panel.x + PANEL_WIDTH, panel.y + height - 9, 0xC80C172A);
            Gui.drawRect(panel.x + 9, panel.y, panel.x + PANEL_WIDTH - 9, panel.y + height, 0xC80C172A);
            RenderUtils.roundedRect(panel.x, panel.y, panel.x + PANEL_WIDTH, panel.y + height, 9.0F, 0xC80C172A);
            RenderUtils.roundedTopRect(panel.x, panel.y, panel.x + PANEL_WIDTH, panel.y + 24, 9.0F, 0xD5122845);
            RenderUtils.roundedOutline(panel.x, panel.y, panel.x + PANEL_WIDTH, panel.y + height, 9.0F, 1.0F, 0xFF283A58);
        } else {
            RenderUtils.roundedRect(panel.x, panel.y, panel.x + PANEL_WIDTH, panel.y + height, 8.0F, RenderUtils.INK);
            RenderUtils.roundedOutline(panel.x, panel.y, panel.x + PANEL_WIDTH, panel.y + height, 8.0F, 1.0F, accent);
            RenderUtils.roundedTopRect(panel.x, panel.y, panel.x + PANEL_WIDTH, panel.y + 23, 8.0F, RenderUtils.alpha(RenderUtils.SURFACE, 230));
        }
        fontRendererObj.drawStringWithShadow(panel.category.getLabel().toUpperCase(), panel.x + 10, panel.y + 9, RenderUtils.TEXT);
        int y = panel.y + 25;
        for (Module module : Vibe.getInstance().getModuleManager().getModules(panel.category)) {
            y = drawModule(panel, module, y, mouseX, mouseY, accent, futuristic);
        }
    }

    private int drawModule(Panel panel, Module module, int y, int mouseX, int mouseY, int accent, boolean futuristic) {
        boolean hovered = contains(mouseX, mouseY, panel.x + 5, y, panel.x + PANEL_WIDTH - 5, y + 19);
        int fill = module.isEnabled() ? RenderUtils.alpha(accent, futuristic ? 105 : 48)
                : (hovered ? (futuristic ? 0xFF1B304D : RenderUtils.SURFACE_HOVER)
                : (futuristic ? 0xF9112036 : RenderUtils.alpha(RenderUtils.SURFACE, 160)));
        RenderUtils.roundedRect(panel.x + 5, y, panel.x + PANEL_WIDTH - 5, y + 19, 6.0F, fill);
        boolean hasSettings = hasVisibleSettings(module);
        String key = module.getKey() == Keyboard.KEY_NONE ? "" : Keyboard.getKeyName(module.getKey());
        String suffix = hasSettings ? (key.isEmpty() ? "" : "[" + key + "] ") + (expandedModules.contains(module) ? "-" : "+")
                : (key.isEmpty() ? "" : "[" + key + "]");
        fontRendererObj.drawStringWithShadow(module.getName(), panel.x + 12, y + 5, module.isEnabled() ? RenderUtils.TEXT : 0xFFD5E1F5);
        String bindHint = bindingModule == module ? "PRESS KEY" : suffix;
        fontRendererObj.drawStringWithShadow(bindHint, panel.x + PANEL_WIDTH - 10 - fontRendererObj.getStringWidth(bindHint), y + 5,
                bindingModule == module ? accent : RenderUtils.MUTED);
        targets.add(new Target(TargetType.MODULE, panel.x + 5, y, panel.x + PANEL_WIDTH - 5, y + 19, module, null, -1));
        y += 20;
        if (expandedModules.contains(module)) {
            for (Setting<?> setting : module.getSettings()) {
                if (setting.isVisible()) {
                    y = drawSetting(panel, setting, y, accent, futuristic);
                }
            }
        }
        return y;
    }

    private int drawSetting(Panel panel, Setting<?> setting, int y, int accent, boolean futuristic) {
        return drawSettingAt(setting, panel.x + 9, panel.x + PANEL_WIDTH - 9, y, accent, futuristic, false);
    }

    private int settingControlLeft(Setting<?> setting, int left, int right, boolean skeet) {
        if (setting instanceof NumberSetting || setting instanceof RangeSetting) return left + Math.round((right - left) * .50F);
        if (setting instanceof ColorSetting) return right - (skeet ? 18 : 46);
        String value = "";
        if (setting instanceof BooleanSetting) value = LanguageManager.translate(((BooleanSetting) setting).isEnabled() ? "ON" : "OFF");
        else if (setting instanceof ModeSetting) value = LanguageManager.translate(((ModeSetting) setting).getValue());
        else if (setting instanceof StringSetting) value = ellipsize(editing == setting ? editBuffer : ((StringSetting) setting).getValue(), 15);
        else if (setting instanceof MultiSelectSetting) value = ((MultiSelectSetting) setting).getValue().size() + " " + LanguageManager.translate("selected");
        return Math.max(left + Math.round((right - left) * .4F), right - 5 - fontRendererObj.getStringWidth(value));
    }

    private void drawControlValue(String text, int left, int right, int y, int color) {
        int textWidth = fontRendererObj.getStringWidth(text);
        if (textWidth <= right - left) fontRendererObj.drawStringWithShadow(text, right - textWidth, y, color);
        else drawScrollingLabel(text, left, y, right, color);
    }

    private void drawScrollingLabel(String text, int left, int y, int right, int color) {
        int available = right - left;
        if (available <= 0) return;
        int overflow = fontRendererObj.getStringWidth(text) - available;
        if (overflow <= 0) { fontRendererObj.drawStringWithShadow(text, left, y, color); return; }
        double travel = overflow / 24.0;
        double phase = (System.currentTimeMillis() / 1000.0) % (travel * 2 + 2);
        double offset = phase < 1 ? 0 : phase < travel + 1 ? (phase - 1) * 24
                : phase < travel + 2 ? overflow : overflow - (phase - travel - 2) * 24;
        try (GuiClip clip = new GuiClip(left, y, available, 10)) {
            fontRendererObj.drawStringWithShadow(text, left - (float) offset, y, color);
        }
    }

    /** Shared setting renderer used by floating panels and the Skeet workspace. */
    private int drawSettingAt(Setting<?> setting, int left, int right, int y, int accent,
                              boolean futuristic, boolean skeet) {
        int base = futuristic ? 0xFA0A1628 : 0xA3111B2F;
        if (skeet) {
            base = 0xF5111114;
        }
        RenderUtils.roundedRect(left, y, right, y + 17, skeet ? 2.0F : 5.0F, base);
        int controlLeft = settingControlLeft(setting, left, right, skeet);
        drawScrollingLabel(setting.getName(), left + 4, y + 4, controlLeft - 4, RenderUtils.MUTED);
        if (setting instanceof BooleanSetting) {
            BooleanSetting value = (BooleanSetting) setting;
            String state = LanguageManager.translate(value.isEnabled() ? "ON" : "OFF");
            fontRendererObj.drawStringWithShadow(state, right - 5 - fontRendererObj.getStringWidth(state), y + 4,
                    value.isEnabled() ? accent : 0xFF667995);
            targets.add(new Target(TargetType.BOOLEAN, left, y, right, y + 17, null, setting, -1));
            return y + 18;
        }
        if (setting instanceof NumberSetting) {
            NumberSetting value = (NumberSetting) setting;
            int sliderLeft = controlLeft;
            int sliderRight = right - 5;
            float percent = (float) ((value.getDouble() - value.getMinimum()) / (value.getMaximum() - value.getMinimum()));
            RenderUtils.roundedRect(sliderLeft, y + 9, sliderRight, y + 13, 2.0F, 0xFF243550);
            RenderUtils.roundedRect(sliderLeft, y + 9, sliderLeft + Math.round((sliderRight - sliderLeft) * percent), y + 13, 2.0F, accent);
            String number = compact(value.getDouble());
            drawControlValue(number, sliderLeft, sliderRight, y + 1, RenderUtils.TEXT);
            targets.add(new Target(TargetType.NUMBER, sliderLeft, y, sliderRight, y + 17, null, setting, -1));
            return y + 18;
        }
        if (setting instanceof RangeSetting) {
            RangeSetting value = (RangeSetting) setting;
            int sliderLeft = controlLeft;
            int sliderRight = right - 5;
            float minimum = (float) ((value.getMin() - value.getMinimum()) / (value.getMaximum() - value.getMinimum()));
            float maximum = (float) ((value.getMax() - value.getMinimum()) / (value.getMaximum() - value.getMinimum()));
            int minX = sliderLeft + Math.round((sliderRight - sliderLeft) * minimum);
            int maxX = sliderLeft + Math.round((sliderRight - sliderLeft) * maximum);
            RenderUtils.roundedRect(sliderLeft, y + 9, sliderRight, y + 13, 2.0F, 0xFF243550);
            RenderUtils.roundedRect(minX, y + 9, maxX, y + 13, 2.0F, accent);
            Gui.drawRect(minX - 1, y + 8, minX + 1, y + 14, RenderUtils.TEXT);
            Gui.drawRect(maxX - 1, y + 8, maxX + 1, y + 14, RenderUtils.TEXT);
            String range = compact(value.getMin()) + " - " + compact(value.getMax());
            drawControlValue(range, sliderLeft, sliderRight, y + 1, RenderUtils.TEXT);
            targets.add(new Target(TargetType.RANGE, sliderLeft, y, sliderRight, y + 17, null, setting, -1));
            return y + 18;
        }
        if (setting instanceof ModeSetting) {
            ModeSetting value = (ModeSetting) setting;
            String mode = LanguageManager.translate(value.getValue());
            drawControlValue(mode, controlLeft, right - 5, y + 4, accent);
            targets.add(new Target(TargetType.MODE, left, y, right, y + 17, null, setting, -1));
            return y + 18;
        }
        if (setting instanceof ColorSetting) {
            ColorSetting value = (ColorSetting) setting;
            // Skeet uses the compact square source control from its original
            // layout. Futuristic retains its wide alpha-preview swatch.
            int swatchLeft = skeet ? right - 17 : right - 45;
            int swatchRight = skeet ? right - 4 : right - 4;
            RenderUtils.roundedRect(swatchLeft - 1, y + 2, right - 3, y + 15, 3.0F, 0xFF060B14);
            RenderUtils.transparencyGrid(swatchLeft, y + 3, swatchRight, y + 14, 3);
            RenderUtils.roundedRect(swatchLeft, y + 3, swatchRight, y + 14, 2.0F, value.getArgb());
            RenderUtils.roundedOutline(swatchLeft, y + 3, swatchRight, y + 14, 3.0F, 1.0F, 0x889AB6DA);
            targets.add(new Target(TargetType.COLOR_TOGGLE, swatchLeft, y, swatchRight, y + 17, null, setting, -1));
            y += 18;
            return y;
        }
        if (setting instanceof StringSetting) {
            String value = editing == setting ? editBuffer : ((StringSetting) setting).getValue();
            fontRendererObj.drawStringWithShadow(ellipsize(value, 15), right - 5 - fontRendererObj.getStringWidth(ellipsize(value, 15)), y + 4,
                    editing == setting ? RenderUtils.TEXT : RenderUtils.MUTED);
            targets.add(new Target(TargetType.TEXT, left, y, right, y + 17, null, setting, -1));
            return y + 18;
        }
        if (setting instanceof MultiSelectSetting) {
            MultiSelectSetting value = (MultiSelectSetting) setting;
            String count = value.getValue().size() + " " + LanguageManager.translate("selected");
            fontRendererObj.drawStringWithShadow(count, right - 5 - fontRendererObj.getStringWidth(count), y + 4, RenderUtils.MUTED);
            targets.add(new Target(TargetType.MULTI_TOGGLE, left, y, right, y + 17, null, setting, -1));
            y += 18;
            if (openSelections.contains(value)) {
                for (String option : value.getOptions()) {
                    RenderUtils.roundedRect(left + 4, y, right - 4, y + 12, 2.0F, 0xA30A1020);
                    String marker = value.isSelected(option) ? "[x] " : "[ ] ";
                    fontRendererObj.drawStringWithShadow(marker + ellipsize(LanguageManager.translate(option), 18), left + 7, y + 2,
                            value.isSelected(option) ? accent : RenderUtils.MUTED);
                    targets.add(new Target(TargetType.MULTI_OPTION, left + 4, y, right - 4, y + 12, null, setting, value.getOptions().indexOf(option)));
                    y += 13;
                }
            }
            return y;
        }
        return y + 18;
    }

    private int panelHeight(Category category) {
        int height = 27;
        for (Module module : Vibe.getInstance().getModuleManager().getModules(category)) {
            height += 20;
            if (expandedModules.contains(module)) {
                for (Setting<?> setting : module.getSettings()) {
                    if (!setting.isVisible()) {
                        continue;
                    }
                    height += 18;
                    if (setting instanceof MultiSelectSetting && openSelections.contains(setting)) {
                        height += ((MultiSelectSetting) setting).getOptions().size() * 13;
                    }
                }
            }
        }
        return height + 4;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (isXanaxTheme()) { xanax.click(mouseX, mouseY, mouseButton); return; }
        if (isNeverLoseTheme()) { neverLose.click(mouseX, mouseY, mouseButton); return; }
        if (isSigmaTheme()) { sigma.click(mouseX, mouseY, mouseButton); return; }
        if (isAugustusTheme()) {
            return;
        }
        if (colorPopup != null && colorPopup.contains(mouseX, mouseY)) {
            handleColorPopupClick(mouseX, mouseY, mouseButton);
            return;
        }
        if (isSkeetTheme()) {
            int boardWidth = boardWidth();
            int boardHeight = boardHeight();
            // The first card begins at skeetY + 5.  The old 26px drag region
            // covered that whole row, stealing clicks from the first modules.
            // Only the thin title strip is draggable, and never by right-click.
            if (mouseButton == 0 && contains(mouseX, mouseY, skeetX, skeetY - 5, skeetX + boardWidth, skeetY + 4)) {
                draggingSkeet = true;
                skeetDragOffsetX = mouseX - skeetX;
                skeetDragOffsetY = mouseY - skeetY;
                return;
            }
            if (mouseButton == 0 && contains(mouseX, mouseY, skeetX + boardWidth - 14, skeetY + boardHeight - 14,
                    skeetX + boardWidth, skeetY + boardHeight)) {
                resizingSkeet = true;
                return;
            }
        } else if (isFuturisticTheme()) {
            for (Category category : Category.values()) {
                Panel panel = panels.get(category);
                if (mouseButton == 0 && panel != null && contains(mouseX, mouseY, panel.x, panel.y, panel.x + PANEL_WIDTH, panel.y + 24)) {
                    dragging = panel;
                    dragOffsetX = mouseX - panel.x;
                    dragOffsetY = mouseY - panel.y;
                    return;
                }
            }
        }
        for (int index = targets.size() - 1; index >= 0; index--) {
            Target target = targets.get(index);
            if (isSkeetTheme() && target.type != TargetType.CATEGORY && !insideSkeetContent(mouseX, mouseY)) {
                continue;
            }
            if (!target.contains(mouseX, mouseY)) {
                continue;
            }
            applyTarget(target, mouseX, mouseButton);
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void applyTarget(Target target, int mouseX, int mouseButton) {
        if (target.type == TargetType.CATEGORY) {
            if (target.channel >= 0 && target.channel < Category.values().length) {
                skeetCategory = Category.values()[target.channel];
                skeetScroll = 0;
            }
            return;
        }
        if (target.type == TargetType.MODULE) {
            if (mouseButton == 1) {
                if (hasVisibleSettings(target.module)) {
                    if (!expandedModules.add(target.module)) {
                        expandedModules.remove(target.module);
                        if (colorPopup != null && target.module.getSettings().contains(colorPopup.setting)) {
                            colorPopup = null;
                            draggingColor = null;
                            editing = null;
                        }
                    }
                }
            } else if (mouseButton == 2) {
                bindingModule = target.module;
            } else {
                target.module.toggle();
            }
            return;
        }
        Setting<?> setting = target.setting;
        if (target.type == TargetType.BOOLEAN) {
            ((BooleanSetting) setting).toggle();
        } else if (target.type == TargetType.NUMBER) {
            draggingSlider = target;
            updateSlider(target, mouseX);
        } else if (target.type == TargetType.RANGE) {
            draggingSlider = target;
            updateSlider(target, mouseX);
        } else if (target.type == TargetType.MODE) {
            ((ModeSetting) setting).cycle(mouseButton == 1);
        } else if (target.type == TargetType.COLOR_TOGGLE) {
            ColorSetting color = (ColorSetting) setting;
            if (colorPopup != null && colorPopup.setting == color) {
                colorPopup = null;
                draggingColor = null;
                editing = null;
                return;
            }
            colorPopup = new ColorPopup(color, Math.min(width - COLOR_POPUP_WIDTH - 4, target.right + 6),
                    Math.max(36, Math.min(height - COLOR_POPUP_HEIGHT - 4, target.top)));
        } else if (target.type == TargetType.TEXT) {
            commitEditing();
            editing = setting;
            editBuffer = setting instanceof ColorSetting ? ((ColorSetting) setting).getHex() : ((StringSetting) setting).getValue();
        } else if (target.type == TargetType.MULTI_TOGGLE) {
            MultiSelectSetting multi = (MultiSelectSetting) setting;
            if (!openSelections.add(multi)) {
                openSelections.remove(multi);
            }
        } else if (target.type == TargetType.MULTI_OPTION) {
            MultiSelectSetting multi = (MultiSelectSetting) setting;
            if (target.channel >= 0 && target.channel < multi.getOptions().size()) {
                multi.toggle(multi.getOptions().get(target.channel));
            }
        }
        Vibe.getInstance().getConfig().save(Vibe.getInstance().getModuleManager());
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        neverLose.release(state);
        xanax.release(state);
        if (isAugustusTheme()) {
            super.mouseReleased(mouseX, mouseY, state);
            return;
        }
        if (state == 0) {
            dragging = null;
            draggingSkeet = false;
            resizingSkeet = false;
            if (draggingSlider != null) {
                Vibe.getInstance().getConfig().save(Vibe.getInstance().getModuleManager());
                draggingSlider = null;
            }
            if (draggingColor != null) {
                Vibe.getInstance().getConfig().save(Vibe.getInstance().getModuleManager());
            }
            draggingColor = null;
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void updateScreen() {
        if (draggingSkeet) {
            int boardWidth = boardWidth();
            int boardHeight = boardHeight();
            skeetX = Math.max(8, Math.min(width - boardWidth - 8, mouseX() - skeetDragOffsetX));
            skeetY = Math.max(32, Math.min(height - boardHeight - 8, mouseY() - skeetDragOffsetY));
        }
        if (resizingSkeet) {
            skeetWidth = Math.max(SKEET_MIN_WIDTH, Math.min(width - skeetX - 8, mouseX() - skeetX));
            skeetHeight = Math.max(SKEET_MIN_HEIGHT, Math.min(height - skeetY - 8, mouseY() - skeetY));
        }
        if (dragging != null) {
            int desiredX = mouseX() - dragOffsetX;
            int desiredY = mouseY() - dragOffsetY;
            // Keep the exact user-selected position. Snapping made panels
            // jump away from a grid cell while the cursor was still dragging.
            dragging.x = Math.max(0, Math.min(width - PANEL_WIDTH, desiredX));
            dragging.y = Math.max(32, Math.min(height - 25, desiredY));
        }
        super.updateScreen();
    }

    private int mouseX() {
        return org.lwjgl.input.Mouse.getX() * width / mc.displayWidth;
    }

    private int mouseY() {
        return height - org.lwjgl.input.Mouse.getY() * height / mc.displayHeight - 1;
    }

    @Override
    public void handleMouseInput() throws IOException {
        if (isAugustusTheme()) {
            AugustusImGui.handleMouse();
            return;
        }
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        if (isXanaxTheme()) { xanax.wheel(mouseX(), mouseY(), wheel); return; }
        if (isSigmaTheme()) { sigma.wheel(mouseX(), mouseY(), wheel); return; }
        if (isSkeetTheme()) {
            int boardWidth = boardWidth();
            int boardHeight = boardHeight();
            int contentLeft = skeetX + SKEET_SIDEBAR_WIDTH + 8;
            int contentTop = skeetY + 5;
            int contentRight = skeetX + boardWidth - 6;
            int contentBottom = skeetY + boardHeight - 5;
            if (skeetMaxScroll > 0 && contains(mouseX(), mouseY(), contentLeft, contentTop, contentRight, contentBottom)) {
                int next = Math.max(0, Math.min(skeetMaxScroll, skeetScroll + (wheel > 0 ? -22 : 22)));
                if (next != skeetScroll) {
                    skeetScroll = next;
                }
            }
            return;
        }
        if (isNeverLoseTheme()) {
            neverLose.wheel(mouseX(), mouseY(), wheel);
            return;
        }
        int pointerX = mouseX();
        int pointerY = mouseY();
        if (!isOverFuturisticPanel(pointerX, pointerY)) return;
        // Futuristic panels are an unconstrained workspace.  Wheel input
        // scrolls the workspace directly, independently of card height.
        int delta = wheel > 0 ? 18 : -18;
        for (Panel panel : panels.values()) panel.y += delta;
    }

    @Override
    public void handleKeyboardInput() throws IOException {
        if (isAugustusTheme()) {
            // GuiScreen only forwards pressed keys to keyTyped(), whereas
            // ImGui also needs raw releases for text widgets and modifiers.
            if (AugustusImGui.handleKey()) return;
            if (Keyboard.getEventKeyState()) {
                int key = Keyboard.getEventKey();
                if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RSHIFT) {
                    mc.displayGuiScreen(null);
                }
            }
            return;
        }
        super.handleKeyboardInput();
    }

    private boolean isOverFuturisticPanel(int mouseX, int mouseY) {
        for (Panel panel : panels.values()) {
            if (contains(mouseX, mouseY, panel.x, panel.y, panel.x + PANEL_WIDTH, panel.y + panelHeight(panel.category))) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (isXanaxTheme()) {
            if (!xanax.key(typedChar, keyCode) && (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RSHIFT)) mc.displayGuiScreen(null);
            return;
        }
        if (isNeverLoseTheme()) {
            if (!neverLose.key(typedChar, keyCode) && (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RSHIFT)) mc.displayGuiScreen(null);
            return;
        }
        if (isSigmaTheme()) {
            if (!sigma.key(typedChar, keyCode) && (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RSHIFT)) mc.displayGuiScreen(null);
            return;
        }
        if (isAugustusTheme()) {
            AugustusImGui.handleKey();
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RSHIFT) {
                mc.displayGuiScreen(null);
            }
            return;
        }
        if (bindingModule != null) {
            bindingModule.setKey(keyCode == Keyboard.KEY_ESCAPE ? Keyboard.KEY_NONE : keyCode);
            bindingModule = null;
            Vibe.getInstance().getConfig().save(Vibe.getInstance().getModuleManager());
            return;
        }
        if (editing != null) {
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                commitEditing();
                editing = null;
                return;
            }
            if (keyCode == Keyboard.KEY_ESCAPE) {
                editing = null;
                return;
            }
            if (keyCode == Keyboard.KEY_BACK && !editBuffer.isEmpty()) {
                editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
                commitEditing();
                return;
            }
            if (typedChar >= 32 && typedChar <= 126 && editBuffer.length() < 96) {
                editBuffer += typedChar;
                commitEditing();
            }
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RSHIFT) {
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void onGuiClosed() {
        neverLose.close();
        sigma.close();
        xanax.close();
        AugustusImGui.closed();
        commitEditing();
        SAVED_PANELS.clear();
        for (Panel panel : panels.values()) {
            SAVED_PANELS.put(panel.category, new int[] {panel.x, panel.y});
        }
        SAVED_EXPANDED.clear();
        for (Module module : expandedModules) {
            SAVED_EXPANDED.add(module.getId());
        }
        SAVED_OPEN_SETTINGS.clear();
        for (Module module : Vibe.getInstance().getModuleManager().getModules()) {
            for (Setting<?> setting : module.getSettings()) {
                if (setting instanceof MultiSelectSetting && openSelections.contains(setting)) {
                    SAVED_OPEN_SETTINGS.add(settingKey(module, setting));
                }
            }
        }
        SAVED_SKEET_X = skeetX;
        SAVED_SKEET_Y = skeetY;
        SAVED_SKEET_SCROLL = skeetScroll;
        SAVED_SKEET_WIDTH = skeetWidth;
        SAVED_SKEET_HEIGHT = skeetHeight;
        SAVED_SKEET_CATEGORY = skeetCategory;
        Vibe.getInstance().getConfig().save(Vibe.getInstance().getModuleManager());
        ClickGuiModule clickGui = Vibe.getInstance().getModuleManager().getModule(ClickGuiModule.class);
        if (clickGui != null) clickGui.setEnabled(false);
        super.onGuiClosed();
    }

    /** Text controls are live: every keystroke updates and persists the setting. */
    private void commitEditing() {
        if (editing instanceof ColorSetting) {
            ((ColorSetting) editing).setHex(editBuffer);
        } else if (editing instanceof StringSetting) {
            ((StringSetting) editing).setValue(editBuffer);
        }
        if (editing != null && Vibe.getInstance().getConfig() != null) {
            Vibe.getInstance().getConfig().save(Vibe.getInstance().getModuleManager());
        }
    }

    private boolean hasVisibleSettings(Module module) {
        for (Setting<?> setting : module.getSettings()) {
            if (setting.isVisible()) {
                return true;
            }
        }
        return false;
    }

    private String settingKey(Module module, Setting<?> setting) {
        return module.getId() + ":" + setting.getRawName();
    }

    private void drawColorPopup(ColorPopup popup) {
        int left = popup.left;
        int top = popup.top;
        int squareLeft = left + 6;
        int squareTop = top + 16;
        int squareSize = 76;
        RenderUtils.roundedRect(left - 2, top - 2, left + COLOR_POPUP_WIDTH + 2, top + COLOR_POPUP_HEIGHT + 2, 7.0F, 0xFF050912);
        RenderUtils.roundedRect(left, top, left + COLOR_POPUP_WIDTH, top + COLOR_POPUP_HEIGHT, 6.0F, 0xFF101C30);
        RenderUtils.roundedTopRect(left, top, left + COLOR_POPUP_WIDTH, top + 14, 6.0F, 0xFF172A46);
        RenderUtils.roundedOutline(left, top, left + COLOR_POPUP_WIDTH, top + COLOR_POPUP_HEIGHT, 6.0F, 1.0F, 0xFF354B6B);
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("COLOR"), left + 6, top + 4, RenderUtils.TEXT);
        for (int row = 0; row < 10; row++) {
            for (int column = 0; column < 10; column++) {
                float saturation = column / 9.0F;
                float brightness = 1.0F - row / 9.0F;
                int rgb = Color.HSBtoRGB(popup.hue, saturation, brightness) | 0xFF000000;
                int x = squareLeft + Math.round(column * squareSize / 10.0F);
                int y = squareTop + Math.round(row * squareSize / 10.0F);
                Gui.drawRect(x, y, squareLeft + Math.round((column + 1) * squareSize / 10.0F),
                        squareTop + Math.round((row + 1) * squareSize / 10.0F), rgb);
            }
        }
        int selectorX = squareLeft + Math.round(popup.saturation * squareSize);
        int selectorY = squareTop + Math.round((1.0F - popup.brightness) * squareSize);
        drawColorSelector(selectorX, selectorY);
        RenderUtils.roundedRect(left + 86, squareTop - 2, left + 111, squareTop + squareSize + 2, 4.0F, 0xFF060B14);
        RenderUtils.transparencyGrid(left + 88, squareTop, left + 109, squareTop + squareSize, 3);
        RenderUtils.roundedRect(left + 88, squareTop, left + 109, squareTop + squareSize, 2.0F, popup.setting.getArgb());
        int hueTop = squareTop + squareSize + 5;
        for (int segment = 0; segment < 12; segment++) {
            int color = Color.HSBtoRGB(segment / 12.0F, 0.90F, 1.0F) | 0xFF000000;
            int x = squareLeft + Math.round(segment * squareSize / 12.0F);
            Gui.drawRect(x, hueTop, squareLeft + Math.round((segment + 1) * squareSize / 12.0F), hueTop + 8, color);
        }
        int hueX = squareLeft + Math.round(popup.hue * squareSize);
        Gui.drawRect(hueX - 1, hueTop - 2, hueX + 2, hueTop + 10, 0xFFFFFFFF);
        int alphaTop = hueTop + 14;
        RenderUtils.roundedRect(squareLeft - 1, alphaTop - 1, squareLeft + squareSize + 1, alphaTop + 9, 3.0F, 0xFF060B14);
        RenderUtils.transparencyGrid(squareLeft, alphaTop, squareLeft + squareSize, alphaTop + 8, 3);
        int opaque = popup.setting.getArgb() | 0xFF000000;
        for (int segment = 0; segment < squareSize; segment += 2) {
            int alpha = Math.round((segment + 2) * 255.0F / squareSize);
            Gui.drawRect(squareLeft + segment, alphaTop, Math.min(squareLeft + squareSize, squareLeft + segment + 2), alphaTop + 8,
                    RenderUtils.alpha(opaque, alpha));
        }
        int alphaX = squareLeft + Math.round(popup.setting.getAlpha() * squareSize / 255.0F);
        Gui.drawRect(alphaX - 1, alphaTop - 2, alphaX + 2, alphaTop + 10, 0xFFFFFFFF);
        RenderUtils.roundedRect(squareLeft, top + 127, left + 109, top + 142, 3.0F, 0xFF0A1020);
        String hex = editing == popup.setting ? editBuffer : popup.setting.getHex();
        fontRendererObj.drawStringWithShadow(hex, squareLeft + 4, top + 131, editing == popup.setting ? RenderUtils.TEXT : RenderUtils.MUTED);
    }

    private void handleColorPopupClick(int mouseX, int mouseY, int mouseButton) {
        int squareLeft = colorPopup.left + 6;
        int squareTop = colorPopup.top + 16;
        if (contains(mouseX, mouseY, squareLeft, squareTop, squareLeft + 76, squareTop + 76)) {
            draggingColor = ColorPopupPart.SATURATION_BRIGHTNESS;
            updateColorPopup(mouseX, mouseY);
            return;
        }
        int hueTop = squareTop + 76 + 5;
        if (contains(mouseX, mouseY, squareLeft, hueTop - 2, squareLeft + 76, hueTop + 10)) {
            draggingColor = ColorPopupPart.HUE;
            updateColorPopup(mouseX, mouseY);
            return;
        }
        int alphaTop = hueTop + 14;
        if (contains(mouseX, mouseY, squareLeft, alphaTop - 2, squareLeft + 76, alphaTop + 10)) {
            draggingColor = ColorPopupPart.ALPHA;
            updateColorPopup(mouseX, mouseY);
            return;
        }
        if (contains(mouseX, mouseY, squareLeft, colorPopup.top + 126, colorPopup.left + 110, colorPopup.top + 143)) {
            editing = colorPopup.setting;
            editBuffer = colorPopup.setting.getHex();
        }
    }

    private void updateColorPopup(int mouseX, int mouseY) {
        if (draggingColor == ColorPopupPart.SATURATION_BRIGHTNESS) {
            colorPopup.saturation = Math.max(0.0F, Math.min(1.0F, (mouseX - colorPopup.left - 6) / 76.0F));
            colorPopup.brightness = Math.max(0.0F, Math.min(1.0F, 1.0F - (mouseY - colorPopup.top - 16) / 76.0F));
        } else if (draggingColor == ColorPopupPart.HUE) {
            colorPopup.hue = Math.max(0.0F, Math.min(1.0F, (mouseX - colorPopup.left - 6) / 76.0F));
        }
        int rgb = Color.HSBtoRGB(colorPopup.hue, colorPopup.saturation, colorPopup.brightness);
        int alpha = draggingColor == ColorPopupPart.ALPHA
                ? Math.max(0, Math.min(255, Math.round((mouseX - colorPopup.left - 6) * 255.0F / 76.0F)))
                : colorPopup.setting.getAlpha();
        colorPopup.setting.setRgba((rgb >>> 16) & 255, (rgb >>> 8) & 255, rgb & 255, alpha);
    }

    /** Pixel-circle marker: legible against any saturation/brightness value. */
    private void drawColorSelector(int x, int y) {
        Gui.drawRect(x - 2, y - 4, x + 3, y + 5, 0xFF101827);
        Gui.drawRect(x - 4, y - 2, x + 5, y + 3, 0xFF101827);
        Gui.drawRect(x - 1, y - 3, x + 2, y + 4, 0xFFFFFFFF);
        Gui.drawRect(x - 3, y - 1, x + 4, y + 2, 0xFFFFFFFF);
        Gui.drawRect(x - 1, y - 1, x + 2, y + 2, 0xFF101827);
    }

    private int guiAccent(float phase) {
        ClickGuiModule clickGui = Vibe.getInstance().getModuleManager().getModule(ClickGuiModule.class);
        if (clickGui == null) {
            return RenderUtils.accent(phase);
        }
        float time = (System.currentTimeMillis() % 5500L) / 5500.0F;
        float blend = (float) ((Math.sin((time + phase) * Math.PI * 2.0D) + 1.0D) * 0.5D);
        return RenderUtils.blend(clickGui.getPrimaryColor().getArgb(), clickGui.getSecondaryColor().getArgb(), blend);
    }

    private void updateSlider(Target target, int mouseX) {
        double ratio = Math.max(0.0D, Math.min(1.0D,
                (mouseX - target.left) / (double) Math.max(1, target.right - target.left)));
        if (target.type == TargetType.NUMBER) {
            NumberSetting number = (NumberSetting) target.setting;
            number.setValue(number.getMinimum() + (number.getMaximum() - number.getMinimum()) * ratio);
        } else if (target.type == TargetType.RANGE) {
            RangeSetting range = (RangeSetting) target.setting;
            double value = range.getMinimum() + (range.getMaximum() - range.getMinimum()) * ratio;
            if (Math.abs(value - range.getMin()) <= Math.abs(value - range.getMax())) {
                range.setMin(Math.min(value, range.getMax()));
            } else {
                range.setMax(Math.max(value, range.getMin()));
            }
        }
    }

    private String compact(double value) {
        return Math.abs(value - Math.rint(value)) < 0.001D ? Integer.toString((int) Math.rint(value))
                : String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private String ellipsize(String value, int max) {
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }

    private boolean contains(int x, int y, int left, int top, int right, int bottom) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    private boolean isFuturisticTheme() {
        ClickGuiModule clickGui = Vibe.getInstance().getModuleManager().getModule(ClickGuiModule.class);
        return clickGui != null && clickGui.getTheme().is("Futuristic");
    }

    private boolean isSkeetTheme() {
        ClickGuiModule clickGui = Vibe.getInstance().getModuleManager().getModule(ClickGuiModule.class);
        return clickGui == null || clickGui.getTheme().is("Skeet");
    }

    private boolean isNeverLoseTheme() {
        ClickGuiModule clickGui = Vibe.getInstance().getModuleManager().getModule(ClickGuiModule.class);
        return clickGui != null && clickGui.getTheme().is("NeverLose");
    }

    private boolean isSigmaTheme() {
        ClickGuiModule clickGui = Vibe.getInstance().getModuleManager().getModule(ClickGuiModule.class);
        return clickGui != null && clickGui.getTheme().is("Sigma");
    }

    private boolean isAugustusTheme() {
        ClickGuiModule clickGui = Vibe.getInstance().getModuleManager().getModule(ClickGuiModule.class);
        return clickGui != null && clickGui.getTheme().is("Augustus");
    }

    private boolean isXanaxTheme() {
        ClickGuiModule clickGui = Vibe.getInstance().getModuleManager().getModule(ClickGuiModule.class);
        return clickGui != null && clickGui.getTheme().is("Xanax");
    }

    private boolean insideSkeetContent(int mouseX, int mouseY) {
        int boardWidth = boardWidth();
        int boardHeight = boardHeight();
        return contains(mouseX, mouseY, skeetX + SKEET_SIDEBAR_WIDTH + 8, skeetY + 5,
                skeetX + boardWidth - 6, skeetY + boardHeight - 5);
    }

    private int boardWidth() {
        return Math.max(SKEET_MIN_WIDTH, Math.min(skeetWidth, Math.max(SKEET_MIN_WIDTH, width - 16)));
    }

    private int boardHeight() {
        return Math.max(120, Math.min(skeetHeight, Math.max(120, height - 40)));
    }

    private enum TargetType {
        CATEGORY, MODULE, BOOLEAN, NUMBER, RANGE, MODE, COLOR_TOGGLE, TEXT, MULTI_TOGGLE, MULTI_OPTION
    }

    private enum ColorPopupPart {
        SATURATION_BRIGHTNESS, HUE, ALPHA
    }

    private static final class ColorPopup {
        private final ColorSetting setting;
        private final int left;
        private final int top;
        private float hue;
        private float saturation;
        private float brightness;

        private ColorPopup(ColorSetting setting, int left, int top) {
            this.setting = setting;
            this.left = left;
            this.top = top;
            float[] hsb = Color.RGBtoHSB(setting.getRed(), setting.getGreen(), setting.getBlue(), null);
            hue = hsb[0];
            saturation = hsb[1];
            brightness = hsb[2];
        }

        private boolean contains(int x, int y) {
            return x >= left && x < left + COLOR_POPUP_WIDTH && y >= top && y < top + COLOR_POPUP_HEIGHT;
        }
    }

    private static final class Target {
        private final TargetType type;
        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final Module module;
        private final Setting<?> setting;
        private final int channel;

        private Target(TargetType type, int left, int top, int right, int bottom, Module module, Setting<?> setting, int channel) {
            this.type = type;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.module = module;
            this.setting = setting;
            this.channel = channel;
        }

        private boolean contains(int x, int y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }

    private static final class Panel {
        private final Category category;
        private int x;
        private int y;
        private int height;

        private Panel(Category category, int x, int y) {
            this.category = category;
            this.x = x;
            this.y = y;
        }
    }
}
