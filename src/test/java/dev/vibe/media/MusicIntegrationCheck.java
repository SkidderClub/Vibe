package dev.vibe.media;

/** Opt-in real MP3 decoder/output check. Output is muted; does not alter system volume. */
public final class MusicIntegrationCheck {
    public static void main(String[] args) throws Exception {
        RadioPlayer radio=new RadioPlayer("https://somafm.com/groovesalad.pls","Groove Salad",0,false);
        try {
            long deadline=System.nanoTime()+20_000_000_000L;
            while(!radio.track().playing&&System.nanoTime()<deadline&&!radio.isStopped())Thread.sleep(50);
            if(!radio.track().playing)throw new AssertionError("Radio did not decode/play: "+radio.track().status);
            System.out.println("Radio decoded MP3 into a muted output line; live metadata state available.");
        } finally {radio.close();}
        long deadline=System.nanoTime()+3_000_000_000L;
        while(!radio.isStopped()&&System.nanoTime()<deadline)Thread.sleep(20);
        if(!radio.isStopped())throw new AssertionError("Radio worker failed to stop");
        System.out.println("Radio cancellation stopped network/decode worker.");
    }
}
