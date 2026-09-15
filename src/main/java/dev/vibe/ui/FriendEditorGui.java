package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.friend.FriendManager.Friend;
import dev.vibe.cosmetic.CosmeticPreset;
import dev.vibe.module.impl.BlurModule;
import dev.vibe.module.impl.FriendEditorModule;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;

/** Friend roster editor with live in-world skin, armour and held-item preview. */
public final class FriendEditorGui extends GuiScreen {
    private final FriendEditorModule module;
    private final ParticlesRenderer particles = new ParticlesRenderer();
    private GuiTextField name;
    private GuiTextField alias;
    private Friend selected;
    private int left, top, scroll;

    public FriendEditorGui(FriendEditorModule module) { this.module = module; }
    private float uiScale = 1;
    @Override public void initGui() {
        uiScale = Math.min(1F, Math.min(width / 440F, height / 330F));
        int canvasWidth = Math.round(width / uiScale), canvasHeight = Math.round(height / uiScale);
        left = (canvasWidth - 420) / 2; top = Math.max(24, (canvasHeight - 300) / 2);
        name = new GuiTextField(0, fontRendererObj, left + 13, top + 34, 135, 16); name.setMaxStringLength(16);
        alias = new GuiTextField(1, fontRendererObj, left + 158, top + 34, 135, 16); alias.setMaxStringLength(16);
        name.setEnableBackgroundDrawing(false);
        alias.setEnableBackgroundDrawing(false);
    }
    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        SkeetEditorStyle.backdrop(this, BlurModule.FRIEND_EDITOR, partialTicks);
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.scale(uiScale, uiScale, 1);
        SkeetEditorStyle.window(left, top, left + 420, top + 300, "Friend editor", "");
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("Name"), left + 13, top + 25, SkeetEditorStyle.MUTED);
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("Alias"), left + 158, top + 25, SkeetEditorStyle.MUTED);
        SkeetEditorStyle.input(left + 11, top + 32, left + 150, top + 52);
        SkeetEditorStyle.input(left + 156, top + 32, left + 295, top + 52);
        name.drawTextBox(); alias.drawTextBox();
        button(left + 304, top + 32, "Add", 0xFF2DE2C2); button(left + 350, top + 32, "Save", 0xFFA855F7);
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("Friends"), left + 13, top + 64, RenderUtils.MUTED);
        List<Friend> friends = Vibe.getInstance().getFriendManager().getFriends();
        int y = top + 80;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, friends.size() - 5)));
        for (Friend friend : friends.subList(scroll, friends.size())) {
            boolean picked = friend == selected;
            SkeetEditorStyle.row(left + 10, y, left + 272, y + 36, picked, false);
            fontRendererObj.drawStringWithShadow(friend.getName(), left + 16, y + 4, SkeetEditorStyle.TEXT);
            fontRendererObj.drawStringWithShadow(friend.getAlias(), left + 105, y + 4, SkeetEditorStyle.MUTED);
            fontRendererObj.drawStringWithShadow(friend.getAddedDate(), left + 185, y + 4, SkeetEditorStyle.MUTED);
            fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("×"), left + 255, y + 4, 0xFFFF6A82);
            SkeetEditorStyle.row(left + 16, y + 17, left + 247, y + 32, !friend.getCosmeticPreset().isEmpty(), false);
            String profile = "Cosmetic profile";
            fontRendererObj.drawStringWithShadow(fontRendererObj.trimStringToWidth(dev.vibe.language.LanguageManager.translate(profile), 106), left + 20, y + 20, SkeetEditorStyle.MUTED);
            String presetLabel = friend.getCosmeticPreset().isEmpty() ? dev.vibe.language.LanguageManager.translate("None") : presetName(friend);
            fontRendererObj.drawStringWithShadow("< " + fontRendererObj.trimStringToWidth(presetLabel, 89) + " >", left + 132, y + 20, SkeetEditorStyle.TEXT);
            y += 40;
            if (y + 36 > top + 288) break;
        }
        drawLivePreview();
        net.minecraft.client.renderer.GlStateManager.popMatrix();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
    private void button(int x, int y, String text, int color) { SkeetEditorStyle.button(x, y, x + 40, y + 17, text, color != 0xFFFF6A82); }
    private void drawLivePreview() {
        int px = left + 288, py = top + 70;
        SkeetEditorStyle.panel(px, py, px + 120, py + 200, "Live preview");
        if (selected == null) { fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("Select a friend"), px + 22, py + 80, RenderUtils.MUTED); return; }
        EntityPlayer player = findPlayer(selected.getName());
        if (player == null) { fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.translate("Not in this world"), px + 15, py + 80, RenderUtils.MUTED); return; }
        if (player instanceof AbstractClientPlayer) {
            ResourceLocation skin = ((AbstractClientPlayer) player).getLocationSkin();
            mc.getTextureManager().bindTexture(skin);
            Gui.drawScaledCustomSizeModalRect(px + 45, py + 29, 8, 8, 8, 8, 28, 28, 64, 64);
            Gui.drawScaledCustomSizeModalRect(px + 45, py + 29, 40, 8, 8, 8, 28, 28, 64, 64);
        }
        fontRendererObj.drawStringWithShadow(player.getName(), px + 8, py + 61, RenderUtils.TEXT);
        String preset = selectedPresetName();
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.format("Preset: %s", preset), px + 8, py + 71, SkeetEditorStyle.MUTED);
        int itemX = px + 8;
        ItemStack held = player.getHeldItem(); if (held != null) drawItem(held, itemX, py + 90);
        for (int slot = 0; slot < 4; slot++) { ItemStack armor = player.getCurrentArmor(slot); if (armor != null) drawItem(armor, px + 8 + slot * 25, py + 120); }
    }
    private void drawItem(ItemStack stack, int x, int y) { RenderHelper.enableGUIStandardItemLighting(); itemRender.renderItemAndEffectIntoGUI(stack, x, y); RenderHelper.disableStandardItemLighting(); }
    private EntityPlayer findPlayer(String ign) { if (mc.theWorld == null) return null; for (Object object : mc.theWorld.playerEntities) if (object instanceof EntityPlayer && ((EntityPlayer) object).getName().equalsIgnoreCase(ign)) return (EntityPlayer) object; return null; }
    @Override protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        mouseX = Math.round(mouseX / uiScale); mouseY = Math.round(mouseY / uiScale);
        name.mouseClicked(mouseX, mouseY, mouseButton); alias.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton == 0 && mouseX >= left + 304 && mouseX < left + 344 && mouseY >= top + 32 && mouseY < top + 49) { if (Vibe.getInstance().getFriendManager().add(name.getText(), alias.getText())) { name.setText(""); alias.setText(""); } return; }
        if (mouseButton == 0 && mouseX >= left + 350 && mouseX < left + 390 && mouseY >= top + 32 && mouseY < top + 49) { if (selected != null) Vibe.getInstance().getFriendManager().rename(selected.getName(), alias.getText()); return; }
        if (mouseButton == 0 || mouseButton == 1) {
            List<Friend> friends = Vibe.getInstance().getFriendManager().getFriends();
            int y = top + 80;
            for (int i = scroll; i < friends.size() && y + 36 <= top + 288; i++, y += 40) {
                Friend friend = friends.get(i);
                if (mouseX < left + 10 || mouseX >= left + 272 || mouseY < y || mouseY >= y + 36) continue;
                if (mouseX >= left + 250 && mouseButton == 0) {
                    Vibe.getInstance().getFriendManager().remove(friend.getName()); if (friend == selected) selected = null;
                } else {
                    selected = friend; name.setText(friend.getName()); alias.setText(friend.getAlias());
                    if (mouseY >= y + 17 && mouseX < left + 247) cyclePreset(mouseButton == 1);
                }
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel != 0) scroll = Math.max(0, Math.min(Math.max(0, Vibe.getInstance().getFriendManager().getFriends().size() - 5), scroll + (wheel > 0 ? -1 : 1)));
    }
    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException { if (keyCode == Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(null); return; } if (!name.textboxKeyTyped(typedChar, keyCode)) alias.textboxKeyTyped(typedChar, keyCode); }
    @Override public void onGuiClosed() { if (module.isEnabled()) module.setEnabled(false); super.onGuiClosed(); }
    @Override public boolean doesGuiPauseGame() { return false; }
    private String selectedPresetName() { return presetName(selected); }
    private String presetName(Friend selected) {
        if (selected == null || selected.getCosmeticPreset() == null || selected.getCosmeticPreset().isEmpty()) return "None";
        CosmeticPreset preset = Vibe.getInstance().getCosmeticPresetManager().findById(selected.getCosmeticPreset());
        return preset == null ? "None" : preset.getName();
    }
    private void cyclePreset(boolean backwards) {
        if (selected == null) return;
        List<CosmeticPreset> presets = Vibe.getInstance().getCosmeticPresetManager().getPresets();
        String id = Vibe.getInstance().getCosmeticPresetManager().cycleId(selected.getCosmeticPreset(), backwards);
        Vibe.getInstance().getFriendManager().setCosmeticPreset(selected.getName(), id);
    }
}
