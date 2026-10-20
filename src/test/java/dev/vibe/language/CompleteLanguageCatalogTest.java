package dev.vibe.language;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.*;

/** Prevents a future UI key from silently falling back to English in a supported locale. */
public class CompleteLanguageCatalogTest {
    @Test
    public void everyGeneratedBuiltInKeyHasAValueInEverySelectableLanguage() throws Exception {
        List<String> languages = LanguageManager.languages();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/assets/vibe/lang/complete.tsv"), StandardCharsets.UTF_8))) {
            String[] header = reader.readLine().split("\\t", -1);
            assertEquals("key", header[0]);
            assertEquals(languages.size(), header.length);
            for (int index = 1; index < header.length; index++) assertEquals(languages.get(index), header[index]);
            String line;
            int keys = 0;
            while ((line = reader.readLine()) != null) {
                String[] row = line.split("\\t", -1);
                assertEquals("Malformed translation row for " + Arrays.toString(row), header.length, row.length);
                for (int index = 1; index < row.length; index++) {
                    assertFalse("Missing " + header[index] + " translation for " + row[0], row[index].trim().isEmpty());
                    assertTrue("Runtime catalog lost " + header[index] + " translation for " + row[0],
                            LanguageManager.hasTranslation(row[0], header[index]));
                }
                keys++;
            }
            assertTrue("The complete catalog should cover every built-in visible client key", keys >= 3000);
            for (String key : Arrays.asList("Reconnect", "Random username", "Operations", "Your army", "Global",
                    "Encrypted local activity", "Each account and every generated cracked profile remain separate in the encrypted local vault.")) {
                for (int index = 1; index < header.length; index++) {
                    assertTrue("Missing visible UI translation for " + key + " in " + header[index],
                            LanguageManager.hasTranslation(key, header[index]));
                }
            }
        }
    }

    @Test
    public void generatedVisibleSourceManifestIsFullyCoveredForEveryLanguage() throws Exception {
        List<String> languages = LanguageManager.languages();
        int keys = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/assets/vibe/lang/visible-keys.txt"), StandardCharsets.UTF_8))) {
            String key;
            while ((key = reader.readLine()) != null) {
                if (key.trim().isEmpty()) continue;
                for (int index = 1; index < languages.size(); index++) {
                    assertTrue("Missing visible source translation for " + key + " in " + languages.get(index),
                            LanguageManager.hasTranslation(key, languages.get(index)));
                }
                keys++;
            }
        }
        assertTrue("The source manifest should cover the complete visible client catalog", keys >= 3000);
    }
}
