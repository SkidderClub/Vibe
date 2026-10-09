# Vibe Launcher: error codes and fixes

Every error in the Vibe Launcher has a fixed code in the format **`VL-xxx`**. For each code, this page
explains what happened, what usually causes it and how to fix it step by step.

You find the code in four places:

- **Toast at the bottom right:** The title starts with the code, e.g. `VL-101 · No internet connection`.
  Below it are the cause and a short hint on how to fix it. **How to fix** opens the matching section of
  this page.
- **Play bar:** After a failed start, it shows the code with a hint. Hover over it to see the exact
  message. A click opens this page.
- **Console:** Errors are marked in red. Every error message starts with the code, followed by the fix
  and a link.
- **Launcher log:** `logs/launcher.log` in the data folder, with the full stack trace (Settings →
  Troubleshooting → Launcher log).

The hundreds digit tells you where the problem is:

| Codes | Area |
| --- | --- |
| [1xx](#1xx-internet-and-servers) | Internet and servers |
| [2xx](#2xx-files-and-folders) | Files, folders and disk space |
| [3xx](#3xx-java) | The launcher's Java runtimes |
| [4xx](#4xx-vibe-download-and-updates) | Downloading and updating Vibe |
| [5xx](#5xx-building-vibe-gradle) | Building Vibe with Gradle |
| [6xx](#6xx-starting-and-playing-minecraft) | Starting Minecraft and crashes |
| [7xx](#7xx-the-launcher-itself) | The launcher itself |
| [8xx](#8xx-custom-mods) | Custom mods |
| [900](#900-unexpected-errors) | Unexpected errors |

## Contents

- [First aid for any error](#first-aid-for-any-error)
- [Important folders](#important-folders)
- [All codes at a glance](#all-codes-at-a-glance)
- [The codes in detail](#1xx-internet-and-servers)
- [Problems without an error code](#problems-without-an-error-code)
- [Reporting an error](#reporting-an-error)

## First aid for any error

1. **Read the whole message.** The second line names the exact cause, e.g. the server that does not
   answer or the file that is missing.
2. **Try again.** Many network errors are gone after a few minutes. Downloads that break off are retried
   twice automatically.
3. **Look at the console** (left sidebar → Console). Red lines show what went wrong.
4. **Use the repair buttons** in Settings:
   - *Troubleshooting → Clear build cache*: Vibe is rebuilt from scratch on the next start.
   - *Troubleshooting → Reinstall Vibe*: The source code is downloaded again. Worlds, settings, accounts
     and mods are kept.
   - *Java → Reinstall*: Java 8 and Java 21 are downloaded again (about 250 MB).
5. **Restart your PC** if files are locked or Java processes hang.

## Important folders

The launcher's **data folder** is here:

| System | Data folder |
| --- | --- |
| Windows | `%APPDATA%\VibeLauncher` (e.g. `C:\Users\Name\AppData\Roaming\VibeLauncher`) |
| macOS | `~/Library/Application Support/VibeLauncher` |
| Linux | `~/.local/share/VibeLauncher` (or `$XDG_DATA_HOME/VibeLauncher`) |

Open it with Settings → Folders → *Launcher data*. It contains:

| Path | Contents |
| --- | --- |
| `source/` | The Vibe source code that Vibe is built from |
| `source/run/client/` | Your game profile: worlds, options, accounts, mods, crash reports |
| `source/run/client/crash-reports/` | Minecraft's crash reports |
| `source/run/client/mods/` | Your custom mods |
| `source/run/client/vibe/accounts/` | Account vault (`accounts.vault`) and key (`accounts.key`) |
| `source/build/` | Build output; `source/build/optifine/` holds the OptiFine JAR |
| `runtime/temurin-8`, `runtime/temurin-21` | Java 8 (starts Minecraft) and Java 21 (builds Vibe) |
| `logs/launcher.log` | Launcher log |
| `logs/game/` | One log per game start (build and Minecraft output) |
| `launcher.properties` | The launcher's settings |

Gradle also uses its own **Gradle folder** in your user directory: `%USERPROFILE%\.gradle` on Windows,
`~/.gradle` on macOS and Linux (or the folder from the `GRADLE_USER_HOME` environment variable). It holds
Gradle itself (`wrapper/dists/`), Minecraft, Forge and all libraries (`caches/`).

> **Tip:** You can move the data folder by starting the launcher like this:
> `java -Dvibe.launcher.home="D:\VibeLauncher" -jar VibeLauncher.jar`. Move the Gradle folder with the
> `GRADLE_USER_HOME` environment variable (e.g. `D:\gradle`).

## All codes at a glance

| Code | Problem | Quick fix |
| --- | --- | --- |
| [VL-101](#vl-101) | No internet connection | Check Wi-Fi/cable |
| [VL-102](#vl-102) | Connection blocked | Check firewall, proxy, VPN |
| [VL-103](#vl-103) | Connection timed out | Try again, better connection |
| [VL-104](#vl-104) | Secure connection failed | Check the clock, turn off HTTPS scanning |
| [VL-105](#vl-105) | GitHub request limit reached | Wait or use another network |
| [VL-106](#vl-106) | Server error | Try again later |
| [VL-107](#vl-107) | Download interrupted | Stable connection, try again |
| [VL-108](#vl-108) | Unexpected answer from the server | Wi-Fi login page, proxy, filter |
| [VL-109](#vl-109) | File not found on the server | Update the launcher |
| [VL-201](#vl-201) | No permission to write files | Antivirus/OneDrive/permissions |
| [VL-202](#vl-202) | Files are in use | Close programs, restart |
| [VL-203](#vl-203) | Not enough disk space | Free up space |
| [VL-204](#vl-204) | File system error | Check the drive |
| [VL-205](#vl-205) | No Minecraft folder found | Start Minecraft once |
| [VL-206](#vl-206) | Launcher data folder unusable | Check permissions, choose another folder |
| [VL-301](#vl-301) | No Java for this system | 64-bit system required |
| [VL-302](#vl-302) | Java download damaged | Try again |
| [VL-303](#vl-303) | Java installation unusable | Reinstall Java |
| [VL-304](#vl-304) | Java could not be started | Antivirus, reinstall Java |
| [VL-305](#vl-305) | Wrong Java version | Reinstall Java |
| [VL-401](#vl-401) | Vibe could not be downloaded | Check the connection to GitHub |
| [VL-402](#vl-402) | Last update did not finish | Go online and press Play |
| [VL-403](#vl-403) | Vibe download is invalid | Reinstall Vibe |
| [VL-404](#vl-404) | Game profile could not be moved | Close programs, try again |
| [VL-405](#vl-405) | Vibe files are missing | Reinstall Vibe |
| [VL-500](#vl-500) | Build failed | Read the console, clear the build cache |
| [VL-501](#vl-501) | Download during the build failed | Check the connection, try again later |
| [VL-502](#vl-502) | Vibe does not compile | Clear cache, reinstall, report |
| [VL-503](#vl-503) | Gradle could not be set up | Delete the Gradle download |
| [VL-504](#vl-504) | OptiFine could not be downloaded | Try again later or by hand |
| [VL-505](#vl-505) | Not enough memory for the build | Close programs, paging file |
| [VL-506](#vl-506) | Gradle cache damaged | Delete the Gradle cache |
| [VL-507](#vl-507) | Build files are locked | End other builds, restart |
| [VL-508](#vl-508) | Minecraft files are missing | Delete the Unimined cache |
| [VL-600](#vl-600) | Minecraft crashed | Read the crash report, test mods |
| [VL-601](#vl-601) | Minecraft could not reserve its memory | Lower the memory |
| [VL-602](#vl-602) | Minecraft ran out of memory | Raise the memory |
| [VL-603](#vl-603) | Graphics driver problem | Update the driver |
| [VL-604](#vl-604) | A mod prevents the start | Disable custom mods |
| [VL-605](#vl-605) | Game libraries could not be loaded | Clear the build cache |
| [VL-606](#vl-606) | Java crashed | Driver, turn off overlays |
| [VL-607](#vl-607) | Rosetta 2 is missing | Install Rosetta (Mac) |
| [VL-701](#vl-701) | Launcher is already open | Find the window or end the process |
| [VL-702](#vl-702) | No desktop available | Start in a desktop session |
| [VL-703](#vl-703) | Launcher update failed | Update later or by hand |
| [VL-704](#vl-704) | Launcher update damaged | Try again later |
| [VL-705](#vl-705) | Launcher does not run from VibeLauncher.jar | Start with `java -jar` |
| [VL-706](#vl-706) | Account vault unreadable | Restore a backup or start a new one |
| [VL-707](#vl-707) | Account key missing | Backup or sign in again |
| [VL-708](#vl-708) | Could not open | Open by hand |
| [VL-709](#vl-709) | Launcher could not start | Restart, report the log |
| [VL-801](#vl-801) | Not a mod file | Only `.jar`/`.zip` |
| [VL-802](#vl-802) | Mod file too large | Check the file |
| [VL-803](#vl-803) | Damaged mod file | Download the mod again |
| [VL-804](#vl-804) | OptiFine is already included | Nothing to do |
| [VL-805](#vl-805) | Vibe is loaded automatically | Nothing to do |
| [VL-806](#vl-806) | Fabric mod | Look for a Forge 1.8.9 version |
| [VL-807](#vl-807) | Mod for a newer Minecraft | Look for a 1.8.9 version |
| [VL-808](#vl-808) | Mod already installed | Nothing to do |
| [VL-809](#vl-809) | File name already taken | Remove the duplicate copy |
| [VL-810](#vl-810) | Mod for another Minecraft version | Look for a 1.8.9 version |
| [VL-900](#vl-900) | Unexpected error | Try again, report |

## 1xx: Internet and servers

The launcher needs the internet for the first start, for updates and for the first build. It talks to
these servers; a firewall or filter must allow them:

| Server | Used for |
| --- | --- |
| `api.github.com`, `github.com`, `codeload.github.com`, `raw.githubusercontent.com`, `objects.githubusercontent.com`, `release-assets.githubusercontent.com` | Vibe source code, updates, launcher updates, Java downloads |
| `api.adoptium.net` | Looking up Java 8 and Java 21 |
| `services.gradle.org` | Gradle itself (on the first build) |
| `repo.maven.apache.org`, `maven.minecraftforge.net`, `maven.fabricmc.net`, `maven.wagyourtail.xyz`, `jitpack.io` | Libraries for the build |
| `piston-meta.mojang.com`, `launchermeta.mojang.com`, `libraries.minecraft.net`, `resources.download.minecraft.net` | Minecraft 1.8.9, libraries and assets |
| `optifine.net` | OptiFine |
| `api.mojang.com`, `sessionserver.mojang.com`, `textures.minecraft.net` | Skins and capes in the preview |

Once Vibe is installed and built, it also starts **without internet**, as long as no update is
half-finished ([VL-402](#vl-402)).

### VL-101
**No internet connection**

The launcher cannot find the server. Typical message: `api.github.com could not be found.` or, in the
console, `UnknownHostException`, `Name or service not known`, `No such host is known`.

**Causes:** No Wi-Fi or cable, the network has no internet access, a DNS problem, or an ad blocker or
parental-control filter that blocks the address.

**Fix:**
1. Open any web page in your browser. If that does not work, the problem is the network.
2. Reconnect to the Wi-Fi or plug the cable in again; restart the router if necessary.
3. If you use a DNS filter (Pi-hole, AdGuard, parental controls), allow the servers from the table above.
4. Windows: `ipconfig /flushdns` in the Command Prompt clears the DNS cache.
5. Press **Play** again.

### VL-102
**Connection blocked**

The server can be reached, but the connection is refused, or a proxy asks for a login (HTTP 401, 403,
407).

**Causes:** Firewall, antivirus with web protection, company or school network, VPN, proxy with login.

**Fix:**
1. Allow Java through the firewall. This affects the Java you start the launcher with
   (`javaw.exe`/`java.exe`) and the runtimes in the data folder under `runtime\`.
2. Turn off your VPN to test.
3. The launcher uses the system's proxy settings. Proxies with a user name and password are not
   supported: use another network instead, e.g. a phone hotspot.
4. GitHub or Maven are often blocked in school or company networks. Play on another network then.

### VL-103
**Connection timed out**

The server did not answer in time (message `The server did not answer in time.`, `Read timed out`,
`connect timed out`).

**Fix:**
1. Try again. The launcher already retries interrupted downloads twice on its own.
2. Pause other large downloads, streams or updates.
3. Use a network cable or move closer to the router.
4. Turn off VPN or proxy to test.

### VL-104
**Secure connection failed**

The encrypted HTTPS connection could not be established (`SSLHandshakeException`,
`PKIX path building failed`, `unable to find valid certification path`).

**Causes:**
- The PC's date or time is wrong, which makes all certificates look invalid.
- An antivirus scans HTTPS connections and swaps certificates while doing so (e.g. "HTTPS scanning",
  "Web Shield", "scan encrypted connections" in Avast, AVG, Kaspersky, ESET, Bitdefender).
- A company or school proxy breaks up HTTPS.
- The launcher runs on a very old Java 8 that lacks newer root certificates.

**Fix:**
1. Set date, time and time zone automatically (Windows: Settings → Time & language).
2. Turn off your antivirus's HTTPS scanning or add an exception for Java.
3. Update the Java you open the launcher with, e.g. to a current Eclipse Temurin 8, 17 or 21
   (https://adoptium.net).
4. Try another network.

### VL-105
**GitHub request limit reached**

Without signing in, GitHub allows about 60 requests per hour per network (IP address). The message names
the time from which it works again, e.g. `GitHub's request limit is reached (until 14:05).`

**Causes:** Many starts or update checks in a row, or many people on the same network (school, shared
flat, VPN) using GitHub.

**Fix:**
1. Wait until the time given (at most one hour).
2. If Vibe is already installed, turn off Settings → Updates → *Update Vibe automatically*: Vibe then
   starts without asking GitHub.
3. Alternatively use another network (e.g. a phone hotspot) or turn off the VPN.

### VL-106
**Server error**

The server answers with an error (HTTP 5xx or another unexpected status). The message names the server,
e.g. `api.github.com answered HTTP 502.`

**Fix:**
1. Wait a few minutes and try again.
2. Check whether the service has an outage: https://www.githubstatus.com for GitHub.

### VL-107
**Download interrupted**

The connection dropped in the middle of a download (`Connection reset`, `ended early`, `Premature EOF`).
By then, the launcher has already tried large downloads three times.

**Fix:**
1. Check the connection and try again.
2. Turn off VPN or proxy to test.
3. Some antivirus programs break off large downloads: add an exception for Java.

### VL-108
**Unexpected answer from the server**

Something other than the expected data came back, usually a web page. Typical for:

- **Wi-Fi with a login page** (hotel, train, café, university guest network): you are not signed in yet.
- Proxies, parental-control or ad filters that show a block page.
- A redirect from HTTPS to HTTP, which the launcher refuses for security reasons.

**Fix:**
1. Open a web page in your browser and sign in to the Wi-Fi if a login page appears.
2. Check filters and proxy or use another network.
3. Try again.

### VL-109
**File not found on the server**

The server reports HTTP 404: the file the launcher wants to download no longer exists. This happens when
an old launcher uses an address that has changed.

**Fix:**
1. Update the launcher (Settings → Updates) or download the latest `VibeLauncher.jar` from the
   [GitHub releases](https://github.com/SkidderClub/Vibe/releases).
2. If that does not help, [report the error](#reporting-an-error).

## 2xx: Files and folders

### VL-201
**No permission to write files**

The launcher is not allowed to write or delete a file (`Access is denied`, `Permission denied`,
`AccessDeniedException`).

**Causes and fix:**
1. **Antivirus:** Add the data folder and the Gradle folder as exceptions.
2. **Windows "Controlled folder access"** (Windows Security → Virus & threat protection → Ransomware
   protection): Allow `java.exe` and `javaw.exe` or turn the feature off to test.
3. **OneDrive/Dropbox:** If the launcher or its data folder is in a synced folder, move it out (see the tip
   under [Important folders](#important-folders)).
4. **Linux/macOS:** If the launcher was ever started with `sudo`, files belong to `root`. Fix this with
   `sudo chown -R "$USER" ~/.local/share/VibeLauncher ~/.gradle` (macOS:
   `sudo chown -R "$USER" ~/Library/Application\ Support/VibeLauncher ~/.gradle`). Never start the
   launcher with `sudo`.
5. Do **not** start the launcher as administrator if it ran normally before; otherwise it creates files
   you will not be allowed to change later.

### VL-202
**Files are in use**

A file or folder is currently open in another program (`being used by another process`,
`The Vibe folder is in use`).

**Fix:**
1. Close Minecraft and other launchers.
2. Close Explorer windows, editors or IDEs that have files in the data folder open.
3. End hanging Java processes: Windows Task Manager → "OpenJDK Platform binary" or
   "Java(TM) Platform SE binary" → End task. macOS/Linux: Activity Monitor or `pkill -f GradleWrapperMain`.
4. Antivirus programs and Windows Search sometimes hold on to files briefly: wait a moment and try
   again.
5. If none of this helps, restart your PC.

### VL-203
**Not enough disk space**

Before downloads and builds, the launcher checks whether enough space is free and otherwise stops with a
message such as `Only 800 MB free for C:\Users\…\VibeLauncher, about 1.6 GB are needed.` The code also
appears when the drive fills up during a download or build.

**Space needed (approximately):**

| What | Where | Size |
| --- | --- | --- |
| Vibe source code | Data folder | 250 MB |
| Java 8 and Java 21 | Data folder | 450 MB |
| Build output | Data folder | 500 MB |
| Gradle, Minecraft, Forge, libraries, assets | Gradle folder | 1–1.5 GB |

So the first start needs about **3 GB**, split between the data folder and the Gradle folder (usually
both on `C:`).

**Fix:**
1. Free up disk space (Windows: Settings → System → Storage → Temporary files).
2. Or move the data folder and the Gradle folder to another drive (see [Important folders](#important-folders)).
3. Try again.

### VL-204
**File system error**

A file could not be read or written for some other reason.

**Fix:**
1. Try again.
2. If it happens again, check the drive: Windows: right-click the drive → Properties → Tools → Check.
   macOS: Disk Utility → First Aid.
3. If the data folder is on a USB stick or network drive, move it to an internal drive.

### VL-205
**No Minecraft folder found**

Appears for Settings → Folders → *Import from Minecraft*: the normal Minecraft folder
(`%APPDATA%\.minecraft`, `~/Library/Application Support/minecraft` or `~/.minecraft`) does not exist.
This is not a problem for Vibe; nothing is imported, that's all.

**Fix:**
1. Start the official Minecraft launcher once; then the folder exists.
2. Or copy `options.txt` and resource packs into the game folder by hand (Settings → Folders →
   *Game folder* or *Resource packs*).

### VL-206
**Launcher data folder unusable**

The launcher cannot write to its data folder when it starts and quits with a message window.

**Fix:**
1. Check whether the drive is full ([VL-203](#vl-203)).
2. Check the folder's permissions ([VL-201](#vl-201)), on Linux/macOS especially after a start with `sudo`.
3. Create the data folder somewhere else:
   `java -Dvibe.launcher.home="D:\VibeLauncher" -jar VibeLauncher.jar`
   (on macOS/Linux e.g. `java -Dvibe.launcher.home="$HOME/VibeLauncher" -jar VibeLauncher.jar`).

## 3xx: Java

The launcher brings **its own Java versions**: Java 8 starts Minecraft 1.8.9, Java 21 builds Vibe. They
come from Eclipse Temurin (Adoptium), are verified by checksum and live in the data folder under
`runtime/`. A Java installed on your PC or `JAVA_HOME` **does not matter**; it is only needed to open
`VibeLauncher.jar` (see [Problems without an error code](#problems-without-an-error-code)).

### VL-301
**No Java for this system**

Adoptium offers no suitable Java for your operating system or processor.

**Fix:** Vibe needs a **64-bit** Windows, macOS (Intel, or Apple Silicon with Rosetta 2, see
[VL-607](#vl-607)) or 64-bit Linux on an x64 processor. The launcher does not run on 32-bit Windows, very
old systems or exotic processors; Linux on ARM does get Java, but Minecraft 1.8.9 itself does not run there
([VL-605](#vl-605)). On Windows, check: Settings → System → About → System type.

### VL-302
**Java download damaged**

The downloaded Java does not match its checksum, or the archive cannot be unpacked. The file is
discarded.

**Fix:**
1. Try again; the file is downloaded again.
2. If it happens again, an antivirus or proxy is probably altering downloads: add an exception for Java
   or use another network.
3. Check the free disk space ([VL-203](#vl-203)).

### VL-303
**Java installation unusable**

The Java download contains no usable installation, or the installed Java is incomplete (e.g. an
antivirus deleted files).

**Fix:**
1. Settings → Java → **Reinstall**.
2. If that does not help, close the launcher, delete the `runtime` folder in the data folder and start
   again.
3. Add the `runtime` folder as an exception in your antivirus.

### VL-304
**Java could not be started**

The launcher's Java could not be run (`Cannot run program`, `A problem occurred starting process`,
`CreateProcess error=…`).

**Causes:** An antivirus blocks `java.exe` or has moved it to quarantine, the file is not executable
(Linux/macOS), or the system is 32-bit (`error=193`, "is not a valid Win32 application").

**Fix:**
1. Look in your antivirus's quarantine and restore Java; add the `runtime` folder as an exception.
2. Settings → Java → **Reinstall**.
3. Linux/macOS: If the data folder is on a drive mounted with `noexec`, move it ([VL-206](#vl-206)).
4. 32-bit Windows is not supported ([VL-301](#vl-301)).

### VL-305
**Wrong Java version**

Gradle did not find the Java version it needs for the build (`No matching toolchains found`,
`Unsupported class file major version`).

**Fix:**
1. Settings → Java → **Reinstall**. Afterwards Java 8 and Java 21 are set up cleanly again.
2. If you changed `java8Home` or `jdk21Home` in `launcher.properties` by hand, delete these lines. The
   launcher now detects a Java with the wrong version itself and then uses its own.
3. Then Settings → Troubleshooting → **Clear build cache** and start again.

## 4xx: Vibe download and updates

### VL-401
**Vibe could not be downloaded**

The Vibe source code could not be downloaded on the first start. Usually the more specific code of the
cause is shown instead, e.g. [VL-101](#vl-101) or [VL-105](#vl-105).

**Fix:**
1. Check your internet connection and whether https://github.com/SkidderClub/Vibe loads in the browser.
2. Follow the section for the more specific code if the console names one.
3. Try again.

### VL-402
**Last update did not finish**

A Vibe update was interrupted (launcher closed, crash, power cut). So that Vibe is not built from a mix of
two versions, the launcher only starts after the repair. That is not possible without internet; you then
often see the code of the network cause (e.g. [VL-101](#vl-101)).

**Fix:**
1. Connect to the internet and press **Play**: the rest of the update is downloaded.
2. Alternatively Settings → Troubleshooting → **Reinstall Vibe**.

Your game profile (worlds, settings, accounts, mods) is kept in both cases.

### VL-403
**Vibe download is invalid**

The downloaded Vibe archive is damaged or does not contain a Gradle project.

**Fix:**
1. Try again.
2. Settings → Troubleshooting → **Reinstall Vibe**.
3. An antivirus or proxy can alter downloads: add an exception or try another network.
4. If nothing helps, [report the error](#reporting-an-error): the current state on GitHub is broken.

### VL-404
**Game profile could not be moved**

During a full update, the old source folder is swapped for the new one and your game profile (`run/`) is
moved over. That did not work, usually because files were open. The launcher restores the previous state.

**Fix:**
1. Close Minecraft, Explorer windows in the data folder and other programs ([VL-202](#vl-202)).
2. Press **Play** again or use Settings → Updates → *Update now*.
3. **Important:** Do not delete any `source-old-…` folders in the data folder that contain `run/client`.
   The launcher automatically brings back a profile stored there on the next start.

### VL-405
**Vibe files are missing**

Important files are missing from the installed source code, e.g. the Gradle wrapper
(`gradle/wrapper/gradle-wrapper.jar`). This happens when files were deleted by hand or removed by an
antivirus.

**Fix:** Settings → Troubleshooting → **Reinstall Vibe**. Your game profile is kept.

## 5xx: Building Vibe (Gradle)

Before every start, the launcher builds Vibe from the source code with Gradle and then starts Forge 1.8.9
with OptiFine. The **first build takes 5–15 minutes** because Gradle, Minecraft, Forge and all libraries
are downloaded; after that it is much faster. The full output is in the console and in
`logs/game/<date>.log`.

### VL-500
**Build failed**

The build failed for a reason the launcher cannot pin down further. The message shows the first and the
last line of Gradle's "What went wrong".

**Fix:**
1. Open the console and read the section after `* What went wrong:`.
2. Settings → Troubleshooting → **Clear build cache**, then start again.
3. If that does not help: **Reinstall Vibe**.
4. If the error remains, [report it](#reporting-an-error) with the game log.

### VL-501
**Download during the build failed**

Gradle could not download Minecraft, Forge or a library (`Could not resolve …`, `Could not GET …`,
`Read timed out`, `Received status code 5xx`).

**Fix:**
1. Check the connection and try again in a few minutes; Maven servers are sometimes briefly
   overloaded.
2. Allow the build servers from the [table under 1xx](#1xx-internet-and-servers) in your firewall and
   filters.
3. Turn off VPN and proxy to test.
4. If it always stops at the same file, delete the Gradle cache ([VL-506](#vl-506)).

### VL-502
**Vibe does not compile**

The Java compiler reports errors in the Vibe source code (`Compilation failed`, `error: cannot find symbol`).

**Fix:**
1. Settings → Troubleshooting → **Clear build cache**, then start.
2. If that does not help: **Reinstall Vibe** (in case files were changed by hand).
3. If the error remains, the current Vibe version on GitHub is broken. Wait for an update (the launcher
   downloads it automatically) and [report the error](#reporting-an-error).

### VL-503
**Gradle could not be set up**

Gradle itself (the build software, about 130 MB from `services.gradle.org`) could not be downloaded, or
the download is damaged (`Could not install Gradle distribution`, `Exception in thread "main" java.net…`,
`zip END header not found`).

**Fix:**
1. Check the connection to `services.gradle.org` and try again.
2. If the download is damaged: close the launcher and delete the folder `wrapper/dists/gradle-8.8-bin` in
   the Gradle folder (Windows: `%USERPROFILE%\.gradle\wrapper\dists\gradle-8.8-bin`). Gradle is
   downloaded again on the next start.

### VL-504
**OptiFine could not be downloaded**

Vibe starts with OptiFine 1.8.9 HD U M6 pre2, which is downloaded from optifine.net on the first build.
optifine.net did not provide a download link, or the file is not a valid OptiFine JAR.

**Fix:**
1. Try again later; optifine.net is sometimes overloaded or changes its download page.
2. **By hand:** Download `preview_OptiFine_1.8.9_HD_U_M6_pre2.jar` via
   https://optifine.net/adloadx?f=preview_OptiFine_1.8.9_HD_U_M6_pre2.jar (click "Download" after the ad)
   and save the file unchanged as
   `<data folder>/source/build/optifine/preview_OptiFine_1.8.9_HD_U_M6_pre2.jar`. Create the `optifine`
   folder if it is missing. The file is kept across updates and when the build cache is cleared.
3. Ad or DNS filters can block optifine.net: add an exception.

### VL-505
**Not enough memory for the build**

Gradle needs up to 3 GB of memory while building. It could not start or ran out
(`Unable to start the daemon process`, `Could not reserve enough space for object heap`,
`OutOfMemoryError`).

**Fix:**
1. Close browsers, games and other large programs and start again.
2. **Windows:** The paging file must be enabled. Control Panel → System → Advanced system settings →
   Performance → Settings → Advanced → Virtual memory → enable *Automatically manage paging file size for
   all drives* and restart.
3. Lower Settings → Game → **Memory** so that Gradle and Minecraft fit in together.
4. At least 8 GB of RAM is recommended.

### VL-506
**Gradle cache damaged**

Files in Gradle's cache are broken, usually after a crash, a full drive or an interrupted download
(`Could not read workspace metadata`, `invalid LOC header`, `error in opening zip file`).

**Fix:**
1. Settings → Troubleshooting → **Clear build cache**, then start.
2. If that does not help: close the launcher and delete the `caches` folder in the Gradle folder
   (Windows: `%USERPROFILE%\.gradle\caches`). Everything is downloaded again on the next start (about
   1 GB). If you also use Gradle for other projects, their dependencies are downloaded again as well.

### VL-507
**Build files are locked**

Another Gradle process holds a lock on Gradle's cache (`Timeout waiting to lock`,
`It is currently in use by another Gradle instance`).

**Fix:**
1. End other builds: IDEs such as IntelliJ IDEA, an open `run.bat`/`gradlew`, a second launcher.
2. End hanging Java processes in the Task Manager ("OpenJDK Platform binary").
3. Restart your PC if the lock remains.

### VL-508
**Minecraft files are missing**

Unimined (the Gradle plugin for Minecraft) did not prepare the Minecraft 1.8.9 client
(`Unimined did not prepare the Minecraft 1.8.9 client`), usually after an interrupted first build.

**Fix:**
1. Close the launcher and delete `caches/unimined` in the Gradle folder
   (Windows: `%USERPROFILE%\.gradle\caches\unimined`).
2. Start the launcher with an internet connection and press **Play**: Minecraft is downloaded again.

## 6xx: Starting and playing Minecraft

These codes appear when the build worked but Minecraft does not start or exits with an error. If there is
a fresh crash report, **Open crash report** opens it directly; it is in
`<data folder>/source/run/client/crash-reports/`.

### VL-600
**Minecraft crashed**

Minecraft exited with an error. The cause could not be matched to one of the more specific codes.

**Fix:**
1. Open the crash report (button in the message) or the console. What matters is the `Description:` line
   and the first line of the stack trace below it.
2. If you have custom mods, disable them on the Mods page and start again. If it works then, enable them
   one by one until the error comes back.
3. Settings → Troubleshooting → **Clear build cache**.
4. If it remains, [report the error](#reporting-an-error) with the crash report.

Note: Ending Minecraft through the Task Manager also produces this code.

### VL-601
**Minecraft could not reserve its memory**

Java could not get the configured memory and could not even start Minecraft
(`Could not reserve enough space for object heap`, `Error occurred during initialization of VM`,
`There is insufficient memory for the Java Runtime Environment`).

**Fix:**
1. Lower Settings → Game → **Memory** (e.g. to 2–3 GB).
2. Close other programs; Gradle also uses memory while you play.
3. Windows: Enable the paging file (see [VL-505](#vl-505)).

### VL-602
**Minecraft ran out of memory**

Minecraft used up its memory (`java.lang.OutOfMemoryError`).

**Fix:**
1. Raise Settings → Game → **Memory** to 3–4 GB. More than 6 GB rarely helps with 1.8.9.
2. Use smaller resource packs (512x and higher need a lot of memory), fewer shaders and mods.
3. Lower the render distance.

### VL-603
**Graphics driver problem**

Minecraft could not open an OpenGL window (`Pixel format not accelerated`, `No OpenGL context found`,
`LWJGLException`, `GLXBadFBConfig`).

**Fix:**
1. **Install the latest graphics driver** directly from the manufacturer (NVIDIA, AMD or Intel), not just
   via Windows Update.
2. **Laptops with two graphics cards:** Windows: Settings → System → Display → Graphics → *Browse* → add
   `<data folder>\runtime\temurin-8\<folder>\bin\java.exe` and `javaw.exe` → Options →
   *High performance*. With NVIDIA, alternatively in the NVIDIA Control Panel.
3. **Remote desktop/streaming:** There is often no OpenGL over an RDP session; start directly on the PC.
4. **Linux:** Install the graphics drivers (Mesa or the proprietary driver) and `xrandr`
   (Debian/Ubuntu: `sudo apt install x11-xserver-utils`). Minecraft 1.8.9 needs X11; on Wayland it runs
   through XWayland.

### VL-604
**A mod prevents the start**

Forge rejected a custom mod, or a mod crashed while loading (`MissingModsException`,
`DuplicateModsFoundException`, `WrongMinecraftVersionException`, `UnsupportedClassVersionError`). The
message shows the line that names the mod.

**Fix:**
1. Open the Mods page and disable all custom mods. If Vibe starts then, enable them one by one to find the
   culprit.
2. `Missing Mods`/`requires`: the mod needs another mod that is missing; install it too.
3. `Duplicate Mods`: a mod is in the mods folder twice; remove one copy.
4. `UnsupportedClassVersionError`: the mod is built for newer Minecraft/Java versions and does not run
   with 1.8.9; look for a 1.8.9 version.

### VL-605
**Game libraries could not be loaded**

The native LWJGL libraries (graphics, sound, input) could not be loaded (`UnsatisfiedLinkError`,
`no lwjgl64 in java.library.path`).

**Fix:**
1. Settings → Troubleshooting → **Clear build cache**: the libraries are unpacked again.
2. Check your antivirus's quarantine (e.g. `lwjgl64.dll`, `OpenAL64.dll`) and add the data folder and the
   Gradle folder as exceptions.
3. Linux on ARM processors is not supported by Minecraft 1.8.9 (LWJGL 2).

### VL-606
**Java crashed**

The Java runtime itself crashed (`A fatal error has been detected by the Java Runtime Environment`,
`EXCEPTION_ACCESS_VIOLATION`). There is then no Minecraft crash report but a file
`hs_err_pid<number>.log` in the game folder (`<data folder>/source/run/client/`).

**Causes:** Almost always the graphics driver or programs that hook into the game.

**Fix:**
1. Update the graphics driver ([VL-603](#vl-603)).
2. Turn off overlays: Discord overlay, MSI Afterburner/RivaTuner, Overwolf, GeForce Experience overlay,
   recording tools.
3. Disable custom mods and shaders to test.
4. If nothing helps, [report the error](#reporting-an-error) with the `hs_err_pid…log` file.

### VL-607
**Rosetta 2 is missing**

On Macs with Apple chips (M1, M2, …), Minecraft 1.8.9 runs on an Intel Java, which needs Rosetta 2
(`Bad CPU type in executable`).

**Fix:** Open Terminal and run:

```
softwareupdate --install-rosetta --agree-to-license
```

Then restart the launcher and press **Play**.

## 7xx: The launcher itself

### VL-701
**Launcher is already open**

A Vibe Launcher with the same data folder is already running. Two launchers would get in each other's way
when updating and building.

**Fix:**
1. Look for the open window (taskbar, Alt+Tab; it may be minimized).
2. If none is visible, an old launcher is hanging in the background: Windows Task Manager → end
   "OpenJDK Platform binary" or "Java(TM) Platform SE binary" with the Vibe Launcher. macOS/Linux:
   `pkill -f VibeLauncher.jar`.
3. After a launcher update, the launcher restarts on its own for a moment; wait a few seconds.

### VL-702
**No desktop available**

The launcher was started without a graphical interface, e.g. over SSH, in a Docker container or on Linux
without `DISPLAY`. The message appears on the command line.

**Fix:** Start the launcher in a normal desktop session. Under WSL you need WSLg (Windows 11).

### VL-703
**Launcher update failed**

The launcher update could not be downloaded or installed.

**Fix:**
1. Try again later (Settings → Updates → *Install update*).
2. If `VibeLauncher.jar` is in a folder without write permission (e.g. `C:\Program Files`), move the file
   to the desktop or your user folder.
3. **By hand:** Download the latest `VibeLauncher.jar` from the
   [GitHub releases](https://github.com/SkidderClub/Vibe/releases), close the launcher and replace the old
   file.

### VL-704
**Launcher update damaged**

The downloaded update did not match its checksum and was discarded for your safety.

**Fix:** Try again later. If it keeps happening, download the file by hand ([VL-703](#vl-703)) and check
your antivirus or proxy.

### VL-705
**Launcher does not run from VibeLauncher.jar**

Automatic updates and restarts (e.g. after changing the language) only work when the launcher runs from
the `VibeLauncher.jar` file, not from an IDE or from unpacked classes.

**Fix:** Start the launcher with `java -jar VibeLauncher.jar` or by double-clicking the JAR. After
changing the language, you can also simply restart it by hand.

### VL-706
**Account vault unreadable**

Vibe's account list (`accounts.vault`) is damaged or does not match the key (`accounts.key`), e.g.
because one of the two files comes from another profile. Both are in
`<data folder>/source/run/client/vibe/accounts/`. The Accounts page shows the error; Vibe itself reports
"Cannot read the account vault" in the Alt Manager and does not save any accounts until this is fixed.

**Fix:**
1. If you have a backup of the `accounts` folder, restore `accounts.vault` and `accounts.key`
   **together** from it. The two files always belong together; copied separately they do not match.
2. If there is no backup: close Minecraft and rename `accounts.vault` to `accounts.vault.broken`. On the
   next start, Vibe begins with an empty vault. Then press **Add account** and sign in again in the Alt
   Manager.
3. Your Minecraft or Microsoft account itself is not affected, only the locally saved sign-in.

### VL-707
**Account key missing**

`accounts.vault` is there, but `accounts.key` is missing. Without the key the vault cannot be decrypted,
and Vibe does not create a new one as long as the old vault exists.

**Fix:**
1. Restore `accounts.key` from a backup or the recycle bin (same folder as `accounts.vault`).
2. If that is not possible, the saved sign-ins are lost: rename `accounts.vault` (see
   [VL-706](#vl-706), step 2) and sign in again in the Alt Manager.

### VL-708
**Could not open**

A folder, file or link could not be opened because no suitable program (file manager, text editor,
browser) was found. The message names the path or address, and so does the console.

**Fix:** Open the path or link by hand. On Linux, installing `xdg-utils` often helps.

### VL-709
**Launcher could not start**

An unexpected error occurred while building the window.

**Fix:**
1. Start the launcher again.
2. Update the Java you open the launcher with.
3. If it remains, [report the error](#reporting-an-error) with `logs/launcher.log` from the data folder.

## 8xx: Custom mods

These codes are shown next to rejected files when you drag mods onto the launcher or select them with
**Add mods**, e.g. `Sodium.jar: Fabric mods do not work with Forge 1.8.9 (VL-806)`. Vibe loads mods for
**Forge 1.8.9**.

### VL-801
**Not a mod file**

Only `.jar` or `.zip` files can be mods. Unpack downloaded archives (`.rar`, `.7z`) and add the `.jar`
inside.

### VL-802
**Mod file too large**

Files over 300 MB are refused. Check that it really is a mod (and not a modpack or a game).

### VL-803
**Damaged mod file**

The file is not a valid archive, often an interrupted download or an HTML page with a `.jar` extension.
Download the mod again from its official page.

### VL-804
**OptiFine is already included**

Vibe always starts with OptiFine 1.8.9 HD U M6 pre2. A second OptiFine version would prevent the start;
you do not need to do anything.

### VL-805
**Vibe is loaded automatically**

You added a Vibe JAR. The launcher builds and loads Vibe itself; a second copy would make Forge abort.

### VL-806
**Fabric mod**

Fabric mods do not work with Forge. Look on the mod's page for a version for **Forge 1.8.9**.

### VL-807
**Mod for a newer Minecraft**

The mod is for Forge 1.13 or newer (it contains `META-INF/mods.toml`). Look for a version for 1.8.9.

### VL-808
**Mod already installed**

A file with the same name is already in the mods folder (enabled or disabled). To install a newer
version, first remove the old one on the Mods page.

### VL-809
**File name already taken**

When enabling or disabling a mod, the target file already exists, e.g. `Mod.jar` and
`Mod.jar.disabled` are next to each other. Open the mods folder (Mods page → *Open folder*) and delete one
of the two files.

### VL-810
**Mod for another Minecraft version**

Not an error but a warning on the Mods page: according to `mcmod.info`, the mod was made for another
Minecraft version. It may not load ([VL-604](#vl-604)). Look for a 1.8.9 version.

## 900: Unexpected errors

### VL-900
**Unexpected error**

An error that has no code of its own. The message contains the technical description.

**Fix:**
1. Try again and restart the launcher if necessary.
2. If it remains, [report the error](#reporting-an-error) with the launcher log. That way the problem will
   get its own code in the future.

## Problems without an error code

**`VibeLauncher.jar` does not open on double-click.**
To open the JAR you need Java 8 or newer installed.
1. Install Java, e.g. Eclipse Temurin 21 from https://adoptium.net (enable "Set JAVA_HOME" and
   "Associate .jar" during setup).
2. If WinRAR or 7-Zip opens instead, `.jar` is associated with the wrong program: right-click → Open with
   → *Java(TM) Platform SE binary* or *OpenJDK Platform binary* → Always use this app.
3. Or start it from the command line in the file's folder: `java -jar VibeLauncher.jar`. Error messages
   then appear there.
4. If Java reports `UnsupportedClassVersionError … class file version 52.0`, your Java is older than
   Java 8: update it.

**The first start takes very long.**
That is normal: the first time, Vibe, two Java versions, Gradle, Minecraft, Forge and OptiFine are
downloaded and Vibe is built (5–15 minutes, depending on your connection). The play bar shows the
progress, the console every line. Later starts are much faster because only what changed is rebuilt.

**The game starts, but with the wrong account or not signed in.**
Choose the account on the Accounts page. *Automatic* uses Vibe's Auto Login. Renew expired Microsoft
sign-ins in the Alt Manager (Add account).

**The skin is missing in the preview.**
Skins come from Mojang's servers. Offline accounts and accounts without their own skin show Steve or Alex.
Without internet, the last loaded skin stays visible. This only affects the preview, not the game.

**Minecraft keeps running after the launcher is closed.**
That is intended: the game is independent of the launcher. When you open the launcher again, it connects
to the running game (requires Java 9 or newer for the launcher).

## Reporting an error

If none of the fixes help, report the error on [Discord](https://dsc.gg/vibe-skidder-club) or as a
[GitHub issue](https://github.com/SkidderClub/Vibe/issues). Include the following:

1. The **error code** and the full message (a screenshot is enough).
2. The **launcher log**: `logs/launcher.log` in the data folder (Console → *Launcher log*).
3. The **game log** of the failed start: `logs/game/<date>.log` (Console → *Game log*).
4. For crashes, the **crash report** from `source/run/client/crash-reports/` or the
   `hs_err_pid…log` file.
5. Your operating system and, if known, graphics card and memory.

The launcher itself does not write passwords or sign-in tokens to its logs: from the account vault it only
reads names and UUIDs. Still, take a quick look over the files before you share them publicly.
