package dev.vibe.module.impl;

import dev.vibe.game.meme.GameType;
import dev.vibe.game.meme.MemeGameState;
import dev.vibe.game.meme.MemeGamePreferences;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.ui.MemeGameGui;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumParticleTypes;
import org.lwjgl.input.Keyboard;

/** Shared invitation protocol and lifetime state for the three chat board games. */
public abstract class MemeGameModule extends Module {
    // 1.8.9's C01 chat packet trims outgoing text at one hundred characters.
    private static final int MINECRAFT_CHAT_LIMIT = 100;
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final GameType type;
    private final MemeGameState game;
    private final MemeGamePreferences preferences = MemeGamePreferences.get();
    private final Random random = new Random();
    private String opponent;
    private String requestedOpponent;
    private String pendingOpponent;
    private boolean robot;
    private int localSide;
    private long nextChatAt;
    private long robotMoveAt;
    private long sentAt;
    private String pendingEcho;
    private String notice = "Choose a player or robot to begin";
    private int celebrationTicks;

    protected MemeGameModule(String name, String description, GameType type, MemeGameState game) {
        super(name, description, Category.MEME, Keyboard.KEY_NONE);
        this.type = type;
        this.game = game;
    }

    @Override protected void onEnable() {
        if (minecraft.thePlayer == null) { setEnabled(false); return; }
        minecraft.displayGuiScreen(new MemeGameGui(this));
    }

    public final GameType getType() { return type; }
    public final MemeGameState getGame() { return game; }
    public final MemeGamePreferences getPreferences() { return preferences; }
    public final String getOpponent() { return opponent; }
    public final String getPendingOpponent() { return pendingOpponent; }
    public final String getNotice() { return notice; }
    public final boolean isRobotGame() { return robot; }
    public final boolean hasActiveGame() { return opponent != null; }
    public final boolean isWaitingForAcceptance() { return requestedOpponent != null; }
    public final boolean isMyTurn() { return opponent != null && !game.isFinished() && game.getTurn() == localSide; }
    public final int getLocalSide() { return localSide; }

    /** Sends the specified first-player request; acceptance creates the board. */
    public final boolean request(String player) {
        if (!validPlayer(player)) { notice = "Select a player first"; return false; }
        if (!canChat()) return false;
        requestedOpponent = player; pendingOpponent = null; opponent = null; robot = false; game.reset(); localSide = 0;
        send("@" + player + ", wanna play a game of " + type.getDisplayName() + "?");
        notice = "Request sent to " + player + " — you will start when they accept";
        return true;
    }

    public final boolean accept() {
        if (!validPlayer(pendingOpponent)) { notice = "No pending request"; return false; }
        if (!canChat()) return false;
        opponent = pendingOpponent; pendingOpponent = null; requestedOpponent = null; robot = false; localSide = 1; game.reset();
        send("@" + opponent + " yes lets play a game of " + type.getDisplayName() + "!");
        notice = opponent + " starts as " + sideName(0);
        return true;
    }

    public final void startRobot(boolean playerStarts) {
        requestedOpponent = pendingOpponent = null; opponent = "Robot"; robot = true; localSide = playerStarts ? 0 : 1; game.reset();
        notice = "Playing Robot • strength " + preferences.getRobotStrength() + " • you are " + sideName(localSide);
        if (!playerStarts) robotMoveAt = System.currentTimeMillis() + robotThinkingDelay();
    }

    /** Local move becomes chat-visible only after it has passed the configured delay. */
    public final boolean play(String move) {
        if (!isMyTurn()) { notice = "Wait for " + (robot ? "Robot" : opponent) + " to move"; return false; }
        if (!robot && !canChat()) return false;
        if (!game.move(move)) { notice = "That move is not legal"; return false; }
        if (!robot) send(type.getWireName() + ": " + move);
        notice = game.getStatus();
        finishCompletedMatch();
        if (robot && !game.isFinished()) robotMoveAt = System.currentTimeMillis() + robotThinkingDelay();
        return true;
    }

    public final void cancel() {
        if (opponent != null && !robot) send("@" + opponent + " I will cancel the match of " + type.getDisplayName() + " with you, because i don't want to play anymore");
        clear("Match ended");
    }

