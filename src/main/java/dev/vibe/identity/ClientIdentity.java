package dev.vibe.identity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/** Persistent local identity selected before the Vibe main menu is shown. */
public final class ClientIdentity {

    private final File file;
    private String gamertag = "";
    private String language = "English";

    public ClientIdentity(File minecraftDirectory) {
        this.file = new File(new File(minecraftDirectory, "vibe"), "identity.properties");
        load();
    }

    public boolean isConfigured() {
        return isValid(gamertag);
    }

    public String getGamertag() {
        return gamertag;
    }

    public String getLanguage() { return language; }

    public void setLanguage(String value) {
        if (value == null) return;
        for (String language : new String[] {"English", "Chinese", "Russian", "Japanese", "Bavarian"}) {
            if (language.equalsIgnoreCase(value.trim())) {
                this.language = language;
                save();
                return;
            }
        }
    }

    public boolean setGamertag(String value) {
        String clean = value == null ? "" : value.trim();
        if (!isValid(clean)) {
            return false;
        }
        gamertag = clean;
        save();
        return true;
    }

    public static boolean isValid(String value) {
        return value != null && value.matches("[A-Za-z0-9_]{3,16}");
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }
        try {
            Properties properties = new Properties();
            FileInputStream stream = new FileInputStream(file);
            try {
                properties.load(stream);
            } finally {
                stream.close();
            }
            String value = properties.getProperty("gamertag", "").trim();
            if (isValid(value)) {
                gamertag = value;
            }
            setLanguageInternal(properties.getProperty("language", "English"));
        } catch (Exception ignored) {
        }
    }

    private void save() {
        File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            return;
        }
        try {
            Properties properties = new Properties();
            properties.setProperty("gamertag", gamertag);
            properties.setProperty("language", language);
            FileOutputStream stream = new FileOutputStream(file);
            try {
                properties.store(stream, "Vibe local identity");
            } finally {
                stream.close();
            }
        } catch (Exception ignored) {
        }
    }

    private void setLanguageInternal(String value) {
        if (value == null) return;
        for (String candidate : new String[] {"English", "Chinese", "Russian", "Japanese", "Bavarian"}) {
            if (candidate.equalsIgnoreCase(value.trim())) {
                language = candidate;
                return;
            }
        }
    }
}
