package dev.vibe.launcher.app;

import dev.vibe.launcher.VibeLauncher;
import dev.vibe.launcher.core.AppLog;
import dev.vibe.launcher.core.AppPaths;
import dev.vibe.launcher.core.ErrorCode;
import dev.vibe.launcher.core.FileUtil;
import dev.vibe.launcher.core.I18n;
import dev.vibe.launcher.core.LauncherException;
import dev.vibe.launcher.core.Platform;
import dev.vibe.launcher.core.Progress;
import dev.vibe.launcher.core.Settings;
import dev.vibe.launcher.core.Text;
import dev.vibe.launcher.core.Version;
import dev.vibe.launcher.game.AccountVault;
import dev.vibe.launcher.game.ExtensionLibrary;
import dev.vibe.launcher.game.FailureAnalyzer;
import dev.vibe.launcher.game.GameProfile;
import dev.vibe.launcher.game.GameSession;
import dev.vibe.launcher.game.LaunchMode;
import dev.vibe.launcher.game.ModLibrary;
import dev.vibe.launcher.game.Theme;
import dev.vibe.launcher.install.ChangelogSection;
import dev.vibe.launcher.install.LauncherUpdater;
import dev.vibe.launcher.install.RuntimeManager;
import dev.vibe.launcher.minecraft.GameRuntime;
import dev.vibe.launcher.minecraft.LaunchCommand;
import dev.vibe.launcher.skin.SkinService;
import dev.vibe.launcher.versions.ReleaseCatalog;
import dev.vibe.launcher.versions.ReleaseNotes;
import dev.vibe.launcher.versions.VersionStore;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.swing.SwingUtilities;

/**
 * The launcher's state and every operation the interface can trigger.
 *
 * <p>Vibe itself is a published JAR: the launcher downloads versions from
 * {@code VibeUpdate/VibeReleases}, keeps them under {@code versions/} and links
 * the chosen one into {@code mods/}. Minecraft, Forge and OptiFine are prepared
 * separately once, so launching later needs no source checkout or Gradle.</p>
 *
 * <p>Long-running work runs on one worker thread, so downloads, version changes
 * and launches never overlap; listeners are notified on the Swing thread.</p>
 */
public final class LauncherController {
    public enum Event { STATE, ACCOUNTS, MODS, THEME, VERSIONS, LAUNCHER_UPDATE, SETTINGS, SKIN }

    public enum State { IDLE, PREPARING, STARTING, RUNNING, STOPPING }

    public interface Listener { void changed(Event event); }

    public interface Notifier {
        void notify(Notice notice);
    }

    /** A toast: level, text, an optional action and, for errors, a link to the fix. */
    public static final class Notice {
        public enum Level { INFO, SUCCESS, WARNING, ERROR }
        public final Level level;
        public final String title, message, actionLabel;
        public final Runnable action;
        /** The error code shown in the title, or {@code null}. */
        public final ErrorCode code;
        /** Opens the error guide at {@link #code}; {@code null} when there is nothing to look up. */
        public final Runnable help;
        /** Action marker: the interface opens its console page. */
        public static final Runnable SHOW_CONSOLE = () -> { };
        public Notice(Level level, String title, String message, String actionLabel, Runnable action) {
            this(level, title, message, actionLabel, action, null, null);
        }
        public Notice(Level level, String title, String message, String actionLabel, Runnable action, ErrorCode code, Runnable help) {
            this.level = level; this.title = title; this.message = message; this.actionLabel = actionLabel; this.action = action; this.code = code; this.help = help;
        }
    }

    /** The last failed download, version change or launch, shown in the play bar until the next attempt. */
    public static final class Failure {
        public final ErrorCode code;
        public final String detail;
        Failure(ErrorCode code, String detail) { this.code = code; this.detail = detail; }
    }

    private static final long MB = 1024L * 1024L;
    private static final long MINECRAFT_SPACE = 1600L * MB;
    private static final long JAVA_SPACE = 700L * MB;
    private static final long VERSION_SPACE = 300L * MB;

    private final AppPaths paths;
    private final Settings settings;
    private final AppLog log;
    private final ReleaseCatalog catalog;
    private final VersionStore versions;
    private final GameRuntime runtime;
    private final RuntimeManager runtimes;
    private final LauncherUpdater updater;
    private final SkinService skins;
    private final GameProfile profile;
    private final ModLibrary mods;
    private final ExtensionLibrary extensions;
    private final ConsoleBuffer console = new ConsoleBuffer();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<Listener>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Vibe launcher worker");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Notifier notifier;

    private volatile State state = State.IDLE;
    private volatile String progressTitle = "", progressDetail = "";
    private volatile double progress = -1;
    private volatile Failure lastFailure;
    private volatile boolean launchQueued;
    /** Guards the hand-off between a queued Play click and the preparing task. */
    private final Object queueLock = new Object();
    private boolean queueOpen;
    private volatile Future<?> task;
    private volatile Runnable exit = () -> System.exit(0);
    private volatile GameSession session;
    private volatile LaunchMode runningMode;
    private volatile long lastActivity;

    private volatile Theme theme;
    private volatile LaunchMode mode;
    private volatile List<AccountVault.Account> accounts = Collections.emptyList();
    private volatile String accountsError = "";
    private volatile ErrorCode accountsCode;
    private volatile List<ModLibrary.Mod> modList = Collections.emptyList();
    private volatile List<ExtensionLibrary.Entry> extensionList = Collections.emptyList();
    private volatile List<ReleaseCatalog.Release> releases = Collections.emptyList();
    private volatile List<VersionStore.Installed> versionList = Collections.emptyList();
    private volatile String releasesError = "";
    private volatile boolean releasesBusy;
    private volatile List<ChangelogSection> changelog = Collections.emptyList();
    private volatile LauncherUpdater.Release launcherUpdate;
    private volatile String launcherUpdateStatus = "";
    private volatile SkinService.Skin skin;

