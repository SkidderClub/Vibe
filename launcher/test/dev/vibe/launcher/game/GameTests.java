package dev.vibe.launcher.game;

import dev.vibe.launcher.Check;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Account vault, mod library and the Gradle session, the latter against a fake gradlew. */
public final class GameTests {
    private GameTests() { }

    public static void run(Check check, Path temp) {
        check.test("vault yields names and types, never tokens", () -> {
            Path directory = Files.createDirectories(temp.resolve("accounts"));
            String offline = UUID.nameUUIDFromBytes("OfflinePlayer:Alex".getBytes(StandardCharsets.UTF_8)).toString();
            byte[] key = writeVault(directory, "{\"version\":2,\"accounts\":[{\"name\":\"Notch\",\"uuid\":\"069a79f4-44e9-4726-a5be-fca90e38aaf5\","
                    + "\"refreshToken\":\"TOP-SECRET\",\"application\":\"IAS\"},{\"name\":\"Alex\",\"uuid\":\"" + offline
                    + "\",\"refreshToken\":\"\",\"application\":\"IAS\"},{\"name\":\"bad name!\",\"uuid\":\"x\",\"refreshToken\":\"\"}]}");
            Files.write(directory.resolve("auto-login.txt"), ("offline:" + offline).getBytes(StandardCharsets.UTF_8));
            List<AccountVault.Account> accounts = AccountVault.read(directory);
            Check.equal(2, accounts.size());
            Check.equal("Notch", accounts.get(0).name);
            Check.isTrue(accounts.get(0).microsoft && !accounts.get(0).autoLogin, "Microsoft account");
            Check.isTrue(!accounts.get(1).microsoft && accounts.get(1).autoLogin, "offline auto-login account");
            // A different key must fail without echoing vault content.
            key[0] ^= 1;
            Files.write(directory.resolve("accounts.key"), key);
            IOException error = Check.fails(IOException.class, () -> AccountVault.read(directory));
            Check.isTrue(!String.valueOf(error.getMessage()).contains("SECRET"), "no content in error");
        });

        check.test("environment keys are replaced regardless of case", () -> {
            java.util.Map<String, String> environment = new java.util.HashMap<String, String>();
            environment.put("Path", "C:\\Windows");
            environment.put("OTHER", "x");
            Check.equal("C:\\Windows", GameSession.get(environment, "PATH"));
            GameSession.put(environment, "PATH", "C:\\jdk\\bin;C:\\Windows");
            Check.equal(2, environment.size());
            Check.equal("C:\\jdk\\bin;C:\\Windows", environment.get("Path"));
        });

        check.test("missing vault means no accounts", () -> Check.equal(0, AccountVault.read(temp.resolve("nowhere")).size()));

        check.test("mcmod.info in both formats", () -> {
            ModLibrary.ModInfo plain = new ModLibrary.ModInfo();
            ModLibrary.parseMcmodInfo("[{\"modid\":\"p\",\"name\":\"§aPatcher\",\"version\":\"${version}\",\"mcversion\":\"1.8.9\",\"authorList\":[\"Sk1er\",\"LLC\"]}]", plain);
            Check.equal("Patcher", plain.name);
            Check.equal("", plain.version);
            Check.equal("Sk1er, LLC", plain.authors);
            ModLibrary.ModInfo listed = new ModLibrary.ModInfo();
            ModLibrary.parseMcmodInfo("{\"modListVersion\":2,\"modList\":[{\"modid\":\"k\",\"name\":\"Keystrokes\",\"mcversion\":\"1.12.2\"}]}", listed);
            Check.equal("Keystrokes", listed.name);
            ModLibrary.ModInfo broken = new ModLibrary.ModInfo();
            ModLibrary.parseMcmodInfo("{not json", broken);
            Check.equal("", broken.name);
        });

        check.test("mods import, reject, disable and list", () -> {
            Path source = Files.createDirectories(temp.resolve("downloads"));
            File good = jar(source.resolve("Patcher.jar"), "mcmod.info", "[{\"modid\":\"patcher\",\"name\":\"Patcher\",\"mcversion\":\"1.8.9\"}]");
            File old = jar(source.resolve("Map.jar"), "mcmod.info", "[{\"modid\":\"map\",\"name\":\"Map\",\"mcversion\":\"1.12.2\"}]");
            File optifine = jar(source.resolve("OptiFine.jar"), "optifine/OptiFineTweaker.class", "x");
            File fabric = jar(source.resolve("Sodium.jar"), "fabric.mod.json", "{}");
            File vibe = jar(source.resolve("Vibe.jar"), "mcmod.info", "[{\"modid\":\"vibe\",\"name\":\"Vibe\"}]");
            File text = Files.write(source.resolve("readme.txt"), new byte[] { 1 }).toFile();
            File broken = Files.write(source.resolve("broken.jar"), new byte[] { 1, 2, 3 }).toFile();
            ModLibrary library = new ModLibrary(temp.resolve("mods"));
            ModLibrary.ImportResult result = library.importFiles(Arrays.asList(good, old, optifine, fabric, vibe, text, broken));
            Check.equal(Arrays.asList("Patcher", "Map"), result.added);
            Check.equal(5, result.rejected.size());
            Check.equal(1, library.importFiles(Collections.singletonList(good)).rejected.size());
            List<ModLibrary.Mod> mods = library.list();
            Check.equal(2, mods.size());
            ModLibrary.Mod map = mods.get(0);
            Check.equal("Map", map.name);
            Check.isTrue(map.wrongVersion(), "1.12.2 flagged");
            Check.isTrue(!mods.get(1).wrongVersion(), "1.8.9 accepted");
            library.setEnabled(map, false);
            Check.isTrue(Files.isRegularFile(temp.resolve("mods/Map.jar.disabled")), "renamed to .disabled");
            ModLibrary.Mod disabled = library.list().get(0);
            Check.isTrue(!disabled.enabled, "listed as disabled");
            library.setEnabled(disabled, true);
            Check.isTrue(Files.isRegularFile(temp.resolve("mods/Map.jar")), "enabled again");
            library.delete(library.list().get(0));
            Check.equal(1, library.list().size());
        });

        check.test("session follows stages, readiness and exit code", () -> {
            Path project = Files.createDirectories(temp.resolve("project-ok"));
            fakeWrapper(project);
            Recorder recorder = runSession(project, temp.resolve("ok"));
            Check.equal(Integer.valueOf(0), recorder.code);
            Check.isTrue(recorder.reachedGame, "ready file seen");
            Check.isTrue(recorder.stages.contains(GameSession.Stage.COMPILING), "compile stage: " + recorder.stages);
            Check.isTrue(recorder.stages.contains(GameSession.Stage.ASSETS), "asset stage: " + recorder.stages);
            Check.isTrue(recorder.stages.contains(GameSession.Stage.STARTING), "start stage: " + recorder.stages);
            Check.equal(GameSession.Stage.RUNNING, recorder.stages.get(recorder.stages.size() - 1));
            Check.isTrue(recorder.lines.contains("runClient") && recorder.lines.contains("-PvibeMaxMemory=2048"), "Gradle arguments: " + recorder.lines);
            Check.isTrue(recorder.lines.contains("[Client thread/INFO]: M\u00fcnchen \u2713"), "UTF-8 output: " + recorder.lines);
            Check.isTrue(recorder.lines.contains("2% (2/3)") && recorder.lines.contains("100% (3/3)"), "carriage returns split lines: " + recorder.lines);
            Path jdk = Paths.get(System.getProperty("java.home"));
            Check.isTrue(recorder.lines.contains("JAVA_HOME=" + jdk), "JAVA_HOME: " + recorder.lines);
            String inherited = System.getenv("PATH");
            String expectedPath = "PATH=" + jdk.resolve("bin") + File.pathSeparator + (inherited == null ? "" : inherited);
            Check.isTrue(recorder.lines.contains(expectedPath), "PATH keeps the system entries: " + recorder.lines);
        });

        check.test("failed build reports what went wrong", () -> {
            Path project = Files.createDirectories(temp.resolve("project-fail"));
            fakeWrapper(project);
            Files.write(project.resolve("fail.marker"), new byte[0]);
            Recorder recorder = runSession(project, temp.resolve("fail"));
            Check.equal(Integer.valueOf(1), recorder.code);
            Check.isTrue(!recorder.reachedGame, "never reached the game");
            Check.equal("Execution failed for task ':compileJava'.\nCompilation failed; see the compiler error output for details.", recorder.failure);
        });
    }