    /** Runs from the client tick even while this module's window is closed. */
    public final void tick() {
        long now = System.currentTimeMillis();
        tickCelebration();
        if (pendingEcho != null && now - sentAt > 4500L) { notice = "Chat echo was not seen; continue when your move appears"; pendingEcho = null; }
        if (robot && opponent != null && robotMoveAt > 0L && now >= robotMoveAt && !game.isFinished() && game.getTurn() != localSide) {
            robotMoveAt = 0L;
            String move = game.chooseRobotMove(preferences.getRobotStrength(), random);
            if (move != null && game.move(move)) {
                playOpponentMoveSound();
                notice = "Robot played " + move + " • " + game.getStatus();
                finishCompletedMatch();
            }
        }
    }

    /** Called for every received chat line before name protection can rewrite it. */
    public final void receiveChat(String raw) {
        if (raw == null || minecraft.thePlayer == null) return;
        ChatLine line = ChatLine.parse(raw);
        String content = line.content;
        if (pendingEcho != null && content.contains(pendingEcho)) { pendingEcho = null; notice = "Message sent • " + game.getStatus(); return; }
        String self = minecraft.thePlayer.getName();
        if (line.sender != null && line.sender.equalsIgnoreCase(self)) return;
        Matcher request = invitationPattern().matcher(content);
        if (request.find() && request.group(1).equalsIgnoreCase(self) && validPlayer(line.sender)) {
            pendingOpponent = line.sender; notice = line.sender + " invited you to " + type.getDisplayName(); return;
        }
        Matcher acceptance = acceptancePattern().matcher(content);
        if (acceptance.find() && acceptance.group(1).equalsIgnoreCase(self) && requestedOpponent != null
                && requestedOpponent.equalsIgnoreCase(line.sender)) {
            opponent = requestedOpponent; requestedOpponent = pendingOpponent = null; robot = false; localSide = 0; game.reset();
            notice = opponent + " accepted — you start as " + sideName(0); return;
        }
        Matcher cancelled = cancellationPattern().matcher(content);
        if (cancelled.find() && cancelled.group(1).equalsIgnoreCase(self) && matchesOpponent(line.sender)) { clear(opponent + " cancelled the match"); return; }
        if (!robot && opponent != null && !game.isFinished() && !isMyTurn() && matchesOpponent(line.sender)) {
            Matcher move = movePattern().matcher(content);
            if (move.find()) {
                String value = move.group(1).trim();
                if (!game.move(value)) {
                    String cheater = opponent; send("@" + cheater + " I will cancel the match of " + type.getDisplayName() + " with you, because you cheated");
                    clear("Illegal move received — match cancelled");
                } else {
                    playOpponentMoveSound();
                    notice = opponent + " played " + value + " • " + game.getStatus();
                    finishCompletedMatch();
                }
            }
        }
    }

