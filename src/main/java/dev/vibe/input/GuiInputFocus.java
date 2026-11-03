package dev.vibe.input;

import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.gui.GuiTextField;

/** Checks native and Vibe text controls without consuming any keyboard events. */
public final class GuiInputFocus {
    private static final Map<Class<?>,List<Field>> FIELDS=new HashMap<>();
    private GuiInputFocus(){}
    public static boolean typing(Object view){
        if(view==null)return false;
        for(Field f:FIELDS.computeIfAbsent(view.getClass(),GuiInputFocus::fields))try{
            Object value=f.get(view);
            if(value instanceof GuiTextField&&((GuiTextField)value).isFocused())return true;
            String name=f.getName();
            if((name.equals("editing")||name.equals("binding")||name.equals("bindingModule"))&&value!=null)return true;
            if((name.equals("searching")||name.equals("creating"))&&Boolean.TRUE.equals(value))return true;
        }catch(IllegalAccessException ignored){}
        return false;
    }
    private static List<Field> fields(Class<?> type){
        List<Field> result=new ArrayList<>();
        for(Class<?> c=type;c!=null&&c!=Object.class;c=c.getSuperclass())for(Field f:c.getDeclaredFields()){
            String n=f.getName();if(java.lang.reflect.Modifier.isStatic(f.getModifiers()))continue;
            if(GuiTextField.class.isAssignableFrom(f.getType())||n.equals("editing")||n.equals("binding")||n.equals("bindingModule")||n.equals("searching")||n.equals("creating")){f.setAccessible(true);result.add(f);}
        }
        return result;
    }
}
