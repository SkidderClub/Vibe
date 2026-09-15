package dev.vibe.ui;

import java.awt.image.BufferedImage;
import net.minecraft.client.renderer.ImageBufferDownload;
import org.junit.Test;
import static org.junit.Assert.*;

public class PreviewSkinTest {
    @Test public void legacyFriendSkinGetsValidModernLimbUvs() {
        BufferedImage skin = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y=0; y<32; y++) for (int x=0; x<64; x++) skin.setRGB(x,y,0xFF000000 | x << 16 | y << 8);
        BufferedImage converted = new ImageBufferDownload().parseUserSkin(skin);
        assertEquals(64, converted.getHeight());
        assertEquals(skin.getRGB(7,20), converted.getRGB(20,52));
        assertEquals(skin.getRGB(47,20), converted.getRGB(36,52));
    }
    @Test public void modernPlayerAndTargetSkinsRetainTheirBaseAndOuterPixels() {
        BufferedImage skin = new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
        skin.setRGB(8,8,0xFF123456);
        skin.setRGB(40,8,0xFF654321);
        skin.setRGB(20,52,0xFF55AA88);
        skin.setRGB(36,52,0xFFAA5599);
        BufferedImage converted = new ImageBufferDownload().parseUserSkin(skin);
        for (int[] pixel : new int[][] {{8,8},{40,8},{20,52},{36,52}})
            assertEquals(skin.getRGB(pixel[0],pixel[1]), converted.getRGB(pixel[0],pixel[1]));
    }
}
