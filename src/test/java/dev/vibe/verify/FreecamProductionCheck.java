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
            } catch(Throwable failure) {
                failure.printStackTrace(ERROR);throw new RuntimeException("Production Freecam linkage failed",failure);
            }
        }
    }
}