    public LauncherController(AppPaths paths, Settings settings, AppLog log) {
        this.paths = paths;
        this.settings = settings;
        this.log = log;
        this.catalog = new ReleaseCatalog(paths.releasesCache(), log);
        this.versions = new VersionStore(paths, settings, log);
        this.runtime = new GameRuntime(paths, log);
        this.runtimes = new RuntimeManager(paths, settings, log);
        this.updater = new LauncherUpdater(paths, log);
        this.skins = new SkinService(paths.skinCache(), log);
        this.profile = new GameProfile(paths.profile());
        this.mods = new ModLibrary(profile.mods());
        this.extensions = new ExtensionLibrary(mods, profile.vibe().resolve("extensions.properties"));
        this.extensionList = extensions.list(modList);
        Theme shared = profile.readTheme();
        this.theme = shared != null ? shared : Theme.parse(settings.theme());
        this.mode = LaunchMode.parse(settings.launchMode());
        log.addListener((level, message) -> {
            if (!"INFO".equals(level)) console.append("[Launcher] " + level + ": " + message);
        });
    }

    // ---- lifecycle ---------------------------------------------------------

    /** Loads local state and starts the background checks; call once the window is visible. */
    public void start() {
        reloadAccounts();
        reloadMods();
        refreshVersionList();
        refreshNotes();
        console.append("[Launcher] " + VibeLauncher.PRODUCT + " " + VibeLauncher.VERSION + " · Java " + System.getProperty("java.version")
                + " · " + System.getProperty("os.name"));
        GameSession attached = GameSession.reattach(paths.sessionFile(), sessionListener());
        if (attached != null) {
            session = attached;
            if (attached.reachedGame()) setState(State.RUNNING, I18n.t("Vibe is running"), "", 1);
            else setState(State.STARTING, I18n.t("Starting Minecraft"), I18n.t("Forge is loading mods"), 0.05);
            console.append("[Launcher] Reconnected to the running game.");
        }
        worker.execute(() -> {
            versions.cleanLeftovers();
            updater.cleanUp();
        });
        boolean setupNeeded = versionList.isEmpty() || !runtime.isReady();
        // Never touch downloaded files while a game started earlier is still using them.
        if (attached == null && (setupNeeded || settings.autoUpdate())) {
            submit(setupNeeded ? I18n.t("Preparing Vibe") : I18n.t("Checking for updates"), false, false);
        } else {
            worker.execute(this::loadReleases);
        }
        worker.execute(this::checkLauncherUpdate);
    }

