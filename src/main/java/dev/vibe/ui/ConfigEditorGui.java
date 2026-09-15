package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.config.VibeConfig.ConfigInfo;
import dev.vibe.module.impl.BlurModule;
import dev.vibe.module.impl.ConfigEditorModule;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

/** Searchable profile manager with creator/audit metadata. */
public final class ConfigEditorGui extends GuiScreen {
    private final ConfigEditorModule module;
    private final ParticlesRenderer particles = new ParticlesRenderer();
    private final List<String> configs = new ArrayList<String>();
    private GuiTextField name;
    private GuiTextField search;
    private String selected;
    private int left, top, panelWidth;
    private static boolean loadWithKeybinds = true, loadWithVisuals = true;

    public ConfigEditorGui(ConfigEditorModule module) { this.module = module; }

    private float uiScale = 1;
    @Override public void initGui() {
        uiScale = Math.min(1F, Math.min(width / 560F, height / 400F));
        int canvasWidth = Math.round(width / uiScale), canvasHeight = Math.round(height / uiScale);
        panelWidth = Math.max(540, Math.min(670, canvasWidth - 18));
        left = Math.max(9, (canvasWidth - panelWidth) / 2);
        top = Math.max(18, (canvasHeight - 365) / 2);
        search = new GuiTextField(0, fontRendererObj, left + 14, top + 52, 226, 17);
        search.setMaxStringLength(48);
        search.setEnableBackgroundDrawing(false);
        name = new GuiTextField(1, fontRendererObj, left + 14, top + 328, 190, 18);
        name.setMaxStringLength(48);
        name.setEnableBackgroundDrawing(false);
        refresh();
    }

    private void refresh() {
        configs.clear(); configs.addAll(Vibe.getInstance().getConfig().list());
        if (selected != null && !configs.contains(selected)) selected = null;
    }

