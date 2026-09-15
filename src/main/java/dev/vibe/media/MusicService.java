package dev.vibe.media;

import dev.vibe.module.impl.MusicModule;

/** Owns one current radio and one helper per OS data source. Configuration changes cancel old work. */
public final class MusicService implements AutoCloseable {
    private volatile MediaTrack systemTrack=MediaTrack.idle("Waiting for media");
    private volatile String audioStatus="", mediaStatus="";
    private final AudioSpectrum systemSpectrum=new AudioSpectrum();
    private WindowsMediaBridge media, audio;
    private RadioPlayer radio;
    private String owners="", station="";
    private boolean useSystemAudio;
    private volatile boolean closed;
    private volatile long mediaGeneration, audioGeneration;
    private final Thread shutdown=new Thread(this::close,"Vibe-Music-Shutdown");
    public MusicService(){Runtime.getRuntime().addShutdownHook(shutdown);}
    public void update(MusicModule module) {
        if(closed)return;
        boolean needMedia=module.systemMedia.isEnabled()&&!module.radio.isEnabled();
        if(media!=null&&(!needMedia||!owners.equals(module.owners.getValue()))) {mediaGeneration++;media.close();media=null;systemTrack=MediaTrack.idle("Waiting for media");}
        if(needMedia&&media==null) {
            owners=module.owners.getValue();final long generation=++mediaGeneration;
            media=new WindowsMediaBridge(false,owners,t->{if(!closed&&generation==mediaGeneration){systemTrack=t;mediaStatus="";}},systemSpectrum,
                    s->{if(!closed&&generation==mediaGeneration){mediaStatus=s;systemTrack=MediaTrack.idle(s);}});
        }
        String selected=module.stationUrl()+"\n"+module.station.getValue();
        if(radio!=null&&(!module.radio.isEnabled()||!station.equals(selected))){radio.close();radio=null;}
        if(module.radio.isEnabled()&&radio==null){station=selected;radio=new RadioPlayer(module.stationUrl(),module.station.getValue(),module.volume.getFloat()/100,module.reconnect.isEnabled());}
        if(radio!=null)radio.settings(module.volume.getFloat()/100,module.reconnect.isEnabled());
        useSystemAudio=module.audioSource.is("System")||(module.audioSource.is("Auto")&&radio==null);
        boolean needAudio=module.visualizer.isEnabled()&&useSystemAudio;
        if(audio!=null&&!needAudio){audioGeneration++;audio.close();audio=null;systemSpectrum.clear();audioStatus="";}
        if(needAudio&&audio==null) {
            final long generation=++audioGeneration;
            audio=new WindowsMediaBridge(true,"",t->{},systemSpectrum,s->{if(!closed&&generation==audioGeneration)audioStatus=s;});
        }
        if(!needMedia&&radio==null)systemTrack=MediaTrack.idle("Enable System Media or Radio");
    }
    public MediaTrack track(){return radio!=null?radio.track():systemTrack;}
    public AudioSpectrum.Frame spectrum(){return useSystemAudio?systemSpectrum.frame():radio==null?AudioSpectrum.Frame.SILENT:radio.spectrum.frame();}
    public String audioStatus(){return useSystemAudio?audioStatus:radio==null?"Enable Radio or choose System audio":"Radio audio";}
    @Override public synchronized void close() {
        if(closed)return;closed=true;mediaGeneration++;audioGeneration++;
        if(media!=null)media.close();if(audio!=null)audio.close();if(radio!=null)radio.close();systemSpectrum.clear();
        if(Thread.currentThread()!=shutdown)try{Runtime.getRuntime().removeShutdownHook(shutdown);}catch(IllegalStateException ignored){}
    }
}
