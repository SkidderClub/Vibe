package dev.vibe.cosmetic;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.junit.Test;
import static org.junit.Assert.*;

public class CosmeticaAssetsTest {
    @Test public void entireOfflineCatalogHasItsAuthoredModelsTexturesAndPngPreviews() throws Exception {
        JsonObject catalog;
        try (InputStream in = resource("catalog.json")) { catalog = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject(); }
        Set<String> ids = new HashSet<String>();
        int models = 0, capes = 0;
        for (JsonElement row : catalog.getAsJsonArray("accessories")) {
            String id = row.getAsJsonObject().get("id").getAsString();
            CosmeticaAccessory accessory = CosmeticaAccessory.fromApi(row.getAsJsonObject());
            assertNotNull(id, accessory);
            assertTrue(id, ids.add(id.toLowerCase(java.util.Locale.ROOT)));
            if (accessory.isCape()) {
                capes++;
            } else {
                models++;
                try (InputStream in = resource("models/" + id + ".json")) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = in.read(buffer)) != -1) bytes.write(buffer, 0, count);
                    float[] vertices = CosmeticaModel.parse(new String(bytes.toByteArray(), StandardCharsets.UTF_8)).bakedVertices();
                    assertTrue(id, vertices.length > 0 && vertices.length % 20 == 0);
                    for (float coordinate : vertices) assertTrue(id, Float.isFinite(coordinate));
                }
            }
            String optimized = "render-textures/" + id + ".png";
            String texture = getClass().getResource("/assets/vibe/cosmetica/" + optimized) == null
                    ? "textures/" + id + ".png" : optimized;
            try (InputStream in = resource(texture); ImageInputStream image = ImageIO.createImageInputStream(in)) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(image);
                assertTrue(id, readers.hasNext());
                ImageReader reader = readers.next();
                try {
                    reader.setInput(image);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    assertTrue(id, width <= 4096 && height <= 4096);
                    assertEquals(id, 0, height % accessory.getFrames());
                    if (accessory.isCape()) assertTrue(id, Math.abs(width - 2 * height / accessory.getFrames()) <= 2);
                } finally { reader.dispose(); }
            }
            try (InputStream in = resource("thumbnails/" + id + ".png")) { assertNotNull(id, ImageIO.read(in)); }
        }
        assertTrue("Bundled model snapshot", models >= 1318);
        assertTrue("Bundled cape snapshot", capes >= 419);
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