    private boolean canChat() {
        if (minecraft.thePlayer == null) { notice = "Join a world first"; return false; }
        long remaining = nextChatAt - System.currentTimeMillis();
        if (remaining > 0L) { notice = "Chat delay: wait " + remaining + " ms"; return false; }
        if (preferences.isCheckMessage() && pendingEcho != null) { notice = "Waiting for the previous chat message"; return false; }
        return true;
    }
    private void send(String core) {
        String wire = decorate(core); minecraft.thePlayer.sendChatMessage(wire); nextChatAt = System.currentTimeMillis() + preferences.getChatDelay();
        if (preferences.isCheckMessage()) { pendingEcho = core; sentAt = System.currentTimeMillis(); }
    }
    private String decorate(String core) {
        if (core.length() >= MINECRAFT_CHAT_LIMIT) return core.substring(0, MINECRAFT_CHAT_LIMIT);
        if (!preferences.isAddGarbage()) return core;
        // Never cut protocol text to make room for camouflage. Long requests
        // simply receive less (or no) garbage instead.
        int budget = MINECRAFT_CHAT_LIMIT - core.length() - 2;
        if (budget < 2) return core;
        int each = Math.min(7, Math.max(1, budget / 2));
        String start = randomGarbage(each), end = randomGarbage(each);
        return start + " " + core + " " + end;
    }
    private String randomGarbage(int size) { String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"; StringBuilder value = new StringBuilder(size); for (int i=0;i<size;i++) value.append(alphabet.charAt(random.nextInt(alphabet.length()))); return value.toString(); }
    private boolean matchesOpponent(String sender) { return sender != null && opponent != null && sender.equalsIgnoreCase(opponent); }
    private boolean validPlayer(String player) { return player != null && player.matches("[A-Za-z0-9_]{1,16}"); }
    private long robotThinkingDelay() { return 260L + (11 - preferences.getRobotStrength()) * 85L; }
    private String sideName(int side) { if (type == GameType.CHESS) return side == 0 ? "White" : "Black"; if (type == GameType.TIC_TAC_TOE) return side == 0 ? "X" : "O"; return side == 0 ? "Red" : "Yellow"; }
    private void finishCompletedMatch() {
        if (!game.isFinished()) return;
        String result = game.getStatus();
        if (result.toLowerCase(Locale.ROOT).contains(sideName(localSide).toLowerCase(Locale.ROOT))) celebrateWin();
        clear("Match complete — " + result);
    }

    private void playOpponentMoveSound() {
        if (minecraft.thePlayer != null) minecraft.thePlayer.playSound("random.levelup", 0.45F, 1.0F);
    }

    private void celebrateWin() {
        celebrationTicks = 48;
        if (minecraft.thePlayer != null) {
            minecraft.thePlayer.playSound("random.levelup", 0.8F, 1.15F);
            minecraft.thePlayer.playSound("fireworks.largeBlast", 0.6F, 1.0F);
        }
    }

    private void tickCelebration() {
        if (celebrationTicks-- <= 0 || minecraft.theWorld == null || minecraft.thePlayer == null) return;
        for (int index = 0; index < 4; index++) {
            double x = minecraft.thePlayer.posX + (random.nextDouble() - 0.5D) * 3.0D;
            double y = minecraft.thePlayer.posY + 0.5D + random.nextDouble() * 2.0D;
            double z = minecraft.thePlayer.posZ + (random.nextDouble() - 0.5D) * 3.0D;
            minecraft.theWorld.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, x, y, z,
                    (random.nextDouble() - 0.5D) * 0.09D, random.nextDouble() * 0.12D, (random.nextDouble() - 0.5D) * 0.09D);
        }
    }

    private void clear(String message) {
        opponent = requestedOpponent = pendingOpponent = null;
        robot = false;
        robotMoveAt = 0L;
        pendingEcho = null;
        game.reset();
        notice = message;
    }
    private Pattern invitationPattern() { return Pattern.compile("@([A-Za-z0-9_]{1,16}),?\\s+wanna\\s+play\\s+a\\s+game\\s+of\\s+" + Pattern.quote(type.getDisplayName()) + "\\?", Pattern.CASE_INSENSITIVE); }
    private Pattern acceptancePattern() { return Pattern.compile("@([A-Za-z0-9_]{1,16})\\s+yes\\s+lets\\s+play\\s+a\\s+game\\s+of\\s+" + Pattern.quote(type.getDisplayName()) + "!", Pattern.CASE_INSENSITIVE); }
    private Pattern cancellationPattern() { return Pattern.compile("@([A-Za-z0-9_]{1,16})\\s+I\\s+will\\s+cancel\\s+the\\s+match\\s+of\\s+" + Pattern.quote(type.getDisplayName()) + "\\s+with\\s+you", Pattern.CASE_INSENSITIVE); }
    private Pattern movePattern() { return Pattern.compile("(?:^|\\s)" + Pattern.quote(type.getWireName()) + "\\s*:\\s*([^\\r\\n]+?)(?=\\s+[a-z0-9]{3,7}\\s*$|$)", Pattern.CASE_INSENSITIVE); }

    private static final class ChatLine {
        private static final Pattern ANGLED = Pattern.compile("^\\s*<([A-Za-z0-9_]{1,16})>\\s*(.*)$");
        private static final Pattern COLON = Pattern.compile("^.*?([A-Za-z0-9_]{1,16})\\s*:\\s*(.*)$");
        final String sender, content;
        private ChatLine(String sender, String content) { this.sender = sender; this.content = content; }
        static ChatLine parse(String raw) {
            Matcher angled = ANGLED.matcher(raw); if (angled.matches()) return new ChatLine(angled.group(1), angled.group(2));
            Matcher colon = COLON.matcher(raw); if (colon.matches()) return new ChatLine(colon.group(1), colon.group(2));
            return new ChatLine(null, raw);
        }
    }
}
