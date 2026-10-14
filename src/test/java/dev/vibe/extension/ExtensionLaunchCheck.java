package dev.vibe.extension;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

/** Isolated Forge classloader for each installation, with extensions absent from the base classpath. */
public final class ExtensionLaunchCheck {
    public static void main(String[] args) throws Exception {
        ArrayList<URL> urls = new ArrayList<URL>();
        for (String file : System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator)))
            urls.add(new File(file).toURI().toURL());
        String[] modes = args[2].isEmpty() ? new String[] {"core", "example", "cosmetica", "public"}
                : new String[] {"core", "example", "lego", "both", "cosmetica", "all"};
        for (String mode : modes) {
            try (LaunchClassLoader loader = new LaunchClassLoader(urls.toArray(new URL[0]))) {
                Launch.classLoader = loader; Launch.blackboard = new HashMap<String, Object>();
                loader.addClassLoaderExclusion("net.minecraft.launchwrapper.");
                loader.registerTransformer("net.minecraftforge.fml.common.asm.transformers.EventSubscriptionTransformer");
                Thread.currentThread().setContextClassLoader(loader);
                Class<?> check = Class.forName("dev.vibe.extension.ExtensionIntegrationCheck", true, loader);
                try { check.getMethod("main", String[].class).invoke(null, (Object) new String[] {args[0], mode, args[1], args[2], args[3]}); }
                catch (InvocationTargetException error) { throw new RuntimeException(mode + " extension installation failed", error.getCause()); }
            }
        }
        System.out.println("Extension installations passed: " + java.util.Arrays.toString(modes));
    }
}
