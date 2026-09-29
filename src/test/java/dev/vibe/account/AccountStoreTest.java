package dev.vibe.account;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class AccountStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void encryptedRoundTripAndBackupRestore() throws Exception {
        Path root = temporary.newFolder().toPath();
        AccountStore store = new AccountStore(root);
        Account online = new Account("Example", UUID.randomUUID(), "synthetic-refresh-token");
        Account offline = Account.offline("Local_User");
        store.save(Arrays.asList(online, offline));
        assertEquals(2, store.load().size());
        assertEquals(online.getUuid(), store.load().get(0).getUuid());
        assertEquals("synthetic-refresh-token", store.load().get(0).refreshToken());
        assertFalse(store.load().get(1).isMicrosoft());
        String bytes = new String(Files.readAllBytes(root.resolve("accounts.vault")), StandardCharsets.ISO_8859_1);
        assertFalse(bytes.contains("synthetic-refresh-token"));
        assertFalse(bytes.contains("Example"));
        byte[] first = Files.readAllBytes(root.resolve("accounts.vault"));
        store.save(Arrays.asList(online, offline));
        assertFalse(Arrays.equals(first, Files.readAllBytes(root.resolve("accounts.vault"))));
        Path backup = root.resolve("saved.vibeaccounts");
        store.write(backup, store.load());
        store.save(Collections.emptyList());
        store.save(store.read(backup));
        assertEquals(2, store.load().size());
    }

    @Test public void tamperingIsRejectedWithoutReplacingVault() throws Exception {
        Path root = temporary.newFolder().toPath();
        AccountStore store = new AccountStore(root);
        store.save(Collections.singletonList(Account.offline("Example")));
        Path path = root.resolve("accounts.vault");
        byte[] bytes = Files.readAllBytes(path);
        bytes[bytes.length - 1] ^= 1;
        Files.write(path, bytes);
        assertThrows(IOException.class, store::load);
        assertArrayEquals(bytes, Files.readAllBytes(path));
    }

    @Test public void missingKeyDoesNotGenerateReplacementForExistingVault() throws Exception {
        Path root = temporary.newFolder().toPath();
        AccountStore store = new AccountStore(root);
        store.save(Collections.singletonList(Account.offline("Example")));
        byte[] vault = Files.readAllBytes(root.resolve("accounts.vault"));
        Files.delete(root.resolve("accounts.key"));
        assertThrows(IOException.class, store::load);
        assertThrows(IOException.class, () -> store.save(Collections.emptyList()));
        assertFalse(Files.exists(root.resolve("accounts.key")));
        assertArrayEquals(vault, Files.readAllBytes(root.resolve("accounts.vault")));
    }

    @Test public void wrongInstallationAndPlaintextFilesAreRejected() throws Exception {
        AccountStore first = new AccountStore(temporary.newFolder().toPath());
        AccountStore second = new AccountStore(temporary.newFolder().toPath());
        second.save(Collections.emptyList());
        Path backup = temporary.newFile("backup.vibeaccounts").toPath();
        first.write(backup, Collections.singletonList(Account.offline("Example")));
        assertThrows(IOException.class, () -> second.read(backup));
        Files.write(backup, "# Netscape HTTP Cookie File\nexample.invalid\tFALSE\t/\tTRUE\t0\tfake\tvalue".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> first.read(backup));
    }

    @Test public void offlineIdentityIsDeterministicAndNamesAreValidated() {
        assertEquals(UUID.nameUUIDFromBytes("OfflinePlayer:Test".getBytes(StandardCharsets.UTF_8)), Account.offline("Test").getUuid());
        assertTrue(Account.offline("Test").sameIdentity(Account.offline(" Test ")));
        assertThrows(IllegalArgumentException.class, () -> Account.offline("bad name"));
        assertThrows(IllegalArgumentException.class, () -> Account.offline(""));
        assertThrows(IllegalArgumentException.class, () -> Account.offline("abcdefghijklmnopq"));
        assertFalse(Account.offline("Test").sameIdentity(new Account("Test", Account.offline("Test").getUuid(), "synthetic")));
    }

    @Test public void oversizedBackupIsRejected() throws Exception {
        AccountStore store = new AccountStore(temporary.newFolder().toPath());
        Path backup = temporary.newFile("large.vibeaccounts").toPath();
        Files.write(backup, new byte[2 * 1024 * 1024 + 1]);
        assertThrows(IOException.class, () -> store.read(backup));
    }

    @Test public void storesBothApplicationsAndKeepsProfileDeduplicationIndependentOfLoginMethod() throws Exception {
        AccountStore store = new AccountStore(temporary.newFolder().toPath());
        UUID id = UUID.randomUUID();
        Account browser = new Account("Browser", id, "ias-refresh");
        Account cookies = new Account("Cookies", id, "minecraft-refresh", MicrosoftApplication.MINECRAFT);
        assertTrue(browser.sameIdentity(cookies));
        Account other = new Account("Other", UUID.randomUUID(), "other-refresh", MicrosoftApplication.MINECRAFT);
        store.save(Arrays.asList(browser, other));
        assertEquals(MicrosoftApplication.IAS, store.load().get(0).application());
        assertEquals(MicrosoftApplication.MINECRAFT, store.load().get(1).application());
        Path backup = temporary.newFile("mixed.vibeaccounts").toPath();
        store.write(backup, store.load());
        assertEquals(MicrosoftApplication.MINECRAFT, store.read(backup).get(1).application());
    }

    @Test public void versionOneVaultAndBackupUpgradeWithoutLosingTheIssuingApplication() throws Exception {
        Path root = temporary.newFolder().toPath();
        AccountStore store = new AccountStore(root);
        store.save(Collections.emptyList());
        Path vault = root.resolve("accounts.vault");
        writeFixture(root, vault, "{\"version\":1,\"accounts\":[{\"name\":\"Legacy\","
                + "\"uuid\":\"12345678-1234-1234-1234-123456789abc\",\"refreshToken\":\"legacy-refresh\"}]}");
        Path backup = temporary.newFile("legacy.vibeaccounts").toPath();
        Files.write(backup, Files.readAllBytes(vault));
        Account legacy = store.load().get(0);
        assertEquals(MicrosoftApplication.IAS, legacy.application());
        assertEquals("legacy-refresh", legacy.refreshToken());
        store.save(Arrays.asList(legacy, new Account("Cookie", UUID.randomUUID(), "new-refresh", MicrosoftApplication.MINECRAFT)));
        assertEquals(2, store.load().size());
        assertEquals(MicrosoftApplication.IAS, store.load().get(0).application());
        assertEquals("legacy-refresh", store.read(backup).get(0).refreshToken());
        assertEquals(MicrosoftApplication.IAS, store.read(backup).get(0).application());
    }

    @Test public void unknownOrMissingVersionTwoApplicationIsRejectedWithoutOverwritingVault() throws Exception {
        Path root = temporary.newFolder().toPath();
        AccountStore store = new AccountStore(root);
        store.save(Collections.emptyList());
        Path vault = root.resolve("accounts.vault");
        for (String field : Arrays.asList("", ",\"application\":\"synthetic-private-unknown\"")) {
            writeFixture(root, vault, "{\"version\":2,\"accounts\":[{\"name\":\"Example\","
                    + "\"uuid\":\"12345678-1234-1234-1234-123456789abc\",\"refreshToken\":\"synthetic-refresh\"" + field + "}]}");
            byte[] original = Files.readAllBytes(vault);
            IOException failure = assertThrows(IOException.class, store::load);
            assertFalse(failure.getMessage().contains("synthetic-private"));
            assertNull(failure.getCause());
            assertArrayEquals(original, Files.readAllBytes(vault));
        }
    }

    @Test public void accessTokenAccountsRoundTripAndOnlyThenRequireVersionThree() throws Exception {
        Path root = temporary.newFolder().toPath();
        Path vault = root.resolve("accounts.vault");
        AccountStore store = new AccountStore(root);
        UUID id = UUID.randomUUID();
        String token = MinecraftTokenTest.jwt("{\"exp\":4102444800}");
        Account microsoft = new Account("Example", id, "synthetic-refresh");
        Account pasted = Account.token("Example", id, token);
        assertFalse(pasted.sameIdentity(microsoft));
        assertTrue(pasted.sameIdentity(Account.token("Renamed", id, MinecraftTokenTest.jwt("{}"))));
        store.save(Arrays.asList(microsoft, Account.offline("Local")));
        assertTrue(readFixture(root, vault).contains("\"version\":2"));
        store.save(Arrays.asList(microsoft, pasted, Account.offline("Local")));
        assertTrue(readFixture(root, vault).contains("\"version\":3"));
        assertFalse(new String(Files.readAllBytes(vault), StandardCharsets.ISO_8859_1).contains(token.split("\\.")[1]));
        java.util.List<Account> loaded = store.load();
        assertEquals(3, loaded.size());
        assertEquals("synthetic-refresh", loaded.get(0).refreshToken());
        assertTrue(loaded.get(1).isToken());
        assertFalse(loaded.get(1).isMicrosoft());
        assertEquals(token, loaded.get(1).token().value());
        assertEquals(4102444800000L, loaded.get(1).getTokenExpiry());
        assertFalse(loaded.get(2).isOnline());
        Path backup = temporary.newFile("tokens.vibeaccounts").toPath();
        store.write(backup, loaded);
        assertTrue(store.read(backup).get(1).isToken());
    }

    @Test public void invalidStoredAccessTokenIsRejectedWithoutLeakingOrOverwritingVault() throws Exception {
        Path root = temporary.newFolder().toPath();
        AccountStore store = new AccountStore(root);
        store.save(Collections.emptyList());
        Path vault = root.resolve("accounts.vault");
        writeFixture(root, vault, "{\"version\":3,\"accounts\":[{\"name\":\"Example\","
                + "\"uuid\":\"12345678-1234-1234-1234-123456789abc\",\"accessToken\":\"synthetic-private-token\"}]}");
        byte[] original = Files.readAllBytes(vault);
        IOException failure = assertThrows(IOException.class, store::load);
        assertFalse(failure.getMessage().contains("synthetic-private"));
        assertNull(failure.getCause());
        assertArrayEquals(original, Files.readAllBytes(vault));
    }

    private static String readFixture(Path root, Path source) throws Exception {
        byte[] data = Files.readAllBytes(source);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(Files.readAllBytes(root.resolve("accounts.key")), "AES"),
                new GCMParameterSpec(128, Arrays.copyOfRange(data, 8, 20)));
        cipher.updateAAD("VIBEAC01".getBytes(StandardCharsets.US_ASCII));
        return new String(cipher.doFinal(data, 20, data.length - 20), StandardCharsets.UTF_8);
    }

    private static void writeFixture(Path root, Path target, String json) throws Exception {
        byte[] magic = "VIBEAC01".getBytes(StandardCharsets.US_ASCII);
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(Files.readAllBytes(root.resolve("accounts.key")), "AES"), new GCMParameterSpec(128, iv));
        cipher.updateAAD(magic);
        byte[] encrypted = cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));
        Files.write(target, ByteBuffer.allocate(magic.length + iv.length + encrypted.length).put(magic).put(iv).put(encrypted).array());
    }
}
