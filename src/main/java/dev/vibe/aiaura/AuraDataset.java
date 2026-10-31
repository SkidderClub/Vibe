package dev.vibe.aiaura;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Manual demonstrations only. Binary, versioned and bounded; the temporal split precedes window construction. */
public final class AuraDataset {
    public static final int LIMIT=60000,VERSION=1;
    public static final class Frame {
        public long session,time,target;
        public float[] x=new float[AuraFeatures.WIDTH],y=new float[AuraFeatures.OUTPUTS],mask=new float[AuraFeatures.OUTPUTS];
        public int keys,wheel,slot,pressCount;public boolean press;public float sensitivity;
        public Frame copy(){Frame f=new Frame();f.session=session;f.time=time;f.target=target;f.x=x.clone();f.y=y.clone();f.mask=mask.clone();f.keys=keys;f.wheel=wheel;f.slot=slot;f.pressCount=pressCount;f.press=press;f.sensitivity=sensitivity;return f;}
    }
    public final List<Frame> frames=new ArrayList<>();
    public void add(Frame frame){if(frames.size()>=LIMIT)frames.subList(0,1000).clear();frames.add(frame);}
    public AuraDataset copy(){AuraDataset result=new AuraDataset();for(Frame frame:frames)result.frames.add(frame.copy());return result;}
    public int clicks(){int n=0;for(Frame f:frames)n+=Math.max(f.press?1:0,f.pressCount);return n;}
    public int reactions(){int n=0;for(Frame f:frames)if(f.mask[4]>0)n++;return n;}
    public int labels(int head){int n=0;for(Frame f:frames)if(f.mask[head]>0)n++;return n;}
    public int[] coverage(){int[] count=new int[3];for(Frame f:frames)if(f.x[19]>0){count[f.x[24]>0?0:f.x[25]>0?1:2]++;}return count;}
    public boolean ready(){return requirement().isEmpty();}
    public String requirement(){int[] c=coverage();if(frames.size()<600)return "Record "+(600-frames.size())+" more frames (30 seconds minimum)";if(clicks()<15)return "Record "+(15-clicks())+" more physical clicks";if(reactions()<3||labels(5)<3)return "Record at least 3 aim reactions and 3 first-click reactions to visible targets";if(labels(3)<10||labels(9)<10)return "Record 10 repeated click intervals and 10 button releases";if(labels(6)<20)return "Track a target with the crosshair for 20 frames";if(c[0]<20||c[1]<20||c[2]<20)return "Record 20 frames near reach, at its edge and outside reach";
        int trainingEnd=Math.max(1,validationStart()-HISTORY_GAP);for(int head:new int[]{3,4,5,6,9}){boolean found=false;for(int i=0;i<trainingEnd;i++)if(frames.get(i).mask[head]>0){found=true;break;}if(!found)return "Record another varied session to include timing and aim labels in the training portion";}return "";}
    private static final int HISTORY_GAP=AuraFeatures.HISTORY+4;
    public void prepareLabels(){
        // Burst intent covers the pauses between physical down/up edges; it is
        // not a claim that a held button creates repeat attack packets.
        for(int i=0;i<frames.size();i++){Frame f=frames.get(i);boolean nearby=false;for(int k=Math.max(0,i-4);k<=Math.min(frames.size()-1,i+4);k++){Frame n=frames.get(k);if(n.session==f.session&&Math.abs(n.time-f.time)<=200&&n.press){nearby=true;break;}}f.y[2]=nearby?1:0;f.mask[2]=1;}
    }
    public float[] window(int index){float[] result=new float[AuraFeatures.INPUTS];Frame current=frames.get(index);for(int h=0;h<AuraFeatures.HISTORY&&index-h>=0;h++){Frame f=frames.get(index-h);if(f.session!=current.session||current.time-f.time>h*80L+20)break;System.arraycopy(f.x,0,result,h*AuraFeatures.WIDTH,AuraFeatures.WIDTH);}return result;}
    public int validationStart(){
        int desired=Math.max(1,(int)(frames.size()*.8));long session=frames.get(Math.min(desired,frames.size()-1)).session;
        int first=desired;while(first>0&&frames.get(first-1).session==session)first--;
        return first>=frames.size()/2?first:desired;
    }
    public void write(Path path)throws IOException{
        Files.createDirectories(path.getParent());Path temp=path.resolveSibling(path.getFileName()+".tmp");
        try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(temp))))){
            out.writeInt(0x56414941);out.writeInt(VERSION);out.writeInt(AuraFeatures.WIDTH);out.writeInt(AuraFeatures.OUTPUTS);out.writeInt(frames.size());
            for(Frame f:frames){out.writeLong(f.session);out.writeLong(f.time);out.writeLong(f.target);out.writeInt(f.keys);out.writeInt(f.wheel);out.writeInt(f.slot);out.writeInt(f.pressCount);out.writeBoolean(f.press);out.writeFloat(f.sensitivity);for(float[] values:new float[][]{f.x,f.y,f.mask})for(float v:values)out.writeFloat(v);}
        }move(temp,path);
    }
    public static AuraDataset read(Path path)throws IOException{
        AuraDataset data=new AuraDataset();if(!Files.isRegularFile(path))return data;
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path))))){
            if(in.readInt()!=0x56414941||in.readInt()!=VERSION||in.readInt()!=AuraFeatures.WIDTH||in.readInt()!=AuraFeatures.OUTPUTS)throw new IOException("Unsupported AIAura recording format");int count=in.readInt();if(count<0||count>LIMIT)throw new IOException("Invalid recording size");
            for(int i=0;i<count;i++){Frame f=new Frame();f.session=in.readLong();f.time=in.readLong();f.target=in.readLong();f.keys=in.readInt();f.wheel=in.readInt();f.slot=in.readInt();f.pressCount=in.readInt();f.press=in.readBoolean();f.sensitivity=in.readFloat();if(!Float.isFinite(f.sensitivity)||f.sensitivity<0||f.sensitivity>1||f.pressCount<0||f.pressCount>100)throw new IOException("Invalid recording metadata");for(float[] values:new float[][]{f.x,f.y,f.mask})for(int k=0;k<values.length;k++){values[k]=in.readFloat();if(!Float.isFinite(values[k]))throw new IOException("Non-finite recording value");}for(float mask:f.mask)if(mask<0||mask>1)throw new IOException("Invalid label mask");data.frames.add(f);}
        }return data;
    }
    static void move(Path from,Path to)throws IOException{try{Files.move(from,to,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(from,to,StandardCopyOption.REPLACE_EXISTING);}}
}
