package dev.vibe.aiaura;

import java.util.HashSet;
import java.util.Set;

/** Derived manual-recording statistics; no generated or preview actions are counted. */
public final class AuraStatistics {
    public final int frames,clicks,sessions,aimpoints,visibleFrames,heightSamples;
    public final double seconds,cps,aimReactionMs,clickReactionMs,angularError,turnSpeed;
    private AuraStatistics(AuraDataset data){
        frames=data.frames.size();clicks=data.clicks();seconds=frames/20D;Set<Long> ids=new HashSet<>();int aim=0,seen=0,height=0,rotationN=0,clickN=0,intervalN=0;double rotation=0,click=0,interval=0,error=0,turn=0;
        for(AuraDataset.Frame f:data.frames){ids.add(f.session);if(f.mask[6]>0)aim++;if(f.x[19]>0){seen++;error+=Math.hypot(f.x[0]*180,f.x[1]*90);if(Math.abs(f.x[10]*5+1.62)>.5)height++;}if(f.mask[4]>0){rotation+=f.y[4]*1000;rotationN++;}if(f.mask[5]>0){click+=f.y[5]*1000;clickN++;}if(f.mask[3]>0){interval+=Math.exp(f.y[3])*100;intervalN++;}turn+=Math.hypot(f.y[0],f.y[1])*128*f.x[20]*.15;}
        sessions=ids.size();aimpoints=aim;visibleFrames=seen;heightSamples=height;cps=intervalN==0?0:1000*intervalN/interval;aimReactionMs=rotationN==0?0:rotation/rotationN;clickReactionMs=clickN==0?0:click/clickN;angularError=seen==0?0:error/seen;turnSpeed=seconds==0?0:turn/seconds;
    }
    public static AuraStatistics from(AuraDataset data){return new AuraStatistics(data);}
}
