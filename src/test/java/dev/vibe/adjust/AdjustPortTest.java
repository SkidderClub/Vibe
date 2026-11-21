package dev.vibe.adjust;
import dev.vibe.inventory.*;
import dev.vibe.module.impl.client.*;
import dev.vibe.module.impl.movement.*;
import dev.vibe.path.PathTestFixture;
import dev.vibe.setting.*;
import dev.vibe.hud.ChatActions;
import dev.vibe.movement.AdjustJesus;
import java.io.*;
import java.net.JarURLConnection;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.Assert.*;

public class AdjustPortTest {
    @Test public void stepMeasuresOneOneAndHalfAndTwoBlocksAndKeepsOriginalOffsets()throws Exception{
        for(int height=2;height<=4;height++)try(AutomationFixture f=new AutomationFixture()){
            CollisionWorld w=PathTestFixture.allocate(CollisionWorld.class);w.blocks=new HashMap<>(f.base.world.blocks);w.loaded=true;f.mc.theWorld=w;f.player.worldObj=w;f.player.width=.6F;
            w.blocks.put(new BlockPos(1,64,0),Blocks.stone.getDefaultState());if(height>=3)w.blocks.put(new BlockPos(1,65,0),(height==3?Blocks.stone_slab:Blocks.stone).getDefaultState());
            f.player.motionX=.8;f.player.motionY=-.0784;f.player.setEntityBoundingBox(new AxisAlignedBB(.4,64,.2,1,65.8,.8));
            net.minecraft.util.Timer timer=PathTestFixture.allocate(net.minecraft.util.Timer.class);PathTestFixture.set(net.minecraft.util.Timer.class,timer,"timerSpeed",1F);PathTestFixture.set(Minecraft.class,f.mc,"timer",timer);
            dev.vibe.movement.AdjustStep step=new dev.vibe.movement.AdjustStep();step.reset(f.player);PathTestFixture.set(dev.vibe.movement.AdjustStep.class,step,"lastStep",0L);assertEquals(2,step.prepare(2.5F),0);
            assertTrue("A predicted step must not send offsets", f.queue.packets.isEmpty());
            assertEquals(1, dev.vibe.combat.CombatTimerAccess.getSpeed(), 0);
            step.confirm();
            assertTrue("Vanilla can reject the step", f.queue.packets.isEmpty());
            assertEquals(1, dev.vibe.combat.CombatTimerAccess.getSpeed(), 0);
            assertEquals(2, step.prepare(2.5F), 0);
            f.player.setEntityBoundingBox(f.player.getEntityBoundingBox().offset(.8, height / 2.0, 0));
            step.confirm();
            assertEquals(height==2?2:height==3?6:8,f.queue.packets.size());net.minecraft.network.play.client.C03PacketPlayer first=(net.minecraft.network.play.client.C03PacketPlayer)f.queue.packets.get(0);assertEquals(64+.42,first.getPositionY(),.000001);assertFalse(first.isOnGround());
            float speed=height==2?.7F:height==3?.35F:.25F;assertEquals(speed,dev.vibe.combat.CombatTimerAccess.getSpeed(),0);step.preUpdate();assertEquals(speed,dev.vibe.combat.CombatTimerAccess.getSpeed(),0);step.preUpdate();step.preUpdate();assertEquals(1,dev.vibe.combat.CombatTimerAccess.getSpeed(),0);step.reset(null);
        }
    }
    public static class CollisionWorld extends PathTestFixture.FixtureWorld{
        private CollisionWorld(){super();}
        @Override public List<AxisAlignedBB> getCollidingBoundingBoxes(net.minecraft.entity.Entity entity,AxisAlignedBB query){List<AxisAlignedBB> result=new ArrayList<>();for(Map.Entry<BlockPos,net.minecraft.block.state.IBlockState> e:blocks.entrySet())e.getValue().getBlock().addCollisionBoxesToList(this,e.getKey(),e.getValue(),query,result,entity);return result;}
    }
    @Test public void inventoryManagerDoesNotPlanOrAttemptProtectedDrops()throws Exception{
        try(AutomationFixture f=new AutomationFixture()){
            QolModule qol=new QolModule();f.base.modules.registerDynamic(qol);qol.setEnabled(true);qol.dropConfirm.setValue(true);
            dev.vibe.module.impl.world.InventoryManagerModule manager=new dev.vibe.module.impl.world.InventoryManagerModule();f.base.modules.registerDynamic(manager);
            InventoryEditorModule editor=new InventoryEditorModule();f.base.modules.registerDynamic(editor);InventoryLayout layout=new InventoryLayout();layout.set(40,InventoryLayout.Type.SWORD);editor.setLayout(layout);
            for(Setting<?> setting:manager.getSettings())if(setting instanceof RangeSetting)((RangeSetting)setting).setRange(0,0);
            f.player.inventory.mainInventory[9]=new ItemStack(Items.diamond_sword);f.mc.currentScreen=PathTestFixture.allocate(net.minecraft.client.gui.inventory.GuiInventory.class);manager.setEnabled(true);
            for(int i=0;i<12;i++){f.player.ticksExisted++;manager.tick();}assertTrue("Protected stacks must never reach the window-click dispatcher",f.queue.packets.isEmpty());assertNotNull(f.player.inventory.mainInventory[9]);
            qol.dropConfirm.setValue(false);for(int i=0;i<4;i++){f.player.ticksExisted++;manager.tick();}assertNull("Without confirmation the configured drop bin must still work",f.player.inventory.mainInventory[9]);manager.setEnabled(false);
        }
    }
    @Test public void antiAimIsFallbackAndCombatOwnsTheNextInput() throws Exception {
        try(AutomationFixture f=new AutomationFixture()){
            MoveFixModule fix=new MoveFixModule();AntiAimModule anti=new AntiAimModule();f.base.modules.registerDynamic(fix);f.base.modules.registerDynamic(anti);fix.setEnabled(true);anti.setEnabled(true);
            f.player.rotationYaw=20;fix.beginRotationTick();anti.applyFallback();assertTrue(fix.ownsRotation("antiaim"));assertEquals(-160,fix.getRotationYaw(),.001);assertEquals(90,fix.getRotationPitch(),0);
            f.player.inventory.mainInventory[0] = new ItemStack(Items.potionitem, 1, 16386);
            anti.applyFallback();
            assertFalse(fix.ownsRotation("antiaim"));
            assertEquals(20, fix.getRotationYaw(), 0);
            f.player.inventory.mainInventory[0] = null;
            fix.beginRotationTick();assertFalse("A fallback must not block another producer's ownership check",fix.hasOtherRotationOwner("killaura"));
            fix.setFakeRotation("killaura",41,17);anti.applyFallback();assertTrue(fix.ownsRotation("killaura"));assertEquals(0,MathHelper.wrapAngleTo180_float(fix.getRotationYaw()-41),.001);assertEquals(17,fix.getRotationPitch(),0);
            fix.clearFakeRotation("killaura");anti.pitch.setValue("Down Headless");anti.applyFallback();assertEquals(-180,fix.getRotationPitch(),0);
            anti.setEnabled(false);assertFalse(fix.ownsRotation("antiaim"));
        }
    }
    @Test public void confirmUsefulDropsBeforeMutationAndExpireOnSlotChange() throws Exception {
        try(AutomationFixture f=new AutomationFixture()){
            QolModule qol=new QolModule();f.base.modules.registerDynamic(qol);qol.setEnabled(true);qol.dropConfirm.setValue(true);
            ItemStack sword=new ItemStack(Items.diamond_sword);f.player.inventory.mainInventory[0]=sword;
            assertFalse(DropConfirmation.allowDrop(f.player,false));assertSame(sword,f.player.getHeldItem());assertTrue(DropConfirmation.allowDrop(f.player,false));
            f.player.inventory.mainInventory[1]=new ItemStack(Items.iron_sword);f.player.inventory.currentItem=1;assertFalse(DropConfirmation.allowDrop(f.player,true));assertTrue(DropConfirmation.allowDrop(f.player,true));
            f.player.inventory.mainInventory[2]=new ItemStack(Items.stick);f.player.inventory.currentItem=2;assertTrue(DropConfirmation.allowDrop(f.player,true));
            f.player.inventory.currentItem=0;assertFalse(DropConfirmation.allowClick(0,36,1,4,f.player));assertTrue(DropConfirmation.allowClick(0,36,1,4,f.player));
            qol.setEnabled(false);assertTrue(DropConfirmation.allowDrop(f.player,true));
        }
    }
    @Test public void noFallUsesAccumulatedDistanceAndJumpPotionThreshold() throws Exception {
        try(AutomationFixture f=new AutomationFixture()){
            NoFallModule n=new NoFallModule();f.base.modules.registerDynamic(n);((ModeSetting)n.getSettings().get(0)).setValue("RewoundAdjust");n.setEnabled(true);
            f.player.fallDistance=2.3F;f.player.motionY=-.4;n.beforeWalking(f.player);assertFalse(n.walkingGround(f.player,false));
            f.player.fallDistance=2.7F;n.beforeWalking(f.player);assertTrue(n.walkingGround(f.player,false));assertEquals(2.7F,f.player.fallDistance,0);n.afterWalking();
            f.player.fallDistance=4;n.beforeWalking(f.player);assertFalse(n.walkingGround(f.player,false));
            f.player.fallDistance=5.4F;n.beforeWalking(f.player);assertTrue(n.walkingGround(f.player,false));
            f.player.fallDistance=0;n.beforeWalking(f.player);f.player.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.jump.id,100,1));
            f.player.fallDistance=4.1F;n.beforeWalking(f.player);assertFalse(n.walkingGround(f.player,false));f.player.fallDistance=4.8F;n.beforeWalking(f.player);assertTrue(n.walkingGround(f.player,false));
            f.player.capabilities.allowFlying=true;f.player.fallDistance=20;n.beforeWalking(f.player);assertFalse(n.walkingGround(f.player,false));
        }
    }
    @Test public void liquidSurfaceHoverJumpAndStepMatchAdjustCycle() throws Exception {
        try(AutomationFixture f=new AutomationFixture()){
            JesusModule jesus=new JesusModule();f.base.modules.registerDynamic(jesus);jesus.mode.setValue("RewoundAdjust");jesus.setEnabled(true);f.base.block(0,63,0,Blocks.water.getDefaultState());
            AxisAlignedBB q=f.player.getEntityBoundingBox().offset(0,-.1,0);List<AxisAlignedBB> original=Collections.emptyList();List<AxisAlignedBB> boxes=AdjustJesus.collisions(original,f.player,q);
            assertEquals(1,boxes.size());assertEquals(64,boxes.get(0).maxY,0);assertEquals(0,original.size());assertTrue(AdjustJesus.cancelJump(f.player));assertEquals(.6F,AdjustJesus.stepHeight(2),0);
            double[] base={.000014,.000024,.000014,.000024,.000017};for(int i=0;i<10;i++){AdjustJesus.beforeWalking();double delta=AdjustJesus.walkingY(64)-64;assertEquals(base[i%5],delta,.0000005);}
            f.player.posY=63.5;assertSame(original,AdjustJesus.collisions(original,f.player,q));jesus.setEnabled(false);assertFalse(AdjustJesus.cancelJump(f.player));assertEquals(64,AdjustJesus.walkingY(64),0);
        }
    }
    @Test public void copyingAndRemovingWrappedMessagesRetainsOtherMessagesAndActions() throws Exception {
        try(AutomationFixture f=new AutomationFixture()){
            GuiNewChat chat=new GuiNewChat(f.mc);IChatComponent root=new ChatComponentText("§aHello ").appendSibling(new ChatComponentText("play").setChatStyle(new ChatStyle().setChatClickEvent(new net.minecraft.event.ClickEvent(net.minecraft.event.ClickEvent.Action.RUN_COMMAND,"/play"))));
            ChatLine first=new ChatLine(0,new ChatComponentText("§aHello "),0),second=new ChatLine(0,root.getSiblings().get(0),0),raw=new ChatLine(0,root,0),other=new ChatLine(0,new ChatComponentText("Other"),0);
            ChatActions.track(first,root);ChatActions.track(second,root);ChatActions.track(raw,root);List<ChatLine> drawn=new ArrayList<>(Arrays.asList(first,second,other)),history=new ArrayList<>(Arrays.asList(raw,other));
            PathTestFixture.set(GuiNewChat.class,chat,"drawnChatLines",drawn);PathTestFixture.set(GuiNewChat.class,chat,"chatLines",history);
            assertEquals("Hello play",ChatActions.copy(first,false,false));assertEquals("Hello ",ChatActions.copy(first,true,false));assertTrue(ChatActions.copy(first,false,true).contains("§a"));
            assertTrue(ChatActions.remove(chat,first,true));assertTrue(history.contains(raw));assertTrue(ChatActions.remove(chat,second,false));assertEquals(Collections.singletonList(other),drawn);assertEquals(Collections.singletonList(other),history);
            assertEquals("/play",root.getSiblings().get(0).getChatStyle().getChatClickEvent().getValue());
        }
    }
    @Test public void releaseAndMappedHooksHitActualMethodsAndVerifyStacks() throws Exception {
        String[] mapped={"net.minecraft.client.entity.EntityPlayerSP","net.minecraft.client.multiplayer.PlayerControllerMP","net.minecraft.world.World","net.minecraft.entity.EntityLivingBase"},raw={"bew","bda","adm","pr"};
        try(JarFile official=official()){
            for(int i=0;i<raw.length;i++)for(boolean obfuscated:new boolean[]{false,true}){
                byte[] bytes=obfuscated?read(official.getInputStream(official.getJarEntry(raw[i]+".class"))):read(getClass().getClassLoader().getResourceAsStream(mapped[i].replace('.','/')+".class"));
                dev.vibe.core.AdjustTransformer t=new dev.vibe.core.AdjustTransformer();byte[] result=t.transform(obfuscated?raw[i]:mapped[i],mapped[i],bytes);assertArrayEquals(result,t.transform(obfuscated?raw[i]:mapped[i],mapped[i],result));
                ClassNode c=new ClassNode();new ClassReader(result).accept(c,0);int count=0;for(Object value:c.methods){MethodNode m=(MethodNode)value;new Analyzer(new BasicVerifier()).analyze(c.name,m);for(AbstractInsnNode ins:m.instructions.toArray())if(ins instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)ins;if(call.owner.equals("dev/vibe/inventory/DropConfirmation")||call.owner.equals("dev/vibe/movement/AdjustJesus")){count++;if(i==0)assertEquals(obfuscated?"a":"dropOneItem",m.name);}}}assertTrue(mapped[i]+" has no hook",count>0);
            }
        }
        String name="net.minecraft.client.gui.GuiNewChat";dev.vibe.core.HudChatTransformer transformer=new dev.vibe.core.HudChatTransformer();byte[] patched=transformer.transform(name,name,read(getClass().getClassLoader().getResourceAsStream(name.replace('.','/')+".class")));ClassNode c=new ClassNode();new ClassReader(patched).accept(c,0);int tracks=0;for(Object value:c.methods){MethodNode m=(MethodNode)value;new Analyzer(new BasicVerifier()).analyze(c.name,m);for(AbstractInsnNode ins:m.instructions.toArray())if(ins instanceof MethodInsnNode&&((MethodInsnNode)ins).owner.equals("dev/vibe/hud/ChatActions"))tracks++;}assertEquals(2,tracks);
    }
    private JarFile official()throws Exception{Path path=Paths.get(((JarURLConnection)getClass().getClassLoader().getResource("net/minecraft/client/Minecraft.class").openConnection()).getJarFileURL().toURI());for(Path dir=path.getParent();dir!=null;dir=dir.getParent())try(DirectoryStream<Path> jars=Files.newDirectoryStream(dir,"*merged+MinecraftForge-FG2+forge*-official.jar")){for(Path jar:jars)return new JarFile(jar.toFile());}throw new AssertionError("Missing official Minecraft fixture");}
    private static byte[] read(InputStream in)throws Exception{try(InputStream input=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];for(int n;(n=input.read(b))!=-1;)out.write(b,0,n);return out.toByteArray();}}
}
