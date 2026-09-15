package keystrokesmod.script.model;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;

public class Message {
    public ChatComponentText component;
    public Message(String message){component=new ChatComponentText(message);}
    public void appendStyle(String style,String action,String styleMessage,String message){ChatStyle chat=new ChatStyle();try{if("HOVER".equalsIgnoreCase(style))chat.setChatHoverEvent(new HoverEvent(HoverEvent.Action.valueOf(action.toUpperCase(java.util.Locale.ROOT)),new ChatComponentText(styleMessage)));else if("CLICK".equalsIgnoreCase(style))chat.setChatClickEvent(new ClickEvent(ClickEvent.Action.valueOf(action.toUpperCase(java.util.Locale.ROOT)),styleMessage));}catch(Exception ignored){}component.appendSibling(new ChatComponentText(message).setChatStyle(chat));}
    public void append(String value){component.appendSibling(new ChatComponentText(value));} public List<Message> getSiblings(){List<Message> result=new ArrayList<Message>();for(IChatComponent child:component.getSiblings())result.add(new Message(child.getUnformattedTextForChat()));return result;} public String getStyle(){return component.getChatStyle().toString();} public String getText(){return component.getUnformattedTextForChat();} @Override public String toString(){return getText();}
}
