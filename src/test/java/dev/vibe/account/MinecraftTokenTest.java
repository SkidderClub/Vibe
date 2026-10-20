package dev.vibe.account;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.junit.Test;
import static org.junit.Assert.*;

public class MinecraftTokenTest {
    /** A synthetic token shaped like Minecraft's: {"kid":...,"alg":"RS256"} header, given claims, fake signature. */
    static String jwt(String claims) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString("{\"kid\":\"ac84a6\",\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(claims.getBytes(StandardCharsets.UTF_8)) + ".c3ludGhldGljLXNpZ25hdHVyZQ";
    }

    @Test public void acceptsCommonPastedFormsAndReadsTheExpiry() {
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String token = jwt("{\"exp\":" + exp + ",\"profiles\":{\"mc\":\"synthetic\"}}");
        assertTrue(token.startsWith("eyJra"));
        for (String input : Arrays.asList(token, "  " + token + "\r\n", "Bearer " + token, "bearer\t" + token,
                "\"" + token + "\"", token.substring(0, 30) + "\n" + token.substring(30))) {
            MinecraftToken parsed = MinecraftToken.parse(input);
            assertNotNull(input, parsed);
            assertEquals(token, parsed.value());
            assertEquals(exp * 1000L, parsed.expiresAt());
            assertFalse(parsed.isExpired(System.currentTimeMillis()));
        }
    }

    @Test public void rejectsTextThatIsNotAnAccessToken() {
        StringBuilder oversized = new StringBuilder("eyJ");
        while (oversized.length() <= MinecraftToken.MAX_LENGTH) oversized.append('a');
        oversized.append(".b.c");
        for (String input : Arrays.asList(null, "", "   ", "synthetic-refresh-token", "M.C555_synthetic.refresh",
                "abc.def.ghi", "eyJ.def.ghi", "eyJraWQi.only-two", "eyJraWQi.a.b.c", "eyJraWQi.a+b.c", oversized.toString())) {
            assertNull(input, MinecraftToken.parse(input));
        }
    }

    @Test public void expiredAndUnreadableClaimsAreHandledLocally() {
        MinecraftToken expired = MinecraftToken.parse(jwt("{\"exp\":1000}"));
        assertEquals(1000000L, expired.expiresAt());
        assertTrue(expired.isExpired(System.currentTimeMillis()));
        String header = jwt("{}").substring(0, jwt("{}").indexOf('.'));
        for (String token : Arrays.asList(jwt("{}"), jwt("[1]"), jwt("{\"exp\":\"soon\"}"), jwt("not json"), header + ".a.c2ln")) {
            MinecraftToken unknown = MinecraftToken.parse(token);
            assertNotNull(token, unknown);
            assertEquals(0, unknown.expiresAt());
            assertFalse(unknown.isExpired(System.currentTimeMillis()));
        }
    }

    @Test public void previewShowsNeitherClaimsNorSignature() {
        String token = jwt("{\"exp\":4102444800}");
        String preview = MinecraftToken.parse(token).preview();
        assertTrue(preview.startsWith("eyJraWQi"));
        assertTrue(preview.contains(String.valueOf(token.length())));
        assertFalse(preview.contains(token.substring(token.indexOf('.'), token.indexOf('.') + 8)));
        assertFalse(preview.contains("c3ludGhldGlj"));
    }
}
