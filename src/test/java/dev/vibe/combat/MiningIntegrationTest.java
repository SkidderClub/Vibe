package dev.vibe.combat;

import dev.vibe.Vibe;
import dev.vibe.core.*;
import dev.vibe.module.impl.world.FastBreakModule;
import dev.vibe.setting.*;
import dev.vibe.statistics.StatisticsService;
import java.io.*;
import java.lang.reflect.*;
import java.net.JarURLConnection;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.*;
import org.junit.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.Assert.*;

/** Execute vanilla mining and BedAura input through the same patched controller used with OptiFine. */
public class MiningIntegrationTest {
    @Rule public org.junit.rules.TemporaryFolder temporary=new org.junit.rules.TemporaryFolder();

    @Test public void speedScalesEachNativeIncrementAndFinishFasterCompletesAtItsThreshold()throws Exception{
        for(String mode:new String[]{"Speed","FinishFaster"})try(BedAuraPacketsTest.Fixture f=new BedAuraPacketsTest.Fixture()){
            FastBreakModule fast=install(f,mode);f.bedBlock.damage=.1F;
            number(fast,"Speed Multiplier",2);number(fast,"Finish At (%)",60);
            f.step(false,false);assertEquals(mode.equals("Speed")?.2F:.1F,progress(f),.00001F);
            f.step(false,false);assertEquals(mode.equals("Speed")?.4F:.2F,progress(f),.00001F);
            int ticks=2;while(!f.world.isAirBlock(f.bed)&&ticks<20){f.step(false,false);ticks++;}
            assertEquals(mode,mode.equals("Speed")?5:6,ticks);
            assertEquals(1,f.queue.packets.stream().filter(p->p instanceof C07PacketPlayerDigging&&((C07PacketPlayerDigging)p).getStatus()==C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK).count());
            assertTrue(f.controller.getClass().getSuperclass()==PlayerControllerMP.class);
        }
    }
    @Test public void exclusionsAndToolRequirementKeepVanillaMiningAndDisabledModeDoesNotAccelerate()throws Exception{
        for(int condition=0;condition<3;condition++)try(BedAuraPacketsTest.Fixture f=new BedAuraPacketsTest.Fixture()){
            FastBreakModule fast=install(f,"Speed");f.bedBlock.damage=.1F;number(fast,"Speed Multiplier",4);
            if(condition==0)((MultiSelectSetting)setting(fast,"Not On")).toggle("Bed");
            if(condition==1)((BooleanSetting)setting(fast,"Only With Tools")).setEnabled(true);
            if(condition==2)fast.setEnabled(false);
            f.step(false,false);assertEquals(.1F,progress(f),.00001F);
        }
    }
    @Test public void manualAndEveryBedAuraModeCountExactlyOneBedWithDelayedDigPackets()throws Exception{
        for(String mode:new String[]{"Manual","Raycast","Vanilla","Hypixel"})for(String breaking:new String[]{"Speed","FinishFaster"})for(String switching:new String[]{"Basic","Silent"})
            try(BedAuraPacketsTest.Fixture f=new BedAuraPacketsTest.Fixture()){
                FastBreakModule fast=install(f,breaking);((BooleanSetting)setting(fast,"Only With Tools")).setEnabled(true);f.aura.switching.mode.setValue(switching);f.bedBlock.damage=.1F;boolean automated=!mode.equals("Manual");if(automated)f.aura.getMode().setValue(mode);
                f.player.inventory.mainInventory[3]=new ItemStack(Items.iron_axe);f.player.inventory.currentItem=automated?0:3;
                StatisticsService stats=new StatisticsService(f.mc,temporary.newFolder().toPath());dev.vibe.path.PathTestFixture.set(Vibe.class,Vibe.getInstance(),"statistics",stats);stats.tick();
                List<StatisticsService.Activity> events=new ArrayList<>();stats.addActivityListener((action,pos,target)->events.add(action));
                if(!automated)stats.watchBlockBreak(f.bed);
                for(int i=0;i<30&&!f.world.isAirBlock(f.bed);i++){f.step(automated,false);stats.tick();}
                assertTrue(mode,f.world.isAirBlock(f.bed));
                // Netty can deliver STOP after the local world is already air.
                for(net.minecraft.network.Packet<?> packet:f.queue.packets)if(packet instanceof C07PacketPlayerDigging)stats.recordDigging((C07PacketPlayerDigging)packet);
                stats.tick();stats.tick();
                assertEquals(mode+" "+breaking,1,stats.snapshot(StatisticsService.Scope.GLOBAL).bedsBroken);
                assertEquals(1,stats.snapshot(StatisticsService.Scope.ACCOUNT).bedsBroken);assertEquals(1,stats.snapshot(StatisticsService.Scope.GLOBAL).blocksBroken);
                assertEquals(1,Collections.frequency(events,StatisticsService.Activity.BED_BROKEN));
            }
    }
    @Test public void realRawAndMappedControllerBodiesHaveOneProgressHookAndValidFrames()throws Exception{
        verify("net.minecraft.client.multiplayer.PlayerControllerMP",resource());
        Path path=Paths.get(((JarURLConnection)getClass().getClassLoader().getResource("net/minecraft/client/Minecraft.class").openConnection()).getJarFileURL().toURI());JarFile original=null;
        for(Path dir=path.getParent();dir!=null&&original==null;dir=dir.getParent())try(DirectoryStream<Path> jars=Files.newDirectoryStream(dir,"*merged+MinecraftForge-FG2+forge*-official.jar")){for(Path jar:jars){original=new JarFile(jar.toFile());break;}}
        assertNotNull(original);try(JarFile jar=original){verify("bda",read(jar.getInputStream(jar.getJarEntry("bda.class"))));}
    }
    private static FastBreakModule install(BedAuraPacketsTest.Fixture f,String mode)throws Exception{
        f.controller=controller(f.mc,f.queue);f.mc.playerController=f.controller;FastBreakModule fast=new FastBreakModule();f.modules.registerDynamic(fast);fast.setEnabled(true);
        ((ModeSetting)setting(fast,"Mode")).setValue(mode);((BooleanSetting)setting(fast,"Only With Tools")).setEnabled(false);return fast;
    }
    private static Setting<?> setting(FastBreakModule m,String name){for(Setting<?> s:m.getSettings())if(s.getRawName().equals(name))return s;throw new AssertionError(name);}
    private static void number(FastBreakModule m,String name,double value){((NumberSetting)setting(m,name)).setValue(value);}
    private static float progress(BedAuraPacketsTest.Fixture f)throws Exception{Field field=f.controller.getClass().getDeclaredField("curBlockDamageMP");field.setAccessible(true);return field.getFloat(f.controller);}
    private static void verify(String raw,byte[] bytes)throws Exception{
        String full="net.minecraft.client.multiplayer.PlayerControllerMP";byte[] scoped=new MoveFixTransformer().transform(raw,full,bytes);BlockBreakingTransformer transformer=new BlockBreakingTransformer();byte[] patched=transformer.transform(raw,full,scoped);
        assertArrayEquals(patched,transformer.transform(raw,full,patched));ClassNode node=new ClassNode();new ClassReader(patched).accept(node,0);int steps=0,progress=0,delay=0,begins=0;
        for(Object value:node.methods){MethodNode method=(MethodNode)value;new Analyzer(new BasicVerifier()).analyze(node.name,method);
            for(AbstractInsnNode i:method.instructions.toArray())if(i instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)i;
                if(call.owner.endsWith("FastBreakModule")){assertTrue(method.name.startsWith("vibe$tool$"));if(call.name.equals("stepHook"))steps++;if(call.name.equals("progressHook"))progress++;if(call.name.equals("delayHook"))delay++;}
                if(call.owner.endsWith("BlockBreakHooks")&&call.name.equals("begin"))begins++;
            }
        }
        assertEquals(1,steps);assertEquals(1,progress);assertEquals(1,delay);assertEquals(1,begins);
    }
    private static PlayerControllerMP controller(Minecraft mc,NetHandlerPlayClient queue)throws Exception{
        String parent="net/minecraft/client/multiplayer/PlayerControllerMP",name="dev/vibe/combat/NativeMiningController";
        byte[] scoped=new MoveFixTransformer().transform(parent.replace('/','.'),parent.replace('/','.'),resource());ClassNode node=new ClassNode();new ClassReader(new BlockBreakingTransformer().transform(parent.replace('/','.'),parent.replace('/','.'),scoped)).accept(node,0);
        node.name=name;node.superName=parent;
        for(Object value:node.methods){MethodNode method=(MethodNode)value;method.desc=method.desc.replace("L"+parent+";","L"+name+";");
            for(AbstractInsnNode i:method.instructions.toArray()){
                if(i instanceof FieldInsnNode){FieldInsnNode field=(FieldInsnNode)i;if(field.owner.equals(parent))field.owner=name;}
                else if(i instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)i;if(call.owner.equals(parent)){call.owner=name;call.desc=call.desc.replace("L"+parent+";","L"+name+";");}
                    if(method.name.equals("<init>")&&call.owner.equals("java/lang/Object")&&call.name.equals("<init>")){InsnList args=new InsnList();args.add(new VarInsnNode(Opcodes.ALOAD,1));args.add(new VarInsnNode(Opcodes.ALOAD,2));method.instructions.insertBefore(call,args);call.owner=parent;call.desc="(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/network/NetHandlerPlayClient;)V";}}
                else if(i instanceof FrameNode){FrameNode frame=(FrameNode)i;if(frame.local!=null)Collections.replaceAll(frame.local,parent,name);if(frame.stack!=null)Collections.replaceAll(frame.stack,parent,name);}
            }
        }
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);byte[] bytes=writer.toByteArray();
        Class<?> type=new ClassLoader(MiningIntegrationTest.class.getClassLoader()){Class<?> define(){return defineClass(name.replace('/','.'),bytes,0,bytes.length);}}.define();
        return (PlayerControllerMP)type.getConstructor(Minecraft.class,NetHandlerPlayClient.class).newInstance(mc,queue);
    }
    private static byte[] resource()throws Exception{return read(PlayerControllerMP.class.getResourceAsStream("PlayerControllerMP.class"));}
    private static byte[] read(InputStream source)throws Exception{try(InputStream in=source;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);return out.toByteArray();}}
}
