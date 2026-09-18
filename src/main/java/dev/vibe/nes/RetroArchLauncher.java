package dev.vibe.nes;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/** Opens locally owned ROMs in the user's installed RetroArch frontend. */
public final class RetroArchLauncher {
    private RetroArchLauncher() { }

    public static String launch(File rom, String executable, String core, String system) {
        if (rom == null || !rom.isFile()) return "ROM file is missing.";
        File program = findExecutable(executable);
        if (program == null) return "RetroArch was not found. Set its executable in the module settings.";
        try {
            ProcessBuilder command = new ProcessBuilder();
            command.command().add(program.getAbsolutePath());
            File selectedCore = findCore(program, core, system);
            if (selectedCore != null) { command.command().add("-L"); command.command().add(selectedCore.getAbsolutePath()); }
            command.command().add(rom.getAbsolutePath());
            command.directory(program.getParentFile());
            command.start();
            return "Opened " + rom.getName() + " in RetroArch" + (selectedCore == null ? ". Select a core there if prompted." : ".");
        } catch (IOException error) { return "Could not start RetroArch: " + friendly(error); }
    }

    public static boolean isAvailable(String executable) { return findExecutable(executable) != null; }
    private static File findExecutable(String configured) {
        if (configured != null && !configured.trim().isEmpty()) {
            File value = new File(configured.trim()); if (value.isFile()) return value;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String name = os.contains("win") ? "retroarch.exe" : "retroarch";
        String[] candidates = os.contains("win") ? new String[] {
                "C:/Program Files/RetroArch/retroarch.exe", "C:/Program Files (x86)/RetroArch/retroarch.exe"
        } : new String[] {"/usr/bin/retroarch", "/Applications/RetroArch.app/Contents/MacOS/RetroArch"};
        for (String candidate : candidates) { File value = new File(candidate); if (value.isFile()) return value; }
        // A bare executable is intentionally not run: ProcessBuilder cannot reliably distinguish
        // an absent PATH entry from a failed launch, whereas these locations yield clear feedback.
        return null;
    }
    private static File findCore(File retroArch, String configured, String system) {
        if (configured != null && !configured.trim().isEmpty()) { File value = new File(configured.trim()); if (value.isFile()) return value; }
        String extension = retroArch.getName().endsWith(".exe") ? ".dll" : ".so";
        File folder = new File(retroArch.getParentFile(), "cores");
        String[] names = system.equals("SNES") ? new String[] {"snes9x_libretro", "bsnes_mercury_performance_libretro"}
                : system.equals("Game Boy") || system.equals("Game Boy Color") ? new String[] {"gambatte_libretro", "mgba_libretro"}
                : system.equals("Game Boy Advance") ? new String[] {"mgba_libretro", "vba_next_libretro"}
                : system.equals("Sega Genesis") || system.equals("Master System") || system.equals("Game Gear") ? new String[] {"genesis_plus_gx_libretro"}
                : system.equals("PC Engine") ? new String[] {"mednafen_pce_fast_libretro"}
                : system.equals("PlayStation") ? new String[] {"pcsx_rearmed_libretro", "swanstation_libretro"}
                : system.equals("Nintendo 64") ? new String[] {"mupen64plus_next_libretro", "parallel_n64_libretro"}
                : system.equals("Nintendo DS") ? new String[] {"melonds_libretro", "desmume_libretro"}
                : new String[] {"fceumm_libretro", "nestopia_libretro"};
        for (String name : names) { File core = new File(folder, name + extension); if (core.isFile()) return core; }
        return null;
    }
    private static String friendly(Exception error) {
        String text = error.getMessage(); return text == null || text.trim().isEmpty() ? error.getClass().getSimpleName() : text;
    }
}
