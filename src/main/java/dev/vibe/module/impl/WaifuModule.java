package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.MultiSelectSetting;
import java.util.Arrays;
import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.lwjgl.input.Keyboard;

/** Displays a user-selected image from the Vibe waifu directory in ClickGUI. */
public final class WaifuModule extends Module {
    private final NumberSetting scale = addSetting(new NumberSetting("Scale", 0.35D, 0.05D, 1.0D, 0.05D));
    private final BooleanSetting openFolder = addSetting(new BooleanSetting("Open Folder", false));
    private final BooleanSetting gravity = addSetting(new BooleanSetting("Gravity", false));
    private final ModeSetting selected;
        private final MultiSelectSetting guiTargets = addSetting(new MultiSelectSetting("Show In",
            Arrays.asList("ClickGUI", "Inventory", "Inventory Editor", "Friend Editor", "GTA7", "Meme Games", "Chest", "Escape", "Anvil", "Dropper", "Thrower", "Other"),
            Arrays.asList("ClickGUI", "Inventory", "Chest", "Escape", "Anvil", "Dropper", "Thrower")));
    private final File folder;
    private long lastImageScan;

    public WaifuModule() {
        super("Waifu", "Show a custom image in the ClickGUI", Category.CLIENT, Keyboard.KEY_NONE);
        folder = new File(net.minecraft.client.Minecraft.getMinecraft().mcDataDir, "vibe/waifu");
        syncPresets();
        selected = addSetting(new ModeSetting("Waifu", "None", imageChoices().toArray(new String[imageChoices().size()])));
    }

    private void syncPresets() {
        if (!folder.isDirectory()) folder.mkdirs();
        InputStream manifest = WaifuModule.class.getResourceAsStream("/assets/vibe/waifu/presets.txt");
        if (manifest == null) return;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(manifest, StandardCharsets.UTF_8));
            try {
                String name;
                while ((name = reader.readLine()) != null) {
                    name = name.trim();
                    if (name.isEmpty() || name.contains("/") || name.contains("\\")) continue;
                    InputStream preset = WaifuModule.class.getResourceAsStream("/assets/vibe/waifu/" + name);
                    if (preset == null) continue;
                    try {
                        File target = new File(folder, name);
                        if (!target.isFile()) {
                            Files.copy(preset, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        }
                    } finally {
                        preset.close();
                    }
                }
            } finally {
                reader.close();
            }
        } catch (Exception ignored) {
            // A user-added image must remain usable even when a preset cannot
            // be extracted on this installation.
        }
    }

    public void tick() {
        // Folder-backed ModeSettings used to be frozen at startup, which made
        // newly copied images impossible to select until a game restart.
        if (System.currentTimeMillis() - lastImageScan >= 750L) {
            refreshImageChoices();
        }
        if (openFolder.isEnabled()) {
            openFolder.setEnabled(false);
            if (!folder.isDirectory()) {
                folder.mkdirs();
            }
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(folder);
                }
            } catch (Exception ignored) { }
            refreshImageChoices();
        }
    }

    public NumberSetting getScale() { return scale; }
    public ModeSetting getSelected() { return selected; }
    public BooleanSetting getGravity() { return gravity; }
    public MultiSelectSetting getGuiTargets() { return guiTargets; }
    public File getFolder() { return folder; }

    private List<String> imageChoices() {
        List<String> names = new ArrayList<String>();
        names.add("None");
        for (File image : getImages()) names.add(image.getName());
        return names;
    }

    private void refreshImageChoices() {
        lastImageScan = System.currentTimeMillis();
        List<String> choices = imageChoices();
        if (!choices.equals(selected.getModes())) selected.replaceModes(choices);
    }

    public List<File> getImages() {
        if (!folder.isDirectory()) {
            return Collections.emptyList();
        }
        File[] files = folder.listFiles();
        List<File> images = new ArrayList<File>();
        if (files != null) {
            for (File file : files) {
                String name = file.getName().toLowerCase(java.util.Locale.ROOT);
                if (file.isFile() && (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                        || name.endsWith(".webp") || name.endsWith(".gif") || name.endsWith(".bmp"))) {
                    images.add(file);
                }
            }
        }
        Collections.sort(images);
        return images;
    }

    public File getSelectedFile() {
        if ("None".equalsIgnoreCase(selected.getValue())) return null;
        for (File image : getImages()) {
            if (image.getName().equalsIgnoreCase(selected.getValue())) return image;
        }
        return null;
    }

    public boolean shouldRender(net.minecraft.client.gui.GuiScreen screen) {
        if (screen instanceof net.minecraft.client.gui.inventory.GuiInventory
                || screen instanceof net.minecraft.client.gui.inventory.GuiContainerCreative) {
            return guiTargets.isSelected("Inventory");
        }
        if (screen instanceof net.minecraft.client.gui.inventory.GuiChest) return guiTargets.isSelected("Chest");
        if (screen instanceof net.minecraft.client.gui.GuiIngameMenu) return guiTargets.isSelected("Escape");
        String simpleName = screen.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
        if (simpleName.contains("repair") || simpleName.contains("anvil")) return guiTargets.isSelected("Anvil");
        if (screen instanceof net.minecraft.client.gui.inventory.GuiDispenser) return guiTargets.isSelected("Dropper") || guiTargets.isSelected("Thrower");
        if (screen instanceof dev.vibe.ui.InventoryEditorGui) return guiTargets.isSelected("Inventory Editor");
        if (screen instanceof dev.vibe.ui.FriendEditorGui) return guiTargets.isSelected("Friend Editor");
        if (screen instanceof dev.vibe.ui.Gta7Gui) return guiTargets.isSelected("GTA7");
        if (screen instanceof dev.vibe.ui.MemeGameGui) return guiTargets.isSelected("Meme Games");
        if (screen instanceof dev.vibe.ui.SlotsGui) return guiTargets.isSelected("Meme Games");
        if (screen instanceof net.minecraft.client.gui.inventory.GuiContainer) return guiTargets.isSelected("Other");
        return screen instanceof dev.vibe.ui.VibeClickGui && guiTargets.isSelected("ClickGUI");
    }
}
