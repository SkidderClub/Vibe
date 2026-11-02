package dev.vibe.aiaura;

import dev.vibe.core.RecorderRenderTransformer;
import dev.vibe.path.PathTestFixture;
import dev.vibe.ui.render.AuraArenaRenderer;
import dev.vibe.ui.screen.AIAuraRecorderGui;
import java.io.*;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.Assert.*;

public class RecorderRenderHooksTest {
    @Test public void onlyStandaloneRecorderSuppressesWorldDrawingAndCameraGuiEntryStaysIntact()throws Exception{
        try(PathTestFixture f=new PathTestFixture()){
            assertFalse(AuraArenaRenderer.recorderOwnsFrame());f.mc.currentScreen=PathTestFixture.allocate(AIAuraRecorderGui.class);assertTrue(AuraArenaRenderer.recorderOwnsFrame());
            f.mc.currentScreen=new net.minecraft.client.gui.GuiChat();assertFalse(AuraArenaRenderer.recorderOwnsFrame());
            byte[] raw;try(InputStream input=getClass().getClassLoader().getResourceAsStream("net/minecraft/client/renderer/EntityRenderer.class");ByteArrayOutputStream output=new ByteArrayOutputStream()){byte[] chunk=new byte[8192];int n;while((n=input.read(chunk))!=-1)output.write(chunk,0,n);raw=output.toByteArray();}
            RecorderRenderTransformer transformer=new RecorderRenderTransformer();byte[] patched=transformer.transform("net.minecraft.client.renderer.EntityRenderer","net.minecraft.client.renderer.EntityRenderer",raw);
            assertArrayEquals(patched,transformer.transform("net.minecraft.client.renderer.EntityRenderer","net.minecraft.client.renderer.EntityRenderer",patched));
            ClassNode node=new ClassNode();new ClassReader(patched).accept(node,0);int worldHooks=0;
            for(Object value:node.methods){MethodNode method=(MethodNode)value;new Analyzer(new BasicVerifier()).analyze(node.name,method);
                for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode&&((MethodInsnNode)instruction).name.equals("recorderOwnsFrame")){worldHooks++;assertEquals("renderWorld",method.name);assertEquals("(FJ)V",method.desc);}
            }
            assertEquals(1,worldHooks);
        }
    }
}
