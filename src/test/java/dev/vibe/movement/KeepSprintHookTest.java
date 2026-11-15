package dev.vibe.movement;

import dev.vibe.Vibe;
import dev.vibe.core.KeepSprintTransformer;
import dev.vibe.module.impl.combat.KeepSprintModule;
import dev.vibe.path.PathTestFixture;
import java.io.*;
import java.util.jar.JarFile;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.Assert.*;

public class KeepSprintHookTest {
    @Test public void mappedAndOfficialNativeAttackSitesVerifyAndTransformOnlyOnce()throws Exception{
        check("net.minecraft.entity.player.EntityPlayer",read(getClass().getClassLoader().getResourceAsStream("net/minecraft/entity/player/EntityPlayer.class")));
        java.lang.reflect.Method official=RewoundHooksTest.class.getDeclaredMethod("officialJar");official.setAccessible(true);
        try(JarFile jar=(JarFile)official.invoke(new RewoundHooksTest())){check("wn",read(jar.getInputStream(jar.getJarEntry("wn.class"))));}
    }
    private void check(String name,byte[] bytes)throws Exception{
        KeepSprintTransformer transformer=new KeepSprintTransformer();byte[] result=transformer.transform(name,"net.minecraft.entity.player.EntityPlayer",bytes);
        assertArrayEquals(result,transformer.transform(name,"net.minecraft.entity.player.EntityPlayer",result));ClassNode node=new ClassNode();new ClassReader(result).accept(node,0);
        int slow=0,reset=0;
        for(Object object:node.methods){MethodNode method=(MethodNode)object;new Analyzer(new BasicVerifier()).analyze(node.name,method);
            for(AbstractInsnNode ins:method.instructions.toArray())if(ins instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)ins;
                if(call.owner.equals("dev/vibe/module/impl/combat/KeepSprintModule")){if(call.name.equals("slowdown"))slow++;if(call.name.equals("resetSprint"))reset++;}
            }
        }assertEquals(2,slow);assertEquals(1,reset);
    }
    @Test public void bothNativePoliciesAreIndependentAndOnlyAffectTheLocalPlayer()throws Exception{
        try(RewoundModesTest.Fixture f=new RewoundModesTest.Fixture()){
            KeepSprintModule m=new KeepSprintModule();Vibe.getInstance().getModuleManager().registerDynamic(m);
            SprintPlayer local=PathTestFixture.allocate(SprintPlayer.class),other=PathTestFixture.allocate(SprintPlayer.class);f.mc.thePlayer=local;
            assertEquals(.6,KeepSprintModule.slowdown(.6,local),0);m.setEnabled(true);
            assertEquals(1,KeepSprintModule.slowdown(.6,local),0);assertEquals(.6,KeepSprintModule.slowdown(.6,other),0);
            KeepSprintModule.resetSprint(local,false);assertEquals(0,local.resets);KeepSprintModule.resetSprint(other,false);assertEquals(1,other.resets);
            m.removeResetSprint.setEnabled(false);KeepSprintModule.resetSprint(local,false);assertEquals(1,local.resets);
            m.removeSlowDown.setEnabled(false);assertEquals(.6,KeepSprintModule.slowdown(.6,local),0);
            m.removeSlowDown.setEnabled(true);m.onlyOnGround.setEnabled(true);local.onGround=false;assertEquals(.6,KeepSprintModule.slowdown(.6,local),0);
            local.onGround=true;assertEquals(1,KeepSprintModule.slowdown(.6,local),0);m.setEnabled(false);f.mc.thePlayer=f.player;
        }
    }
    public static class SprintPlayer extends RewoundModesTest.Player {int resets;protected SprintPlayer(){super();}@Override public void setSprinting(boolean value){resets++;}}
    private static byte[] read(InputStream input)throws Exception{try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];for(int n;(n=in.read(buffer))!=-1;)out.write(buffer,0,n);return out.toByteArray();}}
}
