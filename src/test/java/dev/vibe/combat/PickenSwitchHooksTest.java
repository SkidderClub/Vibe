package dev.vibe.combat;

import dev.vibe.Vibe;
import dev.vibe.core.MoveFixTransformer;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.combat.PickenSwitchModule;
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
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
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
    private static boolean failSwing, cancelClick;
    private static PickenSwitchHooksTest activeTest;

    @BeforeClass public static void bootstrap() throws Exception { dev.vibe.module.impl.combat.PickenSwitchTest.bootstrap(); }
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
        failSwing = cancelClick = false; activeTest = this;
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
    @Test public void silentMurderUsesTheSameSlotResolverAndRestoresImmediately() throws Exception {
        for(boolean autoEnabled:new boolean[]{false,true}) {
            installSilentMurder(autoEnabled);calls.clear();server=autoEnabled?4:0;
            hit();
            assertEquals(Arrays.asList("slot:7","attack:7","slot:"+(autoEnabled?4:0)),calls);
            assertEquals(0,mc.thePlayer.inventory.currentItem);
            assertFalse(dev.vibe.module.impl.meme.HypixelModule.ownsSilentSlot());
        }
    }
    @Test public void silentMurderRestoresServerAndVisibleSlotsOnAttackFailure() throws Exception {
        installSilentMurder(true);calls.clear();server=4;failLocal=true;
        try {hit();fail("Expected attack failure");}catch(InvocationTargetException expected){assertTrue(expected.getCause() instanceof IllegalStateException);}
        assertEquals(Arrays.asList("slot:7","attack:7","slot:4"),calls);
        assertEquals(0,mc.thePlayer.inventory.currentItem);
        assertFalse(dev.vibe.module.impl.meme.HypixelModule.ownsSilentSlot());
    }
    private void installSilentMurder(boolean withAuto) throws Exception {
        dev.vibe.module.impl.meme.HypixelModule murder=new dev.vibe.module.impl.meme.HypixelModule();
        murder.getMurderWeapons().setValue("stick");
        for(dev.vibe.setting.Setting<?> s:murder.getSettings())if(s.getRawName().equals("Silent murderer"))((dev.vibe.setting.BooleanSetting)s).setEnabled(true);
        set(Module.class,murder,"enabled",true);set(murder.getClass(),murder,"observedWorld",mc.theWorld);set(murder.getClass(),murder,"murderRoundActive",true);
        mc.thePlayer.inventory.mainInventory[7]=new ItemStack(net.minecraft.init.Items.stick);
        List<Module> modules=new ArrayList<Module>(Arrays.asList(module,murder));
        if(withAuto) {
            dev.vibe.module.impl.world.AutoToolModule auto=new dev.vibe.module.impl.world.AutoToolModule();
            for(dev.vibe.setting.Setting<?> s:auto.getSettings())if(s.getRawName().equals("Mode"))((dev.vibe.setting.ModeSetting)s).setValue("Silent");
            set(Module.class,auto,"enabled",true);set(auto.getClass(),auto,"originalSlot",0);set(auto.getClass(),auto,"spoofedSlot",4);modules.add(auto);
        }
        set(ModuleManager.class,Vibe.getInstance().getModuleManager(),"modules",modules);
    }
    @Test public void silentMurderEquipsBeforeTheSwingAndRestoresAfterTheEntireClick() throws Exception {
        for(boolean auto:new boolean[]{false,true}) {
            installSilentMurder(auto);calls.clear();server=auto?4:0;
            mc.objectMouseOver=new net.minecraft.util.MovingObjectPosition(victim);
            click();
            assertEquals(Arrays.asList("slot:7","swing:7","attack:7","slot:"+(auto?4:0)),calls);
            assertEquals(0,mc.thePlayer.inventory.currentItem);
            assertFalse(dev.vibe.module.impl.meme.HypixelModule.ownsSilentSlot());
        }
    }
    @Test public void wholeClickRestoresEvenWhenTheAttackIsSkippedOrSwingOrAttackThrows() throws Exception {
        for(boolean auto:new boolean[]{false,true})for(int failure=0;failure<3;failure++) {
            installSilentMurder(auto);calls.clear();server=auto?4:0;
            mc.objectMouseOver=new net.minecraft.util.MovingObjectPosition(victim);
            cancelClick=failure==0;failSwing=failure==1;failLocal=failure==2;
            try {click();if(failure!=0)fail("Expected failure");}
            catch(InvocationTargetException error) {assertTrue(error.getCause() instanceof IllegalStateException);}
            assertEquals("slot:7",calls.get(0));assertEquals("swing:7",calls.get(1));
            assertEquals("slot:"+(auto?4:0),calls.get(calls.size()-1));
            assertEquals(0,mc.thePlayer.inventory.currentItem);
            assertFalse(dev.vibe.module.impl.meme.HypixelModule.ownsSilentSlot());
        }
    }
    @Test public void aMissDoesNotSelectAMurderWeapon() throws Exception {
        installSilentMurder(false);calls.clear();mc.objectMouseOver=null;
        click();assertEquals(Arrays.asList("swing:0","miss"),calls);
        assertEquals(0,mc.thePlayer.inventory.currentItem);
    }
    private void click() throws Exception {
        String name="dev/vibe/combat/SilentClickFixture";
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);writer.visit(V1_8,ACC_PUBLIC,name,null,"java/lang/Object",null);
        MethodVisitor init=writer.visitMethod(ACC_PUBLIC,"<init>","()V",null,null);
        init.visitCode();init.visitVarInsn(ALOAD,0);init.visitMethodInsn(INVOKESPECIAL,"java/lang/Object","<init>","()V",false);
        init.visitInsn(RETURN);init.visitMaxs(0,0);init.visitEnd();
        MethodVisitor body=writer.visitMethod(ACC_PUBLIC,"clickMouse","()V",null,null);body.visitCode();
        body.visitMethodInsn(INVOKESTATIC,"dev/vibe/combat/PickenSwitchHooksTest","swing","()V",false);
        body.visitMethodInsn(INVOKESTATIC,"dev/vibe/combat/PickenSwitchHooksTest","clickAttack","()V",false);
        body.visitInsn(RETURN);body.visitMaxs(0,0);body.visitEnd();writer.visitEnd();
        final byte[] bytes=new MoveFixTransformer().transform(name,"net.minecraft.client.Minecraft",writer.toByteArray());
        Class<?> type=new ClassLoader(getClass().getClassLoader()) {
            Class<?> define(){return defineClass(name.replace('/','.'),bytes,0,bytes.length);}
        }.define();
        type.getMethod("clickMouse").invoke(type.newInstance());
    }
    public static void swing() {calls.add("swing:"+mc.thePlayer.inventory.currentItem);if(failSwing)throw new IllegalStateException("Swing failure");}
    public static void clickAttack() {
        if(cancelClick) {calls.add("cancelled");return;}
        if(mc.objectMouseOver==null||mc.objectMouseOver.entityHit==null) {calls.add("miss");return;}
        try {activeTest.hit();}
        catch(InvocationTargetException error) {throw (RuntimeException)error.getCause();}
        catch(Exception error) {throw new IllegalStateException(error);}
    }
    @Test public void hooksInstallInTheActualMinecraftAndControllerClasses() throws Exception {
        for(String name:new String[]{"net.minecraft.client.Minecraft","net.minecraft.client.multiplayer.PlayerControllerMP"}) {
            java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();
            try(java.io.InputStream input=getClass().getResourceAsStream("/"+name.replace('.','/')+".class")) {
                assertNotNull(input);byte[] buffer=new byte[8192];int count;
                while((count=input.read(buffer))!=-1)output.write(buffer,0,count);
            }
            byte[] transformed=new MoveFixTransformer().transform(name,name,output.toByteArray());
            ClassNode node=new ClassNode();new ClassReader(transformed).accept(node,0);
            int hooks=0;boolean swingBeforeAttack=false;
            for(Object value:node.methods) {
                MethodNode method=(MethodNode)value;int swing=-1,attack=-1,index=0;
                for(AbstractInsnNode instruction:method.instructions.toArray()) {
                    if(instruction instanceof MethodInsnNode) {
                        MethodInsnNode call=(MethodInsnNode)instruction;
                        if(call.owner.equals("dev/vibe/module/impl/meme/HypixelModule")&&call.name.equals(name.endsWith("Minecraft")?"beginClickHook":"beginAttackHook"))hooks++;
                        if(call.name.equals("swingItem")||call.name.equals("func_71038_i"))swing=index;
                        if(call.name.equals("attackEntity")||call.name.equals("func_78764_a"))attack=index;
                    }
                    index++;
                }
                if(method.name.startsWith("vibe$click$")&&swing>=0&&attack>swing)swingBeforeAttack=true;
            }
            assertEquals(name,1,hooks);
            if(name.endsWith("Minecraft"))assertTrue("Vanilla swings before calling the controller",swingBeforeAttack);
        }
    }
    private static int server;
    public static void sync() { int next = dev.vibe.module.impl.world.AutoToolModule.serverSlotHook(mc.thePlayer.inventory.currentItem); if (next != server) { calls.add("slot:" + next); server = next; } }
    private static final class Controller extends PlayerControllerMP { Controller(Minecraft minecraft) { super(minecraft, null); } @Override public void updateController() { sync(); } }
    public static final class Victim extends EntityOtherPlayerMP {
        private Victim() { super(null, null); }
        @Override public boolean isEntityAlive() { return true; }
        @Override public boolean isBurning() { return false; }
    }
    private static void set(Class<?> type, Object receiver, String name, Object value) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(receiver, value); }
    private static <T> T allocate(Class<T> type) throws Exception { Class<?> unsafe = Class.forName("sun.misc.Unsafe"); Field field = unsafe.getDeclaredField("theUnsafe"); field.setAccessible(true); return type.cast(unsafe.getMethod("allocateInstance", Class.class).invoke(field.get(null), type)); }
}
