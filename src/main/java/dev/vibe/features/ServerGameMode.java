package dev.vibe.features;

import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;

/** Reads the server sidebar and the original tab names, independently of HUD rendering. */
public enum ServerGameMode {
    BEDWARS("Bedwars"), SKYWARS("Skywars"), SKYWARS_TEAMS("Skywars"),
    SURVIVAL_GAMES("Survivalgames"), PIT("Pit"), UNKNOWN("");

    private static final Pattern SKY_TEAM_PREFIX = Pattern.compile("^(?:[✖✕×*]\\s*)?\\[[A-Z]]\\s+");
    private static final Pattern SKY_TEAMS = Pattern.compile("\\b(?:DOUBLES?|DUOS?|TEAMS?|MEGA)\\b");
    private static final Pattern SKY_SOLO = Pattern.compile("\\bSOLO\\b");
    private static WeakReference<EntityPlayerSP> sampledPlayer = new WeakReference<>(null);
    private static WeakReference<ScoreObjective> sampledObjective = new WeakReference<>(null);
    private static String sampledTitle;
    private static int sampledTick;
    private static ServerGameMode sampledMode = UNKNOWN;
    private final String label;

    ServerGameMode(String label) { this.label = label; }

    public String label() { return label; }

    public static ServerGameMode current() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) return UNKNOWN;
        Scoreboard board = mc.theWorld.getScoreboard();
        if (board == null) return UNKNOWN;
        ScorePlayerTeam team = board.getPlayersTeam(mc.thePlayer.getName());
        int slot = team == null || team.getChatFormat() == null ? -1 : team.getChatFormat().getColorIndex();
        ScoreObjective objective = slot < 0 ? null : board.getObjectiveInDisplaySlot(3 + slot);
        if (objective == null) objective = board.getObjectiveInDisplaySlot(1);
        boolean sampledThisTick = mc.thePlayer == sampledPlayer.get() && sampledTick == mc.thePlayer.ticksExisted;
        // Vibe's HUD can temporarily hide the sidebar while vanilla draws Tab.
        if (objective == null) return sampledThisTick ? sampledMode : UNKNOWN;
        String title = objective.getDisplayName();
        if (sampledThisTick && objective == sampledObjective.get() && Objects.equals(title, sampledTitle)) return sampledMode;
        ServerGameMode mode = fromTitle(title);
        if (mode == SKYWARS) {
            boolean solo = SKY_SOLO.matcher(plain(title)).find();
            boolean teams = false;
            for (Score score : board.getSortedScores(objective)) {
                String name = score.getPlayerName();
                if (name.startsWith("#")) continue;
                String row = plain(ScorePlayerTeam.formatPlayerName(board.getPlayersTeam(name), name));
                if (row.startsWith("MODE:") || row.startsWith("TYPE:")) {
                    solo |= SKY_SOLO.matcher(row).find();
                    teams |= SKY_TEAMS.matcher(row).find();
                }
            }
            if (!solo && (teams || SKY_TEAM_PREFIX.matcher(plain(formattedName(mc.thePlayer))).find())) mode = SKYWARS_TEAMS;
        }
        if (sampledPlayer.get() != mc.thePlayer) sampledPlayer = new WeakReference<>(mc.thePlayer);
        if (sampledObjective.get() != objective) sampledObjective = new WeakReference<>(objective);
        sampledTitle = title;
        sampledTick = mc.thePlayer.ticksExisted;
        return sampledMode = mode;
    }

    static ServerGameMode fromTitle(String title) {
        String text = plain(title);
        if (text.matches(".*\\bBED\\s*WARS\\b.*")) return BEDWARS;
        if (text.matches(".*\\bSKY\\s*WARS\\b.*")) return SKY_TEAMS.matcher(text).find() ? SKYWARS_TEAMS : SKYWARS;
        if (text.matches(".*\\bPIT\\b.*")) return PIT;
        if (text.matches(".*\\b(?:BLITZ\\s*SG|BLITZ\\s+SURVIVAL\\s*GAMES|SURVIVAL\\s*GAMES)\\b.*")) return SURVIVAL_GAMES;
        return UNKNOWN;
    }

    public static String formattedName(EntityPlayer player) {
        Minecraft mc = Minecraft.getMinecraft();
        NetworkPlayerInfo info = mc != null && mc.getNetHandler() != null ? mc.getNetHandler().getPlayerInfo(player.getUniqueID()) : null;
        if (info != null && info.getDisplayName() != null) return info.getDisplayName().getFormattedText();
        return ScorePlayerTeam.formatPlayerName(player.getTeam(), player.getName());
    }

    public static char playerNameColor(EntityPlayer player) {
        return nameColor(formattedName(player), player.getName());
    }

    static char nameColor(String formatted, String playerName) {
        if (formatted == null || playerName == null) return 0;
        int start = formatted.lastIndexOf(playerName);
        if (start < 0) return 0;
        char color = 0;
        for (int i = 0; i + 1 < start; i++) {
            if (formatted.charAt(i) != '§') continue;
            char code = Character.toLowerCase(formatted.charAt(++i));
            if (code == 'r') color = 0;
            else if ("0123456789abcdef".indexOf(code) >= 0) color = code;
        }
        return color;
    }

    private static String plain(String value) {
        String text = EnumChatFormatting.getTextWithoutFormattingCodes(value);
        return text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
    }
}
