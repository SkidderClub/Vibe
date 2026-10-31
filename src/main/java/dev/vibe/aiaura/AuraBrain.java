package dev.vibe.aiaura;

import java.util.Random;

/** Stateful learned policy, separate from vanilla input transport and from training. */
public final class AuraBrain {
    private final Random random;
    public final AuraFeatures.Memory memory=new AuraFeatures.Memory();
    private TrainedAura model;
    private float[] prediction;
    private double carryX,carryY,clickBoost=1;
    private final AuraAimFilter aimFilter=new AuraAimFilter();
    private long lastAdvance; private boolean aimingStarted;
    private long rotationAt,clickAt,nextClick,heldUntil;
    private int bucket;
    public AuraBrain(){this(new Random());}public AuraBrain(Random random){this.random=random;}
    public void reset(){memory.reset();model=null;prediction=null;carryX=carryY=0;aimFilter.reset();aimingStarted=false;lastAdvance=0;nextClick=heldUntil=0;}
    /** Returns physical integer mouse counts, with positive pitch counts looking down. */
    public int[] advance(TrainedAura trained,AuraFeatures.Observation observation,long now,double aimBoost,double clickingBoost){
        if(trained==null||!trained.valid()||!observation.visible||observation.targetId<0||observation.targetHealth<=0){reset();return new int[2];}
        if(model!=trained){reset();model=trained;}
        boolean acquired=memory.target!=observation.targetId;observation.leftHeld|=now<heldUntil;
        float[] features=AuraFeatures.build(observation,memory,now);double actualStep=MouseMath.step(observation.sensitivity),reference=Math.max(model.minStep,Math.min(model.maxStep,actualStep));features[20]=(float)(reference/.15);bucket=AuraFeatures.bucket(features);
        prediction=model.network.predict(memory.push(features));clickBoost=clickingBoost;
        if(acquired){carryX=carryY=0;aimFilter.reset();aimingStarted=false;lastAdvance=now;rotationAt=now+sampleDelay(prediction[4]*1000,model.rotationDelaySigma,model.minRotationDelay,model.maxRotationDelay);clickAt=now+sampleDelay(prediction[5]*1000,model.clickDelaySigma,model.minClickDelay,model.maxClickDelay);nextClick=clickAt;}
        double elapsed=lastAdvance==now?50:Math.max(1,Math.min(100,now-lastAdvance));lastAdvance=now;double pointBlend=1-Math.exp(-elapsed/130);
        memory.pointX+=(bound(prediction[6],.05,.95)-memory.pointX)*pointBlend;memory.pointY+=(bound(prediction[7],.05,.95)-memory.pointY)*pointBlend;memory.pointZ+=(bound(prediction[8],.05,.95)-memory.pointZ)*pointBlend;
        int x=0,y=0;
        if(now>=rotationAt){
            AuraPitchStyle vertical=model.pitchStyle==null?AuraPitchStyle.legacy(model):model.pitchStyle;
            double px=observation.targetX+(memory.pointX-.5)*observation.targetWidth,py=observation.targetY+memory.pointY*observation.targetHeight,pz=observation.targetZ+(memory.pointZ-.5)*observation.targetWidth;
            double goal=AuraFeatures.angles(observation.eyeX,observation.eyeY,observation.eyeZ,px,py,pz)[1];
            double future=AuraFeatures.angles(observation.eyeX+observation.vx,observation.eyeY+observation.vy,observation.eyeZ+observation.vz,px+observation.targetVx,py+observation.targetVy,pz+observation.targetVz)[1];
            double pitchError=goal-observation.pitch,conversion=aimBoost*reference/actualStep,maxX=model.maxCounts[bucket][0]*conversion,maxY=vertical.maximumCounts*conversion;
            double wantedX=bound(prediction[0]*128,-model.maxCounts[bucket][0],model.maxCounts[bucket][0])*conversion,wantedY=vertical.counts(pitchError,future-goal,(float)reference,prediction[1]*128)*conversion;
            // Allow corrections/overshoot inside the target footprint, but never sustain a turn away into empty space.
            if(Math.signum(wantedX)!=Math.signum(features[0])&&Math.abs(features[0]*180)>features[3]*90*.35)wantedX=0;
            if(Math.hypot(observation.vx,observation.vz)<.01){
                if(Math.abs(features[5])<.002)wantedX*=Math.min(1,Math.abs(features[0]*180)/Math.max(actualStep*.75,features[3]*90*.08));
                if(Math.abs(future-goal)<actualStep*.2)wantedY*=Math.min(1,Math.abs(pitchError)/Math.max(actualStep*.75,features[4]*90*.04));
            }
            double[] smooth=aimFilter.advance(wantedX,wantedY,aimingStarted?elapsed:50,maxX,maxY);double dx=smooth[0]+carryX,dy=smooth[1]+carryY;aimingStarted=true;
            if(Math.abs(features[0]*180)<actualStep*.75&&Math.abs(wantedX)<.5){dx=0;carryX=0;}
            if(Math.abs(pitchError)<actualStep*.75&&Math.abs(future-goal)<actualStep*.2){dy=0;carryY=0;}
            else if(dy*pitchError>0&&Math.abs(future-goal)<actualStep*.2)dy=Math.copySign(Math.min(Math.abs(dy),Math.abs(pitchError)/actualStep),dy);
            x=(int)Math.round(bound(dx,-4096,4096));y=(int)Math.round(bound(dy,-4096,4096));carryX=bound(dx-x,-.5,.5);carryY=bound(dy-y,-.5,.5);
        }
        memory.motion(x*actualStep/reference,y*actualStep/reference);return new int[]{x,y};
    }
    public boolean due(long now){return model!=null&&prediction!=null&&prediction[2]>=.35&&now>=clickAt&&now>=nextClick;}
    public int poll(long now){
        if(!due(now))return 0;int count=0;if(nextClick<now-250)nextClick=now;
        while(nextClick<=now&&count<8){double interval=Math.exp(bound(prediction[3]+random.nextGaussian()*model.intervalSigma[bucket],-4,5))*100;interval=bound(interval,model.minInterval,model.maxInterval);nextClick+=Math.max(2,Math.round(interval/Math.max(.1,clickBoost)));count++;}
        memory.lastClick=now;heldUntil=now+Math.round(bound(Math.exp(bound(prediction[9],-4,4))*50,1,500));return count;
    }
    private long sampleDelay(double mean,double sigma,double min,double max){return Math.round(bound(mean+random.nextGaussian()*sigma,Math.max(0,min),Math.min(3600000,Math.max(min,max))));}
    private static double bound(double value,double min,double max){return Math.max(min,Math.min(max,Double.isFinite(value)?value:0));}
}
