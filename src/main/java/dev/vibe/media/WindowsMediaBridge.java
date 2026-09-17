package dev.vibe.media;

import com.google.gson.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

/** Isolated Windows helpers: SMTC for metadata, WASAPI render loopback for audio, no microphone. */
public final class WindowsMediaBridge implements AutoCloseable {
    public static final boolean SUPPORTED=System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win");
    private volatile boolean closed;
    private volatile Process process;
    private final Thread worker;
    public WindowsMediaBridge(boolean audio, String owners, Consumer<MediaTrack> media, AudioSpectrum spectrum, Consumer<String> status) {
        worker=new Thread(() -> run(audio,owners,media,spectrum,status),audio?"Vibe-System-Audio":"Vibe-Media-Session");
        worker.setDaemon(true);worker.start();
    }
    private void run(boolean audio,String owners,Consumer<MediaTrack> media,AudioSpectrum spectrum,Consumer<String> status) {
        if(!SUPPORTED){status.accept("System media and system audio require Windows 10/11");return;}
        Path directory=null;
        try {
            directory=Files.createTempDirectory("vibe-media-");
            copy("media-session.ps1",directory);copy("audio-loopback.ps1",directory);copy("AudioLoopback.cs",directory);
            List<String> command=new ArrayList<String>(Arrays.asList("powershell.exe","-NoLogo","-NoProfile","-NonInteractive","-WindowStyle","Hidden",
                    "-ExecutionPolicy","Bypass","-File",directory.resolve(audio?"audio-loopback.ps1":"media-session.ps1").toString(),
                    "-ParentId",java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0]));
            if(!audio){command.add("-OwnerPriority");command.add(owners);}
            if(closed)return;
            process=new ProcessBuilder(command).redirectErrorStream(true).start();
            if(closed){process.destroyForcibly();return;}
            if(audio)status.accept("Waiting for system audio");
            String lastArtwork="";BufferedImage artwork=null;
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))) {
                String line;
                while(!closed&&(line=reader.readLine())!=null) {
                    if(line.length()>12_000_000)continue;
                    if(audio && line.startsWith("PCM:")) {
                        int separator=line.indexOf(':',4);if(separator<0)continue;
                        int rate=Integer.parseInt(line.substring(4,separator));
                        byte[] bytes=Base64.getDecoder().decode(line.substring(separator+1));
                        FloatBuffer floats=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
                        float[] samples=new float[floats.remaining()];floats.get(samples);
                        spectrum.accept(samples,samples.length,rate);status.accept("System audio");
                    } else if(line.startsWith("{")) {
                        JsonObject json=new JsonParser().parse(line).getAsJsonObject();
                        if(json.has("error")){status.accept(json.get("error").getAsString());continue;}
                        if(audio)continue;
                        String encoded=string(json,"artwork");
                        if(!encoded.equals(lastArtwork)) {
                            lastArtwork=encoded;artwork=null;
                            if(!encoded.isEmpty())try{artwork=decodeArtwork(Base64.getDecoder().decode(encoded));}catch(Exception ignored){}
                        }
                        media.accept(new MediaTrack(string(json,"title"),string(json,"artist"),string(json,"owner"),string(json,"status"),
                                json.has("playing")&&json.get("playing").getAsBoolean(),false,number(json,"position"),number(json,"duration"),artwork));
                    }
                }
            }
            if(!closed)status.accept("Windows media helper stopped; toggle Music to retry");
        } catch(Exception e){if(!closed)status.accept("Windows media unavailable: "+e.getClass().getSimpleName());}
        finally {
            Process current=process;if(current!=null&&current.isAlive())current.destroyForcibly();
            if(directory!=null)for(String name:new String[]{"media-session.ps1","audio-loopback.ps1","AudioLoopback.cs",""})
                try{Files.deleteIfExists(name.isEmpty()?directory:directory.resolve(name));}catch(IOException ignored){}
        }
    }
    private static BufferedImage decodeArtwork(byte[] bytes) throws IOException {
        if(bytes.length>8_000_000)return null;
        try(javax.imageio.stream.ImageInputStream stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<javax.imageio.ImageReader> readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())return null;
            javax.imageio.ImageReader reader=readers.next();
            try {
                reader.setInput(stream);int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<=0||h<=0||w>8192||h>8192||(long)w*h>20_000_000)return null;
                javax.imageio.ImageReadParam param=reader.getDefaultReadParam();int sample=Math.max(1,Math.max(w,h)/512);
                param.setSourceSubsampling(sample,sample,0,0);return reader.read(0,param);
            } finally{reader.dispose();}
        }
    }
    private static void copy(String name,Path directory)throws IOException {
        try(InputStream stream=WindowsMediaBridge.class.getResourceAsStream("/assets/vibe/media/"+name)) {
            if(stream==null)throw new FileNotFoundException(name);Files.copy(stream,directory.resolve(name));
        }
    }
    private static String string(JsonObject j,String key){return j.has(key)&&!j.get(key).isJsonNull()?j.get(key).getAsString():"";}
    private static long number(JsonObject j,String key){try{return j.get(key).getAsLong();}catch(Exception e){return 0;}}
    @Override public void close(){closed=true;Process current=process;if(current!=null)current.destroyForcibly();worker.interrupt();}
}
