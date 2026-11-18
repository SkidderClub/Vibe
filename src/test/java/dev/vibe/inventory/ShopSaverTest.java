package dev.vibe.inventory;

import dev.vibe.core.ShopSaverTransformer;
import dev.vibe.module.impl.client.ShopSaverModule;
import dev.vibe.path.PathTestFixture;
import java.io.*;
import java.lang.reflect.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.*;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.Assert.*;

public class ShopSaverTest {
    @Test public void nativeDismissRetainsContainerAndCursorReopensLiveContentsAndDisablingClosesOnce() throws Exception {
        try (AutomationFixture f = new AutomationFixture()) {
            Client mc = client(f); ShopSaverModule module = new ShopSaverModule(); f.base.modules.registerDynamic(module); module.setEnabled(true);
            Chest chest = chest(mc); ItemStack carried = new ItemStack(Items.apple, 2); mc.thePlayer.inventory.setItemStack(carried);
            mc.thePlayer.closeScreen();
            assertNull(mc.currentScreen); assertSame(chest.inventorySlots, mc.thePlayer.openContainer);
            assertSame(carried, mc.thePlayer.inventory.getItemStack()); assertEquals(0, chest.closes); assertTrue(f.queue.packets.isEmpty());
            chest.inventorySlots.putStackInSlot(0, new ItemStack(Items.diamond, 12));
            mc.displayGuiScreen(new GuiInventory(mc.thePlayer));
            assertSame(chest, mc.currentScreen); assertEquals(12, ((net.minecraft.inventory.ContainerChest)chest.inventorySlots).getLowerChestInventory().getStackInSlot(0).stackSize);
            mc.thePlayer.closeScreen(); assertNull(mc.currentScreen); assertTrue(f.queue.packets.isEmpty());
            module.setEnabled(false);
            assertSame(mc.thePlayer.inventoryContainer, mc.thePlayer.openContainer); assertEquals(1, f.queue.packets.size());
            assertTrue(f.queue.packets.get(0) instanceof C0DPacketCloseWindow);
            net.minecraft.network.PacketBuffer packet=new net.minecraft.network.PacketBuffer(io.netty.buffer.Unpooled.buffer());
            try{f.queue.packets.get(0).writePacketData(packet);assertEquals(42,packet.readUnsignedByte());}finally{packet.release();}
            module.setEnabled(false); assertEquals(1, f.queue.packets.size());
            assertTrue(module.opening(new GuiInventory(mc.thePlayer)) instanceof GuiInventory);
        }
    }
    @Test public void serverClosureAndNewWindowsAreAuthoritativeAndOrdinaryInventoryStillCloses() throws Exception {
        try (AutomationFixture f = new AutomationFixture()) {
            Client mc = client(f); ShopSaverModule module = new ShopSaverModule(); f.base.modules.registerDynamic(module); module.setEnabled(true);
            Chest chest = chest(mc); mc.thePlayer.closeScreen();
            // This is the native path used by S2EPacketCloseWindow, deliberately unhooked.
            mc.thePlayer.closeScreenAndDropStack(); module.tick();
            assertSame(mc.thePlayer.inventoryContainer, mc.thePlayer.openContainer); assertTrue(f.queue.packets.isEmpty());
            mc.displayGuiScreen(new GuiInventory(mc.thePlayer)); assertTrue(mc.currentScreen instanceof GuiInventory);
            mc.thePlayer.closeScreen(); assertEquals(1, f.queue.packets.size());
            chest = chest(mc); mc.thePlayer.closeScreen();
            mc.thePlayer.openContainer = mc.thePlayer.inventoryContainer; module.tick();
            assertTrue(module.opening(new GuiInventory(mc.thePlayer)) instanceof GuiInventory);
            chest(mc); mc.thePlayer.isDead = true; mc.thePlayer.closeScreen();
            assertSame(mc.thePlayer.inventoryContainer, mc.thePlayer.openContainer);
        }
    }
    @Test public void hooksVerifyAndAreIdempotentForNativePlayerAndContainer() throws Exception {
        for(String name : new String[]{"net.minecraft.client.entity.EntityPlayerSP", "net.minecraft.client.gui.inventory.GuiContainer"}) {
            ShopSaverTransformer transformer = new ShopSaverTransformer(); byte[] patched = transformer.transform(name,name,resource(name));
            assertArrayEquals(patched,transformer.transform(name,name,patched)); ClassNode node = node(patched); int count=0;
            for(Object value:node.methods){MethodNode m=(MethodNode)value;new Analyzer(new BasicVerifier()).analyze(node.name,m);
                for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode&&((MethodInsnNode)i).owner.endsWith("ShopSaverModule"))count++;}
            assertEquals(1,count);
        }
    }
    private static Client client(AutomationFixture f) throws Exception {
        Client mc = PathTestFixture.allocate(Client.class); copy(f.mc,mc,Minecraft.class); PathTestFixture.set(Minecraft.class,null,"theMinecraft",mc);
        String source="net.minecraft.client.entity.EntityPlayerSP"; ClassNode nativeNode=node(new ShopSaverTransformer().transform(source,source,resource(source)));
        ClassNode type=new ClassNode(); type.version=52;type.access=Opcodes.ACC_PUBLIC;type.name="dev/vibe/inventory/ShopSaverNativePlayer";type.superName=org.objectweb.asm.Type.getInternalName(AutomationFixture.Player.class);
        for(Object value:nativeNode.methods)if(((MethodNode)value).name.equals("closeScreen"))type.methods.add(value);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);type.accept(writer);byte[] bytes=writer.toByteArray();
        Class<?> nativePlayer=new ClassLoader(ShopSaverTest.class.getClassLoader()){Class<?> define(){return defineClass(null,bytes,0,bytes.length);}}.define();
        EntityPlayerSP player=(EntityPlayerSP)PathTestFixture.allocate(nativePlayer);copy(f.player,player,AutomationFixture.Player.class);mc.thePlayer=player;
        PathTestFixture.set(EntityPlayerSP.class,player,"mc",mc);return mc;
    }
    private static Chest chest(Client mc) throws Exception {
        Chest chest=new Chest(mc.thePlayer.inventory,new InventoryBasic("Quick Buy",true,27));chest.inventorySlots.windowId=42;
        PathTestFixture.set(GuiScreen.class,chest,"mc",mc);mc.thePlayer.openContainer=chest.inventorySlots;mc.displayGuiScreen(chest);return chest;
    }
    private static void copy(Object source,Object target,Class<?> type)throws Exception{for(Class<?> c=type;c!=Object.class;c=c.getSuperclass())for(Field field:c.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())){field.setAccessible(true);field.set(target,field.get(source));}}
    private static ClassNode node(byte[] bytes){ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);return node;}
    private static byte[] resource(String name)throws Exception{try(InputStream in=ShopSaverTest.class.getClassLoader().getResourceAsStream(name.replace('.','/')+".class");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);return out.toByteArray();}}
    public static class Client extends Minecraft {
        private Client(){super(null);}
        @Override public void displayGuiScreen(GuiScreen screen){
            ShopSaverModule module=dev.vibe.Vibe.getInstance().getModuleManager().getModule(ShopSaverModule.class);
            if(module!=null)screen=module.opening(screen);
            if(currentScreen!=null)currentScreen.onGuiClosed();currentScreen=screen;
            if(screen!=null)try{PathTestFixture.set(GuiScreen.class,screen,"mc",this);}catch(Exception error){throw new AssertionError(error);}
        }
    }
    public static class Chest extends GuiChest {
        int closes;
        Chest(net.minecraft.inventory.IInventory player,net.minecraft.inventory.IInventory chest){super(player,chest);}
        @Override public void onGuiClosed(){if(!ShopSaverModule.keepContainerHook(this)){closes++;super.onGuiClosed();}}
    }
}
