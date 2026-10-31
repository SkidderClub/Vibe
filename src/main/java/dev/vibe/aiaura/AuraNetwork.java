package dev.vibe.aiaura;

import java.util.*;
import java.util.function.BiConsumer;

/** Temporal behavior cloning: 160 inputs, tanh 32/24 hidden layers and ten masked task heads. */
public final class AuraNetwork {
    public static final int A=32,B=24,OUT=AuraFeatures.OUTPUTS,IN=AuraFeatures.INPUTS;
    private static final int SECOND=A*(IN+1),THIRD=SECOND+B*(A+1);
    public static final int PARAMETERS=THIRD+OUT*(B+1);
    public float[] weights=new float[PARAMETERS];
    public AuraNetwork(){this(42);}
    public AuraNetwork(long seed){Random random=new Random(seed);for(int i=0;i<weights.length;i++)weights[i]=(float)(random.nextGaussian()*.06);}
    public static final class Workspace {final float[] a=new float[A],b=new float[B],out=new float[OUT],da=new float[A],db=new float[B],dout=new float[OUT];}
    public float[] predict(float[] x){Workspace w=new Workspace();forward(x,w);return w.out.clone();}
    public void forward(float[] x,Workspace w){
        if(x.length!=IN)throw new IllegalArgumentException("AIAura feature schema mismatch");
        for(int j=0;j<A;j++){int p=j*(IN+1);float s=weights[p+IN];for(int i=0;i<IN;i++)s+=weights[p+i]*x[i];w.a[j]=(float)Math.tanh(s);}
        for(int j=0;j<B;j++){int p=SECOND+j*(A+1);float s=weights[p+A];for(int i=0;i<A;i++)s+=weights[p+i]*w.a[i];w.b[j]=(float)Math.tanh(s);}
        for(int j=0;j<OUT;j++){int p=THIRD+j*(B+1);float s=weights[p+B];for(int i=0;i<B;i++)s+=weights[p+i]*w.b[i];w.out[j]=probability(j)?sigmoid(s):s;}
    }
    private static boolean probability(int head){return head==2||head>=6&&head<=8;}
    private static float sigmoid(float v){return (float)(1/(1+Math.exp(-Math.max(-30,Math.min(30,v)))));}
    private double gradient(float[] x,AuraDataset.Frame f,Workspace w,float[] gradient){
        forward(x,w);Arrays.fill(w.da,0);Arrays.fill(w.db,0);double loss=0;
        for(int j=0;j<OUT;j++){
            float mask=f.mask[j],error=w.out[j]-f.y[j],scale=j==1?(Math.abs(f.y[1])>.5F/128?2F:.5F):j==0?1:j==2?.8F:j>=6&&j<=8?.3F:.25F;
            if(j==2){loss-=mask*scale*(f.y[j]*Math.log(Math.max(1e-6,w.out[j]))+(1-f.y[j])*Math.log(Math.max(1e-6,1-w.out[j])));w.dout[j]=error*mask*scale;}
            else{loss+=mask*scale*(Math.abs(error)<1?.5*error*error:Math.abs(error)-.5);w.dout[j]=Math.max(-1,Math.min(1,error))*mask*scale*(probability(j)?w.out[j]*(1-w.out[j]):1);}
            int p=THIRD+j*(B+1);for(int i=0;i<B;i++){gradient[p+i]+=w.dout[j]*w.b[i];w.db[i]+=w.dout[j]*weights[p+i];}gradient[p+B]+=w.dout[j];
        }
        for(int j=0;j<B;j++){float d=w.db[j]*(1-w.b[j]*w.b[j]);int p=SECOND+j*(A+1);for(int i=0;i<A;i++){gradient[p+i]+=d*w.a[i];w.da[i]+=d*weights[p+i];}gradient[p+A]+=d;}
        for(int j=0;j<A;j++){float d=w.da[j]*(1-w.a[j]*w.a[j]);int p=j*(IN+1);for(int i=0;i<IN;i++)gradient[p+i]+=d*x[i];gradient[p+IN]+=d;}
        return loss;
    }
    public double loss(AuraDataset data,int start,int end){Workspace w=new Workspace();double loss=0,total=0;for(int index=start;index<end;index++){AuraDataset.Frame f=data.frames.get(index);forward(data.window(index),w);for(int j=0;j<OUT;j++)if(f.mask[j]>0){double error=w.out[j]-f.y[j];loss+=j==2?-(f.y[j]*Math.log(Math.max(1e-6,w.out[j]))+(1-f.y[j])*Math.log(Math.max(1e-6,1-w.out[j]))):error*error;total++;}}return loss/Math.max(1,total);}
    public void train(AuraDataset data,int epochs,int limit,BiConsumer<Integer,double[]> progress)throws InterruptedException{
        int split=data.validationStart(),end=Math.max(1,split-AuraFeatures.HISTORY-4);int[] indices=trainingIndices(data,end,limit);int count=indices.length;
        float[][] inputs=new float[count][];for(int i=0;i<count;i++)inputs[i]=data.window(indices[i]);
        float[] m=new float[PARAMETERS],v=new float[PARAMETERS],g=new float[PARAMETERS];Workspace workspace=new Workspace();Random random=new Random(67);long step=0;double best=Double.POSITIVE_INFINITY;float[] bestWeights=weights.clone();int stale=0;
        for(int epoch=0;epoch<epochs;epoch++){
            int[] order=new int[count];for(int i=0;i<count;i++)order[i]=i;for(int i=count-1;i>0;i--){int k=random.nextInt(i+1),tmp=order[i];order[i]=order[k];order[k]=tmp;}
            for(int batch=0;batch<count;batch+=64){if(Thread.currentThread().isInterrupted())throw new InterruptedException();Arrays.fill(g,0);int size=Math.min(64,count-batch);
                for(int k=0;k<size;k++){int i=order[batch+k];gradient(inputs[i],data.frames.get(indices[i]),workspace,g);}step++;
                double b1=1-Math.pow(.9,step),b2=1-Math.pow(.999,step);
                for(int p=0;p<PARAMETERS;p++){float d=Math.max(-2,Math.min(2,g[p]/size));m[p]=.9F*m[p]+.1F*d;v[p]=.999F*v[p]+.001F*d*d;weights[p]-=.0015F*(m[p]/b1)/(Math.sqrt(v[p]/b2)+1e-8);if(!Float.isFinite(weights[p]))throw new IllegalStateException("Neural training diverged");}
            }
            double training=loss(data,0,Math.min(end,1000)),validation=loss(data,split,data.frames.size());
            if(validation<best){best=validation;bestWeights=weights.clone();stale=0;}else stale++;
            progress.accept(epoch+1,new double[]{training,validation});if(stale>=10&&epoch>=20)break;
        }
        weights=bestWeights;
    }
    /** Preserve scarce acquisition/interval/hold labels when a long recording is subsampled. */
    static int[] trainingIndices(AuraDataset data,int end,int limit){
        int count=Math.min(limit,end);LinkedHashSet<Integer> selected=new LinkedHashSet<>();
        List<Integer> vertical=new ArrayList<>(),height=new ArrayList<>();for(int i=0;i<end;i++){AuraDataset.Frame f=data.frames.get(i);if(Math.abs(f.y[1])>.5F/128)vertical.add(i);if(f.x[19]>0&&Math.abs(f.x[10]*5+1.62)>.5)height.add(i);}
        for(List<Integer> group:Arrays.asList(vertical,height)){int take=Math.min(group.size(),Math.max(1,count/6));for(int i=0;i<take&&selected.size()<count;i++)selected.add(group.get((int)((long)i*group.size()/take)));}
        for(int head:new int[]{4,5,3,9,6}){
            List<Integer> labeled=new ArrayList<>();for(int i=0;i<end;i++)if(data.frames.get(i).mask[head]>0)labeled.add(i);
            int take=Math.min(labeled.size(),Math.max(1,count/(head==4||head==5?6:12)));
            for(int i=0;i<take&&selected.size()<count;i++)selected.add(labeled.get((int)((long)i*labeled.size()/take)));
        }
        for(int i=0;i<count&&selected.size()<count;i++)selected.add((int)((long)i*end/count));
        for(int i=0;i<end&&selected.size()<count;i++)selected.add(i);
        int[] indices=new int[selected.size()];int i=0;for(int index:selected)indices[i++]=index;return indices;
    }
    public boolean valid(){if(weights==null||weights.length!=PARAMETERS)return false;for(float v:weights)if(!Float.isFinite(v)||Math.abs(v)>100)return false;return true;}
}
