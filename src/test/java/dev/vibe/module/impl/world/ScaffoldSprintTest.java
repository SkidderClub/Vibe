package dev.vibe.module.impl.world;

import dev.vibe.core.MoveFixTransformer;
import dev.vibe.inventory.AutomationFixture;
import dev.vibe.module.impl.movement.MoveFixModule;
import dev.vibe.setting.*;
import java.io.*;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.util.MovementInput;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;

public class ScaffoldSprintTest {
    @Test public void alwaysRestoresSprintAfterVanillaCancellationWithoutChangingBackwardInput()throws Exception{
        try(AutomationFixture f=new AutomationFixture()){
            f.player.getAttributeMap().registerAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(.1);
            f.player.movementInput=new MovementInput();f.player.movementInput.moveForward=-1;
            ScaffoldModule scaffold=new ScaffoldModule();MoveFixModule fix=new MoveFixModule();f.base.modules.registerDynamic(scaffold);f.base.modules.registerDynamic(fix);
            fix.setFakeRotation(scaffold.getId(),180,80,"Prevent Backwards Sprinting");scaffold.setEnabled(true);
            f.player.setSprinting(false);ScaffoldModule.nativeSprintHook(f.player);assertTrue(f.player.isSprinting());assertEquals(-1,f.player.movementInput.moveForward,0);
            f.player.movementInput.moveForward=0;f.player.movementInput.moveStrafe=1;f.player.setSprinting(false);ScaffoldModule.nativeSprintHook(f.player);assertTrue(f.player.isSprinting());
            f.player.movementInput.moveStrafe=0;ScaffoldModule.nativeSprintHook(f.player);assertFalse(f.player.isSprinting());
            for(Setting<?> setting:scaffold.getSettings())if(setting.getRawName().equals("Sprint"))((ModeSetting)setting).setValue("Off");
            f.player.movementInput.moveForward=-1;ScaffoldModule.nativeSprintHook(f.player);assertFalse(f.player.isSprinting());scaffold.setEnabled(false);
        }
    }
    @Test public void nativeSprintDecisionPrecedesTheActualMovementUpdate()throws Exception{
        byte[] bytes;try(InputStream in=EntityPlayerSP.class.getResourceAsStream("EntityPlayerSP.class");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];for(int n;(n=in.read(buffer))!=-1;)out.write(buffer,0,n);bytes=out.toByteArray();
        }
        String name=EntityPlayerSP.class.getName();byte[] result=new MoveFixTransformer().transform(name,name,bytes);
        ClassNode node=new ClassNode();new ClassReader(result).accept(node,0);int count=0;
        for(Object value:node.methods)for(AbstractInsnNode i:((MethodNode)value).instructions.toArray())if(i instanceof MethodInsnNode&&((MethodInsnNode)i).name.equals("nativeSprintHook")){
            count++;AbstractInsnNode next=i.getNext();while(next!=null&&next.getOpcode()<0)next=next.getNext();
            assertTrue(next instanceof MethodInsnNode);assertEquals(Opcodes.INVOKESPECIAL,next.getOpcode());assertEquals("onLivingUpdate",((MethodInsnNode)next).name);
        }assertEquals(1,count);
    }
}
