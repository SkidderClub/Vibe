package dev.vibe.ui.render;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.UUID;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.ResourceLocation;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlayerSkinRendererTest {
    @Test public void removingAnOverrideRestoresTheOriginalSkinAfterMultipleChanges() throws Exception {
        PlayerSkinRenderer renderer = new PlayerSkinRenderer();
        Info info = new Info(); ResourceLocation original = new ResourceLocation("test", "original");
        set(info, original);
        renderer.apply(info, new ResourceLocation("test", "preset"));
        renderer.apply(info, new ResourceLocation("test", "privacy"));
        renderer.apply(info, null); assertEquals(original, info.getLocationSkin());
    }
    @Test public void aNewPlayerInfoDoesNotInheritASkinFromThePreviousConnection() throws Exception {
        PlayerSkinRenderer renderer = new PlayerSkinRenderer(); Info first = new Info(), next = new Info();
        ResourceLocation firstSkin = new ResourceLocation("test", "first"), nextSkin = new ResourceLocation("test", "next");
        set(first, firstSkin); set(next, nextSkin);
        renderer.apply(first, new ResourceLocation("test", "preset"));
        renderer.apply(next, null); assertEquals(nextSkin, next.getLocationSkin());
    }
    private static void set(NetworkPlayerInfo info, ResourceLocation location) throws Exception {
        Field f = NetworkPlayerInfo.class.getDeclaredField("locationSkin"); f.setAccessible(true); f.set(info, location);
    }
    private static final class Info extends NetworkPlayerInfo {
        Info() { super(new GameProfile(UUID.randomUUID(), "Test")); }
        @Override public ResourceLocation getLocationSkin() {
            try { Field f = NetworkPlayerInfo.class.getDeclaredField("locationSkin"); f.setAccessible(true); return (ResourceLocation) f.get(this); }
            catch (Exception error) { throw new AssertionError(error); }
        }
    }
}
