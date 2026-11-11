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
        Launch.minecraftHome=new File(args[0]);
        List<String> launch=new ArrayList<String>(Arrays.asList("--tweakClass",Tweaker.class.getName(),"--gameDir",args[0],"--version","1.8.9"));
        if(args.length>1&&args[1].equals("optifine"))Collections.addAll(launch,"--tweakClass","optifine.OptiFineForgeTweaker");
        new File(args[0]).mkdirs();Launch.main(launch.toArray(new String[0]));
    }
    public static final class Tweaker implements ITweaker {
        private final FMLTweaker forge=new FMLTweaker();
        @Override public void acceptOptions(List<String> args,File gameDir,File assetsDir,String profile) {forge.acceptOptions(args,gameDir,assetsDir,profile);}
        @Override public void injectIntoClassLoader(LaunchClassLoader loader) {forge.injectIntoClassLoader(loader);}
        @Override public String getLaunchTarget() {Launch.classLoader.registerTransformer("dev.vibe.verify.FreecamProductionCheck$BrandingObserver");return "dev.vibe.verify.FreecamProductionCheck$Probe";}
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
                for(String name:new String[]{"net.minecraft.client.Minecraft","net.minecraft.client.entity.EntityPlayerSP"}) {
                    boolean scoped=false;
                    for(java.lang.reflect.Method method:Class.forName(name,false,loader).getDeclaredMethods())
                        if(method.getName().startsWith("vibe$hotbar$"))scoped=true;
                    if(!scoped)throw new AssertionError("Production hotbar logic scope missing from "+name);
                }
                for(String name:new String[]{"dev.vibe.inventory.HotbarSwitches","dev.vibe.inventory.ItemSwitching"})
                    Class.forName(name,false,loader).getDeclaredMethods();
                OUTPUT.println("Shared hotbar input, player and controller scopes linked through production Forge/OptiFine.");
                for(String name:new String[]{"net.minecraft.client.renderer.entity.RenderItem","net.minecraft.client.renderer.entity.layers.LayerArmorBase","net.minecraft.client.particle.EffectRenderer",
                        "dev.vibe.features.FeatureHooks","dev.vibe.features.MovementForecast","dev.vibe.features.dodge.DodgePlanner","dev.vibe.features.dodge.DodgeTrajectory","dev.vibe.module.impl.combat.ProjectileDodgeModule","dev.vibe.module.impl.combat.PearlCatcherModule","dev.vibe.module.impl.world.AutoFishModule",
                        "dev.vibe.hud.DashboardWidgets","dev.vibe.hud.DashboardSettings","dev.vibe.hud.SessionJournal","dev.vibe.phone.MinesweeperGame","dev.vibe.phone.SudokuGame","dev.vibe.features.EmotePoses","dev.vibe.module.impl.meme.PocketPetsModule","dev.vibe.ui.screen.DeathReviewGui"})Class.forName(name,false,loader).getDeclaredMethods();
                // Minecraft and ModelPlayer can load during OptiFine setup, before a late observer. Inspect LaunchWrapper's actual final class dumps as well.
                java.lang.reflect.Field temp=LaunchClassLoader.class.getDeclaredField("tempFolder");temp.setAccessible(true);File dumps=(File)temp.get(null);
                for(String className:new String[]{"net.minecraft.client.Minecraft","net.minecraft.client.model.ModelPlayer","net.minecraft.client.renderer.entity.RenderItem","net.minecraft.client.renderer.entity.layers.LayerArmorBase","net.minecraft.client.particle.EffectRenderer"}){
                    Class.forName(className,false,loader).getDeclaredMethods();
                    File file=new File(dumps,className.replace('.','/')+".class");if(!file.isFile())throw new AssertionError("Final class dump missing: "+file);new BrandingObserver().transform(className,className,java.nio.file.Files.readAllBytes(file.toPath()));
                }
                for(String required:new String[]{"RenderItem:tint","RenderItem:time","LayerArmorBase:color","LayerArmorBase:translate","EffectRenderer:destroyHook","Minecraft:perspectiveChangeHook","ModelPlayer:before"})if(!BrandingObserver.featureHooks.contains(required))throw new AssertionError("Production feature hook missing: "+required+" from "+BrandingObserver.featureHooks);
                OUTPUT.println("New feature render, glint, block debris, emote restoration and F5 routing hooks verified in production: "+BrandingObserver.featureHooks);
                for(String name:new String[]{"dev.vibe.hud.HudManager","dev.vibe.hud.DynamicIslandRenderer","dev.vibe.hud.BlockCounterRenderer",
                        "dev.vibe.hud.NameProtectRenderer","dev.vibe.hud.TextGlowScope","net.minecraft.client.gui.FontRenderer"})
                    Class.forName(name,false,loader).getDeclaredMethods();
                OUTPUT.println("Block Counter, Dynamic Island and protected-name font hooks linked through production Forge/OptiFine.");
                for(String name:new String[]{"dev.vibe.hud.EffectsRenderer","dev.vibe.hud.ContainerThemes",
                        "net.minecraft.client.gui.inventory.GuiContainer","net.minecraft.client.gui.inventory.GuiInventory",
                        "net.minecraft.client.gui.inventory.GuiChest","net.minecraft.client.gui.inventory.GuiFurnace","net.minecraft.client.renderer.InventoryEffectRenderer"})
                    Class.forName(name,false,loader).getDeclaredMethods();
                Class.forName("net.minecraft.client.gui.inventory.GuiContainer",false,loader).getDeclaredMethod("func_73729_b",int.class,int.class,int.class,int.class,int.class,int.class);
                OUTPUT.println("Effects and native container panel/label hooks linked through production Forge/OptiFine.");
                for(String name:new String[]{"dev.vibe.module.impl.visual.KillEffectsModule","dev.vibe.module.impl.visual.MotionBlurModule","dev.vibe.ui.render.KillEffectsRenderer","dev.vibe.ui.render.MotionBlurRenderer","dev.vibe.hud.KeystrokesRenderer"})Class.forName(name,false,loader).getDeclaredMethods();
                OUTPUT.println("KillEffects, MotionBlur and Keystrokes linked through production Forge/OptiFine.");
                for(String name:new String[]{"dev.vibe.module.impl.meme.GirlfriendModule","dev.vibe.companion.CompanionFollower","dev.vibe.companion.CompanionModel","dev.vibe.ui.render.CompanionRenderer"})Class.forName(name,false,loader).getDeclaredMethods();
                OUTPUT.println("Girlfriend companion and native positional voice linked through production Forge/OptiFine.");
                for(String name:new String[]{"dev.vibe.module.impl.meme.DabModule","dev.vibe.hud.HudBarGlow","net.minecraft.client.model.ModelBiped","net.minecraft.client.model.ModelPlayer"})Class.forName(name,false,loader).getDeclaredMethods();
                if(!BrandingObserver.memeHooks.contains("poseHook")||!BrandingObserver.memeHooks.contains("basePoseHook")||!BrandingObserver.memeHooks.contains("hitHook"))throw new AssertionError("Dab/companion production hooks missing: "+BrandingObserver.memeHooks);
                OUTPUT.println("Dab native body/armour pose and local companion hit hooks linked through production Forge/OptiFine.");
                for(String name:new String[]{"dev.vibe.module.impl.movement.TeleportModule","dev.vibe.teleport.TeleportDestination","dev.vibe.teleport.PortalSprite","dev.vibe.ui.render.TeleportPortalRenderer","dev.vibe.ui.clickgui.LavalampWorkspace","dev.vibe.ui.menu.MenuThemeEditorGui"})Class.forName(name,false,loader).getDeclaredMethods();
                OUTPUT.println("Teleport and portal rendering linked through production Forge/OptiFine.");
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
        public static final Set<String> memeHooks=new HashSet<String>();
        public static final Set<String> featureHooks=new java.util.TreeSet<String>();
        @Override public byte[] transform(String name,String mapped,byte[] bytes) {
            if(bytes==null)return null;
            boolean renderer="net.minecraft.client.renderer.EntityRenderer".equals(mapped);
            if(!renderer&&!"net.minecraft.client.Minecraft".equals(mapped)&&!"net.minecraft.client.model.ModelBiped".equals(mapped)&&!"net.minecraft.client.model.ModelPlayer".equals(mapped)&&!"net.minecraft.client.renderer.entity.RenderItem".equals(mapped)&&!"net.minecraft.client.renderer.entity.layers.LayerArmorBase".equals(mapped)&&!"net.minecraft.client.particle.EffectRenderer".equals(mapped)&&!name.equals("net.minecraftforge.fml.client.SplashProgress$3"))return bytes;
            org.objectweb.asm.tree.ClassNode node=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader(bytes).accept(node,0);
            for(Object entry:node.methods)for(org.objectweb.asm.tree.AbstractInsnNode insn:((org.objectweb.asm.tree.MethodNode)entry).instructions.toArray())
                if(insn instanceof org.objectweb.asm.tree.MethodInsnNode){org.objectweb.asm.tree.MethodInsnNode call=(org.objectweb.asm.tree.MethodInsnNode)insn;if(call.owner.startsWith("dev/vibe/core/startup/"))hooks.add(call.name);if(renderer&&call.owner.equals("dev/vibe/module/impl/movement/FreecamModule"))cameraHooks.add(((org.objectweb.asm.tree.MethodNode)entry).name+":"+call.name);if(call.owner.equals("dev/vibe/module/impl/meme/DabModule")||call.owner.equals("dev/vibe/module/impl/meme/GirlfriendModule")&&call.name.equals("hitHook"))memeHooks.add(call.name);}
            for(Object entry:node.methods)for(org.objectweb.asm.tree.AbstractInsnNode n:((org.objectweb.asm.tree.MethodNode)entry).instructions.toArray())if(n instanceof org.objectweb.asm.tree.MethodInsnNode){org.objectweb.asm.tree.MethodInsnNode call=(org.objectweb.asm.tree.MethodInsnNode)n;if(call.owner.startsWith("dev/vibe/")&&(call.owner.contains("EnchantmentGlintModule")||call.owner.contains("BlockDebrisModule")||call.name.equals("perspectiveChangeHook")||call.owner.contains("EmotePoses")))featureHooks.add(mapped.substring(mapped.lastIndexOf('.')+1)+":"+call.name);}
            return bytes;
        }
    }
}
