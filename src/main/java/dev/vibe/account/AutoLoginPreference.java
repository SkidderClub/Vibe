package dev.vibe.account;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** One local account identity, kept outside shareable profiles and account backups. */
public final class AutoLoginPreference {
    private final Path file;
    private String identity = "";

    public AutoLoginPreference(Path directory) throws IOException {
        file = directory.resolve("auto-login.txt");
        if (Files.exists(file)) identity = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
    }

    public boolean matches(Account account) { return account != null && identity.equals(key(account)); }

    public void set(Account account) throws IOException {
        String next = account == null ? "" : key(account);
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "auto-login-", ".tmp");
        try {
            Files.write(temporary, next.getBytes(StandardCharsets.UTF_8));
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            identity = next;
        } finally { Files.deleteIfExists(temporary); }
    }

    private static String key(Account account) { return (account.isMicrosoft() ? "microsoft:" : "offline:") + account.getUuid(); }
}
