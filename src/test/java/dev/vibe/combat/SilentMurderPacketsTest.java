package dev.vibe.combat;

import dev.vibe.Vibe;
import dev.vibe.core.MoveFixTransformer;
import dev.vibe.module.Module;
import dev.vibe.module.ModuleManager;
import dev.vibe.module.impl.meme.HypixelModule;
import dev.vibe.module.impl.world.AutoToolModule;
import dev.vibe.path.PathTestFixture;
import dev.vibe.setting.*;
import java.io.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.util.MovingObjectPosition;
import org.junit.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;

/** Executes the real transformed controller's C09 cache and C02 writer, with no socket or server. */
public class SilentMurderPacketsTest implements Opcodes {
    private Minecraft previous,mc;
    private Vibe oldVibe;
    private HypixelModule murder;
    private AutoToolModule auto;
    private Player player;
    private PickenSwitchHooksTest.Victim victim;
    private Connection connection;

    @BeforeClass public static void bootstrap() throws Exception {dev.vibe.module.impl.combat.PickenSwitchTest.bootstrap();}
    @Before public void setup() throws Exception {
        previous=Minecraft.getMinecraft();oldVibe=Vibe.getInstance();
        mc=PathTestFixture.allocate(Minecraft.class);PathTestFixture.set(Minecraft.class,null,"theMinecraft",mc);
        mc.theWorld=PathTestFixture.allocate(WorldClient.class);
        player=PathTestFixture.allocate(Player.class);mc.thePlayer=player;player.worldObj=mc.theWorld;
        player.inventory=new InventoryPlayer(player);player.inventory.currentItem=8;
        player.inventory.mainInventory[0]=new ItemStack(Items.book);
        player.inventory.mainInventory[1]=new ItemStack(Items.bread);
        player.inventory.mainInventory[4]=new ItemStack(Items.wooden_pickaxe);
        player.inventory.mainInventory[7]=new ItemStack(Items.iron_sword);
        connection=PathTestFixture.allocate(Connection.class);connection.trace=new ArrayList<String>();connection.player=player;
        connection.serverSlot=8;
        mc.playerController=nativeController(mc,connection);
        victim=PathTestFixture.allocate(PickenSwitchHooksTest.Victim.class);victim.worldObj=mc.theWorld;victim.setEntityId(42);
        mc.objectMouseOver=new MovingObjectPosition(victim);
        murder=new HypixelModule();auto=new AutoToolModule();
        for(Setting<?> s:murder.getSettings())if(s.getRawName().equals("Silent murderer"))((BooleanSetting)s).setEnabled(true);
        for(Setting<?> s:auto.getSettings())if(s.getRawName().equals("Mode"))((ModeSetting)s).setValue("Silent");
        PathTestFixture.set(Module.class,murder,"enabled",true);
        PathTestFixture.set(HypixelModule.class,murder,"observedWorld",mc.theWorld);
        PathTestFixture.set(HypixelModule.class,murder,"murderRoundActive",true);
        PathTestFixture.set(AutoToolModule.class,auto,"originalSlot",8);
        PathTestFixture.set(AutoToolModule.class,auto,"spoofedSlot",4);
        ModuleManager modules=PathTestFixture.allocate(ModuleManager.class);
        PathTestFixture.set(ModuleManager.class,modules,"modules",new ArrayList<Module>(Arrays.asList(murder,auto)));
        Vibe vibe=new Vibe();PathTestFixture.set(Vibe.class,vibe,"moduleManager",modules);PathTestFixture.set(Vibe.class,null,"instance",vibe);
        mc.playerController.updateController();connection.trace.clear();
    }
    @After public void cleanup() throws Exception {
        try {if(Minecraft.getMinecraft()==mc)HypixelModule.endClickHook(8);}
        finally {PathTestFixture.set(Minecraft.class,null,"theMinecraft",previous);PathTestFixture.set(Vibe.class,null,"instance",oldVibe);}
    }
    @Test public void emptyHandClickUsesRealSwordSelectionAttackAndImmediateReturnPackets() throws Exception {
        for(boolean useAuto:new boolean[]{false,true}) {
            PathTestFixture.set(Module.class,auto,"enabled",useAuto);
            player.inventory.currentItem=8;mc.playerController.updateController();connection.trace.clear();
            int original=HypixelModule.beginClickHook();
            try {
                connection.addToSendQueue(new C0APacketAnimation());
                mc.playerController.attackEntity(player,victim);
            } finally {HypixelModule.endClickHook(original);}
            assertEquals(Arrays.asList("C09:7","C0A:7","C02:7","C09:"+(useAuto?4:8)),connection.trace);
            assertSame(Items.iron_sword,connection.attackItem.getItem());assertSame(Items.iron_sword,player.localAttackItem.getItem());
            assertEquals(8,player.inventory.currentItem);assertNull(player.getHeldItem());
            assertEquals(useAuto?4:8,connection.serverSlot);assertFalse(HypixelModule.ownsSilentSlot());
        }
    }
    @Test public void aDirectControllerAttackAlsoUsesTheRealSwordAndRestoresTheEmptyHand() {
        connection.trace.clear();mc.playerController.attackEntity(player,victim);
        assertEquals(Arrays.asList("C09:7","C02:7","C09:8"),connection.trace);
        assertSame(Items.iron_sword,connection.attackItem.getItem());assertEquals(8,player.inventory.currentItem);assertNull(player.getHeldItem());
    }
    @Test public void aNativeLocalAttackFailureStillReturnsToTheFistAndClearsTheOverride() {
        player.failAttack=true;int original=HypixelModule.beginClickHook();
        try {mc.playerController.attackEntity(player,victim);fail("Expected local attack failure");}
        catch(IllegalStateException expected) {assertEquals("local attack",expected.getMessage());}
        finally {HypixelModule.endClickHook(original);}
        assertEquals(Arrays.asList("C09:7","C02:7","C09:8"),connection.trace);
        assertEquals(8,player.inventory.currentItem);assertNull(player.getHeldItem());assertEquals(8,AutoToolModule.serverSlotHook(8));
    }
    @Test public void aNamedCosmeticKnifeAlsoWinsOverEarlierMenuBooksWhenNoSwordExists() {
        player.inventory.mainInventory[7]=new ItemStack(Items.stick).setStackDisplayName("\u00a7cKnife");
        mc.playerController.attackEntity(player,victim);
        assertEquals(Arrays.asList("C09:7","C02:7","C09:8"),connection.trace);
        assertSame(Items.stick,connection.attackItem.getItem());assertNull(player.getHeldItem());
    }

