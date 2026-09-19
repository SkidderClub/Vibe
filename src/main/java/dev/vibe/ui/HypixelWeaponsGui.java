package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.impl.BlurModule;
import dev.vibe.module.impl.HypixelModule;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Searchable Skeet item picker for Murder Mystery weapon signatures. */
public final class HypixelWeaponsGui extends GuiScreen {
    private final HypixelModule module;
    private final Set<String> selected = new LinkedHashSet<String>();
    private final List<Choice> catalog = new ArrayList<Choice>();
    private final List<Tag> tags = new ArrayList<Tag>();
    private GuiTextField search;
    private int left, top, right, bottom, listTop, listBottom, scroll;

    public HypixelWeaponsGui(HypixelModule module) { this.module = module; }

    @Override public void initGui() {
        int windowWidth = Math.min(620, Math.max(390, width - 24));
        int windowHeight = Math.min(430, Math.max(286, height - 24));
        left = (width - windowWidth) / 2; right = left + windowWidth;
        top = (height - windowHeight) / 2; bottom = top + windowHeight;
        for (String value : module.getMurderWeapons().getValue().split(",")) {
            String id = normalize(value); if (!id.isEmpty()) selected.add(id);
        }
        catalog.clear();
        for (Object value : Item.itemRegistry.getKeys()) {
            if (!(value instanceof ResourceLocation)) continue;
            ResourceLocation location = (ResourceLocation) value;
            Item item = (Item) Item.itemRegistry.getObject(location);
            if (item == null) continue;
            String id = location.getResourceDomain().equals("minecraft") ? location.getResourcePath() : location.toString();
            String label;
            try { label = EnumChatFormatting.getTextWithoutFormattingCodes(new ItemStack(item).getDisplayName()); }
            catch (RuntimeException ignored) { label = prettify(id); }
            catalog.add(new Choice(id, label == null || label.trim().isEmpty() ? prettify(id) : label));
        }
        Collections.sort(catalog, Comparator.comparing((Choice c) -> c.label.toLowerCase(Locale.ROOT)).thenComparing(c -> c.id));
        search = new GuiTextField(0, fontRendererObj, left + 21, top + 157, right - left - 42, 16);
        search.setEnableBackgroundDrawing(false); search.setMaxStringLength(80); search.setFocused(true);
        listTop = top + 218; listBottom = bottom - 48;
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        SkeetEditorStyle.backdrop(this, BlurModule.CLICK_GUI, partialTicks);
        SkeetEditorStyle.window(left, top, right, bottom, "Murderer weapons", "Search vanilla items and add the weapon signatures to watch");

        SkeetEditorStyle.panel(left + 12, top + 31, right - 12, top + 126, "Selected weapons");
        fontRendererObj.drawString("Click an item below to add it. Click a selected tag to remove it.", left + 20, top + 51, SkeetEditorStyle.MUTED);
        drawTags(mouseX, mouseY);

        SkeetEditorStyle.panel(left + 12, top + 137, right - 12, top + 207, "Find a vanilla item");
        SkeetEditorStyle.input(left + 16, top + 153, right - 16, top + 177);
        search.drawTextBox();
        fontRendererObj.drawString("Type a display name or item ID, for example sword, shears, blaze rod", left + 20, top + 184, SkeetEditorStyle.MUTED);

        SkeetEditorStyle.panel(left + 12, top + 218, right - 12, bottom - 48, "Matching items");
        drawChoices(mouseX, mouseY);
        SkeetEditorStyle.button(left + 18, bottom - 35, left + 126, bottom - 16, "Close", false);
        SkeetEditorStyle.button(right - 126, bottom - 35, right - 18, bottom - 16, "Save", true);
    }

    private void drawTags(int mouseX, int mouseY) {
        tags.clear(); int x = left + 21, y = top + 67, limit = right - 20;
        if (selected.isEmpty()) {
            fontRendererObj.drawString("No weapon signatures selected", x, y + 5, SkeetEditorStyle.MUTED); return;
        }
        for (String id : selected) {
            int tagWidth = Math.min(150, Math.max(58, fontRendererObj.getStringWidth(id) + 20));
            if (x + tagWidth > limit) { x = left + 21; y += 21; }
            if (y + 17 > top + 118) break;
            boolean hover = inside(mouseX, mouseY, x, y, x + tagWidth, y + 17);
            SkeetEditorStyle.row(x, y, x + tagWidth, y + 17, false, hover);
            fontRendererObj.drawString(id, x + 6, y + 5, SkeetEditorStyle.TEXT);
            fontRendererObj.drawString("x", x + tagWidth - 10, y + 5, hover ? 0xFFFF737D : SkeetEditorStyle.MUTED);
            tags.add(new Tag(id, x, y, x + tagWidth, y + 17)); x += tagWidth + 4;
        }
    }

