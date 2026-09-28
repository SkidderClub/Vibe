package dev.vibe.launcher.install;

import dev.vibe.launcher.Check;
import dev.vibe.launcher.core.Progress;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Archive extraction, changelog parsing and the Java home search. */
public final class InstallTests {
    private InstallTests() { }

    public static void run(Check check, Path temp) {
        check.test("zip extraction refuses entries outside the target", () -> {
            Path zip = temp.resolve("evil.zip");
            ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip));
            output.putNextEntry(new ZipEntry("ok/file.txt"));
            output.write("fine".getBytes(StandardCharsets.UTF_8));
            output.putNextEntry(new ZipEntry("../../escape.txt"));
            output.write("bad".getBytes(StandardCharsets.UTF_8));
            output.close();
            Check.fails(IOException.class, () -> Archives.unzip(zip, temp.resolve("zip-out"), "", Progress.NONE));
            Check.isTrue(!Files.exists(temp.resolve("escape.txt")) && !Files.exists(temp.getParent().resolve("escape.txt")), "nothing escaped");
        });

        check.test("zip extraction keeps structure and marks gradlew executable", () -> {
            Path zip = temp.resolve("source.zip");
            ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip));
            output.putNextEntry(new ZipEntry("Vibe-abc/"));
            output.putNextEntry(new ZipEntry("Vibe-abc/gradlew"));
            output.write("#!/bin/sh\n".getBytes(StandardCharsets.UTF_8));
            output.putNextEntry(new ZipEntry("Vibe-abc/src/main/A.java"));
            output.write("class A {}".getBytes(StandardCharsets.UTF_8));
            output.close();
            Archives.unzip(zip, temp.resolve("source-out"), "", Progress.NONE);
            Check.isTrue(Files.isRegularFile(temp.resolve("source-out/Vibe-abc/src/main/A.java")), "nested file extracted");
            Check.isTrue(Files.isExecutable(temp.resolve("source-out/Vibe-abc/gradlew")) || isWindows(), "gradlew executable");
        });

        check.test("tar.gz extraction handles GNU long names, PAX paths and modes", () -> {
            ByteArrayOutputStream tar = new ByteArrayOutputStream();
            String longName = "jdk-21/" + repeat("very-long-directory-name/", 5) + "file.txt";
            entry(tar, "././@LongLink", 'L', longName.getBytes(StandardCharsets.UTF_8), 0644);
            entry(tar, longName.substring(0, 99), '0', "long".getBytes(StandardCharsets.UTF_8), 0644);
            String paxPath = "jdk-21/" + repeat("pax-segment/", 9) + "pax.txt";
            byte[] record = paxRecord("path", paxPath);
            entry(tar, "PaxHeaders/pax", 'x', record, 0644);
            entry(tar, "short-name-ignored", '0', "pax".getBytes(StandardCharsets.UTF_8), 0644);
            entry(tar, "jdk-21/bin/", '5', new byte[0], 0755);
            entry(tar, "jdk-21/bin/java", '0', "#!/bin/sh".getBytes(StandardCharsets.UTF_8), 0755);
            entry(tar, "jdk-21/lib/modules", '0', new byte[] { 1 }, 0644);
            entry(tar, "jdk-21/legal/link", '2', new byte[0], 0777);
            tar.write(new byte[1024]);
            Path archive = temp.resolve("runtime.tar.gz");
            OutputStream gzip = new GZIPOutputStream(Files.newOutputStream(archive));
            gzip.write(tar.toByteArray());
            gzip.close();
            Path out = temp.resolve("tar-out");
            Archives.untarGz(archive, out, "", Progress.NONE);
            Check.equal("long", new String(Files.readAllBytes(out.resolve(longName)), StandardCharsets.UTF_8));
            Check.equal("pax", new String(Files.readAllBytes(out.resolve(paxPath)), StandardCharsets.UTF_8));
            Check.isTrue(Files.isExecutable(out.resolve("jdk-21/bin/java")) || isWindows(), "mode applied");
            if (!isWindows()) Check.equal(out.resolve("jdk-21"), RuntimeManager.findHome(out, false));
            Check.isTrue(!Files.exists(out.resolve("jdk-21/legal/link"), java.nio.file.LinkOption.NOFOLLOW_LINKS), "symbolic links are skipped");
        });

        check.test("self-update helper replaces the launcher JAR", () -> {
            Path staged = Files.write(temp.resolve("VibeLauncher-9.9.9.jar"), "new".getBytes(StandardCharsets.UTF_8));
            Path current = Files.write(Files.createDirectories(temp.resolve("Vibe Spiele")).resolve("VibeLauncher.jar"), "old".getBytes(StandardCharsets.UTF_8));
            Path lock = temp.resolve("launcher.lock");
            // "java -version" stands in for starting the launcher again.
            String executable = java.nio.file.Paths.get(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString();
            SelfUpdate.main(new String[] { staged.toString(), current.toString(), executable, lock.toString() });
            Check.equal("new", new String(Files.readAllBytes(current), StandardCharsets.UTF_8));
        });

        check.test("an interrupted update's profile is put back", () -> {
            Path root = Files.createDirectories(temp.resolve("launcher-home"));
            System.setProperty("vibe.launcher.home", root.toString());
            dev.vibe.launcher.core.AppPaths paths = dev.vibe.launcher.core.AppPaths.detect();
            System.clearProperty("vibe.launcher.home");
            Files.createDirectories(root.resolve("source").resolve("src"));
            Path world = Files.createDirectories(root.resolve("source-old-123").resolve("run").resolve("client").resolve("saves"));
            Files.write(world.resolve("level.dat"), new byte[] { 7 });
            dev.vibe.launcher.core.AppLog log = new dev.vibe.launcher.core.AppLog(root.resolve("logs"));
            SourceManager manager = new SourceManager(paths, new dev.vibe.launcher.core.Settings(root.resolve("s.properties"), log), log);
            manager.cleanLeftovers();
            Check.isTrue(Files.isRegularFile(root.resolve("source/run/client/saves/level.dat")), "profile restored");
            Check.isTrue(!Files.exists(root.resolve("source-old-123")), "empty leftover removed");
        });

        check.test("changelog sections become plain text", () -> {
            String markdown = "# Vibe changelog\n\n## Unreleased\n\n- Added **GTA8** in [docs/GTA8.md](GTA8.md), run `./gradlew x`.\n"
                    + "  Continued line.\n- Second\n\n## v0.0.5\n\n* Old entry\n";
            List<ChangelogSection> sections = ChangelogSection.parse(markdown, 4);
            Check.equal(2, sections.size());
            Check.equal("Unreleased", sections.get(0).title);
            Check.equal("Added GTA8 in docs/GTA8.md, run ./gradlew x. Continued line.", sections.get(0).entries.get(0));
            Check.equal("Second", sections.get(0).entries.get(1));
            Check.equal("Old entry", sections.get(1).entries.get(0));
            Check.equal(1, ChangelogSection.parse(markdown, 1).size());
        });

        check.test("GitHub timestamps parse as UTC", () -> {
            Check.equal(1790529960000L, SourceManager.parseTime("2026-09-27T17:26:00Z"));
            Check.equal(0L, SourceManager.parseTime("garbage"));
        });
    }

    private static boolean isWindows() { return System.getProperty("os.name", "").toLowerCase().contains("win"); }

    private static String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < count; index++) builder.append(value);
        return builder.toString();
    }

    private static byte[] paxRecord(String key, String value) {
        String body = " " + key + "=" + value + "\n";
        int length = body.length();
        int digits = Integer.toString(length).length();
        int total = length + digits;
        if (Integer.toString(total).length() != digits) total++;
        return (total + body).getBytes(StandardCharsets.UTF_8);
    }

    /** Writes one ustar header plus padded data. */
    private static void entry(ByteArrayOutputStream tar, String name, char type, byte[] data, int mode) throws IOException {
        byte[] header = new byte[512];
        put(header, 0, name, 100);
        put(header, 100, String.format("%07o", mode), 8);
        put(header, 108, "0000000", 8);
        put(header, 116, "0000000", 8);
        put(header, 124, String.format("%011o", data.length), 12);
        put(header, 136, "00000000000", 12);
        header[156] = (byte) type;
        put(header, 257, "ustar", 6);
        put(header, 263, "00", 2);
        for (int index = 148; index < 156; index++) header[index] = ' ';
        int sum = 0;
        for (byte value : header) sum += value & 0xFF;
        put(header, 148, String.format("%06o", sum), 7);
        tar.write(header);
        tar.write(data);
        int padding = (512 - data.length % 512) % 512;
        tar.write(new byte[padding]);
    }

    private static void put(byte[] header, int offset, String value, int length) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(bytes, 0, header, offset, Math.min(bytes.length, length));
    }
}
