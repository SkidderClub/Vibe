package dev.vibe.easter;

import dev.vibe.easter.HolidayCalendar.Holiday;
import java.time.*;
import java.util.*;

/** Bounded main-thread schedule. Monotonic time owns durations, wall time owns dates and midnight. */
public final class HolidayTimeline {
    public enum Cue { ROCKET, CREEPER }
    public static final List<String> TEST_NAMES=Collections.unmodifiableList(Arrays.asList(
            "halloween","christmas","newyear","chinesenewyear","aprilfools","creeper","console","crash","midnight","auto","off"));
    private final HolidayCalendar calendar;
    private final Random random;
    private Holiday forced,active=Holiday.NONE;
    private boolean started,demo;
    private LocalDate celebrated;
    private long nextRocket,nextCreeper,nextConsole,nextCrash,waveNext,consoleStart,crashStart;
    private long consoleUntil,crashUntil;
    private int waveRemaining;
    private final Deque<Cue> cues=new ArrayDeque<>();
    public HolidayTimeline(HolidayCalendar calendar,Random random){this.calendar=calendar;this.random=random;}
    public Holiday active(){return active;}
    public void refresh(LocalDate date){Holiday next=forced==null?calendar.at(date):forced;if(next!=active){clear();active=next;}}
    public void tick(LocalDateTime wall,long now,boolean inWorld,boolean paused,boolean fullscreen) {
        refresh(wall.toLocalDate());
        if(!inWorld){clear();return;}
        if(paused)return;
        if(!started){
            started=true;nextRocket=now;
            nextCreeper=demo?now:now+minutes(10)+random.nextInt((int)minutes(10)+1);
            nextConsole=now+(demo?2000:minutes(20));nextCrash=now+(demo?4500:minutes(30));demo=false;
        }
        if(active==Holiday.NEWYEAR){
            if(wall.toLocalTime().toSecondOfDay()==0&&!wall.toLocalDate().equals(celebrated)
                    &&(wall.getMonthValue()==1&&wall.getDayOfMonth()==1||calendar.chineseNewYear(wall.toLocalDate()))){
                celebrated=wall.toLocalDate();wave(now);
            }
            if(now>=nextRocket){add(Cue.ROCKET);nextRocket=now+6000+random.nextInt(6001);}
        }
        if(active==Holiday.APRIL){
            if(now>=nextCreeper){add(Cue.CREEPER);nextCreeper=now+minutes(10)+random.nextInt((int)minutes(10)+1);}
            if(now>=nextConsole){consoleStart=now;consoleUntil=now+1600;nextConsole=now+minutes(20);}
            if(now>=nextCrash){if(fullscreen){crashStart=now;crashUntil=now+5000;}nextCrash=now+minutes(30);}
        }
        if(waveRemaining>0&&now>=waveNext){add(Cue.ROCKET);waveRemaining--;waveNext=now+250;}
    }
    public boolean command(String value,LocalDate date,long now,boolean fullscreen) {
        String name=value.trim().toLowerCase(Locale.ROOT).replace(" ","").replace("-","");
        if(name.equals("creeper")){add(Cue.CREEPER);return true;}
        if(name.equals("console")){consoleStart=now;consoleUntil=now+1600;return true;}
        if(name.equals("crash")){if(fullscreen){crashStart=now;crashUntil=now+5000;}return true;}
        if(name.equals("midnight")){wave(now);return true;}
        Holiday next;
        if(name.equals("auto"))next=null;else if(name.equals("off"))next=Holiday.NONE;
        else if(name.equals("halloween"))next=Holiday.HALLOWEEN;else if(name.equals("christmas"))next=Holiday.CHRISTMAS;
        else if(name.equals("newyear")||name.equals("chinesenewyear"))next=Holiday.NEWYEAR;
        else if(name.equals("aprilfools")||name.equals("april"))next=Holiday.APRIL;else return false;
        clear();forced=next;active=forced==null?calendar.at(date):forced;demo=next==Holiday.APRIL;return true;
    }
    private void wave(long now){waveRemaining=32;waveNext=now;}
    private void add(Cue cue){if(cues.size()<16)cues.add(cue);}
    public Cue poll(){return cues.poll();}
    public boolean console(long now){return consoleUntil!=0&&now<consoleUntil;}
    public boolean crash(long now,boolean fullscreen){return fullscreen&&crashUntil!=0&&now<crashUntil;}
    public double consoleProgress(long now){return Math.max(0,Math.min(1,(now-consoleStart)/1600D));}
    public double crashProgress(long now){return Math.max(0,Math.min(1,(now-crashStart)/5000D));}
    public void clear(){started=false;cues.clear();waveRemaining=0;consoleUntil=crashUntil=0;}
    private static long minutes(int minutes){return minutes*60000L;}
}
