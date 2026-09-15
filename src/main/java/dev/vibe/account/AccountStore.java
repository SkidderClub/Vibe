package dev.vibe.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Encrypted, atomic local storage, separate from shareable Vibe settings. */
public final class AccountStore {
    private static final byte[] MAGIC = "VIBEAC01".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private final Path directory;
    private final Path file;
    private final Path keyFile;
    private final SecureRandom random = new SecureRandom();

    public AccountStore(Path directory) {
        this.directory = directory;
        this.file = directory.resolve("accounts.vault");
        this.keyFile = directory.resolve("accounts.key");
    }

    public List<Account> load() throws IOException {
        if (!Files.exists(file)) return new ArrayList<>();
        return read(file);
    }

    public void save(List<Account> accounts) throws IOException {
        write(file, accounts);
    }

    public List<Account> read(Path source) throws IOException {
        try {
            byte[] data = readBounded(source, MAX_BYTES);
            if (data.length < MAGIC.length + 12 + 16
                    || !Arrays.equals(MAGIC, Arrays.copyOf(data, MAGIC.length))) {
                throw new IOException("This is not a Vibe account backup.");
            }
            byte[] iv = Arrays.copyOfRange(data, MAGIC.length, MAGIC.length + 12);
            Cipher cipher = cipher(Cipher.DECRYPT_MODE, key(false), iv);
            byte[] plain = cipher.doFinal(data, MAGIC.length + 12, data.length - MAGIC.length - 12);
            try {
                JsonObject root = new JsonParser().parse(new String(plain, StandardCharsets.UTF_8)).getAsJsonObject();
                int version = root.get("version").getAsInt();
                if (version != 1 && version != 2) throw new IOException("Unsupported account file version.");
                JsonArray entries = root.getAsJsonArray("accounts");
                if (entries.size() > 1000) throw new IOException("Account file is too large.");
                List<Account> result = new ArrayList<>();
                for (JsonElement entry : entries) {
                    JsonObject value = entry.getAsJsonObject();
                    Account account = new Account(value.get("name").getAsString(),
                            UUID.fromString(value.get("uuid").getAsString()), value.get("refreshToken").getAsString(),
                            version == 1 ? MicrosoftApplication.IAS : MicrosoftApplication.valueOf(value.get("application").getAsString()));
                    if (result.stream().noneMatch(account::sameIdentity)) result.add(account);
                }
                return result;
            } finally {
                Arrays.fill(plain, (byte) 0);
            }
        } catch (GeneralSecurityException | RuntimeException e) {
            // Never attach parser errors: they can contain plaintext credentials.
            throw new IOException("Account file is damaged or belongs to another Vibe installation.");
        }
    }

    public void write(Path target, List<Account> accounts) throws IOException {
        if (accounts.size() > 1000) throw new IOException("Account limit reached (1000).");
        JsonObject root = new JsonObject();
        root.addProperty("version", 2);
        JsonArray entries = new JsonArray();
        for (Account account : accounts) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", account.getName());
            entry.addProperty("uuid", account.getUuid().toString());
            entry.addProperty("refreshToken", account.refreshToken());
            entry.addProperty("application", account.application().name());
            entries.add(entry);
        }
        root.add("accounts", entries);
        byte[] plain = root.toString().getBytes(StandardCharsets.UTF_8);
        try {
            if (plain.length > MAX_BYTES - 64) throw new IOException("Account file is too large.");
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            byte[] encrypted = cipher(Cipher.ENCRYPT_MODE, key(true), iv).doFinal(plain);
            atomicWrite(target, ByteBuffer.allocate(MAGIC.length + iv.length + encrypted.length)
                    .put(MAGIC).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IOException("Account encryption is unavailable.");
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    private byte[] key(boolean create) throws IOException {
        Files.createDirectories(directory);
        if (!Files.exists(keyFile)) {
            if (!create || Files.exists(file)) throw new IOException("The local account key is missing.");
            byte[] key = new byte[16]; // AES-128 also works with older Java 8 policy files.
            random.nextBytes(key);
            Path temporary = Files.createTempFile(directory, ".vibe-key-", ".tmp");
            try {
                restrict(temporary);
                Files.write(temporary, key);
                // No REPLACE_EXISTING: never replace a key created by another client.
                Files.move(temporary, keyFile);
            } finally {
                Arrays.fill(key, (byte) 0);
                Files.deleteIfExists(temporary);
            }
        }
        byte[] key = readBounded(keyFile, 16);
        if (key.length != 16) throw new IOException("The local account key is invalid.");
        restrict(keyFile);
        return key;
    }

    private static Cipher cipher(int mode, byte[] key, byte[] iv) throws GeneralSecurityException {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(MAGIC);
            return cipher;
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private static byte[] readBounded(Path path, int limit) throws IOException {
        try (java.io.InputStream in = Files.newInputStream(path);
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (out.size() + count > limit) throw new IOException("Account file is too large.");
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        }
    }

    private static void atomicWrite(Path target, byte[] data) throws IOException {
        Path absolute = target.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path temporary = Files.createTempFile(absolute.getParent(), ".vibe-accounts-", ".tmp");
        try {
            restrict(temporary);
            Files.write(temporary, data);
            try {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void restrict(Path path) throws IOException {
        PosixFileAttributeView posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (posix != null) {
            posix.setPermissions(PosixFilePermissions.fromString("rw-------"));
            return;
        }
        AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (acl != null) acl.setAcl(Collections.singletonList(AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(path))
                .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
    }
}
