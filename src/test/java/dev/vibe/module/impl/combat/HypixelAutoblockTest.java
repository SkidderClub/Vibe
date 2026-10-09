package dev.vibe.module.impl.combat;

import dev.vibe.Vibe;
import dev.vibe.setting.*;
import java.lang.reflect.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.*;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.*;
import static org.junit.Assert.*;

public class HypixelAutoblockTest {
    private Minecraft previous, minecraft;
    private Vibe previousVibe;
    private TestPlayer player;
    private KillAuraModule aura;
    private HypixelAutoblock block;

    @Before public void setup() throws Exception {
        Field registered = field(net.minecraft.init.Bootstrap.class, "alreadyRegistered");
        if (!registered.getBoolean(null)) {
            registered.setBoolean(null, true);
            net.minecraft.block.Block.registerBlocks(); net.minecraft.item.Item.registerItems();
        }
        previous = Minecraft.getMinecraft(); previousVibe = Vibe.getInstance();
        minecraft = allocate(Minecraft.class);
        field(Minecraft.class, "theMinecraft").set(null, minecraft);
        field(Vibe.class, "instance").set(null, null);
        minecraft.gameSettings = new GameSettings();
        minecraft.inGameHasFocus = true;
        minecraft.theWorld = allocate(WorldClient.class);
        player = allocate(TestPlayer.class);
        player.inventory = new InventoryPlayer(player);
        player.inventory.mainInventory[0] = new ItemStack(Items.iron_sword);
        minecraft.thePlayer = player;
        aura = new KillAuraModule();
        ((ModeSetting) setting("Autoblock Mode")).setValue("Hypixel");
        aura.setEnabled(true);
        block = new HypixelAutoblock(aura);
        KeyBinding.unPressAllKeys();
    }
    @After public void cleanup() throws Exception {
        KeyBinding.unPressAllKeys();
        field(Minecraft.class, "theMinecraft").set(null, previous);
        field(Vibe.class, "instance").set(null, previousVibe);
    }
    @Test public void screenshotDefaultsAreExposedOnlyForHypixel() {
        assertEquals(3.5D, aura.hypixelRange.getDouble(), 0.0D);
        assertEquals(200D, aura.hypixelMaximumHurtTime.getDouble(), 0.0D);
        assertEquals(150D, aura.hypixelMaximumHoldDuration.getDouble(), 0.0D);
        assertEquals(150D, aura.hypixelCooldown.getDouble(), 0.0D);
        assertEquals("Once", aura.hypixelUnblockOutOfRange.getValue());
        assertTrue(aura.hypixelForceAttack.isEnabled());
        assertFalse(aura.hypixelForceBlockAnimation.isEnabled());
        assertTrue(aura.hypixelRequireLeftMouse.isEnabled());
        assertFalse(aura.hypixelRequireRightMouse.isEnabled());
        assertFalse(aura.hypixelDamaged.isEnabled());
        assertTrue(aura.hypixelIgnoreTeammates.isEnabled());
        assertTrue(aura.hypixelRange.isVisible());
        ((ModeSetting) setting("Autoblock Mode")).setValue("Vanilla");
        assertFalse(aura.hypixelRange.isVisible());
    }
    @Test public void forceAttackQueuesEveryClickUntilVanillaHasReleasedSwordUse() throws Exception {
        invoke("startBlocking", new Class<?>[]{int.class}, 1);
        player.using = true;
        KeyBinding attack = minecraft.gameSettings.keyBindAttack;
        KeyBinding.onTick(attack.getKeyCode()); KeyBinding.onTick(attack.getKeyCode());
        block.onForceAttack();
        assertEquals(2, field(HypixelAutoblock.class, "pendingForceAttacks").getInt(block));
        assertFalse(minecraft.gameSettings.keyBindUseItem.isKeyDown());
        assertFalse(attack.isPressed());
        assertTrue(block.blocksUse());
        player.using = false;
        block.onForceAttack();
        assertEquals(0, field(HypixelAutoblock.class, "pendingForceAttacks").getInt(block));
        assertTrue(attack.isPressed()); assertTrue(attack.isPressed()); assertFalse(attack.isPressed());
    }
    @Test public void cooldownDoesNotRestartABlockAndHurtTriggerIsFourTicks() throws Exception {
        invoke("startBlocking", new Class<?>[]{int.class}, 1);
        invoke("stopBlocking", new Class<?>[]{boolean.class}, true);
        invoke("startBlocking", new Class<?>[]{int.class}, 2);
        assertFalse(field(HypixelAutoblock.class, "isBlocking").getBoolean(block));
        field(HypixelAutoblock.class, "lastBlockEndTimeMs").setLong(block, System.currentTimeMillis() - 151L);
        invoke("startBlocking", new Class<?>[]{int.class}, 3);
        assertTrue(field(HypixelAutoblock.class, "isBlocking").getBoolean(block));
        aura.hypixelDamaged.setValue(true);
        player.hurtTime = 4;
        assertEquals(true, invoke("shouldPredictiveBlock", new Class<?>[0]));
        player.hurtTime = 3;
        assertEquals(false, invoke("shouldPredictiveBlock", new Class<?>[0]));
    }
    private Object invoke(String name, Class<?>[] signature, Object... arguments) throws Exception {
        Method method = HypixelAutoblock.class.getDeclaredMethod(name, signature);
        method.setAccessible(true); return method.invoke(block, arguments);
    }
    private Setting<?> setting(String name) {
        for (Setting<?> setting : aura.getSettings()) if (setting.getRawName().equals(name)) return setting;
        throw new AssertionError(name);
    }
    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        return type.cast(unsafe.getMethod("allocateInstance", Class.class).invoke(field(unsafe, "theUnsafe").get(null), type));
    }
    public static class TestPlayer extends EntityPlayerSP {
        boolean using;
        private TestPlayer() { super(null, null, null, null); }
        @Override public ItemStack getHeldItem() { return inventory.getCurrentItem(); }
        @Override public boolean isUsingItem() { return using; }
        @Override public boolean isBlocking() { return using; }
    }
}
