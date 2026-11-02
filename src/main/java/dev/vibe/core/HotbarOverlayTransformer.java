package dev.vibe.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Move the selected-item caption with the replacement hotbar instead of leaving it at vanilla coordinates. */
public final class HotbarOverlayTransformer implements IClassTransformer,Opcodes {
    private static final String HOOK="dev/vibe/hud/HudManager";
    @Override public byte[] transform(String name,String mapped,byte[] bytes){
        boolean forge="net.minecraftforge.client.GuiIngameForge".equals(name)||"net.minecraftforge.client.GuiIngameForge".equals(mapped);
        if(bytes==null||!forge&&!"net.minecraft.client.gui.GuiIngame".equals(mapped)&&!"net.minecraft.client.gui.GuiIngame".equals(name))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(Object value:node.methods){MethodNode method=(MethodNode)value;
            boolean caption=forge?(method.name.equals("renderToolHightlight")||method.name.equals("renderToolHighlight"))&&method.desc.endsWith(")V")
                    :(method.name.equals("renderSelectedItem")||method.name.equals("func_181551_a"))&&method.desc.endsWith(")V");
            if(!caption)continue;boolean hooked=false;for(AbstractInsnNode i:method.instructions.toArray())if(i instanceof MethodInsnNode&&((MethodInsnNode)i).owner.equals(HOOK))hooked=true;if(hooked)continue;
            LabelNode vanilla=new LabelNode();InsnList head=new InsnList();head.add(new MethodInsnNode(INVOKESTATIC,HOOK,"replacesHotbar","()Z",false));head.add(new JumpInsnNode(IFEQ,vanilla));head.add(new InsnNode(RETURN));head.add(vanilla);head.add(new FrameNode(F_SAME,0,null,0,null));method.instructions.insert(head);changed=true;
        }
        if(!changed)return bytes;ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
