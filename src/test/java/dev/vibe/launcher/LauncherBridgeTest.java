package dev.vibe.launcher;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class LauncherBridgeTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void oneShotRoutesAreConsumedWithoutChangingTheSelectedAccount() throws Exception {
        Path minecraft = temporary.newFolder("minecraft").toPath();
        write(minecraft, "accounts", "123e4567-e89b-12d3-a456-426655440000", "VibeUser");

        assertTrue(LauncherBridge.consumeAccountsRequest(minecraft.toFile()));
        assertFalse(LauncherBridge.consumeAccountsRequest(minecraft.toFile()));
        Properties afterAccounts = LauncherBridge.read(minecraft.toFile());
        assertEquals("vibe", afterAccounts.getProperty("mode"));
        assertEquals("123e4567-e89b-12d3-a456-426655440000", afterAccounts.getProperty("selectedUuid"));
        assertEquals("VibeUser", afterAccounts.getProperty("selectedName"));

        write(minecraft, "gta7", "123e4567-e89b-12d3-a456-426655440000", "VibeUser");
        assertTrue(LauncherBridge.gta7Requested(minecraft.toFile()));
        assertTrue(LauncherBridge.consumeGta7Request(minecraft.toFile()));
        assertFalse(LauncherBridge.gta7Requested(minecraft.toFile()));
    }

    private static void write(Path minecraft, String mode, String uuid, String name) throws Exception {
        Path file = minecraft.resolve("vibe").resolve("launcher.properties");
        Files.createDirectories(file.getParent());
        Properties values = new Properties();
        values.setProperty("mode", mode);
        values.setProperty("selectedUuid", uuid);
        values.setProperty("selectedName", name);
        try (OutputStream output = Files.newOutputStream(file)) {
            values.store(output, "test");
        }
    }
}
