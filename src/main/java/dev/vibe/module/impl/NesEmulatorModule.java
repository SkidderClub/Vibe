package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.StringSetting;
import dev.vibe.nes.RetroArchLauncher;
import dev.vibe.ui.NesEmulatorGui;
import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/** Built-in fast NES player plus locally installed RetroArch support for other systems. */
public final class NesEmulatorModule extends Module {
    private final File folder = new File(Minecraft.getMinecraft().mcDataDir, "vibe/roms");
    private final ModeSetting rom;
    private final ModeSetting backend = addSetting(new ModeSetting("Backend", "Built-in NES", "Built-in NES", "RetroArch"));
    private final ModeSetting system = addSetting(new ModeSetting("System", "Auto", "Auto", "NES", "SNES", "Game Boy", "Game Boy Color",
            "Game Boy Advance", "Sega Genesis", "Master System", "Game Gear", "PC Engine", "Neo Geo Pocket", "WonderSwan",
            "Atari 2600", "Atari 7800", "Atari Lynx", "Atari Jaguar", "Commodore 64", "Sega CD", "Sega 32X", "Virtual Boy",
            "PlayStation", "Nintendo 64", "Nintendo DS", "PSP", "Arcade"));
    private final ModeSetting region = addSetting(new ModeSetting("Region", "NTSC", "NTSC", "PAL"));
    private final NumberSetting scale = addSetting(new NumberSetting("Scale", 2.0D, 1.0D, 3.0D, 1.0D));
    private final NumberSetting presentationFps = addSetting(new NumberSetting("Presentation FPS", 30.0D, 30.0D, 60.0D, 30.0D,
            () -> backend.is("Built-in NES")));
    private final StringSetting retroArchExecutable = addSetting(new StringSetting("RetroArch Executable", "", 512,
            () -> backend.is("RetroArch")));
    private final StringSetting retroArchCore = addSetting(new StringSetting("RetroArch Core", "", 512,
            () -> backend.is("RetroArch")));
    private final BooleanSetting openFolder = addSetting(new BooleanSetting("Open Folder", false));
    private long lastScan;

    public NesEmulatorModule() {
        super("NES Emulator", "Fast built-in NES and RetroArch ROM launcher", Category.MEME, Keyboard.KEY_NONE);
        ensureFolder();
        List<String> choices = romChoices();
        rom = addSetting(new ModeSetting("ROM", "None", choices.toArray(new String[choices.size()])));
    }

    @Override protected void onEnable() {
        if (isConfigLoading()) { setEnabled(false); return; }
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
        for (File file : getRoms()) {
            // Older profiles stored only the file name. Keep those working
            // while new entries use a relative path so duplicate ROM names
            // in separate console folders remain distinguishable.
            if (displayName(file).equalsIgnoreCase(rom.getValue()) || file.getName().equalsIgnoreCase(rom.getValue())) return file;
        }
        return null;
    }

    public List<File> getRoms() {
        if (!folder.isDirectory()) return Collections.emptyList();
        List<File> roms = new ArrayList<File>();
        collectRoms(folder, roms, 0);
        Collections.sort(roms);
        return roms;
    }

