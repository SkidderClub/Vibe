package dev.vibe.verify;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Called by the existing headless Forge/OptiFine production probe. */
public final class RewoundProductionCheck {
    public static void verify(LaunchClassLoader loader) throws Exception {
        Set<String> hooks = new HashSet<>();
        java.lang.reflect.Field field = LaunchClassLoader.class.getDeclaredField("tempFolder"); field.setAccessible(true);
        File dumps = (File) field.get(null);
        for (String name : new String[] {"net.minecraft.client.entity.EntityPlayerSP", "net.minecraft.client.network.NetHandlerPlayClient", "net.minecraft.entity.Entity"}) {
            Class.forName(name, false, loader).getDeclaredMethods();
            File dump = new File(dumps, name.replace('.', '/') + ".class");
            if (!dump.isFile()) throw new AssertionError("Missing final production dump: " + dump);
            ClassNode node = new ClassNode(); new ClassReader(Files.readAllBytes(dump.toPath())).accept(node, 0);
            for (Object value : node.methods) for (AbstractInsnNode instruction : ((MethodNode) value).instructions.toArray()) {
                if (instruction instanceof MethodInsnNode && ((MethodInsnNode) instruction).owner.equals("dev/vibe/movement/RewoundHooks")) hooks.add(((MethodInsnNode) instruction).name);
            }
        }
        for (String hook : new String[] {"move", "afterMove", "beforeWalking", "afterWalking", "endWalking", "ground", "correction", "preUpdate", "preStepHeight", "stepHeight", "postStep", "walkingY", "positionTicks"}) {
            if (!hooks.contains(hook)) throw new AssertionError("Missing production Rewound hook: " + hook + " in " + hooks);
        }
        for (String name : new String[] {"dev.vibe.movement.RewoundHooks", "dev.vibe.movement.RewoundSpeed",
                "dev.vibe.module.impl.combat.RewoundAutoblock", "dev.vibe.module.impl.movement.SpeedModule",
                "dev.vibe.module.impl.movement.NoSlowModule", "dev.vibe.module.impl.movement.NoFallModule", "dev.vibe.module.impl.movement.StepModule",
                "dev.vibe.module.impl.movement.AntiVoidModule", "dev.vibe.module.impl.combat.CriticalsModule", "dev.vibe.movement.RewoundStep", "dev.vibe.movement.RewoundVoid"}) Class.forName(name, false, loader).getDeclaredMethods();
    }
}
