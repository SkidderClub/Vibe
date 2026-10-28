package dev.vibe.chatter;

import com.google.gson.Gson;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** AES-GCM and owner-only atomic files, following the existing account-vault storage policy. */
public final class ChatterStore {
    private static final byte[] MAGIC="VIBECH01".getBytes(StandardCharsets.US_ASCII);
    private final Path directory,file,keyFile;
    private final SecureRandom random=new SecureRandom();
    private final Gson gson=new Gson();
    public ChatterStore(Path directory){this.directory=directory;file=directory.resolve("chatter.vault");keyFile=directory.resolve("chatter.key");}
    public ChatterData load() throws IOException {
        if(!Files.exists(file))return ChatterData.defaults();
        byte[] plain=null;
        try {
            byte[] data=read(file,2*1024*1024);
            if(data.length<36||!Arrays.equals(MAGIC,Arrays.copyOf(data,8)))throw new IOException("Cannot read Chatter data.");
            plain=cipher(Cipher.DECRYPT_MODE,key(false),Arrays.copyOfRange(data,8,20)).doFinal(data,20,data.length-20);
            ChatterData result=gson.fromJson(new String(plain,StandardCharsets.UTF_8),ChatterData.class);result.validate();return result;
        } catch(GeneralSecurityException|RuntimeException error) { throw new IOException("Cannot read Chatter data. Restore the vault and its matching key."); }
        finally {if(plain!=null)Arrays.fill(plain,(byte)0);}
    }
    public void save(ChatterData data) throws IOException {
        data.validate();byte[] plain=gson.toJson(data).getBytes(StandardCharsets.UTF_8);
        try {
            if(plain.length>2*1024*1024)throw new IOException("Chatter data is too large.");
            byte[] iv=new byte[12];random.nextBytes(iv);
            byte[] encrypted=cipher(Cipher.ENCRYPT_MODE,key(true),iv).doFinal(plain);
            atomic(file,ByteBuffer.allocate(20+encrypted.length).put(MAGIC).put(iv).put(encrypted).array());
        } catch(GeneralSecurityException error){throw new IOException("Chatter encryption is unavailable.");}
        finally {Arrays.fill(plain,(byte)0);}
    }
    private byte[] key(boolean create) throws IOException {
        Files.createDirectories(directory);
        if(!Files.exists(keyFile)) {
            if(!create||Files.exists(file))throw new IOException("The Chatter key is missing. Restore it before editing passwords.");
            byte[] key=new byte[16];random.nextBytes(key);Path temporary=Files.createTempFile(directory,".chatter-key-",".tmp");
            try {restrict(temporary);Files.write(temporary,key);Files.move(temporary,keyFile);}
            finally {Arrays.fill(key,(byte)0);Files.deleteIfExists(temporary);}
        }
        byte[] key=read(keyFile,16);if(key.length!=16)throw new IOException("The Chatter key is invalid.");restrict(keyFile);return key;
    }
    private static Cipher cipher(int mode,byte[] key,byte[] iv) throws GeneralSecurityException {
        try {Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));cipher.updateAAD(MAGIC);return cipher;}
        finally {Arrays.fill(key,(byte)0);}
    }
    private static byte[] read(Path path,int maximum) throws IOException {
        try(InputStream input=Files.newInputStream(path);ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[4096];int size;while((size=input.read(buffer))!=-1){if(output.size()+size>maximum)throw new IOException("Chatter file is too large.");output.write(buffer,0,size);}return output.toByteArray();
        }
    }
    private static void atomic(Path path,byte[] bytes) throws IOException {
        Path temporary=Files.createTempFile(path.getParent(),".chatter-",".tmp");
        try {restrict(temporary);Files.write(temporary,bytes);
            try {Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ignored){Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temporary);}
    }
    private static void restrict(Path path) throws IOException {
        PosixFileAttributeView posix=Files.getFileAttributeView(path,PosixFileAttributeView.class);
        if(posix!=null){posix.setPermissions(PosixFilePermissions.fromString("rw-------"));return;}
        AclFileAttributeView acl=Files.getFileAttributeView(path,AclFileAttributeView.class);
        if(acl!=null)acl.setAcl(Collections.singletonList(AclEntry.newBuilder().setType(AclEntryType.ALLOW)
                .setPrincipal(Files.getOwner(path)).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
    }
}
