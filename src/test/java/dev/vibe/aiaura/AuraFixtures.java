package dev.vibe.aiaura;

import java.util.Arrays;

/** Synthetic fixtures are test-only and never installed as a user model. */
public final class AuraFixtures {
    public static AuraDataset demonstrations(){
        AuraDataset data=new AuraDataset();
        for(int i=0;i<1000;i++){
            AuraDataset.Frame f=new AuraDataset.Frame();f.session=i<800?1:2;f.time=(i<800?i:i-800)*50L;f.target=1+i/100;f.sensitivity=.5F;
            f.x[0]=(float)Math.sin(i*.08);f.x[1]=(float)Math.cos(i*.06);f.x[19]=1;f.x[20]=(float)(MouseMath.step(.5F)/.15);f.x[24+i%3]=1;
            f.y[0]=f.x[0]*.12F;f.y[1]=f.x[1]*.08F;f.y[2]=f.x[0]>0?1:0;f.y[3]=0;f.y[4]=.15F;f.y[5]=.2F;f.y[6]=.5F;f.y[7]=.6F;f.y[8]=.5F;f.y[9]=0;
            Arrays.fill(f.mask,1);f.press=i%4==0;f.pressCount=f.press?1:0;data.add(f);
        }return data;
    }
    public static TrainedAura policy(){
        TrainedAura m=new TrainedAura();m.network=new AuraNetwork();Arrays.fill(m.network.weights,0);
        bias(m,0,.07F);bias(m,1,.04F);bias(m,2,10);bias(m,4,.15F);bias(m,5,.2F);
        m.records=1000;m.clicks=100;m.reactions=10;m.epochs=10;m.minStep=m.maxStep=MouseMath.step(.5F);m.minInterval=m.maxInterval=100;m.minRotationDelay=m.maxRotationDelay=150;m.minClickDelay=m.maxClickDelay=200;
        for(double[] counts:m.maxCounts)Arrays.fill(counts,16);return m;
    }
    public static void bias(TrainedAura m,int head,float value){m.network.weights[AuraNetwork.PARAMETERS-AuraNetwork.OUT*(AuraNetwork.B+1)+head*(AuraNetwork.B+1)+AuraNetwork.B]=value;}
    public static AuraFeatures.Observation observation(){AuraFeatures.Observation o=new AuraFeatures.Observation();o.eyeY=1.62;o.targetX=-1;o.targetZ=3;o.targetId=5;o.visible=true;o.grounded=true;return o;}
}
