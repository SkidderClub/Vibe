package dev.vibe.language;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.*;

public class LanguageRegistryTest {
    @Test
    public void requestedLanguagesAndLegacyNamesResolveToSupportedEntries() {
        List<String> expected = Arrays.asList("Finnish", "Swedish", "Greek", "Spanish", "German", "French",
                "Enchantment Table", "Portuguese", "Ukrainian", "Hindi", "Standard Arabic", "Bengali",
                "Indonesian", "Urdu", "Nigerian Pidgin", "Egyptian Arabic", "Marathi", "Vietnamese", "Telugu",
                "Swahili", "Hausa", "Turkish", "Western Punjabi", "Tagalog", "Tamil", "Iranian Persian", "Korean",
                "Amharic", "Thai", "Javanese", "Italian", "Gujarati", "Dutch", "Nepali", "Czech", "Polish",
                "Zulu", "Romanian", "Aurebesh");
        assertTrue(LanguageManager.languages().containsAll(expected));
        assertEquals("Finnish", LanguageManager.normalizeLanguage("Finish"));
        assertEquals("Swedish", LanguageManager.normalizeLanguage("Sweden"));
        assertEquals("Tagalog", LanguageManager.normalizeLanguage("Tagalog (Filipino)"));
        assertEquals("Enchantment Table", LanguageManager.normalizeLanguage("Minecraft Entchantment Table"));
        assertEquals("Aurebesh", LanguageManager.normalizeLanguage("Aurebesh from StarWars"));
        assertFalse(LanguageManager.isSupported("not a Vibe language"));
    }

    @Test
    public void extendedCatalogIncludesBaseUiAndModernModuleLabels() {
        assertEquals("Idioma", LanguageManager.translate("Language", "Spanish"));
        assertEquals("Estadísticas", LanguageManager.translate("Statistics", "Spanish"));
        assertEquals("Statistiken", LanguageManager.translate("Statistics", "German"));
        assertEquals("اللغة", LanguageManager.translate("Language", "Standard Arabic"));
        assertEquals("Custom Cosmetics", LanguageManager.translate("Custom Cosmetics", "Hindi"));
        assertEquals("Aurebesh", LanguageManager.displayName("Aurebesh"));
    }
}
