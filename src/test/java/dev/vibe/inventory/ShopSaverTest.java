package dev.vibe.inventory;

import dev.vibe.core.ShopSaverTransformer;
import dev.vibe.module.impl.client.ShopSaverModule;
import dev.vibe.path.PathTestFixture;
import java.io.*;
import java.lang.reflect.*;
import java.net.JarURLConnection;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.*;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.*;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.Assert.*;

public class ShopSaverTest {
    @Test public void closeShopClosesTheLiveWindowWithoutTurningOffShopSaver() throws Exception {
        try (AutomationFixture f = new AutomationFixture()) {
            Client mc = client(f);
            ShopSaverModule module = new ShopSaverModule();
            f.base.modules.registerDynamic(module);
            module.setEnabled(true);
            Chest shop = chest(mc);
            mc.thePlayer.closeScreen();
            mc.displayGuiScreen(new GuiInventory(mc.thePlayer));
            assertSame(shop, mc.currentScreen);
            java.util.List<net.minecraft.client.gui.GuiButton> buttons = new java.util.ArrayList<>();
            shop.width = 320;
            shop.height = 240;
            dev.vibe.event.ChestControlsEvents controls = new dev.vibe.event.ChestControlsEvents();
            controls.init(new net.minecraftforge.client.event.GuiScreenEvent.InitGuiEvent.Post(shop, buttons));
            net.minecraft.client.gui.GuiButton close = buttons.get(0);
            assertEquals("Close Shop", close.displayString);
            assertTrue(close.visible);
            assertEquals(8, close.yPosition);
            controls.click(new net.minecraftforge.client.event.GuiScreenEvent.ActionPerformedEvent.Pre(shop, close, buttons) {
                @Override public boolean isCancelable() { return true; }
            });
            assertNull(mc.currentScreen);
            assertSame(mc.thePlayer.inventoryContainer, mc.thePlayer.openContainer);
            assertTrue(module.isEnabled());
            assertEquals(1, f.queue.packets.size());
            assertTrue(f.queue.packets.get(0) instanceof C0DPacketCloseWindow);
            assertTrue(module.opening(new GuiInventory(mc.thePlayer)) instanceof GuiInventory);
        }
    }
    @Test public void nativeDismissRetainsContainerAndCursorReopensLiveContentsAndDisablingClosesOnce() throws Exception {
        try (AutomationFixture f = new AutomationFixture()) {
            Client mc = client(f); ShopSaverModule module = new ShopSaverModule(); f.base.modules.registerDynamic(module); module.setEnabled(true);
            Chest chest = chest(mc); ItemStack carried = new ItemStack(Items.apple, 2); mc.thePlayer.inventory.setItemStack(carried);
            dev.vibe.ui.GuiInput.keyTyped(chest, '\0', 1);
            assertNull(mc.currentScreen); assertSame(chest.inventorySlots, mc.thePlayer.openContainer);
            assertSame(carried, mc.thePlayer.inventory.getItemStack()); assertEquals(0, chest.closes); assertTrue(f.queue.packets.isEmpty());
            chest.inventorySlots.putStackInSlot(19, new ItemStack(Items.diamond, 12));
            mc.displayGuiScreen(new GuiInventory(mc.thePlayer));
            assertSame(chest, mc.currentScreen); assertEquals(12, ((net.minecraft.inventory.ContainerChest)chest.inventorySlots).getLowerChestInventory().getStackInSlot(19).stackSize);
            dev.vibe.ui.GuiInput.keyTyped(chest, '\0', mc.gameSettings.keyBindInventory.getKeyCode());
            assertNull(mc.currentScreen); assertTrue(f.queue.packets.isEmpty());
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
    @Test public void suppliedShopPagesAreRecognizedButStorageAndOtherMenusCloseNormally() throws Exception {
        try (AutomationFixture f = new AutomationFixture()) {
            Client mc = client(f); ShopSaverModule module = new ShopSaverModule(); f.base.modules.registerDynamic(module); module.setEnabled(true);
            for (String title : new String[]{"Quick Buy", "Blocks", "Melee", "Armor", "Tools", "Ranged", "Potions", "Utility"}) {
                InventoryBasic inventory = shopInventory("shop-quick-buy.snbt", "§a"+title);
                Chest menu = chest(mc, inventory); assertTrue(title, ShopSaverModule.isShop((ContainerChest)menu.inventorySlots));
                module.tick(); assertSame("Enabling or ticking must not hide a shop", menu, mc.currentScreen);
                dev.vibe.ui.GuiInput.keyTyped(menu, '\0', 1); assertNull(mc.currentScreen); assertSame(menu.inventorySlots, mc.thePlayer.openContainer);
                mc.displayGuiScreen(new GuiInventory(mc.thePlayer)); assertSame(menu, mc.currentScreen);
                mc.thePlayer.closeScreenAndDropStack(); module.tick();
            }
            Chest utility = chest(mc, shopInventory("shop-utility.snbt", "Utility"));
            dev.vibe.ui.GuiInput.keyTyped(utility, '\0', 1); mc.displayGuiScreen(new GuiInventory(mc.thePlayer));assertSame(utility,mc.currentScreen);
            mc.thePlayer.closeScreenAndDropStack(); module.tick(); assertTrue(f.queue.packets.isEmpty());
            for (IInventory inventory : new IInventory[]{new InventoryBasic("container.chest",false,27),new InventoryBasic("container.chestDouble",false,54),new net.minecraft.inventory.InventoryEnderChest(),
                    new InventoryBasic("Quick Buy",true,54),new InventoryBasic("Blocks",true,54),shopInventory("shop-quick-buy.snbt","Other menu")}) {
                Chest storage=chest(mc,inventory);assertFalse(inventory.getName(),ShopSaverModule.isShop((ContainerChest)storage.inventorySlots));
                dev.vibe.ui.GuiInput.keyTyped(storage,'\0',1);assertNull(mc.currentScreen);assertSame(mc.thePlayer.inventoryContainer,mc.thePlayer.openContainer);
                assertEquals(1,storage.closes);mc.displayGuiScreen(new GuiInventory(mc.thePlayer));assertTrue(mc.currentScreen instanceof GuiInventory);
                mc.displayGuiScreen(null);
            }
            assertEquals("Every storage or unrelated menu must send its native close packet",6,f.queue.packets.size());
        }
    }
    @Test public void realReleaseHookInterceptsLocalCloseAndNeverWalkingOrServerClosure() throws Exception {
        Path path=Paths.get(((JarURLConnection)getClass().getClassLoader().getResource("net/minecraft/client/Minecraft.class").openConnection()).getJarFileURL().toURI());
        JarFile original=null;
        for(Path dir=path.getParent();dir!=null&&original==null;dir=dir.getParent())try(DirectoryStream<Path> jars=Files.newDirectoryStream(dir,"*merged+MinecraftForge-FG2+forge*-official.jar")){
            for(Path jar:jars){original=new JarFile(jar.toFile());break;}
        }
        assertNotNull(original);
        try(JarFile jar=original){
            byte[] raw;try(InputStream in=jar.getInputStream(jar.getJarEntry("bew.class"));ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);raw=out.toByteArray();
            }
            ShopSaverTransformer transformer=new ShopSaverTransformer();byte[] patched=transformer.transform("bew","net.minecraft.client.entity.EntityPlayerSP",raw);
            assertArrayEquals(patched,transformer.transform("bew","net.minecraft.client.entity.EntityPlayerSP",patched));int hooks=0;
            ClassNode node=node(patched);
            for(Object value:node.methods){MethodNode method=(MethodNode)value;new Analyzer(new BasicVerifier()).analyze(node.name,method);
                for(AbstractInsnNode i:method.instructions.toArray())if(i instanceof MethodInsnNode&&((MethodInsnNode)i).owner.endsWith("ShopSaverModule")){
                    assertEquals("Hook must belong to closeScreen, not onUpdateWalkingPlayer (p) or server close (q)","n",method.name);assertEquals("()V",method.desc);hooks++;
                    boolean closesWindow=false;for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof TypeInsnNode&&instruction.getOpcode()==Opcodes.NEW&&((TypeInsnNode)instruction).desc.equals("il"))closesWindow=true;
                    assertTrue("The intercepted method must be the one constructing C0DPacketCloseWindow",closesWindow);
                }
            }
            assertEquals(1,hooks);
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
        return chest(mc,shopInventory("shop-quick-buy.snbt","Quick Buy"));
    }
    private static Chest chest(Client mc,IInventory inventory) throws Exception {
        Chest chest=new Chest(mc.thePlayer.inventory,inventory);chest.inventorySlots.windowId=42;
        PathTestFixture.set(GuiScreen.class,chest,"mc",mc);mc.thePlayer.openContainer=chest.inventorySlots;mc.displayGuiScreen(chest);return chest;
    }
    private static InventoryBasic shopInventory(String resource,String title)throws Exception{
        byte[] bytes;try(InputStream in=ShopSaverTest.class.getResourceAsStream(resource);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);bytes=out.toByteArray();
        }
        NBTTagCompound root=(NBTTagCompound)JsonToNBT.getTagFromJson(new String(bytes,StandardCharsets.UTF_8).replace("\uFEFF",""));
        InventoryBasic inventory=new InventoryBasic(title,true,root.getInteger("Size"));NBTTagList items=root.getTagList("Items",10);
        for(int i=0;i<items.tagCount();i++){NBTTagCompound item=items.getCompoundTagAt(i);inventory.setInventorySlotContents(item.getByte("Slot")&255,ItemStack.loadItemStackFromNBT(item));}
        return inventory;
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
