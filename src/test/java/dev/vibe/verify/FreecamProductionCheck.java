package dev.vibe.verify;

import java.io.File;
import java.io.PrintStream;
import java.util.*;
import net.minecraft.launchwrapper.*;
import net.minecraftforge.fml.common.launcher.FMLTweaker;

/** Boots run.bat's real Forge/OptiFine transformers, replacing only the game/window entry point. */
public final class FreecamProductionCheck {
    private static final PrintStream OUTPUT=System.out,ERROR=System.err;
    public static void main(String[] args) {
        List<String> launch=new ArrayList<String>(Arrays.asList("--tweakClass",Tweaker.class.getName(),"--gameDir",args[0],"--version","1.8.9"));
        if(args.length>1&&args[1].equals("optifine"))Collections.addAll(launch,"--tweakClass","optifine.OptiFineForgeTweaker");
        new File(args[0]).mkdirs();Launch.main(launch.toArray(new String[0]));
    }
    public static final class Tweaker implements ITweaker {
        private final FMLTweaker forge=new FMLTweaker();
        @Override public void acceptOptions(List<String> args,File gameDir,File assetsDir,String profile) {forge.acceptOptions(args,gameDir,assetsDir,profile);}
        @Override public void injectIntoClassLoader(LaunchClassLoader loader) {forge.injectIntoClassLoader(loader);}
        @Override public String getLaunchTarget() {return Probe.class.getName();}
        @Override public String[] getLaunchArguments() {return forge.getLaunchArguments();}
    }
    public static final class Probe {
        public static void main(String[] ignored) throws Exception {
            try {
                LaunchClassLoader loader=Launch.classLoader;
                loader.registerTransformer(BrandingObserver.class.getName());
                // A rebuild replaces the source JAR after the running loader has opened
                // its client. Lazy Freecam loading must keep using the session copy.
                File mutable=new File(System.getProperty("vibe.check.mutableJar"));
                File frozen=new File(System.getProperty("vibe.check.frozenJar"));
                long frozenSize=frozen.length();
                try(java.util.jar.JarOutputStream replacement=new java.util.jar.JarOutputStream(new java.io.FileOutputStream(mutable))) { }
                if(frozen.length()!=frozenSize||frozenSize<1000||mutable.length()>=1000)
                    throw new AssertionError("Build replacement changed the frozen runtime JAR");
                Class<?> type=Class.forName("dev.vibe.camera.DroneCollisions",true,loader);
                Class<?> world=Class.forName("net.minecraft.world.World",false,loader);
                Object collisions=type.getConstructor(world).newInstance(new Object[]{null});
                type.getDeclaredMethods();
                if(type.getClassLoader()!=loader||!type.getProtectionDomain().getCodeSource().getLocation().toString().contains("/runtime/session-"))
                    throw new AssertionError("Freecam did not load from the frozen production JAR");
                OUTPUT.println("Freecam loaded through production Forge/OptiFine after replacing the build JAR: "+collisions.getClass().getProtectionDomain().getCodeSource().getLocation());
                for(String name:new String[]{"net.minecraft.client.Minecraft","net.minecraft.client.multiplayer.PlayerControllerMP"}) {
                    Class<?> owner=Class.forName(name,false,loader);boolean found=false;
                    String prefix=name.endsWith("Minecraft")?"vibe$click$":"vibe$tool$";
                    for(java.lang.reflect.Method method:owner.getDeclaredMethods())if(method.getName().startsWith(prefix))found=true;
                    if(!found)throw new AssertionError("Production Silent Murder scope missing from "+name);
                }
                OUTPUT.println("Silent Murder click and controller scopes linked through production Forge/OptiFine.");
                for(String name:new String[]{"net.minecraft.client.renderer.ActiveRenderInfo","net.minecraft.client.renderer.EntityRenderer",
                        "dev.vibe.module.impl.visual.SmoothF5Module","dev.vibe.ui.render.HologramRenderer","dev.vibe.ui.render.HologramPose"})Class.forName(name,false,loader).getDeclaredMethods();
                Class.forName("dev.vibe.module.impl.movement.FreecamModule",false,loader).getMethod("frontViewHook",boolean.class);
                OUTPUT.println("SmoothF5 camera hooks and native hologram models linked through production Forge/OptiFine.");
                OUTPUT.println("Installed renderer camera hooks: "+BrandingObserver.cameraHooks);
                if(!BrandingObserver.cameraHooks.contains("func_78467_g:yawHook")
                        ||!BrandingObserver.cameraHooks.contains("func_78467_g:pitchHook")
                        ||!BrandingObserver.cameraHooks.contains("func_181560_a:mouseLookHook"))
                    throw new AssertionError("Installed first-person camera hooks are missing");
                for(String name:new String[]{"dev.vibe.module.impl.combat.AIAuraModule","dev.vibe.aiaura.AuraNetwork","dev.vibe.aiaura.AuraBrain","dev.vibe.ui.screen.AIAuraRecorderGui","dev.vibe.ui.render.AuraArenaRenderer"})Class.forName(name,false,loader).getDeclaredMethods();
                Class.forName("dev.vibe.module.impl.combat.KillAuraModule",false,loader).getMethod("activeController");
                Class.forName("dev.vibe.module.impl.combat.AIAuraModule",false,loader).getMethod("cameraYawHook",Object.class,float.class);
                Class.forName("dev.vibe.module.impl.combat.AIAuraModule",false,loader).getMethod("cameraPitchHook",Object.class,float.class);
                OUTPUT.println("AIAura neural policy, shared combat hooks and recorder linked through production Forge/OptiFine.");
                for(String name:new String[]{"dev.vibe.core.startup.GhostStartup","dev.vibe.core.startup.GhostWindowIcon",
                        "dev.vibe.core.startup.GhostLoadingRenderer","net.minecraftforge.fml.client.SplashProgress$3",
                        "net.minecraftforge.fml.common.ProgressManager$ProgressBar"})Class.forName(name,false,loader).getDeclaredMethods();
                OUTPUT.println("Ghost startup, native icon and Forge splash classes linked through production Forge/OptiFine.");
                for(String name:new String[]{"dev.vibe.module.impl.combat.AutoBuffModule","dev.vibe.module.impl.world.AutoRespawnModule",
                        "dev.vibe.module.impl.world.AutoCrafterModule","dev.vibe.inventory.PlayerInventorySession",
                        "dev.vibe.inventory.CraftingPlanner","dev.vibe.setting.BlockCoordinateSetting"})Class.forName(name,false,loader).getDeclaredMethods();
                OUTPUT.println("AutoBuff, AutoRespawn, AutoCrafter and target coordinates linked through production Forge/OptiFine.");
                if(!BrandingObserver.hooks.contains("renderForge")||!BrandingObserver.hooks.contains("drawVanilla")||!BrandingObserver.hooks.contains("apply"))
                    throw new AssertionError("Installed ghost branding hooks missing: "+BrandingObserver.hooks);
            } catch(Throwable failure) {
                failure.printStackTrace(ERROR);throw new RuntimeException("Production Freecam linkage failed",failure);
            }
        }
    }
    public static final class BrandingObserver implements IClassTransformer {
        public static final Set<String> hooks=new HashSet<String>();
        public static final Set<String> cameraHooks=new java.util.TreeSet<String>();
        @Override public byte[] transform(String name,String mapped,byte[] bytes) {
            if(bytes==null)return null;
            boolean renderer="net.minecraft.client.renderer.EntityRenderer".equals(mapped);
            if(!renderer&&!"net.minecraft.client.Minecraft".equals(mapped)&&!name.equals("net.minecraftforge.fml.client.SplashProgress$3"))return bytes;
            org.objectweb.asm.tree.ClassNode node=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader(bytes).accept(node,0);
            for(Object entry:node.methods)for(org.objectweb.asm.tree.AbstractInsnNode insn:((org.objectweb.asm.tree.MethodNode)entry).instructions.toArray())
                if(insn instanceof org.objectweb.asm.tree.MethodInsnNode){org.objectweb.asm.tree.MethodInsnNode call=(org.objectweb.asm.tree.MethodInsnNode)insn;if(call.owner.startsWith("dev/vibe/core/startup/"))hooks.add(call.name);if(renderer&&call.owner.equals("dev/vibe/module/impl/movement/FreecamModule"))cameraHooks.add(((org.objectweb.asm.tree.MethodNode)entry).name+":"+call.name);}
            return bytes;
        }
    }
}
