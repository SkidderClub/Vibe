package dev.vibe.aiaura;

import java.util.*;

/** Recorded motor response and speed limits for closed-loop vertical tracking. */
public final class AuraPitchStyle {
    public double response=.18,maximumCounts=16;
    public int verticalSamples;
    public static AuraPitchStyle learn(AuraDataset data){
        AuraPitchStyle style=new AuraPitchStyle();List<Double> yawSpeed=new ArrayList<>(),pitchSpeed=new ArrayList<>(),yawResponse=new ArrayList<>(),pitchResponse=new ArrayList<>();
        for(AuraDataset.Frame f:data.frames){if(f.x[19]<=0)continue;double step=f.x[20]*.15;
            for(int axis=0;axis<2;axis++){double counts=f.y[axis]*128,error=f.x[axis]*(axis==0?180:90),angle=counts*step;
                if(Math.abs(counts)>=.5)(axis==0?yawSpeed:pitchSpeed).add(Math.abs(counts));
                if(Math.abs(error)>1&&angle*error>0&&Math.abs(angle)>.02&&Math.abs(angle/error)<1)(axis==0?yawResponse:pitchResponse).add(Math.abs(angle/error));
            }
            if(Math.abs(f.x[10]*5+1.62)>.5)style.verticalSamples++;
        }
        List<Double> responses=pitchResponse.size()>=3?pitchResponse:yawResponse;if(!responses.isEmpty())style.response=clamp(percentile(responses,.5),.12,.42);
        style.maximumCounts=Math.min(4096,Math.max(6,Math.max(percentile(pitchSpeed,.99),percentile(yawSpeed,.99)*.75)));return style;
    }
    public static AuraPitchStyle legacy(TrainedAura model){AuraPitchStyle style=new AuraPitchStyle();double cap=0;for(double[] counts:model.maxCounts)cap=Math.max(cap,Math.max(counts[1],counts[0]*.75));style.maximumCounts=Math.min(4096,Math.max(6,cap));return style;}
    public double counts(double errorDegrees,double angularVelocity,float step,double neuralCounts){
        double feedback=(errorDegrees*response+angularVelocity*.5)/step;
        // A zero or wrong-sign regressor cannot pin pitch to the horizon.
        double trusted=neuralCounts*errorDegrees>0?neuralCounts:0;
        if(Math.abs(errorDegrees)<step*.6&&Math.abs(angularVelocity)<step*.2)return 0;
        return clamp(feedback*.75+trusted*.25,-maximumCounts,maximumCounts);
    }
    public boolean valid(){return Double.isFinite(response)&&response>=.02&&response<=.8&&Double.isFinite(maximumCounts)&&maximumCounts>=1&&maximumCounts<=8192&&verticalSamples>=0;}
    private static double percentile(List<Double> values,double quantile){if(values.isEmpty())return 0;Collections.sort(values);return values.get(Math.min(values.size()-1,(int)(values.size()*quantile)));}
    private static double clamp(double value,double min,double max){return Math.max(min,Math.min(max,value));}
}
