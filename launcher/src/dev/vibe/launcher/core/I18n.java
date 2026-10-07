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
        de("The last Vibe update did not finish. Connect to the internet so it can be repaired.",
                "Das letzte Vibe-Update wurde nicht abgeschlossen. Verbinde dich mit dem Internet, damit es repariert werden kann.");
        de("Please wait", "Bitte warten");
        de("Restart once the current download has finished.", "Starte neu, sobald der aktuelle Download fertig ist.");
        de("Downloading Minecraft assets", "Lade Minecraft-Assets herunter");
        de("Only needed once", "Nur beim ersten Mal nötig");
        de("Game mode", "Spielmodus");
        de("Mods queued", "Mods vorgemerkt");
        de("They are added as soon as the current step has finished.", "Sie werden hinzugefügt, sobald der aktuelle Schritt abgeschlossen ist.");
        de("Forge 1.8.9 + OptiFine", "Forge 1.8.9 + OptiFine");
        de("Opens the GTA7 city", "Öffnet die GTA7-Stadt");
        de("Opens the Los Vibes city", "Öffnet die Stadt Los Vibes");
        de("Opens Vibe's account manager", "Öffnet den Account-Manager von Vibe");

        // Error codes: title and fix of every ErrorCode (see docs/LAUNCHER_ERRORS.md)
        de("No internet connection", "Keine Internetverbindung");
        de("Check your Wi-Fi or network cable and try again.", "Prüfe dein WLAN oder Netzwerkkabel und versuche es erneut.");
        de("Connection blocked", "Verbindung blockiert");
        de("A firewall, proxy, VPN or network filter blocks the connection. Allow Java through the firewall or try another network.",
                "Eine Firewall, ein Proxy, VPN oder Netzwerkfilter blockiert die Verbindung. Erlaube Java in der Firewall oder nutze ein anderes Netzwerk.");
        de("Connection timed out", "Zeitüberschreitung der Verbindung");
        de("The connection is too slow or unstable. Try again, ideally on a faster or wired connection.",
                "Die Verbindung ist zu langsam oder instabil. Versuche es erneut, am besten mit einer schnelleren Verbindung oder per Kabel.");
        de("Secure connection failed", "Sichere Verbindung fehlgeschlagen");
        de("Check that your PC's date and time are correct and turn off HTTPS scanning in your antivirus, then try again.",
                "Prüfe, ob Datum und Uhrzeit deines PCs stimmen, und schalte den HTTPS-Scan deines Virenscanners aus. Versuche es dann erneut.");
        de("GitHub request limit reached", "GitHub-Anfragelimit erreicht");
        de("GitHub allows about 60 requests per hour per network. Wait until the time in the message and try again.",
                "GitHub erlaubt etwa 60 Anfragen pro Stunde und Netzwerk. Warte bis zur genannten Uhrzeit und versuche es erneut.");
        de("Server error", "Serverfehler");
        de("The server has a problem right now. Try again in a few minutes.", "Der Server hat gerade ein Problem. Versuche es in ein paar Minuten erneut.");
        de("Download interrupted", "Download abgebrochen");
        de("The connection dropped during a download. Try again on a stable connection.",
                "Die Verbindung ist während eines Downloads abgerissen. Versuche es mit einer stabilen Verbindung erneut.");
        de("Unexpected answer from the server", "Unerwartete Antwort vom Server");
        de("A Wi-Fi login page, proxy or filter answered instead of the server. Sign in to the network in your browser or use another network.",
                "Statt des Servers hat eine WLAN-Anmeldeseite, ein Proxy oder ein Filter geantwortet. Melde dich im Browser beim Netzwerk an oder nutze ein anderes Netzwerk.");
        de("File not found on the server", "Datei auf dem Server nicht gefunden");
        de("The download no longer exists. Update the launcher; if that does not help, report the error.",
                "Der Download existiert nicht mehr. Aktualisiere den Launcher; hilft das nicht, melde den Fehler.");
        de("No permission to write files", "Keine Schreibrechte");
        de("Your antivirus, a sync tool such as OneDrive or missing rights block the launcher's folder. Allow it and try again.",
                "Dein Virenscanner, ein Sync-Programm wie OneDrive oder fehlende Rechte blockieren den Launcher-Ordner. Gib ihn frei und versuche es erneut.");
        de("Files are in use", "Dateien werden verwendet");
        de("Close Minecraft and programs that have Vibe's files open, then try again. A restart helps if nothing else does.",
                "Schließe Minecraft und Programme, die Dateien von Vibe geöffnet haben, und versuche es erneut. Hilft nichts davon, starte den PC neu.");
        de("Not enough disk space", "Nicht genug Speicherplatz");
        de("Free up disk space and try again. The first start needs about 3 GB.", "Gib Speicherplatz frei und versuche es erneut. Der erste Start braucht etwa 3 GB.");
        de("File system error", "Dateisystemfehler");
        de("A file could not be read or written. Try again; if it repeats, check the drive for errors.",
                "Eine Datei konnte nicht gelesen oder geschrieben werden. Versuche es erneut; passiert es wieder, prüfe das Laufwerk auf Fehler.");
        de("No Minecraft folder found", "Kein Minecraft-Ordner gefunden");
        de("Start the normal Minecraft launcher once, or copy options.txt into Vibe's game folder by hand.",
                "Starte einmal den normalen Minecraft-Launcher oder kopiere options.txt von Hand in den Spielordner von Vibe.");
        de("Launcher data folder unusable", "Launcher-Datenordner nicht nutzbar");
        de("The launcher cannot write to its data folder. Check its permissions or choose another folder (see help).",
                "Der Launcher kann nicht in seinen Datenordner schreiben. Prüfe die Berechtigungen oder wähle einen anderen Ordner (siehe Hilfe).");
        de("No Java for this system", "Kein Java für dieses System");
        de("Adoptium offers no Java for this system. Vibe needs 64-bit Windows, macOS or Linux.",
                "Adoptium bietet kein Java für dieses System an. Vibe braucht 64-Bit-Windows, macOS oder Linux.");
        de("Java download damaged", "Java-Download beschädigt");
        de("The Java download was damaged on the way. Try again; an antivirus or proxy may be altering downloads.",
                "Der Java-Download wurde unterwegs beschädigt. Versuche es erneut; eventuell verändert ein Virenscanner oder Proxy Downloads.");
        de("Java installation unusable", "Java-Installation unbrauchbar");
        de("Settings → Java → Reinstall downloads both runtimes again.", "Einstellungen → Java → Neu installieren lädt beide Runtimes neu herunter.");
        de("Java could not be started", "Java konnte nicht gestartet werden");
        de("An antivirus may have blocked Java. Allow the launcher's runtime folder, then use Settings → Java → Reinstall.",
                "Vermutlich hat ein Virenscanner Java blockiert. Gib den Runtime-Ordner des Launchers frei und nutze dann Einstellungen → Java → Neu installieren.");
        de("Wrong Java version", "Falsche Java-Version");
        de("The build did not find the Java version it needs. Settings → Java → Reinstall sets up Java 8 and Java 21 again.",
                "Der Build hat die benötigte Java-Version nicht gefunden. Einstellungen → Java → Neu installieren richtet Java 8 und Java 21 neu ein.");
        de("Vibe could not be downloaded", "Vibe konnte nicht heruntergeladen werden");
        de("Check your connection to GitHub and try again.", "Prüfe deine Verbindung zu GitHub und versuche es erneut.");
        de("Last update did not finish", "Letztes Update nicht abgeschlossen");
        de("Connect to the internet and press Play: the launcher repairs the update.",
                "Verbinde dich mit dem Internet und klicke auf Spielen: Der Launcher repariert das Update.");
        de("Vibe download is invalid", "Vibe-Download ungültig");
        de("Try again. If it keeps failing, use Settings → Troubleshooting → Reinstall Vibe.",
                "Versuche es erneut. Schlägt es weiter fehl, nutze Einstellungen → Fehlerbehebung → Vibe neu installieren.");
        de("Game profile could not be moved", "Spielprofil konnte nicht übernommen werden");
        de("Close Minecraft and programs that use the game folder, then try again. Your worlds are kept.",
                "Schließe Minecraft und Programme, die den Spielordner nutzen, und versuche es erneut. Deine Welten bleiben erhalten.");
        de("Vibe files are missing", "Vibe-Dateien fehlen");
        de("Settings → Troubleshooting → Reinstall Vibe downloads the source again; your profile is kept.",
                "Einstellungen → Fehlerbehebung → Vibe neu installieren lädt den Quellcode neu; dein Profil bleibt erhalten.");
        de("Build failed", "Build fehlgeschlagen");
        de("The console shows why. Settings → Troubleshooting → Clear build cache often helps.",
                "Die Konsole zeigt den Grund. Oft hilft Einstellungen → Fehlerbehebung → Build-Cache leeren.");
        de("Download during the build failed", "Download beim Build fehlgeschlagen");
        de("Gradle could not download Minecraft, Forge or a library. Check your connection and try again in a few minutes.",
                "Gradle konnte Minecraft, Forge oder eine Bibliothek nicht herunterladen. Prüfe deine Verbindung und versuche es in ein paar Minuten erneut.");
        de("Vibe does not compile", "Vibe lässt sich nicht kompilieren");
        de("Clear the build cache and reinstall Vibe. If it remains, this Vibe version is broken: wait for a fix or report it.",
                "Leere den Build-Cache und installiere Vibe neu. Bleibt der Fehler, ist diese Vibe-Version kaputt: Warte auf einen Fix oder melde ihn.");
        de("Gradle could not be set up", "Gradle konnte nicht eingerichtet werden");
        de("Gradle's own download failed or is damaged. Check your connection and try again (see help).",
                "Der Download von Gradle selbst ist fehlgeschlagen oder beschädigt. Prüfe deine Verbindung und versuche es erneut (siehe Hilfe).");
        de("OptiFine could not be downloaded", "OptiFine konnte nicht heruntergeladen werden");
        de("optifine.net did not deliver the file. Try again later or save the OptiFine JAR by hand (see help).",
                "optifine.net hat die Datei nicht geliefert. Versuche es später erneut oder speichere die OptiFine-JAR von Hand (siehe Hilfe).");
        de("Not enough memory for the build", "Zu wenig Arbeitsspeicher für den Build");
        de("Close other programs and try again. The build needs about 3 GB of free RAM.",
                "Schließe andere Programme und versuche es erneut. Der Build braucht etwa 3 GB freien Arbeitsspeicher.");
        de("Gradle cache damaged", "Gradle-Cache beschädigt");
        de("Clear the build cache. If that does not help, delete Gradle's cache folder (see help).",
                "Leere den Build-Cache. Hilft das nicht, lösche den Cache-Ordner von Gradle (siehe Hilfe).");
        de("Build files are locked", "Build-Dateien sind gesperrt");
        de("Another Gradle build is running, for example in an IDE. Close it or restart the PC, then try again.",
                "Ein anderer Gradle-Build läuft, zum Beispiel in einer IDE. Beende ihn oder starte den PC neu und versuche es erneut.");
        de("Minecraft files are missing", "Minecraft-Dateien fehlen");
        de("Minecraft 1.8.9 was not prepared. Delete the Unimined cache (see help) and start again with internet.",
                "Minecraft 1.8.9 wurde nicht vorbereitet. Lösche den Unimined-Cache (siehe Hilfe) und starte erneut mit Internet.");
        de("The crash report or the console shows the cause. Disable your own mods to test whether one of them is responsible.",
                "Der Absturzbericht oder die Konsole zeigt die Ursache. Deaktiviere deine eigenen Mods, um zu testen, ob einer davon schuld ist.");
        de("Minecraft could not reserve its memory", "Minecraft konnte seinen Arbeitsspeicher nicht reservieren");
        de("Lower Settings → Memory and close other programs, then try again.",
                "Verringere Einstellungen → Arbeitsspeicher, schließe andere Programme und versuche es erneut.");
        de("Minecraft ran out of memory", "Minecraft hat keinen Arbeitsspeicher mehr");
        de("Raise Settings → Memory to 3–4 GB and use fewer or smaller resource packs and mods.",
                "Erhöhe Einstellungen → Arbeitsspeicher auf 3–4 GB und nutze weniger oder kleinere Ressourcenpakete und Mods.");
        de("Graphics driver problem", "Problem mit dem Grafiktreiber");
        de("Update your graphics driver. On laptops, let Java use the dedicated graphics card.",
                "Aktualisiere deinen Grafiktreiber. Auf Laptops lass Java die dedizierte Grafikkarte nutzen.");
        de("A mod prevents the start", "Ein Mod verhindert den Start");
        de("Disable your own mods on the Mods page and start again, then enable them one by one to find the culprit.",
                "Deaktiviere deine eigenen Mods auf der Mods-Seite und starte erneut; aktiviere sie dann einzeln, um den Schuldigen zu finden.");
        de("Game libraries could not be loaded", "Spielbibliotheken konnten nicht geladen werden");
        de("Clear the build cache so the LWJGL libraries are unpacked again, and allow them in your antivirus.",
                "Leere den Build-Cache, damit die LWJGL-Bibliotheken neu entpackt werden, und erlaube sie in deinem Virenscanner.");
        de("Java crashed", "Java ist abgestürzt");
        de("Usually the graphics driver or an overlay (Discord, RivaTuner, …). Update the driver and turn overlays off.",
                "Meist liegt es am Grafiktreiber oder an einem Overlay (Discord, RivaTuner, …). Aktualisiere den Treiber und schalte Overlays aus.");
        de("Rosetta 2 is missing", "Rosetta 2 fehlt");
        de("Minecraft 1.8.9 needs Rosetta 2 on Apple Silicon: run softwareupdate --install-rosetta in Terminal.",
                "Minecraft 1.8.9 braucht auf Apple Silicon Rosetta 2: Führe im Terminal softwareupdate --install-rosetta aus.");
        de("Launcher is already open", "Launcher ist bereits geöffnet");
        de("Switch to the open window. If none is visible, end the Java process in the task manager and start again.",
                "Wechsle zum geöffneten Fenster. Ist keines zu sehen, beende den Java-Prozess im Task-Manager und starte erneut.");
        de("No desktop available", "Kein Desktop verfügbar");
        de("Start the launcher in a desktop session, not over SSH or in a headless environment.",
                "Starte den Launcher in einer Desktop-Sitzung, nicht über SSH oder in einer Umgebung ohne Bildschirm.");
        de("Try again later or download VibeLauncher.jar from the GitHub releases by hand.",
                "Versuche es später erneut oder lade VibeLauncher.jar von Hand aus den GitHub-Releases herunter.");
        de("Launcher update damaged", "Launcher-Update beschädigt");
        de("The download did not match its checksum and was discarded. Try again later.",
                "Der Download passte nicht zur Prüfsumme und wurde verworfen. Versuche es später erneut.");
        de("Launcher does not run from VibeLauncher.jar", "Launcher läuft nicht aus VibeLauncher.jar");
        de("Start the launcher with java -jar VibeLauncher.jar to use updates and restarts.",
                "Starte den Launcher mit java -jar VibeLauncher.jar, um Updates und Neustarts zu nutzen.");
        de("Account vault unreadable", "Account-Tresor nicht lesbar");
        de("Restore accounts.vault together with its matching accounts.key, or rename accounts.vault to start with an empty vault (see help).",
                "Stelle accounts.vault zusammen mit dem passenden accounts.key wieder her oder benenne accounts.vault um, um mit einem leeren Tresor neu zu beginnen (siehe Hilfe).");
        de("Account key missing", "Account-Schlüssel fehlt");
        de("Restore accounts.key from a backup. Without it the saved accounts are lost: rename accounts.vault and sign in again.",
                "Stelle accounts.key aus einem Backup wieder her. Ohne ihn sind die gespeicherten Accounts verloren: Benenne accounts.vault um und melde dich neu an.");
        de("Could not open", "Konnte nicht geöffnet werden");
        de("Open the file, folder or link by hand; the message names it.", "Öffne die Datei, den Ordner oder Link von Hand; die Meldung nennt ihn.");
        de("Launcher could not start", "Launcher konnte nicht starten");
        de("Start it again. If it keeps failing, report it with the launcher log.", "Starte ihn erneut. Schlägt es weiter fehl, melde es mit dem Launcher-Log.");
        de("Not a mod file", "Keine Mod-Datei");
        de("Only Forge mods as .jar or .zip files can be added.", "Nur Forge-Mods als .jar- oder .zip-Datei können hinzugefügt werden.");
        de("Mod file too large", "Mod-Datei zu groß");
        de("Files over 300 MB are refused. Check that it really is a mod.", "Dateien über 300 MB werden abgelehnt. Prüfe, ob es wirklich ein Mod ist.");
        de("Damaged mod file", "Beschädigte Mod-Datei");
        de("The file is not a valid archive. Download the mod again.", "Die Datei ist kein gültiges Archiv. Lade den Mod erneut herunter.");
        de("OptiFine is already included", "OptiFine ist bereits enthalten");
        de("Vibe always starts with OptiFine; no extra copy is needed.", "Vibe startet immer mit OptiFine; eine weitere Kopie ist nicht nötig.");
        de("Vibe is loaded automatically", "Vibe wird automatisch geladen");
        de("Vibe itself must not be added as a mod.", "Vibe selbst darf nicht als Mod hinzugefügt werden.");
        de("Fabric mod", "Fabric-Mod");
        de("Fabric mods do not work with Forge 1.8.9. Look for a Forge 1.8.9 version.",
                "Fabric-Mods funktionieren nicht mit Forge 1.8.9. Suche nach einer Version für Forge 1.8.9.");
        de("Mod for a newer Minecraft", "Mod für ein neueres Minecraft");
        de("This mod is for Forge 1.13 or newer. Look for a 1.8.9 version.", "Dieser Mod ist für Forge 1.13 oder neuer. Suche nach einer Version für 1.8.9.");
        de("Mod already installed", "Mod bereits installiert");
        de("A file with this name is already in the mods folder.", "Eine Datei mit diesem Namen liegt bereits im Mods-Ordner.");
        de("File name already taken", "Dateiname bereits vergeben");
        de("Enabled and disabled copies of this mod exist. Remove one of them in the mods folder.",
                "Es gibt eine aktive und eine deaktivierte Kopie dieses Mods. Entferne eine davon im Mods-Ordner.");
        de("Mod for another Minecraft version", "Mod für eine andere Minecraft-Version");
        de("The mod says it was made for another Minecraft version and may not load.",
                "Der Mod ist laut eigener Angabe für eine andere Minecraft-Version und lädt eventuell nicht.");
        de("Unexpected error", "Unerwarteter Fehler");
        de("Try again. If it repeats, report it on Discord with the launcher log.", "Versuche es erneut. Passiert es wieder, melde es auf Discord mit dem Launcher-Log.");

        // Error messages
        de("(until {0})", "(bis {0})");
        de("GitHub's request limit is reached{0}.", "Das Anfragelimit von GitHub ist erreicht{0}.");
        de("{0} answered HTTP {1}.", "{0} antwortete mit HTTP {1}.");
        de("{0} could not be found.", "{0} wurde nicht gefunden.");
        de("The server did not answer in time.", "Der Server hat nicht rechtzeitig geantwortet.");
        de("GitHub did not return any Vibe commits.", "GitHub hat keine Vibe-Commits geliefert.");
        de("The downloaded Vibe archive is damaged.", "Das heruntergeladene Vibe-Archiv ist beschädigt.");
        de("The downloaded Vibe archive does not contain a Gradle project.", "Das heruntergeladene Vibe-Archiv enthält kein Gradle-Projekt.");
        de("The game profile could not be carried over to the new Vibe version.", "Das Spielprofil konnte nicht in die neue Vibe-Version übernommen werden.");
        de("Adoptium has no {0} build for {1}.", "Adoptium hat keinen {0}-Build für {1}.");
        de("Adoptium returned an incomplete {0} download.", "Adoptium hat einen unvollständigen {0}-Download geliefert.");
        de("The {0} download is damaged (checksum mismatch).", "Der {0}-Download ist beschädigt (Prüfsumme stimmt nicht).");
        de("The {0} archive could not be unpacked.", "Das {0}-Archiv konnte nicht entpackt werden.");
        de("The {0} download does not contain a usable Java installation.", "Der {0}-Download enthält keine nutzbare Java-Installation.");
        de("{0} cannot be replaced. Move the launcher to a folder you can write to, such as your desktop.",
                "{0} kann nicht ersetzt werden. Verschiebe den Launcher in einen Ordner mit Schreibrechten, etwa auf den Desktop.");
        de("The update helper could not be started.", "Das Update-Hilfsprogramm konnte nicht gestartet werden.");
        de("No Minecraft folder was found at {0}.", "Unter {0} wurde kein Minecraft-Ordner gefunden.");
        de("The Vibe source has no Gradle wrapper ({0}).", "Dem Vibe-Quellcode fehlt der Gradle-Wrapper ({0}).");
        de("Java 21 is missing at {0}.", "Java 21 fehlt unter {0}.");
        de("Java 21 could not be started: {0}", "Java 21 konnte nicht gestartet werden: {0}");
        de("Help: {0}", "Hilfe: {0}");
        de("The last Vibe update did not finish and GitHub is not reachable.",
                "Das letzte Vibe-Update wurde nicht abgeschlossen und GitHub ist nicht erreichbar.");
        de("Vibe could not be downloaded from GitHub.", "Vibe konnte nicht von GitHub heruntergeladen werden.");
        de("Only {0} free for {1}, about {2} are needed.", "Für {1} sind nur {0} frei, benötigt werden etwa {2}.");
        de("Click to open the guide for {0}.", "Klicke, um die Anleitung zu {0} zu öffnen.");
        de("How to fix", "So behebst du es");
        de("Error codes", "Fehlercodes");
        de("Every error shows a code such as VL-101. The guide explains each code and how to fix it.",
                "Jeder Fehler zeigt einen Code wie VL-101. Die Anleitung erklärt jeden Code und wie du ihn behebst.");
        de("Open guide", "Anleitung öffnen");
        de("The launcher cannot write to {0}.", "Der Launcher kann nicht in {0} schreiben.");
        de("Launcher log: {0}", "Launcher-Log: {0}");
    }
}