    private List<String> visibleConfigs() {
        String needle = search == null ? "" : search.getText().trim().toLowerCase(java.util.Locale.ROOT);
        if (needle.isEmpty()) return configs;
        List<String> found = new ArrayList<String>();
        for (String config : configs) if (config.toLowerCase(java.util.Locale.ROOT).contains(needle)) found.add(config);
        return found;
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        SkeetEditorStyle.backdrop(this, BlurModule.CONFIG_EDITOR, partialTicks);
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.scale(uiScale, uiScale, 1);
        SkeetEditorStyle.window(left, top, left + panelWidth, top + 365, "Config editor", "profiles • metadata • authors");
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("Search"), left + 14, top + 39, SkeetEditorStyle.MUTED);
        SkeetEditorStyle.input(left + 12, top + 50, left + 242, top + 70);
        search.drawTextBox();
        drawConfigList();
        drawMetadata();
        button(left + panelWidth - 14 - buttonWidth("Open folder"), top + 328, "Open folder", 0xFF5BE8A6);
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("Name (new / rename)"), left + 14, top + 315, SkeetEditorStyle.MUTED);
        SkeetEditorStyle.input(left + 12, top + 326, left + 206, top + 348);
        name.drawTextBox();
        button(left + 212, top + 328, "New", 0xFF2DE2C2);
        net.minecraft.client.renderer.GlStateManager.popMatrix();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawConfigList() {
        int listLeft = left + 12, listTop = top + 75, listRight = left + 252, listBottom = top + 306;
        SkeetEditorStyle.panel(listLeft, listTop, listRight, listBottom, "Configs");
        // Reserve the panel caption strip.  Starting rows inside it made the
        // first config name visually overlap the "Configs" label.
        int y = listTop + 20;
        for (String config : visibleConfigs()) {
            if (y + 21 > listBottom) break;
            boolean active = config.equals(selected);
            SkeetEditorStyle.row(listLeft + 4, y, listRight - 4, y + 19, active, false);
            fontRendererObj.drawStringWithShadow(trim(config + ".json", listRight - listLeft - 18), listLeft + 10, y + 6, active ? SkeetEditorStyle.accent(0.1F) : SkeetEditorStyle.TEXT);
            y += 22;
        }
        if (visibleConfigs().isEmpty()) fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("No matching configs"), listLeft + 12, listTop + 22, RenderUtils.MUTED);
    }

    private void drawMetadata() {
        int detailsLeft = left + 270, detailsRight = left + panelWidth - 14;
        SkeetEditorStyle.panel(detailsLeft, top + 42, detailsRight, top + 306, "Details");
        if (selected == null) {
            fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("Select a configuration to inspect it."), detailsLeft + 13, top + 62, RenderUtils.MUTED);
            return;
        }
        ConfigInfo info = Vibe.getInstance().getConfig().getInfo(selected);
        // The details panel has its own caption.  Keep all metadata below its
        // divider, rather than drawing the file name into the caption itself.
        fontRendererObj.drawStringWithShadow(trim(info.getName() + ".json", detailsRight - detailsLeft - 24), detailsLeft + 13, top + 63, SkeetEditorStyle.accent(0.1F));
        metadataLine("Creator", info.getCreator(), detailsLeft + 13, top + 86, detailsRight - detailsLeft - 26);
        metadataLine("Authors", join(info.getAuthors()), detailsLeft + 13, top + 112, detailsRight - detailsLeft - 26);
        metadataLine("Created", info.getCreatedAt(), detailsLeft + 13, top + 138, detailsRight - detailsLeft - 26);
        metadataLine("Latest save", info.getSavedAt(), detailsLeft + 13, top + 164, detailsRight - detailsLeft - 26);
        button(detailsLeft + 13, top + 198, "Load", 0xFF2DE2C2);
        button(detailsLeft + 19 + buttonWidth("Load"), top + 198, "Save", 0xFFA855F7);
        button(detailsLeft + 25 + buttonWidth("Load") + buttonWidth("Save"), top + 198, "Delete", 0xFFFF6A82);
        button(detailsLeft + 13, top + 223, "Rename", 0xFF60D5FF);
        toggle(detailsLeft + 13, top + 249, "Load with Keybinds", loadWithKeybinds);
        toggle(detailsLeft + 13, top + 277, "Load with Visuals", loadWithVisuals);

    }

    private void metadataLine(String title, String value, int x, int y, int width) {
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate(title), x, y, SkeetEditorStyle.MUTED);
        fontRendererObj.drawStringWithShadow(trim(value == null || value.isEmpty() ? dev.vibe.language.LanguageManager.translate("Unknown") : value, width), x, y + 10, SkeetEditorStyle.TEXT);
    }

    private int buttonWidth(String text) { return Math.max(46, fontRendererObj.getStringWidth(dev.vibe.language.LanguageManager.translate(text)) + 18); }
    private void button(int x, int y, String text, int color) {
        int w = buttonWidth(text);
        RenderUtils.roundedRect(x, y, x + w, y + 22, 4, 0xFF252329);
        RenderUtils.roundedOutline(x, y, x + w, y + 22, 4, 1, color);
        String label = dev.vibe.language.LanguageManager.translate(text);
        fontRendererObj.drawStringWithShadow(label, x + (w - fontRendererObj.getStringWidth(label)) / 2, y + 7, SkeetEditorStyle.TEXT);
    }
    private void toggle(int x, int y, String label, boolean enabled) {
        SkeetEditorStyle.row(x, y, x + 14, y + 14, enabled, false);
        if (enabled) fontRendererObj.drawString(dev.vibe.language.LanguageManager.translate("x"), x + 4, y + 3, SkeetEditorStyle.accent(0));
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate(label), x + 21, y + 3, SkeetEditorStyle.TEXT);
    }
    private boolean in(int x, int y, int w, int h, int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    private String trim(String value, int width) { return fontRendererObj.trimStringToWidth(value, Math.max(0, width)); }
    private String join(List<String> values) { if (values == null || values.isEmpty()) return "None"; StringBuilder result = new StringBuilder(); for (String value : values) { if (result.length() > 0) result.append(", "); result.append(value); } return result.toString(); }

    @Override protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        mouseX = Math.round(mouseX / uiScale); mouseY = Math.round(mouseY / uiScale);
        search.mouseClicked(mouseX, mouseY, mouseButton); name.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0) { super.mouseClicked(mouseX, mouseY, mouseButton); return; }
        int y = top + 95;
        for (String config : visibleConfigs()) {
            if (y + 19 > top + 306) break;
            if (in(left + 16, y, 232, 19, mouseX, mouseY)) { selected = config; name.setText(config); return; }
            y += 22;
        }
        String typed = name.getText().trim();
        int detailsLeft = left + 270;
        if (in(left + 212, top + 328, buttonWidth("New"), 22, mouseX, mouseY)) {
            if (!typed.isEmpty() && Vibe.getInstance().getConfig().save(typed, Vibe.getInstance().getModuleManager())) { selected = typed; refresh(); }
        } else if (selected != null && in(detailsLeft + 13, top + 198, buttonWidth("Load"), 22, mouseX, mouseY)) {
            Vibe.getInstance().getConfig().load(selected, Vibe.getInstance().getModuleManager(), loadWithKeybinds, loadWithVisuals);
        } else if (selected != null && in(detailsLeft + 19 + buttonWidth("Load"), top + 198, buttonWidth("Save"), 22, mouseX, mouseY)) {
            Vibe.getInstance().getConfig().save(selected, Vibe.getInstance().getModuleManager()); refresh();
        } else if (selected != null && in(detailsLeft + 25 + buttonWidth("Load") + buttonWidth("Save"), top + 198, buttonWidth("Delete"), 22, mouseX, mouseY)) {
            Vibe.getInstance().getConfig().delete(selected); refresh();
        } else if (selected != null && !typed.isEmpty() && in(detailsLeft + 13, top + 223, buttonWidth("Rename"), 22, mouseX, mouseY)) {
            if (Vibe.getInstance().getConfig().rename(selected, typed)) { selected = typed; refresh(); }
        } else if (in(left + panelWidth - 14 - buttonWidth("Open folder"), top + 328, buttonWidth("Open folder"), 22, mouseX, mouseY)) {
            Vibe.getInstance().getConfig().openDirectory();
        } else if (selected != null && in(detailsLeft + 13, top + 249, panelWidth - 310, 20, mouseX, mouseY)) loadWithKeybinds = !loadWithKeybinds;
        else if (selected != null && in(detailsLeft + 13, top + 277, panelWidth - 310, 20, mouseX, mouseY)) loadWithVisuals = !loadWithVisuals;
        else super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(null); return; }
        if (search.isFocused()) search.textboxKeyTyped(typedChar, keyCode); else name.textboxKeyTyped(typedChar, keyCode);
    }
    @Override public void onGuiClosed() { if (module.isEnabled()) module.setEnabled(false); super.onGuiClosed(); }
    @Override public boolean doesGuiPauseGame() { return false; }
}
