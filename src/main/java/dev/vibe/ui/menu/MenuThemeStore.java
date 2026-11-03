package dev.vibe.ui.menu;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Local custom themes and sampled shader colours; writes replace files atomically. */
final class MenuThemeStore {
    final List<MenuTheme> custom=new ArrayList<>();final Map<String,Integer> shaderColors=new LinkedHashMap<>();
    private final Path file;
    MenuThemeStore(Path directory){file=directory.resolve("vibe/menu-themes.json");load();}
    private void load(){
        if(!Files.isRegularFile(file))return;
        try(Reader in=Files.newBufferedReader(file,StandardCharsets.UTF_8)){JsonObject root=new JsonParser().parse(in).getAsJsonObject();
            JsonArray themes=root.has("themes")?root.getAsJsonArray("themes"):new JsonArray();
            for(JsonElement value:themes)try{if(custom.size()>=64)break;MenuTheme t=new Gson().fromJson(value,MenuTheme.class);t.validate();if(!t.automatic()&&custom.stream().noneMatch(e->e.id.equals(t.id)))custom.add(t);}catch(RuntimeException invalid){}
            if(root.has("shaderColors"))for(Map.Entry<String,JsonElement> entry:root.getAsJsonObject("shaderColors").entrySet())if(shaderColors.size()<512)try{shaderColors.put(entry.getKey(),entry.getValue().getAsInt()|0xFF000000);}catch(RuntimeException invalid){}
        }catch(IOException|RuntimeException invalid){custom.clear();shaderColors.clear();}
    }
    void save()throws IOException{JsonObject root=new JsonObject();root.addProperty("version",1);root.add("themes",new Gson().toJsonTree(custom));root.add("shaderColors",new Gson().toJsonTree(shaderColors));write(file,new GsonBuilder().setPrettyPrinting().create().toJson(root));}
    static void write(Path file,String text)throws IOException{Files.createDirectories(file.getParent());Path temp=Files.createTempFile(file.getParent(),"menu-",".tmp");try{Files.write(temp,text.getBytes(StandardCharsets.UTF_8));try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}}
}
