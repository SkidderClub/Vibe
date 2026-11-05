package dev.vibe.ambience;

import dev.vibe.core.*;
import java.io.*;
import java.net.JarURLConnection;
import java.nio.file.*;
import java.util.jar.JarFile;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.Assert.*;

public class AmbienceHooksTest {
    @Test public void realForgeSkyRetainsItsPipelineAndWrapsExactlySunAndMoon()throws Exception{
        String type="net.minecraft.client.renderer.RenderGlobal";
        check(type,type,read(getClass().getClassLoader().getResourceAsStream(type.replace('.','/')+".class")));
        java.net.URL url=getClass().getClassLoader().getResource(type.replace('.','/')+".class");
        Path mcp=Paths.get(((JarURLConnection)url.openConnection()).getJarFileURL().toURI());
        Path official=mcp.getParent().getParent().resolve("forge-1.8.9-MinecraftForge-FG2-merged+MinecraftForge-FG2+forge-1.8.9-11.15.1.2318-1.8.9-official.jar");
        try(JarFile jar=new JarFile(official.toFile())){check("bfr",type,read(jar.getInputStream(jar.getJarEntry("bfr.class"))));}
    }
    private static void check(String raw,String mapped,byte[] bytes)throws Exception{
        bytes=new FreecamTransformer().transform(raw,mapped,bytes);
        AmbienceSkyTransformer transformer=new AmbienceSkyTransformer();byte[] result=transformer.transform(raw,mapped,bytes);
        assertFalse(java.util.Arrays.equals(bytes,result));assertArrayEquals(result,transformer.transform(raw,mapped,result));
        ClassNode before=new ClassNode(),after=new ClassNode();new ClassReader(bytes).accept(before,0);new ClassReader(result).accept(after,0);
        int sun=0,moon=0;
        for(int i=0;i<after.methods.size();i++){
            MethodNode a=(MethodNode)after.methods.get(i),b=(MethodNode)before.methods.get(i);
            if(!a.desc.equals("(FI)V")||!(a.name.equals("renderSky")||a.name.equals("func_174976_a")||a.name.equals("a"))){
                assertArrayEquals(method(before,b),method(after,a));continue;
            }
            new Analyzer(new BasicVerifier()).analyze(after.name,a);
            for(AbstractInsnNode instruction:a.instructions.toArray())if(instruction instanceof MethodInsnNode){MethodInsnNode c=(MethodInsnNode)instruction;if(c.owner.equals("dev/vibe/ambience/AmbienceRenderer")){if(c.name.equals("drawSun"))sun++;if(c.name.equals("drawMoon"))moon++;}}
        }
        assertEquals(1,sun);assertEquals(1,moon);
    }
    private static byte[] method(ClassNode owner,MethodNode method){ClassWriter writer=new ClassWriter(0);writer.visit(owner.version,owner.access,owner.name,null,owner.superName,null);method.accept(writer);writer.visitEnd();return writer.toByteArray();}
    private static byte[] read(InputStream stream)throws IOException{assertNotNull(stream);try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
}
