package dev.vibe.core;

import java.util.ArrayList;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Equip before vanilla's swing as well as its attack, restoring on every exit from clickMouse. */
final class SilentMurderClickTransformer implements Opcodes {
    private static final String HOOK="dev/vibe/module/impl/meme/HypixelModule";
    static byte[] transform(byte[] bytes) {
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        for(Object value:new ArrayList<Object>(node.methods)) {
            MethodNode body=(MethodNode)value;
            if(!body.desc.equals("()V")||!(body.name.equals("clickMouse")||body.name.equals("func_147116_af")||body.name.equals("aw")))continue;
            String name=body.name;body.name="vibe$click$"+name;
            MethodNode wrapper=new MethodNode(ASM5,body.access,name,body.desc,body.signature,(String[])body.exceptions.toArray(new String[0]));
            body.access=ACC_PRIVATE|ACC_SYNTHETIC;
            wrapper.visitCode();
            wrapper.visitMethodInsn(INVOKESTATIC,HOOK,"beginClickHook","()I",false);wrapper.visitVarInsn(ISTORE,1);
            Label start=new Label(),end=new Label(),failure=new Label();
            wrapper.visitTryCatchBlock(start,end,failure,null);wrapper.visitLabel(start);
            wrapper.visitVarInsn(ALOAD,0);wrapper.visitMethodInsn(INVOKESPECIAL,node.name,body.name,"()V",false);
            wrapper.visitLabel(end);wrapper.visitVarInsn(ILOAD,1);
            wrapper.visitMethodInsn(INVOKESTATIC,HOOK,"endClickHook","(I)V",false);wrapper.visitInsn(RETURN);
            wrapper.visitLabel(failure);wrapper.visitFrame(F_FULL,2,new Object[]{node.name,INTEGER},1,new Object[]{"java/lang/Throwable"});
            wrapper.visitVarInsn(ILOAD,1);wrapper.visitMethodInsn(INVOKESTATIC,HOOK,"endClickHook","(I)V",false);
            wrapper.visitInsn(ATHROW);wrapper.visitMaxs(0,0);wrapper.visitEnd();node.methods.add(wrapper);
        }
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
