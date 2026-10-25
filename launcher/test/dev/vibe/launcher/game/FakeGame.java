package dev.vibe.launcher.game;

import java.nio.file.Files;
import java.nio.file.Paths;

/** A stand-in for Minecraft that prints like a Forge client and exits on demand. */
public final class FakeGame {
    private FakeGame() { }

    public static void main(String[] args) throws Exception {
        String ready = null;
        int exit = 0;
        boolean crash = false;
        for (int index = 0; index < args.length; index++) {
            if (args[index].equals("--ready") && index + 1 < args.length) ready = args[++index];
            else if (args[index].equals("--exit") && index + 1 < args.length) exit = Integer.parseInt(args[++index]);
            else if (args[index].equals("--crash")) crash = true;
        }
        emit("[12:00:00] [Client thread/INFO]: Setting user: Player\n");
        emit("[12:00:00] [Client thread/INFO]: M\u00fcnchen \u2713\n");
        emit("2% (2/3)\r");
        emit("100% (3/3)\r\n");
        if (crash) emit("[12:00:01] [Client thread/ERROR]: java.lang.UnsatisfiedLinkError: no lwjgl in java.library.path\n");
        System.out.flush();
        if (ready != null) Files.write(Paths.get(ready), new byte[0]);
        Thread.sleep(400);
        System.exit(exit);
    }

    /** Writes UTF-8 bytes, independent of the console encoding of the test JVM. */
    private static void emit(String text) throws Exception {
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.out.write(bytes, 0, bytes.length);
    }
}
