package dev.vibe.media;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.sound.sampled.*;
import javazoom.jl.decoder.*;

/** Bounded network work and MP3 decoding live entirely on a cancellable daemon thread. */
public final class RadioPlayer implements AutoCloseable {
    public final AudioSpectrum spectrum = new AudioSpectrum();
    private final String url, name;
    private final Thread thread;
    private volatile boolean cancelled, reconnect;
    private volatile float volume;
    private volatile HttpURLConnection connection;
    private volatile SourceDataLine line;
    private volatile MediaTrack track;
    private volatile String streamTitle = "";
    private volatile boolean outputStarted;
    public RadioPlayer(String url, String name, float volume, boolean reconnect) {
        this.url=url; this.name=name; this.volume=volume; this.reconnect=reconnect;
        track=MediaTrack.idle("Connecting to " + name);
        thread=new Thread(this::run,"Vibe-Radio"); thread.setDaemon(true); thread.start();
    }
    public void settings(float volume, boolean reconnect) { this.volume=volume; this.reconnect=reconnect; }
    public MediaTrack track() { return track; }
    private void run() {
        int attempts=0;
        do {
            try { play(); if (!cancelled) throw new EOFException("Station ended the stream"); }
            catch (Exception e) { if(!cancelled)track=new MediaTrack(name,"","Radio","Radio: "+message(e),false,true,0,0,null); }
            finally { outputStarted=false; release(); spectrum.clear(); }
            if(cancelled||!reconnect)break;
            try { Thread.sleep(Math.min(30000, 2000L << Math.min(attempts++,4))); }
            catch(InterruptedException e){break;}
        } while(!cancelled);
    }
    private static String message(Exception e) { return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage(); }
    private HttpURLConnection open(URL target) throws IOException {
        for(int redirects=0;redirects<6;redirects++) {
            String scheme=target.getProtocol();
            if(!"http".equals(scheme)&&!"https".equals(scheme))throw new IOException("Use an HTTP(S) MP3 stream or playlist");
            if(cancelled)throw new InterruptedIOException();
            HttpURLConnection next=(HttpURLConnection)target.openConnection(); connection=next;
            next.setConnectTimeout(6000);next.setReadTimeout(8000);next.setInstanceFollowRedirects(false);
            next.setRequestProperty("User-Agent","Vibe-Music/1.0");next.setRequestProperty("Icy-MetaData","1");
            int status=next.getResponseCode();
            if(cancelled){next.disconnect();throw new InterruptedIOException();}
            if(status>=300&&status<400) {
                String location=next.getHeaderField("Location");next.disconnect();
                if(location==null)throw new IOException("Station redirect has no destination");target=new URL(target,location);continue;
            }
            if(status!=200){next.disconnect();throw new IOException("Station returned HTTP "+status);}
            return next;
        }
        throw new IOException("Too many station redirects");
    }
    private void play() throws Exception {
        if(url.isEmpty())throw new IOException("Enter a radio URL");
        URL target=new URL(url); HttpURLConnection stream=null;
        for(int playlists=0;playlists<4;playlists++) {
            stream=open(target);
            String type=String.valueOf(stream.getContentType()).toLowerCase(Locale.ROOT);
            String path=target.getPath().toLowerCase(Locale.ROOT);
            if(path.endsWith(".m3u8"))throw new IOException("HLS is unsupported; choose an MP3 stream");
            if(!(path.endsWith(".pls")||path.endsWith(".m3u")||type.contains("scpls")||type.contains("mpegurl")))break;
            StringBuilder playlist=new StringBuilder();
            try(Reader reader=new InputStreamReader(stream.getInputStream(),StandardCharsets.UTF_8)) {
                char[] buffer=new char[2048]; int n;
                while((n=reader.read(buffer))>=0){playlist.append(buffer,0,n);if(playlist.length()>65536)throw new IOException("Playlist too large");}
            } finally { stream.disconnect(); }
            target=playlistUrl(playlist.toString(),target);stream=null;
        }
        if(stream==null)throw new IOException("Too many nested playlists");
        String type=String.valueOf(stream.getContentType()).toLowerCase(Locale.ROOT);
        if(type.contains("aac")||type.contains("html"))throw new IOException("Choose an MP3 station (AAC/HLS/web pages are unsupported)");
        int meta=0; try{meta=Integer.parseInt(stream.getHeaderField("icy-metaint"));}catch(Exception ignored){}
        meta=Math.max(0,meta);
        InputStream input=new IcyInputStream(new BufferedInputStream(stream.getInputStream()),meta,this::titleChanged);
        Bitstream bitstream=new Bitstream(input); Decoder decoder=new Decoder();
        try {
            Header header; byte[] pcm=null; float[] mono=null;
            while(!cancelled&&(header=bitstream.readFrame())!=null) {
                SampleBuffer decoded=(SampleBuffer)decoder.decodeFrame(header,bitstream);
                int channels=decoded.getChannelCount(),rate=decoded.getSampleFrequency(),count=decoded.getBufferLength();
                if(line==null||line.getFormat().getSampleRate()!=rate||line.getFormat().getChannels()!=channels) {
                    if(line!=null){line.stop();line.close();}
                    AudioFormat format=new AudioFormat(rate,16,channels,true,false);
                    SourceDataLine next=AudioSystem.getSourceDataLine(format); line=next;
                    next.open(format, Math.max(4096,rate*channels/5));
                    if(cancelled)break;next.start();
                }
                if(pcm==null||pcm.length<count*2)pcm=new byte[count*2];
                if(mono==null||mono.length<count/channels)mono=new float[count/channels];
                short[] samples=decoded.getBuffer();float gain=volume;
                for(int i=0;i<count;i++) {
                    short value=(short)(samples[i]*gain);pcm[i*2]=(byte)value;pcm[i*2+1]=(byte)(value>>8);
                }
                for(int i=0;i<count/channels;i++) {float sum=0;for(int c=0;c<channels;c++)sum+=samples[i*channels+c]/32768f*gain;mono[i]=sum/channels;}
                SourceDataLine output=line;
                if(!cancelled&&output!=null) {
                    int offset=0;while(offset<count*2&&!cancelled){int n=output.write(pcm,offset,count*2-offset);if(n<=0)break;offset+=n;}
                    if(offset>0&&!cancelled) {
                        outputStarted=true;
                        spectrum.accept(mono,count/channels,rate);
                        if(!track.playing)titleChanged(streamTitle);
                    }
                }
                bitstream.closeFrame();
            }
        } finally { bitstream.close(); input.close(); }
    }
    private void titleChanged(String title) {
        streamTitle=title;
        String artist=name, song=title.isEmpty()?name:title;
        int separator=title.indexOf(" - ");if(separator>0){artist=title.substring(0,separator);song=title.substring(separator+3);}
        track=new MediaTrack(song,artist,"Radio / "+name,outputStarted?"Live":"Buffering",outputStarted,true,0,0,null);
    }
    public static URL playlistUrl(String playlist,URL base) throws IOException {
        for(String value:playlist.split("\\r?\\n")) {
            value=value.trim();
            if(value.startsWith("#EXT-X-"))throw new IOException("HLS playlists are unsupported");
            if(value.matches("(?i)File[0-9]+=.*"))value=value.substring(value.indexOf('=')+1).trim();
            else if(value.isEmpty()||value.startsWith("#")||value.startsWith("[")||value.matches("(?i)^(Title[0-9]+|Length[0-9]+|NumberOfEntries|Version)\\s*=.*"))continue;
            URL candidate=new URL(base,value);
            if(candidate.getProtocol().equals("http")||candidate.getProtocol().equals("https"))return candidate;
        }
        throw new IOException("No playable station in playlist");
    }
    private void release() {
        SourceDataLine output=line;line=null;
        if(output!=null){try{output.stop();output.flush();output.close();}catch(Exception ignored){}}
        HttpURLConnection current=connection;connection=null;if(current!=null)current.disconnect();
    }
    @Override public void close() {
        if(cancelled)return;
        cancelled=true;thread.interrupt();spectrum.clear();
        // A driver/socket may block during close. Never put that work on the game thread.
        Thread closer=new Thread(this::release,"Vibe-Radio-Close");closer.setDaemon(true);closer.start();
    }
    public boolean isStopped() { return !thread.isAlive(); }
}
