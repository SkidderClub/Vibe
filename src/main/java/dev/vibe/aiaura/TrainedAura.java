package dev.vibe.aiaura;

import java.util.*;

/** Neural checkpoint plus measured, condition-specific demonstration residuals. No bundled profile. */
public final class TrainedAura {
    public int version=1,records,clicks,reactions,epochs;
    public long trainedAt;
    public double trainingLoss,validationLoss,minStep=Double.POSITIVE_INFINITY,maxStep;
    public AuraNetwork network;
    public AuraPitchStyle pitchStyle;
    public double[][] sigma=new double[9][2],rho=new double[9][2],maxCounts=new double[9][2];
    public double[] correlation=new double[9],intervalSigma=new double[9];
    public double rotationDelaySigma,clickDelaySigma,minRotationDelay=Double.POSITIVE_INFINITY,maxRotationDelay,minClickDelay=Double.POSITIVE_INFINITY,maxClickDelay;
    public double minInterval=10000,maxInterval;
    public int[] cells=new int[9];
    public static TrainedAura calibrate(AuraNetwork network,AuraDataset data,int epochs){
        TrainedAura model=new TrainedAura();model.network=network;model.epochs=epochs;model.records=data.frames.size();model.clicks=data.clicks();model.reactions=data.reactions();model.trainedAt=System.currentTimeMillis();int split=data.validationStart();model.trainingLoss=network.loss(data,0,Math.max(1,split-8));model.validationLoss=network.loss(data,split,data.frames.size());
        double[][] sum=new double[9][2],squares=new double[9][2],lag=new double[9][2],prev=new double[9][2];double[] cross=new double[9],intervalSquares=new double[9];int[] intervals=new int[9];double rotSquares=0,clickSquares=0;int rotations=0,clickDelays=0;long previousSession=-1;int previousCell=-1;
        @SuppressWarnings("unchecked") List<Double>[][] magnitudes=new List[9][2];for(int b=0;b<9;b++)for(int j=0;j<2;j++)magnitudes[b][j]=new ArrayList<>();
        for(int i=0;i<data.frames.size();i++){
            AuraDataset.Frame frame=data.frames.get(i);model.minStep=Math.min(model.minStep,frame.x[20]*.15);model.maxStep=Math.max(model.maxStep,frame.x[20]*.15);
            if(frame.x[19]==0){previousCell=-1;continue;}
            int b=AuraFeatures.bucket(frame.x);for(int j=0;j<2;j++)magnitudes[b][j].add(Math.abs(frame.y[j]*128D));
            if(frame.mask[3]>0){double interval=Math.exp(frame.y[3])*100;model.minInterval=Math.min(model.minInterval,interval);model.maxInterval=Math.max(model.maxInterval,interval);}
            if(frame.mask[4]>0){model.minRotationDelay=Math.min(model.minRotationDelay,frame.y[4]*1000);model.maxRotationDelay=Math.max(model.maxRotationDelay,frame.y[4]*1000);}
            if(frame.mask[5]>0){model.minClickDelay=Math.min(model.minClickDelay,frame.y[5]*1000);model.maxClickDelay=Math.max(model.maxClickDelay,frame.y[5]*1000);}
            if(i<split)continue;float[] prediction=network.predict(data.window(i));double[] residual={(frame.y[0]-prediction[0])*128,(frame.y[1]-prediction[1])*128};model.cells[b]++;
            for(int j=0;j<2;j++){sum[b][j]+=residual[j];squares[b][j]+=residual[j]*residual[j];if(frame.session==previousSession&&b==previousCell)lag[b][j]+=residual[j]*prev[b][j];prev[b][j]=residual[j];}cross[b]+=residual[0]*residual[1];previousSession=frame.session;previousCell=b;
            if(frame.mask[3]>0){double d=frame.y[3]-prediction[3];intervalSquares[b]+=d*d;intervals[b]++;}
            if(frame.mask[4]>0){double d=(frame.y[4]-prediction[4])*1000;rotSquares+=d*d;rotations++;}if(frame.mask[5]>0){double d=(frame.y[5]-prediction[5])*1000;clickSquares+=d*d;clickDelays++;}
        }
        for(int b=0;b<9;b++){
            for(int j=0;j<2;j++){int n=model.cells[b];model.sigma[b][j]=n>=4?Math.sqrt(Math.max(0,squares[b][j]/n-Math.pow(sum[b][j]/n,2))):0;model.rho[b][j]=squares[b][j]>1e-9?Math.max(-.95,Math.min(.95,lag[b][j]/squares[b][j])):0;List<Double> values=magnitudes[b][j];Collections.sort(values);model.maxCounts[b][j]=values.isEmpty()?0:values.get(Math.min(values.size()-1,(int)(values.size()*.995)));}
            double denominator=model.sigma[b][0]*model.sigma[b][1];model.correlation[b]=denominator>1e-9?Math.max(-.95,Math.min(.95,(cross[b]/model.cells[b]-(sum[b][0]/model.cells[b])*(sum[b][1]/model.cells[b]))/denominator)):0;
            model.intervalSigma[b]=intervals[b]>=3?Math.sqrt(intervalSquares[b]/intervals[b]):0;
        }
        model.rotationDelaySigma=rotations>=3?Math.sqrt(rotSquares/rotations):0;model.clickDelaySigma=clickDelays>=3?Math.sqrt(clickSquares/clickDelays):0;
        if(model.minInterval>model.maxInterval){model.minInterval=50;model.maxInterval=1000;}
        if(model.minRotationDelay>model.maxRotationDelay)model.minRotationDelay=model.maxRotationDelay=0;if(model.minClickDelay>model.maxClickDelay)model.minClickDelay=model.maxClickDelay=0;
        model.pitchStyle=AuraPitchStyle.learn(data);return model;
    }
    public boolean valid(){
        if(pitchStyle!=null&&!pitchStyle.valid())return false;
        if(version!=1||network==null||!network.valid()||records<600||clicks<15||reactions<3||epochs<1||!Double.isFinite(trainingLoss)||trainingLoss<0||!Double.isFinite(validationLoss)||validationLoss<0||minStep<=0||!Double.isFinite(minStep)||!Double.isFinite(maxStep)||maxStep<minStep||maxStep>MouseMath.step(1F)+1e-6)return false;
        if(sigma==null||rho==null||maxCounts==null||sigma.length!=9||rho.length!=9||maxCounts.length!=9||correlation==null||correlation.length!=9||intervalSigma==null||intervalSigma.length!=9||cells==null||cells.length!=9)return false;
        for(int b=0;b<9;b++){if(sigma[b]==null||sigma[b].length!=2||rho[b]==null||rho[b].length!=2||maxCounts[b]==null||maxCounts[b].length!=2)return false;for(int j=0;j<2;j++)if(!Double.isFinite(sigma[b][j])||sigma[b][j]<0||!Double.isFinite(rho[b][j])||Math.abs(rho[b][j])>.951||!Double.isFinite(maxCounts[b][j])||maxCounts[b][j]<0)return false;if(!Double.isFinite(correlation[b])||Math.abs(correlation[b])>.951||!Double.isFinite(intervalSigma[b])||intervalSigma[b]<0)return false;}
        return Double.isFinite(minInterval)&&Double.isFinite(maxInterval)&&minInterval>=1&&maxInterval>=minInterval&&maxInterval<=10001&&Double.isFinite(minRotationDelay)&&Double.isFinite(maxRotationDelay)&&minRotationDelay>=0&&maxRotationDelay>=minRotationDelay&&maxRotationDelay<=3600000&&Double.isFinite(minClickDelay)&&Double.isFinite(maxClickDelay)&&minClickDelay>=0&&maxClickDelay>=minClickDelay&&maxClickDelay<=3600000&&Double.isFinite(rotationDelaySigma)&&rotationDelaySigma>=0&&Double.isFinite(clickDelaySigma)&&clickDelaySigma>=0;
    }
}
