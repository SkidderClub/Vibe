package dev.vibe.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Changes chat geometry and hit testing while retaining Minecraft's history, wrapping and actions. */
public final class HudChatTransformer implements IClassTransformer,Opcodes {
    private static final String HOOK="dev/vibe/hud/HudChat";
    @Override public byte[] transform(String name,String mapped,byte[] bytes){
        if(bytes==null||!"net.minecraft.client.gui.GuiNewChat".equals(mapped)&&!"net.minecraft.client.gui.GuiNewChat".equals(name))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(Object entry:node.methods){MethodNode method=(MethodNode)entry;boolean hooked=false;for(AbstractInsnNode i:method.instructions.toArray())if(i instanceof MethodInsnNode&&((MethodInsnNode)i).owner.equals(HOOK))hooked=true;if(hooked)continue;
            String hook=named(method,"getChatWidth","func_146228_f")?"width":named(method,"getChatScale","func_146244_h")?"scale":named(method,"getLineCount","func_146232_i")?"lines":null;
            if(hook!=null){for(AbstractInsnNode i:method.instructions.toArray())if(i.getOpcode()==IRETURN||i.getOpcode()==FRETURN)method.instructions.insertBefore(i,new MethodInsnNode(INVOKESTATIC,HOOK,hook,i.getOpcode()==FRETURN?"(F)F":"(I)I",false));changed=true;}
            else if(named(method,"getChatComponent","func_146236_a")){
                LabelNode vanilla=new LabelNode();InsnList head=new InsnList();head.add(new MethodInsnNode(INVOKESTATIC,HOOK,"active","()Z",false));head.add(new JumpInsnNode(IFEQ,vanilla));
                head.add(new VarInsnNode(ILOAD,1));head.add(new VarInsnNode(ILOAD,2));head.add(new MethodInsnNode(INVOKESTATIC,HOOK,"component","(II)Ljava/lang/Object;",false));head.add(new TypeInsnNode(CHECKCAST,Type.getReturnType(method.desc).getInternalName()));head.add(new InsnNode(ARETURN));head.add(vanilla);head.add(new FrameNode(F_SAME,0,null,0,null));method.instructions.insert(head);changed=true;
            }
        }
        if(!changed)return bytes;ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
    private static boolean named(MethodNode method,String mcp,String srg){return method.name.equals(mcp)||method.name.equals(srg);}
}
