package dev.vibe.core;

import java.util.ArrayList;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Protect native input and local-player item-use ticks with a finally scope. */
final class HotbarLogicTransformer implements Opcodes {
    private static final String HOOK="dev/vibe/inventory/HotbarSwitches";
    static byte[] transform(byte[] bytes,boolean player){
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        for(Object entry:new ArrayList<Object>(node.methods)){
            MethodNode original=(MethodNode)entry;
            if(!original.desc.equals("()V")||!(player?named(original.name,"onUpdate","func_70071_h_","t_"):named(original.name,"runTick","func_71407_l","s")))continue;
            String name=original.name;original.name="vibe$hotbar$"+name;
            MethodNode wrapper=new MethodNode(ASM5,original.access,name,original.desc,original.signature,(String[])original.exceptions.toArray(new String[0]));
            original.access=ACC_PRIVATE|ACC_SYNTHETIC;
            wrapper.visitCode();
            if(player){wrapper.visitVarInsn(ALOAD,0);wrapper.visitMethodInsn(INVOKESTATIC,HOOK,"beginPlayerHook","(Ljava/lang/Object;)I",false);wrapper.visitVarInsn(ISTORE,1);}
            Label start=new Label(),end=new Label(),handler=new Label();wrapper.visitTryCatchBlock(start,end,handler,null);wrapper.visitLabel(start);
            wrapper.visitVarInsn(ALOAD,0);wrapper.visitMethodInsn(INVOKESPECIAL,node.name,original.name,"()V",false);wrapper.visitLabel(end);
            finish(wrapper,player);wrapper.visitInsn(RETURN);wrapper.visitLabel(handler);
            wrapper.visitFrame(F_FULL,player?2:1,player?new Object[]{node.name,INTEGER}:new Object[]{node.name},1,new Object[]{"java/lang/Throwable"});
            finish(wrapper,player);wrapper.visitInsn(ATHROW);wrapper.visitMaxs(0,0);wrapper.visitEnd();node.methods.add(wrapper);
        }
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
    private static void finish(MethodVisitor visitor,boolean player){
        if(player){visitor.visitVarInsn(ILOAD,1);visitor.visitMethodInsn(INVOKESTATIC,HOOK,"endActionHook","(I)V",false);}
        else {
            visitor.visitMethodInsn(INVOKESTATIC,"dev/vibe/module/impl/combat/AutoBuffModule","finishInputHook","()V",false);
            visitor.visitMethodInsn(INVOKESTATIC,HOOK,"finishInputHook","()V",false);
        }
    }
    private static boolean named(String name,String... names){for(String value:names)if(value.equals(name))return true;return false;}
}
