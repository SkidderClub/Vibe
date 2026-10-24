package dev.vibe.launcher.minecraft;

import dev.vibe.launcher.VibeLauncher;
import dev.vibe.launcher.core.Platform;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The exact {@code java} command that starts Forge 1.8.9 with OptiFine and the
 * Vibe JAR from {@code mods/}.
 *
 * <p>Forge's own {@code minecraftArguments} template is filled with the local,
 * credential-free account values; without a template the equivalent arguments
 * are built by hand. Minecraft runs as a direct Java process, so the launcher can
 * stop it without a Gradle wrapper in between.</p>
 */
public final class LaunchCommand {
    public static final class Launch {
        public final List<String> command;
        public final Path workDir;
        public final Map<String, String> environment;

        public Launch(List<String> command, Path workDir, Map<String, String> environment) {
            this.command = command;
            this.workDir = workDir;
            this.environment = environment;
        }
    }

    private static final String TWEAK_CLASS = "optifine.OptiFineForgeTweaker";

    private LaunchCommand() { }

    public static Launch build(Path java, GameRuntime.Prepared prepared, Map<String, String> values,
            int memoryMb, List<String> gameArguments, Path readyFile) {
        List<String> command = new ArrayList<String>();
        command.add(java.toString());
        command.add("-Xmx" + memoryMb + "m");
        command.add("-Djava.library.path=" + prepared.natives);
        command.add("-Dminecraft.launcher.brand=" + VibeLauncher.PRODUCT.replace(' ', '_'));
        command.add("-Dminecraft.launcher.version=" + VibeLauncher.VERSION);
        command.add("-Dfml.ignoreInvalidMinecraftCertificates=true");
        command.add("-Dfml.ignorePatchDiscrepancies=true");
        if (Platform.os() == Platform.Os.MAC) command.add("-XstartOnFirstThread");
        command.add("-cp");
        command.add(classpath(prepared));
        command.add("net.minecraft.launchwrapper.Launch");
        command.addAll(arguments(gameArguments, values));

        Map<String, String> environment = new LinkedHashMap<String, String>();
        environment.put("VIBE_NO_PAUSE", "1");
        if (readyFile != null) environment.put("VIBE_LAUNCH_READY_FILE", readyFile.toString());
        return new Launch(command, prepared.gameDir, environment);
    }

    private static String classpath(GameRuntime.Prepared prepared) {
        StringBuilder text = new StringBuilder();
        for (Path entry : prepared.classpath) {
            if (text.length() > 0) text.append(File.pathSeparatorChar);
            text.append(entry);
        }
        return text.toString();
    }

    /** Fills the Forge template, or builds the 1.8.9 arguments when there is none. */
    static List<String> arguments(List<String> template, Map<String, String> values) {
        List<String> source = template == null || template.isEmpty() ? fallback(values) : template;
        List<String> result = new ArrayList<String>();
        boolean optifine = false;
        for (String argument : source) {
            String filled = fill(argument, values);
            result.add(filled);
            if (filled.equals(TWEAK_CLASS)) optifine = true;
        }
        if (result.isEmpty()) {
            for (String argument : fallback(values)) result.add(fill(argument, values));
        }
        // Forge's own tweak class stays: FMLTweaker hands the game arguments to
        // Main. OptiFine only registers its transformer, so it runs in addition.
        if (!optifine) {
            result.add("--tweakClass");
            result.add(TWEAK_CLASS);
        }
        return result;
    }

    private static List<String> fallback(Map<String, String> values) {
        List<String> args = new ArrayList<String>();
        args.add("--username");
        args.add(value(values, "auth_player_name", "Player"));
        args.add("--version");
        args.add(value(values, "version_name", "1.8.9-forge1.8.9-11.15.1.2318"));
        args.add("--gameDir");
        args.add(value(values, "game_directory", "."));
        args.add("--assetsDir");
        args.add(value(values, "assets_root", "assets"));
        args.add("--assetIndex");
        args.add(value(values, "assets_index_name", "1.8"));
        args.add("--uuid");
        args.add(value(values, "auth_uuid", ""));
        args.add("--accessToken");
        args.add(value(values, "auth_access_token", "0"));
        args.add("--userProperties");
        args.add(value(values, "user_properties", "{}"));
        args.add("--userType");
        args.add(value(values, "user_type", "legacy"));
        return args;
    }

    private static String fill(String argument, Map<String, String> values) {
        String result = argument;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            if (result.contains(placeholder)) result = result.replace(placeholder, entry.getValue());
        }
        return result;
    }

    private static String value(Map<String, String> values, String key, String fallback) {
        String value = values.get(key);
        return value == null || value.isEmpty() ? fallback : value;
    }
}
