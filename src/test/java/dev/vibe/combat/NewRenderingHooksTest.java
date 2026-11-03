package dev.vibe.combat;
import dev.vibe.core.*;
import java.io.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class NewRenderingHooksTest {
    private byte[] bytes(Class<?> type)throws Exception{try(InputStream in=type.getResourceAsStream(type.getSimpleName()+".class");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))>=0)out.write(b,0,n);return out.toByteArray();}}
    private int verify(byte[] bytes,String owner)throws Exception{ClassNode c=new ClassNode();new ClassReader(bytes).accept(c,0);int hooks=0;for(Object value:c.methods){MethodNode m=(MethodNode)value;new Analyzer(new BasicVerifier()).analyze(c.name,m);for(AbstractInsnNode n:m.instructions.toArray())if(n instanceof MethodInsnNode&&((MethodInsnNode)n).owner.equals(owner))hooks++;}return hooks;}
    @Test public void nativeItemTransformAndTitleHooksVerifyAndAreIdempotent()throws Exception{
        ItemPhysicsTransformer items=new ItemPhysicsTransformer();byte[] raw=bytes(net.minecraft.client.renderer.entity.RenderEntityItem.class),patched=items.transform("b", "net.minecraft.client.renderer.entity.RenderEntityItem",raw);
        assertEquals(1,verify(patched,"dev/vibe/module/impl/visual/ItemPhysicsModule"));assertArrayEquals(patched,items.transform("b","net.minecraft.client.renderer.entity.RenderEntityItem",patched));
        ChatterTitleTransformer titles=new ChatterTitleTransformer();raw=bytes(net.minecraft.client.gui.GuiIngame.class);patched=titles.transform("avo","net.minecraft.client.gui.GuiIngame",raw);
        assertEquals(1,verify(patched,"dev/vibe/module/impl/client/ChatterModule"));assertArrayEquals(patched,titles.transform("avo","net.minecraft.client.gui.GuiIngame",patched));
    }
}