    private static PlayerControllerMP nativeController(Minecraft mc,Connection connection) throws Exception {
        String parent="net/minecraft/client/multiplayer/PlayerControllerMP",name="dev/vibe/combat/NativeMurderController";
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try(InputStream input=PlayerControllerMP.class.getResourceAsStream("PlayerControllerMP.class")) {
            byte[] buffer=new byte[8192];for(int n;(n=input.read(buffer))!=-1;)output.write(buffer,0,n);
        }
        ClassNode node=new ClassNode();new ClassReader(new MoveFixTransformer().transform(parent,parent,output.toByteArray())).accept(node,0);
        node.name=name;node.superName=parent;
        // Retain the actual attack and slot-sync path; unrelated block helpers accept
        // the original controller class and are not part of this combat fixture.
        for(Iterator<?> methods=node.methods.iterator();methods.hasNext();) {
            MethodNode method=(MethodNode)methods.next();
            if(!(method.name.equals("<init>")||method.name.equals("syncCurrentPlayItem")||method.name.equals("isSpectator")
                    ||method.name.equals("updateController")||method.name.equals("attackEntity")||method.name.equals("vibe$tool$attackEntity")))methods.remove();
        }
        for(Object value:node.methods) {
            MethodNode method=(MethodNode)value;
            for(AbstractInsnNode instruction:method.instructions.toArray()) {
                if(instruction instanceof FieldInsnNode) {
                    FieldInsnNode field=(FieldInsnNode)instruction;if(field.owner.equals(parent))field.owner=name;
                } else if(instruction instanceof MethodInsnNode) {
                    MethodInsnNode call=(MethodInsnNode)instruction;if(call.owner.equals(parent))call.owner=name;
                    if(method.name.equals("<init>")&&call.owner.equals("java/lang/Object")&&call.name.equals("<init>")) {
                        InsnList args=new InsnList();args.add(new VarInsnNode(ALOAD,1));args.add(new VarInsnNode(ALOAD,2));
                        method.instructions.insertBefore(call,args);call.owner=parent;
                        call.desc="(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/network/NetHandlerPlayClient;)V";
                    }
                } else if(instruction instanceof FrameNode) {
                    FrameNode frame=(FrameNode)instruction;
                    if(frame.local!=null)Collections.replaceAll(frame.local,parent,name);
                    if(frame.stack!=null)Collections.replaceAll(frame.stack,parent,name);
                }
            }
            if(method.name.equals("updateController")) {
                // Keep the real slot-sync method. Omit only the unrelated network-device poll.
                method.instructions.clear();method.tryCatchBlocks.clear();if(method.localVariables!=null)method.localVariables.clear();
                method.instructions.add(new VarInsnNode(ALOAD,0));
                method.instructions.add(new MethodInsnNode(INVOKESPECIAL,name,"syncCurrentPlayItem","()V",false));
                method.instructions.add(new InsnNode(RETURN));method.maxLocals=1;method.maxStack=1;
            }
        }
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);final byte[] bytes=writer.toByteArray();
        Class<?> type=new ClassLoader(SilentMurderPacketsTest.class.getClassLoader()) {
            Class<?> define(){return defineClass(name.replace('/','.'),bytes,0,bytes.length);}
        }.define();
        return (PlayerControllerMP)type.getConstructor(Minecraft.class,NetHandlerPlayClient.class).newInstance(mc,connection);
    }
    public static class Player extends EntityPlayerSP {
        ItemStack localAttackItem;boolean failAttack;
        private Player(){super(null,null,null,null);}
        @Override public void attackTargetEntityWithCurrentItem(Entity entity) {
            localAttackItem=getHeldItem();if(failAttack)throw new IllegalStateException("local attack");
        }
    }
    public static class Connection extends NetHandlerPlayClient {
        List<String> trace;Player player;int serverSlot;ItemStack attackItem;
        private Connection(){super(null,null,null,null);}
        @Override public void addToSendQueue(Packet packet) {
            if(packet instanceof C09PacketHeldItemChange) {serverSlot=((C09PacketHeldItemChange)packet).getSlotId();trace.add("C09:"+serverSlot);}
            else if(packet instanceof C0APacketAnimation)trace.add("C0A:"+serverSlot);
            else if(packet instanceof C02PacketUseEntity&&((C02PacketUseEntity)packet).getAction()==C02PacketUseEntity.Action.ATTACK) {
                attackItem=player.inventory.mainInventory[serverSlot];trace.add("C02:"+serverSlot);
            }
        }
    }
}
