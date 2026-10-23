package dev.vibe.ui.screen;

import dev.vibe.game.hypixel.MurderWeapons;
import dev.vibe.module.impl.client.BlurModule;
import dev.vibe.module.impl.meme.HypixelModule;
import java.io.IOException;
import java.util.*;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Scrollable item/variant picker. Applying edits changes only the running configuration. */
public final class HypixelWeaponsGui extends GuiScreen {
    private static final int TEXT = SkeetEditorStyle.TEXT, MUTED = SkeetEditorStyle.MUTED;
    private static final String[] FILTERS = {"Default skins", "Selected", "All items"};
    private final HypixelModule module;
    private final GuiScreen parent;
    private final Set<String> selected = new LinkedHashSet<String>();
    private final Set<String> defaults = MurderWeapons.defaults();
    private final List<Choice> catalog = new ArrayList<Choice>();
    private final List<Cell> cells = new ArrayList<Cell>();
    private List<Choice> filtered;
    private GuiTextField search;
    private int left, top, right, bottom, searchY, tabsY, gridTop, gridBottom, footerY;
    private int columns, rows, cellWidth, scroll, filter;
    private boolean initialized;

    public HypixelWeaponsGui(HypixelModule module) { this(module, null); }
    public HypixelWeaponsGui(HypixelModule module, GuiScreen parent) { this.module = module; this.parent = parent; }

    @Override public void initGui() {
        Keyboard.enableRepeatEvents(true);
        String query = search == null ? "" : search.getText();
        if (!initialized) { selected.addAll(MurderWeapons.parse(module.getMurderWeapons().getValue())); initialized = true; }
        int windowWidth = Math.min(720, width - 24), windowHeight = Math.min(480, height - 24);
        left = (width - windowWidth) / 2; right = left + windowWidth;
        top = (height - windowHeight) / 2; bottom = top + windowHeight;
        searchY = top + (windowHeight < 320 ? 37 : 54);
        tabsY = searchY + 30; gridTop = tabsY + 30; footerY = bottom - 34; gridBottom = footerY - 23;
        columns = Math.max(3, (windowWidth - 40) / 62);
        cellWidth = (windowWidth - 36) / columns;
        rows = Math.max(1, (gridBottom - gridTop) / 52);
        buildCatalog();
        search = new GuiTextField(0, fontRendererObj, left + 25, searchY + 7, windowWidth - 69, 14);
        search.setEnableBackgroundDrawing(false); search.setMaxStringLength(80); search.setText(query); search.setFocused(true);
        filtered = null;
    }

    private void buildCatalog() {
        Map<String, Choice> choices = new LinkedHashMap<String, Choice>();
        for (Object value : Item.itemRegistry.getKeys()) {
            if (!(value instanceof ResourceLocation)) continue;
            Item item = Item.itemRegistry.getObject((ResourceLocation) value);
            if (item == null) continue;
            List<ItemStack> variants = new ArrayList<ItemStack>();
            try { item.getSubItems(item, CreativeTabs.tabAllSearch, variants); }
            catch (RuntimeException invalidVariants) { variants.clear(); }
            if (variants.isEmpty()) variants.add(new ItemStack(item));
            if (item.getHasSubtypes()) {
                ItemStack first = variants.get(0);
                String id = MurderWeapons.itemId(first);
                choices.put(id, new Choice(id, label(first) + " (any variant)", first));
            }
            for (ItemStack stack : variants) {
                String id = MurderWeapons.signature(stack);
                choices.put(id, new Choice(id, label(stack), stack));
            }
        }
        // Unknown and legacy signatures stay visible and removable in Selected.
        for (String id : selected) if (!choices.containsKey(id)) {
            ItemStack stack = MurderWeapons.stack(id);
            choices.put(id, new Choice(id, stack == null ? id : label(stack), stack));
        }
        catalog.clear(); catalog.addAll(choices.values());
        Collections.sort(catalog, Comparator.comparing((Choice c) -> c.label.toLowerCase(Locale.ROOT)).thenComparing(c -> c.id));
    }

