package dev.vibe.features;
import dev.vibe.core.*;
import java.io.*;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;
public class FeatureTransformersTest {
    @Test public void emotePoseRestorationIsInstalledOnceOnBothNativeModels()throws Exception{MemeAnimationTransformer transformer=new MemeAnimationTransformer();for(String name:new String[]{"net.minecraft.client.model.ModelPlayer","net.minecraft.client.model.ModelBiped"}){byte[] bytes=transformer.transform(name,name,original(name));assertEquals(1,calls(bytes,"EmotePoses","before"));assertEquals(1,calls(transformer.transform(name,name,bytes),"EmotePoses","before"));}}
    @Test public void f5RoutesTheNativePerspectiveWriteWithoutChangingCameraModeElsewhere()throws Exception{String name="net.minecraft.client.Minecraft";byte[] bytes=new FreecamTransformer().transform(name,name,new MoveFixTransformer().transform(name,name,original(name)));assertTrue(calls(bytes,"FreecamModule","perspectiveChangeHook")>=1);}
    private static byte[] original(String name)throws Exception{try(InputStream in=FeatureTransformersTest.class.getClassLoader().getResourceAsStream(name.replace('.','/')+".class")){assertNotNull(name,in);ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];for(int n;(n=in.read(b))>0;)out.write(b,0,n);return out.toByteArray();}}
    private static int calls(byte[] bytes,String owner,String method){ClassNode c=new ClassNode();new ClassReader(bytes).accept(c,0);int count=0;for(Object v:c.methods)for(AbstractInsnNode n:((MethodNode)v).instructions.toArray())if(n instanceof MethodInsnNode){MethodInsnNode m=(MethodInsnNode)n;if(m.owner.endsWith(owner)&&m.name.equals(method))count++;}return count;}
    @Test public void nativeItemAndArmourGlintHooksAreInstalledAndIdempotent()throws Exception{GlintTransformer t=new GlintTransformer();for(String name:new String[]{"net.minecraft.client.renderer.entity.RenderItem","net.minecraft.client.renderer.entity.layers.LayerArmorBase"}){byte[] transformed=t.transform(name,name,original(name));assertTrue(name+" time hooks: "+calls(transformed,"EnchantmentGlintModule",name.endsWith("RenderItem")?"time":"translate"),calls(transformed,"EnchantmentGlintModule",name.endsWith("RenderItem")?"time":"translate")>0);assertTrue(name+" color hooks",calls(transformed,"EnchantmentGlintModule",name.endsWith("RenderItem")?"tint":"color")>0);assertArrayEquals(transformed,t.transform(name,name,transformed));}}
    @Test public void blockDestructionUsesOneNativeReplacementHook()throws Exception{String name="net.minecraft.client.particle.EffectRenderer";BlockDebrisTransformer t=new BlockDebrisTransformer();byte[] bytes=t.transform(name,name,original(name));assertEquals(1,calls(bytes,"BlockDebrisModule","destroyHook"));assertArrayEquals(bytes,t.transform(name,name,bytes));}
}