    private void collectRoms(File root, List<File> into, int depth) {
        File[] files = root.listFiles(); if (files == null || depth > 3) return;
        for (File file : files) {
            if (file.isDirectory()) collectRoms(file, into, depth + 1);
            else if (isSupported(file)) into.add(file);
        }
    }
    private boolean isSupported(File file) {
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".nes") || name.endsWith(".fds") || name.endsWith(".sfc") || name.endsWith(".smc")
                || name.endsWith(".gb") || name.endsWith(".gbc") || name.endsWith(".gba") || name.endsWith(".gen")
                || name.endsWith(".md") || name.endsWith(".sms") || name.endsWith(".gg") || name.endsWith(".pce")
                || name.endsWith(".ngp") || name.endsWith(".ngc") || name.endsWith(".ws") || name.endsWith(".wsc")
                || name.endsWith(".a26") || name.endsWith(".a78") || name.endsWith(".lnx") || name.endsWith(".cue")
                || name.endsWith(".chd") || name.endsWith(".n64") || name.endsWith(".z64") || name.endsWith(".v64")
                || name.endsWith(".vb") || name.endsWith(".vboy") || name.endsWith(".j64") || name.endsWith(".jag")
                || name.endsWith(".d64") || name.endsWith(".t64") || name.endsWith(".crt") || name.endsWith(".32x")
                || name.endsWith(".nds") || name.endsWith(".iso") || name.endsWith(".cso") || name.endsWith(".zip");
    }

    private List<String> romChoices() {
        List<String> choices = new ArrayList<String>(); choices.add("None");
        for (File file : getRoms()) choices.add(displayName(file));
        return choices;
    }
    public String displayName(File file) {
        if (file == null) return "None";
        try { return folder.toPath().relativize(file.toPath()).toString().replace('\\', '/'); }
        catch (Exception ignored) { return file.getName(); }
    }
    public void selectRom(File file) { if (file != null) rom.setValue(displayName(file)); }
    public void saveSettings() {
        if (dev.vibe.Vibe.getInstance() != null && dev.vibe.Vibe.getInstance().getConfig() != null)
            dev.vibe.Vibe.getInstance().getConfig().save(dev.vibe.Vibe.getInstance().getModuleManager());
    }
    private void ensureFolder() { if (!folder.isDirectory()) folder.mkdirs(); }
    public File getFolder() { return folder; }
    public ModeSetting getRom() { return rom; }
    public ModeSetting getBackend() { return backend; }
    public ModeSetting getSystem() { return system; }
    public ModeSetting getRegion() { return region; }
    public NumberSetting getScale() { return scale; }
    public NumberSetting getPresentationFps() { return presentationFps; }
    public StringSetting getRetroArchExecutable() { return retroArchExecutable; }
    public StringSetting getRetroArchCore() { return retroArchCore; }
    public BooleanSetting getOpenFolder() { return openFolder; }
    public String selectedSystem() {
        if (!system.is("Auto")) return system.getValue();
        File file = getSelectedRom(); if (file == null) return "NES";
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".sfc") || name.endsWith(".smc")) return "SNES";
        if (name.endsWith(".gbc")) return "Game Boy Color"; if (name.endsWith(".gb")) return "Game Boy";
        if (name.endsWith(".gba")) return "Game Boy Advance";
        if (name.endsWith(".gen") || name.endsWith(".md")) return "Sega Genesis";
        if (name.endsWith(".sms")) return "Master System"; if (name.endsWith(".gg")) return "Game Gear";
        if (name.endsWith(".pce")) return "PC Engine"; if (name.endsWith(".n64") || name.endsWith(".z64") || name.endsWith(".v64")) return "Nintendo 64";
        if (name.endsWith(".ngp") || name.endsWith(".ngc")) return "Neo Geo Pocket";
        if (name.endsWith(".ws") || name.endsWith(".wsc")) return "WonderSwan";
        if (name.endsWith(".a26")) return "Atari 2600"; if (name.endsWith(".a78")) return "Atari 7800";
        if (name.endsWith(".lnx")) return "Atari Lynx"; if (name.endsWith(".j64") || name.endsWith(".jag")) return "Atari Jaguar";
        if (name.endsWith(".d64") || name.endsWith(".t64") || name.endsWith(".crt")) return "Commodore 64";
        if (name.endsWith(".32x")) return "Sega 32X"; if (name.endsWith(".vb") || name.endsWith(".vboy")) return "Virtual Boy";
        if (name.endsWith(".nds")) return "Nintendo DS"; if (name.endsWith(".iso") || name.endsWith(".cso")) return "PSP";
        if (name.endsWith(".cue") || name.endsWith(".chd")) return "PlayStation"; return "NES";
    }
    public String launchRetroArch() { return RetroArchLauncher.launch(getSelectedRom(), retroArchExecutable.getValue(), retroArchCore.getValue(), selectedSystem()); }
    public boolean canUseBuiltIn() { return selectedSystem().equals("NES"); }
}
