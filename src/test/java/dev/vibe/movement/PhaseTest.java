package dev.vibe.movement;
import dev.vibe.Vibe;
import dev.vibe.module.impl.movement.PhaseModule;
import dev.vibe.core.PhaseTransformer;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import java.io.*;
import java.util.jar.JarFile;
import net.minecraft.init.Blocks;
import net.minecraft.network.*;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.*;
import net.minecraft.util.*;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.Assert.*;
public class PhaseTest {
    @Test public void watchdogCageQueuesMovementKeepsControlPacketsAndReleasesOnTheStartBanner()throws Exception{
        try(RewoundModesTest.Fixture f=new RewoundModesTest.Fixture()){
            PhaseModule phase=new PhaseModule();Vibe.getInstance().getModuleManager().registerDynamic(phase);f.player.ticksExisted=31;phase.setEnabled(true);
            phase.receive(new S45PacketTitle(S45PacketTitle.Type.TITLE,new ChatComponentText("§c3")));phase.tick();phase.preUpdate(f.player);
            EmbeddedChannel channel=new EmbeddedChannel(new ChannelDuplexHandler(){@Override public void write(ChannelHandlerContext ctx,Object packet,ChannelPromise promise){if(!phase.outbound((Packet<?>)packet,ctx,promise))ctx.write(packet,promise);}});
            C03PacketPlayer movement=new C03PacketPlayer();assertFalse(channel.writeOutbound(movement));assertEquals(1,phase.queuedPackets());
            C00PacketKeepAlive alive=new C00PacketKeepAlive();C01PacketChatMessage chat=new C01PacketChatMessage("hello");C0FPacketConfirmTransaction confirm=new C0FPacketConfirmTransaction();
            channel.writeOutbound(alive,chat,confirm);assertSame(alive,channel.readOutbound());assertSame(chat,channel.readOutbound());assertSame(confirm,channel.readOutbound());
            phase.receive(new S02PacketChat(new ChatComponentText("▬▬▬▬▬▬▬▬▬▬▬")));phase.tick();channel.runPendingTasks();assertSame(movement,channel.readOutbound());assertEquals(0,phase.queuedPackets());
            phase.setEnabled(false);channel.finish();
        }
    }
    @Test public void ncpOwnsOnlyItsLocalCollisionAndMotionStateAndRestoresItWhenDisabled()throws Exception{
        try(RewoundModesTest.Fixture f=new RewoundModesTest.Fixture()){
            f.position(63.5);f.player.jumpMovementFactor=.027F;
            PhaseModule phase=new PhaseModule();Vibe.getInstance().getModuleManager().registerDynamic(phase);phase.mode.setValue("NCP");phase.setEnabled(true);
            assertTrue(f.player.noClip);assertEquals(0,f.player.jumpMovementFactor,0);assertTrue(PhaseModule.cancelPush(f.player));assertFalse(PhaseModule.cancelPush(new Object()));
            List<AxisAlignedBB> boxes=Arrays.asList(new AxisAlignedBB(-1,62,-1,1,63,1),new AxisAlignedBB(-1,63,-1,1,65,1));
            assertEquals(1,PhaseModule.collisions(boxes,f.player).size());assertSame(boxes,PhaseModule.collisions(boxes,new Object()));
            double before=f.player.posZ;for(int i=0;i<3;i++)phase.beforeWalking(f.player);assertEquals(.617,f.player.posZ-before,1E-8);
            phase.setEnabled(false);assertFalse(f.player.noClip);assertEquals(.027F,f.player.jumpMovementFactor,0);assertSame(boxes,PhaseModule.collisions(boxes,f.player));
        }
    }
    @Test public void nativeMappedAndOfficialCollisionAndPushHooksVerifyAndAreIdempotent()throws Exception{
        check("net.minecraft.world.World","net.minecraft.world.World",read(getClass().getClassLoader().getResourceAsStream("net/minecraft/world/World.class")),true);
        check("net.minecraft.client.entity.EntityPlayerSP","net.minecraft.client.entity.EntityPlayerSP",read(getClass().getClassLoader().getResourceAsStream("net/minecraft/client/entity/EntityPlayerSP.class")),false);
        java.lang.reflect.Method method=RewoundHooksTest.class.getDeclaredMethod("officialJar");method.setAccessible(true);
        try(JarFile jar=(JarFile)method.invoke(new RewoundHooksTest())){
            check("adm","net.minecraft.world.World",read(jar.getInputStream(jar.getJarEntry("adm.class"))),true);
            check("bew","net.minecraft.client.entity.EntityPlayerSP",read(jar.getInputStream(jar.getJarEntry("bew.class"))),false);
        }
    }
    private void check(String raw,String mapped,byte[] bytes,boolean world)throws Exception{
        PhaseTransformer transformer=new PhaseTransformer();byte[] result=transformer.transform(raw,mapped,bytes);assertArrayEquals(result,transformer.transform(raw,mapped,result));ClassNode node=new ClassNode();new ClassReader(result).accept(node,0);int hooks=0;
        for(Object object:node.methods){MethodNode method=(MethodNode)object;new Analyzer(new BasicVerifier()).analyze(node.name,method);for(AbstractInsnNode ins:method.instructions.toArray())if(ins instanceof MethodInsnNode&&((MethodInsnNode)ins).owner.equals("dev/vibe/module/impl/movement/PhaseModule"))hooks++;}
        assertTrue(world?hooks>0:hooks==1);
    }
    private static byte[] read(InputStream input)throws Exception{try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];for(int n;(n=in.read(buffer))!=-1;)out.write(buffer,0,n);return out.toByteArray();}}
}
