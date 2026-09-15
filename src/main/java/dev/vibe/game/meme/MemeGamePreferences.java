package dev.vibe.game.meme;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;
import net.minecraft.client.Minecraft;

/**
 * Per-client game-table preferences.  They intentionally live outside module
 * settings so Chess, Tic Tac Toe and 4 Wins always use the same chat rules.
 */
public final class MemeGamePreferences {
    private static MemeGamePreferences instance;

    private final File file;
    private int chatDelay = 650;
    private boolean checkMessage = true;
    private boolean addGarbage;
    private int robotStrength = 5;

    private MemeGamePreferences(File file) {
        this.file = file;
        load();
    }

    public static synchronized MemeGamePreferences get() {
        if (instance == null) {
            instance = new MemeGamePreferences(new File(Minecraft.getMinecraft().mcDataDir, "vibe/game-table.properties"));
        }
        return instance;
    }

    public int getChatDelay() { return chatDelay; }
    public boolean isCheckMessage() { return checkMessage; }
    public boolean isAddGarbage() { return addGarbage; }
    public int getRobotStrength() { return robotStrength; }

    public void setChatDelay(int value) { chatDelay = clamp(value, 0, 5000); save(); }
    public void setCheckMessage(boolean value) { checkMessage = value; save(); }
    public void setAddGarbage(boolean value) { addGarbage = value; save(); }
    public void setRobotStrength(int value) { robotStrength = clamp(value, 1, 10); save(); }

    private void load() {
        if (!file.isFile()) return;
        Properties values = new Properties();
        try (FileInputStream input = new FileInputStream(file)) {
            values.load(input);
            chatDelay = clamp(Integer.parseInt(values.getProperty("chatDelay", "650")), 0, 5000);
            checkMessage = Boolean.parseBoolean(values.getProperty("checkMessage", "true"));
            addGarbage = Boolean.parseBoolean(values.getProperty("addGarbage", "false"));
            robotStrength = clamp(Integer.parseInt(values.getProperty("robotStrength", "5")), 1, 10);
        } catch (Exception ignored) {
            // A malformed optional preference file must never prevent the game
            // modules from opening.  Defaults remain in effect instead.
        }
    }

    private void save() {
        Properties values = new Properties();
        values.setProperty("chatDelay", String.valueOf(chatDelay));
        values.setProperty("checkMessage", String.valueOf(checkMessage));
        values.setProperty("addGarbage", String.valueOf(addGarbage));
        values.setProperty("robotStrength", String.valueOf(robotStrength));
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try (FileOutputStream output = new FileOutputStream(file)) {
            values.store(output, "Vibe game table preferences");
        } catch (Exception ignored) {
            // The preferences are a convenience only; an unwritable disk does
            // not make a multiplayer match invalid.
        }
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
