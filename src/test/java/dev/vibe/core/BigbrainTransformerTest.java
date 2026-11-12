package dev.vibe.core;

import java.io.*;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;

public class BigbrainTransformerTest {
    @Test public void nativeSkinArmourAndAttachmentsAreHookedExactlyOnce()throws Exception{
        BigbrainTransformer transformer=new BigbrainTransformer();
        String[] classes={"net.minecraft.client.model.ModelBiped","net.minecraft.client.model.ModelPlayer","net.minecraft.client.renderer.entity.layers.LayerHeldItem","net.minecraft.client.renderer.entity.layers.LayerCustomHead","net.minecraft.client.renderer.entity.layers.LayerCape"};
        String[] methods={"renderPart","renderPart","postRenderArm","postRenderHead","beginCape"};
        for(int i=0;i<classes.length;i++){byte[] source=original(classes[i]),changed=transformer.transform(classes[i],classes[i],source);assertTrue(classes[i],calls(changed,null,methods[i])>0);assertArrayEquals(changed,transformer.transform(classes[i],classes[i],changed));}
        byte[] player=transformer.transform(classes[1],classes[1],original(classes[1]));assertEquals(1,calls(player,null,"renderAttachment"));
        assertEquals("First-person arms retain their native proportions",0,calls(player,"renderRightArm","renderPart"));assertEquals(0,calls(player,"renderLeftArm","renderPart"));
    }
    @Test public void playsAlongsideTheExistingPoseTransformerInEitherOrder()throws Exception{
        String name="net.minecraft.client.model.ModelPlayer";MemeAnimationTransformer poses=new MemeAnimationTransformer();BigbrainTransformer scale=new BigbrainTransformer();
        for(byte[] transformed:new byte[][]{scale.transform(name,name,poses.transform(name,name,original(name))),poses.transform(name,name,scale.transform(name,name,original(name)))}){assertEquals(1,callsOwner(transformed,"dev/vibe/features/EmotePoses","before"));assertTrue(calls(transformed,null,"renderPart")>0);}
    }
    private static byte[] original(String name)throws Exception{try(InputStream input=BigbrainTransformerTest.class.getClassLoader().getResourceAsStream(name.replace('.','/')+".class");ByteArrayOutputStream out=new ByteArrayOutputStream()){assertNotNull(input);byte[] buffer=new byte[8192];for(int n;(n=input.read(buffer))>0;)out.write(buffer,0,n);return out.toByteArray();}}
    private static int calls(byte[] bytes,String method,String hook){return count(bytes,method,"dev/vibe/module/impl/meme/BigbrainModule",hook);}
    private static int callsOwner(byte[] bytes,String owner,String hook){return count(bytes,null,owner,hook);}
    private static int count(byte[] bytes,String method,String owner,String hook){ClassNode c=new ClassNode();new ClassReader(bytes).accept(c,0);int count=0;for(Object value:c.methods){MethodNode m=(MethodNode)value;if(method!=null&&!m.name.equals(method))continue;for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)i;if(call.owner.equals(owner)&&call.name.equals(hook))count++;}}return count;}
}
