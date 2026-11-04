package dev.vibe.cosmetic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import static org.junit.Assert.*;

public class CosmeticaModelTest {
    @Test public void uvUsesMinecraftSpaceEvenWithAnHdTextureAndRotatedFace() {
        float[] quad = bake("", "{\"uv\":[12,4,4,12],\"rotation\":90}");
        assertArrayEquals(new float[]{0.75F, 0.75F}, new float[]{quad[3], quad[4]}, 0.0001F);
        assertArrayEquals(new float[]{0.25F, 0.75F}, new float[]{quad[8], quad[9]}, 0.0001F);
    }

    @Test public void modernEulerRotationsAndLegacyAxisAnglesUseTheSameModelBasis() {
        float[] axis = bake(",\"rotation\":{\"origin\":[8,8,8],\"axis\":\"Y\",\"angle\":90}", "{}");
        float[] euler = bake(",\"rotation\":{\"origin\":[8,8,8],\"x\":0,\"y\":90,\"z\":0}", "{}");
        assertArrayEquals(axis, euler, 0.0001F);
        assertArrayEquals(new float[]{-0.5F, 0.75F, -0.5F}, new float[]{euler[0], euler[1], euler[2]}, 0.0001F);
        float[] multiple = bake(",\"rotation\":{\"origin\":[8,8,8],\"x\":90,\"y\":90,\"z\":90}", "{}");
        assertArrayEquals(new float[]{-0.5F, 0.75F, -0.5F}, new float[]{multiple[0], multiple[1], multiple[2]}, 0.0001F);
    }

    @Test public void cloakCapesRoundTripAndElytraOnlyEntriesAreExcluded() {
        JsonObject row = new JsonParser().parse("{\"animatedTextureCosmetic\":{\"type\":\"cape\","
                + "\"id\":\"cape1\",\"flags\":3,\"texture\":\"cape.png\",\"frames\":5}}").getAsJsonObject();
        CosmeticaAccessory first = CosmeticaAccessory.fromApi(row);
        assertNotNull(first);
        assertTrue(first.isCape());
        assertEquals(5, CosmeticaAccessory.fromJson(first.toJson()).getFrames());
        JsonObject data = row.getAsJsonObject("animatedTextureCosmetic");
        data.addProperty("id", "cape2");
        CosmeticPreset preset = new CosmeticPreset("outfit", "Test");
        preset.equip(first);
        preset.equip(CosmeticaAccessory.fromApi(row));
        assertEquals(1, preset.getAccessories().size());
        assertEquals("cape2", preset.getCape().getId());
        data.addProperty("flags", 2);
        assertNull(CosmeticaAccessory.fromApi(row));
    }

    private static float[] bake(String rotation, String face) {
        return CosmeticaModel.parse("{\"texture_size\":[512,512],\"elements\":[{"
                + "\"from\":[0,0,0],\"to\":[16,16,16]" + rotation
                + ",\"faces\":{\"north\":" + face + "}}]}").bakedVertices();
    }
}
