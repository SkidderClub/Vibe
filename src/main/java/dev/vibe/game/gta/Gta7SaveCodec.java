package dev.vibe.game.gta;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Authenticated local saves. Protects against file edits, not a modified game executable. */
final class Gta7SaveCodec {
    private static final byte[] HEADER = "GTA7SAVE2".getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RANDOM = new SecureRandom();
    private Gta7SaveCodec() { }

    static Path keyFile(Path save) { return save.toAbsolutePath().resolveSibling(".gta7-save.key"); }
    static boolean encrypted(byte[] bytes) {
        return bytes.length >= HEADER.length && Arrays.equals(HEADER, Arrays.copyOf(bytes, HEADER.length));
    }
    private static byte[] key(Path save, boolean create) throws IOException {
        Path path = keyFile(save);
        if (!Files.exists(path) && create) {
            Path backup=save.resolveSibling(save.getFileName()+".bak");
            if(Files.exists(save)&&encrypted(Files.readAllBytes(save))||Files.exists(backup)&&encrypted(Files.readAllBytes(backup)))
                throw new IOException("Existing encrypted profile requires its original .gta7-save.key");
            byte[] generated = new byte[16]; RANDOM.nextBytes(generated);
            Files.write(path, generated, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
        byte[] key = Files.readAllBytes(path);
        if (key.length != 16) throw new IOException("Invalid GTA7 save key");
        return key;
    }
    static byte[] encrypt(Path save, byte[] plain) throws IOException {
        try {
            byte[] iv = new byte[12]; RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(save, true), "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(HEADER);
            byte[] payload = cipher.doFinal(plain);
            return ByteBuffer.allocate(HEADER.length + iv.length + payload.length).put(HEADER).put(iv).put(payload).array();
        } catch (GeneralSecurityException failure) { throw new IOException("Could not encrypt progress", failure); }
    }
    static byte[] decrypt(Path save, byte[] bytes) throws IOException {
        if (!encrypted(bytes) || bytes.length < HEADER.length + 12 + 16) throw new IOException("Invalid encrypted save");
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(save, false), "AES"),
                    new GCMParameterSpec(128, Arrays.copyOfRange(bytes, HEADER.length, HEADER.length + 12)));
            cipher.updateAAD(HEADER);
            return cipher.doFinal(bytes, HEADER.length + 12, bytes.length - HEADER.length - 12);
        } catch (GeneralSecurityException failure) { throw new IOException("Save authentication failed", failure); }
    }
}
