package com.example.recime.helper;

import java.text.*;
import java.util.*;

/** Calendar dates at noon avoid DST midnight gaps; no database dates are rewritten. */
public final class PlannerWeek {
  public static final String[] TYPES={"Sáng","Trưa","Tối","Ăn nhẹ"};
  public static final String[] LABELS={"Breakfast","Lunch","Dinner","Snack"};
  private PlannerWeek() {}
  public static Calendar start(Calendar date,int firstDay) {
    if(firstDay<Calendar.SUNDAY||firstDay>Calendar.SATURDAY)firstDay=Calendar.MONDAY;
    Calendar result=(Calendar)date.clone();
    result.set(Calendar.HOUR_OF_DAY,12);result.set(Calendar.MINUTE,0);
    result.set(Calendar.SECOND,0);result.set(Calendar.MILLISECOND,0);
    result.add(Calendar.DAY_OF_MONTH,-((result.get(Calendar.DAY_OF_WEEK)-firstDay+7)%7));
    return result;
  }
  public static String day(Calendar date){return new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(date.getTime());}
  public static Calendar parse(String day) {
    try {
      if(day==null||!day.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new IllegalArgumentException("Ngày không hợp lệ");
      SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);format.setLenient(false);
      Calendar result=Calendar.getInstance();result.setTime(format.parse(day));result.set(Calendar.HOUR_OF_DAY,12);
      return result;
    }catch(ParseException error){throw new IllegalArgumentException("Ngày không hợp lệ",error);}
  }
  public static int typeIndex(String type){for(int n=0;n<TYPES.length;n++)if(TYPES[n].equals(type)||LABELS[n].equals(type))return n;throw new IllegalArgumentException("Loại bữa không hợp lệ");}
  public static String label(String type){return LABELS[typeIndex(type)];}
  public static void validate(String day,String type,double servings){
    parse(day);typeIndex(type);
    if(!Double.isFinite(servings)||servings<0||servings>10000)throw new IllegalArgumentException("Khẩu phần không hợp lệ");
  }
}
