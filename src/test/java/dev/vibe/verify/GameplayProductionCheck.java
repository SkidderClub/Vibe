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
        int manual=0,guard=0,finish=0,uses=0,slow=0,sprint=0,multi=0,collisions=0,push=0,tab=0,scaffoldSprint=0,shopHide=0,shopKeep=0,chatLimit=0,chatPick=0,chatMouse=0;
        for(String name:new String[]{"net.minecraft.client.Minecraft","net.minecraft.client.multiplayer.PlayerControllerMP","net.minecraft.entity.player.EntityPlayer","net.minecraft.world.World","net.minecraft.client.entity.EntityPlayerSP","net.minecraft.client.gui.GuiPlayerTabOverlay","net.minecraft.client.gui.inventory.GuiContainer","net.minecraft.client.gui.GuiNewChat","net.minecraft.client.gui.GuiChat"}){
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
                if(call.owner.equals("dev/vibe/module/impl/client/ShopSaverModule")){if(call.name.equals("hideHook"))shopHide++;if(call.name.equals("keepContainerHook"))shopKeep++;}
                if(call.owner.equals("dev/vibe/hud/HudChat")){if(call.name.equals("historyLimit"))chatLimit++;if(call.name.equals("component"))chatPick++;if(call.name.equals("mouseX")||call.name.equals("mouseY"))chatMouse++;}
            }
        }
        if(manual!=1||guard!=1||uses!=2||finish<2)throw new AssertionError("Gameplay production hooks: manual="+manual+", guard="+guard+", uses="+uses+", finish="+finish);
        if(slow!=2||sprint!=1||multi!=1)throw new AssertionError("Native combat hooks: slowdown="+slow+", sprint="+sprint+", multi="+multi);
        if(collisions<1||push!=1)throw new AssertionError("Phase native hooks: collision="+collisions+", push="+push);
        if(tab!=1||scaffoldSprint!=1)throw new AssertionError("Presentation/sprint hooks: tab="+tab+", scaffoldSprint="+scaffoldSprint);
        if(shopHide!=1||shopKeep!=1||chatLimit!=2||chatPick!=1||chatMouse!=2)throw new AssertionError("Shop/chat hooks: hide="+shopHide+", keep="+shopKeep+", limits="+chatLimit+", pick="+chatPick+", mouse="+chatMouse);
        Class.forName("dev.vibe.module.impl.client.ShopSaverModule",false,loader).getDeclaredMethods();
        Class.forName("dev.vibe.inventory.ChestSnapshot",false,loader).getDeclaredMethods();
        Class.forName("dev.vibe.hud.HudChat",false,loader).getDeclaredMethods();
        Class.forName("dev.vibe.ui.render.RewoundPlayers",false,loader).getDeclaredMethods();
        for(String name:new String[]{"dev.vibe.module.impl.world.NoEscMenuModule","dev.vibe.module.impl.movement.TargetStrafeModule","dev.vibe.module.impl.world.ScaffoldModule","dev.vibe.module.impl.combat.AutoBuffModule",
                "dev.vibe.module.impl.meme.RewoundPitModule","dev.vibe.pit.PitStatus","dev.vibe.pit.PitMenus","dev.vibe.pit.PitPerk",
                "dev.vibe.module.impl.combat.KeepSprintModule","dev.vibe.module.impl.movement.JesusModule","dev.vibe.module.impl.movement.PhaseModule","dev.vibe.module.impl.client.DisablerModule"})Class.forName(name,false,loader).getDeclaredMethods();
    }
}
