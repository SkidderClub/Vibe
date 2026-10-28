package dev.vibe.easter;

import dev.vibe.easter.HolidayCalendar.Holiday;
import java.time.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class HolidayTimelineTest {
    private HolidayTimeline timeline(){return new HolidayTimeline(new HolidayCalendar(),new Random(67));}
    private static LocalDateTime date(int year,int month,int day){return LocalDateTime.of(year,month,day,12,0);}
    @Test public void fixedDatesAndTheirBoundariesAreExact(){
        HolidayCalendar c=new HolidayCalendar();
        assertEquals(Holiday.NONE,c.at(LocalDate.of(2026,10,30)));assertEquals(Holiday.HALLOWEEN,c.at(LocalDate.of(2026,10,31)));assertEquals(Holiday.NONE,c.at(LocalDate.of(2026,11,1)));
        for(int day:new int[]{24,25})assertEquals(Holiday.CHRISTMAS,c.at(LocalDate.of(2026,12,day)));
        assertEquals(Holiday.NONE,c.at(LocalDate.of(2026,12,26)));assertEquals(Holiday.NEWYEAR,c.at(LocalDate.of(2026,12,31)));assertEquals(Holiday.NEWYEAR,c.at(LocalDate.of(2027,1,1)));assertEquals(Holiday.NONE,c.at(LocalDate.of(2027,1,2)));
        assertEquals(Holiday.APRIL,c.at(LocalDate.of(2026,4,1)));assertEquals(Holiday.NONE,c.at(LocalDate.of(2026,4,2)));
    }
    @Test public void lunarNewYearUsesTheChineseCalendarRatherThanAFixedGregorianDate(){
        HolidayCalendar c=new HolidayCalendar();
        for(LocalDate date:Arrays.asList(LocalDate.of(2025,1,29),LocalDate.of(2026,2,17),LocalDate.of(2027,2,6),LocalDate.of(2028,1,26),LocalDate.of(2030,2,3))){
            assertTrue(date.toString(),c.chineseNewYear(date));assertEquals(Holiday.NEWYEAR,c.at(date));assertFalse(c.chineseNewYear(date.plusDays(1)));
        }
    }
    @Test public void normalNewYearHasPeriodicNativeRocketCuesAndAMidnightWave(){
        HolidayTimeline t=timeline();t.tick(date(2026,12,31),0,true,false,true);assertEquals(HolidayTimeline.Cue.ROCKET,t.poll());assertNull(t.poll());
        t.tick(date(2026,12,31),5999,true,false,true);assertNull(t.poll());t.tick(date(2026,12,31),12000,true,false,true);assertEquals(HolidayTimeline.Cue.ROCKET,t.poll());
        LocalDateTime midnight=LocalDateTime.of(2027,1,1,0,0);int rockets=0;
        for(int tick=0;tick<=20;tick++){t.tick(midnight,20000+tick*50,true,false,true);while(t.poll()!=null)rockets++;}
        assertTrue(rockets>=5&&rockets<=6);
    }
    @Test public void lunarMidnightAlsoCelebratesAndMiddayDoesNotStartAHugeWave(){
        HolidayTimeline t=timeline();t.tick(LocalDateTime.of(2026,2,17,0,0),0,true,false,true);
        assertEquals(HolidayTimeline.Cue.ROCKET,t.poll());assertEquals(HolidayTimeline.Cue.ROCKET,t.poll());assertNull(t.poll());
        HolidayTimeline midday=timeline();midday.tick(date(2026,2,17),0,true,false,true);assertEquals(HolidayTimeline.Cue.ROCKET,midday.poll());assertNull(midday.poll());
    }
    @Test public void aprilIntervalsConsoleExpiryAndFiveSecondFullscreenCrashAreBounded(){
        HolidayTimeline t=timeline();LocalDateTime april=date(2026,4,1);t.tick(april,0,true,false,true);
        t.tick(april,599999,true,false,true);assertNull(t.poll());assertFalse(t.console(599999));
        t.tick(april,1200000,true,false,true);assertEquals(HolidayTimeline.Cue.CREEPER,t.poll());assertTrue(t.console(1200000));assertFalse(t.console(1201600));
        t.tick(april,1799999,true,false,true);assertFalse(t.crash(1799999,true));t.tick(april,1800000,true,false,true);
        assertTrue(t.crash(1804999,true));assertFalse(t.crash(1805000,true));assertFalse(t.crash(1800100,false));
        t.tick(april,3600000,true,false,false);assertFalse(t.crash(3600000,true));
    }
    @Test public void hiddenAprilTestCanPreviewAllThreeWithoutWaitingTwentyMinutes(){
        HolidayTimeline t=timeline();LocalDateTime ordinary=date(2026,10,27);assertTrue(t.command("aprilfools",ordinary.toLocalDate(),0,true));
        t.tick(ordinary,0,true,false,true);assertEquals(HolidayTimeline.Cue.CREEPER,t.poll());t.tick(ordinary,2000,true,false,true);assertTrue(t.console(2000));
        t.tick(ordinary,4500,true,false,true);assertTrue(t.crash(4500,true));assertFalse(t.crash(9500,true));
    }
    @Test public void commandsRestoreRealDatesAndTransientTestsDoNotNeedACalendarOverride(){
        HolidayTimeline t=timeline();LocalDate date=LocalDate.of(2026,12,24);
        t.command("halloween",date,0,false);assertEquals(Holiday.HALLOWEEN,t.active());t.command("off",date,0,false);assertEquals(Holiday.NONE,t.active());
        t.command("auto",date,0,false);assertEquals(Holiday.CHRISTMAS,t.active());t.command("crash",date,0,false);assertFalse(t.crash(0,true));
        t.command("console",date,0,false);assertTrue(t.console(1599));assertFalse(t.console(1600));
        t.command("midnight",date,0,false);int count=0;for(int i=0;i<40;i++){t.tick(date.atTime(12,0),i*250,true,false,false);while(t.poll()!=null)count++;}assertEquals(32,count);
    }
    @Test public void pausedAndUnloadedWorldsDoNotBuildUpEvents(){
        HolidayTimeline t=timeline();LocalDateTime april=date(2026,4,1);t.tick(april,0,true,false,true);
        t.tick(april,3600000,true,true,true);assertNull(t.poll());assertFalse(t.console(3600000));
        t.tick(april,3600000,false,false,true);t.tick(april,3600001,true,false,true);assertNull(t.poll());assertFalse(t.crash(3600001,true));
    }
    @Test public void automaticAprilHasNormalDelaysAndNegativeMonotonicOriginsAreValid(){
        HolidayTimeline t=timeline();LocalDateTime april=date(2026,4,1);
        t.command("auto",april.toLocalDate(),-10000,true);t.tick(april,-10000,true,false,true);assertNull(t.poll());assertFalse(t.console(-10000));assertFalse(t.crash(-10000,true));
        t.command("console",april.toLocalDate(),-10000,true);assertTrue(t.console(-8401));assertFalse(t.console(-8400));
    }
}
