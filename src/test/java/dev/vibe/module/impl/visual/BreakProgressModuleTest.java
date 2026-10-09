package dev.vibe.module.impl.visual;

import dev.vibe.Vibe;
import dev.vibe.module.impl.world.RavenBlockAccess;
import java.lang.reflect.Field;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.*;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import org.junit.*;
import static org.junit.Assert.*;

public class BreakProgressModuleTest {
    private Minecraft previous, mc;
    private Vibe previousVibe;
    private BreakProgressModule module;
    private Player player;
    private BlockPos target = new BlockPos(1,63,2);
    @Before public void setup() throws Exception {
        Field registered = field(net.minecraft.init.Bootstrap.class,"alreadyRegistered");
        if (!registered.getBoolean(null)) {
            registered.setBoolean(null,true); net.minecraft.block.Block.registerBlocks(); net.minecraft.item.Item.registerItems();
        }
        previous = Minecraft.getMinecraft(); previousVibe = Vibe.getInstance();
        mc = allocate(Minecraft.class);
        field(Minecraft.class,"theMinecraft").set(null,mc);
        field(Vibe.class,"instance").set(null,null);
        mc.theWorld = allocate(World.class);
        mc.playerController = allocate(Controller.class);
        player = allocate(Player.class); player.capabilities = new PlayerCapabilities(); player.onGround = true;
        mc.thePlayer = player;
        mc.objectMouseOver = new MovingObjectPosition(new Vec3(1,64,2),EnumFacing.UP,target);
        field(PlayerControllerMP.class,"curBlockDamageMP").setFloat(mc.playerController,.5F);
        module = new BreakProgressModule(); module.setEnabled(true);
    }
    @After public void cleanup() throws Exception {
        field(Minecraft.class,"theMinecraft").set(null,previous); field(Vibe.class,"instance").set(null,previousVibe);
    }
    @Test public void manualProgressUsesControllerDamageAndSourceTextFormats() {
        module.tick();
        assertEquals(target,module.getBlock()); assertEquals(.5F,module.getProgress(),0F);
        assertEquals("50%",module.getProgressText());
        module.getMode().setValue("Decimal"); module.tick(); assertEquals("0.5",module.getProgressText());
        module.getMode().setValue("Second"); module.tick();
        float hardness = RavenBlockAccess.getBlockHardness(Blocks.stone,null,false,false);
        double expected = Math.round(((1F-.5F)/hardness/20D)*10D)/10D;
        assertEquals(expected+"s",module.getProgressText());
        assertFalse(module.getFadeIn().isEnabled());
    }
    @Test public void creativeNonEditableMissingWorldAndDisableResetTheBillboard() {
        module.tick(); player.capabilities.isCreativeMode = true; module.tick(); assertNull(module.getBlock());
        player.capabilities.isCreativeMode = false; player.capabilities.allowEdit = false; module.tick(); assertNull(module.getBlock());
        player.capabilities.allowEdit = true; module.tick(); assertNotNull(module.getBlock());
        module.setEnabled(false); assertNull(module.getBlock()); assertEquals("",module.getProgressText());
        module.setEnabled(true); mc.theWorld = null; module.tick(); assertNull(module.getBlock());
    }
    @Test public void manualProgressIsNotClampedAndZeroClearsTheBlock() throws Exception {
        field(PlayerControllerMP.class,"curBlockDamageMP").setFloat(mc.playerController,1.2F);
        module.tick(); assertEquals(1.2F,module.getProgress(),0F);
        field(PlayerControllerMP.class,"curBlockDamageMP").setFloat(mc.playerController,0F);
        module.tick(); assertNull(module.getBlock());
    }
    private static Field field(Class<?> type,String name)throws Exception {
        Field field=type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static <T> T allocate(Class<T> type)throws Exception {
        Class<?> unsafe=Class.forName("sun.misc.Unsafe");
        return type.cast(unsafe.getMethod("allocateInstance",Class.class).invoke(field(unsafe,"theUnsafe").get(null),type));
    }
    public static class Player extends EntityPlayerSP {
        private Player(){super(null,null,null,null);}
        @Override public ItemStack getHeldItem(){return null;}
        @Override public boolean isPotionActive(Potion potion){return false;}
        @Override public boolean isInsideOfMaterial(Material material){return false;}
    }
    public static class World extends WorldClient {
        private World(){super(null,null,0,null,null);}
        @Override public IBlockState getBlockState(BlockPos pos){return Blocks.stone.getDefaultState();}
    }
    public static class Controller extends PlayerControllerMP {
        private Controller(){super(null,null);}
    }
}
