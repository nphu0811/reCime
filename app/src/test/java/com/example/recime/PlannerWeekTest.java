package com.example.recime;
import static org.junit.Assert.*;
import com.example.recime.helper.PlannerWeek;
import java.util.*;
import org.junit.Test;

public class PlannerWeekTest {
  @Test public void everyStartDayContainsAnchorAcrossYearBoundary(){
    Calendar date=PlannerWeek.parse("2027-01-01");
    for(int first=1;first<=7;first++){
      Calendar start=PlannerWeek.start(date,first);assertEquals(first,start.get(Calendar.DAY_OF_WEEK));
      Calendar end=(Calendar)start.clone();end.add(Calendar.DAY_OF_MONTH,6);
      assertTrue(start.compareTo(date)<=0);assertTrue(end.compareTo(date)>=0);
    }
    assertEquals("2026-12-28",PlannerWeek.day(PlannerWeek.start(date,Calendar.MONDAY)));
    assertEquals("2027-01-01",PlannerWeek.day(date));
  }
  @Test public void nextPreviousWeekAndDstUseCalendarDays(){
    Calendar anchor=Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
    anchor.set(2026,Calendar.MARCH,8,12,0,0);
    Calendar start=PlannerWeek.start(anchor,Calendar.SUNDAY);String original=PlannerWeek.day(start);
    start.add(Calendar.DAY_OF_MONTH,7);assertEquals("2026-03-15",PlannerWeek.day(start));
    start.add(Calendar.DAY_OF_MONTH,-7);assertEquals(original,PlannerWeek.day(start));
    assertEquals(12,start.get(Calendar.HOUR_OF_DAY));
  }
  @Test public void storageTypesKeepLegacyKeysAndEnglishPresentation(){
    assertEquals("Breakfast",PlannerWeek.label("Sáng"));assertEquals("Snack",PlannerWeek.label("Ăn nhẹ"));
    assertEquals("2026-10-19",PlannerWeek.day(PlannerWeek.start(PlannerWeek.parse("2026-10-23"),99)));
  }
  @Test public void invalidMoveInputsFailBeforeAnyWrite(){
    for(String day:new String[]{"2026-02-29","2026-13-01","2026-1-01"})
      assertThrows(IllegalArgumentException.class,()->PlannerWeek.validate(day,"Sáng",2));
    assertThrows(IllegalArgumentException.class,()->PlannerWeek.validate("2026-10-23","Other",2));
    assertThrows(IllegalArgumentException.class,()->PlannerWeek.validate("2026-10-23","Sáng",Double.NaN));
    assertThrows(IllegalArgumentException.class,()->PlannerWeek.validate("2026-10-23","Sáng",-1));
    PlannerWeek.validate("2026-10-23","Sáng",0);
  }
}
