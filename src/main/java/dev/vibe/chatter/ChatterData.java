package dev.vibe.chatter;

import java.security.SecureRandom;
import java.util.*;

/** Private Chatter state, deliberately separate from shareable module profiles. */
public final class ChatterData {
    public int version=1;
    public List<Rule> rules=new ArrayList<>();
    public List<Password> passwords=new ArrayList<>();
    public Generation generation=new Generation();
    public boolean perServer=true;

    public static ChatterData defaults() {
        ChatterData data=new ChatterData();
        data.rules.add(new Rule("/register",Action.MESSAGE,"/register %password% %password%",300,600,true));
        data.rules.add(new Rule("/login",Action.MESSAGE,"/login %password%",200,400,true));
        data.rules.add(new Rule("%name%*cheat*?",Action.LEAVE,"",200,400,true));
        return data;
    }
    public void validate() {
        if(version!=1||rules==null||passwords==null||generation==null||rules.size()>64||passwords.size()>1000)
            throw new IllegalArgumentException("Unsupported or oversized Chatter data.");
        generation.validate();Set<String> ids=new HashSet<>(),accounts=new HashSet<>();
        for(Rule rule:rules){if(rule==null)throw new IllegalArgumentException("Invalid rule.");rule.validate();if(!ids.add(rule.id))throw new IllegalArgumentException("Duplicate rule.");}
        for(Password pass:passwords){if(pass==null)throw new IllegalArgumentException("Invalid account.");pass.validate();if(!accounts.add(pass.key()))throw new IllegalArgumentException("Duplicate account.");}
    }
    public enum Action { MESSAGE, LEAVE }
    public static final class Rule {
        public String id=UUID.randomUUID().toString(),trigger="",message="";
        public Action action=Action.MESSAGE;
        public boolean enabled=true,oncePerConnection;
        public int minimum=300,maximum=600,cooldown=2000;
        public Rule() { }
        public Rule(String trigger,Action action,String message,int minimum,int maximum,boolean once) {
            this.trigger=trigger;this.action=action;this.message=message;this.minimum=minimum;this.maximum=maximum;oncePerConnection=once;
        }
        public Rule copy() {
            Rule value=new Rule(trigger,action,message,minimum,maximum,oncePerConnection);
            value.id=id;value.enabled=enabled;value.cooldown=cooldown;return value;
        }
        public void validate() {
            if(id==null||id.length()>64||action==null)throw new IllegalArgumentException("Invalid rule.");
            if(trigger==null||trigger.trim().replace("*","").isEmpty()||trigger.length()>512||!plain(trigger))
                throw new IllegalArgumentException("Enter a trigger containing text, not only *.");
            if(message==null||message.length()>512||!plain(message)||action==Action.MESSAGE&&message.trim().isEmpty())
                throw new IllegalArgumentException("Enter a message to send.");
            if(minimum<0||maximum<minimum||maximum>60000)throw new IllegalArgumentException("Delay must be 0 - 60000 ms, with Min no greater than Max.");
            if(cooldown<1000||cooldown>300000)throw new IllegalArgumentException("Cooldown must be 1000 - 300000 ms.");
        }
    }
    public static final class Generation {
        public int length=16,lettersLength=6,digitsLength=3;
        public boolean lower=true,upper=true,digits=true,symbols,excludeAmbiguous=true;
        public void validate() {
            if(length<4||length>64||lettersLength<1||lettersLength>32||digitsLength<1||digitsLength>16)
                throw new IllegalArgumentException("Password length: 4 - 64. Random letters: 1 - 32. Random digits: 1 - 16.");
            if(!lower&&!upper&&!digits&&!symbols)throw new IllegalArgumentException("Select at least one password character group.");
        }
        public Generation copy() {
            Generation v=new Generation();v.length=length;v.lettersLength=lettersLength;v.digitsLength=digitsLength;
            v.lower=lower;v.upper=upper;v.digits=digits;v.symbols=symbols;v.excludeAmbiguous=excludeAmbiguous;return v;
        }
        public String password(SecureRandom random) {
            validate();List<String> groups=new ArrayList<>();
            if(lower)groups.add(excludeAmbiguous?"abcdefghijkmnopqrstuvwxyz":"abcdefghijklmnopqrstuvwxyz");
            if(upper)groups.add(excludeAmbiguous?"ABCDEFGHJKLMNPQRSTUVWXYZ":"ABCDEFGHIJKLMNOPQRSTUVWXYZ");
            if(digits)groups.add(excludeAmbiguous?"23456789":"0123456789");
            if(symbols)groups.add("!@#$%&*_-+=");
            String alphabet=String.join("",groups);List<Character> result=new ArrayList<>();
            for(String group:groups)result.add(group.charAt(random.nextInt(group.length())));
            while(result.size()<length)result.add(alphabet.charAt(random.nextInt(alphabet.length())));
            Collections.shuffle(result,random);StringBuilder text=new StringBuilder();for(char c:result)text.append(c);return text.toString();
        }
    }
    public static final class Password {
        public String name,server,value;
        public Password(String name,String server,String value){this.name=name;this.server=server;this.value=value;}
        public Password copy(){return new Password(name,server,value);}
        public String key(){return server.toLowerCase(Locale.ROOT)+"\u0000"+name.toLowerCase(Locale.ROOT);}
        public void validate(){
            if(name==null||!name.matches("[A-Za-z0-9_]{1,16}"))throw new IllegalArgumentException("Use a Minecraft username (1 - 16 letters, digits or underscores).");
            if(server==null||server.length()>255||!plain(server))throw new IllegalArgumentException("Enter a valid server address, or leave it empty for all servers.");
            if(value==null||value.length()<1||value.length()>64||!plain(value)||value.indexOf(' ')>=0)
                throw new IllegalArgumentException("Password must contain 1 - 64 characters without spaces.");
        }
    }
    public static boolean plain(String value) {
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c<32||c==127||c=='\u00a7')return false;}return true;
    }
}
