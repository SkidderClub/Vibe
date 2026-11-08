package dev.vibe.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Intercept panel-sized textured rectangles; all native slot drawing and clicking remains intact. */
public final class ContainerThemeTransformer implements IClassTransformer,Opcodes {
    private static final String HOOK="dev/vibe/hud/ContainerThemes";
    @Override public byte[] transform(String name,String mapped,byte[] bytes){
        if("net.minecraft.client.renderer.InventoryEffectRenderer".equals(mapped)||"net.minecraft.client.renderer.InventoryEffectRenderer".equals(name)||"ayw".equals(name))return potionRenderer(bytes);
        if(bytes==null||!"net.minecraft.client.gui.inventory.GuiContainer".equals(mapped)&&!"net.minecraft.client.gui.inventory.GuiContainer".equals(name)&&!"ayl".equals(name))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        boolean mcp=false,srg=false;for(Object entry:node.methods){MethodNode method=(MethodNode)entry;if(method.name.equals("drawScreen"))mcp=true;if(method.name.equals("func_73863_a"))srg=true;
            for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode&&((MethodInsnNode)instruction).owner.equals(HOOK))return bytes;}
        String texture=mcp?"drawTexturedModalRect":srg?"func_73729_b":"b";
        MethodNode hook=new MethodNode(ASM5,ACC_PUBLIC,texture,"(IIIIII)V",null,null);hook.visitCode();hook.visitVarInsn(ALOAD,0);for(int i=1;i<=6;i++)hook.visitVarInsn(ILOAD,i);
        hook.visitMethodInsn(INVOKESTATIC,HOOK,"backdrop","(Ljava/lang/Object;IIIIII)Z",false);Label vanilla=new Label();hook.visitJumpInsn(IFEQ,vanilla);hook.visitInsn(RETURN);hook.visitLabel(vanilla);hook.visitFrame(F_SAME,0,null,0,null);
        hook.visitVarInsn(ALOAD,0);for(int i=1;i<=6;i++)hook.visitVarInsn(ILOAD,i);hook.visitMethodInsn(INVOKESPECIAL,node.superName,texture,"(IIIIII)V",false);hook.visitInsn(RETURN);hook.visitMaxs(0,0);hook.visitEnd();node.methods.add(hook);
        for(Object entry:node.methods){MethodNode method=(MethodNode)entry;if(!(method.name.equals("drawScreen")||method.name.equals("func_73863_a")||method.name.equals("a"))||!method.desc.equals("(IIF)V"))continue;
            for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)instruction;
                if(call.desc.equals("(II)V")&&(call.name.equals("drawGuiContainerForegroundLayer")||call.name.equals("func_146979_b")||call.name.equals("b"))){call.setOpcode(INVOKESTATIC);call.owner=HOOK;call.name="foreground";call.desc="(Ljava/lang/Object;II)V";call.itf=false;}}
        }
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
    private byte[] potionRenderer(byte[] bytes){
        if(bytes==null)return null;ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        for(Object entry:node.methods)for(AbstractInsnNode instruction:((MethodNode)entry).instructions.toArray())
            if(instruction instanceof MethodInsnNode&&((MethodInsnNode)instruction).owner.equals(HOOK))return bytes;
        for(Object entry:node.methods){MethodNode method=(MethodNode)entry;if(!method.desc.equals("()V"))continue;
            if(method.name.equals("updateActivePotionEffects")||method.name.equals("func_175378_g")||method.name.equals("a")){
                for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()==RETURN){InsnList hook=new InsnList();hook.add(new VarInsnNode(ALOAD,0));hook.add(new MethodInsnNode(INVOKESTATIC,HOOK,"updatePotionLayout","(Ljava/lang/Object;)V",false));method.instructions.insertBefore(instruction,hook);}
            }else if(method.name.equals("drawActivePotionEffects")||method.name.equals("func_147044_g")||method.name.equals("f")){
                InsnList hook=new InsnList();LabelNode vanilla=new LabelNode();hook.add(new VarInsnNode(ALOAD,0));hook.add(new MethodInsnNode(INVOKESTATIC,HOOK,"potionEffects","(Ljava/lang/Object;)Z",false));hook.add(new JumpInsnNode(IFEQ,vanilla));hook.add(new InsnNode(RETURN));hook.add(vanilla);hook.add(new FrameNode(F_SAME,0,null,0,null));method.instructions.insert(hook);
            }
        }
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
