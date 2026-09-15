package dev.vibe.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;

/**
 * Offline-first Cosmetica catalogue client. The build packages a snapshot of
 * the public catalogue's authored model, texture and preview assets, while
 * retaining the per-installation cache only as a fallback for later entries.
 */
public final class CosmeticaCatalogService {
    private static final String API = "https://api.cloaks.gg/search/cosmetics";
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final File models, textures;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(new java.util.concurrent.ThreadFactory() {
        @Override public Thread newThread(Runnable runnable) { Thread thread = new Thread(runnable, "Vibe-Cosmetica-Catalog"); thread.setDaemon(true); return thread; }
    });
    private final Map<String,CosmeticaModel> loadedModels = Collections.synchronizedMap(new HashMap<String,CosmeticaModel>());
    private final Map<String,ResourceLocation> loadedTextures = Collections.synchronizedMap(new HashMap<String,ResourceLocation>());
    private volatile List<CosmeticaAccessory> results = Collections.emptyList();
    private volatile boolean loading;
    private volatile String status = "Loading catalog…";
    private volatile int page = 1, pages = 1;
    private String requested = "";
    private volatile List<CosmeticaAccessory> bundledCatalog;

    public CosmeticaCatalogService(File minecraftDirectory) {
        File root = new File(new File(minecraftDirectory, "vibe"), "cosmetica"); models = new File(root,"models"); textures = new File(root,"textures");
        models.mkdirs(); textures.mkdirs(); search("", 1);
    }

    public void search(final String query, final int wantedPage) {
        final String clean=query==null?"":query.trim();
        List<CosmeticaAccessory> local = bundledCatalog();
        if (!local.isEmpty()) {
            requested = clean;
            List<CosmeticaAccessory> filtered = new ArrayList<CosmeticaAccessory>();
            String needle = clean.toLowerCase(java.util.Locale.ROOT);
            for (CosmeticaAccessory accessory : local) {
                if (needle.isEmpty() || accessory.getName().toLowerCase(java.util.Locale.ROOT).contains(needle)
                        || accessory.getDescription().toLowerCase(java.util.Locale.ROOT).contains(needle)
                        || accessory.getAttachment().contains(needle)) filtered.add(accessory);
            }
            // All bundled accessories are local. Keep a single virtual list
            // instead of forcing a page change every 24 entries; the editor
            // only binds the visible thumbnail rows and a small nearby buffer.
            page = 1;
            pages = 1;
            results = Collections.unmodifiableList(filtered);
            status = filtered.isEmpty() ? "No local accessories found." : filtered.size() + " local accessories";
            loading = false;
            return;
        }
        synchronized(this) { if(loading && clean.equals(requested) && wantedPage==page)return; loading=true; requested=clean; status="Loading catalog…"; }
        worker.submit(new Runnable(){@Override public void run(){
            try {
                String payload="{\"query\":"+quote(clean)+",\"pageSize\":24,\"page\":"+Math.max(1,wantedPage)+"}";
                JsonObject root=new JsonParser().parse(post(API,payload)).getAsJsonObject(); List<CosmeticaAccessory> listed=new ArrayList<CosmeticaAccessory>();
                JsonArray list=root.has("results")&&root.get("results").isJsonArray()?root.getAsJsonArray("results"):new JsonArray();
                for(JsonElement element:list) if(element.isJsonObject()) { CosmeticaAccessory accessory=CosmeticaAccessory.fromApi(element.getAsJsonObject()); if(accessory!=null&&!accessory.getModelUrl().isEmpty()&&!accessory.getTextureUrl().isEmpty())listed.add(accessory); }
                results=Collections.unmodifiableList(listed); page=root.has("page")?root.get("page").getAsInt():Math.max(1,wantedPage); pages=root.has("estimatedPages")?Math.max(1,root.get("estimatedPages").getAsInt()):1; status=listed.isEmpty()?"No published accessories found.":listed.size()+" authored accessories";
            } catch(Exception error) { results=Collections.emptyList(); status="Catalog unavailable: "+shortMessage(error); }
            finally { loading=false; }
        }});
    }

