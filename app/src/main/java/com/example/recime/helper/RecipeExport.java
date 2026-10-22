package com.example.recime.helper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import androidx.core.content.FileProvider;
import com.example.recime.model.Recipe;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Real local PDF output using Android PdfDocument; never uploads recipe data. */
public final class RecipeExport {
  private RecipeExport(){}

  private static List<String> wrap(String source,Paint paint,int width) {
    List<String> lines=new ArrayList<>();
    for(String paragraph:source.split("\n",-1)){
      if(paragraph.trim().isEmpty()){lines.add("");continue;}
      StringBuilder current=new StringBuilder();
      for(String word:paragraph.trim().split("\\s+")){
        String next=current.length()==0?word:current+" "+word;
        if(paint.measureText(next)>width && current.length()>0){
          lines.add(current.toString());current.setLength(0);current.append(word);
        }else {current.setLength(0);current.append(next);}
      }
      if(current.length()>0)lines.add(current.toString());
    }
    return lines;
  }

  public static File pdf(Context context,Recipe recipe) throws IOException{
    File dir=new File(context.getCacheDir(),"exports");
    if(!dir.exists()&&!dir.mkdirs())throw new IOException("Không tạo được thư mục PDF");
    File file=new File(dir,"recime-recipe-"+recipe.id+".pdf");
    PdfDocument pdf=new PdfDocument();
    final int width=595,height=842,left=42,right=42,baseline=52,bottom=790;
    Paint title=new Paint(Paint.ANTI_ALIAS_FLAG);title.setColor(0xff18203d);title.setTextSize(23);title.setFakeBoldText(true);
    Paint body=new Paint(Paint.ANTI_ALIAS_FLAG);body.setColor(0xff25293e);body.setTextSize(12);
    Paint label=new Paint(Paint.ANTI_ALIAS_FLAG);label.setColor(0xff3d5e4c);label.setTextSize(14);label.setFakeBoldText(true);
    List<String> lines=new ArrayList<>();
    lines.add("reCime · Công thức của bạn");
    lines.add(recipe.title);
    lines.add(recipe.servings+" phần · "+recipe.cookbook);
    lines.add("");
    lines.add("NGUYÊN LIỆU");
    for(String s:recipe.ingredients.split("\n"))lines.add("• "+s);
    lines.add("");
    lines.add("CÁCH LÀM");
    int step=1;
    for(String s:recipe.steps.split("\n"))if(!s.trim().isEmpty())lines.add(step+++". "+s);
    lines.add("");
    lines.add("NGUỒN VÀ GHI CÔNG");
    lines.add(recipe.source.isEmpty()?"Công thức tự nhập":recipe.source);
    for(String s:recipe.attribution.split("\n"))lines.add(s);
    if(!recipe.imageAttribution.isEmpty()){
      lines.add("Nguồn ảnh:");
      for(String s:recipe.imageAttribution.split("\n"))lines.add(s);
    }
    int page=0,y=baseline;
    PdfDocument.Page current=null;
    Canvas canvas=null;
    try{
      for(int i=0;i<lines.size();i++){
        String ln=lines.get(i);
        Paint pen=(i==1)?title:((ln.equals("NGUYÊN LIỆU")||ln.equals("CÁCH LÀM")||ln.equals("NGUỒN VÀ GHI CÔNG"))?label:body);
        for(String line:wrap(ln,pen,width-left-right)){
          if(current==null || y>bottom){
            if(current!=null)pdf.finishPage(current);
            current=pdf.startPage(new PdfDocument.PageInfo.Builder(width,height,++page).create());
            canvas=current.getCanvas();y=baseline;
          }
          canvas.drawText(line,left,y,pen);
          y+=pen==title?34:pen==label?26:19;
        }
      }
      if(current!=null)pdf.finishPage(current);
      try(OutputStream out=new FileOutputStream(file)){pdf.writeTo(out);}
    }finally{pdf.close();}
    return file;
  }

  public static void share(Activity activity,Recipe recipe) throws IOException{
    File file=pdf(activity,recipe);
    Uri uri=FileProvider.getUriForFile(activity,
        activity.getPackageName()+".files",file);
    Intent send=new Intent(Intent.ACTION_SEND);
    send.setType("application/pdf");
    send.putExtra(Intent.EXTRA_STREAM,uri);
    send.setClipData(android.content.ClipData.newUri(activity.getContentResolver(),"recipe",uri));
    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    activity.startActivity(Intent.createChooser(send,"Gửi PDF công thức"));
  }

  public static void print(Activity activity,Recipe recipe) throws IOException{
    File file=pdf(activity,recipe);
    PrintManager manager=(PrintManager)activity.getSystemService(Context.PRINT_SERVICE);
    if(manager==null)throw new IOException("Thiết bị không hỗ trợ in");
    manager.print("reCime - "+recipe.title,new PrintDocumentAdapter(){
      @Override public void onLayout(PrintAttributes old,PrintAttributes attrs,
          CancellationSignal signal,LayoutResultCallback callback,android.os.Bundle extras){
        if(signal.isCanceled()){callback.onLayoutCancelled();return;}
        callback.onLayoutFinished(new PrintDocumentInfo.Builder(file.getName())
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(),true);
      }
      @Override public void onWrite(android.print.PageRange[] pages,ParcelFileDescriptor destination,
          CancellationSignal signal,WriteResultCallback callback){
        try(InputStream in=new FileInputStream(file);
            OutputStream out=new FileOutputStream(destination.getFileDescriptor())){
          byte[] buffer=new byte[8192];int n;
          while((n=in.read(buffer))!=-1){
            if(signal.isCanceled()){callback.onWriteCancelled();return;}
            out.write(buffer,0,n);
          }
          callback.onWriteFinished(new android.print.PageRange[]{android.print.PageRange.ALL_PAGES});
        }catch(IOException e){callback.onWriteFailed(e.getMessage());}
      }
    },null);
  }
}
