package dev.vibe.language;

import java.awt.Font;
import java.io.InputStream;
import org.junit.Test;

import static org.junit.Assert.*;

/** Guards the dedicated, usable script fonts behind the two novelty languages. */
public class CustomScriptLanguageTest {
    @Test
    public void bundledScriptFontsCanRenderTheAsciiSourceAlphabet() throws Exception {
        assertScriptFont("/assets/vibe/fonts/StandardGalactic-Regular.ttf");
        assertScriptFont("/assets/vibe/fonts/Aurebesh-Rodian.otf");
    }

    @Test
    public void catalogValuesResolveBackToTextForTheDedicatedScriptFonts() {
        for (String language : new String[] {"Enchantment Table", "Aurebesh"}) {
            String encoded = LanguageManager.translate("Custom Cosmetics", language);
            assertNotEquals("Custom Cosmetics", encoded);
            assertEquals("Custom Cosmetics", LanguageManager.scriptFontText(encoded, language));
            assertEquals("HUD Editor", LanguageManager.scriptFontText("HUD Editor", language));
        }
    }

    private static void assertScriptFont(String resource) throws Exception {
        try (InputStream input = CustomScriptLanguageTest.class.getResourceAsStream(resource)) {
            assertNotNull("Missing bundled script font " + resource, input);
            Font font = Font.createFont(Font.TRUETYPE_FONT, input).deriveFont(20F);
            for (char character : "Vibe HUD 0123456789".toCharArray()) {
                assertTrue("Script font cannot render " + character + " from " + resource, font.canDisplay(character));
            }
        }
    }
}
