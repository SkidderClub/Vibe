package dev.vibe.launcher;

import dev.vibe.launcher.core.AppLog;
import dev.vibe.launcher.core.FileUtil;
import dev.vibe.launcher.core.I18n;
import dev.vibe.launcher.core.Json;
import dev.vibe.launcher.core.Settings;
import dev.vibe.launcher.core.Version;
import dev.vibe.launcher.game.GameProfile;
import dev.vibe.launcher.game.LaunchMode;
import dev.vibe.launcher.game.Theme;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Runs every launcher test; used by buildLauncher before packaging. Exit code 1 on failure. */
public final class LauncherTests {
    private LauncherTests() { }

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("vibe-launcher-tests");
        Check check = new Check();
        try {
            System.out.println("core");
            core(check, temp);
            System.out.println("install");
            dev.vibe.launcher.install.InstallTests.run(check, temp);
            System.out.println("game");
            dev.vibe.launcher.game.GameTests.run(check, temp);
            System.out.println("skin");
            dev.vibe.launcher.skin.SkinTests.run(check);
            System.out.println("errors");
            dev.vibe.launcher.core.ErrorTests.run(check);
        } finally {
            FileUtil.deleteTreeQuietly(temp);
        }
        System.out.println(check.passed() + " passed, " + check.failed() + " failed");
        System.exit(check.failed() == 0 ? 0 : 1);
    }

    private static void core(Check check, Path temp) {
        check.test("json parses nested documents and escapes", () -> {
            Map<String, Object> root = Json.object(Json.parse("{\"a\":[1,2.5,{\"b\":\"x\\\"y\\u00e4\\n\"}],\"t\":true,\"n\":null}"));
            List<Object> a = Json.array(root, "a");
            Check.equal(3, a.size());
            Check.equal(1L, Json.number(Json.object(Json.parse("{\"v\":1}")), "v", 0));
            Check.equal("x\"y\u00e4\n", Json.string(Json.object(a.get(2)), "b"));
            Check.isTrue(Json.bool(root, "t"), "true literal");
            Check.isTrue(root.containsKey("n") && root.get("n") == null, "null literal");
        });
        check.test("json rejects broken input", () -> {
            Check.fails(IOException.class, () -> Json.parse("{\"a\":"));
            Check.fails(IOException.class, () -> Json.parse("[1,2] x"));
            Check.fails(IOException.class, () -> Json.parse("{\"a\":\"unterminated}"));
        });
        check.test("json redacts secrets without materialising them", () -> {
            char[] text = "{\"name\":\"x\",\"refreshToken\":\"secret\\\"value\",\"empty\":\"\"}".toCharArray();
            Map<String, Object> root = Json.object(Json.parse(text, new HashSet<String>(Arrays.asList("refreshToken", "empty"))));
            Check.equal(Boolean.TRUE, root.get("refreshToken"));
            Check.equal(Boolean.FALSE, root.get("empty"));
            Check.isTrue(!root.toString().contains("secret"), "secret must not be present");
        });
        check.test("versions compare numerically", () -> {
            Check.isTrue(Version.isNewer("2.10.0", "2.9.3"), "2.10.0 > 2.9.3");
            Check.isTrue(Version.isNewer("v2.0.1", "2.0.0"), "prefix ignored");
            Check.isTrue(!Version.isNewer("2.0", "2.0.0"), "equal versions");
            Check.isTrue(!Version.isNewer("1.9.9", "2.0.0"), "older version");
        });
        check.test("paths from GitHub cannot escape the checkout", () -> {
            Path root = temp.resolve("root");
            Check.equal(root.resolve("src").resolve("A.java").normalize(), FileUtil.resolveInside(root, "src/A.java"));
            for (String bad : new String[] { "../x", "/etc/passwd", "a/../../b", "C:/x", "a//b", "a\\..\\b", "" }) {
                Check.fails(IOException.class, () -> FileUtil.resolveInside(root, bad));
            }
        });
        check.test("settings clamp memory and keep legacy keys", () -> {
            Path file = temp.resolve("settings.properties");
            Properties legacy = new Properties();
            legacy.setProperty("selectedUuid", "069a79f4-44e9-4726-a5be-fca90e38aaf5");
            legacy.setProperty("projectRoot", "C:/old");
            legacy.setProperty("memoryMb", "100000");
            FileUtil.writeProperties(file, legacy, null);
            Settings settings = new Settings(file, new AppLog(temp.resolve("logs")));
            Check.equal("069a79f4-44e9-4726-a5be-fca90e38aaf5", settings.selectedUuid());
            Check.equal(Settings.maxMemoryMb(), settings.memoryMb());
            settings.setMemoryMb(2000);
            Check.equal(1792, settings.memoryMb());
            Check.isTrue(!FileUtil.readProperties(file).containsKey("projectRoot"), "obsolete key removed");
        });
        check.test("bridge and theme files match what Vibe reads", () -> {
            GameProfile profile = new GameProfile(temp.resolve("profile"));
            profile.writeBridge(LaunchMode.GTA8, "069a79f4-44e9-4726-a5be-fca90e38aaf5", "Notch");
            Properties bridge = FileUtil.readProperties(temp.resolve("profile/vibe/launcher.properties"));
            Check.equal("gta8", bridge.getProperty("mode"));
            Check.equal("Notch", bridge.getProperty("selectedName"));
            Check.equal(null, profile.readTheme());
            Properties menu = new Properties();
            menu.setProperty("shader", "prestige.frag");
            FileUtil.writeProperties(temp.resolve("profile/vibe/menu.properties"), menu, null);
            profile.writeTheme(Theme.MINT);
            Check.equal(Theme.MINT, profile.readTheme());
            Check.equal("prestige.frag", FileUtil.readProperties(temp.resolve("profile/vibe/menu.properties")).getProperty("shader"));
        });
        check.test("german translations and placeholders", () -> {
            I18n.init("de", "");
            Check.equal("vor 3 Minuten", I18n.t("{0} minutes ago", 3));
            Check.equal("SPIELEN", I18n.t("PLAY"));
            Check.equal("Untranslated 7", I18n.t("Untranslated {0}", 7));
            I18n.init("auto", "English");
            Check.equal("PLAY", I18n.t("PLAY"));
        });
        check.test("text files are written atomically", () -> {
            Path file = temp.resolve("atomic/test.txt");
            FileUtil.writeText(file, "first");
            FileUtil.writeText(file, "second");
            Check.equal("second", new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            Check.equal(1, FileUtil.children(file.getParent()).size());
        });
    }
}
