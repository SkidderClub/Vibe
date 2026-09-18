package dev.vibe.account;

import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

public class RandomUsernameTest {
    @Test public void generatedOfflineNamesAreReadableAndAlwaysMinecraftLegal() {
        assertTrue("The configured pool must exceed twenty million names", RandomUsername.possibilities() >= 20_000_000L);
        Random deterministic = new Random(486291L);
        for (int index = 0; index < 10000; index++) {
            String name = RandomUsername.generate(deterministic);
            assertTrue(name, name.matches("[A-Za-z0-9_]{3,16}"));
            assertTrue("A word-pair name should not be a random character string: " + name, name.matches("[A-Z][a-z]+[A-Z][a-z]+[0-9]{3}"));
        }
    }
}
