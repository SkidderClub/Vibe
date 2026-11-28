package dev.vibe.language;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.*;

/** Locks the terminology pass: no padded values, unified brands, and no literal module mistranslations. */
public class LanguageTranslationQualityTest {
    private static final Set<String> SCRIPT_LANGUAGES =
            new HashSet<String>(Arrays.asList("Enchantment Table", "Aurebesh"));

    @Test
    public void translationsHaveNoLeadingOrTrailingWhitespace() throws Exception {
        int total = 0;
        for (String language : LanguageManager.languages()) {
            if ("English".equals(language)) continue;
            String file = LanguageManager.catalogFile(language);
            assertNotNull(language, file);
            InputStream stream = getClass().getResourceAsStream("/assets/vibe/lang/" + file);
            assertNotNull(file, stream);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int equals = line.indexOf('=');
                    assertTrue(language + " malformed row: " + line, equals > 0);
                    String value = line.substring(equals + 1);
                    assertFalse(language + " has an empty value: " + line, value.isEmpty());
                    assertEquals(language + " padded value: " + line, value.trim(), value);
                    total++;
                }
            }
        }
        assertTrue("Expected the complete catalog", total > 3000);
    }

    @Test
    public void literalModuleMistranslationsAreGone() {
        assertNotEquals("Gliederung", LanguageManager.translate("Outline", "German"));
        assertNotEquals("Aperçu", LanguageManager.translate("Outline", "French"));
        assertNotEquals("Pause Rapide", LanguageManager.translate("FastBreak", "French"));
        assertNotEquals("BlocSuperposition", LanguageManager.translate("BlockOverlay", "French"));
        assertEquals("轮廓", LanguageManager.translate("Outline", "Chinese"));
    }

    @Test
    public void brandNamesStayUntranslated() {
        for (String language : LanguageManager.languages()) {
            if ("English".equals(language) || SCRIPT_LANGUAGES.contains(language)) continue;
            for (String brand : Arrays.asList("Vibe", "Vibe Discord", "Discord", "Skeet", "GTA7",
                    "OptiFine", "Minecraft", "Forge")) {
                assertEquals(language + " translated " + brand, brand, LanguageManager.translate(brand, language));
            }
        }
    }

    @Test
    public void previouslyMissingUiKeysAreTranslated() {
        for (String language : LanguageManager.languages()) {
            if ("English".equals(language)) continue;
            for (String key : Arrays.asList("Extended History", "Session Info", "Strict",
                    "Attacks Per Second", "Return To Origin Speed")) {
                assertNotEquals(language + " kept " + key + " in English", key,
                        LanguageManager.translate(key, language));
            }
        }
        assertEquals("扩展聊天记录", LanguageManager.translate("Extended History", "Chinese"));
        assertNotEquals("Session Info", LanguageManager.translate("Session Info", "French"));
        assertNotEquals("Session Info", LanguageManager.translate("Session Info", "German"));
    }
}
