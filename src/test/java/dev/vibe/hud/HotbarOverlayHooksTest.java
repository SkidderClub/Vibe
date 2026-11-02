package dev.vibe.hud;

import dev.vibe.core.HotbarOverlayTransformer;
import java.io.*;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.Assert.*;

public class HotbarOverlayHooksTest {
    @Test public void vanillaAndForgeItemCaptionsAreSuppressedOnlyThroughTheOptionalHotbarGate()throws Exception{
        HotbarOverlayTransformer transformer=new HotbarOverlayTransformer();
        for(String name:new String[]{"net.minecraft.client.gui.GuiIngame","net.minecraftforge.client.GuiIngameForge"}){
            byte[] raw;try(InputStream input=getClass().getClassLoader().getResourceAsStream(name.replace('.','/')+".class");ByteArrayOutputStream output=new ByteArrayOutputStream()){byte[] chunk=new byte[8192];int n;while((n=input.read(chunk))!=-1)output.write(chunk,0,n);raw=output.toByteArray();}
            byte[] patched=transformer.transform(name,name,raw);assertArrayEquals(patched,transformer.transform(name,name,patched));
            ClassNode node=new ClassNode();new ClassReader(patched).accept(node,0);int guards=0;
            for(Object value:node.methods){MethodNode method=(MethodNode)value;new Analyzer(new BasicVerifier()).analyze(node.name,method);for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode&&((MethodInsnNode)instruction).name.equals("replacesHotbar"))guards++;}
            assertEquals("Missing native caption hook in "+name,1,guards);
        }
    }
}
