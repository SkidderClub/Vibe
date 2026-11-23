package dev.vibe.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Keeps the client's entity set keyed by the final network ID. */
public final class WorldClientEntityTransformer implements IClassTransformer, Opcodes {
    private static final String WORLD = "net.minecraft.client.multiplayer.WorldClient";
    private static final String ENTITY = "net/minecraft/entity/Entity";

    @Override
    public byte[] transform(String name, String mapped, byte[] bytes) {
        if (bytes == null || !(WORLD.equals(mapped) || WORLD.equals(name) || "bdb".equals(name))) return bytes;

        ClassNode type = new ClassNode();
        new ClassReader(bytes).accept(type, 0);
        boolean changed = false;
        for (Object entry : type.methods) {
            MethodNode method = (MethodNode) entry;
            if (("(ILnet/minecraft/entity/Entity;)V".equals(method.desc) || "(ILpk;)V".equals(method.desc))
                    && named(method.name, "addEntityToWorld", "func_73027_a", "a")) {
                changed |= assignIdBeforeTracking(type.name, method);
            }
            if ("(III)V".equals(method.desc) && named(method.name, "doVoidFogParticles", "func_73029_E", "b")) {
                // Attach to the predicate, after the first read of Blocks.barrier.
                boolean predicate = false;
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof FieldInsnNode && instruction.getOpcode() == GETSTATIC) {
                        FieldInsnNode field = (FieldInsnNode) instruction;
                        if (("net/minecraft/init/Blocks".equals(field.owner) || "afi".equals(field.owner))
                                && named(field.name, "barrier", "field_180401_cv", "cv")) predicate = true;
                    }
                    if (!predicate || instruction.getOpcode() != ISTORE) continue;
                    method.instructions.insertBefore(instruction, new MethodInsnNode(INVOKESTATIC,
                            "dev/vibe/module/impl/client/QolModule", "barrierParticles", "(Z)Z", false));
                    changed = true;
                    break;
                }
            }
        }
        if (!changed) return bytes;
        ClassWriter writer = new ClassWriter(0);
        type.accept(writer);
        return writer.toByteArray();
    }

    private static boolean assignIdBeforeTracking(String world, MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode setId = (MethodInsnNode) instruction;
            if (setId.getOpcode() != INVOKEVIRTUAL || !"(I)V".equals(setId.desc)
                    || !(ENTITY.equals(setId.owner) || "pk".equals(setId.owner))
                    || !named(setId.name, "setEntityId", "func_145769_d", "d")) continue;

            AbstractInsnNode id = previousCode(setId);
            AbstractInsnNode entity = previousCode(id);
            AbstractInsnNode pop = previousCode(entity);
            AbstractInsnNode add = previousCode(pop);
            AbstractInsnNode trackedEntity = previousCode(add);
            AbstractInsnNode field = previousCode(trackedEntity);
            AbstractInsnNode self = previousCode(field);
            if (!load(id, ILOAD, 1) || !load(entity, ALOAD, 2) || pop == null || pop.getOpcode() != POP
                    || !setAdd(add) || !load(trackedEntity, ALOAD, 2) || !trackingField(field, world)
                    || !load(self, ALOAD, 0)) continue;

            // Entity.hashCode() uses its ID. Mutating it after Set.add makes the old entry unreachable by Set.remove.
            InsnList assignId = new InsnList();
            for (AbstractInsnNode node : new AbstractInsnNode[]{entity, id, setId}) {
                method.instructions.remove(node);
                assignId.add(node);
            }
            method.instructions.insertBefore(self, assignId);
            return true;
        }
        return false;
    }

    private static AbstractInsnNode previousCode(AbstractInsnNode instruction) {
        if (instruction == null) return null;
        AbstractInsnNode previous = instruction.getPrevious();
        while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
        return previous;
    }

    private static boolean load(AbstractInsnNode instruction, int opcode, int slot) {
        return instruction instanceof VarInsnNode && instruction.getOpcode() == opcode
                && ((VarInsnNode) instruction).var == slot;
    }

    private static boolean setAdd(AbstractInsnNode instruction) {
        if (!(instruction instanceof MethodInsnNode)) return false;
        MethodInsnNode call = (MethodInsnNode) instruction;
        return call.getOpcode() == INVOKEINTERFACE && "java/util/Set".equals(call.owner)
                && "add".equals(call.name) && "(Ljava/lang/Object;)Z".equals(call.desc);
    }

    private static boolean trackingField(AbstractInsnNode instruction, String world) {
        if (!(instruction instanceof FieldInsnNode)) return false;
        FieldInsnNode field = (FieldInsnNode) instruction;
        return field.getOpcode() == GETFIELD && world.equals(field.owner) && "Ljava/util/Set;".equals(field.desc)
                && named(field.name, "entityList", "field_73032_d", "c");
    }

    private static boolean named(String name, String mcp, String srg, String raw) {
        return mcp.equals(name) || srg.equals(name) || raw.equals(name);
    }
}
