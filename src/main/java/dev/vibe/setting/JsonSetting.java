package dev.vibe.setting;
import com.google.gson.*;
/** Structured editor data in normal module profiles; edited through its dedicated screen. */
public final class JsonSetting extends Setting<JsonObject>{
    public JsonSetting(String name){super(name,new JsonObject(),()->false);}
    @Override protected JsonObject sanitize(JsonObject value){if(value==null||value.entrySet().size()>512||value.toString().length()>150000)throw new IllegalArgumentException("Editor data is too large");return new JsonParser().parse(value.toString()).getAsJsonObject();}
}
