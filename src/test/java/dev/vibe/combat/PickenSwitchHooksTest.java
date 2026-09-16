package dev.vibe.combat;

import dev.vibe.Vibe;
import dev.vibe.core.MoveFixTransformer;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.*;
import dev.vibe.setting.ModeSetting;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.*;
import net.minecraft.item.*;
import net.minecraft.enchantment.Enchantment;
import org.junit.*;
import org.objectweb.asm.*;
import static org.junit.Assert.*;

public class PickenSwitchHooksTest implements Opcodes {
    private Minecraft previous;private Vibe oldVibe;private static Minecraft mc;
    private PickenSwitchModule module;private Victim victim;private Method attack;private Object controller;
    private static final List<String> packets=new ArrayList<>();private static int server;private static boolean failAttack;
    @BeforeClass public static void bootstrap()throws Exception{PickenSwitchTest.bootstrap();}
    @Before public void setup()throws Exception {
        previous=Minecraft.getMinecraft();oldVibe=Vibe.getInstance();mc=allocate(Minecraft.class);set(Minecraft.class,null,"theMinecraft",mc);
        mc.theWorld=allocate(WorldClient.class);mc.thePlayer=allocate(EntityPlayerSP.class);mc.thePlayer.worldObj=mc.theWorld;
        mc.thePlayer.inventory=new InventoryPlayer(mc.thePlayer);mc.playerController=new Controller(mc);
        mc.thePlayer.inventory.mainInventory[0]=new ItemStack(new ItemSword(Item.ToolMaterial.EMERALD));
        ItemStack utility=new ItemStack(new Item());utility.addEnchantment(Enchantment.knockback,2);utility.addEnchantment(Enchantment.fireAspect,1);mc.thePlayer.inventory.mainInventory[4]=utility;
        victim=allocate(Victim.class);victim.worldObj=mc.theWorld;
        module=new PickenSwitchModule();set(Module.class,module,"enabled",true);
        ModuleManager manager=allocate(ModuleManager.class);set(ModuleManager.class,manager,"modules",new ArrayList<Module>(Arrays.asList(module)));
        Vibe vibe=new Vibe();set(Vibe.class,vibe,"moduleManager",manager);set(Vibe.class,null,"instance",vibe);
        packets.clear();server=0;failAttack=false;
        String name="dev/vibe/combat/PickenControllerFixture";ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(V1_8,ACC_PUBLIC,name,null,"java/lang/Object",null);
        MethodVisitor c=w.visitMethod(ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(ALOAD,0);c.visitMethodInsn(INVOKESPECIAL,"java/lang/Object","<init>","()V",false);c.visitInsn(RETURN);c.visitMaxs(0,0);c.visitEnd();
        MethodVisitor a=w.visitMethod(ACC_PUBLIC,"attackEntity","(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/entity/Entity;)V",null,null);a.visitCode();a.visitMethodInsn(INVOKESTATIC,"dev/vibe/combat/PickenSwitchHooksTest","vanillaAttack","()V",false);a.visitInsn(RETURN);a.visitMaxs(0,0);a.visitEnd();w.visitEnd();
        byte[] bytes=new MoveFixTransformer().transform(name,"net.minecraft.client.multiplayer.PlayerControllerMP",w.toByteArray());
        Class<?> fixture=new ClassLoader(getClass().getClassLoader()){Class<?> define(){return defineClass(name.replace('/','.'),bytes,0,bytes.length);}}.define();
        controller=fixture.newInstance();attack=fixture.getMethod("attackEntity",EntityPlayer.class,Entity.class);
    }
    @After public void cleanup()throws Exception{set(Minecraft.class,null,"theMinecraft",previous);set(Vibe.class,null,"instance",oldVibe);}
    private void mode(String mode){for(dev.vibe.setting.Setting<?> s:module.getSettings())if(s.getRawName().equals("Mode"))((ModeSetting)s).setValue(mode);}
    private void warm(){module.tick();module.tick();module.tick();}
    private void hit()throws Exception{attack.invoke(controller,mc.thePlayer,victim);}
    @Test public void basicSwitchPrecedesAttackAndRestoresOnFollowingTick()throws Exception{
        warm();hit();assertEquals(Arrays.asList("slot:4","attack:4"),packets);assertEquals(4,mc.thePlayer.inventory.currentItem);
        packets.add("movement");module.tick();assertEquals(Arrays.asList("slot:4","attack:4","movement","slot:0"),packets);assertEquals(0,mc.thePlayer.inventory.currentItem);
    }
    @Test public void silentKeepsVisualSlotAndCleansUpAfterExceptionsAndDisable()throws Exception{
        mode("Silent");warm();failAttack=true;
        try{hit();fail("Expected controller error");}catch(InvocationTargetException e){assertTrue(e.getCause() instanceof IllegalStateException);}
        assertEquals(0,mc.thePlayer.inventory.currentItem);assertEquals(4,AutoToolModule.serverSlotHook(0));assertTrue(module.hasSilentSlot());
        module.setEnabled(false);assertEquals(2,packets.size());packets.add("movement");module.tick();
        assertEquals(0,AutoToolModule.serverSlotHook(0));assertFalse(module.hasSilentSlot());assertEquals("slot:0",packets.get(3));
    }
    @Test public void warmupAndCooldownAvoidRepeatedSwitchesAndManualScrollWins()throws Exception{
        hit();assertEquals(Arrays.asList("attack:0"),packets);packets.clear();warm();hit();
        mc.thePlayer.inventory.currentItem=2;module.tick();assertEquals(2,mc.thePlayer.inventory.currentItem);assertEquals("slot:2",packets.get(2));
        hit();assertEquals("attack:2",packets.get(3));
    }
    public static void vanillaAttack(){sync();packets.add("attack:"+mc.thePlayer.inventory.currentItem);if(failAttack)throw new IllegalStateException("Fixture");}
    private static void sync(){int next=AutoToolModule.serverSlotHook(mc.thePlayer.inventory.currentItem);if(next!=server){packets.add("slot:"+next);server=next;}}
    private static final class Controller extends PlayerControllerMP{Controller(Minecraft mc){super(mc,null);}@Override public void updateController(){sync();}}
    public static final class Victim extends EntityOtherPlayerMP{private Victim(){super(null,null);}@Override public boolean isEntityAlive(){return true;}@Override public boolean isBurning(){return false;}}
    private static void set(Class<?> c,Object o,String n,Object v)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    private static <T>T allocate(Class<T> c)throws Exception{Class<?> u=Class.forName("sun.misc.Unsafe");Field f=u.getDeclaredField("theUnsafe");f.setAccessible(true);return c.cast(u.getMethod("allocateInstance",Class.class).invoke(f.get(null),c));}
}
