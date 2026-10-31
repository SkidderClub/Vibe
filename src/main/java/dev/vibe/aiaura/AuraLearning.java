package dev.vibe.aiaura;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPOutputStream;

/** Local persistence and cancellable CPU training. Background jobs publish complete immutable checkpoints. */
public final class AuraLearning {
    private final Path root;
    private final AuraDataset dataset;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Vibe AIAura trainer");t.setDaemon(true);t.setPriority(Thread.MIN_PRIORITY);return t;});
    private Future<?> training;
    private volatile TrainedAura model;
    private volatile long generation;
    public volatile String status="Record manual play to build your model";
    public volatile int epoch;
    public volatile double trainingLoss,validationLoss;
    public volatile boolean busy;
    private DataOutputStream events;
    private long session,eventsCount;
    private int eventsPart;
    private AuraStatistics statistics;
    private long statisticsAt;
    public AuraLearning(Path root){
        this.root=root;AuraDataset loaded;
        try{loaded=AuraDataset.read(root.resolve("demonstrations.bin.gz"));}catch(IOException e){loaded=new AuraDataset();status="Recording could not be loaded: "+e.getMessage();}
        dataset=loaded;
        Path path=root.resolve("model.json");if(Files.isRegularFile(path))try(Reader in=Files.newBufferedReader(path,StandardCharsets.UTF_8)){
            if(Files.size(path)>2_000_000)throw new IOException("Checkpoint is too large");JsonObject json=new JsonParser().parse(in).getAsJsonObject();
            if(!json.has("network")||!json.getAsJsonObject("network").has("weights")||json.getAsJsonObject("network").getAsJsonArray("weights").size()!=AuraNetwork.PARAMETERS)throw new IOException("Missing neural weights");
            TrainedAura saved=new Gson().fromJson(json,TrainedAura.class);if(saved==null||!saved.valid())throw new IOException("Invalid checkpoint");if(saved.pitchStyle==null)saved.pitchStyle=dataset.frames.isEmpty()?AuraPitchStyle.legacy(saved):AuraPitchStyle.learn(dataset);model=saved;epoch=saved.epochs;trainingLoss=saved.trainingLoss;validationLoss=saved.validationLoss;status="Personal model ready";
        }catch(Exception e){status="Model could not be loaded; recordings are available for retraining";}
    }
    public TrainedAura model(){return model;}
    public synchronized AuraDataset snapshot(){return dataset.copy();}
    public synchronized int frames(){return dataset.frames.size();}
    public synchronized int clicks(){return dataset.clicks();}
    public synchronized int reactions(){return dataset.reactions();}
    public synchronized int[] coverage(){return dataset.coverage();}
    public synchronized boolean ready(){return dataset.ready();}
    public synchronized String requirement(){return dataset.requirement();}
    public synchronized AuraStatistics statistics(){long now=System.nanoTime();if(statistics==null||statistics.frames!=dataset.frames.size()||now-statisticsAt>500000000L){statistics=AuraStatistics.from(dataset);statisticsAt=now;}return statistics;}
    public synchronized long begin()throws IOException{
        if(busy)throw new IOException("Wait for training to finish");closeEvents();Files.createDirectories(root);session=System.currentTimeMillis();eventsCount=0;eventsPart=0;openEvents();status="Recording manual inputs";return session;
    }
    private void openEvents()throws IOException{events=new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(root.resolve("session-"+session+"-"+(eventsPart++)+".events.gz")))));events.writeInt(0x56414945);events.writeInt(1);events.writeLong(session);}
    public synchronized void input(long nanos,int type,int code,boolean down,int dx,int dy,int wheel)throws IOException{
        if(events==null)return;if(eventsCount++>=1_000_000){closeEvents();eventsCount=0;openEvents();}
        events.writeLong(nanos);events.writeByte(type);events.writeInt(code);events.writeBoolean(down);events.writeInt(dx);events.writeInt(dy);events.writeInt(wheel);
    }
    public synchronized void append(AuraDataset.Frame frame){if(!busy)dataset.add(frame);}
    public synchronized void finish(){try{closeEvents();dataset.prepareLabels();dataset.write(root.resolve("demonstrations.bin.gz"));status="Recording saved locally";}catch(IOException e){status="Could not save recording: "+e.getMessage();}}
    private void closeEvents()throws IOException{if(events!=null){events.close();events=null;}}
    public synchronized void train(){
        if(busy)return;if(!dataset.ready()){status=dataset.requirement();return;}
        finish();AuraDataset data=dataset.copy();long version=generation;busy=true;epoch=0;status="Training personal neural model";
        training=worker.submit(()->{
            try{
                AuraNetwork net=new AuraNetwork(67+data.frames.size());net.train(data,60,10000,(e,loss)->{if(generation==version){epoch=e;trainingLoss=loss[0];validationLoss=loss[1];}});
                if(Thread.currentThread().isInterrupted()||generation!=version)return;TrainedAura trained=TrainedAura.calibrate(net,data,epoch);if(!trained.valid())throw new IOException("Training produced an invalid checkpoint");
                synchronized(this){if(generation!=version)return;Path temp=root.resolve("model.json.tmp");try(Writer out=Files.newBufferedWriter(temp,StandardCharsets.UTF_8)){new Gson().toJson(trained,out);}AuraDataset.move(temp,root.resolve("model.json"));model=trained;trainingLoss=trained.trainingLoss;validationLoss=trained.validationLoss;status="Personal model trained";}
            }catch(InterruptedException e){Thread.currentThread().interrupt();}catch(Exception e){if(generation==version)status="Training failed: "+e.getMessage();}finally{if(generation==version)busy=false;}
        });
    }
    public synchronized void reset()throws IOException{
        generation++;if(training!=null)training.cancel(true);busy=false;closeEvents();dataset.frames.clear();statistics=null;model=null;epoch=0;trainingLoss=validationLoss=0;
        Files.deleteIfExists(root.resolve("demonstrations.bin.gz"));Files.deleteIfExists(root.resolve("model.json"));Files.deleteIfExists(root.resolve("model.json.tmp"));Files.deleteIfExists(root.resolve("demonstrations.bin.gz.tmp"));
        if(Files.isDirectory(root))try(DirectoryStream<Path> files=Files.newDirectoryStream(root,"session-*.events.gz")){for(Path file:files)if(file.normalize().getParent().equals(root.normalize()))Files.deleteIfExists(file);}
        status="AI data reset";
    }
}
