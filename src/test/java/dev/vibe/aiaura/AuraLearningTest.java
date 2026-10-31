package dev.vibe.aiaura;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class AuraLearningTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    @Test public void nativeMouseCountsRetainFloatArithmeticAndClampPitch(){
        Random r=new Random(6);for(int i=0;i<5000;i++){float sens=r.nextFloat(),angle=r.nextFloat()*300-150;int dx=r.nextInt(801)-400;float gain=sens*.6F+.2F;gain=gain*gain*gain*8F;float expected=(float)(angle+dx*gain*.15D);assertEquals(expected,MouseMath.angle(angle,dx,sens),0);}
        assertEquals(90,MouseMath.pitch(89,100,.5F),0);assertEquals(-90,MouseMath.pitch(-89,-100,.5F),0);assertEquals(-179,MouseMath.wrap(181),0);
    }
    @Test public void contextSeparatesReachAndKeepsCollisionBorder(){
        AuraFeatures.Observation o=AuraFixtures.observation();o.targetX=0;AuraFeatures.Memory memory=new AuraFeatures.Memory();o.targetZ=2;float[] near=AuraFeatures.build(o,memory,0);assertEquals(1,near[24],0);
        o.targetZ=3;float[] edge=AuraFeatures.build(o,memory,50);assertEquals(1,edge[25],0);assertEquals(2.6,AuraFeatures.distance(o),1e-6);
        o.targetZ=4;float[] far=AuraFeatures.build(o,memory,100);assertEquals(1,far[26],0);assertTrue(AuraFeatures.bucket(far)/3!=AuraFeatures.bucket(near)/3);
        o.targetZ=-4;assertFalse(AuraFeatures.inView(o,70,16/9D));
    }
    @Test public void datasetRoundTripPreservesMasksMultiplePressesAndSessions()throws Exception{
        AuraDataset data=AuraFixtures.demonstrations();data.frames.get(0).pressCount=3;Path file=temp.newFolder().toPath().resolve("data.gz");data.write(file);AuraDataset loaded=AuraDataset.read(file);
        assertEquals(1000,loaded.frames.size());assertEquals(252,loaded.clicks());assertArrayEquals(data.frames.get(50).x,loaded.frames.get(50).x,0);assertArrayEquals(data.frames.get(50).mask,loaded.frames.get(50).mask,0);assertTrue(loaded.ready());assertEquals(800,loaded.validationStart());
        float[] window=loaded.window(800);for(int i=AuraFeatures.WIDTH;i<window.length;i++)assertEquals(0,window[i],0);
        assertEquals(data.frames.get(2).x[0],loaded.window(3)[AuraFeatures.WIDTH],0);loaded.frames.get(3).time=1000;assertEquals(0,loaded.window(3)[AuraFeatures.WIDTH],0);
    }
    @Test public void readinessRequiresActualReactionTimingHoldAndAimLabels(){
        AuraDataset data=AuraFixtures.demonstrations();assertTrue(data.ready());for(AuraDataset.Frame f:data.frames)f.mask[9]=0;assertFalse(data.ready());assertTrue(data.requirement().contains("releases"));
        for(AuraDataset.Frame f:data.frames)f.mask[9]=1;for(AuraDataset.Frame f:data.frames)f.mask[5]=0;assertFalse(data.ready());assertTrue(data.requirement().contains("reactions"));
        for(int i=800;i<1000;i++)data.frames.get(i).mask[5]=1;assertFalse(data.ready());assertTrue(data.requirement().contains("training portion"));
    }
    @Test public void invalidRecordingsAreRejectedInsteadOfPropagatingNan()throws Exception{
        AuraDataset data=AuraFixtures.demonstrations();data.frames.get(2).x[0]=Float.NaN;Path file=temp.newFolder().toPath().resolve("bad.gz");data.write(file);
        try{AuraDataset.read(file);fail("NaN recording accepted");}catch(java.io.IOException expected){assertTrue(expected.getMessage().contains("finite"));}
    }
    @Test public void intentLabelsDoNotLeakAcrossRecordingSessions(){
        AuraDataset data=new AuraDataset();for(int i=0;i<10;i++){AuraDataset.Frame f=new AuraDataset.Frame();f.session=i<5?1:2;f.time=i*50;f.press=i==4;data.add(f);}data.prepareLabels();assertEquals(1,data.frames.get(2).y[2],0);assertEquals(0,data.frames.get(5).y[2],0);
    }
    @Test public void actualBackpropagationImprovesChronologicalValidation()throws Exception{
        AuraDataset data=AuraFixtures.demonstrations();AuraNetwork network=new AuraNetwork(12);double initial=network.loss(data,800,1000);AtomicInteger epochs=new AtomicInteger();network.train(data,28,1000,(epoch,loss)->epochs.set(epoch));
        assertTrue(epochs.get()>=20);assertTrue(network.valid());assertTrue("Validation loss did not improve",network.loss(data,800,1000)<initial*.65);
        TrainedAura model=TrainedAura.calibrate(network,data,epochs.get());assertTrue(model.valid());String json=new Gson().toJson(model);TrainedAura loaded=new Gson().fromJson(json,TrainedAura.class);assertTrue(loaded.valid());assertArrayEquals(network.predict(data.window(850)),loaded.network.predict(data.window(850)),0);
    }
    @Test public void subsamplingRetainsSparseReactionsAndExcludesValidation(){
        AuraDataset data=AuraFixtures.demonstrations();for(AuraDataset.Frame frame:data.frames)frame.mask[4]=frame.mask[5]=0;data.frames.get(13).mask[4]=1;data.frames.get(47).mask[5]=1;
        int[] indices=AuraNetwork.trainingIndices(data,792,100);assertEquals(100,indices.length);Set<Integer> unique=new HashSet<>();for(int i:indices){assertTrue(i<792);unique.add(i);}assertEquals(100,unique.size());assertTrue(unique.contains(13));assertTrue(unique.contains(47));
    }
    @Test public void checkpointMustContainExplicitWeightsAndSaneTiming()throws Exception{
        Path root=temp.newFolder().toPath();TrainedAura model=AuraFixtures.policy();assertTrue(model.valid());Files.write(root.resolve("model.json"),new Gson().toJson(model).getBytes(StandardCharsets.UTF_8));assertNotNull(new AuraLearning(root).model());
        model.minRotationDelay=-1;assertFalse(model.valid());model=AuraFixtures.policy();model.maxInterval=Double.POSITIVE_INFINITY;assertFalse(model.valid());
        com.google.gson.JsonObject json=new com.google.gson.JsonParser().parse(new Gson().toJson(AuraFixtures.policy())).getAsJsonObject();json.getAsJsonObject("network").remove("weights");Files.write(root.resolve("model.json"),json.toString().getBytes(StandardCharsets.UTF_8));assertNull(new AuraLearning(root).model());
    }
    @Test public void resetCancelsTrainingAndDeletesOnlyOwnedData()throws Exception{
        Path root=temp.newFolder().toPath();AuraLearning service=new AuraLearning(root);assertNull(service.model());for(AuraDataset.Frame frame:AuraFixtures.demonstrations().frames)service.append(frame);service.begin();service.input(0,1,-100,true,2,3,0);service.finish();Files.write(root.resolve("keep.txt"),new byte[]{1});service.train();service.reset();
        assertEquals(0,service.frames());assertNull(service.model());assertFalse(service.busy);assertTrue(Files.exists(root.resolve("keep.txt")));assertFalse(Files.exists(root.resolve("model.json")));try(java.util.stream.Stream<Path> files=Files.list(root)){assertEquals(1,files.count());}
    }
}
