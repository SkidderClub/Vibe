package dev.vibe.language;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.*;

/** Prevents a future UI key from silently falling back to English in a supported locale. */
public class CompleteLanguageCatalogTest {

    private static final String PREFIX = "vibe.";

    @Test
    public void everyLanguageFileHasEveryKeyInTheEnglishKeyList() throws Exception {
        Map<String, String> sources = englishSourceKeys();
        List<String> languages = LanguageManager.languages();
        assertTrue("The key list should cover every built-in visible client key", sources.size() >= 3000);
        for (String language : languages) {
            String file = LanguageManager.catalogFile(language);
            assertNotNull("Missing catalog file for " + language, file);
            try (BufferedReader reader = open("/assets/vibe/lang/" + file)) {
                String line;
                int rows = 0;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty() || line.charAt(0) == '#') continue;
                    int equals = line.indexOf('=');
                    assertTrue(file + ": malformed line " + line,
                            line.startsWith(PREFIX) && equals > PREFIX.length());
                    String id = line.substring(PREFIX.length(), equals);
                    String value = line.substring(equals + 1);
                    String source = sources.get(id);
                    assertNotNull(file + " has unknown id " + id, source);
                    if (!"English".equals(language)) {
                        assertFalse(file + " is missing a value for " + id, value.trim().isEmpty());
                        assertTrue("Runtime catalog lost " + language + " translation for " + source,
                                LanguageManager.hasTranslation(source, language));
                    }
                    rows++;
                }
                assertEquals("Every language file must cover the English key list", sources.size(), rows);
            }
        }
    }

    @Test
    public void generatedVisibleSourcesAreTranslatedForEveryLanguage() throws Exception {
        Map<String, String> sources = englishSourceKeys();
        List<String> visible = Arrays.asList("Reconnect", "Random username", "OPERATIONS", "Your army",
                "Global", "Encrypted local activity",
                "Each account and every generated cracked profile remain separate in the encrypted local vault.");
        for (String key : visible) assertTrue("The English key list lost " + key, sources.containsValue(key));
        for (String language : LanguageManager.languages()) {
            if ("English".equals(language)) continue;
            for (String source : sources.values()) {
                assertTrue("Missing visible source translation for " + source + " in " + language,
                        LanguageManager.hasTranslation(source, language));
            }
        }
    }

    /** en.lang doubles as the key list: one vibe.<id>=<English source> row per key. */
    private static Map<String, String> englishSourceKeys() throws Exception {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        try (BufferedReader reader = open("/assets/vibe/lang/en.lang")) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                int equals = line.indexOf('=');
                assertTrue("Malformed en.lang line " + line,
                        line.startsWith(PREFIX) && equals > PREFIX.length());
                String id = line.substring(PREFIX.length(), equals);
                String source = line.substring(equals + 1);
                assertFalse("Duplicate key id " + id, sources.containsKey(id));
                assertTrue("Invalid key id " + id, id.matches("[a-z0-9_]+"));
                assertFalse("en.lang is missing the English text for " + id, source.isEmpty());
                sources.put(id, source);
            }
        }
        return sources;
    }

    private static BufferedReader open(String resource) {
        return new BufferedReader(new InputStreamReader(
                CompleteLanguageCatalogTest.class.getResourceAsStream(resource), StandardCharsets.UTF_8));
    }
}
