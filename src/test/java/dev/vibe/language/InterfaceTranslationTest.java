package dev.vibe.language;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class InterfaceTranslationTest {
    @Test public void everyCatalogEntryIsAvailableInAllLanguages() throws Exception {
        Set<String> keys = new HashSet<String>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/assets/vibe/lang/interface.tsv"),StandardCharsets.UTF_8))) {
            String line;
            while((line=reader.readLine())!=null) {
                if(line.startsWith("#")||line.isEmpty())continue;
                String[] parts=line.split("\t",-1); assertEquals(line,5,parts.length); assertTrue("Duplicate: "+parts[0],keys.add(parts[0].toLowerCase(java.util.Locale.ROOT)));
                String[] languages={"Chinese","Russian","Japanese","Bavarian"};
                for(int i=0;i<4;i++) {
                    assertFalse(parts[i+1].isEmpty());
                    assertEquals(parts[i+1],LanguageManager.translate(parts[0],languages[i]));
                    assertEquals(parts[i+1],LanguageManager.translate(parts[0].toUpperCase(java.util.Locale.ROOT),languages[i]));
                }
                assertEquals(parts[0],LanguageManager.translate(parts[0],"English"));
            }
        }
        assertTrue(keys.size()>=200);
        assertEquals("My custom preset",LanguageManager.translate("My custom preset","Chinese"));
        assertEquals("Outline custom-profile",LanguageManager.translate("Outline custom-profile","Chinese"));
        assertEquals("轮廓 / 颜色: 速度",LanguageManager.translate("outline / color: speed","Chinese"));
    }
}