    private static final class Recorder implements GameSession.Listener {
        final List<String> lines = Collections.synchronizedList(new ArrayList<String>());
        final List<GameSession.Stage> stages = Collections.synchronizedList(new ArrayList<GameSession.Stage>());
        final CountDownLatch done = new CountDownLatch(1);
        volatile Integer code;
        volatile boolean reachedGame;
        volatile String failure;

        @Override public void output(List<String> added) { lines.addAll(added); }
        @Override public void stage(GameSession.Stage stage) { stages.add(stage); }
        @Override public void exited(Integer exitCode, boolean reached, String summary) {
            code = exitCode;
            reachedGame = reached;
            failure = summary;
            done.countDown();
        }
    }

    private static Recorder runSession(Path project, Path work) throws Exception {
        GameSession.Request request = new GameSession.Request();
        request.projectRoot = project;
        request.java8 = work.resolve("java8");
        request.jdk21 = Paths.get(System.getProperty("java.home"));
        request.memoryMb = 2048;
        request.logFile = work.resolve("game.log");
        request.readyFile = work.resolve("ready");
        request.sessionFile = work.resolve("session.properties");
        Recorder recorder = new Recorder();
        GameSession.start(request, recorder);
        if (!recorder.done.await(30, TimeUnit.SECONDS)) throw new AssertionError("session did not finish");
        Check.isTrue(!Files.exists(request.sessionFile), "session marker removed");
        return recorder;
    }

