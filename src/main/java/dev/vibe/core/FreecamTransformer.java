package dev.vibe.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Camera transforms and control routing only. Never hooks a packet or entity movement method. */
public final class FreecamTransformer implements IClassTransformer, Opcodes {
    private static final String HOOK = "dev/vibe/module/impl/movement/FreecamModule";

    @Override public byte[] transform(String name, String mapped, byte[] bytes) {
        if (bytes == null) return null;
        String raw = name == null ? "" : name.replace('.', '/');
        String full = mapped == null ? raw : mapped.replace('.', '/');
        boolean renderer = isClass(full, raw, "net/minecraft/client/renderer/EntityRenderer", "bfk");
        boolean terrain = isClass(full, raw, "net/minecraft/client/renderer/RenderGlobal", "bfr");
        boolean player = isClass(full, raw, "net/minecraft/client/entity/EntityPlayerSP", "bew");
        boolean minecraft = isClass(full, raw, "net/minecraft/client/Minecraft", "ave");
        boolean activeInfo = isClass(full, raw, "net/minecraft/client/renderer/ActiveRenderInfo", "auz");
        boolean manager = isClass(full, raw, "net/minecraft/client/renderer/entity/RenderManager", "biu");
        if (!renderer && !terrain && !player && !minecraft && !activeInfo && !manager) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (Object item : node.methods) {
            MethodNode method = (MethodNode) item;
            if (renderer) {
                if (method.desc.equals("(F)V") && named(method.name, "orientCamera", "func_78467_g", "f")) {
                    InsnList hook = new InsnList();
                    hook.add(new VarInsnNode(FLOAD, 1));
                    hook.add(call("orientCameraHook", "(F)Z"));
                    guard(method, hook);
                }
                if (method.desc.equals("(FJ)V") && named(method.name, "updateCameraAndRender", "func_181560_a", "a")) {
                    for (AbstractInsnNode instruction : method.instructions.toArray()) {
                        if (!(instruction instanceof MethodInsnNode)) continue;
                        MethodInsnNode invocation = (MethodInsnNode) instruction;
                        if (invocation.desc.equals("(FF)V") && named(invocation.name, "setAngles", "func_70082_c", "c")
                                && named(invocation.owner, "net/minecraft/client/entity/EntityPlayerSP", "net/minecraft/entity/Entity", "bew", "pk")) {
                            method.instructions.set(invocation, call("mouseLookHook", "(Ljava/lang/Object;FF)V"));
                        }
                    }
                }
                if ((method.desc.equals("(FI)V") && named(method.name, "renderHand", "func_78476_b", "b"))
                        || (method.desc.equals("(F)V") && named(method.name, "hurtCameraEffect", "func_78482_e", "d",
                                "setupViewBobbing", "func_78475_f", "e"))) guardActive(method);
                if (method.desc.equals("(FZ)F") && named(method.name, "getFOVModifier", "func_78481_a", "a")) {
                    for (AbstractInsnNode instruction : method.instructions.toArray()) {
                        if (instruction.getOpcode() == FRETURN) method.instructions.insertBefore(instruction, call("fovHook", "(F)F"));
                    }
                }
                if (method.desc.equals("(F)V") && named(method.name, "renderStreamIndicator", "func_152430_c", "b")) guardHideHud(method);
                if (method.desc.equals("(IFJ)V") && named(method.name, "renderWorldPass", "func_175068_a", "a")) {
                    thirdPersonReads(method);
                    for (AbstractInsnNode instruction : method.instructions.toArray()) {
                        if (!(instruction instanceof MethodInsnNode)) continue;
                        MethodInsnNode invocation = (MethodInsnNode) instruction;
                        if (invocation.desc.equals("(FI)V") && named(invocation.name, "renderSky", "func_174976_a", "a")
                                && named(invocation.owner, "net/minecraft/client/renderer/RenderGlobal", "bfr")) {
                            method.instructions.set(invocation, call("renderSkyHook", "(Ljava/lang/Object;FI)V"));
                        }
                    }
                }
            }
            if (minecraft && ((method.desc.equals("()V") && named(method.name, "clickMouse", "func_147116_af", "aw",
                    "rightClickMouse", "func_147121_ag", "ax"))
                    || (method.desc.equals("(Z)V") && named(method.name, "sendClickBlockToController", "func_147115_a", "b")))) guardActive(method);
            // The F3 profiler pie chart renders outside GuiIngame's overlay events.
            if (minecraft && method.desc.equals("(J)V") && named(method.name, "displayDebugInfo", "func_71366_a", "a")) {
                guardHideHud(method);
            }
            if (minecraft && method.desc.equals("()V") && named(method.name, "runGameLoop", "func_71411_J", "av")) {
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (!(instruction instanceof MethodInsnNode)) continue;
                    MethodInsnNode invocation = (MethodInsnNode) instruction;
                    if (invocation.desc.equals("()V") && named(invocation.name, "updateAchievementWindow", "func_146254_a", "a")
                            && named(invocation.owner, "net/minecraft/client/gui/achievement/GuiAchievement", "ayd")) {
                        method.instructions.set(invocation, call("achievementHudHook", "(Ljava/lang/Object;)V"));
                    }
                }
            }
            if (player && method.desc.equals("()V") && named(method.name, "onLivingUpdate", "func_70636_d", "m")) {
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (!(instruction instanceof MethodInsnNode)) continue;
                    MethodInsnNode invocation = (MethodInsnNode) instruction;
                    if (invocation.desc.equals("()V") && named(invocation.name, "updatePlayerMoveState", "func_78898_a", "a")
                            && named(invocation.owner, "net/minecraft/util/MovementInput", "beu")) {
                        InsnList hook = new InsnList();
                        hook.add(new VarInsnNode(ALOAD, 0)); hook.add(call("movementInputHook", "(Ljava/lang/Object;)V"));
                        method.instructions.insert(instruction, hook);
                    }
                }
            }
            if (terrain) {
                if ((method.desc.equals("(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V")
                        || method.desc.equals("(Lpk;Lbia;F)V"))
                        && (named(method.name, "renderEntities", "func_180446_a", "a") || method.name.startsWith("vibe$chams$"))) {
                    thirdPersonReads(method);
                }
                boolean setup = (method.desc.equals("(Lnet/minecraft/entity/Entity;DLnet/minecraft/client/renderer/culling/ICamera;IZ)V")
                        || method.desc.equals("(Lpk;DLbia;IZ)V")) && named(method.name, "setupTerrain", "func_174970_a", "a");
                boolean vector = method.desc.endsWith("D)Lorg/lwjgl/util/vector/Vector3f;")
                        && named(method.name, "getViewVector", "func_174962_a", "a");
                if (setup || vector) renderReads(method, setup);
                if (setup) {
                    for (AbstractInsnNode instruction : method.instructions.toArray()) {
                        if (!(instruction instanceof MethodInsnNode)) continue;
                        MethodInsnNode invocation = (MethodInsnNode) instruction;
                        if (invocation.desc.equals("(DDD)V") && named(invocation.name, "initialize", "func_178004_a", "a")
                                && named(invocation.owner, "net/minecraft/client/renderer/ChunkRenderContainer", "bfh")) {
                            method.instructions.set(invocation, call("terrainOriginHook", "(Ljava/lang/Object;DDD)V"));
                        }
                    }
                }
            }
            if (activeInfo && (method.desc.equals("(Lnet/minecraft/entity/player/EntityPlayer;Z)V") || method.desc.equals("(Lwn;Z)V"))
                    && named(method.name, "updateRenderInfo", "func_74583_a", "a")) renderReads(method, false);
            if (manager && (method.desc.equals("(Lnet/minecraft/world/World;Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity;Lnet/minecraft/client/settings/GameSettings;F)V")
                    || method.desc.equals("(Ladm;Lavn;Lpk;Lpk;Lavh;F)V"))
                    && named(method.name, "cacheActiveRenderInfo", "func_180597_a", "a")) {
                renderReads(method, false);
                thirdPersonReads(method);
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void renderReads(MethodNode method, boolean positions) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof FieldInsnNode) || instruction.getOpcode() != GETFIELD) continue;
            FieldInsnNode field = (FieldInsnNode) instruction;
            String hook = null;
            if (field.desc.equals("F")) {
                if (named(field.name, "rotationYaw", "prevRotationYaw", "field_70177_z", "field_70126_B", "y", "B")) hook = "yawHook";
                if (named(field.name, "rotationPitch", "prevRotationPitch", "field_70125_A", "field_70127_C", "z", "C")) hook = "pitchHook";
            }
            if (positions && field.desc.equals("D")) {
                if (named(field.name, "posX", "prevPosX", "lastTickPosX", "field_70165_t", "field_70169_q", "field_70142_S", "s", "p", "P")) hook = "terrainXHook";
                if (named(field.name, "posY", "prevPosY", "lastTickPosY", "field_70163_u", "field_70167_r", "field_70137_T", "t", "q", "Q")) hook = "terrainYHook";
                if (named(field.name, "posZ", "prevPosZ", "lastTickPosZ", "field_70161_v", "field_70166_s", "field_70136_U", "u", "r", "R")) hook = "terrainZHook";
            }
            if (positions && field.desc.equals("I")) {
                if (named(field.name, "chunkCoordX", "field_70176_ah", "ae")) hook = "terrainChunkXHook";
                if (named(field.name, "chunkCoordY", "field_70162_ai", "af")) hook = "terrainChunkYHook";
                if (named(field.name, "chunkCoordZ", "field_70164_aj", "ag")) hook = "terrainChunkZHook";
            }
            if (hook == null || !named(field.owner, "net/minecraft/entity/Entity", "net/minecraft/entity/player/EntityPlayer", "pk", "wn")) continue;
            method.instructions.insertBefore(field, new InsnNode(DUP));
            method.instructions.insert(field, call(hook, "(Ljava/lang/Object;" + field.desc + ")" + field.desc));
        }
    }

    private static void thirdPersonReads(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof FieldInsnNode) || instruction.getOpcode() != GETFIELD) continue;
            FieldInsnNode field = (FieldInsnNode) instruction;
            if (field.desc.equals("I") && named(field.owner, "net/minecraft/client/settings/GameSettings", "avh")
                    && named(field.name, "thirdPersonView", "field_74320_O", "aB")) {
                method.instructions.insert(field, call("thirdPersonHook", "(I)I"));
            }
        }
    }

    private static void guardActive(MethodNode method) {
        InsnList hook = new InsnList(); hook.add(call("cameraActiveHook", "()Z")); guard(method, hook);
    }
    private static void guardHideHud(MethodNode method) {
        InsnList hook = new InsnList(); hook.add(call("hideHudHook", "()Z")); guard(method, hook);
    }
    private static void guard(MethodNode method, InsnList hook) {
        LabelNode resume = new LabelNode();
        hook.add(new JumpInsnNode(IFEQ, resume)); hook.add(new InsnNode(RETURN));
        hook.add(resume); hook.add(new FrameNode(F_SAME, 0, null, 0, null));
        method.instructions.insert(hook);
    }
    private static MethodInsnNode call(String name, String desc) { return new MethodInsnNode(INVOKESTATIC, HOOK, name, desc, false); }
    private static boolean isClass(String mapped, String raw, String mcp, String obfuscated) { return mapped.equals(mcp) || raw.equals(mcp) || raw.equals(obfuscated); }
    private static boolean named(String value, String... names) {
        for (String name : names) if (value.equals(name)) return true;
        return false;
    }
}
