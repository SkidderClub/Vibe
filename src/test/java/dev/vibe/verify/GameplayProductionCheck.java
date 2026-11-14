package dev.vibe.verify;

import java.io.File;
import java.nio.file.Files;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

/** Check final Forge/OptiFine classes rather than development names alone. */
public final class GameplayProductionCheck {
    public static void verify(LaunchClassLoader loader) throws Exception {
        java.lang.reflect.Field field=LaunchClassLoader.class.getDeclaredField("tempFolder");field.setAccessible(true);File dumps=(File)field.get(null);
        int manual=0,guard=0,finish=0,uses=0;
        for(String name:new String[]{"net.minecraft.client.Minecraft","net.minecraft.client.multiplayer.PlayerControllerMP"}){
            Class.forName(name,false,loader).getDeclaredMethods();File dump=new File(dumps,name.replace('.','/')+".class");
            ClassNode node=new ClassNode();new ClassReader(Files.readAllBytes(dump.toPath())).accept(node,0);
            for(Object value:node.methods)for(AbstractInsnNode instruction:((MethodNode)value).instructions.toArray())if(instruction instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)instruction;
                if(call.owner.equals("dev/vibe/module/impl/world/NoEscMenuModule")){if(call.name.equals("openManualHook"))manual++;if(call.name.equals("blocksMenuHook"))guard++;}
                if(call.owner.equals("dev/vibe/module/impl/combat/AutoBuffModule")){if(call.name.equals("headUseSentHook"))uses++;if(call.name.equals("finishInputHook"))finish++;}
            }
        }
        if(manual!=1||guard!=1||uses!=2||finish<2)throw new AssertionError("Gameplay production hooks: manual="+manual+", guard="+guard+", uses="+uses+", finish="+finish);
        for(String name:new String[]{"dev.vibe.module.impl.world.NoEscMenuModule","dev.vibe.module.impl.movement.TargetStrafeModule","dev.vibe.module.impl.world.ScaffoldModule","dev.vibe.module.impl.combat.AutoBuffModule"})Class.forName(name,false,loader).getDeclaredMethods();
    }
}
