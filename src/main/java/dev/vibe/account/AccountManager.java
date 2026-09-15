package dev.vibe.account;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;

/** Serializes authentication and persistence; only the Minecraft thread changes the live session. */
public final class AccountManager {
    private final Minecraft minecraft;
    private final Session launcherSession;
    private final AccountStore store;
    private final CookieFolderImporter cookieFolder;
    private final MicrosoftLogin auth = new MicrosoftLogin();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Vibe accounts");
        thread.setDaemon(true);
        return thread;
    });
    private List<Account> accounts = new ArrayList<>();
    private Future<?> operation;
    private long generation;
    private volatile boolean busy;
    private volatile String status = "Add a Microsoft account or select a saved account.";
    private volatile boolean error;
    private volatile MicrosoftLogin.DeviceCode deviceCode;
    private boolean storageUnavailable;
    private AutoLoginPreference autoLogin;

    public AccountManager(Minecraft minecraft, Path directory) {
        this.minecraft = minecraft;
        launcherSession = minecraft.getSession();
        store = new AccountStore(directory);
        cookieFolder = new CookieFolderImporter(directory.resolveSibling("cookies"),
                cookies -> auth.fromCookies(cookies, ignored -> { }).account, this::saveAccount);
        try {
            accounts = store.load();
        } catch (IOException e) {
            storageUnavailable = true;
            error = true;
            status = "Cannot read the account vault. Restore accounts.vault and its matching accounts.key, then restart.";
        }
        try { autoLogin = new AutoLoginPreference(directory); }
        catch (IOException e) { report("Cannot read the Auto Login preference. Check the account folder permissions."); }
        worker.scheduleWithFixedDelay(() -> {
            if (!busy && !storageUnavailable) cookieFolder.scan();
        }, 0, 3, TimeUnit.SECONDS);
        if (!storageUnavailable) {
            Account startup = accounts.stream().filter(this::isAutoLogin).findFirst().orElse(null);
            if (startup != null) login(startup);
        }
    }

    public synchronized List<Account> getAccounts() { return Collections.unmodifiableList(new ArrayList<>(accounts)); }
    public boolean isBusy() { return busy; }
    public boolean hasError() { return error; }
    public String getStatus() { return status; }
    public MicrosoftLogin.DeviceCode getDeviceCode() { return deviceCode; }
    public boolean isStorageAvailable() { return !storageUnavailable; }
    public CookieFolderImporter getCookieFolder() { return cookieFolder; }

    public synchronized boolean isAutoLogin(Account account) { return autoLogin != null && autoLogin.matches(account); }

    public synchronized void toggleAutoLogin(Account account) {
        if (busy || storageUnavailable || account == null || accounts.stream().noneMatch(account::sameIdentity)) return;
        if (autoLogin == null) { report("Auto Login preference is unavailable. Check the account folder permissions, then restart."); return; }
        try {
            boolean disable = isAutoLogin(account);
            autoLogin.set(disable ? null : account);
            error = false;
            status = disable ? "Auto Login disabled." : "Auto Login enabled for " + account.getName() + ".";
        } catch (IOException e) { report("Could not save Auto Login. Check the account folder permissions."); }
    }

    public void addMicrosoft() {
        start("Requesting Microsoft sign-in code...", ticket -> {
            MicrosoftLogin.DeviceCode code = auth.begin();
            synchronized (this) {
                checkCurrent(ticket);
                deviceCode = code;
                status = "Open the Microsoft sign-in page and enter the code below.";
            }
            // Opening a browser is best-effort; the URL and code always remain available in the GUI.
            openBrowser(code.verificationUri);
            MicrosoftLogin.Result result = auth.await(code, message -> progress(ticket, message));
            persist(ticket, result.account);
            finish(ticket, () -> activate(result.account, result.accessToken()));
        });
    }

    public void login(Account account) {
        if (account == null) return;
        start("Signing in as " + account.getName() + "...", ticket -> {
            // An in-flight folder import may update this profile before the queued login starts.
            Account current = getAccounts().stream().filter(account::sameIdentity).findFirst().orElse(account);
            if (!current.isMicrosoft()) {
                finish(ticket, () -> activate(current, "0"));
                return;
            }
            MicrosoftLogin.Result result = auth.refresh(current, updated -> {
                try { persist(ticket, updated); }
                catch (IOException e) { throw new UncheckedIOException(e); }
            }, message -> progress(ticket, message));
            persist(ticket, result.account);
            finish(ticket, () -> activate(result.account, result.accessToken()));
        });
    }

    public void addMicrosoftCookies(Path source) {
        if (source == null) return;
        start("Reading Microsoft cookie export...", ticket -> {
            MicrosoftLogin.Result result = auth.fromCookies(source, message -> progress(ticket, message));
            persist(ticket, result.account);
            finish(ticket, () -> activate(result.account, result.accessToken()));
        });
    }

    public void addOffline(String name) {
        final Account account;
        try { account = Account.offline(name); }
        catch (IllegalArgumentException e) { report("Use 1-16 letters, numbers, or underscores."); return; }
        start("Saving offline profile...", ticket -> {
            persist(ticket, account);
            finish(ticket, () -> activate(account, "0"));
        });
    }

    public void remove(Account account) {
        if (account == null) return;
        start("Removing saved account...", ticket -> {
            synchronized (this) {
                checkCurrent(ticket);
                List<Account> next = new ArrayList<>(accounts);
                next.removeIf(account::sameIdentity);
                store.save(next);
                accounts = next;
                if (isAutoLogin(account)) autoLogin.set(null);
            }
            finish(ticket, () -> status = "Removed " + account.getName() + " from saved accounts. Current session remains active.");
        });
    }

    public void restoreLauncher() {
        if (busy) return;
        try {
            setSession(launcherSession);
            error = false;
            status = "Restored launcher account: " + launcherSession.getUsername() + ".";
        } catch (IOException e) { report(e.getMessage()); }
    }

    public void importBackup(Path source) {
        start("Importing Vibe account backups...", ticket -> {
            List<Path> files;
            if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
                try (Stream<Path> stream = Files.list(source)) {
                    files = stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                            .filter(AccountManager::isBackup).sorted().limit(101).collect(Collectors.toList());
                }
            } else {
                files = Collections.singletonList(source);
            }
            if (files.size() > 100) throw new IOException("Choose a folder with at most 100 Vibe backups.");
            if (files.isEmpty()) throw new IOException("No .vibeaccounts backups found in the selected folder.");
            List<Account> added = new ArrayList<>();
            int rejected = 0;
            int duplicates = 0;
            List<Account> next = new ArrayList<>(getAccounts());
            // Do not hold the UI state lock while scanning/decrypting a directory.
            for (Path file : files) {
                synchronized (this) { checkCurrent(ticket); }
                if (!isBackup(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) { rejected++; continue; }
                List<Account> imported;
                try { imported = store.read(file); }
                catch (IOException e) { rejected++; continue; }
                for (Account account : imported) {
                    // Backups must not replace a newer, rotated credential with an older one.
                    if (next.stream().anyMatch(account::sameIdentity)) { duplicates++; continue; }
                    if (next.size() >= 1000) throw new IOException("Import would exceed the account limit (1000). No accounts were imported.");
                    next.add(account);
                    added.add(account);
                }
            }
            synchronized (this) {
                checkCurrent(ticket);
                if (!added.isEmpty()) {
                    store.save(next);
                    accounts = next;
                }
            }
            final String summary = "Imported " + added.size() + " accounts; " + duplicates + " duplicates; " + rejected + " unreadable files.";
            finish(ticket, () -> {
                status = summary;
                if (added.size() == 1) login(added.get(0));
            });
        });
    }

    public void exportBackup(Path target) {
        start("Exporting encrypted Vibe backup...", ticket -> {
            if (!isBackup(target)) throw new IOException("Use the .vibeaccounts extension for account backups.");
            synchronized (this) {
                checkCurrent(ticket);
                store.write(target, accounts);
            }
            finish(ticket, () -> status = "Encrypted backup saved. It requires this installation's accounts.key.");
        });
    }

    private static boolean isBackup(Path path) {
        return path.getFileName() != null && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".vibeaccounts");
    }

    public synchronized void cancel() {
        if (!busy) return;
        generation++;
        if (operation != null) operation.cancel(true);
        busy = false;
        deviceCode = null;
        error = false;
        status = "Operation cancelled.";
    }

    public synchronized void report(String message) {
        error = true;
        status = message;
    }

    private synchronized void start(String message, Work work) {
        if (busy) return;
        if (storageUnavailable) { error = true; return; }
        if (minecraft.theWorld != null) { report("Disconnect from the world or server before changing accounts."); return; }
        final long ticket = ++generation;
        busy = true;
        error = false;
        deviceCode = null;
        status = message;
        operation = worker.submit(() -> {
            try {
                work.run(ticket);
            } catch (InterruptedException | CancellationException e) {
                Thread.currentThread().interrupt();
            } catch (UncheckedIOException e) {
                fail(ticket, "Could not save refreshed account credentials. Check the account folder permissions.");
            } catch (IOException e) {
                fail(ticket, e.getMessage() == null ? "Account operation failed. Please try again." : e.getMessage());
            } catch (RuntimeException e) {
                fail(ticket, "Account operation failed. Please try signing in again.");
            }
        });
    }

    private synchronized void persist(long ticket, Account account) throws IOException {
        checkCurrent(ticket);
        saveAccount(account);
    }

    private synchronized boolean saveAccount(Account account) throws IOException {
        List<Account> next = new ArrayList<>(accounts);
        int index = -1;
        for (int i = 0; i < next.size(); i++) if (account.sameIdentity(next.get(i))) { index = i; break; }
        if (index == -1) next.add(account); else next.set(index, account);
        store.save(next);
        accounts = next;
        return index == -1;
    }

    private synchronized void progress(long ticket, String message) {
        checkCurrent(ticket);
        status = message;
        deviceCode = null;
    }

    private void finish(long ticket, Runnable completed) {
        minecraft.addScheduledTask(() -> {
            synchronized (AccountManager.this) {
                if (ticket != generation) return;
                busy = false;
                deviceCode = null;
                completed.run();
            }
        });
    }

    private void fail(long ticket, String message) {
        finish(ticket, () -> report(message));
    }

    private void checkCurrent(long ticket) {
        if (ticket != generation || Thread.currentThread().isInterrupted()) throw new CancellationException();
    }

    private void activate(Account account, String accessToken) {
        try {
            setSession(new Session(account.getName(), account.getUuid().toString().replace("-", ""), accessToken,
                    account.isMicrosoft() ? "mojang" : "legacy"));
            status = "Active: " + account.getName() + (account.isMicrosoft() ? " (Microsoft)." : " (offline / local servers only).");
            error = false;
        } catch (IOException e) { report(e.getMessage()); }
    }

    private void setSession(Session session) throws IOException {
        if (minecraft.theWorld != null) throw new IOException("Account saved. Disconnect from the world before switching.");
        if (!minecraft.isCallingFromMinecraftThread()) throw new IOException("Account switch must run on the game thread.");
        try {
            Field field = null;
            for (String name : new String[] {"session", "field_71449_j"}) {
                try { field = Minecraft.class.getDeclaredField(name); break; }
                catch (NoSuchFieldException ignored) { }
            }
            if (field == null) throw new NoSuchFieldException();
            field.setAccessible(true);
            field.set(minecraft, session);
            if (minecraft.getSession() != session) throw new IllegalStateException();
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IOException("This Minecraft runtime does not allow account switching.");
        }
    }

    public static boolean openBrowser(java.net.URI uri) {
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(uri);
                return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private interface Work { void run(long ticket) throws IOException, InterruptedException; }
}