    public List<CosmeticaAccessory> getResults(){return results;} public boolean isLoading(){return loading;} public String getStatus(){return status;} public int getPage(){return page;} public int getPages(){return pages;}
    public void nextPage(){if(!loading&&page<pages)search(requested,page+1);} public void previousPage(){if(!loading&&page>1)search(requested,page-1);}

    public CosmeticaModel getModel(final CosmeticaAccessory accessory) {
        if(accessory==null||accessory.getModelUrl().isEmpty())return null; CosmeticaModel model=loadedModels.get(accessory.getId()); if(model!=null)return model;
        String bundled = readBundled("models/" + safe(accessory.getId()) + ".json");
        if (bundled != null) try { model = CosmeticaModel.parse(bundled); loadedModels.put(accessory.getId(), model); return model; } catch (Exception ignored) { }
        File file=new File(models,safe(accessory.getId())+".json");
        try { if(file.isFile()) { model=CosmeticaModel.parse(read(file)); loadedModels.put(accessory.getId(),model); return model; } } catch(Exception ignored) { file.delete(); }
        queueModel(accessory,file); return null;
    }

    public ResourceLocation getTexture(CosmeticaAccessory accessory, boolean thumbnail) {
        if(accessory==null)return DefaultPlayerSkin.getDefaultSkinLegacy(); String url=thumbnail&&!accessory.getThumbnailUrl().isEmpty()?accessory.getThumbnailUrl():accessory.getTextureUrl(); if(url.isEmpty()||!safeUrl(url))return DefaultPlayerSkin.getDefaultSkinLegacy();
        String key=accessory.getId()+(thumbnail?"_thumb":""); ResourceLocation existing=loadedTextures.get(key); if(existing!=null)return existing;
        String group = thumbnail ? "thumbnails/" : "textures/";
        // Minecraft 1.8.9's texture loader does not decode WebP.  The
        // build-time catalogue dumper therefore retains the original WebP
        // thumbnail for provenance and creates a PNG sibling for Vibe's GUI.
        String bundledExtension = thumbnail ? ".png" : extension(url);
        ResourceLocation bundled = new ResourceLocation("vibe", "cosmetica/" + group + safe(accessory.getId()) + bundledExtension);
        if (hasBundled(bundled)) { loadedTextures.put(key, bundled); return bundled; }
        ResourceLocation location=new ResourceLocation("vibe","cosmetica/"+safe(key));
        try { minecraft.getTextureManager().loadTexture(location,new ThreadDownloadImageData(new File(textures,safe(key)+extension(url)),url,DefaultPlayerSkin.getDefaultSkinLegacy(),null)); } catch(Throwable ignored) { }
        loadedTextures.put(key,location); return location;
    }

    private void queueModel(final CosmeticaAccessory accessory,final File file) {
        synchronized(loadedModels) { if(loadedModels.containsKey(accessory.getId())||!safeUrl(accessory.getModelUrl()))return; loadedModels.put(accessory.getId(),null); }
        worker.submit(new Runnable(){@Override public void run(){try { String source=get(accessory.getModelUrl()); CosmeticaModel model=CosmeticaModel.parse(source); write(file,source); loadedModels.put(accessory.getId(),model); } catch(Exception ignored) { loadedModels.remove(accessory.getId()); } }});
    }

