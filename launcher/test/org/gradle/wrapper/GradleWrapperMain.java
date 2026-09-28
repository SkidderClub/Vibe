package org.gradle.wrapper;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Stand-in for Gradle's wrapper in the launcher tests. It prints what Vibe's
 * {@code runClient} prints, signals readiness like Vibe does, or fails like a
 * broken build when {@code fail.marker} exists in the project directory.
 */
public final class GradleWrapperMain {
    private GradleWrapperMain() { }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8");
        out.println("> Configure project :");
        out.println("> Task :compileJava");
        if (new File("fail.marker").exists()) {
            out.println("FAILURE: Build failed with an exception.");
            out.println();
            out.println("* What went wrong:");
            out.println("Execution failed for task ':compileJava'.");
            out.println("> Compilation failed; see the compiler error output for details.");
            out.println();
            out.println("* Try:");
            out.println("> Run with --stacktrace option to get the stack trace.");
            System.exit(1);
        }
        out.println("> Task :preRunClient");
        out.print("1% (1/3)\r2% (2/3)\r100% (3/3)\r\n");
        out.flush();
        out.println("> Task :runClient");
        for (String arg : args) out.println(arg);
        out.println("JAVA_HOME=" + System.getenv("JAVA_HOME"));
        out.println("PATH=" + System.getenv("PATH"));
        Files.write(Paths.get(System.getenv("VIBE_LAUNCH_READY_FILE")), "ok".getBytes(StandardCharsets.US_ASCII));
        Thread.sleep(700);
        out.println("[Client thread/INFO]: München ✓");
        System.exit(0);
    }
}