    private static String label(ItemStack stack) {
        try {
            String name = EnumChatFormatting.getTextWithoutFormattingCodes(stack.getDisplayName());
            if (name != null && !name.trim().isEmpty()) return name;
        } catch (RuntimeException | LinkageError unavailableTranslation) {
            // A malformed third-party name must not prevent editing the registry signature.
        }
        StringBuilder name = new StringBuilder();
        for (String word : MurderWeapons.signature(stack).replace('_', ' ').split(" ")) {
            if (!word.isEmpty()) name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return name.toString().trim();
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        SkeetEditorStyle.backdrop(this, BlurModule.CLICK_GUI, partialTicks);
        SkeetEditorStyle.window(left, top, right, bottom, "Murder Mystery", selected.size() + " selected");
        if (searchY - top > 40) fontRendererObj.drawString("Choose the items that reveal a murderer", left + 18, top + 31, MUTED);
        SkeetEditorStyle.input(left + 17, searchY, right - 17, searchY + 24);
        if (search.isFocused()) SkeetEditorStyle.border(left + 17, searchY, right - 17, searchY + 24, SkeetEditorStyle.accent(.18F));
        if (search.getText().isEmpty() && !search.isFocused()) fontRendererObj.drawString("Search item names or IDs...", left + 25, searchY + 8, MUTED);
        search.drawTextBox();
        fontRendererObj.drawString("x", right - 31, searchY + 8, MUTED);
        int tabLeft = left + 18;
        for (int index = 0; index < FILTERS.length; index++) {
            int tabWidth = fontRendererObj.getStringWidth(FILTERS[index]) + 18;
            button(tabLeft, tabsY, tabLeft + tabWidth, tabsY + 21, FILTERS[index], filter == index,
                    inside(mouseX, mouseY, tabLeft, tabsY, tabLeft + tabWidth, tabsY + 21));
            tabLeft += tabWidth + 6;
        }
        drawGrid(mouseX, mouseY);
        String help = "Click to toggle  /  " + selected.size() + " selected";
        fontRendererObj.drawString(fontRendererObj.trimStringToWidth(help, right - left - 36), left + 18, footerY - 15, MUTED);
        button(left + 18, footerY, left + 128, footerY + 22, "Restore defaults", false,
                inside(mouseX, mouseY, left + 18, footerY, left + 128, footerY + 22));
        button(right - 150, footerY, right - 88, footerY + 22, "Cancel", false,
                inside(mouseX, mouseY, right - 150, footerY, right - 88, footerY + 22));
        button(right - 80, footerY, right - 18, footerY + 22, "Apply", true,
                inside(mouseX, mouseY, right - 80, footerY, right - 18, footerY + 22));
        for (Cell cell : cells) if (inside(mouseX, mouseY, cell.x, cell.y, cell.x + cellWidth - 5, cell.y + 47)) {
            drawHoveringText(Arrays.asList(cell.choice.label, EnumChatFormatting.GRAY + cell.choice.id,
                    selected.contains(cell.choice.id) ? "Selected - click to remove" : EnumChatFormatting.GRAY + "Click to add"), mouseX, mouseY);
            break;
        }
    }

    private void drawGrid(int mouseX, int mouseY) {
        cells.clear(); List<Choice> matches = matches();
        int maxScroll = Math.max(0, (matches.size() + columns - 1) / columns - rows);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        if (matches.isEmpty()) { fontRendererObj.drawString("No matching items", left + 21, gridTop + 17, MUTED); return; }
        for (int index = 0; index < columns * rows && scroll * columns + index < matches.size(); index++) {
            Choice choice = matches.get(scroll * columns + index);
            int x = left + 18 + index % columns * cellWidth, y = gridTop + index / columns * 52;
            boolean active = selected.contains(choice.id), hover = inside(mouseX, mouseY, x, y, x + cellWidth - 5, y + 47);
            SkeetEditorStyle.row(x, y, x + cellWidth - 5, y + 47, active, hover);
            if (choice.stack != null) {
                RenderHelper.enableGUIStandardItemLighting(); GlStateManager.enableDepth();
                mc.getRenderItem().renderItemAndEffectIntoGUI(choice.stack, x + (cellWidth - 21) / 2, y + 7);
                GlStateManager.disableDepth(); RenderHelper.disableStandardItemLighting();
            } else fontRendererObj.drawString("?", x + cellWidth / 2 - 4, y + 12, MUTED);
            GlStateManager.disableLighting();
            String name = fontRendererObj.trimStringToWidth(choice.label, cellWidth - 13);
            fontRendererObj.drawString(name, x + (cellWidth - 5 - fontRendererObj.getStringWidth(name)) / 2, y + 33, active ? TEXT : MUTED);
            if (active) drawRect(x + cellWidth - 13, y + 5, x + cellWidth - 9, y + 9, SkeetEditorStyle.accent(.18F));
            cells.add(new Cell(choice, x, y));
        }
        RenderHelper.disableStandardItemLighting(); GlStateManager.disableDepth();
        if (maxScroll > 0) {
            int trackBottom = gridTop + rows * 52 - 5, trackHeight = trackBottom - gridTop;
            drawRect(right - 13, gridTop, right - 10, trackBottom, SkeetEditorStyle.BORDER);
            int thumb = Math.max(12, trackHeight * rows / (maxScroll + rows));
            int thumbY = gridTop + (trackHeight - thumb) * scroll / maxScroll;
            drawRect(right - 13, thumbY, right - 10, thumbY + thumb, SkeetEditorStyle.accent(.18F));
        }
    }

    private List<Choice> matches() {
        if (filtered != null) return filtered;
        String query = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT).replace('_', ' ');
        filtered = new ArrayList<Choice>();
        for (Choice choice : catalog) {
            if (filter == 0 && !defaults.contains(choice.id) || filter == 1 && !selected.contains(choice.id)) continue;
            if (query.isEmpty() || choice.words.contains(query)) filtered.add(choice);
        }
        return filtered;
    }

