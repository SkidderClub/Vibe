package dev.vibe.combat;

import dev.vibe.Vibe;
import dev.vibe.core.MoveFixTransformer;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.PickenSwitchModule;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.Item;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import static org.junit.Assert.*;

/** Verifies the selected combat item remains the one sent in the C02 attack. */
public class PickenSwitchHooksTest implements Opcodes {
    private Minecraft previous;
    private Vibe oldVibe;
    private static Minecraft mc;
    private PickenSwitchModule module;
    private Victim victim;
    private Method attack;
    private Object controller;
    private static final List<String> calls = new ArrayList<String>();
    private static boolean failLocal;

    @BeforeClass public static void bootstrap() throws Exception { dev.vibe.module.impl.PickenSwitchTest.bootstrap(); }
    @Before public void setup() throws Exception {
        previous = Minecraft.getMinecraft();
        oldVibe = Vibe.getInstance();
        mc = allocate(Minecraft.class);
        set(Minecraft.class, null, "theMinecraft", mc);
        mc.theWorld = allocate(WorldClient.class);
        mc.thePlayer = allocate(EntityPlayerSP.class);
        mc.thePlayer.worldObj = mc.theWorld;
        mc.thePlayer.inventory = new net.minecraft.entity.player.InventoryPlayer(mc.thePlayer);
        mc.playerController = new Controller(mc);
        mc.thePlayer.inventory.mainInventory[0] = new ItemStack(new ItemSword(Item.ToolMaterial.EMERALD));
        ItemStack utility = new ItemStack(new net.minecraft.item.Item());
        utility.addEnchantment(Enchantment.knockback, 2);
        utility.addEnchantment(Enchantment.fireAspect, 1);
        mc.thePlayer.inventory.mainInventory[4] = utility;
        victim = allocate(Victim.class);
        victim.worldObj = mc.theWorld;
        module = new PickenSwitchModule();
        set(Module.class, module, "enabled", true);
        ModuleManager manager = allocate(ModuleManager.class);
        set(ModuleManager.class, manager, "modules", new ArrayList<Module>(Arrays.asList(module)));
        Vibe vibe = new Vibe();
        set(Vibe.class, vibe, "moduleManager", manager);
        set(Vibe.class, null, "instance", vibe);
        calls.clear();
        failLocal = false;
        server = 0;
        String name = "dev/vibe/combat/PickenControllerFixture";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_8, ACC_PUBLIC, name, null, "java/lang/Object", null);
        MethodVisitor constructor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode(); constructor.visitVarInsn(ALOAD, 0); constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false); constructor.visitInsn(RETURN); constructor.visitMaxs(0, 0); constructor.visitEnd();
        MethodVisitor syncMethod = writer.visitMethod(ACC_PRIVATE, "syncCurrentPlayItem", "()V", null, null);
        syncMethod.visitCode(); syncMethod.visitMethodInsn(INVOKESTATIC, "dev/vibe/combat/PickenSwitchHooksTest", "sync", "()V", false); syncMethod.visitInsn(RETURN); syncMethod.visitMaxs(0, 0); syncMethod.visitEnd();
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "attackEntity", "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/entity/Entity;)V", null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 0); method.visitMethodInsn(INVOKESPECIAL, name, "syncCurrentPlayItem", "()V", false);
        method.visitMethodInsn(INVOKESTATIC, "dev/vibe/combat/PickenSwitchHooksTest", "networkAttack", "()V", false);
        method.visitInsn(RETURN); method.visitMaxs(0, 0); method.visitEnd(); writer.visitEnd();
        byte[] bytes = new MoveFixTransformer().transform(name, "net.minecraft.client.multiplayer.PlayerControllerMP", writer.toByteArray());
        Class<?> fixture = new ClassLoader(getClass().getClassLoader()) { Class<?> define() { return defineClass(name.replace('/', '.'), bytes, 0, bytes.length); } }.define();
        controller = fixture.newInstance();
        attack = fixture.getMethod("attackEntity", EntityPlayer.class, Entity.class);
    }
    @After public void cleanup() throws Exception { set(Minecraft.class, null, "theMinecraft", previous); set(Vibe.class, null, "instance", oldVibe); }
    private void warm() { module.tick(); module.tick(); module.tick(); }
    private void hit() throws Exception { attack.invoke(controller, mc.thePlayer, victim); }

    @Test public void sendsUtilitySlotImmediatelyBeforeC02WhileKeepingMainWeaponVisible() throws Exception {
        warm();
        hit();
        assertEquals(Arrays.asList("slot:4", "attack:4"), calls);
        assertEquals(0, mc.thePlayer.inventory.currentItem);
        assertTrue(module.hasSilentSlot());
        module.tick();
        assertEquals(Arrays.asList("slot:4", "attack:4", "slot:0"), calls);
        assertFalse(module.hasSilentSlot());
    }

    @Test public void restoresTheWeaponOnAModdedLocalAttackException() throws Exception {
        warm();
        failLocal = true;
        try { hit(); fail("Expected fixture error"); }
        catch (InvocationTargetException expected) { assertTrue(expected.getCause() instanceof IllegalStateException); }
        assertEquals(Arrays.asList("slot:4", "attack:4", "slot:0"), calls);
        assertEquals(0, mc.thePlayer.inventory.currentItem);
    }

    public static void networkAttack() { sync(); calls.add("attack:" + mc.thePlayer.inventory.currentItem); if (failLocal) throw new IllegalStateException("Fixture"); }
    private static int server;
    public static void sync() { int next = dev.vibe.module.impl.AutoToolModule.serverSlotHook(mc.thePlayer.inventory.currentItem); if (next != server) { calls.add("slot:" + next); server = next; } }
    private static final class Controller extends PlayerControllerMP { Controller(Minecraft minecraft) { super(minecraft, null); } @Override public void updateController() { sync(); } }
    public static final class Victim extends EntityOtherPlayerMP {
        private Victim() { super(null, null); }
        @Override public boolean isEntityAlive() { return true; }
        @Override public boolean isBurning() { return false; }
    }
    private static void set(Class<?> type, Object receiver, String name, Object value) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(receiver, value); }
    private static <T> T allocate(Class<T> type) throws Exception { Class<?> unsafe = Class.forName("sun.misc.Unsafe"); Field field = unsafe.getDeclaredField("theUnsafe"); field.setAccessible(true); return type.cast(unsafe.getMethod("allocateInstance", Class.class).invoke(field.get(null), type)); }
}
