package dev.vibe.launcher.versions;

import dev.vibe.launcher.Check;
import dev.vibe.launcher.core.AppPaths;
import dev.vibe.launcher.core.AppLog;
import dev.vibe.launcher.core.ErrorCode;
import dev.vibe.launcher.core.LauncherException;
import dev.vibe.launcher.core.Settings;
import dev.vibe.launcher.install.ChangelogSection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Release parsing, release notes, and the downloaded version store. */
public final class ReleaseTests {
    public static void run(Check check, Path temp) throws Exception {

        check.test("releases parse, drafts drop and assets get their checksum", () -> {
            String json = "[\n"
                    + " {\"tag_name\":\"v0.0.10\",\"name\":\"Vibe 0.0.10\",\"draft\":false,\"prerelease\":false,"
                    + "\"body\":\"## 0.0.10\\n\\n### Changes\\n- Fixed **crashes**\\n  on startup\\n- Added `GTA8`\","
                    + "\"published_at\":\"2026-10-01T10:00:00Z\",\"html_url\":\"https://github.com/VibeUpdate/VibeReleases/releases/tag/v0.0.10\","
                    + "\"assets\":[{\"name\":\"Vibe-1.8.9-0.0.10.jar\",\"browser_download_url\":\"https://github.com/x/0.0.10.jar\","
                    + "\"digest\":\"sha256:" + repeat("a", 64) + "\",\"size\":1234},"
                    + "{\"name\":\"Vibe-1.8.9-0.0.10.jar.sha256\",\"browser_download_url\":\"https://github.com/x/0.0.10.sha256\"}]},\n"
                    + " {\"tag_name\":\"v0.0.9\",\"name\":\"Draft\",\"draft\":true,\"assets\":[]},\n"
                    + " {\"tag_name\":\"v0.0.11-beta\",\"name\":\"Vibe 0.0.11 beta\",\"draft\":false,\"prerelease\":true,"
                    + "\"body\":\"\",\"published_at\":\"2026-10-20T10:00:00Z\",\"html_url\":\"https://github.com/x\","
                    + "\"assets\":[{\"name\":\"Vibe-1.8.9-0.0.11-beta.jar\",\"browser_download_url\":\"https://github.com/x/b.jar\",\"size\":99}]},\n"
                    + " {\"tag_name\":\"v0.0.8\",\"name\":\"Old\",\"draft\":false,\"prerelease\":false,\"body\":\"## 0.0.8\\n- Old\","
                    + "\"published_at\":\"2026-09-01T10:00:00Z\",\"html_url\":\"https://github.com/x\","
                    + "\"assets\":[{\"name\":\"Vibe-1.8.9-0.0.8.jar\",\"browser_download_url\":\"https://github.com/x/8.jar\"},"
                    + "{\"name\":\"Vibe-1.8.9-0.0.8.jar.sha256\",\"browser_download_url\":\"https://github.com/x/8.sha256\"}]}\n"
                    + "]";
            List<ReleaseCatalog.Release> releases = ReleaseCatalog.parse(json);
            Check.equal(3, releases.size());
            Check.equal("v0.0.11-beta", releases.get(0).tag);
            Check.isTrue(releases.get(0).prerelease, "pre-release flag");
            Check.equal("v0.0.10", releases.get(1).tag);
            Check.equal("0.0.10", releases.get(1).version());
            Check.equal(repeat("a", 64), releases.get(1).asset.sha256);
            Check.equal(1234L, releases.get(1).asset.size);
            Check.equal(null, releases.get(1).asset.sumUrl);
            Check.equal("https://github.com/x/8.sha256", releases.get(2).asset.sumUrl);
            Check.equal("", releases.get(2).asset.sha256);
            Check.isTrue(releases.get(1).published > releases.get(2).published, "newest first by date");
        });

        check.test("release notes become sections", () -> {
            List<ChangelogSection> sections = ReleaseNotes.parse(
                    "## 0.0.6\n\n### Changes\n- Added **GTA8** in [docs](GTA8.md).\n  Continued here.\n- Second\n\n### Fixes\n* A crash\n");
            Check.equal(2, sections.size());
            Check.equal("Changes", sections.get(0).title);
            Check.equal("Added GTA8 in docs. Continued here.", sections.get(0).entries.get(0));
            Check.equal("Second", sections.get(0).entries.get(1));
            Check.equal("Fixes", sections.get(1).title);
            Check.equal("A crash", sections.get(1).entries.get(0));

            List<ChangelogSection> plain = ReleaseNotes.parse("- One\n- Two");
            Check.equal(1, plain.size());
            Check.equal("Changes", plain.get(0).title);
            Check.equal(2, plain.get(0).entries.size());
            Check.equal(0, ReleaseNotes.parse("   ").size());
        });

        check.test("installed versions are read, linked and removed", () -> {
            Path root = Files.createDirectories(temp.resolve("store"));
            System.setProperty("vibe.launcher.home", root.toString());
            AppPaths paths = AppPaths.detect();
            System.clearProperty("vibe.launcher.home");
            AppLog log = new AppLog(root.resolve("logs"));
            Settings settings = new Settings(root.resolve("settings.properties"), log);
            VersionStore store = new VersionStore(paths, settings, log);

            Path folder = Files.createDirectories(paths.versions().resolve("v0.0.6"));
            writeJar(folder.resolve("Vibe-1.8.9-0.0.6.jar"));
            Properties meta = new Properties();
            meta.setProperty("tag", "v0.0.6");
            meta.setProperty("name", "Vibe 0.0.6");
            meta.setProperty("version", "0.0.6");
            meta.setProperty("asset", "Vibe-1.8.9-0.0.6.jar");
            meta.setProperty("published", "1790844000000");
            meta.setProperty("size", "1234");
            dev.vibe.launcher.core.FileUtil.writeProperties(folder.resolve("release.properties"), meta, "test");
            Files.write(folder.resolve("changelog.md"), "## 0.0.6\n- Fixed a crash\n".getBytes(StandardCharsets.UTF_8));

            List<VersionStore.Installed> installed = store.listInstalled();
            Check.equal(1, installed.size());
            Check.equal("0.0.6", installed.get(0).displayVersion());
            Check.equal("Vibe-1.8.9-0.0.6.jar", installed.get(0).jar.getFileName().toString());
            Check.equal("Fixed a crash", store.notes("v0.0.6").get(0).entries.get(0));

            Path mods = Files.createDirectories(paths.profile().resolve("mods"));
            Files.write(mods.resolve("Vibe-1.8.9-0.0.5.jar"), new byte[] { 1 });
            Files.write(mods.resolve("OptiFine.jar"), new byte[] { 2 });
            VersionStore.Installed selected = store.select("v0.0.6", mods);
            Check.equal("v0.0.6", settings.selectedVibeVersion());
            Check.isTrue(Files.isRegularFile(mods.resolve(selected.jar.getFileName())), "active JAR linked");
            Check.isTrue(!Files.exists(mods.resolve("Vibe-1.8.9-0.0.5.jar")), "old managed JAR removed");
            Check.isTrue(Files.exists(mods.resolve("OptiFine.jar")), "other mods survive");

            store.remove("v0.0.6");
            Check.equal(0, store.listInstalled().size());
            Check.equal("", settings.selectedVibeVersion());

            Files.write(Files.createDirectories(paths.versions().resolve("v0.0.7")).resolve("x.part"), new byte[] { 1 });
            store.cleanLeftovers();
            Check.isTrue(!Files.exists(paths.versions().resolve("v0.0.7").resolve("x.part")), "partial download removed");
        });

        check.test("a release without a checksum is not installed", () -> {
            Path root = Files.createDirectories(temp.resolve("unchecked"));
            System.setProperty("vibe.launcher.home", root.toString());
            AppPaths paths = AppPaths.detect();
            System.clearProperty("vibe.launcher.home");
            AppLog log = new AppLog(root.resolve("logs"));
            VersionStore store = new VersionStore(paths, new Settings(root.resolve("settings.properties"), log), log);
            List<ReleaseCatalog.Release> releases = ReleaseCatalog.parse("[{\"tag_name\":\"v0.0.9\",\"name\":\"\",\"body\":\"\","
                    + "\"published_at\":\"2026-10-01T10:00:00Z\",\"html_url\":\"https://github.com/x\","
                    + "\"assets\":[{\"name\":\"Vibe-1.8.9-0.0.9.jar\",\"browser_download_url\":\"https://github.com/x/9.jar\",\"size\":99}]}]");
            Check.equal(1, releases.size());
            try {
                store.install(releases.get(0), null);
                Check.isTrue(false, "an unverifiable JAR must be refused");
            } catch (LauncherException error) {
                Check.equal(ErrorCode.RELEASE_CHECKSUM, error.code);
            }
            Check.isTrue(!Files.exists(paths.versions().resolve("v0.0.9")), "nothing was downloaded");
        });

        check.test("a damaged file is not a Vibe JAR", () -> {
            Path html = temp.resolve("damaged.jar");
            Files.write(html, "<html>error</html>".getBytes(StandardCharsets.UTF_8));
            Check.isTrue(!VersionStore.isVibeJar(html), "HTML is refused");
            Path real = temp.resolve("real.jar");
            writeJar(real);
            Check.isTrue(VersionStore.isVibeJar(real), "a mod JAR is accepted");
        });
    }

    private static void writeJar(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file));
        try {
            zip.putNextEntry(new ZipEntry("mcmod.info"));
            zip.write("[{\"modid\":\"vibe\"}]".getBytes(StandardCharsets.UTF_8));
        } finally {
            zip.close();
        }
    }

    private static String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < count; index++) builder.append(value);
        return builder.toString();
    }
}
