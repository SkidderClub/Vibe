package dev.vibe.chatter;

import dev.vibe.chatter.ChatterData.*;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;

/** Main-thread chat automation; delays never run network actions on a timer thread. */
public final class ChatterEngine {
    public interface Connection {
        Object identity(); String name(); String server();
        void message(String value); void leave();
    }
    private final ChatterStore store;
    private final SecureRandom random=new SecureRandom();
    private ChatterData data;
    private Object connection;
    private String name="",server="",status="Ready";
    private boolean available=true;
    private final List<Pending> pending=new ArrayList<>();
    private final Set<String> completed=new HashSet<>();
    private final Map<String,Long> lastMatch=new HashMap<>();
    private String lastSent="";private long lastSentAt;
    public ChatterEngine(ChatterStore store) {
        this.store=store;
        try {data=store.load();}catch(IOException error){data=ChatterData.defaults();available=false;status=error.getMessage();}
    }
    public boolean available(){return available;}
    public ChatterData data(){return data;}
    public String status(){return status;}
    public int pending(){return pending.size();}
    public void save() throws IOException {if(!available)throw new IOException(status);store.save(data);pending.clear();status="Saved locally";}
    public void cancel(){pending.clear();completed.clear();lastMatch.clear();connection=null;name=server=lastSent="";}
    private boolean bind(Connection context) {
        if(context==null||context.identity()==null){cancel();return false;}
        String currentName=context.name(),currentServer=normalizeServer(context.server());
        if(connection!=context.identity()||!name.equals(currentName)||!server.equals(currentServer)) {
            cancel();connection=context.identity();name=currentName;server=currentServer;
        }
        return true;
    }
    public void receive(String text,Connection context,long now) {
        if(!available||text==null||text.length()>16384||!bind(context))return;
        text=text.replaceAll("(?i)\u00a7[0-9A-FK-OR]","");
        if(!lastSent.isEmpty()&&now-lastSentAt<5000&&text.contains(lastSent))return;
        for(Rule rule:data.rules) {
            if(!rule.enabled||rule.oncePerConnection&&completed.contains(rule.id)||pending.stream().anyMatch(p->p.rule.id.equals(rule.id)))continue;
            Long previous=lastMatch.get(rule.id);if(previous!=null&&now-previous<rule.cooldown)continue;
            if(matches(rule.trigger,text,name)&&pending.size()<32) {
                pending.add(new Pending(rule.copy(),now+rule.minimum+random.nextInt(rule.maximum-rule.minimum+1)));
                lastMatch.put(rule.id,now);status="Action queued";
            }
        }
    }
    public void tick(Connection context,long now) {
        if(!available||!bind(context))return;
        for(Iterator<Pending> iterator=pending.iterator();iterator.hasNext();) {
            Pending next=iterator.next();if(now<next.due)continue;iterator.remove();
            try {
                if(next.rule.action==Action.LEAVE){context.leave();status="Left server";cancel();return;}
                String output=expand(next.rule.message,name,server);
                if(output.trim().isEmpty()||output.length()>100||!ChatterData.plain(output)) {
                    status="Message not sent: expanded text must contain 1 - 100 valid characters.";continue;
                }
                context.message(output);lastSent=output;lastSentAt=now;completed.add(next.rule.id);status="Message sent";
            } catch(IOException|IllegalArgumentException error){status="Message not sent: could not save or generate the account password.";}
            // One normal chat send per client tick, even when several rules match.
            return;
        }
    }
    public static boolean matches(String pattern,String text,String name) {
        if(pattern==null||text==null)return false;
        String expanded=pattern.replace("%name%",name).toLowerCase(Locale.ROOT);
        String haystack=text.toLowerCase(Locale.ROOT);int position=0;boolean literal=false;
        for(String piece:expanded.split("\\*+",-1)) {
            if(piece.isEmpty())continue;literal=true;int found=haystack.indexOf(piece,position);if(found<0)return false;position=found+piece.length();
        }
        return literal;
    }
    public String expand(String template,String username,String host) throws IOException {
        StringBuilder result=new StringBuilder();String password=null;
        for(int i=0;i<template.length();) {
            String token=null;for(String option:new String[]{"%password%","%pass%","%name%","%s","%n"})if(template.startsWith(option,i)){token=option;break;}
            if(token==null){result.append(template.charAt(i++));continue;}
            if(token.equals("%name%"))result.append(username);
            else if(token.equals("%s"))result.append(randomText("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ",data.generation.lettersLength));
            else if(token.equals("%n"))result.append(randomText("0123456789",data.generation.digitsLength));
            else {if(password==null)password=password(username,host);result.append(password);}
            i+=token.length();
        }
        return result.toString();
    }
    private String randomText(String alphabet,int length){StringBuilder value=new StringBuilder();for(int i=0;i<length;i++)value.append(alphabet.charAt(random.nextInt(alphabet.length())));return value.toString();}
    private String password(String username,String host) throws IOException {
        String currentHost=normalizeServer(host),scope=data.perServer?currentHost:"";
        Password global=null;
        for(Password value:data.passwords)if(value.name.equalsIgnoreCase(username)) {
            if(!value.server.isEmpty()&&value.server.equalsIgnoreCase(currentHost))return value.value;
            if(value.server.isEmpty())global=value;
        }
        if(global!=null)return global.value;
        if(data.passwords.size()>=1000)throw new IOException("Password limit reached.");
        Password value=new Password(username,scope,data.generation.password(random));value.validate();data.passwords.add(value);
        try {if(!available)throw new IOException("Storage unavailable.");store.save(data);}
        catch(IOException|RuntimeException error){data.passwords.remove(value);throw error;}
        return value.value;
    }
    public String examplePassword(Generation generation){return generation.password(random);}
    public static String normalizeServer(String host) {
        String value=host==null?"":host.trim().toLowerCase(Locale.ROOT);
        return value.endsWith(":25565")?value.substring(0,value.length()-6):value;
    }
    private static final class Pending {final Rule rule;final long due;Pending(Rule rule,long due){this.rule=rule;this.due=due;}}
}
