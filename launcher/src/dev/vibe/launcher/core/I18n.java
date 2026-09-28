package dev.vibe.launcher.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * English/German interface text. Like Vibe's LanguageManager, the English text
 * is the key, so untranslated strings simply stay English. Placeholders are
 * {@code {0}}, {@code {1}} and so on.
 */
public final class I18n {
    private static final Map<String, String> GERMAN = new HashMap<String, String>();
    private static volatile boolean german;

    private I18n() { }

    /**
     * @param setting "auto", "en" or "de"
     * @param clientLanguage the language chosen in Vibe itself, e.g. "German"
     */
    public static void init(String setting, String clientLanguage) {
        if ("de".equals(setting)) german = true;
        else if ("en".equals(setting)) german = false;
        else if (clientLanguage != null && !clientLanguage.isEmpty() && !"auto".equals(clientLanguage)) german = "German".equalsIgnoreCase(clientLanguage);
        else german = "de".equals(Locale.getDefault().getLanguage());
    }

    public static boolean german() { return german; }

    public static String t(String english, Object... arguments) {
        String text = english;
        if (german) {
            String translated = GERMAN.get(english);
            if (translated != null) text = translated;
        }
        for (int index = 0; index < arguments.length; index++) text = text.replace("{" + index + "}", String.valueOf(arguments[index]));
        return text;
    }

    /** "3 minutes ago" style age of a timestamp. */
    public static String ago(long time) {
        if (time <= 0) return "";
        long seconds = Math.max(0, (System.currentTimeMillis() - time) / 1000);
        if (seconds < 60) return t("just now");
        long minutes = seconds / 60;
        if (minutes < 60) return minutes == 1 ? t("1 minute ago") : t("{0} minutes ago", minutes);
        long hours = minutes / 60;
        if (hours < 24) return hours == 1 ? t("1 hour ago") : t("{0} hours ago", hours);
        long days = hours / 24;
        if (days < 30) return days == 1 ? t("yesterday") : t("{0} days ago", days);
        long months = days / 30;
        if (months < 12) return months == 1 ? t("1 month ago") : t("{0} months ago", months);
        long years = days / 365;
        return years <= 1 ? t("1 year ago") : t("{0} years ago", years);
    }

    private static void de(String english, String german) { GERMAN.put(english, german); }

