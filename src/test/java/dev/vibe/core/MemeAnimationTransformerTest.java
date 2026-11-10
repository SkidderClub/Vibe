package dev.vibe.core;

import java.io.*;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;

public class MemeAnimationTransformerTest {
    @Test public void hooksBothVanillaPoseClassesAndTheClickEntry()throws Exception{
        for(String name:new String[]{"net.minecraft.client.model.ModelBiped","net.minecraft.client.model.ModelPlayer","net.minecraft.client.Minecraft"}){
            byte[] source;try(InputStream in=getClass().getClassLoader().getResourceAsStream(name.replace('.','/')+".class");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);source=out.toByteArray();}
            byte[] transformed=new MemeAnimationTransformer().transform(name,name,source);ClassNode node=new ClassNode();new ClassReader(transformed).accept(node,0);int hooks=0;
            for(Object value:node.methods)for(AbstractInsnNode instruction:((MethodNode)value).instructions.toArray())if(instruction instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)instruction;if(call.owner.equals("dev/vibe/module/impl/meme/DabModule")||call.owner.equals("dev/vibe/module/impl/meme/GirlfriendModule"))hooks++;}
            assertTrue("No hook in "+name,hooks>0);
        }
    }
}