    private void apply() { module.getMurderWeapons().setValue(String.join(",", selected)); mc.displayGuiScreen(parent); }

    @Override protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (button != 0) return;
        if (inside(mouseX, mouseY, right - 80, footerY, right - 18, footerY + 22)) { apply(); return; }
        if (inside(mouseX, mouseY, right - 150, footerY, right - 88, footerY + 22)) { mc.displayGuiScreen(parent); return; }
        if (inside(mouseX, mouseY, left + 18, footerY, left + 128, footerY + 22)) {
            selected.clear(); selected.addAll(defaults); buildCatalog(); changed(); return;
        }
        if (inside(mouseX, mouseY, right - 42, searchY, right - 17, searchY + 24)) { search.setText(""); changed(); return; }
        int tabLeft = left + 18;
        for (int index = 0; index < FILTERS.length; index++) {
            int tabWidth = fontRendererObj.getStringWidth(FILTERS[index]) + 18;
            if (inside(mouseX, mouseY, tabLeft, tabsY, tabLeft + tabWidth, tabsY + 21)) { filter = index; changed(); return; }
            tabLeft += tabWidth + 6;
        }
        for (Cell cell : cells) if (inside(mouseX, mouseY, cell.x, cell.y, cell.x + cellWidth - 5, cell.y + 47)) {
            if (!selected.add(cell.choice.id)) selected.remove(cell.choice.id);
            filtered = null; return;
        }
        search.mouseClicked(mouseX, mouseY, button);
    }

    private void changed() { scroll = 0; filtered = null; cells.clear(); }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput(); int wheel = Mouse.getEventDWheel();
        int mouseX = Mouse.getEventX() * width / Math.max(1, mc.displayWidth);
        int mouseY = height - Mouse.getEventY() * height / Math.max(1, mc.displayHeight) - 1;
        if (wheel != 0 && inside(mouseX, mouseY, left + 18, gridTop, right - 9, gridBottom)) scroll += wheel > 0 ? -1 : 1;
    }
    @Override protected void keyTyped(char character, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(parent); return; }
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) { apply(); return; }
        if (key == Keyboard.KEY_UP || key == Keyboard.KEY_PRIOR) { scroll -= key == Keyboard.KEY_UP ? 1 : rows; return; }
        if (key == Keyboard.KEY_DOWN || key == Keyboard.KEY_NEXT) { scroll += key == Keyboard.KEY_DOWN ? 1 : rows; return; }
        if (search.textboxKeyTyped(character, key)) changed();
    }
    @Override public void updateScreen() { if (search != null) search.updateCursorCounter(); }
    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }
    @Override public boolean doesGuiPauseGame() { return false; }
    private static boolean inside(int x, int y, int left, int top, int right, int bottom) { return x >= left && x < right && y >= top && y < bottom; }
    private void button(int left, int top, int right, int bottom, String text, boolean active, boolean hover) {
        SkeetEditorStyle.button(left, top, right, bottom, text, active);
        if (hover && !active) SkeetEditorStyle.border(left, top, right, bottom, SkeetEditorStyle.accent(.18F));
    }
    private static final class Choice {
        final String id, label, words; final ItemStack stack;
        Choice(String id, String label, ItemStack stack) { this.id = id; this.label = label; this.stack = stack; this.words = (id + " " + label).toLowerCase(Locale.ROOT).replace('_', ' '); }
    }
    private static final class Cell {
        final Choice choice; final int x, y;
        Cell(Choice choice, int x, int y) { this.choice = choice; this.x = x; this.y = y; }
    }
}
