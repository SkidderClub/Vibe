package dev.vibe.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Changes chat geometry and hit testing while retaining Minecraft's history, wrapping and actions. */
public final class HudChatTransformer implements IClassTransformer,Opcodes {
    private static final String HOOK="dev/vibe/hud/HudChat";
    @Override public byte[] transform(String name,String mapped,byte[] bytes){
        if(bytes!=null&&("net.minecraft.client.gui.GuiChat".equals(mapped)||"net.minecraft.client.gui.GuiChat".equals(name)||"awv".equals(name)))return clickCoordinates(bytes);
        if(bytes==null||!"net.minecraft.client.gui.GuiNewChat".equals(mapped)&&!"net.minecraft.client.gui.GuiNewChat".equals(name)&&!"avt".equals(name))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(Object entry:node.methods){MethodNode method=(MethodNode)entry;boolean hooked=false;for(AbstractInsnNode i:method.instructions.toArray())if(i instanceof MethodInsnNode&&((MethodInsnNode)i).owner.equals(HOOK))hooked=true;if(hooked)continue;
            String hook=method.desc.equals("()I")&&named(method,"getChatWidth","func_146228_f","f")?"width":method.desc.equals("()F")&&named(method,"getChatScale","func_146244_h","h")?"scale":method.desc.equals("()I")&&named(method,"getLineCount","func_146232_i","i")?"lines":null;
            if(hook!=null){for(AbstractInsnNode i:method.instructions.toArray())if(i.getOpcode()==IRETURN||i.getOpcode()==FRETURN)method.instructions.insertBefore(i,new MethodInsnNode(INVOKESTATIC,HOOK,hook,i.getOpcode()==FRETURN?"(F)F":"(I)I",false));changed=true;}
            else if(method.desc.startsWith("(II)L")&&named(method,"getChatComponent","func_146236_a","a")){
                method.instructions.clear();method.tryCatchBlocks.clear();method.localVariables=null;
                method.instructions.add(new VarInsnNode(ALOAD,0));method.instructions.add(new VarInsnNode(ILOAD,1));method.instructions.add(new VarInsnNode(ILOAD,2));
                method.instructions.add(new MethodInsnNode(INVOKESTATIC,HOOK,"component","(Ljava/lang/Object;II)Ljava/lang/Object;",false));
                method.instructions.add(new TypeInsnNode(CHECKCAST,Type.getReturnType(method.desc).getInternalName()));method.instructions.add(new InsnNode(ARETURN));changed=true;
            }else if(method.desc.startsWith("(L")&&method.desc.endsWith(";IIZ)V")&&named(method,"setChatLine","func_146237_a","a")){
                for(AbstractInsnNode i:method.instructions.toArray())if(i instanceof IntInsnNode&&i.getOpcode()==BIPUSH&&((IntInsnNode)i).operand==100){
                    method.instructions.insert(i,new MethodInsnNode(INVOKESTATIC,HOOK,"historyLimit","(I)I",false));changed=true;
                }
            }
        }
        if(!changed)return bytes;ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
    private byte[] clickCoordinates(byte[] bytes){
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(Object entry:node.methods){MethodNode method=(MethodNode)entry;
            if(!method.desc.equals("(III)V")||!named(method,"mouseClicked","func_73864_a","a"))continue;
            for(AbstractInsnNode i:method.instructions.toArray())if(i instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)i;
                if(!call.owner.equals("org/lwjgl/input/Mouse")||!call.desc.equals("()I"))continue;
                if(!call.name.equals("getX")&&!call.name.equals("getY"))continue;
                boolean x=call.name.equals("getX");method.instructions.insertBefore(call,new VarInsnNode(ILOAD,x?1:2));
                method.instructions.set(call,new MethodInsnNode(INVOKESTATIC,HOOK,x?"mouseX":"mouseY","(I)I",false));changed=true;
            }
        }
        if(!changed)return bytes;ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
    private static boolean named(MethodNode method,String mcp,String srg,String raw){return method.name.equals(mcp)||method.name.equals(srg)||method.name.equals(raw);}
}
