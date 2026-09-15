package keystrokesmod.script.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Small JSON helper retained verbatim at the Raven API boundary. */
public class Json {
    public enum Type { OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL }
    private final JsonElement element; private final Type type;
    private Json(JsonElement e){element=e; if(e.isJsonObject())type=Type.OBJECT;else if(e.isJsonArray())type=Type.ARRAY;else if(e.isJsonNull())type=Type.NULL;else{JsonPrimitive p=e.getAsJsonPrimitive();type=p.isBoolean()?Type.BOOLEAN:p.isNumber()?Type.NUMBER:Type.STRING;}}
    public static Json parse(String value){return new Json(new JsonParser().parse(value));} public static Json object(){return new Json(new JsonObject());} public static Json array(){return new Json(new JsonArray());} public static Json string(String value){return new Json(new JsonPrimitive(value));} public static Json number(Number value){return new Json(new JsonPrimitive(value));} public static Json booleanValue(boolean value){return new Json(new JsonPrimitive(value));} public static Json nullValue(){return new Json(JsonNull.INSTANCE);} public Type type(){return type;}
    private void objectOnly(){if(type!=Type.OBJECT)throw new IllegalStateException("Not a JSON object: "+type);} private void arrayOnly(){if(type!=Type.ARRAY)throw new IllegalStateException("Not a JSON array: "+type);} private void primitiveOnly(){if(type!=Type.STRING&&type!=Type.NUMBER&&type!=Type.BOOLEAN)throw new IllegalStateException("Not a primitive: "+type);}
    public Json add(String key,Json value){objectOnly();element.getAsJsonObject().add(key,value.element);return this;} public Json add(String key,String value){return add(key,string(value));} public Json add(String key,Number value){return add(key,number(value));} public Json add(String key,boolean value){return add(key,booleanValue(value));} public Json get(String key){objectOnly();JsonElement value=element.getAsJsonObject().get(key);return value==null?nullValue():new Json(value);} public boolean has(String key){objectOnly();return element.getAsJsonObject().has(key);} public Json add(Json value){arrayOnly();element.getAsJsonArray().add(value.element);return this;} public Json add(String value){return add(string(value));} public Json add(Number value){return add(number(value));} public Json add(boolean value){return add(booleanValue(value));}
    public List<Json> asArray(){arrayOnly();List<Json> result=new ArrayList<Json>();for(JsonElement value:element.getAsJsonArray())result.add(new Json(value));return result;} public String asString(){primitiveOnly();return element.getAsString();} public int asInt(){primitiveOnly();return element.getAsInt();} public double asDouble(){primitiveOnly();return element.getAsDouble();} public long asLong(){primitiveOnly();return element.getAsLong();} public float asFloat(){primitiveOnly();return element.getAsFloat();} public boolean asBoolean(){primitiveOnly();return element.getAsBoolean();}
    public LinkedHashSet<String> keys(){objectOnly();LinkedHashSet<String> result=new LinkedHashSet<String>();for(Map.Entry<String,JsonElement> entry:element.getAsJsonObject().entrySet())result.add(entry.getKey());return result;} public Json remove(String key){objectOnly();element.getAsJsonObject().remove(key);return this;} public Json remove(int index){arrayOnly();if(index>=0&&index<element.getAsJsonArray().size()){int current=0;for(Iterator<JsonElement> iterator=element.getAsJsonArray().iterator();iterator.hasNext();current++){iterator.next();if(current==index){iterator.remove();break;}}}return this;} @Override public String toString(){return element.toString();}
}
