package dev.vibe.launcher.minecraft;

import dev.vibe.launcher.Check;
import dev.vibe.launcher.core.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Maven coordinates, library rules, natives and the generated Java command. */
public final class MinecraftTests {
    public static void run(Check check, Path temp) throws Exception {

        check.test("Maven coordinates map to library paths", () -> {
            Check.equal("com/mojang/brigadier/1.0.6/brigadier-1.0.6.jar", MavenLibrary.path("com.mojang:brigadier:1.0.6"));
            Check.equal("org/lwjgl/lwjgl/lwjgl/2.9.4-nightly-20150209/lwjgl-2.9.4-nightly-20150209.jar",
                    MavenLibrary.path("org.lwjgl.lwjgl:lwjgl:2.9.4-nightly-20150209"));
            Check.equal("org/lwjgl/lwjgl/lwjgl-platform/2.9.4-nightly-20150209/lwjgl-platform-2.9.4-nightly-20150209-natives-windows.jar",
                    MavenLibrary.path("org.lwjgl.lwjgl:lwjgl-platform:2.9.4-nightly-20150209:natives-windows"));
            Check.equal("net/minecraftforge/forge/1.8.9-11.15.1.2318-1.8.9/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar",
                    MavenLibrary.path("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9:universal"));
            Check.equal("de/oceanlabs/mcp/mcp/1.8.9/mcp-1.8.9-srg.zip", MavenLibrary.path("de.oceanlabs.mcp:mcp:1.8.9@srg.zip"));
            Check.equal("net.minecraftforge:forge:universal", MavenLibrary.key("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9:universal"));
            Check.equal("1.8.9-11.15.1.2318-1.8.9", MavenLibrary.version("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9:universal"));
        });

        check.test("library rules respect this platform", () -> {
            Check.isTrue(MavenLibrary.allowed(Collections.emptyList()), "no rules means allowed");
            List<Object> windows = Json.array(Json.parse("[{\"action\":\"allow\",\"os\":{\"name\":\"windows\"}}]"));
            Check.equal("windows".equals(MavenLibrary.osName()), MavenLibrary.allowed(windows));
            List<Object> blocked = Json.array(Json.parse("[{\"action\":\"allow\"},{\"action\":\"disallow\",\"os\":{\"name\":\""
                    + MavenLibrary.osName() + "\"}}]"));
            Check.isTrue(!MavenLibrary.allowed(blocked), "the last matching rule wins");
            Check.isTrue(!MavenLibrary.allowed(Json.array(Json.parse("[{\"action\":\"allow\",\"features\":{\"has_custom_resolution\":true}}]"))),
                    "unknown features never match");
        });

        check.test("natives-only libraries are not downloaded as a main JAR", () -> {
            Map<String, Object> jinput = Json.object(Json.parse(
                    "{\"name\":\"net.java.jinput:jinput-platform:2.0.5\","
                    + "\"natives\":{\"windows\":\"natives-windows\"},"
                    + "\"downloads\":{\"classifiers\":{\"natives-windows\":{\"path\":"
                    + "\"net/java/jinput/jinput-platform/2.0.5/jinput-platform-2.0.5-natives-windows.jar\"}}}}"));
            Check.isTrue(GameRuntime.classifierOnly(jinput), "jinput-platform has no main JAR");
            Map<String, Object> lwjgl = Json.object(Json.parse(
                    "{\"name\":\"org.lwjgl.lwjgl:lwjgl-platform:2.9.4-nightly-20150209\","
                    + "\"natives\":{\"windows\":\"natives-windows\"},"
                    + "\"downloads\":{\"artifact\":{\"path\":"
                    + "\"org/lwjgl/lwjgl/lwjgl-platform/2.9.4-nightly-20150209/lwjgl-platform-2.9.4-nightly-20150209.jar\"},"
                    + "\"classifiers\":{\"natives-windows\":{\"path\":\"natives.jar\"}}}}"));
            Check.isTrue(!GameRuntime.classifierOnly(lwjgl), "a real main JAR is kept");
            Check.isTrue(!GameRuntime.classifierOnly(Json.object(Json.parse("{\"name\":\"net.minecraft:launchwrapper:1.12\"}"))),
                    "Forge libraries without a downloads block still need their main JAR");
            Check.isTrue(!GameRuntime.classifierOnly(Json.object(Json.parse("{\"name\":\"x:y:1.0\"}"))),
                    "plain libraries are kept");
        });

        check.test("the legacy ${arch} native placeholder is expanded", () -> {
            String classifier = GameRuntime.nativeClassifier("natives-windows-${arch}");
            Check.isTrue(classifier.equals("natives-windows-32") || classifier.equals("natives-windows-64"),
                    "expanded to the 32- or 64-bit classifier");
            Check.isTrue(!classifier.contains("${"), "no placeholder remains");
            Check.equal("natives-osx", GameRuntime.nativeClassifier("natives-osx"));
        });

        check.test("duplicate asset hashes are downloaded once", () -> {
            Map<String, Object> objects = Json.object(Json.parse(
                    "{\"icons/minecraft.icns\":{\"hash\":\"991b421dfd401f115241601b2b373140a8d78572\",\"size\":114786},"
                    + "\"minecraft/icons/minecraft.icns\":{\"hash\":\"991b421dfd401f115241601b2b373140a8d78572\",\"size\":114786},"
                    + "\"sound\":{\"hash\":\"bdf48ef6b5d0d23bbb02e17d04865216179f510a\",\"size\":7},"
                    + "\"broken\":{\"hash\":\"not-a-hash\",\"size\":1}}"));
            Map<String, Long> unique = GameRuntime.uniqueAssets(objects);
            Check.equal(2, unique.size());
            Check.equal(114786L, unique.get("991b421dfd401f115241601b2b373140a8d78572"));
        });

        check.test("natives unpack without META-INF or escapes", () -> {
            Path jar = temp.resolve("natives.jar");
            ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar));
            try {
                write(zip, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n");
                write(zip, "lwjgl.dll", "dll");
                write(zip, "sub/native.so", "so");
                zip.putNextEntry(new ZipEntry("../escape.txt"));
                zip.write("bad".getBytes(StandardCharsets.UTF_8));
            } finally {
                zip.close();
            }
            Path target = temp.resolve("natives");
            Natives.extract(jar, target, new ArrayList<String>());
            Check.isTrue(Files.isRegularFile(target.resolve("lwjgl.dll")), "dll unpacked");
            Check.isTrue(Files.isRegularFile(target.resolve("sub").resolve("native.so")), "nested .so unpacked");
            Check.isTrue(!Files.exists(target.resolve("META-INF")), "META-INF skipped");
            Check.isTrue(!Files.exists(temp.resolve("escape.txt")), "path traversal refused");
            Check.isTrue(Natives.hasFiles(target), "folder not empty");
        });

