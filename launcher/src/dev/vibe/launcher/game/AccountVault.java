package dev.vibe.launcher.game;

import dev.vibe.launcher.core.Json;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Read-only view of Vibe's encrypted {@code AccountStore}. Only names, UUIDs and
 * the account type are extracted; refresh tokens are skipped by the parser and
 * the decrypted buffers are wiped. Sign-in itself stays in Vibe's Alt Manager.
 */
public final class AccountVault {
    public static final class Account {
        public final String name, uuid;
        public final boolean microsoft, autoLogin;
        Account(String name, String uuid, boolean microsoft, boolean autoLogin) {
            this.name = name; this.uuid = uuid; this.microsoft = microsoft; this.autoLogin = autoLogin;
        }
    }

    private static final byte[] MAGIC = "VIBEAC01".getBytes(StandardCharsets.US_ASCII);

    /** An error whose message is known to contain no vault content. */
    private static final class SafeException extends IOException {
        private static final long serialVersionUID = 1L;
        SafeException(String message) { super(message); }
    }

    private AccountVault() { }

    public static List<Account> read(Path directory) throws IOException {
        Path vault = directory.resolve("accounts.vault"), keyFile = directory.resolve("accounts.key");
        if (!Files.isRegularFile(vault, LinkOption.NOFOLLOW_LINKS)) return Collections.emptyList();
        if (!Files.isRegularFile(keyFile, LinkOption.NOFOLLOW_LINKS)) throw new IOException("The account key is missing.");
        String autoLogin = "";
        Path autoLoginFile = directory.resolve("auto-login.txt");
        if (Files.isRegularFile(autoLoginFile)) autoLogin = new String(bounded(autoLoginFile, 256), StandardCharsets.UTF_8).trim();

        byte[] data = bounded(vault, 2 * 1024 * 1024), key = bounded(keyFile, 16), plain = null;
        char[] text = null;
        try {
            if (data.length < MAGIC.length + 12 + 16 || !Arrays.equals(MAGIC, Arrays.copyOf(data, MAGIC.length)) || key.length != 16) {
                throw new SafeException("The account vault is not a Vibe vault.");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, Arrays.copyOfRange(data, MAGIC.length, MAGIC.length + 12)));
            cipher.updateAAD(MAGIC);
            plain = cipher.doFinal(data, MAGIC.length + 12, data.length - MAGIC.length - 12);
            CharBuffer chars = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(plain));
            text = new char[chars.remaining()];
            chars.get(text);
            if (chars.hasArray()) Arrays.fill(chars.array(), '\0');
            Map<String, Object> root = Json.object(Json.parse(text, Collections.singleton("refreshToken")));
            List<Account> result = new ArrayList<Account>();
            for (Object item : Json.array(root, "accounts")) {
                Map<String, Object> entry = Json.object(item);
                String name = Json.string(entry, "name"), uuid = Json.string(entry, "uuid");
                if (!name.matches("[A-Za-z0-9_]{1,16}")) continue;
                try { uuid = UUID.fromString(uuid).toString(); } catch (IllegalArgumentException ignored) { continue; }
                boolean microsoft = Boolean.TRUE.equals(entry.get("refreshToken"));
                String identity = (microsoft ? "microsoft:" : "offline:") + uuid;
                result.add(new Account(name, uuid, microsoft, identity.equalsIgnoreCase(autoLogin)));
                if (result.size() >= 1000) break;
            }
            return result;
        } catch (SafeException error) {
            throw error;
        } catch (Exception error) {
            // Never attach the cause: cipher and parser messages could echo vault content.
            throw new IOException("The account vault could not be decrypted.");
        } finally {
            Arrays.fill(data, (byte) 0);
            Arrays.fill(key, (byte) 0);
            if (plain != null) Arrays.fill(plain, (byte) 0);
            if (text != null) Arrays.fill(text, '\0');
        }
    }

    private static byte[] bounded(Path file, int limit) throws IOException {
        InputStream input = Files.newInputStream(file);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > limit) throw new IOException(file.getFileName() + " is too large.");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }
}
