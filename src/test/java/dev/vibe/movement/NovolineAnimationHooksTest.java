package dev.vibe.movement;

import dev.vibe.core.NovolineAnimationTransformer;
import java.io.*;
import java.net.JarURLConnection;
import java.nio.file.*;
import java.util.jar.JarFile;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.Assert.*;

public class NovolineAnimationHooksTest {
    @Test public void exactNativeBlockSitesVerifyInMcpAndObfuscatedForge()throws Exception{
        String type="net.minecraft.client.renderer.ItemRenderer";
        check(type,type,read(getClass().getClassLoader().getResourceAsStream(type.replace('.','/')+".class")));
        Path directory=Paths.get(((JarURLConnection)getClass().getClassLoader().getResource("net/minecraft/client/Minecraft.class").openConnection()).getJarFileURL().toURI()).getParent();
        for(int i=0;i<4&&directory!=null;i++,directory=directory.getParent())try(DirectoryStream<Path> jars=Files.newDirectoryStream(directory,"*merged+MinecraftForge-FG2+forge*-official.jar")){
            for(Path path:jars)try(JarFile jar=new JarFile(path.toFile())){check("bfn",type,read(jar.getInputStream(jar.getJarEntry("bfn.class"))));return;}
        }
        fail("Missing official Forge jar");
    }
    private void check(String raw,String mapped,byte[] original)throws Exception{
        NovolineAnimationTransformer transformer=new NovolineAnimationTransformer();byte[] result=transformer.transform(raw,mapped,original);
        assertArrayEquals(result,transformer.transform(raw,mapped,result));ClassNode node=new ClassNode();new ClassReader(result).accept(node,0);int pose=0,block=0;
        for(Object value:node.methods){MethodNode method=(MethodNode)value;new Analyzer(new BasicVerifier()).analyze(node.name,method);
            for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)instruction;if(call.owner.equals("dev/vibe/ui/render/NovolineAnimations")){if(call.name.equals("transform"))pose++;if(call.name.equals("active"))block++;}}
        }assertEquals(1,pose);assertEquals(1,block);
    }
    private static byte[] read(InputStream source)throws Exception{assertNotNull(source);try(InputStream in=source;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];for(int n;(n=in.read(buffer))!=-1;)out.write(buffer,0,n);return out.toByteArray();}}
}
