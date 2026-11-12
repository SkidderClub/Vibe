package dev.vibe.features;
import dev.vibe.module.Category;
import dev.vibe.module.impl.meme.*;
import dev.vibe.inventory.AutomationFixture;
import net.minecraft.util.AxisAlignedBB;
import org.junit.Test;
import static org.junit.Assert.*;
public class BigbrainModuleTest {
    @Test public void memeModuleHasNoSettingsAndDoesNotChangeThePlayer()throws Exception{try(AutomationFixture f=new AutomationFixture()){
        BigbrainModule module=new BigbrainModule();f.base.modules.registerDynamic(module);AxisAlignedBB box=f.player.getEntityBoundingBox();float yaw=f.player.rotationYaw,pitch=f.player.rotationPitch;
        assertEquals("Bigbrain",module.getRawName());assertEquals(Category.MEME,module.getCategory());assertTrue(module.getSettings().isEmpty());module.setEnabled(true);assertSame(box,f.player.getEntityBoundingBox());assertEquals(yaw,f.player.rotationYaw,0);assertEquals(pitch,f.player.rotationPitch,0);module.setEnabled(false);
    }}
}