        check.test("the Forge template keeps its tweak class and adds OptiFine", () -> {
            Map<String, String> values = values();
            List<String> template = Arrays.asList("--username", "${auth_player_name}", "--tweakClass",
                    "net.minecraftforge.fml.common.launcher.FMLTweaker", "--gameDir", "${game_directory}", "--assetIndex", "${assets_index_name}");
            List<String> args = LaunchCommand.arguments(template, values);
            Check.isTrue(args.contains("net.minecraftforge.fml.common.launcher.FMLTweaker"), "Forge tweak kept: " + args);
            Check.equal("Player", args.get(args.indexOf("--username") + 1));
            Check.equal("1.8", args.get(args.indexOf("--assetIndex") + 1));
            Check.equal("optifine.OptiFineForgeTweaker", args.get(args.size() - 1));
            Check.equal("--tweakClass", args.get(args.size() - 2));
        });

        check.test("without a template the 1.8.9 arguments are built", () -> {
            List<String> args = LaunchCommand.arguments(Collections.<String>emptyList(), values());
            Check.equal("Player", args.get(args.indexOf("--username") + 1));
            Check.equal("1.8", args.get(args.indexOf("--assetIndex") + 1));
            Check.isTrue(args.contains("--userType") && args.contains("--accessToken"), "credential-free account arguments: " + args);
            Check.equal("optifine.OptiFineForgeTweaker", args.get(args.size() - 1));
        });

        check.test("the command starts Java directly with the classpath", () -> {
            Path game = Files.createDirectories(temp.resolve("profile"));
            Path natives = Files.createDirectories(temp.resolve("natives"));
            Path assets = Files.createDirectories(temp.resolve("assets"));
            List<Path> classpath = Arrays.asList(temp.resolve("client.jar"), temp.resolve("forge.jar"), temp.resolve("OptiFine.jar"));
            GameRuntime.Prepared prepared = new GameRuntime.Prepared(game, temp, classpath.get(0), classpath.get(1), classpath.get(2),
                    natives, assets, assets.resolve("indexes").resolve("1.8.json"), "1.8", "1.8.9-forge1.8.9-11.15.1.2318",
                    classpath, Collections.<String>emptyList());
            LaunchCommand.Launch launch = LaunchCommand.build(temp.resolve("java"), prepared, values(), 3072,
                    Collections.<String>emptyList(), temp.resolve("ready"));
            String command = String.join(" ", launch.command);
            Check.isTrue(command.contains("-Xmx3072m"), command);
            Check.isTrue(command.contains("-Djava.library.path=" + natives), command);
            Check.isTrue(command.contains("net.minecraft.launchwrapper.Launch"), command);
            Check.isTrue(command.contains(classpath.get(0).toString()), command);
            Check.equal(game, launch.workDir);
            Check.equal(temp.resolve("ready").toString(), launch.environment.get("VIBE_LAUNCH_READY_FILE"));
        });

        check.test("the offline UUID is deterministic", () -> {
            String uuid = GameRuntime.offlineUuid("Player");
            Check.equal(uuid, GameRuntime.offlineUuid("Player"));
            Check.equal(UUID.nameUUIDFromBytes("OfflinePlayer:Player".getBytes(StandardCharsets.UTF_8)).toString(), uuid);
            Check.isTrue(!uuid.equals(GameRuntime.offlineUuid("Notch")), "different names, different UUIDs");
        });
    }

    private static Map<String, String> values() {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("auth_player_name", "Player");
        values.put("version_name", "1.8.9-forge1.8.9-11.15.1.2318");
        values.put("game_directory", "/game");
        values.put("assets_root", "/assets");
        values.put("assets_index_name", "1.8");
        values.put("auth_uuid", "0");
        values.put("auth_access_token", "0");
        values.put("user_properties", "{}");
        values.put("user_type", "legacy");
        return values;
    }

    private static void write(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
    }
}
