package dev.vibe.miniblox;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/** Opt-in online check: the real installer, upstream process, MC 1.8.9 status handshake and reuse. */
public final class MinibloxRuntimeSmoke {
    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args[0]);
        try (MinibloxRuntime runtime = new MinibloxRuntime(root)) {
            MinibloxRuntime.Attempt attempt = runtime.start();
            long end = System.nanoTime() + TimeUnit.MINUTES.toNanos(12); String previous = "";
            while (attempt.port == 0 && attempt.error == null && System.nanoTime() < end) {
                if (!previous.equals(attempt.message)) { System.out.println(attempt.message); previous = attempt.message; }
                Thread.sleep(250);
            }
            if (attempt.error != null) throw new IOException(attempt.error);
            if (attempt.port <= 0) throw new IOException("Installer timeout");
            try (Socket socket = new Socket("127.0.0.1", attempt.port)) {
                socket.setSoTimeout(10000);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream handshake = new DataOutputStream(bytes);
                varInt(handshake,0); varInt(handshake,47); byte[] host = "miniblox.io".getBytes(StandardCharsets.UTF_8);
                varInt(handshake,host.length); handshake.write(host); handshake.writeShort(attempt.port); varInt(handshake,1);
                DataOutputStream output = new DataOutputStream(socket.getOutputStream()); varInt(output,bytes.size()); bytes.writeTo(output);
                output.write(new byte[]{1,0}); output.flush();
                DataInputStream input = new DataInputStream(socket.getInputStream()); readVarInt(input);
                if(readVarInt(input)!=0)throw new IOException("Unexpected status packet");
                int length = readVarInt(input); if(length<1||length>32767)throw new IOException("Bad status length");
                byte[] json = new byte[length]; input.readFully(json); String status = new String(json,StandardCharsets.UTF_8);
                if(!status.contains("Miniblox")||!status.contains("47"))throw new IOException("Not a Miniblox 1.8.9 bridge: "+status);
                System.out.println("Minecraft 1.8.9 status handshake passed on private port " + attempt.port);
            }
            MinibloxRuntime.Attempt reuse = runtime.start();
            if(reuse.port!=attempt.port || !runtime.owns(attempt.port))throw new IOException("Bridge was not reused");
            Files.write(root.resolve("smoke-port.txt"),Integer.toString(attempt.port).getBytes(StandardCharsets.UTF_8));
            System.out.println("Cached installation and process reuse passed; stopping bridge.");
        }
    }
    private static void varInt(DataOutput output,int value)throws IOException {
        do { int next=value&127;value>>>=7;output.writeByte(value==0?next:next|128); }while(value!=0);
    }
    private static int readVarInt(DataInput input)throws IOException {
        int value=0;for(int i=0;i<5;i++){int next=input.readUnsignedByte();value|=(next&127)<<(i*7);if((next&128)==0)return value;}
        throw new IOException("Invalid VarInt");
    }
}