    static {
        // Time
        de("just now", "gerade eben");
        de("1 minute ago", "vor 1 Minute");
        de("{0} minutes ago", "vor {0} Minuten");
        de("1 hour ago", "vor 1 Stunde");
        de("{0} hours ago", "vor {0} Stunden");
        de("yesterday", "gestern");
        de("{0} days ago", "vor {0} Tagen");
        de("1 month ago", "vor 1 Monat");
        de("{0} months ago", "vor {0} Monaten");
        de("1 year ago", "vor 1 Jahr");
        de("{0} years ago", "vor {0} Jahren");

        // Installation
        de("Vibe could not be downloaded. Check your internet connection and try again.",
                "Vibe konnte nicht heruntergeladen werden. Prüfe deine Internetverbindung und versuche es erneut.");
        de("Checking what changed", "Prüfe Änderungen");
        de("Downloading Vibe update", "Lade Vibe-Update herunter");
        de("Downloading Vibe", "Lade Vibe herunter");
        de("Unpacking Vibe", "Entpacke Vibe");
        de("Installing Vibe", "Installiere Vibe");
        de("The Vibe folder is in use. Close programs that have files open in it and try again.",
                "Der Vibe-Ordner wird gerade verwendet. Schließe Programme, die Dateien darin geöffnet haben, und versuche es erneut.");
        de("Looking up {0}", "Suche {0}");
        de("Downloading {0}", "Lade {0} herunter");
        de("Verifying {0}", "Prüfe {0}");
        de("Unpacking {0}", "Entpacke {0}");
        de("Downloading launcher {0}", "Lade Launcher {0} herunter");
        de("The launcher update failed its checksum test and was discarded.",
                "Das Launcher-Update hat die Prüfsummenkontrolle nicht bestanden und wurde verworfen.");
        de("Automatic updates only work when the launcher runs from VibeLauncher.jar.",
                "Automatische Updates funktionieren nur, wenn der Launcher aus VibeLauncher.jar gestartet wird.");
        de("Restart the launcher manually to apply this change.", "Starte den Launcher neu, um die Änderung zu übernehmen.");

        // Mods
        de("{0}: not a .jar mod file", "{0}: keine .jar-Mod-Datei");
        de("{0}: file is too large", "{0}: Datei ist zu groß");
        de("{0}: not a valid mod archive", "{0}: kein gültiges Mod-Archiv");
        de("{0}: OptiFine is already included with Vibe", "{0}: OptiFine ist in Vibe bereits enthalten");
        de("{0}: Vibe is loaded automatically", "{0}: Vibe wird automatisch geladen");
        de("{0}: Fabric mods do not work with Forge 1.8.9", "{0}: Fabric-Mods funktionieren nicht mit Forge 1.8.9");
        de("{0}: made for a newer Minecraft version", "{0}: für eine neuere Minecraft-Version gemacht");
        de("{0}: already installed", "{0}: bereits installiert");
        de("{0} already exists in the mods folder.", "{0} existiert bereits im Mods-Ordner.");

        // Interface
        de("Vibe Launcher is already open.", "Der Vibe Launcher ist bereits geöffnet.");
        de("Vibe is running", "Vibe läuft");
        de("Checking for updates", "Suche nach Updates");
        de("Mod added", "Mod hinzugefügt");
        de("{0} mods added", "{0} Mods hinzugefügt");
        de("Some files were not added", "Einige Dateien wurden nicht hinzugefügt");
        de("Could not add mods", "Mods konnten nicht hinzugefügt werden");
        de("Could not change the mod", "Der Mod konnte nicht geändert werden");
        de("Could not remove the mod", "Der Mod konnte nicht entfernt werden");
        de("Preparing {0}", "Bereite {0} vor");
        de("{0} failed", "{0} fehlgeschlagen");
        de("Show console", "Konsole öffnen");
        de("Vibe updated", "Vibe aktualisiert");
        de("Building Vibe", "Vibe wird gebaut");
        de("Gradle is preparing the build", "Gradle bereitet den Build vor");
        de("Preparing Minecraft & Forge", "Minecraft & Forge werden vorbereitet");
        de("The first build downloads Minecraft and Forge once", "Der erste Build lädt Minecraft und Forge einmalig herunter");
        de("Compiling Vibe", "Vibe wird kompiliert");
        de("Packaging Vibe", "Vibe wird gepackt");
        de("Preparing OptiFine", "OptiFine wird vorbereitet");
        de("Starting Minecraft", "Minecraft startet");
        de("Forge is loading mods", "Forge lädt die Mods");
        de("The build failed. The console shows what went wrong.", "Der Build ist fehlgeschlagen. Die Konsole zeigt, was schiefging.");
        de("Vibe could not be started", "Vibe konnte nicht gestartet werden");
        de("Minecraft closed unexpectedly (exit code {0}).", "Minecraft wurde unerwartet beendet (Exit-Code {0}).");
        de("Minecraft crashed", "Minecraft ist abgestürzt");
        de("Open crash report", "Absturzbericht öffnen");
        de("Stopping Vibe", "Vibe wird beendet");
        de("Updating Vibe", "Vibe wird aktualisiert");
        de("Reinstalling Vibe", "Vibe wird neu installiert");
        de("Reinstalling Java", "Java wird neu installiert");
        de("Java reinstalled", "Java neu installiert");
        de("Java 8 and Java 21 are ready.", "Java 8 und Java 21 sind bereit.");
        de("Java could not be installed", "Java konnte nicht installiert werden");
        de("Build cache cleared", "Build-Cache geleert");
        de("Vibe is compiled from scratch on the next launch.", "Beim nächsten Start wird Vibe komplett neu kompiliert.");
        de("Could not clear the build cache", "Der Build-Cache konnte nicht geleert werden");
        de("Import finished", "Import abgeschlossen");
        de("Everything was already there.", "Es war bereits alles vorhanden.");
        de("{0} items copied from .minecraft.", "{0} Einträge aus .minecraft kopiert.");
        de("Nothing imported", "Nichts importiert");
        de("You have the newest launcher.", "Du hast den neuesten Launcher.");
        de("Version {0} is available.", "Version {0} ist verfügbar.");
        de("Launcher update available", "Launcher-Update verfügbar");
        de("Version {0} is ready to install.", "Version {0} kann installiert werden.");
        de("Update", "Aktualisieren");
        de("Update check failed: {0}", "Update-Prüfung fehlgeschlagen: {0}");
        de("Updating the launcher", "Launcher wird aktualisiert");
        de("Launcher update failed", "Launcher-Update fehlgeschlagen");
        de("Could not open {0}", "{0} konnte nicht geöffnet werden");
        de("Could not open the browser", "Der Browser konnte nicht geöffnet werden");
        de("Add account", "Account hinzufügen");
        de("Starts Vibe straight into its Alt Manager for Microsoft or offline sign-in.",
                "Startet Vibe direkt im Alt Manager für Microsoft- oder Offline-Anmeldung.");
        de("Reload", "Neu laden");
        de("Accounts", "Accounts");
        de("Choose the account Vibe signs in with. New accounts are added in Vibe's own Alt Manager.",
                "Wähle den Account, mit dem Vibe sich anmeldet. Neue Accounts fügst du im Alt Manager von Vibe hinzu.");
        de("Sign-in tokens stay in Vibe's encrypted vault. The launcher only reads names and passes your choice to Vibe; it never sees or stores passwords or tokens.",
                "Anmelde-Tokens bleiben im verschlüsselten Tresor von Vibe. Der Launcher liest nur die Namen und gibt deine Auswahl an Vibe weiter; Passwörter oder Tokens sieht oder speichert er nie.");
        de("Automatic", "Automatisch");
        de("Vibe signs in as {0} (Auto Login)", "Vibe meldet sich als {0} an (Auto Login)");
        de("Vibe uses its Auto Login account, or the last session", "Vibe nutzt seinen Auto-Login-Account oder die letzte Sitzung");
        de("The account vault could not be read", "Der Account-Tresor konnte nicht gelesen werden");
        de("Open Vibe's Alt Manager to repair it: it keeps accounts.vault and accounts.key together in the profile.",
                "Öffne den Alt Manager von Vibe, um ihn zu reparieren: accounts.vault und accounts.key müssen zusammen im Profil liegen.");
        de("No saved accounts yet", "Noch keine gespeicherten Accounts");
        de("Press Add account: Vibe opens its Alt Manager, where you can sign in with Microsoft or create an offline profile. Back here, pick the account to play with.",
                "Klicke auf „Account hinzufügen“: Vibe öffnet den Alt Manager, wo du dich mit Microsoft anmeldest oder ein Offline-Profil anlegst. Danach wählst du hier den Account zum Spielen.");
        de("Microsoft account", "Microsoft-Account");
        de("Offline account", "Offline-Account");
        de("Auto Login", "Auto Login");
        de("Selected", "Ausgewählt");
        de("Use this account", "Diesen Account nutzen");
        de("Appearance", "Aussehen");
        de("The six colour presets of Vibe's main menu. Your choice is shared, so the game and the launcher always match.",
                "Die sechs Farbschemata aus dem Hauptmenü von Vibe. Deine Wahl gilt für beide, damit Spiel und Launcher immer zusammenpassen.");
        de("Launcher", "Launcher");
        de("Animations", "Animationen");
        de("Rotate the player and animate transitions. Motion always pauses while Minecraft is running.",
                "Spieler drehen und Übergänge animieren. Während Minecraft läuft, pausiert jede Bewegung.");
        de("ESP preview", "ESP-Vorschau");
        de("Frame the home page player with Vibe's default 2D ESP.", "Zeigt den Spieler auf der Startseite mit dem Standard-2D-ESP von Vibe.");
        de("Restart the launcher?", "Launcher neu starten?");
        de("The new language is used after a restart.", "Die neue Sprache wird nach einem Neustart verwendet.");
        de("Restart now", "Jetzt neu starten");
        de("Language", "Sprache");
        de("Automatic follows the language chosen in Vibe, then your system.", "„Automatisch“ folgt der Sprache in Vibe, sonst der deines Systems.");
        de("Copy everything", "Alles kopieren");
        de("Game log", "Spiel-Log");
        de("Launcher log", "Launcher-Log");
        de("Clear", "Leeren");
        de("Console", "Konsole");
        de("Build output, Minecraft's log and launcher messages. Errors are highlighted.",
                "Build-Ausgabe, Minecraft-Log und Launcher-Meldungen. Fehler sind hervorgehoben.");
        de("Cancel", "Abbrechen");
        de("WELCOME BACK", "WILLKOMMEN ZURÜCK");
        de("Show Vibe's default 2D ESP around the preview. Drag the player to rotate it.",
                "Zeigt das Standard-2D-ESP von Vibe um die Vorschau. Zieh am Spieler, um ihn zu drehen.");
        de("What's new", "Neuigkeiten");
        de("Changelog", "Changelog");
        de("Commits", "Commits");
        de("No changelog found in this Vibe version.", "In dieser Vibe-Version gibt es kein Changelog.");
        de("The changelog appears once Vibe has been downloaded.", "Das Changelog erscheint, sobald Vibe heruntergeladen ist.");
        de("Loading commits from GitHub\u2026", "Lade Commits von GitHub\u2026");
        de("GitHub is not reachable right now: {0}", "GitHub ist gerade nicht erreichbar: {0}");
        de("Open on GitHub", "Auf GitHub öffnen");
        de("installed", "installiert");
        de("Close the launcher?", "Launcher schließen?");
        de("Vibe is still being downloaded or updated. The download stops and continues next time.",
                "Vibe wird noch heruntergeladen oder aktualisiert. Der Download stoppt und geht beim nächsten Mal weiter.");
        de("Close", "Schließen");
        de("Drop to add mods", "Loslassen, um Mods hinzuzufügen");
        de("Forge 1.8.9 .jar files are copied into Vibe's mods folder", "Forge-1.8.9-.jar-Dateien werden in den Mods-Ordner von Vibe kopiert");
        de("Add mods", "Mods hinzufügen");
        de("Open folder", "Ordner öffnen");
        de("Mods", "Mods");
        de("Add your own Forge 1.8.9 mods. They are loaded together with Vibe; OptiFine is already included.",
                "Füge eigene Forge-1.8.9-Mods hinzu. Sie werden zusammen mit Vibe geladen; OptiFine ist schon dabei.");
        de("Installed mods", "Installierte Mods");
        de("Forge mods (*.jar, *.zip)", "Forge-Mods (*.jar, *.zip)");
        de("{0} of {1} enabled", "{0} von {1} aktiv");
        de("No custom mods yet", "Noch keine eigenen Mods");
        de("Drop .jar files onto the launcher or use Add mods. Vibe and OptiFine do not need to be added.",
                "Zieh .jar-Dateien auf den Launcher oder nutze „Mods hinzufügen“. Vibe und OptiFine musst du nicht hinzufügen.");
        de("Release to add the mods", "Loslassen, um die Mods hinzuzufügen");
        de("Drop mods here or click to browse", "Mods hierher ziehen oder klicken zum Auswählen");
        de("Forge 1.8.9 .jar files \u00b7 duplicates, OptiFine and Fabric mods are skipped",
                "Forge-1.8.9-.jar-Dateien \u00b7 Duplikate, OptiFine und Fabric-Mods werden übersprungen");
        de("for Minecraft {0}", "für Minecraft {0}");
        de("This mod says it was made for another Minecraft version and may not load.",
                "Dieser Mod ist laut eigener Angabe für eine andere Minecraft-Version und lädt eventuell nicht.");
        de("Disable", "Deaktivieren");
        de("Enable", "Aktivieren");
        de("Remove", "Entfernen");
        de("Remove {0}?", "{0} entfernen?");
        de("The file is moved to the recycle bin where supported, otherwise deleted.",
                "Die Datei wird in den Papierkorb verschoben, wenn möglich, sonst gelöscht.");
        de("Vibe auto-login", "Vibe Auto-Login");
        de("No account selected", "Kein Account ausgewählt");
        de("Stop", "Beenden");
        de("Stop Vibe?", "Vibe beenden?");
        de("Minecraft will be closed immediately. Unsaved progress in singleplayer may be lost.",
                "Minecraft wird sofort geschlossen. Ungespeicherter Fortschritt im Einzelspieler kann verloren gehen.");
        de("Vibe logs in as {0}", "Vibe meldet sich als {0} an");
        de("Use Vibe's auto-login", "Auto-Login von Vibe verwenden");
        de("Manage accounts", "Accounts verwalten");
        de("Add Microsoft or offline accounts", "Microsoft- oder Offline-Accounts hinzufügen");
        de("Something went wrong", "Etwas ist schiefgelaufen");
        de("Vibe is not installed yet", "Vibe ist noch nicht installiert");
        de("Press Play to download and set up everything automatically.",
                "Klicke auf Spielen, um alles automatisch herunterzuladen und einzurichten.");
        de("Ready to play", "Bereit zum Spielen");
        de("{0} is running", "{0} läuft");
        de("Have fun! Close Minecraft or press Stop to end the session.",
                "Viel Spaß! Schließe Minecraft oder klicke auf Beenden, um die Sitzung zu beenden.");
        de("Vibe starts right after this step", "Vibe startet direkt nach diesem Schritt");
        de("update installs on launch", "Update wird beim Start installiert");
        de("update available", "Update verfügbar");
        de("RUNNING", "LÄUFT");
        de("STOPPING", "BEENDE");
        de("STARTING", "STARTET");
        de("Preparing", "Vorbereitung");
        de("PLAY", "SPIELEN");
        de("INSTALL & PLAY", "INSTALLIEREN & SPIELEN");
        de("Settings", "Einstellungen");
        de("How Vibe starts, how it stays up to date and where its files live.",
                "Wie Vibe startet, wie es aktuell bleibt und wo seine Dateien liegen.");
        de("Game", "Spiel");
        de("Memory", "Arbeitsspeicher");
        de("Maximum RAM for Minecraft. 3\u20134 GB suit Vibe with shaders and cosmetics; more rarely helps.",
                "Maximaler RAM für Minecraft. 3\u20134 GB passen für Vibe mit Shadern und Cosmetics; mehr hilft selten.");
        de("Keep open", "Offen lassen");
        de("Minimize", "Minimieren");
        de("When Minecraft starts", "Wenn Minecraft startet");
        de("Closing the launcher never closes the game.", "Das Schließen des Launchers beendet nie das Spiel.");
        de("Updates", "Updates");
        de("Update Vibe automatically", "Vibe automatisch aktualisieren");
        de("Before each launch, download the newest Vibe from GitHub. Only changed files are downloaded.",
                "Lädt vor jedem Start das neueste Vibe von GitHub. Nur geänderte Dateien werden heruntergeladen.");
        de("Update now", "Jetzt aktualisieren");
        de("Check now", "Jetzt prüfen");
        de("Vibe", "Vibe");
        de("Install update", "Update installieren");
        de("Launcher {0}", "Launcher {0}");
        de("Java", "Java");
        de("The launcher keeps its own Eclipse Temurin runtimes, verified by checksum: Java 8 runs Minecraft 1.8.9, Java 21 builds Vibe.",
                "Der Launcher nutzt eigene, per Prüfsumme verifizierte Eclipse-Temurin-Runtimes: Java 8 startet Minecraft 1.8.9, Java 21 baut Vibe.");
        de("Reinstall", "Neu installieren");
        de("Reinstall Java?", "Java neu installieren?");
        de("Both runtimes are deleted and downloaded again (about 250 MB).", "Beide Runtimes werden gelöscht und neu heruntergeladen (etwa 250 MB).");
        de("Folders", "Ordner");
        de("Game folder", "Spielordner");
        de("Resource packs", "Ressourcenpakete");
        de("Screenshots", "Screenshots");
        de("Crash reports", "Absturzberichte");
        de("Launcher data", "Launcher-Daten");
        de("Import from Minecraft", "Aus Minecraft importieren");
        de("Copy options.txt and resource packs from your normal .minecraft folder. Nothing in Vibe's profile is overwritten.",
                "Kopiert options.txt und Ressourcenpakete aus deinem normalen .minecraft-Ordner. Im Vibe-Profil wird nichts überschrieben.");
        de("Import", "Importieren");
        de("Troubleshooting", "Fehlerbehebung");
        de("Clear build cache", "Build-Cache leeren");
        de("Fixes builds that fail after an update. The next launch compiles Vibe from scratch.",
                "Hilft, wenn der Build nach einem Update fehlschlägt. Der nächste Start kompiliert Vibe komplett neu.");
        de("Reinstall Vibe", "Vibe neu installieren");
        de("Downloads the complete Vibe source again. Worlds, settings, accounts and mods are kept.",
                "Lädt den kompletten Vibe-Quellcode neu. Welten, Einstellungen, Accounts und Mods bleiben erhalten.");
        de("Reinstall Vibe?", "Vibe neu installieren?");
        de("The source is downloaded again. Your game profile stays untouched.",
                "Der Quellcode wird neu heruntergeladen. Dein Spielprofil bleibt unverändert.");
        de("Open", "Öffnen");
        de("Not installed yet. It is downloaded on the first launch.", "Noch nicht installiert. Wird beim ersten Start heruntergeladen.");
        de("Installed: {0}", "Installiert: {0}");
        de("Checking GitHub\u2026", "Prüfe GitHub\u2026");
        de("GitHub not reachable: {0}", "GitHub nicht erreichbar: {0}");
        de("A newer version is available: {0}", "Eine neuere Version ist verfügbar: {0}");
        de("Up to date.", "Aktuell.");
        de("installed on first launch", "wird beim ersten Start installiert");
        de("Home", "Start");
        de("Downloading Minecraft assets", "Lade Minecraft-Assets herunter");
        de("Only needed once", "Nur beim ersten Mal nötig");
        de("Game mode", "Spielmodus");
        de("Mods queued", "Mods vorgemerkt");
        de("They are added as soon as the current step has finished.", "Sie werden hinzugefügt, sobald der aktuelle Schritt abgeschlossen ist.");
        de("A download failed while building Vibe. Check your connection and try again in a few minutes.",
                "Beim Bauen von Vibe ist ein Download fehlgeschlagen. Prüfe deine Verbindung und versuche es in ein paar Minuten erneut.");
        de("Forge 1.8.9 + OptiFine", "Forge 1.8.9 + OptiFine");
        de("Opens the GTA7 city", "Öffnet die GTA7-Stadt");
        de("Opens the Los Vibes city", "Öffnet die Stadt Los Vibes");
        de("Opens Vibe's account manager", "Öffnet den Account-Manager von Vibe");
    }
}