    private List<CosmeticaAccessory> bundledCatalog() {
        List<CosmeticaAccessory> cached = bundledCatalog; if (cached != null) return cached;
        synchronized (this) {
            if (bundledCatalog != null) return bundledCatalog;
            List<CosmeticaAccessory> loaded = new ArrayList<CosmeticaAccessory>();
            String source = readBundled("catalog.json");
            if (source != null) try {
                JsonObject root = new JsonParser().parse(source).getAsJsonObject();
                JsonArray entries = root.has("accessories") && root.get("accessories").isJsonArray() ? root.getAsJsonArray("accessories") : new JsonArray();
                for (JsonElement entry : entries) if (entry.isJsonObject()) { CosmeticaAccessory accessory = CosmeticaAccessory.fromApi(entry.getAsJsonObject()); if (accessory != null) loaded.add(accessory); }
            } catch (Exception ignored) { }
            // During Forge pre-init the mod resource pack may not have been
            // attached yet. Do not permanently cache that early miss; the
            // editor's later search will see the bundled catalogue.
            if (source == null) return Collections.emptyList();
            bundledCatalog = Collections.unmodifiableList(loaded); return bundledCatalog;
        }
    }

    private String readBundled(String path) {
        try {
            IResource resource = minecraft.getResourceManager().getResource(new ResourceLocation("vibe", "cosmetica/" + path));
            BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
            try { StringBuilder result = new StringBuilder(); String line; while ((line = reader.readLine()) != null) result.append(line); return result.toString(); }
            finally { reader.close(); }
        } catch (Exception ignored) { return null; }
    }

    private boolean hasBundled(ResourceLocation location) {
        try { minecraft.getResourceManager().getResource(location); return true; } catch (Exception ignored) { return false; }
    }

    private static String post(String address,String body)throws Exception { HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection(); connection.setConnectTimeout(7000); connection.setReadTimeout(15000); connection.setRequestMethod("POST"); connection.setDoOutput(true); connection.setRequestProperty("Content-Type","application/json; charset=utf-8"); OutputStreamWriter writer=new OutputStreamWriter(connection.getOutputStream(),StandardCharsets.UTF_8); try{writer.write(body);}finally{writer.close();} if(connection.getResponseCode()<200||connection.getResponseCode()>=300)throw new IllegalStateException("HTTP "+connection.getResponseCode()); return read(connection); }
    private static String get(String address)throws Exception { HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection(); connection.setConnectTimeout(7000); connection.setReadTimeout(15000); connection.setRequestProperty("User-Agent","Vibe/0.0.5 Cosmetica catalog renderer"); if(connection.getResponseCode()<200||connection.getResponseCode()>=300)throw new IllegalStateException("HTTP "+connection.getResponseCode()); return read(connection); }
    private static String read(HttpURLConnection connection)throws Exception { BufferedReader reader=new BufferedReader(new InputStreamReader(connection.getInputStream(),StandardCharsets.UTF_8)); StringBuilder output=new StringBuilder(); String line; try{while((line=reader.readLine())!=null)output.append(line);}finally{reader.close();}return output.toString(); }
    private static String read(File file)throws Exception { BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8)); StringBuilder output=new StringBuilder(); String line;try{while((line=reader.readLine())!=null)output.append(line);}finally{reader.close();}return output.toString(); }
    private static void write(File file,String content)throws Exception { File parent=file.getParentFile();if(!parent.isDirectory())parent.mkdirs();OutputStreamWriter writer=new OutputStreamWriter(new FileOutputStream(file),StandardCharsets.UTF_8);try{writer.write(content);}finally{writer.close();} }
    private static String quote(String value){return "\""+value.replace("\\","\\\\").replace("\"","\\\"")+"\"";}
    private static String safe(String value){return value.replaceAll("[^A-Za-z0-9_.-]","_");} private static String extension(String url){int index=url.lastIndexOf('.');return index<0||index<url.lastIndexOf('/')?".png":url.substring(index).replaceAll("[^A-Za-z0-9.]","");}
    private static boolean safeUrl(String value){try{URL url=new URL(value);return "https".equalsIgnoreCase(url.getProtocol())&&url.getHost().toLowerCase(java.util.Locale.ROOT).endsWith("cosmetica.cc");}catch(Exception ignored){return false;}}
    private static String shortMessage(Exception error){String value=error.getMessage();return value==null?"connection error":value.length()>48?value.substring(0,48):value;}
}
