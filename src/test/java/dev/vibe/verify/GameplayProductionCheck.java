package dev.vibe.verify;

import java.io.File;
import java.nio.file.Files;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

/** Check final Forge/OptiFine classes rather than development names alone. */
public final class GameplayProductionCheck {
    public static void verify(LaunchClassLoader loader) throws Exception {
        java.lang.reflect.Field field=LaunchClassLoader.class.getDeclaredField("tempFolder");field.setAccessible(true);File dumps=(File)field.get(null);
        verifyWorldEntityTracking(loader, dumps);
        verifyClientHook(loader, dumps, "net.minecraft.client.multiplayer.WorldClient", "func_73029_E",
                "dev/vibe/module/impl/client/QolModule", "barrierParticles", 1);
        verifyClientHook(loader, dumps, "net.minecraft.client.renderer.EntityRenderer", "func_78475_f",
                "dev/vibe/module/impl/visual/NoBobModule", "suppressHook", 1);
        verifyClientHook(loader, dumps, "net.minecraft.client.network.NetHandlerPlayClient", "func_147258_a",
                "dev/vibe/network/InboundSafety", "reject", 1);
        verifyClientHook(loader, dumps, "net.minecraft.client.network.NetHandlerPlayClient", "func_147289_a",
                "dev/vibe/network/InboundSafety", "reject", 1);
        int adjustDrop=0,adjustInventory=0,adjustLiquid=0,adjustJump=0,chatTrack=0;
        int manual=0,guard=0,finish=0,uses=0,slow=0,sprint=0,multi=0,collisions=0,push=0,tab=0,scaffoldSprint=0,shopHide=0,shopKeep=0,chatLimit=0,chatPick=0,chatMouse=0,miningStep=0,miningProgress=0,miningDelay=0,miningBegin=0,miningFinish=0;
        for(String name:new String[]{"net.minecraft.client.Minecraft","net.minecraft.client.multiplayer.PlayerControllerMP","net.minecraft.entity.player.EntityPlayer","net.minecraft.entity.EntityLivingBase","net.minecraft.world.World","net.minecraft.client.entity.EntityPlayerSP","net.minecraft.client.gui.GuiPlayerTabOverlay","net.minecraft.client.gui.inventory.GuiContainer","net.minecraft.client.gui.GuiNewChat","net.minecraft.client.gui.GuiChat"}){
            Class.forName(name,false,loader).getDeclaredMethods();File dump=new File(dumps,name.replace('.','/')+".class");
            ClassNode node=new ClassNode();new ClassReader(Files.readAllBytes(dump.toPath())).accept(node,0);
            for(Object value:node.methods)for(AbstractInsnNode instruction:((MethodNode)value).instructions.toArray())if(instruction instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)instruction;
                if(call.owner.equals("dev/vibe/module/impl/world/NoEscMenuModule")){if(call.name.equals("openManualHook"))manual++;if(call.name.equals("blocksMenuHook"))guard++;}
                if(call.owner.equals("dev/vibe/module/impl/combat/AutoBuffModule")){if(call.name.equals("headUseSentHook"))uses++;if(call.name.equals("finishInputHook"))finish++;}
                if(call.owner.equals("dev/vibe/module/impl/combat/KeepSprintModule")){if(call.name.equals("slowdown"))slow++;if(call.name.equals("resetSprint"))sprint++;}
                if(call.owner.equals("dev/vibe/module/impl/combat/KillAuraModule")&&call.name.equals("multiAttackHook"))multi++;
                if(call.owner.equals("dev/vibe/module/impl/movement/PhaseModule")){if(call.name.equals("collisions"))collisions++;if(call.name.equals("cancelPush"))push++;}
                if(call.owner.equals("dev/vibe/ui/render/RewoundPlayers")&&call.name.equals("tabEntries"))tab++;
                if(call.owner.equals("dev/vibe/module/impl/world/ScaffoldModule")&&call.name.equals("nativeSprintHook"))scaffoldSprint++;
                if(call.owner.equals("dev/vibe/module/impl/client/ShopSaverModule")){
                    MethodNode method=(MethodNode)value;
                    if(call.name.equals("hideHook")){
                        if(!name.equals("net.minecraft.client.entity.EntityPlayerSP")||!method.name.equals("func_71053_j")||!method.desc.equals("()V"))throw new AssertionError("Shop Saver intercepted "+name+"."+method.name+" instead of local closeScreen");
                        shopHide++;
                    }
                    if(call.name.equals("keepContainerHook")){
                        if(!name.equals("net.minecraft.client.gui.inventory.GuiContainer")||!method.name.equals("func_146281_b"))throw new AssertionError("Shop Saver intercepted the wrong container callback: "+method.name);
                        shopKeep++;
                    }
                }
                if(call.owner.equals("dev/vibe/inventory/DropConfirmation")){if(call.name.equals("allowDrop"))adjustDrop++;if(call.name.equals("allowClick"))adjustInventory++;}
                if(call.owner.equals("dev/vibe/movement/AdjustJesus")){if(call.name.equals("collisions"))adjustLiquid++;if(call.name.equals("cancelJump"))adjustJump++;}
                if(call.owner.equals("dev/vibe/hud/ChatActions")&&call.name.equals("track"))chatTrack++;
                if(call.owner.equals("dev/vibe/hud/HudChat")){if(call.name.equals("historyLimit"))chatLimit++;if(call.name.equals("component"))chatPick++;if(call.name.equals("mouseX")||call.name.equals("mouseY"))chatMouse++;}
                if(call.owner.equals("dev/vibe/module/impl/world/FastBreakModule")){
                    if(!(((MethodNode)value).name.equals("vibe$tool$func_180512_c")||((MethodNode)value).name.equals("vibe$tool$c"))||!((MethodNode)value).desc.equals("(Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;)Z"))throw new AssertionError("FastBreak hook outside native mining: "+((MethodNode)value).name);
                    if(call.name.equals("stepHook"))miningStep++;if(call.name.equals("progressHook"))miningProgress++;if(call.name.equals("delayHook"))miningDelay++;
                }
                if(call.owner.equals("dev/vibe/statistics/BlockBreakHooks")){
                    if(!(((MethodNode)value).name.equals("vibe$tool$func_178888_a")||((MethodNode)value).name.equals("vibe$tool$a"))||!((MethodNode)value).desc.equals("(Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;)Z"))throw new AssertionError("Break statistics outside native destroy: "+((MethodNode)value).name);
                    if(call.name.equals("begin"))miningBegin++;if(call.name.equals("finish"))miningFinish++;
                }
            }
        }
        if(adjustDrop!=1||adjustInventory!=1||adjustLiquid<1||adjustJump!=1||chatTrack!=2)throw new AssertionError("Adjust production hooks: drop="+adjustDrop+", inventory="+adjustInventory+", liquid="+adjustLiquid+", jump="+adjustJump+", chat="+chatTrack);
        if(manual!=1||guard!=1||uses!=2||finish<2)throw new AssertionError("Gameplay production hooks: manual="+manual+", guard="+guard+", uses="+uses+", finish="+finish);
        if(slow!=2||sprint!=1||multi!=1)throw new AssertionError("Native combat hooks: slowdown="+slow+", sprint="+sprint+", multi="+multi);
        if(collisions<1||push!=1)throw new AssertionError("Phase native hooks: collision="+collisions+", push="+push);
        if(tab!=1||scaffoldSprint!=1)throw new AssertionError("Presentation/sprint hooks: tab="+tab+", scaffoldSprint="+scaffoldSprint);
        if(shopHide!=1||shopKeep!=1||chatLimit!=2||chatPick!=1||chatMouse!=2)throw new AssertionError("Shop/chat hooks: hide="+shopHide+", keep="+shopKeep+", limits="+chatLimit+", pick="+chatPick+", mouse="+chatMouse);
        if(miningStep!=1||miningProgress!=1||miningDelay!=1||miningBegin!=1||miningFinish<1)throw new AssertionError("Mining hooks: step="+miningStep+", progress="+miningProgress+", delay="+miningDelay+", begin="+miningBegin+", finish="+miningFinish);
        Class.forName("dev.vibe.statistics.BlockBreakHooks",false,loader).getDeclaredMethods();
        Class.forName("dev.vibe.module.impl.world.FastBreakModule",false,loader).getDeclaredMethods();
        Class.forName("dev.vibe.module.impl.client.ShopSaverModule",false,loader).getDeclaredMethods();
        Class.forName("dev.vibe.inventory.ChestSnapshot",false,loader).getDeclaredMethods();
        Class.forName("dev.vibe.hud.HudChat",false,loader).getDeclaredMethods();
        Class.forName("dev.vibe.ui.render.RewoundPlayers",false,loader).getDeclaredMethods();
        for(String name:new String[]{"dev.vibe.module.impl.world.NoEscMenuModule","dev.vibe.module.impl.movement.TargetStrafeModule","dev.vibe.module.impl.world.ScaffoldModule","dev.vibe.module.impl.combat.AutoBuffModule",
                "dev.vibe.module.impl.meme.RewoundPitModule","dev.vibe.pit.PitStatus","dev.vibe.pit.PitMenus","dev.vibe.pit.PitPerk",
                "dev.vibe.module.impl.combat.KeepSprintModule","dev.vibe.module.impl.movement.JesusModule","dev.vibe.module.impl.movement.PhaseModule","dev.vibe.module.impl.client.DisablerModule"})Class.forName(name,false,loader).getDeclaredMethods();
    }

    private static void verifyClientHook(LaunchClassLoader loader, File dumps, String className, String methodName,
            String owner, String hook, int expected) throws Exception {
        Class.forName(className, false, loader).getDeclaredMethods();
        ClassNode node = new ClassNode();
        new ClassReader(Files.readAllBytes(new File(dumps, className.replace('.', '/') + ".class").toPath())).accept(node, 0);
        int found = 0;
        for (Object entry : node.methods) {
            MethodNode method = (MethodNode) entry;
            if (!methodName.equals(method.name)) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (owner.equals(call.owner) && hook.equals(call.name)) found++;
                }
            }
        }
        if (found != expected) throw new AssertionError(className + "." + methodName + " hook " + hook + ": " + found);
    }

    private static void verifyWorldEntityTracking(LaunchClassLoader loader, File dumps) throws Exception {
        String name = "net.minecraft.client.multiplayer.WorldClient";
        Class.forName(name, false, loader).getDeclaredMethods();
        File dump = new File(dumps, name.replace('.', '/') + ".class");
        ClassNode type = new ClassNode();
        new ClassReader(Files.readAllBytes(dump.toPath())).accept(type, 0);
        for (Object entry : type.methods) {
            MethodNode method = (MethodNode) entry;
            if (!method.name.equals("func_73027_a") || !method.desc.equals("(ILnet/minecraft/entity/Entity;)V")) continue;
            boolean assignedId = false;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.owner.equals("net/minecraft/entity/Entity") && call.name.equals("func_145769_d")) assignedId = true;
                if (call.owner.equals("java/util/Set") && call.name.equals("add")) {
                    if (!assignedId) throw new AssertionError("WorldClient tracked an entity before setting its network ID");
                    return;
                }
            }
        }
        throw new AssertionError("WorldClient entity tracking method missing");
    }
}
