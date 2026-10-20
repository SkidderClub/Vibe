package dev.vibe.cosmetic;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;

public class CosmeticaAssetsTest {
    @Test public void entireOfflineCatalogHasItsAuthoredModelsTexturesAndPngPreviews() throws Exception {
        JsonObject catalog;
        try (InputStream in = resource("catalog.json")) { catalog = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject(); }
        assertEquals(629, catalog.getAsJsonArray("accessories").size()); Set<String> ids = new HashSet<String>();
        for (JsonElement row : catalog.getAsJsonArray("accessories")) {
            CosmeticaAccessory accessory = CosmeticaAccessory.fromApi(row.getAsJsonObject()); assertNotNull(accessory);
            assertTrue(ids.add(accessory.getId()));
            try (InputStream in = resource("models/" + accessory.getId() + ".json")) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
                while ((count = in.read(buffer)) != -1) bytes.write(buffer, 0, count);
                assertNotNull(CosmeticaModel.parse(new String(bytes.toByteArray(), StandardCharsets.UTF_8)));
            }
            String url = accessory.getTextureUrl(), extension = url.toLowerCase(java.util.Locale.ROOT).endsWith(".webp") ? ".webp" : ".png";
            try (InputStream in = resource("textures/" + accessory.getId() + extension)) { assertTrue(in.read() >= 0); }
            try (InputStream in = resource("thumbnails/" + accessory.getId() + ".png")) { assertNotNull(ImageIO.read(in)); }
        }
    }
    @Test public void originalSkinAndUiAssetsStayWithTheExtension() throws Exception {
        try (InputStream in = resource("skins/xheist_.png")) { assertNotNull(ImageIO.read(in)); }
        try (InputStream in = resource("upstream-ui/checkbox_check.png")) { assertNotNull(ImageIO.read(in)); }
        assertNull(getClass().getResource("/dev/vibe/module/impl/movement/TestModule.class"));
    }
    private InputStream resource(String path) {
        InputStream in = getClass().getResourceAsStream("/assets/vibe/cosmetica/" + path);
        assertNotNull("Missing Cosmetica resource: " + path, in); return in;
    }
}
