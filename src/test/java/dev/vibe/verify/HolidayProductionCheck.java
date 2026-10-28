package dev.vibe.verify;

import java.io.*;
import java.lang.reflect.*;
import java.time.LocalDate;
import java.util.*;
import net.minecraft.launchwrapper.*;
import net.minecraftforge.fml.common.launcher.FMLTweaker;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** run.bat's actual transformers and Java 8, with no Minecraft window or GL initialization. */
public final class HolidayProductionCheck {
    private static final PrintStream OUTPUT=System.out,ERROR=System.err;
    public static void main(String[] args){
        List<String> options=new ArrayList<>(Arrays.asList("--tweakClass",Tweaker.class.getName(),"--gameDir",args[0],"--version","1.8.9"));
        boolean optifine=args.length>1&&args[1].equals("optifine");System.setProperty("vibe.holiday.optifine",Boolean.toString(optifine));
        if(optifine)Collections.addAll(options,"--tweakClass","optifine.OptiFineForgeTweaker");
        new File(args[0]).mkdirs();Launch.main(options.toArray(new String[0]));
    }
    public static final class Tweaker implements ITweaker {
        private final FMLTweaker forge=new FMLTweaker();
        @Override public void acceptOptions(List<String> args,File gameDir,File assetsDir,String profile){forge.acceptOptions(args,gameDir,assetsDir,profile);}
        @Override public void injectIntoClassLoader(LaunchClassLoader loader){forge.injectIntoClassLoader(loader);}
        @Override public String getLaunchTarget(){return Probe.class.getName();}
        @Override public String[] getLaunchArguments(){return forge.getLaunchArguments();}
    }
    public static final class Capture implements IClassTransformer {
        static final Map<String,Integer> hooks=new HashMap<>();
        @Override public byte[] transform(String name,String mapped,byte[] bytes){
            if(bytes!=null&&"net.minecraft.client.renderer.EntityRenderer".equals(mapped)){
                ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
                for(Object entry:node.methods)for(AbstractInsnNode insn:((MethodNode)entry).instructions.toArray())
                    if(insn instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)insn;if(call.owner.equals("dev/vibe/easter/EasterEggs"))hooks.put(call.name,hooks.getOrDefault(call.name,0)+1);}
            }
            return bytes;
        }
    }
    public static final class Probe {
        public static void main(String[] ignored)throws Exception {
            try{
                LaunchClassLoader loader=Launch.classLoader;loader.registerTransformer(Capture.class.getName());
                Class.forName("net.minecraft.client.renderer.EntityRenderer",false,loader).getDeclaredMethods();
                for(String hook:new String[]{"rainStrength","temperature","canRain","snowBiome"})
                    if(!Capture.hooks.containsKey(hook))throw new AssertionError("Production weather hook missing: "+hook+" in "+Capture.hooks);
                if(Boolean.getBoolean("vibe.holiday.optifine")&&!Capture.hooks.containsKey("rainOff"))throw new AssertionError("OptiFine weather-off switch was not overridden");
                Class<?> calendar=Class.forName("dev.vibe.easter.HolidayCalendar",true,loader);Object dates=calendar.newInstance();
                if(!Boolean.TRUE.equals(calendar.getMethod("chineseNewYear",LocalDate.class).invoke(dates,LocalDate.of(2027,2,6))))throw new AssertionError("Production Chinese New Year failed");
                Class<?> eggs=Class.forName("dev.vibe.easter.EasterEggs",true,loader);Object service=eggs.getField("INSTANCE").get(null);
                Field timeline=eggs.getDeclaredField("timeline");timeline.setAccessible(true);Object schedule=timeline.get(service);
                schedule.getClass().getMethod("command",String.class,LocalDate.class,long.class,boolean.class).invoke(schedule,"christmas",LocalDate.now(),0L,false);
                if(!Boolean.TRUE.equals(eggs.getMethod("canRain",Object.class).invoke(null,new Object[]{null})))throw new AssertionError("Production dry-biome snow failed");
                if(!Float.valueOf(1F).equals(eggs.getMethod("rainStrength",Object.class,float.class).invoke(null,null,0F)))throw new AssertionError("Production rain override failed");
                if(!Boolean.FALSE.equals(eggs.getMethod("rainOff",boolean.class).invoke(null,true)))throw new AssertionError("Production OptiFine override failed");
                OUTPUT.println("Seasonal production checks passed through Forge/OptiFine on Java 8: "+Capture.hooks+", lunar date and loaded snow hooks.");
            }catch(Throwable error){error.printStackTrace(ERROR);throw new RuntimeException("Seasonal production linkage failed",error);}
        }
    }
}
