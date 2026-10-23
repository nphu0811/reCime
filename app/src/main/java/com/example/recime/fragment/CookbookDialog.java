package com.example.recime.fragment;
import android.app.Dialog;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.widget.*;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.recime.R;
import com.example.recime.helper.LibraryScope;
import com.example.recime.model.Recipe;
import com.example.recime.sql.RecipeRepository;
import com.example.recime.viewmodel.CookbookState;
import java.util.*;

public class CookbookDialog extends DialogFragment {
  private EditText name;
  private TextView error;
  private CookbookState state;
  private ArrayList<Long> ids = new ArrayList<>();
  private ArrayList<String> labels = new ArrayList<>();
  private boolean[] selected;
  public static CookbookDialog create(String action,String book) {
    CookbookDialog f=new CookbookDialog();Bundle b=new Bundle();b.putString("action",action);b.putString("book",book);f.setArguments(b);return f;
  }
  private String action(){return requireArguments().getString("action");}
  private String book(){return requireArguments().getString("book");}
  @Override public void onCreate(Bundle saved){
    super.onCreate(saved);state=new ViewModelProvider(this).get(CookbookState.class);
    state.outcome.observe(this,result->{
      if(result==null)return;
      state.outcome.setValue(null); bindBusy();
      if(result.startsWith("saved:")){
        Bundle b=new Bundle();b.putString("action",action());b.putString("old",book());b.putString("name",result.substring(6));
        dismiss();getParentFragmentManager().setFragmentResult("cookbook_changed",b);
      }else if(error!=null){error.setText(result);error.setVisibility(android.view.View.VISIBLE);}
    });
    if(saved!=null&&saved.containsKey("ids")){
      long[] values=saved.getLongArray("ids");for(long id:values)ids.add(id);
      labels=saved.getStringArrayList("labels");selected=saved.getBooleanArray("selected");
    }
  }
  @Override public Dialog onCreateDialog(Bundle saved){
    android.content.Context c=new ContextThemeWrapper(requireContext(),R.style.Theme_RecipeEditor);
    RecipeRepository repo=RecipeRepository.get(c);
    LinearLayout form=new LinearLayout(c);form.setOrientation(LinearLayout.VERTICAL);int pad=(int)(16*getResources().getDisplayMetrics().density);form.setPadding(pad,0,pad,0);
    name=new EditText(c);name.setId(R.id.cookbook_name);name.setSingleLine(true);name.setHint("Tên cookbook");
    name.setText(saved==null?(action().equals("create")?"":book()):saved.getString("name",""));
    if(action().equals("create")||action().equals("rename"))form.addView(name);
    if(action().equals("members")){
      if(selected==null){
        Set<Long> existing=repo.dao.recipeIdsForCookbook(book());
        for(Recipe r:repo.dao.recipeSnapshot())if(LibraryScope.includes(r,false)){ids.add(r.id);labels.add(r.title);}
        selected=new boolean[ids.size()];for(int i=0;i<ids.size();i++)selected[i]=existing.contains(ids.get(i));
      }
      ListView list=new ListView(c);list.setId(R.id.cookbook_members);list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
      list.setAdapter(new ArrayAdapter<>(c,android.R.layout.simple_list_item_multiple_choice,labels));
      for(int i=0;i<selected.length;i++)list.setItemChecked(i,selected[i]);
      list.setOnItemClickListener((p,v,pos,id)->selected[pos]=list.isItemChecked(pos));
      form.addView(list,new LinearLayout.LayoutParams(-1,(int)(320*getResources().getDisplayMetrics().density)));
      if(ids.isEmpty()){TextView empty=new TextView(c);empty.setText("Chưa có công thức cá nhân để chọn.");form.addView(empty);}
    }
    if(action().equals("delete")){
      TextView note=new TextView(c);note.setText("Xóa cookbook này? Công thức và các cookbook khác được giữ lại.");form.addView(note);
    }
    error=new TextView(c);error.setVisibility(android.view.View.GONE);form.addView(error);
    androidx.appcompat.app.AlertDialog dialog=new com.google.android.material.dialog.MaterialAlertDialogBuilder(c)
        .setTitle(action().equals("create")?"Tạo cookbook":action().equals("rename")?"Đổi tên cookbook":action().equals("members")?"Chọn công thức":"Xóa cookbook")
        .setView(form).setNegativeButton("Hủy",null).setPositiveButton(action().equals("delete")?"Xóa":"Lưu",null).create();
    dialog.setOnShowListener(d->{bindBusy();dialog.getButton(-1).setOnClickListener(v->{
      if(state.busy)return;
      String value=name.getText().toString().trim();
      if((action().equals("create")||action().equals("rename"))&&value.isEmpty()){name.setError("Tên không được trống");return;}
      Set<Long> selection=new HashSet<>();if(selected!=null)for(int i=0;i<selected.length;i++)if(selected[i])selection.add(ids.get(i));
      state.busy=true;bindBusy();CookbookState target=state;String operation=action(),old=book();
      repo.io.execute(()->{
        String result="saved:"+value;
        try{
          if(operation.equals("create")){
            if(repo.dao.bookNamesSnapshot().stream().anyMatch(n->n.equalsIgnoreCase(value)))throw new IllegalArgumentException("Tên cookbook đã tồn tại");
            repo.dao.createCookbook(value);
          }else if(operation.equals("rename"))repo.dao.renameCookbook(old,value);
          else if(operation.equals("delete"))repo.dao.deleteCookbook(old);
          else repo.dao.selectCookbookRecipes(old,selection);
        }catch(Exception e){result="Không thể lưu: "+e.getMessage();}
        target.busy=false;target.outcome.postValue(result);
      });
    });});return dialog;
  }
  private void bindBusy(){
    setCancelable(!state.busy);if(name!=null)name.setEnabled(!state.busy);
    if(getDialog() instanceof androidx.appcompat.app.AlertDialog){
      androidx.appcompat.app.AlertDialog d=(androidx.appcompat.app.AlertDialog)getDialog();
      if(d.getButton(-1)!=null){d.getButton(-1).setEnabled(!state.busy);d.getButton(-2).setEnabled(!state.busy);}
      android.view.View list=d.findViewById(R.id.cookbook_members);if(list!=null)list.setEnabled(!state.busy);
    }
  }
  @Override public void onSaveInstanceState(Bundle out){
    super.onSaveInstanceState(out);if(name!=null)out.putString("name",name.getText().toString());
    if(selected!=null){long[] values=new long[ids.size()];for(int i=0;i<values.length;i++)values[i]=ids.get(i);
      out.putLongArray("ids",values);out.putStringArrayList("labels",labels);out.putBooleanArray("selected",selected);}
  }
  @Override public void onDestroyView(){name=null;error=null;super.onDestroyView();}
}
