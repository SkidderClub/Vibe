package dev.vibe.ui.render;

import dev.vibe.core.ChunkAnimationTransformer;
import java.io.*;
import java.net.JarURLConnection;
import java.nio.file.*;
import java.util.jar.JarFile;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.Assert.*;

/** Verify real mapped and release Forge bytecode, including both vanilla terrain renderers. */
public class ChunkAnimationHooksTest {
    private static final String CHUNK="net.minecraft.client.renderer.chunk.RenderChunk", CONTAINER="net.minecraft.client.renderer.ChunkRenderContainer";
    @Test public void mappedHooksAndVboDisplayListPathsHaveValidStacks() throws Exception {
        verify(CHUNK,CHUNK,resource(CHUNK),true);verify(CONTAINER,CONTAINER,resource(CONTAINER),false);
        assertDrawPath(resource("net.minecraft.client.renderer.RenderList"),"preRenderChunk");
        assertDrawPath(resource("net.minecraft.client.renderer.VboRenderList"),"preRenderChunk");
    }
    @Test public void releaseHooksMatchTheActualObfuscatedGame() throws Exception {
        try(JarFile jar=official()){
            verify("bht",CHUNK,read(jar.getInputStream(jar.getJarEntry("bht.class"))),true);
            verify("bfh",CONTAINER,read(jar.getInputStream(jar.getJarEntry("bfh.class"))),false);
        }
    }
    private static void verify(String raw,String mapped,byte[] source,boolean chunk) throws Exception {
        ChunkAnimationTransformer transformer=new ChunkAnimationTransformer();byte[] output=transformer.transform(raw,mapped,source);
        ClassNode node=new ClassNode();new ClassReader(output).accept(node,0);
        int resets=0,compiled=0,discarded=0,applies=0;
        for(Object entry:node.methods){MethodNode method=(MethodNode)entry;
            if((method.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0)new Analyzer(new BasicVerifier()).analyze(node.name,method);
            for(AbstractInsnNode insn:method.instructions.toArray())if(insn instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)insn;
                if(!call.owner.equals("dev/vibe/ui/render/ChunkAnimationHooks"))continue;
                if(call.name.equals("resetPosition"))resets++;if(call.name.equals("compiled"))compiled++;if(call.name.equals("discarded"))discarded++;if(call.name.equals("apply"))applies++;
            }
        }
        if(chunk){assertTrue(node.interfaces.contains("dev/vibe/ui/render/ChunkAnimationState"));assertEquals(1,resets);assertEquals(1,compiled);assertEquals(1,discarded);
            FieldNode timestamp=null;for(Object field:node.fields)if(((FieldNode)field).name.equals("vibe$animationStart"))timestamp=(FieldNode)field;
            assertNotNull(timestamp);assertEquals("J",timestamp.desc);assertTrue((timestamp.access&Opcodes.ACC_VOLATILE)!=0);
        }else assertEquals(1,applies);
        assertArrayEquals(output,transformer.transform(raw,mapped,output));
    }
    private static void assertDrawPath(byte[] bytes,String name){ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);int count=0;
        for(Object entry:node.methods)for(AbstractInsnNode insn:((MethodNode)entry).instructions.toArray())if(insn instanceof MethodInsnNode&&((MethodInsnNode)insn).name.equals(name))count++;
        assertEquals(1,count);
    }
    private static byte[] resource(String name)throws IOException{return read(ChunkAnimationHooksTest.class.getClassLoader().getResourceAsStream(name.replace('.','/')+".class"));}
    private static byte[] read(InputStream source)throws IOException{assertNotNull(source);try(InputStream input=source;ByteArrayOutputStream output=new ByteArrayOutputStream()){
        byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1)output.write(buffer,0,count);return output.toByteArray();}}
    private static JarFile official()throws Exception{
        Path path=Paths.get(((JarURLConnection)ChunkAnimationHooksTest.class.getClassLoader().getResource("net/minecraft/client/Minecraft.class").openConnection()).getJarFileURL().toURI());
        for(Path directory=path.getParent();directory!=null;directory=directory.getParent())try(DirectoryStream<Path> files=Files.newDirectoryStream(directory,"*merged+MinecraftForge-FG2+forge*-official.jar")){
            for(Path file:files)return new JarFile(file.toFile());
        }
        throw new IOException("Original Forge jar not found");
    }
}