    private void drawChoices(int mouseX, int mouseY) {
        List<Choice> matches = matches();
        int rows = Math.max(1, (listBottom - listTop - 18) / 22);
        int maxScroll = Math.max(0, matches.size() - rows); scroll = Math.max(0, Math.min(maxScroll, scroll));
        if (matches.isEmpty()) {
            fontRendererObj.drawString("No matching vanilla items", left + 21, listTop + 23, SkeetEditorStyle.MUTED); return;
        }
        for (int index = 0; index < rows && index + scroll < matches.size(); index++) {
            Choice choice = matches.get(index + scroll); int y = listTop + 18 + index * 22;
            boolean active = selected.contains(choice.id), hover = inside(mouseX, mouseY, left + 18, y, right - 18, y + 18);
            SkeetEditorStyle.row(left + 18, y, right - 18, y + 18, active, hover);
            String label = fontRendererObj.trimStringToWidth(choice.label, Math.max(80, right - left - 245));
            fontRendererObj.drawString(label, left + 25, y + 5, SkeetEditorStyle.TEXT);
            String id = fontRendererObj.trimStringToWidth(choice.id, 118);
            fontRendererObj.drawString(id, right - 183 - fontRendererObj.getStringWidth(id), y + 5, SkeetEditorStyle.MUTED);
            SkeetEditorStyle.button(right - 174, y + 2, right - 24, y + 16, active ? "Remove" : "Add", !active);
        }
        if (maxScroll > 0) {
            String position = (scroll + 1) + "-" + Math.min(matches.size(), scroll + rows) + " of " + matches.size();
            fontRendererObj.drawString(position, right - 20 - fontRendererObj.getStringWidth(position), listTop + 5, SkeetEditorStyle.MUTED);
        }
    }

    private List<Choice> matches() {
        String query = normalize(search == null ? "" : search.getText()).replace('_', ' ');
        if (query.isEmpty()) return catalog;
        List<Choice> result = new ArrayList<Choice>();
        for (Choice choice : catalog) {
            String words = (choice.id + " " + choice.label).toLowerCase(Locale.ROOT).replace('_', ' ');
            if (words.contains(query)) result.add(choice);
        }
        return result;
    }

    private void save() {
        StringBuilder joined = new StringBuilder();
        for (String item : selected) { if (joined.length() > 0) joined.append(','); joined.append(item); }
        module.getMurderWeapons().setValue(joined.toString());
        if (Vibe.getInstance().getConfig() != null) Vibe.getInstance().getConfig().save(Vibe.getInstance().getModuleManager());
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (button != 0) { super.mouseClicked(mouseX, mouseY, button); return; }
        if (inside(mouseX, mouseY, right - 126, bottom - 35, right - 18, bottom - 16)) { save(); return; }
        if (inside(mouseX, mouseY, left + 18, bottom - 35, left + 126, bottom - 16)) { save(); mc.displayGuiScreen(null); return; }
        for (Tag tag : tags) if (inside(mouseX, mouseY, tag.left, tag.top, tag.right, tag.bottom)) { selected.remove(tag.id); return; }
        if (inside(mouseX, mouseY, left + 18, listTop + 18, right - 18, listBottom)) {
            int index = (mouseY - listTop - 18) / 22 + scroll; List<Choice> matches = matches();
            if (index >= 0 && index < matches.size()) { String id = matches.get(index).id; if (!selected.add(id)) selected.remove(id); }
            return;
        }
        search.mouseClicked(mouseX, mouseY, button); super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput(); int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && inside(Mouse.getEventX() * width / Math.max(1, mc.displayWidth),
                height - Mouse.getEventY() * height / Math.max(1, mc.displayHeight) - 1, left + 18, listTop, right - 18, listBottom)) {
            scroll += wheel > 0 ? -3 : 3;
        }
    }

    @Override protected void keyTyped(char character, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) { save(); mc.displayGuiScreen(null); return; }
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) { save(); return; }
        if (key == Keyboard.KEY_UP) { scroll--; return; }
        if (key == Keyboard.KEY_DOWN) { scroll++; return; }
        if (search.textboxKeyTyped(character, key)) scroll = 0;
    }
    @Override public boolean doesGuiPauseGame() { return false; }

    private static boolean inside(int x, int y, int left, int top, int right, int bottom) { return x >= left && x < right && y >= top && y < bottom; }
    private static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_'); }
    private static String prettify(String id) { StringBuilder result = new StringBuilder(); for (String word : id.replace(':', ' ').replace('_', ' ').split(" ")) if (!word.isEmpty()) result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' '); return result.toString().trim(); }
    private static final class Choice { final String id, label; Choice(String id, String label) { this.id = id; this.label = label; } }
    private static final class Tag { final String id; final int left, top, right, bottom; Tag(String id, int left, int top, int right, int bottom) { this.id = id; this.left = left; this.top = top; this.right = right; this.bottom = bottom; } }
}
