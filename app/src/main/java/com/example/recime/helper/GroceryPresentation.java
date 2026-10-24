package com.example.recime.helper;
import com.example.recime.model.*;
import java.util.*;

/** Local display rules, independent of unknown official grouping/aggregation algorithms. */
public final class GroceryPresentation {
  private GroceryPresentation(){}
  private static String group(Grocery g,int sort){
    if(sort==1)return "A → Z";
    if(sort==2)return g.planned?"Tự tính từ Meal Planner":"Tự thêm / bản sao độc lập";
    if(sort==3)return g.checked?"Đã mua":"Chưa mua";
    return g.category.isEmpty()?"Khác":g.category;
  }
  public static List<BoardRow> rows(List<Grocery> input,int sort){
    List<Grocery> sorted=new ArrayList<>(input);
    sorted.sort((a,b)->{
      int group=group(a,sort).compareToIgnoreCase(group(b,sort));
      if(sort==2)group=Boolean.compare(a.planned,b.planned);
      if(sort==3)group=Boolean.compare(a.checked,b.checked);
      if(group!=0)return group;
      if(sort!=1&&a.checked!=b.checked)return a.checked?1:-1;
      int name=Units.key(a.name).compareTo(Units.key(b.name));
      if(name!=0)return name;
      int source=Boolean.compare(a.planned,b.planned);
      return source!=0?source:Long.compare(a.id,b.id);
    });
    List<BoardRow> rows=new ArrayList<>();String last=null;
    for(Grocery grocery:sorted){
      String group=group(grocery,sort);
      if(!group.equals(last)){BoardRow h=new BoardRow();h.groupHeader=true;h.title=group;rows.add(h);last=group;}
      BoardRow row=new BoardRow();row.grocery=grocery;row.title=grocery.name;
      String quantity=grocery.amount==null?"Tùy lượng":Units.format(grocery.amount);
      row.meta=quantity+(grocery.unit.isEmpty()?"":" "+grocery.unit)+" · "+(grocery.planned?"Meal Planner":"Tự thêm / bản sao");
      rows.add(row);
    }
    return rows;
  }
  public static String checklist(List<BoardRow> rows){
    StringBuilder text=new StringBuilder("reCime · Danh sách đi chợ\n");
    for(BoardRow row:rows){
      if(row.isHeader())text.append("\n").append(row.title).append("\n");
      else if(row.grocery!=null)text.append(row.grocery.checked?"[x] ":"[ ] ").append(row.title).append(" · ").append(row.meta).append("\n");
    }
    return text.toString();
  }
}
