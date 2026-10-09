package dev.vibe.launcher.core;

import dev.vibe.launcher.Check;
import dev.vibe.launcher.game.FailureAnalyzer;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Error codes: their catalogue, how failures map onto them and the Gradle/crash diagnosis. */
public final class ErrorTests {
    private ErrorTests() { }

    public static void run(Check check) {
        check.test("error codes are unique, translated and documented", () -> {
            Set<Integer> numbers = new HashSet<Integer>();
            for (ErrorCode code : ErrorCode.values()) {
                Check.isTrue(numbers.add(code.number), "duplicate number " + code.number);
                Check.isTrue(code.id().matches("VL-\\d{3}"), "id " + code.id());
                Check.isTrue(code.helpUrl().endsWith("LAUNCHER_ERRORS.md#vl-" + code.number), "help anchor " + code.helpUrl());
                Check.equal(code, ErrorCode.byNumber(code.number));
            }
            try {
                for (ErrorCode code : ErrorCode.values()) {
                    I18n.init("en", "");
                    String english = code.hint();
                    I18n.init("de", "");
                    Check.isTrue(!code.title().equals(code.englishTitle()), "German title for " + code.id());
                    Check.isTrue(!code.hint().equals(english), "German hint for " + code.id());
                }
            } finally {
                I18n.init("en", "");
            }
            Path guide = guide();
            if (guide == null) {
                System.out.println("        (docs/LAUNCHER_ERRORS.md not found from " + System.getProperty("user.dir") + "; documentation check skipped)");
                return;
            }
            String text = new String(Files.readAllBytes(guide), StandardCharsets.UTF_8);
            for (ErrorCode code : ErrorCode.values()) {
                Check.isTrue(text.contains("\n### " + code.id() + "\n"), code.id() + " has no section in " + guide);
            }
            Matcher headings = Pattern.compile("\n### VL-(\\d{3})\n").matcher(text);
            while (headings.find()) {
                Check.isTrue(ErrorCode.byNumber(Integer.parseInt(headings.group(1))) != null, "the guide documents unknown code VL-" + headings.group(1));
            }
        });

        check.test("failures map onto error codes", () -> {
            Check.equal(ErrorCode.NO_INTERNET, ErrorCode.of(new UnknownHostException("api.github.com")));
            Check.equal(ErrorCode.NO_INTERNET, ErrorCode.of(new ConnectException("Network is unreachable (connect failed)")));
            Check.equal(ErrorCode.CONNECTION_BLOCKED, ErrorCode.of(new ConnectException("Connection refused")));
            Check.equal(ErrorCode.TIMEOUT, ErrorCode.of(new SocketTimeoutException("Read timed out")));
            Check.equal(ErrorCode.SECURE_CONNECTION, ErrorCode.of(new javax.net.ssl.SSLHandshakeException("PKIX path building failed")));
            Check.equal(ErrorCode.DOWNLOAD_INTERRUPTED, ErrorCode.of(new javax.net.ssl.SSLException("Connection reset")));
            Check.equal(ErrorCode.NO_INTERNET, ErrorCode.of(new java.net.SocketException("Network is unreachable")));
            Check.equal(ErrorCode.GITHUB_RATE_LIMIT, ErrorCode.of(new Http.StatusException(403, "api.github.com", true, "limit")));
            Check.equal(ErrorCode.NOT_FOUND, ErrorCode.of(new Http.StatusException(404, "api.adoptium.net", false, "404")));
            Check.equal(ErrorCode.SERVER_ERROR, ErrorCode.of(new Http.StatusException(502, "api.github.com", false, "502")));
            Check.equal(ErrorCode.CONNECTION_BLOCKED, ErrorCode.of(new Http.StatusException(407, "api.github.com", false, "407")));
            Check.equal(ErrorCode.UNEXPECTED_RESPONSE, ErrorCode.of(new Http.StatusException(302, "api.github.com", false, "302")));
            Check.equal(ErrorCode.UNEXPECTED_RESPONSE, ErrorCode.of(Check.fails(IOException.class, () -> Json.parse("<html>Hotel Wi-Fi</html>"))));
            Check.equal(ErrorCode.DOWNLOAD_INTERRUPTED, ErrorCode.of(new IOException("The download from github.com ended early.")));
            Check.equal(ErrorCode.DISK_FULL, ErrorCode.of(new FileSystemException("C:\\x", null, "There is not enough space on the disk")));
            Check.equal(ErrorCode.DISK_FULL, ErrorCode.of(new IOException("No space left on device")));
            Check.equal(ErrorCode.FILE_IN_USE, ErrorCode.of(new FileSystemException("C:\\x", null,
                    "The process cannot access the file because it is being used by another process")));
            Check.equal(ErrorCode.ACCESS_DENIED, ErrorCode.of(new AccessDeniedException("/x")));
            Check.equal(ErrorCode.FILE_ERROR, ErrorCode.of(new FileSystemException("/x")));
            // Causes count, an explicit code wins, and unknown failures take the step's code.
            Check.equal(ErrorCode.NO_INTERNET, ErrorCode.of(new IOException("download failed", new UnknownHostException("github.com"))));
            Check.equal(ErrorCode.JAVA_DAMAGED, ErrorCode.of(new LauncherException(ErrorCode.JAVA_DAMAGED, "x", new UnknownHostException("x"))));
            Check.equal(ErrorCode.UNEXPECTED, ErrorCode.of(new IllegalStateException("bug")));
            Check.equal(ErrorCode.LAUNCHER_UPDATE, ErrorCode.of(new IllegalStateException("bug"), ErrorCode.LAUNCHER_UPDATE));
            Check.equal(ErrorCode.SOURCE_INVALID, ErrorCode.of(LauncherException.wrap(new IOException("invalid LOC header"), ErrorCode.SOURCE_INVALID, "Damaged.")));
            Check.equal(ErrorCode.DISK_FULL, ErrorCode.of(LauncherException.wrap(new IOException("No space left on device"), ErrorCode.SOURCE_INVALID, "Damaged.")));
        });

        check.test("timeouts are failures, interrupts are cancellations", () -> {
            Check.isTrue(!LauncherException.cancelled(new SocketTimeoutException("Read timed out")), "a timeout is no cancel");
            Check.isTrue(LauncherException.cancelled(new InterruptedIOException("Cancelled.")), "interrupted transfer");
            Check.isTrue(LauncherException.cancelled(new InterruptedException()), "interrupted wait");
            Check.isTrue(LauncherException.cancelled(new java.nio.channels.ClosedByInterruptException()), "interrupted channel");
            Check.isTrue(!LauncherException.cancelled(new IOException("x")), "plain failure");
            Check.equal("api.github.com could not be found.", Text.describe(new UnknownHostException("api.github.com")));
        });

        check.test("failed builds are diagnosed from Gradle's output", () -> {
            Check.equal(ErrorCode.OPTIFINE_DOWNLOAD, build(null, "Execution failed for task ':prepareOptifine'.\nOptiFine did not provide a download link. Retry later"));
            Check.equal(ErrorCode.NO_INTERNET, build(null, "Could not resolve all files for configuration ':runtimeClasspath'.\nCould not resolve com.google.code.gson:gson:2.2.4."
                    + "\nCould not GET 'https://repo.maven.apache.org/x.pom'.\nrepo.maven.apache.org: Name or service not known"));
            Check.equal(ErrorCode.BUILD_DOWNLOAD, build(null, "Could not resolve all files for configuration ':runtimeClasspath'.\nCould not GET 'https://maven.minecraftforge.net/x'.\nRead timed out"));
            Check.equal(ErrorCode.COMPILE_ERROR, build(new String[] { "Could not resolve an optional thing" },
                    "Execution failed for task ':compileJava'.\nCompilation failed; see the compiler error output for details."));
            Check.equal(ErrorCode.WRONG_JAVA, build(null, "Could not determine the dependencies of task ':compileJava'.\n"
                    + "No matching toolchains found for requested specification: {languageVersion=21, vendor=any, implementation=vendor-specific}."));
            Check.equal(ErrorCode.GRADLE_LOCKED, build(null, "Gradle could not start your build.\nTimeout waiting to lock journal cache (/home/u/.gradle/caches/journal-1). "
                    + "It is currently in use by another Gradle instance."));
            Check.equal(ErrorCode.GRADLE_MEMORY, build(null, "Unable to start the daemon process.\nCould not reserve enough space for 3145728KB object heap"));
            Check.equal(ErrorCode.MINECRAFT_FILES, build(null, "Execution failed for task ':runClient'.\nUnimined did not prepare the Minecraft 1.8.9 client: /x.jar"));
            Check.equal(ErrorCode.GRADLE_DOWNLOAD, build(new String[] { "Downloading https://services.gradle.org/distributions/gradle-8.8-bin.zip",
                    "Exception in thread \"main\" java.net.UnknownHostException: services.gradle.org" }, ""));
            Check.equal(ErrorCode.COMPILE_ERROR, build(new String[] { "/src/main/java/dev/vibe/A.java:12: error: cannot find symbol" }, ""));
            Check.equal(ErrorCode.DISK_FULL, build(new String[] { "java.io.IOException: No space left on device" }, "Execution failed for task ':remapJar'."));
            Check.equal(ErrorCode.BUILD_FAILED, build(null, "Something nobody has seen before."));
            FailureAnalyzer analyzer = new FailureAnalyzer();
            analyzer.accept("> Task :compileJava", false);
            analyzer.accept("  /src/main/java/dev/vibe/A.java:12: error: cannot find symbol  ", false);
            Check.equal("/src/main/java/dev/vibe/A.java:12: error: cannot find symbol", analyzer.buildFailure("").evidence);
        });

        check.test("crashes are diagnosed from the crash report and the last lines", () -> {
            Check.equal(ErrorCode.GRAPHICS, crash(null, "---- Minecraft Crash Report ----\nDescription: Initializing game\n\norg.lwjgl.LWJGLException: Pixel format not accelerated"));
            Check.equal(ErrorCode.GAME_MEMORY_SETTING, crash(new String[] { "Error occurred during initialization of VM", "Could not reserve enough space for 8388608KB object heap" }, ""));
            Check.equal(ErrorCode.GAME_OUT_OF_MEMORY, crash(new String[] { "[Client thread/ERROR]: java.lang.OutOfMemoryError: Java heap space" }, ""));
            Check.equal(ErrorCode.NATIVE_CRASH, crash(new String[] { "# A fatal error has been detected by the Java Runtime Environment:", "#  EXCEPTION_ACCESS_VIOLATION (0xc0000005)" }, ""));
            Check.equal(ErrorCode.MOD_CONFLICT, crash(null, "net.minecraftforge.fml.common.MissingModsException: Mod keystrokes (Keystrokes) requires [patcher]"));
            Check.equal(ErrorCode.GAME_CRASHED, crash(null, "net.minecraftforge.fml.common.LoaderExceptionModCrash: Caught exception from Vibe (vibe)"));
            Check.equal(ErrorCode.ROSETTA, crash(new String[] { "A problem occurred starting process 'command '/x/bin/java''", "error=86, Bad CPU type in executable" }, ""));
            Check.equal(ErrorCode.GAME_CRASHED, crash(null, "Description: Ticking entity\n\njava.lang.NullPointerException"));
            // Build output and old warnings do not decide a later crash.
            FailureAnalyzer analyzer = new FailureAnalyzer();
            analyzer.accept("java.lang.OutOfMemoryError: Metaspace", false);
            analyzer.accept("[Client thread/WARN]: java.lang.UnsatisfiedLinkError: optional native", true);
            for (int index = 0; index < 250; index++) analyzer.accept("[Client thread/INFO]: line " + index, true);
            Check.equal(ErrorCode.GAME_CRASHED, analyzer.gameFailure("").code);
        });
    }

    private static ErrorCode build(String[] lines, String summary) {
        FailureAnalyzer analyzer = new FailureAnalyzer();
        if (lines != null) for (String line : lines) analyzer.accept(line, false);
        return analyzer.buildFailure(summary).code;
    }

    private static ErrorCode crash(String[] lines, String report) {
        FailureAnalyzer analyzer = new FailureAnalyzer();
        analyzer.accept("> Task :compileJava", false);
        if (lines != null) for (String line : lines) analyzer.accept(line, true);
        return analyzer.gameFailure(report).code;
    }

    /** docs/LAUNCHER_ERRORS.md, searched upwards from the working directory and the test classes. */
    private static Path guide() {
        Path[] starts = { Paths.get(System.getProperty("user.dir")), codeLocation() };
        for (Path start : starts) {
            for (Path folder = start; folder != null; folder = folder.getParent()) {
                Path candidate = folder.resolve("docs").resolve("LAUNCHER_ERRORS.md");
                if (Files.isRegularFile(candidate)) return candidate;
            }
        }
        return null;
    }

    private static Path codeLocation() {
        try { return new File(ErrorTests.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toPath().toAbsolutePath(); }
        catch (Exception ignored) { return null; }
    }
}
