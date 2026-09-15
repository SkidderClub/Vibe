package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.ui.NesEmulatorGui;
import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** Opens Vibe's local, ROM-folder-backed NES player. */
public final class NesEmulatorModule extends Module {
    private final File folder = new File(Minecraft.getMinecraft().mcDataDir, "vibe/nes/roms");
    private final ModeSetting rom;
    private final ModeSetting region = addSetting(new ModeSetting("Region", "NTSC", "NTSC", "PAL"));
    private final NumberSetting scale = addSetting(new NumberSetting("Scale", 2.0D, 1.0D, 3.0D, 1.0D));
    private final BooleanSetting openFolder = addSetting(new BooleanSetting("Open Folder", false));
    private long lastScan;

    public NesEmulatorModule() {
        super("NES Emulator", "Play local .nes ROM files in a Vibe GUI", Category.MEME, Keyboard.KEY_NONE);
        ensureFolder();
        List<String> choices = romChoices();
        rom = addSetting(new ModeSetting("ROM", "None", choices.toArray(new String[choices.size()])));
    }

    @Override protected void onEnable() {
        if (Minecraft.getMinecraft().thePlayer == null) { setEnabled(false); return; }
        refreshRoms();
        Minecraft.getMinecraft().displayGuiScreen(new NesEmulatorGui(this));
    }

    public void tick() {
        if (System.currentTimeMillis() - lastScan > 750L) refreshRoms();
        if (openFolder.isEnabled()) {
            openFolder.setEnabled(false);
            openFolder();
            refreshRoms();
        }
    }

    public void refreshRoms() {
        lastScan = System.currentTimeMillis();
        ensureFolder();
        List<String> choices = romChoices();
        if (!choices.equals(rom.getModes())) rom.replaceModes(choices);
    }

    public boolean openFolder() {
        ensureFolder();
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(folder);
                return true;
            }
        } catch (Exception ignored) { }
        try {
            if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
                new ProcessBuilder("explorer.exe", folder.getAbsolutePath()).start();
                return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    public File getSelectedRom() {
        if (rom.is("None")) return null;
        for (File file : getRoms()) if (file.getName().equalsIgnoreCase(rom.getValue())) return file;
        return null;
    }

    public List<File> getRoms() {
        if (!folder.isDirectory()) return Collections.emptyList();
        File[] files = folder.listFiles();
        List<File> roms = new ArrayList<File>();
        if (files != null) for (File file : files) if (file.isFile() && file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".nes")) roms.add(file);
        Collections.sort(roms);
        return roms;
    }

    private List<String> romChoices() {
        List<String> choices = new ArrayList<String>(); choices.add("None");
        for (File file : getRoms()) choices.add(file.getName());
        return choices;
    }
    private void ensureFolder() { if (!folder.isDirectory()) folder.mkdirs(); }
    public File getFolder() { return folder; }
    public ModeSetting getRom() { return rom; }
    public ModeSetting getRegion() { return region; }
    public NumberSetting getScale() { return scale; }
    public BooleanSetting getOpenFolder() { return openFolder; }
}
