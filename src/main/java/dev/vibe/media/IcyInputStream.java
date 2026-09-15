package dev.vibe.media;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.regex.*;

/** Removes ICY metadata before MP3 decoding, including metadata split across network reads. */
public final class IcyInputStream extends FilterInputStream {
    private final int interval;
    private int remaining;
    private final Consumer<String> title;
    private static final Pattern TITLE = Pattern.compile("StreamTitle='(.*?)';", Pattern.DOTALL);
    public IcyInputStream(InputStream input, int interval, Consumer<String> title) {
        super(input); this.interval=Math.max(0,interval); remaining=this.interval; this.title=title;
    }
    private boolean metadata() throws IOException {
        if (interval==0 || remaining>0) return true;
        int size=in.read(); if(size<0)return false;
        byte[] data=new byte[size*16]; int offset=0;
        while(offset<data.length) { int n=in.read(data,offset,data.length-offset); if(n<0)throw new EOFException("Truncated ICY metadata"); if(n>0)offset+=n; }
        if(data.length>0) {
            String text=new String(data,StandardCharsets.UTF_8);
            if(text.indexOf('\uFFFD')>=0)text=new String(data,StandardCharsets.ISO_8859_1);
            Matcher match=TITLE.matcher(text); if(match.find())title.accept(match.group(1));
        }
        remaining=interval; return true;
    }
    @Override public int read() throws IOException { if(!metadata())return -1; int value=in.read(); if(value>=0&&interval>0)remaining--; return value; }
    @Override public int read(byte[] bytes,int off,int len) throws IOException {
        if(len==0)return 0; if(!metadata())return -1;
        int n=in.read(bytes,off,interval>0?Math.min(len,remaining):len); if(n>0&&interval>0)remaining-=n; return n;
    }
    @Override public long skip(long amount) throws IOException {
        byte[] buffer=new byte[1024]; long skipped=0;
        while(skipped<amount){int n=read(buffer,0,(int)Math.min(buffer.length,amount-skipped));if(n<0)break;skipped+=n;}return skipped;
    }
}