    /** Installs the test's GradleWrapperMain as the project's gradle/wrapper/gradle-wrapper.jar. */
    private static void fakeWrapper(Path project) throws IOException {
        String entry = org.gradle.wrapper.GradleWrapperMain.class.getName().replace('.', '/') + ".class";
        Path wrapper = Files.createDirectories(project.resolve("gradle").resolve("wrapper")).resolve("gradle-wrapper.jar");
        JarOutputStream jar = new JarOutputStream(Files.newOutputStream(wrapper));
        try {
            jar.putNextEntry(new JarEntry(entry));
            InputStream input = GameTests.class.getResourceAsStream("/" + entry);
            try {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) jar.write(buffer, 0, count);
            } finally {
                input.close();
            }
        } finally {
            jar.close();
        }
    }

    private static File jar(Path file, String entry, String content) throws IOException {
        ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(file));
        output.putNextEntry(new ZipEntry(entry));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.close();
        return file.toFile();
    }

    /** Encrypts like Vibe's AccountStore and returns the key. */
    private static byte[] writeVault(Path directory, String json) throws Exception {
        byte[] magic = "VIBEAC01".getBytes(StandardCharsets.US_ASCII), key = new byte[16], iv = new byte[12];
        new SecureRandom().nextBytes(key);
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        cipher.updateAAD(magic);
        byte[] encrypted = cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));
        Files.write(directory.resolve("accounts.vault"), ByteBuffer.allocate(magic.length + iv.length + encrypted.length).put(magic).put(iv).put(encrypted).array());
        Files.write(directory.resolve("accounts.key"), key);
        return key;
    }
}
