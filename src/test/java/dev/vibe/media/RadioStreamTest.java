package dev.vibe.media;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RadioStreamTest {
    @Test public void icyMetadataIsRemovedAcrossSmallReads() throws Exception {
        ByteArrayOutputStream source=new ByteArrayOutputStream();source.write(new byte[]{1,2,3,4});
        byte[] metadata="StreamTitle='Artist - Song';".getBytes(StandardCharsets.UTF_8);source.write(2);source.write(metadata);
        source.write(new byte[32-metadata.length]);source.write(new byte[]{5,6,7,8});source.write(0);source.write(new byte[]{9,10});
        List<String> titles=new ArrayList<String>();IcyInputStream stream=new IcyInputStream(new ByteArrayInputStream(source.toByteArray()),4,titles::add);
        ByteArrayOutputStream audio=new ByteArrayOutputStream();byte[] chunk=new byte[3];int n;
        while((n=stream.read(chunk))!=-1)audio.write(chunk,0,n);
        assertArrayEquals(new byte[]{1,2,3,4,5,6,7,8,9,10},audio.toByteArray());assertEquals(Arrays.asList("Artist - Song"),titles);
    }
    @Test(expected=EOFException.class) public void truncatedMetadataFailsClearly() throws Exception {
        IcyInputStream stream=new IcyInputStream(new ByteArrayInputStream(new byte[]{3,2,0}),1,s->{});
        assertEquals(3,stream.read());stream.read();
    }
    @Test public void parsesPlsAndRelativeM3u() throws Exception {
        URL base=new URL("https://radio.example/stations/list.pls");
        assertEquals("https://stream.example/live",RadioPlayer.playlistUrl("[playlist]\nFile1=https://stream.example/live\nTitle1=Station",base).toString());
        assertEquals("https://radio.example/stream.mp3",RadioPlayer.playlistUrl("#EXTM3U\n#EXTINF:-1,Radio\n../stream.mp3",base).toString());
        assertEquals("https://stream.example/live?token=123",RadioPlayer.playlistUrl("#EXTM3U\nhttps://stream.example/live?token=123",base).toString());
    }
    @Test(expected=IOException.class) public void rejectsHlsWithHelpfulError() throws Exception {
        RadioPlayer.playlistUrl("#EXTM3U\n#EXT-X-VERSION:3\nchunk.ts",new URL("https://radio.example/live"));
    }
    @Test public void zeroDurationAndLiveTracksHaveSafeProgress() {
        assertEquals(0,new MediaTrack("Live","","Radio","",true,true,5,0,null).position());
        assertEquals(400,new MediaTrack("Paused","","","",false,false,400,1000,null).position());
    }
}
