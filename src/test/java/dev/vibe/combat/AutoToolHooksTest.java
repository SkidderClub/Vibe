package dev.vibe.combat;

import dev.vibe.Vibe;
import dev.vibe.core.MoveFixTransformer;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.AutoToolModule;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import org.junit.*;
import org.objectweb.asm.*;
import static org.junit.Assert.*;

public class AutoToolHooksTest implements Opcodes {
    private Minecraft previousMinecraft;
    private Vibe previousVibe;
    private static Minecraft minecraft;
    private static boolean fail;
    private static ItemStack visible, tool;

    @Before public void setUp() throws Exception {
        previousMinecraft = Minecraft.getMinecraft(); previousVibe = Vibe.getInstance();
        minecraft = allocate(Minecraft.class); set(Minecraft.class, null, "theMinecraft", minecraft);
        minecraft.thePlayer = allocate(EntityPlayerSP.class);
        minecraft.thePlayer.inventory = new InventoryPlayer(minecraft.thePlayer);
        visible = allocate(ItemStack.class); tool = allocate(ItemStack.class);
        minecraft.thePlayer.inventory.mainInventory[0] = visible;
        minecraft.thePlayer.inventory.mainInventory[4] = tool;
        AutoToolModule autoTool = new AutoToolModule();
        set(Module.class, autoTool, "enabled", true);
        for (dev.vibe.setting.Setting<?> setting : autoTool.getSettings())
            if (setting.getRawName().equals("Mode")) ((dev.vibe.setting.ModeSetting)setting).setValue("Silent");
        set(AutoToolModule.class, autoTool, "originalSlot", 0);
        set(AutoToolModule.class, autoTool, "spoofedSlot", 4);
        ModuleManager modules = allocate(ModuleManager.class);
        set(ModuleManager.class, modules, "modules", new ArrayList<Module>(Arrays.asList(autoTool)));
        Vibe vibe = new Vibe(); set(Vibe.class,vibe,"moduleManager",modules); set(Vibe.class,null,"instance",vibe);
        fail = false;
    }
    @After public void tearDown() throws Exception {
        set(Minecraft.class,null,"theMinecraft",previousMinecraft); set(Vibe.class,null,"instance",previousVibe);
    }
    @Test public void transformedActionsUseTheToolAndRestoreTheDisplayedItemEvenOnFailure() throws Exception {
        String name = "dev/vibe/combat/ToolScopeFixture";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_8, ACC_PUBLIC, name, null, "java/lang/Object", null);
        MethodVisitor constructor = writer.visitMethod(ACC_PUBLIC,"<init>","()V",null,null);
        constructor.visitCode(); constructor.visitVarInsn(ALOAD,0);
        constructor.visitMethodInsn(INVOKESPECIAL,"java/lang/Object","<init>","()V",false);
        constructor.visitInsn(RETURN); constructor.visitMaxs(0,0); constructor.visitEnd();
        MethodVisitor action = writer.visitMethod(ACC_PUBLIC,"onPlayerDamageBlock","(Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;)Z",null,null);
        action.visitCode();
        action.visitMethodInsn(INVOKESTATIC,"dev/vibe/combat/AutoToolHooksTest","insideAction","()Z",false);
        action.visitInsn(IRETURN); action.visitMaxs(0,0); action.visitEnd(); writer.visitEnd();
        byte[] transformed = new MoveFixTransformer().transform(name,"net.minecraft.client.multiplayer.PlayerControllerMP",writer.toByteArray());
        Class<?> fixture = new ClassLoader(getClass().getClassLoader()) {
            Class<?> define() { return defineClass(name.replace('/','.'),transformed,0,transformed.length); }
        }.define();
        Object instance = fixture.newInstance();
        Method method = fixture.getMethod("onPlayerDamageBlock",BlockPos.class,EnumFacing.class);
        assertSame(visible,minecraft.thePlayer.getHeldItem());
        assertEquals(4,AutoToolModule.serverSlotHook(0));
        assertEquals(true,method.invoke(instance,null,null));
        assertSame(visible,minecraft.thePlayer.getHeldItem());
        fail = true;
        try { method.invoke(instance,null,null); fail("Expected action failure"); }
        catch (InvocationTargetException expected) { assertTrue(expected.getCause() instanceof IllegalStateException); }
        assertSame(visible,minecraft.thePlayer.getHeldItem());
        assertEquals(0,minecraft.thePlayer.inventory.currentItem);
    }
    public static boolean insideAction() {
        assertSame(tool,minecraft.thePlayer.getHeldItem());
        assertEquals(4,minecraft.thePlayer.inventory.currentItem);
        if (fail) throw new IllegalStateException("Simulated controller failure");
        return true;
    }

    @Test public void bedAuraSuppressesOnlyItsOwnIdleMiningReset() throws Exception {
        dev.vibe.module.impl.BedAuraModule bed = new dev.vibe.module.impl.BedAuraModule();
        set(Module.class, bed, "enabled", true);
        set(bed.getClass(), bed, "owner", minecraft.thePlayer);
        set(bed.getClass(), bed, "ready", true);
        ModuleManager modules = Vibe.getInstance().getModuleManager();
        set(ModuleManager.class, modules, "modules", new ArrayList<Module>(Arrays.asList(bed)));
        String name = "dev/vibe/combat/BedResetFixture";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_8, ACC_PUBLIC, name, null, "java/lang/Object", null);
        writer.visitField(ACC_PUBLIC, "resets", "I", null, null).visitEnd();
        MethodVisitor reset = writer.visitMethod(ACC_PUBLIC, "resetBlockRemoving", "()V", null, null);
        reset.visitCode(); reset.visitVarInsn(ALOAD,0); reset.visitInsn(DUP);
        reset.visitFieldInsn(GETFIELD,name,"resets","I"); reset.visitInsn(ICONST_1); reset.visitInsn(IADD);
        reset.visitFieldInsn(PUTFIELD,name,"resets","I"); reset.visitInsn(RETURN); reset.visitMaxs(0,0); reset.visitEnd(); writer.visitEnd();
        byte[] transformed = new MoveFixTransformer().transform(name,"net.minecraft.client.multiplayer.PlayerControllerMP",writer.toByteArray());
        Class<?> type = new ClassLoader(getClass().getClassLoader()) {
            Class<?> define() { return defineClass(name.replace('/','.'),transformed,0,transformed.length); }
        }.define();
        Object fixture = allocate(type);
        Method method = type.getMethod("resetBlockRemoving");
        method.invoke(fixture);
        assertEquals(0,type.getField("resets").getInt(fixture));
        set(bed.getClass(), bed, "resetting", true); method.invoke(fixture);
        assertEquals("Explicit target cancellation must reach vanilla",1,type.getField("resets").getInt(fixture));
        set(bed.getClass(), bed, "resetting", false); set(Module.class,bed,"enabled",false); method.invoke(fixture);
        assertEquals("Disabled BedAura must not alter ordinary mining",2,type.getField("resets").getInt(fixture));
    }
    private static void set(Class<?> type,Object object,String name,Object value) throws Exception {
        Field field=type.getDeclaredField(name); field.setAccessible(true); field.set(object,value);
    }
    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafe=Class.forName("sun.misc.Unsafe"); Field field=unsafe.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(unsafe.getMethod("allocateInstance",Class.class).invoke(field.get(null),type));
    }
}
