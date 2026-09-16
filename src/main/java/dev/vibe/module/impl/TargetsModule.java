package dev.vibe.module.impl;

import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.Vibe;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.IAnimals;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.input.Keyboard;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.util.ChatComponentText;

/** Shared entity filter used by aim and ESP modules. */
public final class TargetsModule extends Module {

    private final BooleanSetting players = addSetting(new BooleanSetting("Players", true));
    private final BooleanSetting mobs = addSetting(new BooleanSetting("Mobs", false));
    private final BooleanSetting animals = addSetting(new BooleanSetting("Animals", false));
    private final BooleanSetting invisibles = addSetting(new BooleanSetting("Invisibles", false));
    private final BooleanSetting ignoreTeammates = addSetting(new BooleanSetting("Ignore Teammates", false));
    private final ModeSetting teammateDetection = addSetting(new ModeSetting("Teammate Detection", "Scoreboard",
            () -> ignoreTeammates.isEnabled(), "Name Color", "Chestplate", "Scoreboard"));
    private final Set<String> warnedTargets = new HashSet<String>();
    private Object warningWorld;

    public TargetsModule() {
        super("Targets", "Choose which entities combat and ESP use", Category.COMBAT, Keyboard.KEY_NONE);
        setEnabled(true);
    }

    @Override
    public boolean isToggleable() {
        return false;
    }

    public boolean canTarget(EntityLivingBase entity) {
        if (entity == null || !entity.isEntityAlive() || entity.isDead) {
            return false;
        }
        if (!invisibles.isEnabled() && entity.isInvisible()) {
            return false;
        }
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            if (!players.isEnabled()) return false;
            FriendsModule friends = Vibe.getInstance().getModuleManager().getModule(FriendsModule.class);
            if (friends != null && !friends.shouldAttack(player)) return false;
            return !ignoreTeammates.isEnabled() || !isTeammate(player);
        }
        if (entity instanceof IMob) {
            return mobs.isEnabled();
        }
        return entity instanceof IAnimals && animals.isEnabled();
    }

    /** ESP uses the same entity-type filter but intentionally keeps friends visible. */
    public boolean canVisualize(EntityLivingBase entity) {
        if (entity == null || !entity.isEntityAlive() || entity.isDead) return false;
        if (!invisibles.isEnabled() && entity.isInvisible()) return false;
        if (entity instanceof EntityPlayer) return players.isEnabled();
        if (entity instanceof IMob) return mobs.isEnabled();
        return entity instanceof IAnimals && animals.isEnabled();
    }

    public BooleanSetting getPlayers() { return players; }
    public BooleanSetting getMobs() { return mobs; }
    public BooleanSetting getAnimals() { return animals; }
    public BooleanSetting getInvisibles() { return invisibles; }
    public BooleanSetting getIgnoreTeammates() { return ignoreTeammates; }
    public ModeSetting getTeammateDetection() { return teammateDetection; }

    /** Emits one local warning per named priority target that appears in a world. */
    public void tickWarnings() {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getMinecraft();
        if (minecraft.theWorld == null || minecraft.thePlayer == null || Vibe.getInstance().getTargetManager() == null) return;
        if (warningWorld != minecraft.theWorld) { warningWorld = minecraft.theWorld; warnedTargets.clear(); }
        for (Object object : minecraft.theWorld.playerEntities) {
            if (!(object instanceof EntityPlayer)) continue;
            EntityPlayer player = (EntityPlayer) object;
            if (player != minecraft.thePlayer && Vibe.getInstance().getTargetManager().isTarget(player)
                    && warnedTargets.add(player.getName().toLowerCase(java.util.Locale.ROOT))) {
                minecraft.thePlayer.addChatMessage(new ChatComponentText("§8[§cTarget§8] §f" + player.getName() + " §7appeared in your game."));
            }
        }
    }

    public boolean isTeammate(EntityPlayer other) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getMinecraft();
        if (minecraft.thePlayer == null) return false;
        if (teammateDetection.is("Scoreboard")) {
            net.minecraft.scoreboard.Team mine = minecraft.thePlayer.getTeam();
            return mine != null && mine == other.getTeam();
        }
        if (teammateDetection.is("Chestplate")) {
            ItemStack first = minecraft.thePlayer.getCurrentArmor(2);
            ItemStack second = other.getCurrentArmor(2);
            if (first == null || second == null || !(first.getItem() instanceof ItemArmor) || !(second.getItem() instanceof ItemArmor)) return false;
            ItemArmor a = (ItemArmor) first.getItem(); ItemArmor b = (ItemArmor) second.getItem();
            return a.getArmorMaterial() == ItemArmor.ArmorMaterial.LEATHER && b.getArmorMaterial() == ItemArmor.ArmorMaterial.LEATHER
                    && a.getColor(first) == b.getColor(second);
        }
        return nameColor(minecraft.thePlayer.getDisplayName().getFormattedText()) != 0
                && nameColor(minecraft.thePlayer.getDisplayName().getFormattedText()) == nameColor(other.getDisplayName().getFormattedText());
    }

    /** Uses the same source as teammate detection, including leather chestplate colors. */
    public int teamColor(EntityPlayer player){
        if(player==null)return 0;
        if(teammateDetection.is("Chestplate")){
            ItemStack stack=player.getCurrentArmor(2);if(stack==null||!(stack.getItem() instanceof ItemArmor))return 0;
            ItemArmor armor=(ItemArmor)stack.getItem();return armor.getArmorMaterial()==ItemArmor.ArmorMaterial.LEATHER?0xFF000000|armor.getColor(stack):0;
        }
        String text=teammateDetection.is("Name Color")?player.getDisplayName().getFormattedText():player.getTeam() instanceof net.minecraft.scoreboard.ScorePlayerTeam?((net.minecraft.scoreboard.ScorePlayerTeam)player.getTeam()).getColorPrefix():null;
        char code=nameColor(text);int index="0123456789abcdef".indexOf(code);
        int[] colors={0x000000,0x0000AA,0x00AA00,0x00AAAA,0xAA0000,0xAA00AA,0xFFAA00,0xAAAAAA,0x555555,0x5555FF,0x55FF55,0x55FFFF,0xFF5555,0xFF55FF,0xFFFF55,0xFFFFFF};
        return index<0?0:0xFF000000|colors[index];
    }

    private char nameColor(String value) {
        if (value == null) return 0;
        for (int index = 0; index + 1 < value.length(); index++) {
            if (value.charAt(index) == '§') {
                char code = Character.toLowerCase(value.charAt(index + 1));
                if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) return code;
            }
        }
        return 0;
    }
}
