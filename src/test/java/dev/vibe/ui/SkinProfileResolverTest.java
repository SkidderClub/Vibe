package dev.vibe.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class SkinProfileResolverTest {
    private static final String ID = "3c3c046e45664170898c59d85b742503";
    private static final UUID UUID_VALUE = UUID.fromString("3c3c046e-4566-4170-898c-59d85b742503");
    private static final String HASH = "199dfc2e1e8d5bdfddadae9f5a3e524eea5751252e62807c91a6b5bc35e0e1cd";

    @Test public void onlineProfileUsesItsUuidWithoutLauncherPropertiesOrNameLookup() throws Exception {
        List<String> requests = new ArrayList<>();
        SkinProfileResolver resolver = new SkinProfileResolver(url -> {
            requests.add(url);
            return profile("http://textures.minecraft.net/texture/" + HASH);
        });
        assertEquals("https://textures.minecraft.net/texture/" + HASH, resolver.resolve(UUID_VALUE, "OldLauncherName"));
        assertEquals(1, requests.size());
        assertEquals("https://sessionserver.mojang.com/session/minecraft/profile/" + ID, requests.get(0));
    }

    @Test public void offlineUuidResolvesTheNamedPublicProfileFirst() throws Exception {
        List<String> requests = new ArrayList<>();
        SkinProfileResolver resolver = new SkinProfileResolver(url -> {
            requests.add(url);
            return requests.size() == 1 ? bytes("{\"id\":\"" + ID + "\",\"name\":\"Cloutness\"}")
                    : profile("https://textures.minecraft.net/texture/" + HASH);
        });
        UUID offline = UUID.nameUUIDFromBytes(bytes("OfflinePlayer:Cloutness"));
        assertEquals("https://textures.minecraft.net/texture/" + HASH, resolver.resolve(offline, "Cloutness"));
        assertEquals("https://api.mojang.com/users/profiles/minecraft/Cloutness", requests.get(0));
        assertTrue(requests.get(1).endsWith(ID));
    }

    @Test public void missingPublicProfileOrSkinKeepsTheDefaultFace() throws Exception {
        assertNull(new SkinProfileResolver(url -> null).resolve(null, "LocalOnly"));
        assertNull(new SkinProfileResolver(url -> bytes("{\"id\":\"" + ID + "\",\"properties\":[]}"))
                .resolve(UUID_VALUE, "Cloutness"));
    }

    @Test public void transientFailureIsReportedForRetryInsteadOfCachedAsNoSkin() {
        assertThrows(IOException.class, () -> new SkinProfileResolver(url -> { throw new IOException("temporary"); })
                .resolve(UUID_VALUE, "Cloutness"));
    }

    @Test public void malformedPropertiesAndUnrelatedTextureHostsAreRejected() {
        assertThrows(IOException.class, () -> new SkinProfileResolver(url -> bytes("not json")).resolve(UUID_VALUE, "Cloutness"));
        assertThrows(IOException.class, () -> new SkinProfileResolver(url -> profile("https://example.invalid/texture/" + HASH))
                .resolve(UUID_VALUE, "Cloutness"));
    }

    @Test public void mismatchedPublicProfileCannotDisplayAnotherPlayersSkin() {
        assertThrows(IOException.class, () -> new SkinProfileResolver(url -> bytes("{\"id\":\"00000000000000000000000000000000\"}"))
                .resolve(UUID_VALUE, "Cloutness"));
    }

    private static byte[] profile(String url) {
        String payload = Base64.getEncoder().encodeToString(bytes("{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}"));
        return bytes("{\"id\":\"" + ID + "\",\"properties\":[{\"name\":\"textures\",\"value\":\"" + payload + "\"}]}");
    }
    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
}