    /** Stops background work. Downloads are interrupted so no half-written file stays behind. */
    public void shutdown() {
        worker.shutdownNow();
        try { worker.awaitTermination(20, java.util.concurrent.TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }

    /** How the launcher exits after a self-update; the window saves its bounds first. */
    public void setExit(Runnable exit) { this.exit = exit; }

    // ---- listeners -------------------------------------------------------

    public void addListener(Listener listener) { listeners.add(listener); }
    public void setNotifier(Notifier notifier) { this.notifier = notifier; }
    public ConsoleBuffer console() { return console; }

    private void fire(final Event event) {
        SwingUtilities.invokeLater(() -> { for (Listener listener : listeners) listener.changed(event); });
    }

    private void notice(Notice.Level level, String title, String message, String actionLabel, Runnable action) {
        notice(new Notice(level, title, message, actionLabel, action));
    }

    private void notice(final Notice notice) {
        SwingUtilities.invokeLater(() -> { Notifier current = notifier; if (current != null) current.notify(notice); });
    }

    // ---- failures ----------------------------------------------------------

    private void fail(String context, Throwable error, ErrorCode fallback, boolean blocking, String actionLabel, Runnable action) {
        ErrorCode code = ErrorCode.of(error, fallback);
        log.error(code.id() + " " + context, error);
        report(code, context + ": " + Text.describe(error), blocking, actionLabel, action);
    }

    private void report(ErrorCode code, String detail, boolean blocking, String actionLabel, Runnable action) {
        if (blocking) lastFailure = new Failure(code, detail);
        console.append("[Launcher] " + code.heading() + " \u2013 " + code.hint());
        console.append("[Launcher] " + I18n.t("Help: {0}", code.helpUrl()));
        Runnable help = code == ErrorCode.OPEN_FAILED ? null : () -> openHelp(code);
        notice(new Notice(Notice.Level.ERROR, code.heading(), detail + "\n" + code.hint(), actionLabel, action, code, help));
        if (blocking) fire(Event.STATE);
    }

    public void openHelp(ErrorCode code) { browse(code == null ? ErrorCode.HELP_URL : code.helpUrl()); }

    // ---- getters ---------------------------------------------------------

    public Settings settings() { return settings; }
    public AppPaths paths() { return paths; }
    public GameProfile profile() { return profile; }
    public AppLog log() { return log; }
    public State state() { return state; }
    public boolean busy() { return state != State.IDLE; }
    public String progressTitle() { return progressTitle; }
    public String progressDetail() { return progressDetail; }
    public double progress() { return progress; }
    public Failure lastFailure() { return lastFailure; }
    public boolean launchQueued() { return launchQueued; }
    public boolean canQueueLaunch() { synchronized (queueLock) { return queueOpen && !launchQueued; } }
    public LaunchMode runningMode() { return runningMode; }
    public Theme theme() { return theme; }
    public LaunchMode mode() { return mode; }
    public List<AccountVault.Account> accounts() { return accounts; }
    public String accountsError() { return accountsError; }
    public ErrorCode accountsCode() { return accountsCode; }
    public List<ModLibrary.Mod> mods() { return modList; }
    public List<ExtensionLibrary.Entry> extensions() { return extensionList; }
    public LauncherUpdater.Release launcherUpdate() { return launcherUpdate; }
    public String launcherUpdateStatus() { return launcherUpdateStatus; }
    public Path logFile() { return log.file(); }

    // ---- versions ---------------------------------------------------------

    /** Published versions, newest first; may come from the cache when GitHub is unreachable. */
    public List<ReleaseCatalog.Release> releases() { return releases; }
    public List<VersionStore.Installed> installedVersions() { return versionList; }
    public String releasesError() { return releasesError; }
    public boolean releasesBusy() { return releasesBusy; }
    public VersionStore.Installed selectedVersion() { return versions.installed(settings.selectedVibeVersion()); }
    public Path versionsFolder() { return paths.versions(); }

    /** The selected version's display version, or "" when none is installed. */
    public String vibeVersion() {
        VersionStore.Installed selected = selectedVersion();
        if (selected != null) return selected.displayVersion();
        return versionList.isEmpty() ? "" : versionList.get(0).displayVersion();
    }

    /** The selected tag, or "" when none is installed. */
    public String installedRevision() {
        VersionStore.Installed selected = selectedVersion();
        if (selected != null) return selected.tag;
        return versionList.isEmpty() ? "" : versionList.get(0).tag;
    }

    public boolean sourceInstalled() { return !versionList.isEmpty(); }

    /** Whether a newer stable release exists than the selected one. */
    public boolean updateAvailable() {
        ReleaseCatalog.Release newest = latestStable();
        VersionStore.Installed selected = selectedVersion();
        return newest != null && selected != null && Version.isNewer(newest.version(), selected.version);
    }

    /** The newest stable release, or the newest release when all are pre-releases. */
    public ReleaseCatalog.Release latestStable() {
        List<ReleaseCatalog.Release> list = releases;
        for (ReleaseCatalog.Release release : list) if (!release.prerelease) return release;
        return list.isEmpty() ? null : list.get(0);
    }

    public ReleaseCatalog.Release release(String tag) {
        if (tag == null) return null;
        for (ReleaseCatalog.Release release : releases) if (release.tag.equalsIgnoreCase(tag)) return release;
        return null;
    }

    /** Release notes stored with an installed version. */
    public List<ChangelogSection> notes(String tag) {
        List<ChangelogSection> sections = versions.notes(tag);
        if (!sections.isEmpty()) return sections;
        ReleaseCatalog.Release release = release(tag);
        return release == null ? Collections.<ChangelogSection>emptyList() : ReleaseNotes.parse(release.body);
    }

    /** The selected version's release notes, else the newest release's notes. */
    public List<ChangelogSection> changelog() {
        List<ChangelogSection> sections = changelog;
        return sections.isEmpty() && release(installedRevision()) != null
                ? ReleaseNotes.parse(release(installedRevision()).body) : sections;
    }

    private void refreshVersionList() {
        versionList = versions.listInstalled();
    }

    private void refreshNotes() {
        List<ChangelogSection> sections = versions.selectedNotes();
        if (sections.isEmpty()) {
            ReleaseCatalog.Release newest = versionList.isEmpty() ? latestStable() : release(installedRevision());
            if (newest != null) sections = ReleaseNotes.parse(newest.body);
        }
        changelog = sections;
    }

    public void refreshReleases() {
        if (state != State.IDLE) return;
        worker.execute(this::loadReleases);
    }

    /** Reads GitHub (or the cache) and updates the version page; never throws. */
    private void loadReleases() {
        releasesBusy = true;
        fire(Event.VERSIONS);
        try {
            ReleaseCatalog.Snapshot snapshot = catalog.fetch();
            releases = snapshot.releases;
            releasesError = snapshot.warning == null ? "" : snapshot.warning;
        } catch (IOException error) {
            if (LauncherException.cancelled(error)) return;
            ErrorCode code = ErrorCode.of(error, ErrorCode.UNEXPECTED_RESPONSE);
            releasesError = Text.describe(error) + " (" + code.id() + ")";
            log.warn(code.id() + " Could not read the Vibe release list: " + Text.describe(error));
        } finally {
            releasesBusy = false;
        }
        refreshNotes();
        fire(Event.VERSIONS);
    }

    /** Downloads a release (when needed) and makes it the version in {@code mods/}. */
    public void installVersion(final ReleaseCatalog.Release release) {
        if (release == null) return;
        if (refuseWhileRunning()) return;
        if (state != State.IDLE) return;
        lastFailure = null;
        setState(State.PREPARING, I18n.t("Downloading Vibe {0}", release.version()), "", -1);
        task = worker.submit(() -> {
            try {
                VersionStore.Installed installed = versions.installed(release.tag);
                if (installed == null) {
                    requireSpace(Collections.singletonMap(paths.versions(), VERSION_SPACE));
                    installed = versions.install(release, progressRange(0, 0.9));
                }
                versions.select(installed.tag, modsDir());
                refreshVersionList();
                refreshNotes();
                fire(Event.VERSIONS);
                notice(Notice.Level.SUCCESS, I18n.t("Vibe {0} is ready", installed.displayVersion()),
                        I18n.t("It starts with the next Play."), null, null);
            } catch (Exception error) {
                if (!LauncherException.cancelled(error) && !Thread.currentThread().isInterrupted()) {
                    fail(I18n.t("Vibe {0} could not be installed", release.version()), error, ErrorCode.VIBE_DOWNLOAD, true,
                            I18n.t("Show console"), Notice.SHOW_CONSOLE);
                }
            }
            setState(State.IDLE, "", "", -1);
        });
    }

    /** Switches to an already installed version. */
    public void useVersion(final String tag) {
        if (tag == null || tag.isEmpty()) return;
        if (refuseWhileRunning()) return;
        if (state != State.IDLE) return;
        lastFailure = null;
        setState(State.PREPARING, I18n.t("Switching to Vibe {0}", displayTag(tag)), "", -1);
        task = worker.submit(() -> {
            try {
                VersionStore.Installed installed = versions.installed(tag);
                if (installed == null) throw new LauncherException(ErrorCode.SOURCE_DAMAGED, I18n.t("Vibe {0} is not installed.", tag));
                versions.select(tag, modsDir());
                refreshVersionList();
                refreshNotes();
                fire(Event.VERSIONS);
            } catch (Exception error) {
                fail(I18n.t("Could not switch the Vibe version"), error, ErrorCode.FILE_ERROR, true, I18n.t("Show console"), Notice.SHOW_CONSOLE);
            }
            setState(State.IDLE, "", "", -1);
        });
    }

    /** Deletes a downloaded version; the active one falls back to the newest remaining. */
    public void removeVersion(final String tag) {
        if (tag == null || tag.isEmpty()) return;
        if (refuseWhileRunning()) return;
        if (state != State.IDLE) return;
        lastFailure = null;
        setState(State.PREPARING, I18n.t("Removing Vibe {0}", displayTag(tag)), "", -1);
        task = worker.submit(() -> {
            try {
                boolean active = tag.equalsIgnoreCase(settings.selectedVibeVersion());
                versions.remove(tag);
                if (active) {
                    List<VersionStore.Installed> remaining = versions.listInstalled();
                    if (remaining.isEmpty()) {
                        versions.clearActive(modsDir());
                        settings.setSelectedVibeVersion("");
                    } else {
                        versions.select(remaining.get(0).tag, modsDir());
                        changelog = versions.selectedNotes();
                    }
                }
                refreshVersionList();
                refreshNotes();
                fire(Event.VERSIONS);
            } catch (Exception error) {
                fail(I18n.t("Vibe {0} could not be removed", displayTag(tag)), error, ErrorCode.FILE_IN_USE, true,
                        I18n.t("Show console"), Notice.SHOW_CONSOLE);
            }
            setState(State.IDLE, "", "", -1);
        });
    }

    /** Downloads the newest stable release and switches to it. */
    public void updateVibeNow() {
        if (refuseWhileRunning()) return;
        if (state != State.IDLE) return;
        lastFailure = null;
        setState(State.PREPARING, I18n.t("Updating Vibe"), "", -1);
        task = worker.submit(() -> {
            try {
                if (releases.isEmpty()) loadReleases();
                ReleaseCatalog.Release newest = latestStable();
                if (newest == null) {
                    throw new LauncherException(ErrorCode.VIBE_DOWNLOAD,
                            I18n.t("No Vibe version could be found. Check your internet connection and refresh the version list."));
                }
                requireSpace(Collections.singletonMap(paths.versions(), VERSION_SPACE));
                VersionStore.Installed installed = versions.installed(newest.tag);
                if (installed == null) installed = versions.install(newest, progressRange(0, 0.9));
                versions.select(installed.tag, modsDir());
                refreshVersionList();
                refreshNotes();
                fire(Event.VERSIONS);
                notice(Notice.Level.SUCCESS, I18n.t("Vibe updated"), I18n.t("Vibe {0} is ready.", installed.displayVersion()), null, null);
            } catch (Exception error) {
                if (!LauncherException.cancelled(error) && !Thread.currentThread().isInterrupted()) {
                    fail(I18n.t("Vibe could not be updated"), error, ErrorCode.VIBE_DOWNLOAD, true, I18n.t("Show console"), Notice.SHOW_CONSOLE);
                }
            }
            setState(State.IDLE, "", "", -1);
        });
    }

    /** Downloads the selected version again, e.g. after a damaged file. */
    public void reinstallVersion() {
        final VersionStore.Installed selected = selectedVersion();
        if (selected == null) {
            installVersion(latestStable());
            return;
        }
        if (refuseWhileRunning()) return;
        if (state != State.IDLE) return;
        lastFailure = null;
        setState(State.PREPARING, I18n.t("Reinstalling Vibe {0}", selected.displayVersion()), "", -1);
        task = worker.submit(() -> {
            try {
                if (releases.isEmpty()) loadReleases();
                ReleaseCatalog.Release release = release(selected.tag);
                versions.remove(selected.tag);
                if (release == null) {
                    throw new LauncherException(ErrorCode.VIBE_DOWNLOAD,
                            I18n.t("No Vibe version could be found. Check your internet connection and refresh the version list."));
                }
                requireSpace(Collections.singletonMap(paths.versions(), VERSION_SPACE));
                VersionStore.Installed installed = versions.install(release, progressRange(0, 0.9));
                versions.select(installed.tag, modsDir());
                refreshVersionList();
                refreshNotes();
                fire(Event.VERSIONS);
            } catch (Exception error) {
                if (!LauncherException.cancelled(error) && !Thread.currentThread().isInterrupted()) {
                    fail(I18n.t("Vibe could not be reinstalled"), error, ErrorCode.VIBE_DOWNLOAD, true, I18n.t("Show console"), Notice.SHOW_CONSOLE);
                }
            }
            setState(State.IDLE, "", "", -1);
        });
    }

    /** Deletes Minecraft/Forge/OptiFine and prepares them again; versions and saves stay. */
    public void repairRuntime() {
        if (refuseWhileRunning()) return;
        if (state != State.IDLE) return;
        lastFailure = null;
        setState(State.PREPARING, I18n.t("Repairing game files"), "", -1);
        task = worker.submit(() -> {
            try {
                requireSpace(Collections.singletonMap(paths.minecraft(), MINECRAFT_SPACE));
                runtime.repair(progressRange(0, 1));
                notice(Notice.Level.SUCCESS, I18n.t("Game files repaired"), I18n.t("Minecraft, Forge and OptiFine were downloaded again."), null, null);
            } catch (Exception error) {
                if (!LauncherException.cancelled(error) && !Thread.currentThread().isInterrupted()) {
                    fail(I18n.t("The game files could not be repaired"), error, ErrorCode.RUNTIME_INCOMPLETE, true, null, null);
                }
            }
            setState(State.IDLE, "", "", -1);
        });
    }

    private boolean refuseWhileRunning() {
        if (state == State.STARTING || state == State.RUNNING || state == State.STOPPING) {
            notice(new Notice(Notice.Level.WARNING, I18n.t("Minecraft is running"),
                    I18n.t("Close Minecraft to switch versions."), null, null));
            return true;
        }
        return false;
    }

    private Path modsDir() { return profile.mods(); }

    private static String displayTag(String tag) { return tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag; }

    private Progress progressRange(double from, double to) {
        return (message, detail, fraction) -> setProgress(message, detail, fraction < 0 ? -1 : from + (to - from) * fraction);
    }

    /** The account that will be used, or {@code null} for Vibe's own auto-login. */
    public AccountVault.Account selectedAccount() {
        String uuid = settings.selectedUuid();
        if (uuid.isEmpty()) return null;
        for (AccountVault.Account account : accounts) if (account.uuid.equalsIgnoreCase(uuid)) return account;
        return null;
    }

    public AccountVault.Account autoLoginAccount() {
        for (AccountVault.Account account : accounts) if (account.autoLogin) return account;
        return null;
    }

    public String displayName() {
        AccountVault.Account account = selectedAccount();
        if (account == null) account = autoLoginAccount();
        if (account != null) return account.name;
        String gamertag = profile.gamertag();
        return gamertag.isEmpty() ? "Player" : gamertag;
    }

    public SkinService.Skin skin() {
        SkinService.Skin current = skin;
        return current != null ? current : skins.defaultSkin("");
    }

    public SkinService skins() { return skins; }

    // ---- settings --------------------------------------------------------

    public void setMode(LaunchMode next) {
        mode = next;
        settings.setLaunchMode(next.name());
        fire(Event.SETTINGS);
    }

    public void setTheme(Theme next) {
        theme = next;
        settings.setTheme(next.name());
        try { profile.writeTheme(next); } catch (IOException error) { log.warn("Could not share the theme with Vibe", error); }
        fire(Event.THEME);
    }

    public void settingsChanged() { fire(Event.SETTINGS); }

    // ---- accounts --------------------------------------------------------

    public void reloadAccounts() {
        try {
            accounts = AccountVault.read(profile.accounts());
            accountsError = "";
            accountsCode = null;
        } catch (IOException error) {
            accounts = Collections.emptyList();
            accountsCode = ErrorCode.of(error, ErrorCode.ACCOUNT_VAULT);
            accountsError = Text.describe(error);
            log.warn(accountsCode.id() + " Account vault unavailable: " + accountsError);
        }
        if (!settings.selectedUuid().isEmpty() && selectedAccount() == null && accountsError.isEmpty() && !accounts.isEmpty()) {
            settings.setSelectedAccount("", "");
        }
        fire(Event.ACCOUNTS);
        refreshSkin();
    }

    public void selectAccount(AccountVault.Account account) {
        settings.setSelectedAccount(account == null ? "" : account.uuid, account == null ? "" : account.name);
        if (state == State.IDLE) writeBridge(LaunchMode.VIBE);
        fire(Event.ACCOUNTS);
        refreshSkin();
    }

    private void refreshSkin() {
        AccountVault.Account account = selectedAccount();
        if (account == null) account = autoLoginAccount();
        final String uuid = account == null ? "" : account.uuid;
        final String name = account == null ? profile.gamertag() : account.name;
        skin = skins.current(uuid, name);
        fire(Event.SKIN);
        if (account == null && name.isEmpty()) return;
        skins.load(uuid, name, account != null && account.microsoft, loaded -> {
            AccountVault.Account now = selectedAccount();
            if (now == null) now = autoLoginAccount();
            String currentUuid = now == null ? "" : now.uuid;
            if (currentUuid.equals(uuid)) {
                skin = loaded;
                fire(Event.SKIN);
            }
        });
    }

    public void openAltManager() { launch(LaunchMode.ACCOUNTS); }

    // ---- mods --------------------------------------------------------------

    public void reloadMods() {
        modList = mods.list();
        extensionList = extensions.list(modList);
        fire(Event.MODS);
    }

    public Path modsFolder() { return mods.folder(); }

    public void importMods(final List<File> files) { importMods(files, null); }

    public void importMods(final List<File> files, final java.util.function.Consumer<Boolean> imported) {
        if (state == State.PREPARING) notice(Notice.Level.INFO, I18n.t("Mods queued"), I18n.t("They are added as soon as the current step has finished."), null, null);
        worker.execute(() -> {
            try {
                ModLibrary.ImportResult result = mods.importFiles(files);
                extensions.imported(result.extensionIds);
                reloadMods();
                if (!result.added.isEmpty()) {
                    notice(Notice.Level.SUCCESS, result.extensionsAdded > 0 ? I18n.t("Extensions added") :
                            result.added.size() == 1 ? I18n.t("Mod added") : I18n.t("{0} mods added", result.added.size()),
                            join(result.added), null, null);
                    log.info("Added mods: " + join(result.added));
                }
                if (!result.rejected.isEmpty()) {
                    final ErrorCode first = result.rejectedCodes.get(0);
                    notice(new Notice(Notice.Level.WARNING, I18n.t("Some files were not added"), join(result.rejected), null, null, first, () -> openHelp(first)));
                }
                if (imported != null) SwingUtilities.invokeLater(() -> imported.accept(result.extensionsAdded > 0));
            } catch (IOException error) {
                fail(I18n.t("Could not add mods"), error, ErrorCode.FILE_ERROR, false, null, null);
            }
        });
    }

    public void setExtensionEnabled(ExtensionLibrary.Entry extension, boolean enabled) {
        worker.execute(() -> {
            try { extensions.setEnabled(extension, enabled, state == State.IDLE); }
            catch (IOException error) { fail(I18n.t("Could not change the extension"), error, ErrorCode.FILE_ERROR, false, null, null); }
            reloadMods();
        });
    }

    public void deleteExtension(ExtensionLibrary.Entry extension) {
        worker.execute(() -> {
            try { extensions.delete(extension); }
            catch (IOException error) { fail(I18n.t("Could not remove the extension"), error, ErrorCode.FILE_ERROR, false, null, null); }
            reloadMods();
        });
    }

    public void setModEnabled(ModLibrary.Mod mod, boolean enabled) {
        try { mods.setEnabled(mod, enabled); }
        catch (IOException error) { fail(I18n.t("Could not change the mod"), error, ErrorCode.FILE_ERROR, false, null, null); }
        reloadMods();
    }

    public void deleteMod(ModLibrary.Mod mod) {
        try {
            mods.delete(mod);
            log.info("Removed mod " + mod.fileName);
        } catch (IOException error) {
            fail(I18n.t("Could not remove the mod"), error, ErrorCode.FILE_ERROR, false, null, null);
        }
        reloadMods();
    }

    // ---- play ------------------------------------------------------------

    public void play() { launch(mode); }

    private void launch(LaunchMode requested) {
        synchronized (queueLock) {
            if (queueOpen) {
                launchQueued = true;
                runningMode = requested;
                fire(Event.STATE);
                return;
            }
        }
        if (state != State.IDLE) return;
        runningMode = requested;
        launchQueued = true;
        submit(I18n.t("Preparing {0}", requested.label), true, false);
    }

    /** Prepares versions and runtime and, if a launch is queued, starts the game afterwards. */
    private void submit(final String title, final boolean forLaunch, final boolean upgrade) {
        lastFailure = null;
        synchronized (queueLock) { queueOpen = true; }
        setState(State.PREPARING, title, "", -1);
        task = worker.submit(() -> {
            try {
                prepare(forLaunch, upgrade);
                boolean start;
                synchronized (queueLock) {
                    start = launchQueued;
                    launchQueued = false;
                    queueOpen = false;
                }
                if (start) startGame();
                else setState(State.IDLE, "", "", -1);
            } catch (Exception error) {
                closeQueue();
                if (Thread.currentThread().isInterrupted() || LauncherException.cancelled(error)) {
                    setState(State.IDLE, "", "", -1);
                    console.append("[Launcher] Cancelled.");
                    return;
                }
                fail(I18n.t("{0} failed", title), error, ErrorCode.UNEXPECTED, true, I18n.t("Show console"), Notice.SHOW_CONSOLE);
                setState(State.IDLE, "", "", -1);
            }
        });
    }

    private void closeQueue() {
        synchronized (queueLock) {
            launchQueued = false;
            queueOpen = false;
        }
    }

    /**
     * Picks the version to play, downloads what is missing and prepares Minecraft.
     * With automatic updates the selected version is upgraded first; a failed
     * upgrade only warns, so an installed version still starts offline.
     */
    private void prepare(boolean forLaunch, boolean upgrade) throws Exception {
        Progress progress = (message, detail, fraction) -> setProgress(message, detail, fraction);
        String tag = settings.selectedVibeVersion();
        VersionStore.Installed installed = versions.installed(tag);

        if (releases.isEmpty()) loadReleases();
        ReleaseCatalog.Release newest = latestStable();

        if ((upgrade || settings.autoUpdate()) && newest != null) {
            boolean newer = installed == null || Version.isNewer(newest.version(), installed.version);
            if (newer) {
                try {
                    requireSpace(Collections.singletonMap(paths.versions(), VERSION_SPACE));
                    VersionStore.Installed updated = versions.installed(newest.tag);
                    if (updated == null) updated = versions.install(newest, progressRange(0, 0.15));
                    tag = updated.tag;
                    installed = updated;
                } catch (IOException error) {
                    if (LauncherException.cancelled(error)) throw error;
                    log.warn("Automatic Vibe update failed; keeping the installed version: " + Text.describe(error));
                }
            }
        }

        if (installed == null) {
            if (tag.isEmpty()) {
                ReleaseCatalog.Release target = newest;
                if (target == null) {
                    List<VersionStore.Installed> known = versions.listInstalled();
                    if (!known.isEmpty()) installed = known.get(0);
                    else if (!releasesError.isEmpty()) {
                        throw new LauncherException(ErrorCode.VIBE_DOWNLOAD,
                                I18n.t("No Vibe version is installed and GitHub is not reachable: {0}", releasesError));
                    } else {
                        throw new LauncherException(ErrorCode.VIBE_DOWNLOAD,
                                I18n.t("No Vibe version is installed. Pick one on the Versions page."));
                    }
                } else {
                    requireSpace(Collections.singletonMap(paths.versions(), VERSION_SPACE));
                    installed = versions.install(target, progressRange(0, 0.15));
                }
            } else {
                ReleaseCatalog.Release target = release(tag);
                if (target != null) {
                    requireSpace(Collections.singletonMap(paths.versions(), VERSION_SPACE));
                    installed = versions.install(target, progressRange(0, 0.15));
                } else {
                    List<VersionStore.Installed> known = versions.listInstalled();
                    if (!known.isEmpty()) installed = known.get(0);
                    else throw new LauncherException(ErrorCode.VIBE_DOWNLOAD, I18n.t("Vibe {0} is not installed.", tag));
                }
            }
        }

        if (installed == null) {
            throw new LauncherException(ErrorCode.VIBE_DOWNLOAD, I18n.t("No Vibe version is installed. Pick one on the Versions page."));
        }
        versions.select(installed.tag, modsDir());
        waitForVersionFile(installed.jar);
        refreshVersionList();
        refreshNotes();
        fire(Event.VERSIONS);

        if (!runtime.isReady()) {
            requireSpace(Collections.singletonMap(paths.minecraft(), MINECRAFT_SPACE));
        }
        if (!runtimes.isReady()) {
            requireSpace(Collections.singletonMap(paths.runtimes(), JAVA_SPACE));
        }
        Path java8 = runtimes.ensure(progressRange(0.15, 0.25));
        settings.setJava8Home(java8.toString());
        runtime.ensure(progressRange(0.25, 1), false);
        writeBridge(runningMode == null ? LaunchMode.VIBE : runningMode);
        if (!forLaunch) console.append("[Launcher] Vibe " + installed.displayVersion() + " is ready.");
    }

    /** The game must not start while the link into mods/ is still being written. */
    private static void waitForVersionFile(Path jar) throws IOException {
        for (int attempt = 0; attempt < 20; attempt++) {
            if (Files.isRegularFile(jar)) return;
            try { Thread.sleep(100); } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new java.io.InterruptedIOException("Cancelled."); }
        }
        throw new LauncherException(ErrorCode.SOURCE_DAMAGED, I18n.t("The Vibe version file is missing. Reinstall it on the Versions page."));
    }

    /**
     * Fails with VL-203 before a download that would run out of disk space
     * halfway. Folders on the same drive add up; an unknown free space is not checked.
     */
    private void requireSpace(Map<Path, Long> needs) throws LauncherException {
        Map<FileStore, Long> total = new LinkedHashMap<FileStore, Long>();
        Map<FileStore, Path> example = new LinkedHashMap<FileStore, Path>();
        for (Map.Entry<Path, Long> need : needs.entrySet()) {
            try {
                Path existing = need.getKey().toAbsolutePath();
                while (existing != null && !Files.exists(existing)) existing = existing.getParent();
                if (existing == null) continue;
                FileStore store = Files.getFileStore(existing);
                Long sum = total.get(store);
                total.put(store, (sum == null ? 0L : sum) + need.getValue());
                if (!example.containsKey(store)) example.put(store, need.getKey());
            } catch (IOException | RuntimeException ignored) {
                // Unknown drive: let the operation try.
            }
        }
        for (Map.Entry<FileStore, Long> entry : total.entrySet()) {
            long free;
            try { free = entry.getKey().getUsableSpace(); } catch (IOException | RuntimeException ignored) { continue; }
            if (free > 0 && free < entry.getValue()) {
                throw new LauncherException(ErrorCode.DISK_FULL, I18n.t("Only {0} free for {1}, about {2} are needed.",
                        Text.bytes(free), example.get(entry.getKey()), Text.bytes(entry.getValue())));
            }
        }
    }

    private void startGame() throws Exception {
        final LaunchMode launchMode = runningMode == null ? LaunchMode.VIBE : runningMode;
        VersionStore.Installed installed = versions.installed(settings.selectedVibeVersion());
        if (installed == null) {
            throw new LauncherException(ErrorCode.VIBE_DOWNLOAD, I18n.t("No Vibe version is installed. Pick one on the Versions page."));
        }
        Path java8 = Paths.get(settings.java8Home());
        writeBridge(launchMode);
        try { profile.writeTheme(theme); } catch (IOException error) { log.warn("Could not share the theme with Vibe", error); }

        GameSession.Request request = new GameSession.Request();
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        request.logFile = paths.gameLogs().resolve(stamp + ".log");
        request.readyFile = paths.root().resolve("session.ready");
        request.sessionFile = paths.sessionFile();
        request.launch = runtime.launch(java8, settings.memoryMb(), displayName(), accountUuid(), request.readyFile);
        pruneGameLogs();

        console.append("");
        console.append("[Launcher] Starting " + launchMode.label + " · Vibe " + installed.displayVersion()
                + " · " + settings.memoryMb() + " MB · " + displayName());
        setState(State.STARTING, I18n.t("Starting Minecraft"), I18n.t("Forge is loading mods"), 0.05);
        log.info("Launching " + launchMode.name() + " with log " + request.logFile);
        session = GameSession.start(request, sessionListener());
    }

    /** The selected account's UUID, or "" for Vibe's own login. */
    private String accountUuid() {
        AccountVault.Account account = selectedAccount();
        return account == null ? "" : account.uuid;
    }

    private GameSession.Listener sessionListener() {
        return new GameSession.Listener() {
            @Override public void output(List<String> lines) {
                if (!lines.isEmpty()) console.append(lines);
                if (state != State.STARTING) return;
                for (int index = lines.size() - 1; index >= 0; index--) {
                    String activity = activity(lines.get(index));
                    if (activity == null) continue;
                    long now = System.currentTimeMillis();
                    if (now - lastActivity > 300) {
                        lastActivity = now;
                        setProgress(progressTitle, activity, progress);
                    }
                    break;
                }
            }

            @Override public void stage(GameSession.Stage stage) {
                if (state == State.STOPPING) return;
                if (stage == GameSession.Stage.RUNNING) {
                    setState(State.RUNNING, I18n.t("Vibe is running"), "", 1);
                    console.append("[Launcher] Minecraft is ready.");
                } else {
                    setState(State.STARTING, I18n.t("Starting Minecraft"), I18n.t("Forge is loading mods"), 0.05);
                }
            }

            @Override public void exited(Integer code, boolean reachedGame, String failure) { sessionEnded(code, reachedGame); }
        };
    }

    private void sessionEnded(Integer code, boolean reachedGame) {
        GameSession ended = session;
        boolean stopped = ended != null && ended.isStopping();
        boolean windowShown = reachedGame || (ended != null && ended.reachedGame());
        session = null;
        runningMode = null;
        console.append("[Launcher] " + (stopped ? "Stopped." : "Game process ended" + (code == null ? "." : " with exit code " + code + ".")));
        reloadAccounts();
        reloadMods();
        refreshVersionList();
        Theme shared = profile.readTheme();
        if (shared != null && shared != theme) {
            theme = shared;
            settings.setTheme(shared.name());
            fire(Event.THEME);
        }
        // The failure is known before the state changes, so the window can come to the front for it.
        if (!stopped && code != null && code != 0) gameCrashed(ended, code, windowShown);
        setState(State.IDLE, "", "", -1);
    }

    /** Reports a Minecraft that ended with an error, using its crash report when there is a fresh one. */
    private void gameCrashed(GameSession ended, Integer exitCode, boolean windowShown) {
        final Path crashReport = profile.latestCrashReport();
        boolean fresh = crashReport != null && System.currentTimeMillis() - crashReport.toFile().lastModified() < 5 * 60 * 1000L;
        String text = fresh ? GameProfile.readCrashReport(crashReport, 256 * 1024) : "";
        FailureAnalyzer.Result result = ended == null ? null : ended.analyzer().gameFailure(text);
        ErrorCode code = result == null ? ErrorCode.GAME_CRASHED : result.code;
        boolean customMods = false;
        for (ModLibrary.Mod mod : modList) customMods |= mod.enabled;
        if (code == ErrorCode.MOD_CONFLICT && !customMods) code = ErrorCode.GAME_CRASHED;
        String detail = windowShown
                ? I18n.t("Minecraft closed unexpectedly (exit code {0}).", exitCode)
                : I18n.t("Minecraft closed before the window appeared (exit code {0}).", exitCode);
        if (result != null && !result.evidence.isEmpty()) detail += "\n" + result.evidence;
        log.error(code.id() + " Minecraft crashed: " + detail.replace('\n', ' ') + (fresh ? " Crash report: " + crashReport : ""), null);
        report(code, detail, true, fresh ? I18n.t("Open crash report") : I18n.t("Show console"),
                fresh ? () -> openPath(crashReport) : Notice.SHOW_CONSOLE);
    }

    public void stop() {
        GameSession current = session;
        if (current == null) return;
        setState(State.STOPPING, I18n.t("Stopping Vibe"), "", -1);
        new Thread(current::stop, "Vibe stop").start();
    }

    /** Cancels preparation or a queued launch. A starting game is stopped like a running one. */
    public void cancel() {
        closeQueue();
        Future<?> current = task;
        if (state == State.PREPARING && current != null) current.cancel(true);
        else if (state == State.STARTING || state == State.RUNNING) stop();
    }

    private void writeBridge(LaunchMode launchMode) {
        try { profile.writeBridge(launchMode, settings.selectedUuid(), settings.selectedName()); }
        catch (IOException error) { log.warn("Could not write the launcher bridge", error); }
    }

    private void pruneGameLogs() {
        List<Path> logs = new ArrayList<Path>(FileUtil.children(paths.gameLogs()));
        for (int index = 0; index < logs.size() - 15; index++) {
            try { Files.deleteIfExists(logs.get(index)); } catch (IOException ignored) { /* in use */ }
        }
    }

    public Path currentGameLog() {
        GameSession current = session;
        if (current != null) return current.logFile();
        List<Path> logs = FileUtil.children(paths.gameLogs());
        return logs.isEmpty() ? null : logs.get(logs.size() - 1);
    }

    // ---- maintenance -------------------------------------------------------

    public void checkForUpdates() {
        if (state != State.IDLE) return;
        worker.execute(this::checkLauncherUpdate);
        submit(I18n.t("Checking for updates"), false, false);
    }

    public void repairRuntimes() {
        if (state != State.IDLE) return;
        lastFailure = null;
        setState(State.PREPARING, I18n.t("Reinstalling Java"), "", -1);
        task = worker.submit(() -> {
            try {
                requireSpace(Collections.singletonMap(paths.runtimes(), JAVA_SPACE));
                Path java8 = runtimes.reinstall(progressRange(0, 1));
                settings.setJava8Home(java8.toString());
                notice(Notice.Level.SUCCESS, I18n.t("Java reinstalled"), I18n.t("Java 8 is ready."), null, null);
            } catch (Exception error) {
                if (!LauncherException.cancelled(error) && !Thread.currentThread().isInterrupted()) {
                    fail(I18n.t("Java could not be installed"), error, ErrorCode.JAVA_BROKEN, true, null, null);
                }
            }
            setState(State.IDLE, "", "", -1);
            fire(Event.SETTINGS);
        });
    }

    public void importMinecraftSettings() {
        worker.execute(() -> {
            try {
                int copied = profile.importFromMinecraft();
                notice(Notice.Level.SUCCESS, I18n.t("Import finished"), copied == 0 ? I18n.t("Everything was already there.") : I18n.t("{0} items copied from .minecraft.", copied), null, null);
            } catch (IOException error) {
                final ErrorCode code = ErrorCode.of(error, ErrorCode.FILE_ERROR);
                log.warn(code.id() + " Import from .minecraft failed", error);
                notice(new Notice(Notice.Level.WARNING, code.id() + " \u00b7 " + I18n.t("Nothing imported"), Text.describe(error) + "\n" + code.hint(),
                        null, null, code, () -> openHelp(code)));
            }
        });
    }

    private void checkLauncherUpdate() {
        try {
            launcherUpdate = updater.check();
            launcherUpdateStatus = launcherUpdate == null ? I18n.t("You have the newest launcher.") : I18n.t("Version {0} is available.", launcherUpdate.version);
            if (launcherUpdate != null) {
                final LauncherUpdater.Release release = launcherUpdate;
                notice(Notice.Level.INFO, I18n.t("Launcher update available"), I18n.t("Version {0} is ready to install.", release.version), I18n.t("Update"), this::installLauncherUpdate);
            }
        } catch (IOException error) {
            ErrorCode code = ErrorCode.of(error, ErrorCode.LAUNCHER_UPDATE);
            launcherUpdateStatus = I18n.t("Update check failed: {0}", Text.describe(error) + " (" + code.id() + ")");
            log.warn(code.id() + " Launcher update check failed: " + Text.describe(error));
        }
        fire(Event.LAUNCHER_UPDATE);
    }

    public void installLauncherUpdate() {
        final LauncherUpdater.Release release = launcherUpdate;
        if (release == null || state != State.IDLE) return;
        setState(State.PREPARING, I18n.t("Updating the launcher"), "", -1);
        task = worker.submit(() -> {
            try {
                Path staged = updater.download(release, (message, detail, fraction) -> setProgress(message, detail, fraction));
                updater.installOnExit(staged);
                SwingUtilities.invokeLater(exit);
            } catch (Exception error) {
                if (LauncherException.cancelled(error) || Thread.currentThread().isInterrupted()) {
                    setState(State.IDLE, "", "", -1);
                    return;
                }
                fail(I18n.t("Launcher update failed"), error, ErrorCode.LAUNCHER_UPDATE, true, null, null);
                setState(State.IDLE, "", "", -1);
            }
        });
    }

    public void openPath(Path path) {
        try {
            if (Files.isRegularFile(path) && java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(path.toFile());
            else Platform.openFolder(path);
        } catch (Exception error) {
            ErrorCode code = ErrorCode.of(error, ErrorCode.OPEN_FAILED);
            log.warn(code.id() + " Could not open " + path, error);
            report(code, I18n.t("Could not open {0}", path) + ": " + Text.describe(error), false, null, null);
        }
    }

    public void browse(String url) {
        try { Platform.browse(url); }
        catch (IOException error) {
            log.warn(ErrorCode.OPEN_FAILED.id() + " Could not open the browser for " + url);
            report(ErrorCode.OPEN_FAILED, I18n.t("Could not open the browser") + ": " + url, false, null, null);
        }
    }

    // ---- state helpers -------------------------------------------------------

    private void setState(State next, String title, String detail, double fraction) {
        state = next;
        progressTitle = title == null ? "" : title;
        progressDetail = detail == null ? "" : detail;
        progress = fraction;
        fire(Event.STATE);
    }

    private void setProgress(String title, String detail, double fraction) {
        progressTitle = title == null ? progressTitle : title;
        progressDetail = detail == null ? "" : detail;
        progress = fraction;
        fire(Event.STATE);
    }

    /** A log line worth showing under the progress bar, or {@code null}. */
    static String activity(String line) {
        String text = line.trim();
        if (text.isEmpty() || text.startsWith("Picked up ") || text.startsWith("at ")) return null;
        // "[17:31:52] [Client thread/INFO]: Setting user" -> "Setting user"
        text = text.replaceFirst("^(\\[[^\\]]{1,60}\\]:?\\s*)+", "").trim();
        if (text.length() < 4 || text.startsWith("{") || text.matches("[.\\d% ]+")) return null;
        return Text.shorten(text, 90);
    }

    private static String join(List<String> values) {
        StringBuilder text = new StringBuilder();
        for (String value : values) {
            if (text.length() > 0) text.append('\n');
            text.append(value);
        }
        return text.toString();
    }
}
