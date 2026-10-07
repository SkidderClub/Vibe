# Vibe Launcher: Fehlercodes und Lösungen

Jeder Fehler im Vibe Launcher hat einen festen Code im Format **`VL-xxx`**. Hier steht für jeden Code, was
passiert ist, woran es meistens liegt und wie du es Schritt für Schritt behebst.

Du findest den Code an vier Stellen:

- **Meldung unten rechts:** Der Titel beginnt mit dem Code, z. B. `VL-101 · Keine Internetverbindung`.
  Darunter steht die Ursache und ein kurzer Lösungshinweis. **So behebst du es** öffnet den passenden
  Abschnitt dieser Seite.
- **Spielleiste:** Nach einem fehlgeschlagenen Start steht dort der Code mit Lösungshinweis. Fährst du mit
  der Maus darüber, siehst du die genaue Meldung. Ein Klick öffnet diese Seite.
- **Konsole:** Fehler sind rot markiert. Jede Fehlermeldung beginnt mit dem Code, danach folgen Lösung
  und Link.
- **Launcher-Log:** `logs/launcher.log` im Datenordner, mit vollständigem Stacktrace (Einstellungen →
  Fehlerbehebung → Launcher-Log).

Die Hunderterstelle sagt, wo das Problem liegt:

| Codes | Bereich |
| --- | --- |
| [1xx](#1xx-internet-und-server) | Internet und Server |
| [2xx](#2xx-dateien-und-ordner) | Dateien, Ordner und Speicherplatz |
| [3xx](#3xx-java) | Java-Runtimes des Launchers |
| [4xx](#4xx-vibe-download-und-updates) | Download und Updates von Vibe |
| [5xx](#5xx-vibe-bauen-gradle) | Bauen von Vibe mit Gradle |
| [6xx](#6xx-minecraft-starten-und-spielen) | Start und Abstürze von Minecraft |
| [7xx](#7xx-der-launcher-selbst) | Der Launcher selbst |
| [8xx](#8xx-eigene-mods) | Eigene Mods |
| [900](#900-unerwartete-fehler) | Unerwartete Fehler |

## Inhalt

- [Erste Hilfe bei jedem Fehler](#erste-hilfe-bei-jedem-fehler)
- [Wichtige Ordner](#wichtige-ordner)
- [Alle Codes auf einen Blick](#alle-codes-auf-einen-blick)
- [Die Codes im Detail](#1xx-internet-und-server)
- [Probleme ohne Fehlercode](#probleme-ohne-fehlercode)
- [Einen Fehler melden](#einen-fehler-melden)

## Erste Hilfe bei jedem Fehler

1. **Lies die ganze Meldung.** Die zweite Zeile nennt die genaue Ursache, z. B. den Server, der nicht
   antwortet, oder die Datei, die fehlt.
2. **Versuch es noch einmal.** Viele Netzwerkfehler sind nach ein paar Minuten weg. Downloads, die
   abbrechen, werden automatisch zweimal wiederholt.
3. **Schau in die Konsole** (linke Leiste → Konsole). Rote Zeilen zeigen, was schiefging.
4. **Nutze die Reparatur-Knöpfe** unter Einstellungen:
   - *Fehlerbehebung → Build-Cache leeren*: Vibe wird beim nächsten Start komplett neu gebaut.
   - *Fehlerbehebung → Vibe neu installieren*: Der Quellcode wird neu geladen. Welten, Einstellungen,
     Accounts und Mods bleiben erhalten.
   - *Java → Neu installieren*: Java 8 und Java 21 werden neu heruntergeladen (etwa 250 MB).
5. **Starte den PC neu**, wenn Dateien gesperrt sind oder Java-Prozesse hängen.

## Wichtige Ordner

Der **Datenordner** des Launchers liegt hier:

| System | Datenordner |
| --- | --- |
| Windows | `%APPDATA%\VibeLauncher` (z. B. `C:\Users\Name\AppData\Roaming\VibeLauncher`) |
| macOS | `~/Library/Application Support/VibeLauncher` |
| Linux | `~/.local/share/VibeLauncher` (oder `$XDG_DATA_HOME/VibeLauncher`) |

Du öffnest ihn mit Einstellungen → Ordner → *Launcher-Daten*. Darin liegen:

| Pfad | Inhalt |
| --- | --- |
| `source/` | Der Vibe-Quellcode, aus dem Vibe gebaut wird |
| `source/run/client/` | Dein Spielprofil: Welten, Optionen, Accounts, Mods, Absturzberichte |
| `source/run/client/crash-reports/` | Absturzberichte von Minecraft |
| `source/run/client/mods/` | Deine eigenen Mods |
| `source/run/client/vibe/accounts/` | Account-Tresor (`accounts.vault`) und Schlüssel (`accounts.key`) |
| `source/build/` | Build-Ausgabe; `source/build/optifine/` enthält die OptiFine-JAR |
| `runtime/temurin-8`, `runtime/temurin-21` | Java 8 (startet Minecraft) und Java 21 (baut Vibe) |
| `logs/launcher.log` | Launcher-Log |
| `logs/game/` | Ein Log pro Spielstart (Build- und Minecraft-Ausgabe) |
| `launcher.properties` | Einstellungen des Launchers |

Außerdem nutzt Gradle seinen eigenen **Gradle-Ordner** im Benutzerverzeichnis: `%USERPROFILE%\.gradle`
unter Windows, `~/.gradle` unter macOS und Linux (oder den Ordner aus der Umgebungsvariable
`GRADLE_USER_HOME`). Dort liegen Gradle selbst (`wrapper/dists/`), Minecraft, Forge und alle
Bibliotheken (`caches/`).

> **Tipp:** Du kannst den Datenordner verschieben, indem du den Launcher so startest:
> `java -Dvibe.launcher.home="D:\VibeLauncher" -jar VibeLauncher.jar`. Den Gradle-Ordner verschiebst du mit
> der Umgebungsvariable `GRADLE_USER_HOME` (z. B. `D:\gradle`).

## Alle Codes auf einen Blick

| Code | Problem | Kurz-Lösung |
| --- | --- | --- |
| [VL-101](#vl-101) | Keine Internetverbindung | WLAN/Kabel prüfen |
| [VL-102](#vl-102) | Verbindung blockiert | Firewall, Proxy, VPN prüfen |
| [VL-103](#vl-103) | Zeitüberschreitung | Erneut versuchen, bessere Verbindung |
| [VL-104](#vl-104) | Sichere Verbindung fehlgeschlagen | Uhrzeit prüfen, HTTPS-Scan aus |
| [VL-105](#vl-105) | GitHub-Anfragelimit erreicht | Warten oder anderes Netzwerk |
| [VL-106](#vl-106) | Serverfehler | Später erneut versuchen |
| [VL-107](#vl-107) | Download abgebrochen | Stabile Verbindung, erneut versuchen |
| [VL-108](#vl-108) | Unerwartete Antwort vom Server | WLAN-Anmeldeseite, Proxy, Filter |
| [VL-109](#vl-109) | Datei auf dem Server nicht gefunden | Launcher aktualisieren |
| [VL-201](#vl-201) | Keine Schreibrechte | Virenscanner/OneDrive/Rechte |
| [VL-202](#vl-202) | Dateien werden verwendet | Programme schließen, Neustart |
| [VL-203](#vl-203) | Nicht genug Speicherplatz | Platz schaffen |
| [VL-204](#vl-204) | Dateisystemfehler | Laufwerk prüfen |
| [VL-205](#vl-205) | Kein Minecraft-Ordner gefunden | Minecraft einmal starten |
| [VL-206](#vl-206) | Launcher-Datenordner nicht nutzbar | Rechte prüfen, anderen Ordner wählen |
| [VL-301](#vl-301) | Kein Java für dieses System | 64-Bit-System nötig |
| [VL-302](#vl-302) | Java-Download beschädigt | Erneut versuchen |
| [VL-303](#vl-303) | Java-Installation unbrauchbar | Java neu installieren |
| [VL-304](#vl-304) | Java konnte nicht gestartet werden | Virenscanner, Java neu installieren |
| [VL-305](#vl-305) | Falsche Java-Version | Java neu installieren |
| [VL-401](#vl-401) | Vibe konnte nicht heruntergeladen werden | Verbindung zu GitHub prüfen |
| [VL-402](#vl-402) | Letztes Update nicht abgeschlossen | Online gehen und Spielen klicken |
| [VL-403](#vl-403) | Vibe-Download ungültig | Vibe neu installieren |
| [VL-404](#vl-404) | Spielprofil konnte nicht übernommen werden | Programme schließen, erneut versuchen |
| [VL-405](#vl-405) | Vibe-Dateien fehlen | Vibe neu installieren |
| [VL-500](#vl-500) | Build fehlgeschlagen | Konsole lesen, Build-Cache leeren |
| [VL-501](#vl-501) | Download beim Build fehlgeschlagen | Verbindung prüfen, später erneut |
| [VL-502](#vl-502) | Vibe lässt sich nicht kompilieren | Cache leeren, neu installieren, melden |
| [VL-503](#vl-503) | Gradle konnte nicht eingerichtet werden | Gradle-Download löschen |
| [VL-504](#vl-504) | OptiFine konnte nicht heruntergeladen werden | Später erneut oder von Hand |
| [VL-505](#vl-505) | Zu wenig Arbeitsspeicher für den Build | Programme schließen, Auslagerungsdatei |
| [VL-506](#vl-506) | Gradle-Cache beschädigt | Gradle-Cache löschen |
| [VL-507](#vl-507) | Build-Dateien sind gesperrt | Andere Builds beenden, Neustart |
| [VL-508](#vl-508) | Minecraft-Dateien fehlen | Unimined-Cache löschen |
| [VL-600](#vl-600) | Minecraft ist abgestürzt | Absturzbericht lesen, Mods testen |
| [VL-601](#vl-601) | Minecraft konnte seinen Arbeitsspeicher nicht reservieren | Arbeitsspeicher senken |
| [VL-602](#vl-602) | Minecraft hat keinen Arbeitsspeicher mehr | Arbeitsspeicher erhöhen |
| [VL-603](#vl-603) | Problem mit dem Grafiktreiber | Treiber aktualisieren |
| [VL-604](#vl-604) | Ein Mod verhindert den Start | Eigene Mods deaktivieren |
| [VL-605](#vl-605) | Spielbibliotheken konnten nicht geladen werden | Build-Cache leeren |
| [VL-606](#vl-606) | Java ist abgestürzt | Treiber, Overlays aus |
| [VL-607](#vl-607) | Rosetta 2 fehlt | Rosetta installieren (Mac) |
| [VL-701](#vl-701) | Launcher ist bereits geöffnet | Fenster suchen oder Prozess beenden |
| [VL-702](#vl-702) | Kein Desktop verfügbar | In einer Desktop-Sitzung starten |
| [VL-703](#vl-703) | Launcher-Update fehlgeschlagen | Später oder von Hand aktualisieren |
| [VL-704](#vl-704) | Launcher-Update beschädigt | Später erneut versuchen |
| [VL-705](#vl-705) | Launcher läuft nicht aus VibeLauncher.jar | Mit `java -jar` starten |
| [VL-706](#vl-706) | Account-Tresor nicht lesbar | Sicherung zurückholen oder neu anlegen |
| [VL-707](#vl-707) | Account-Schlüssel fehlt | Sicherung oder neu anmelden |
| [VL-708](#vl-708) | Konnte nicht geöffnet werden | Von Hand öffnen |
| [VL-709](#vl-709) | Launcher konnte nicht starten | Neu starten, Log melden |
| [VL-801](#vl-801) | Keine Mod-Datei | Nur `.jar`/`.zip` |
| [VL-802](#vl-802) | Mod-Datei zu groß | Datei prüfen |
| [VL-803](#vl-803) | Beschädigte Mod-Datei | Mod neu herunterladen |
| [VL-804](#vl-804) | OptiFine ist bereits enthalten | Nichts tun |
| [VL-805](#vl-805) | Vibe wird automatisch geladen | Nichts tun |
| [VL-806](#vl-806) | Fabric-Mod | Forge-1.8.9-Version suchen |
| [VL-807](#vl-807) | Mod für ein neueres Minecraft | 1.8.9-Version suchen |
| [VL-808](#vl-808) | Mod bereits installiert | Nichts tun |
| [VL-809](#vl-809) | Dateiname bereits vergeben | Doppelte Kopie entfernen |
| [VL-810](#vl-810) | Mod für eine andere Minecraft-Version | 1.8.9-Version suchen |
| [VL-900](#vl-900) | Unerwarteter Fehler | Erneut versuchen, melden |

## 1xx: Internet und Server

Der Launcher braucht Internet für den ersten Start, für Updates und für den ersten Build. Er spricht mit
diesen Servern; eine Firewall oder ein Filter muss sie erlauben:

| Server | Wofür |
| --- | --- |
| `api.github.com`, `github.com`, `codeload.github.com`, `raw.githubusercontent.com`, `objects.githubusercontent.com`, `release-assets.githubusercontent.com` | Vibe-Quellcode, Updates, Launcher-Updates, Java-Downloads |
| `api.adoptium.net` | Suche nach Java 8 und Java 21 |
| `services.gradle.org` | Gradle selbst (beim ersten Build) |
| `repo.maven.apache.org`, `maven.minecraftforge.net`, `maven.fabricmc.net`, `maven.wagyourtail.xyz`, `jitpack.io` | Bibliotheken für den Build |
| `piston-meta.mojang.com`, `launchermeta.mojang.com`, `libraries.minecraft.net`, `resources.download.minecraft.net` | Minecraft 1.8.9, Bibliotheken und Assets |
| `optifine.net` | OptiFine |
| `api.mojang.com`, `sessionserver.mojang.com`, `textures.minecraft.net` | Skins und Capes in der Vorschau |

Ist Vibe einmal installiert und gebaut, startet es auch **ohne Internet**, solange kein Update
halb fertig ist ([VL-402](#vl-402)).

### VL-101
**Keine Internetverbindung**

Der Launcher findet den Server nicht. Typische Meldung: `api.github.com wurde nicht gefunden.` oder in der
Konsole `UnknownHostException`, `Name or service not known`, `No such host is known`.

**Ursachen:** Kein WLAN oder Kabel, das Netzwerk hat keinen Internetzugang, ein DNS-Problem oder ein
Werbe-/Jugendschutzfilter, der die Adresse sperrt.

**Lösung:**
1. Öffne eine beliebige Webseite im Browser. Klappt das nicht, liegt es am Netzwerk.
2. Verbinde dich neu mit dem WLAN oder stecke das Kabel neu ein, starte notfalls den Router neu.
3. Nutzt du einen DNS-Filter (Pi-hole, AdGuard, Kinderschutz), erlaube die Server aus der Tabelle oben.
4. Windows: `ipconfig /flushdns` in der Eingabeaufforderung leert den DNS-Cache.
5. Klicke erneut auf **Spielen**.

### VL-102
**Verbindung blockiert**

Der Server ist erreichbar, aber die Verbindung wird abgelehnt, oder ein Proxy verlangt eine Anmeldung
(HTTP 401, 403, 407).

**Ursachen:** Firewall, Virenscanner mit Web-Schutz, Firmen- oder Schulnetz, VPN, Proxy mit Anmeldung.

**Lösung:**
1. Erlaube Java in der Firewall. Betroffen sind das Java, mit dem du den Launcher startest
   (`javaw.exe`/`java.exe`), und die Runtimes im Datenordner unter `runtime\`.
2. Schalte ein VPN testweise aus.
3. Der Launcher nutzt die Proxy-Einstellungen des Systems. Proxys mit Benutzername und Passwort werden
   nicht unterstützt: Nutze dann ein anderes Netzwerk, z. B. einen Handy-Hotspot.
4. In Schul- oder Firmennetzen sind GitHub oder Maven oft gesperrt. Spiele dann in einem anderen Netz.

### VL-103
**Zeitüberschreitung der Verbindung**

Der Server hat nicht rechtzeitig geantwortet (Meldung `Der Server hat nicht rechtzeitig geantwortet.`,
`Read timed out`, `connect timed out`).

**Lösung:**
1. Versuche es erneut. Abgebrochene Downloads wiederholt der Launcher schon zweimal von selbst.
2. Pausiere andere große Downloads, Streams oder Updates.
3. Nutze ein Netzwerkkabel oder geh näher an den Router.
4. Schalte VPN oder Proxy testweise aus.

### VL-104
**Sichere Verbindung fehlgeschlagen**

Die verschlüsselte HTTPS-Verbindung konnte nicht aufgebaut werden (`SSLHandshakeException`,
`PKIX path building failed`, `unable to find valid certification path`).

**Ursachen:**
- Datum oder Uhrzeit des PCs sind falsch, dann wirken alle Zertifikate ungültig.
- Ein Virenscanner prüft HTTPS-Verbindungen und tauscht dabei Zertifikate aus (z. B. „HTTPS-Scan“,
  „Web-Schutz“, „verschlüsselte Verbindungen prüfen“ bei Avast, AVG, Kaspersky, ESET, Bitdefender).
- Ein Firmen- oder Schul-Proxy bricht HTTPS auf.
- Der Launcher läuft mit einem sehr alten Java 8, dem neue Stammzertifikate fehlen.

**Lösung:**
1. Stelle Datum, Uhrzeit und Zeitzone automatisch ein (Windows: Einstellungen → Zeit und Sprache).
2. Schalte die HTTPS-Prüfung deines Virenscanners aus oder füge eine Ausnahme für Java hinzu.
3. Aktualisiere das Java, mit dem du den Launcher öffnest, z. B. auf ein aktuelles Eclipse Temurin 8, 17
   oder 21 (https://adoptium.net).
4. Versuche es in einem anderen Netzwerk.

### VL-105
**GitHub-Anfragelimit erreicht**

GitHub erlaubt ohne Anmeldung etwa 60 Anfragen pro Stunde und Netzwerk (IP-Adresse). Die Meldung nennt die
Uhrzeit, ab der es wieder geht, z. B. `Das Anfragelimit von GitHub ist erreicht (bis 14:05).`

**Ursachen:** Viele Starts oder Update-Prüfungen hintereinander, oder viele Leute im selben Netz
(Schule, WG, VPN), die GitHub nutzen.

**Lösung:**
1. Warte bis zur genannten Uhrzeit (höchstens eine Stunde).
2. Ist Vibe schon installiert, schalte Einstellungen → Updates → *Vibe automatisch aktualisieren* aus: Dann
   startet Vibe ohne GitHub-Anfrage.
3. Alternativ ein anderes Netzwerk nutzen (z. B. Handy-Hotspot) oder das VPN ausschalten.

### VL-106
**Serverfehler**

Der Server antwortet mit einem Fehler (HTTP 5xx oder einem anderen unerwarteten Status). Die Meldung nennt
den Server, z. B. `api.github.com antwortete mit HTTP 502.`

**Lösung:**
1. Warte ein paar Minuten und versuche es erneut.
2. Prüfe, ob der Dienst gestört ist: https://www.githubstatus.com für GitHub.

### VL-107
**Download abgebrochen**

Die Verbindung ist mitten im Download abgerissen (`Connection reset`, `ended early`, `Premature EOF`).
Große Downloads hat der Launcher dann bereits dreimal versucht.

**Lösung:**
1. Prüfe die Verbindung und versuche es erneut.
2. Schalte VPN oder Proxy testweise aus.
3. Manche Virenscanner brechen große Downloads ab: Füge eine Ausnahme für Java hinzu.

### VL-108
**Unerwartete Antwort vom Server**

Statt der erwarteten Daten kam etwas anderes zurück, meistens eine Webseite. Typisch für:

- **WLAN mit Anmeldeseite** (Hotel, Zug, Café, Uni-Gastnetz): Du bist noch nicht angemeldet.
- Proxys, Jugendschutz- oder Werbefilter, die eine Sperrseite zeigen.
- Eine Umleitung von HTTPS auf HTTP, die der Launcher aus Sicherheitsgründen ablehnt.

**Lösung:**
1. Öffne eine Webseite im Browser und melde dich beim WLAN an, falls eine Anmeldeseite erscheint.
2. Prüfe Filter und Proxy oder nutze ein anderes Netzwerk.
3. Versuche es erneut.

### VL-109
**Datei auf dem Server nicht gefunden**

Der Server meldet HTTP 404: Die Datei, die der Launcher laden will, gibt es nicht mehr. Das passiert, wenn
ein alter Launcher eine Adresse nutzt, die sich geändert hat.

**Lösung:**
1. Aktualisiere den Launcher (Einstellungen → Updates) oder lade die neueste `VibeLauncher.jar` aus den
   [GitHub-Releases](https://github.com/SkidderClub/Vibe/releases).
2. Hilft das nicht, [melde den Fehler](#einen-fehler-melden).

## 2xx: Dateien und Ordner

### VL-201
**Keine Schreibrechte**

Der Launcher darf eine Datei nicht schreiben oder löschen (`Access is denied`, `Permission denied`,
`AccessDeniedException`).

**Ursachen und Lösung:**
1. **Virenscanner:** Füge den Datenordner und den Gradle-Ordner als Ausnahme hinzu.
2. **Windows „Überwachter Ordnerzugriff“** (Windows-Sicherheit → Viren- & Bedrohungsschutz →
   Ransomware-Schutz): Erlaube `java.exe` und `javaw.exe` oder schalte die Funktion testweise aus.
3. **OneDrive/Dropbox:** Liegt der Launcher oder sein Datenordner in einem synchronisierten Ordner, verschiebe
   ihn heraus (siehe Tipp unter [Wichtige Ordner](#wichtige-ordner)).
4. **Linux/macOS:** Wurde der Launcher einmal mit `sudo` gestartet, gehören Dateien `root`. Repariere das mit
   `sudo chown -R "$USER" ~/.local/share/VibeLauncher ~/.gradle` (macOS:
   `sudo chown -R "$USER" ~/Library/Application\ Support/VibeLauncher ~/.gradle`). Starte den Launcher nie
   mit `sudo`.
5. Starte den Launcher **nicht** als Administrator, wenn er vorher normal lief, sonst entstehen Dateien, die
   du später nicht mehr ändern darfst.

### VL-202
**Dateien werden verwendet**

Eine Datei oder ein Ordner ist gerade von einem anderen Programm geöffnet (`being used by another process`,
`Der Vibe-Ordner wird gerade verwendet`).

**Lösung:**
1. Schließe Minecraft und andere Launcher.
2. Schließe Explorer-Fenster, Editoren oder IDEs, die Dateien im Datenordner geöffnet haben.
3. Beende hängende Java-Prozesse: Windows-Task-Manager → „OpenJDK Platform binary“ bzw.
   „Java(TM) Platform SE binary“ → Task beenden. macOS/Linux: Aktivitätsanzeige bzw. `pkill -f GradleWrapperMain`.
4. Virenscanner und die Windows-Suche halten Dateien manchmal kurz fest: einen Moment warten und erneut
   versuchen.
5. Hilft nichts davon, starte den PC neu.

### VL-203
**Nicht genug Speicherplatz**

Der Launcher prüft vor Downloads und Builds, ob genug Platz frei ist, und bricht sonst mit einer Meldung wie
`Für C:\Users\…\VibeLauncher sind nur 800 MB frei, benötigt werden etwa 1,6 GB.` ab. Der Code erscheint auch,
wenn das Laufwerk während eines Downloads oder Builds voll läuft.

**Platzbedarf (ungefähr):**

| Was | Wo | Größe |
| --- | --- | --- |
| Vibe-Quellcode | Datenordner | 250 MB |
| Java 8 und Java 21 | Datenordner | 450 MB |
| Build-Ausgabe | Datenordner | 500 MB |
| Gradle, Minecraft, Forge, Bibliotheken, Assets | Gradle-Ordner | 1–1,5 GB |

Beim ersten Start also etwa **3 GB**, verteilt auf Datenordner und Gradle-Ordner (meist beide auf `C:`).

**Lösung:**
1. Gib Speicherplatz frei (Windows: Einstellungen → System → Speicher → Temporäre Dateien).
2. Oder verschiebe Datenordner und Gradle-Ordner auf ein anderes Laufwerk (siehe [Wichtige Ordner](#wichtige-ordner)).
3. Versuche es erneut.

### VL-204
**Dateisystemfehler**

Eine Datei konnte aus einem anderen Grund nicht gelesen oder geschrieben werden.

**Lösung:**
1. Versuche es erneut.
2. Passiert es wieder, prüfe das Laufwerk: Windows: Rechtsklick auf das Laufwerk → Eigenschaften → Tools →
   Prüfen. macOS: Festplattendienstprogramm → Erste Hilfe.
3. Liegt der Datenordner auf einem USB-Stick oder Netzlaufwerk, verschiebe ihn auf eine interne Festplatte.

### VL-205
**Kein Minecraft-Ordner gefunden**

Erscheint bei Einstellungen → Ordner → *Aus Minecraft importieren*: Der normale Minecraft-Ordner
(`%APPDATA%\.minecraft`, `~/Library/Application Support/minecraft` oder `~/.minecraft`) existiert nicht.
Das ist kein Problem für Vibe, es wird nur nichts importiert.

**Lösung:**
1. Starte den offiziellen Minecraft-Launcher einmal, dann gibt es den Ordner.
2. Oder kopiere `options.txt` und Ressourcenpakete von Hand in den Spielordner (Einstellungen → Ordner →
   *Spielordner* bzw. *Ressourcenpakete*).

### VL-206
**Launcher-Datenordner nicht nutzbar**

Der Launcher kann beim Start nicht in seinen Datenordner schreiben und beendet sich mit einem Hinweisfenster.

**Lösung:**
1. Prüfe, ob das Laufwerk voll ist ([VL-203](#vl-203)).
2. Prüfe die Rechte am Ordner ([VL-201](#vl-201)), unter Linux/macOS besonders nach einem Start mit `sudo`.
3. Lege den Datenordner woanders an:
   `java -Dvibe.launcher.home="D:\VibeLauncher" -jar VibeLauncher.jar`
   (unter macOS/Linux z. B. `java -Dvibe.launcher.home="$HOME/VibeLauncher" -jar VibeLauncher.jar`).

## 3xx: Java

Der Launcher bringt **eigene Java-Versionen** mit: Java 8 startet Minecraft 1.8.9, Java 21 baut Vibe. Sie
kommen von Eclipse Temurin (Adoptium), werden per Prüfsumme kontrolliert und liegen im Datenordner unter
`runtime/`. Ein auf dem PC installiertes Java oder `JAVA_HOME` spielt **keine Rolle**; es wird nur zum
Öffnen der `VibeLauncher.jar` gebraucht (siehe [Probleme ohne Fehlercode](#probleme-ohne-fehlercode)).

### VL-301
**Kein Java für dieses System**

Adoptium bietet für dein Betriebssystem oder deinen Prozessor kein passendes Java an.

**Lösung:** Vibe braucht ein **64-Bit**-Windows, macOS (Intel oder Apple Silicon mit Rosetta 2, siehe
[VL-607](#vl-607)) oder 64-Bit-Linux auf einem x64-Prozessor. Auf 32-Bit-Windows, sehr alten Systemen oder
exotischen Prozessoren läuft der Launcher nicht; Linux auf ARM bekommt zwar Java, Minecraft 1.8.9 selbst läuft
dort aber nicht ([VL-605](#vl-605)). Prüfe unter Windows: Einstellungen → System → Info → Systemtyp.

### VL-302
**Java-Download beschädigt**

Das heruntergeladene Java passt nicht zu seiner Prüfsumme oder das Archiv lässt sich nicht entpacken. Die Datei
wird verworfen.

**Lösung:**
1. Versuche es erneut, die Datei wird neu geladen.
2. Passiert es wieder, verändert vermutlich ein Virenscanner oder Proxy Downloads: Ausnahme für Java
   hinzufügen oder ein anderes Netzwerk nutzen.
3. Prüfe den freien Speicherplatz ([VL-203](#vl-203)).

### VL-303
**Java-Installation unbrauchbar**

Im Java-Download fehlt eine nutzbare Installation, oder das installierte Java ist unvollständig (z. B. hat ein
Virenscanner Dateien gelöscht).

**Lösung:**
1. Einstellungen → Java → **Neu installieren**.
2. Hilft das nicht, schließe den Launcher, lösche im Datenordner den Ordner `runtime` und starte neu.
3. Füge den Ordner `runtime` als Ausnahme im Virenscanner hinzu.

### VL-304
**Java konnte nicht gestartet werden**

Das Java des Launchers ließ sich nicht ausführen (`Cannot run program`, `A problem occurred starting process`,
`CreateProcess error=…`).

**Ursachen:** Ein Virenscanner blockiert oder hat `java.exe` in die Quarantäne verschoben, die Datei ist nicht
ausführbar (Linux/macOS), oder das System ist 32-Bit (`error=193`, „ist keine zulässige Win32-Anwendung“).

**Lösung:**
1. Schau in die Quarantäne deines Virenscanners und stelle Java wieder her; füge den Ordner `runtime` als
   Ausnahme hinzu.
2. Einstellungen → Java → **Neu installieren**.
3. Linux/macOS: Liegt der Datenordner auf einem Laufwerk mit `noexec`, verschiebe ihn ([VL-206](#vl-206)).
4. 32-Bit-Windows wird nicht unterstützt ([VL-301](#vl-301)).

### VL-305
**Falsche Java-Version**

Gradle hat für den Build nicht die Java-Version gefunden, die es braucht (`No matching toolchains found`,
`Unsupported class file major version`).

**Lösung:**
1. Einstellungen → Java → **Neu installieren**. Danach sind Java 8 und Java 21 wieder sauber eingerichtet.
2. Hast du `java8Home` oder `jdk21Home` in `launcher.properties` von Hand geändert, lösche diese Zeilen. Der
   Launcher erkennt ein Java mit falscher Version inzwischen selbst und nutzt dann sein eigenes.
3. Danach Einstellungen → Fehlerbehebung → **Build-Cache leeren** und erneut starten.

## 4xx: Vibe-Download und Updates

### VL-401
**Vibe konnte nicht heruntergeladen werden**

Beim ersten Start konnte der Vibe-Quellcode nicht geladen werden. Meist steht stattdessen der genauere Code
der Ursache da, z. B. [VL-101](#vl-101) oder [VL-105](#vl-105).

**Lösung:**
1. Prüfe die Internetverbindung und ob https://github.com/SkidderClub/Vibe im Browser lädt.
2. Folge dem Abschnitt zum genaueren Code, falls in der Konsole einer genannt ist.
3. Versuche es erneut.

### VL-402
**Letztes Update nicht abgeschlossen**

Ein Vibe-Update wurde unterbrochen (Launcher geschlossen, Absturz, Strom weg). Damit Vibe nicht aus einer
Mischung zweier Versionen gebaut wird, startet der Launcher erst nach der Reparatur. Ohne Internet ist das
nicht möglich; dann siehst du oft den Code der Netzwerkursache (z. B. [VL-101](#vl-101)).

**Lösung:**
1. Verbinde dich mit dem Internet und klicke auf **Spielen**: Das Update wird vollständig nachgeladen.
2. Alternativ Einstellungen → Fehlerbehebung → **Vibe neu installieren**.

Dein Spielprofil (Welten, Einstellungen, Accounts, Mods) bleibt in beiden Fällen erhalten.

### VL-403
**Vibe-Download ungültig**

Das heruntergeladene Vibe-Archiv ist beschädigt oder enthält kein Gradle-Projekt.

**Lösung:**
1. Versuche es erneut.
2. Einstellungen → Fehlerbehebung → **Vibe neu installieren**.
3. Ein Virenscanner oder Proxy kann Downloads verändern: Ausnahme hinzufügen oder anderes Netzwerk testen.
4. Hilft nichts, [melde den Fehler](#einen-fehler-melden): Dann ist der aktuelle Stand auf GitHub fehlerhaft.

### VL-404
**Spielprofil konnte nicht übernommen werden**

Bei einem vollständigen Update wird der alte Quellcode-Ordner gegen den neuen getauscht und dein Spielprofil
(`run/`) hinübergeschoben. Das hat nicht geklappt, meist weil Dateien geöffnet waren. Der Launcher stellt den
alten Zustand wieder her.

**Lösung:**
1. Schließe Minecraft, Explorer-Fenster im Datenordner und andere Programme ([VL-202](#vl-202)).
2. Klicke erneut auf **Spielen** oder Einstellungen → Updates → *Jetzt aktualisieren*.
3. **Wichtig:** Lösche keine Ordner `source-old-…` im Datenordner, die `run/client` enthalten. Der Launcher
   holt ein dort liegendes Profil beim nächsten Start automatisch zurück.

### VL-405
**Vibe-Dateien fehlen**

Im installierten Quellcode fehlen wichtige Dateien, z. B. der Gradle-Wrapper (`gradle/wrapper/gradle-wrapper.jar`).
Das passiert, wenn Dateien von Hand gelöscht oder von einem Virenscanner entfernt wurden.

**Lösung:** Einstellungen → Fehlerbehebung → **Vibe neu installieren**. Dein Spielprofil bleibt erhalten.

## 5xx: Vibe bauen (Gradle)

Vor jedem Start baut der Launcher Vibe mit Gradle aus dem Quellcode und startet dann Forge 1.8.9 mit
OptiFine. Der **erste Build dauert 5–15 Minuten**, weil Gradle, Minecraft, Forge und alle Bibliotheken
heruntergeladen werden; danach geht es deutlich schneller. Die komplette Ausgabe steht in der Konsole und in
`logs/game/<Datum>.log`.

### VL-500
**Build fehlgeschlagen**

Der Build ist aus einem Grund fehlgeschlagen, den der Launcher nicht genauer zuordnen kann. Die Meldung zeigt
die erste und die letzte Zeile von Gradles „What went wrong“.

**Lösung:**
1. Öffne die Konsole und lies den Abschnitt nach `* What went wrong:`.
2. Einstellungen → Fehlerbehebung → **Build-Cache leeren**, dann erneut starten.
3. Hilft das nicht: **Vibe neu installieren**.
4. Bleibt der Fehler, [melde ihn](#einen-fehler-melden) mit dem Spiel-Log.

### VL-501
**Download beim Build fehlgeschlagen**

Gradle konnte Minecraft, Forge oder eine Bibliothek nicht laden (`Could not resolve …`, `Could not GET …`,
`Read timed out`, `Received status code 5xx`).

**Lösung:**
1. Prüfe die Verbindung und versuche es in ein paar Minuten erneut; Maven-Server sind manchmal kurz
   überlastet.
2. Erlaube die Build-Server aus der [Tabelle unter 1xx](#1xx-internet-und-server) in Firewall und Filtern.
3. Schalte VPN und Proxy testweise aus.
4. Bricht es immer an derselben Datei ab, lösche den Gradle-Cache ([VL-506](#vl-506)).

### VL-502
**Vibe lässt sich nicht kompilieren**

Der Java-Compiler meldet Fehler im Vibe-Quellcode (`Compilation failed`, `error: cannot find symbol`).

**Lösung:**
1. Einstellungen → Fehlerbehebung → **Build-Cache leeren**, dann starten.
2. Hilft das nicht: **Vibe neu installieren** (falls Dateien von Hand verändert wurden).
3. Bleibt der Fehler, ist die aktuelle Vibe-Version auf GitHub fehlerhaft. Warte auf ein Update (der Launcher
   lädt es automatisch) und [melde den Fehler](#einen-fehler-melden).

### VL-503
**Gradle konnte nicht eingerichtet werden**

Gradle selbst (die Build-Software, etwa 130 MB von `services.gradle.org`) konnte nicht geladen werden oder der
Download ist beschädigt (`Could not install Gradle distribution`, `Exception in thread "main" java.net…`,
`zip END header not found`).

**Lösung:**
1. Prüfe die Verbindung zu `services.gradle.org` und versuche es erneut.
2. Ist der Download beschädigt: Schließe den Launcher und lösche im Gradle-Ordner den Ordner
   `wrapper/dists/gradle-8.8-bin` (Windows: `%USERPROFILE%\.gradle\wrapper\dists\gradle-8.8-bin`). Beim
   nächsten Start wird Gradle neu geladen.

### VL-504
**OptiFine konnte nicht heruntergeladen werden**

Vibe startet mit OptiFine 1.8.9 HD U M6 pre2, das beim ersten Build von optifine.net geladen wird. optifine.net
hat keinen Download-Link geliefert oder die Datei ist keine gültige OptiFine-JAR.

**Lösung:**
1. Versuche es später erneut; optifine.net ist manchmal überlastet oder ändert seine Download-Seite.
2. **Von Hand:** Lade `preview_OptiFine_1.8.9_HD_U_M6_pre2.jar` über
   https://optifine.net/adloadx?f=preview_OptiFine_1.8.9_HD_U_M6_pre2.jar herunter (nach der Werbung auf
   „Download“ klicken) und speichere die Datei unverändert unter
   `<Datenordner>/source/build/optifine/preview_OptiFine_1.8.9_HD_U_M6_pre2.jar`. Lege den Ordner `optifine`
   an, falls er fehlt. Die Datei bleibt bei Updates und beim Leeren des Build-Caches erhalten.
3. Werbe- oder DNS-Filter können optifine.net sperren: Ausnahme hinzufügen.

### VL-505
**Zu wenig Arbeitsspeicher für den Build**

Gradle braucht beim Bauen bis zu 3 GB Arbeitsspeicher. Es konnte nicht starten oder ist ausgegangen
(`Unable to start the daemon process`, `Could not reserve enough space for object heap`,
`OutOfMemoryError`).

**Lösung:**
1. Schließe Browser, Spiele und andere große Programme und starte erneut.
2. **Windows:** Die Auslagerungsdatei muss aktiv sein. Systemsteuerung → System → Erweiterte
   Systemeinstellungen → Leistung → Einstellungen → Erweitert → Virtueller Arbeitsspeicher → *Größe der
   Auslagerungsdatei für alle Laufwerke automatisch verwalten* aktivieren und neu starten.
3. Senke Einstellungen → Spiel → **Arbeitsspeicher**, damit Gradle und Minecraft zusammen hineinpassen.
4. Empfohlen sind mindestens 8 GB RAM.

### VL-506
**Gradle-Cache beschädigt**

Dateien in Gradles Cache sind kaputt, meist nach einem Absturz, vollem Laufwerk oder abgebrochenem Download
(`Could not read workspace metadata`, `invalid LOC header`, `error in opening zip file`).

**Lösung:**
1. Einstellungen → Fehlerbehebung → **Build-Cache leeren**, dann starten.
2. Hilft das nicht: Schließe den Launcher und lösche im Gradle-Ordner den Ordner `caches`
   (Windows: `%USERPROFILE%\.gradle\caches`). Beim nächsten Start wird alles neu geladen (etwa 1 GB).
   Nutzt du Gradle auch für andere Projekte, werden deren Abhängigkeiten ebenfalls neu geladen.

### VL-507
**Build-Dateien sind gesperrt**

Ein anderer Gradle-Prozess hält eine Sperre auf Gradles Cache (`Timeout waiting to lock`,
`It is currently in use by another Gradle instance`).

**Lösung:**
1. Beende andere Builds: IDEs wie IntelliJ IDEA, ein offenes `run.bat`/`gradlew`, ein zweiter Launcher.
2. Beende hängende Java-Prozesse im Task-Manager („OpenJDK Platform binary“).
3. Starte den PC neu, falls die Sperre bleibt.

### VL-508
**Minecraft-Dateien fehlen**

Unimined (das Gradle-Plugin für Minecraft) hat den Minecraft-1.8.9-Client nicht vorbereitet
(`Unimined did not prepare the Minecraft 1.8.9 client`), meist nach einem abgebrochenen ersten Build.

**Lösung:**
1. Schließe den Launcher und lösche im Gradle-Ordner `caches/unimined`
   (Windows: `%USERPROFILE%\.gradle\caches\unimined`).
2. Starte den Launcher mit Internetverbindung und klicke auf **Spielen**: Minecraft wird neu geladen.

## 6xx: Minecraft starten und spielen

Diese Codes erscheinen, wenn der Build geklappt hat, Minecraft aber nicht startet oder mit einem Fehler
beendet wird. Gibt es einen frischen Absturzbericht, öffnet **Absturzbericht öffnen** ihn direkt; er liegt
unter `<Datenordner>/source/run/client/crash-reports/`.

### VL-600
**Minecraft ist abgestürzt**

Minecraft wurde mit einem Fehler beendet. Die Ursache ließ sich keinem der genaueren Codes zuordnen.

**Lösung:**
1. Öffne den Absturzbericht (Knopf in der Meldung) oder die Konsole. Wichtig sind die Zeile
   `Description:` und die erste Zeile des Stacktraces darunter.
2. Hast du eigene Mods, deaktiviere sie auf der Mods-Seite und starte erneut. Läuft es dann, aktiviere sie
   einzeln, bis der Fehler wieder auftritt.
3. Einstellungen → Fehlerbehebung → **Build-Cache leeren**.
4. Bleibt es, [melde den Fehler](#einen-fehler-melden) mit dem Absturzbericht.

Hinweis: Auch wer Minecraft über den Task-Manager beendet, bekommt diesen Code.

### VL-601
**Minecraft konnte seinen Arbeitsspeicher nicht reservieren**

Java konnte den eingestellten Arbeitsspeicher nicht bekommen und Minecraft gar nicht erst starten
(`Could not reserve enough space for object heap`, `Error occurred during initialization of VM`,
`There is insufficient memory for the Java Runtime Environment`).

**Lösung:**
1. Senke Einstellungen → Spiel → **Arbeitsspeicher** (z. B. auf 2–3 GB).
2. Schließe andere Programme; auch Gradle belegt während des Spiels Speicher.
3. Windows: Aktiviere die Auslagerungsdatei (siehe [VL-505](#vl-505)).

### VL-602
**Minecraft hat keinen Arbeitsspeicher mehr**

Minecraft hat seinen Arbeitsspeicher aufgebraucht (`java.lang.OutOfMemoryError`).

**Lösung:**
1. Erhöhe Einstellungen → Spiel → **Arbeitsspeicher** auf 3–4 GB. Mehr als 6 GB hilft bei 1.8.9 selten.
2. Nutze kleinere Ressourcenpakete (512x und höher brauchen sehr viel Speicher), weniger Shader und Mods.
3. Senke die Sichtweite.

### VL-603
**Problem mit dem Grafiktreiber**

Minecraft konnte kein OpenGL-Fenster öffnen (`Pixel format not accelerated`, `No OpenGL context found`,
`LWJGLException`, `GLXBadFBConfig`).

**Lösung:**
1. **Installiere den aktuellen Grafiktreiber** direkt vom Hersteller (NVIDIA, AMD oder Intel), nicht nur über
   Windows Update.
2. **Laptops mit zwei Grafikkarten:** Windows: Einstellungen → System → Anzeige → Grafik → *Durchsuchen* →
   `<Datenordner>\runtime\temurin-8\<Ordner>\bin\java.exe` und `javaw.exe` hinzufügen → Optionen →
   *Hohe Leistung*. Bei NVIDIA alternativ in der NVIDIA-Systemsteuerung.
3. **Remotedesktop/Streaming:** Über eine RDP-Sitzung gibt es oft kein OpenGL; starte direkt am PC.
4. **Linux:** Installiere die Grafiktreiber (Mesa bzw. den proprietären Treiber) und `xrandr`
   (Debian/Ubuntu: `sudo apt install x11-xserver-utils`). Minecraft 1.8.9 braucht X11; unter Wayland läuft es
   über XWayland.

### VL-604
**Ein Mod verhindert den Start**

Forge hat einen eigenen Mod abgelehnt oder ein Mod ist beim Laden abgestürzt (`MissingModsException`,
`DuplicateModsFoundException`, `WrongMinecraftVersionException`, `UnsupportedClassVersionError`). Die Meldung
zeigt die Zeile, die den Mod nennt.

**Lösung:**
1. Öffne die Mods-Seite und deaktiviere alle eigenen Mods. Startet Vibe dann, aktiviere sie einzeln, um den
   Verursacher zu finden.
2. `Missing Mods`/`requires`: Dem Mod fehlt ein anderer Mod, den er braucht; installiere ihn mit.
3. `Duplicate Mods`: Ein Mod liegt doppelt im Mods-Ordner; entferne eine Kopie.
4. `UnsupportedClassVersionError`: Der Mod ist für neuere Minecraft-/Java-Versionen gebaut und läuft nicht
   mit 1.8.9; suche eine 1.8.9-Version.

### VL-605
**Spielbibliotheken konnten nicht geladen werden**

Die nativen LWJGL-Bibliotheken (Grafik, Ton, Eingabe) ließen sich nicht laden (`UnsatisfiedLinkError`,
`no lwjgl64 in java.library.path`).

**Lösung:**
1. Einstellungen → Fehlerbehebung → **Build-Cache leeren**: Die Bibliotheken werden neu entpackt.
2. Prüfe die Quarantäne deines Virenscanners (z. B. `lwjgl64.dll`, `OpenAL64.dll`) und füge den
   Datenordner und den Gradle-Ordner als Ausnahme hinzu.
3. Linux auf ARM-Prozessoren wird von Minecraft 1.8.9 (LWJGL 2) nicht unterstützt.

### VL-606
**Java ist abgestürzt**

Die Java-Laufzeit selbst ist abgestürzt (`A fatal error has been detected by the Java Runtime Environment`,
`EXCEPTION_ACCESS_VIOLATION`). Es gibt dann keinen Minecraft-Absturzbericht, sondern eine Datei
`hs_err_pid<Zahl>.log` im Spielordner (`<Datenordner>/source/run/client/`).

**Ursachen:** Fast immer Grafiktreiber oder Programme, die sich in das Spiel einklinken.

**Lösung:**
1. Aktualisiere den Grafiktreiber ([VL-603](#vl-603)).
2. Schalte Overlays aus: Discord-Overlay, MSI Afterburner/RivaTuner, Overwolf, GeForce-Experience-Overlay,
   Aufnahme-Tools.
3. Deaktiviere eigene Mods und Shader zum Testen.
4. Hilft nichts, [melde den Fehler](#einen-fehler-melden) mit der `hs_err_pid…log`-Datei.

### VL-607
**Rosetta 2 fehlt**

Auf Macs mit Apple-Chip (M1, M2, …) läuft Minecraft 1.8.9 mit einem Intel-Java, das Rosetta 2 braucht
(`Bad CPU type in executable`).

**Lösung:** Öffne das Terminal und führe aus:

```
softwareupdate --install-rosetta --agree-to-license
```

Starte danach den Launcher neu und klicke auf **Spielen**.

## 7xx: Der Launcher selbst

### VL-701
**Launcher ist bereits geöffnet**

Es läuft schon ein Vibe Launcher mit demselben Datenordner. Zwei Launcher würden sich beim Aktualisieren und
Bauen gegenseitig stören.

**Lösung:**
1. Suche das offene Fenster (Taskleiste, Alt+Tab; es kann minimiert sein).
2. Ist keines sichtbar, hängt ein alter Launcher im Hintergrund: Windows-Task-Manager → „OpenJDK Platform
   binary“ bzw. „Java(TM) Platform SE binary“ mit dem Vibe Launcher beenden. macOS/Linux:
   `pkill -f VibeLauncher.jar`.
3. Nach einem Launcher-Update startet der Launcher kurz von selbst neu; warte ein paar Sekunden.

### VL-702
**Kein Desktop verfügbar**

Der Launcher wurde ohne grafische Oberfläche gestartet, z. B. über SSH, in einem Docker-Container oder unter
Linux ohne `DISPLAY`. Die Meldung erscheint in der Kommandozeile.

**Lösung:** Starte den Launcher in einer normalen Desktop-Sitzung. Unter WSL brauchst du WSLg (Windows 11).

### VL-703
**Launcher-Update fehlgeschlagen**

Das Update des Launchers konnte nicht geladen oder installiert werden.

**Lösung:**
1. Versuche es später erneut (Einstellungen → Updates → *Update installieren*).
2. Liegt `VibeLauncher.jar` in einem Ordner ohne Schreibrechte (z. B. `C:\Programme`), verschiebe die Datei
   auf den Desktop oder in deinen Benutzerordner.
3. **Von Hand:** Lade die neueste `VibeLauncher.jar` aus den
   [GitHub-Releases](https://github.com/SkidderClub/Vibe/releases), schließe den Launcher und ersetze die alte
   Datei.

### VL-704
**Launcher-Update beschädigt**

Das heruntergeladene Update passte nicht zu seiner Prüfsumme und wurde zu deiner Sicherheit verworfen.

**Lösung:** Versuche es später erneut. Passiert es immer wieder, lade die Datei von Hand
([VL-703](#vl-703)) und prüfe Virenscanner oder Proxy.

### VL-705
**Launcher läuft nicht aus VibeLauncher.jar**

Automatische Updates und Neustarts (z. B. nach einem Sprachwechsel) funktionieren nur, wenn der Launcher aus
der Datei `VibeLauncher.jar` läuft, nicht aus einer IDE oder aus entpackten Klassen.

**Lösung:** Starte den Launcher mit `java -jar VibeLauncher.jar` oder per Doppelklick auf die JAR. Nach einem
Sprachwechsel kannst du ihn auch einfach von Hand neu starten.

### VL-706
**Account-Tresor nicht lesbar**

Die Account-Liste von Vibe (`accounts.vault`) ist beschädigt oder passt nicht zum Schlüssel (`accounts.key`),
z. B. weil eine der beiden Dateien aus einem anderen Profil stammt. Beide liegen in
`<Datenordner>/source/run/client/vibe/accounts/`. Die Accounts-Seite zeigt den Fehler an; Vibe selbst meldet
im Alt Manager „Cannot read the account vault“ und speichert keine Accounts, bis das behoben ist.

**Lösung:**
1. Hast du eine Sicherung des Ordners `accounts`, stelle `accounts.vault` und `accounts.key` **zusammen** daraus
   wieder her. Die beiden Dateien gehören immer zusammen; einzeln kopiert passen sie nicht.
2. Ist keine Sicherung da: Schließe Minecraft und benenne `accounts.vault` in `accounts.vault.kaputt` um. Beim
   nächsten Start beginnt Vibe mit einem leeren Tresor. Klicke dann auf **Account hinzufügen** und melde dich
   im Alt Manager neu an.
3. Dein Minecraft- bzw. Microsoft-Account selbst ist davon nicht betroffen, nur die lokal gespeicherte Anmeldung.

### VL-707
**Account-Schlüssel fehlt**

`accounts.vault` ist da, aber `accounts.key` fehlt. Ohne Schlüssel lässt sich der Tresor nicht entschlüsseln,
und Vibe legt auch keinen neuen an, solange der alte Tresor existiert.

**Lösung:**
1. Stelle `accounts.key` aus einer Sicherung oder dem Papierkorb wieder her (gleicher Ordner wie
   `accounts.vault`).
2. Geht das nicht, sind die gespeicherten Anmeldungen verloren: Benenne `accounts.vault` um (siehe
   [VL-706](#vl-706), Schritt 2) und melde dich im Alt Manager neu an.

### VL-708
**Konnte nicht geöffnet werden**

Ein Ordner, eine Datei oder ein Link ließ sich nicht öffnen, weil kein passendes Programm (Dateimanager,
Texteditor, Browser) gefunden wurde. Die Meldung nennt den Pfad bzw. die Adresse, die Konsole ebenfalls.

**Lösung:** Öffne den Pfad oder Link von Hand. Unter Linux hilft oft die Installation von `xdg-utils`.

### VL-709
**Launcher konnte nicht starten**

Beim Aufbau des Fensters ist ein unerwarteter Fehler aufgetreten.

**Lösung:**
1. Starte den Launcher erneut.
2. Aktualisiere das Java, mit dem du den Launcher öffnest.
3. Bleibt es, [melde den Fehler](#einen-fehler-melden) mit `logs/launcher.log` aus dem Datenordner.

## 8xx: Eigene Mods

Diese Codes stehen hinter abgelehnten Dateien, wenn du Mods auf den Launcher ziehst oder über **Mods
hinzufügen** auswählst, z. B. `Sodium.jar: Fabric-Mods funktionieren nicht mit Forge 1.8.9 (VL-806)`.
Vibe lädt Mods für **Forge 1.8.9**.

### VL-801
**Keine Mod-Datei**

Nur `.jar`- oder `.zip`-Dateien können Mods sein. Entpacke heruntergeladene Archive (`.rar`, `.7z`) und
füge die enthaltene `.jar` hinzu.

### VL-802
**Mod-Datei zu groß**

Dateien über 300 MB werden abgelehnt. Prüfe, ob es wirklich ein Mod ist (und kein Modpack oder Spiel).

### VL-803
**Beschädigte Mod-Datei**

Die Datei ist kein gültiges Archiv, oft ein abgebrochener Download oder eine HTML-Seite mit `.jar`-Endung.
Lade den Mod erneut von der offiziellen Seite herunter.

### VL-804
**OptiFine ist bereits enthalten**

Vibe startet immer mit OptiFine 1.8.9 HD U M6 pre2. Eine zweite OptiFine-Version würde den Start verhindern;
du musst nichts tun.

### VL-805
**Vibe wird automatisch geladen**

Du hast eine Vibe-JAR hinzugefügt. Der Launcher baut und lädt Vibe selbst; eine zweite Kopie würde Forge
abbrechen lassen.

### VL-806
**Fabric-Mod**

Fabric-Mods funktionieren nicht mit Forge. Suche auf der Seite des Mods nach einer Version für
**Forge 1.8.9**.

### VL-807
**Mod für ein neueres Minecraft**

Der Mod ist für Forge 1.13 oder neuer (enthält `META-INF/mods.toml`). Suche nach einer Version für 1.8.9.

### VL-808
**Mod bereits installiert**

Eine Datei mit demselben Namen liegt schon im Mods-Ordner (aktiv oder deaktiviert). Willst du eine neuere
Version installieren, entferne zuerst die alte auf der Mods-Seite.

### VL-809
**Dateiname bereits vergeben**

Beim Aktivieren oder Deaktivieren eines Mods gibt es die Zieldatei schon, z. B. liegen `Mod.jar` und
`Mod.jar.disabled` nebeneinander. Öffne den Mods-Ordner (Mods-Seite → *Ordner öffnen*) und lösche eine der
beiden Dateien.

### VL-810
**Mod für eine andere Minecraft-Version**

Kein Fehler, sondern eine Warnung auf der Mods-Seite: Laut `mcmod.info` ist der Mod für eine andere
Minecraft-Version gemacht. Er lädt eventuell nicht ([VL-604](#vl-604)). Suche nach einer 1.8.9-Version.

## 900: Unerwartete Fehler

### VL-900
**Unerwarteter Fehler**

Ein Fehler, für den es keinen eigenen Code gibt. Die Meldung enthält die technische Beschreibung.

**Lösung:**
1. Versuche es erneut und starte notfalls den Launcher neu.
2. Bleibt es, [melde den Fehler](#einen-fehler-melden) mit dem Launcher-Log. Damit bekommt das Problem
   künftig einen eigenen Code.

## Probleme ohne Fehlercode

**Die `VibeLauncher.jar` öffnet sich nicht per Doppelklick.**
Zum Öffnen der JAR brauchst du ein installiertes Java 8 oder neuer.
1. Installiere Java, z. B. Eclipse Temurin 21 von https://adoptium.net (beim Setup „Set JAVA_HOME“ und
   „Associate .jar“ aktivieren).
2. Öffnet sich stattdessen WinRAR oder 7-Zip, ist `.jar` falsch verknüpft: Rechtsklick → Öffnen mit →
   *Java(TM) Platform SE binary* bzw. *OpenJDK Platform binary* → Immer diese App verwenden.
3. Oder starte per Kommandozeile im Ordner der Datei: `java -jar VibeLauncher.jar`. Fehlermeldungen
   erscheinen dann dort.
4. Meldet Java `UnsupportedClassVersionError … class file version 52.0`, ist dein Java älter als Java 8:
   aktualisiere es.

**Der erste Start dauert sehr lange.**
Das ist normal: Beim ersten Mal werden Vibe, zwei Java-Versionen, Gradle, Minecraft, Forge und OptiFine
geladen und Vibe wird gebaut (5–15 Minuten, je nach Leitung). Die Spielleiste zeigt den Fortschritt, die
Konsole jede Zeile. Spätere Starts gehen deutlich schneller, weil nur noch Geändertes gebaut wird.

**Das Spiel startet, aber mit dem falschen Account oder nicht angemeldet.**
Wähle den Account auf der Accounts-Seite. *Automatisch* nutzt das Auto Login von Vibe. Abgelaufene
Microsoft-Anmeldungen erneuerst du im Alt Manager (Account hinzufügen).

**In der Vorschau fehlt der Skin.**
Skins kommen von den Mojang-Servern. Offline-Accounts und Accounts ohne eigenen Skin zeigen Steve bzw. Alex.
Ohne Internet bleibt der zuletzt geladene Skin sichtbar. Das betrifft nur die Vorschau, nicht das Spiel.

**Nach dem Schließen des Launchers läuft Minecraft weiter.**
Absicht: Das Spiel ist unabhängig vom Launcher. Öffnest du den Launcher wieder, verbindet er sich mit dem
laufenden Spiel (ab Java 9 für den Launcher).

## Einen Fehler melden

Hilft keine Lösung, melde den Fehler im [Discord](https://dsc.gg/vibe-skidder-club) oder als
[GitHub-Issue](https://github.com/SkidderClub/Vibe/issues). Gib Folgendes mit:

1. Den **Fehlercode** und die vollständige Meldung (ein Screenshot genügt).
2. Das **Launcher-Log**: `logs/launcher.log` im Datenordner (Konsole → *Launcher-Log*).
3. Das **Spiel-Log** des fehlgeschlagenen Starts: `logs/game/<Datum>.log` (Konsole → *Spiel-Log*).
4. Bei Abstürzen den **Absturzbericht** aus `source/run/client/crash-reports/` bzw. die
   `hs_err_pid…log`-Datei.
5. Dein Betriebssystem und, falls bekannt, Grafikkarte und Arbeitsspeicher.

Der Launcher selbst schreibt keine Passwörter oder Anmelde-Tokens in seine Logs: Aus dem Account-Tresor liest
er nur Namen und UUIDs. Schau trotzdem kurz über die Dateien, bevor du sie öffentlich teilst.
