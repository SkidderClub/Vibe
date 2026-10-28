package dev.vibe.easter;

import com.ibm.icu.impl.CalendarAstronomer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Machine-local Gregorian dates; Minecraft's ICU engine supplies lunar/solar events. */
public final class HolidayCalendar {
    public enum Holiday { NONE, HALLOWEEN, CHRISTMAS, NEWYEAR, APRIL }
    // ChineseCalendar itself requires locale bundles absent from icu4j-core-mojang.
    // Its astronomy engine works without those bundles. Use the Chinese calendar's
    // second non-leap new moon after winter solstice rule with the same engine.
    private final CalendarAstronomer astronomy=new CalendarAstronomer();
    private static final long DAY=86400000L,CHINA_OFFSET=8*3600000L;
    private static final Map<Integer,LocalDate> PRECISE_DATES=preciseDates();
    private int lunarYear=Integer.MIN_VALUE;
    private LocalDate lunarDate;
    private LocalDate cached;
    private Holiday result=Holiday.NONE;
    public Holiday at(LocalDate date) {
        if(date.equals(cached))return result;
        cached=date;int month=date.getMonthValue(),day=date.getDayOfMonth();
        if(month==10&&day==31)result=Holiday.HALLOWEEN;
        else if(month==12&&(day==24||day==25))result=Holiday.CHRISTMAS;
        else if(month==12&&day==31||month==1&&day==1||chineseNewYear(date))result=Holiday.NEWYEAR;
        else if(month==4&&day==1)result=Holiday.APRIL;
        else result=Holiday.NONE;
        return result;
    }
    public boolean chineseNewYear(LocalDate date) {
        if(date.getMonthValue()>2)return false;
        // The 2013 astronomy approximation can cross midnight by a few minutes
        // (notably in 2027). Published calendar dates take priority for 1901–2100.
        LocalDate precise=PRECISE_DATES.get(date.getYear());if(precise!=null)return date.equals(precise);
        if(lunarYear!=date.getYear()){
            int year=date.getYear();
            long first=moon(solstice(year-1)+1,true),second=moon(first+25,true);
            long last=moon(solstice(year)+1,false);
            boolean leapSui=Math.round((last-first)/CalendarAstronomer.SYNODIC_MONTH)==12;
            if(leapSui&&(noPrincipalTerm(first)||noPrincipalTerm(second)))second=moon(second+25,true);
            lunarDate=LocalDate.ofEpochDay(second);lunarYear=year;
        }
        return date.equals(lunarDate);
    }
    private long solstice(int year){astronomy.setTime(midnight(LocalDate.of(year,12,1).toEpochDay()));return chinaDay(astronomy.getSunTime(CalendarAstronomer.WINTER_SOLSTICE,true));}
    private long moon(long day,boolean after){astronomy.setTime(midnight(day));return chinaDay(astronomy.getMoonTime(CalendarAstronomer.NEW_MOON,after));}
    private int principalTerm(long day){astronomy.setTime(midnight(day));return (int)Math.floor(astronomy.getSunLongitude()/(Math.PI/6));}
    private boolean noPrincipalTerm(long start){int term=principalTerm(start);return term==principalTerm(moon(start+25,true));}
    private static long midnight(long day){return day*DAY-CHINA_OFFSET;}
    private static long chinaDay(long instant){return Math.floorDiv(instant+CHINA_OFFSET,DAY);}
    private static Map<Integer,LocalDate> preciseDates(){
        Map<Integer,LocalDate> dates=new HashMap<>();
        try(InputStream stream=HolidayCalendar.class.getResourceAsStream("chinese-new-year.txt")){
            if(stream==null)throw new IOException("Missing bundled lunar dates");
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){String line;
                while((line=reader.readLine())!=null)if(!line.isEmpty()&&!line.startsWith("#")){LocalDate date=LocalDate.parse(line);dates.put(date.getYear(),date);}
            }
        }catch(IOException error){throw new IllegalStateException("Unable to read bundled calendar",error);}
        return Collections.unmodifiableMap(dates);
    }
}
