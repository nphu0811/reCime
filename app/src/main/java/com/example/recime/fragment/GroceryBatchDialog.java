package com.example.recime.fragment;
import android.app.Dialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.text.*;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.recime.R;
import com.example.recime.helper.*;
import com.example.recime.model.*;
import com.example.recime.viewmodel.*;
import java.util.*;

/** Source selection then per-source portions/ingredients, committed in one local transaction. */
public class GroceryBatchDialog extends DialogFragment {
  private GroceryBatchState state;
  private LabViewModel vm;
  private LinearLayout form;
  private TextView error;
  private boolean binding;
  private final List<Source> candidates=new ArrayList<>();
  private static class Source {
    String key,label;Recipe recipe;double portions;
    Source(String key,String label,Recipe recipe,double portions){this.key=key;this.label=label;this.recipe=recipe;this.portions=portions;}
    public String toString(){return label;}
  }
  @Override public void onCreate(Bundle saved){
    super.onCreate(saved);state=new ViewModelProvider(this).get(GroceryBatchState.class);
    vm=new ViewModelProvider(requireActivity()).get(LabViewModel.class);
    vm.recipes.observe(this,recipes->{if(form!=null&&state.stage==0)render();});
    vm.meals.observe(this,meals->{if(form!=null&&state.stage==0&&state.sourceMode==1)render();});
    state.outcome.observe(this,result->{
      if(result==null)return;state.outcome.setValue(null);buttons();
      if(result.equals("saved")){Toast.makeText(requireContext(),"Đã thêm batch độc lập",Toast.LENGTH_SHORT).show();dismiss();}
      else if(error!=null){error.setText(result);error.setVisibility(View.VISIBLE);}
    });
  }
  @Override public Dialog onCreateDialog(Bundle saved){
    android.content.Context context=new ContextThemeWrapper(requireContext(),R.style.Theme_PlannerDialog);
    form=new LinearLayout(context);form.setOrientation(LinearLayout.VERTICAL);
    int padding=(int)(16*getResources().getDisplayMetrics().density);form.setPadding(padding,0,padding,0);
    androidx.appcompat.app.AlertDialog dialog=new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
        .setTitle("Thêm nguyên liệu theo batch").setView(form)
        .setNeutralButton("Quay lại",null).setNegativeButton("Hủy",null).setPositiveButton("Tiếp tục",null).create();
    dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    dialog.setOnShowListener(d->{render();dialog.getButton(-3).setOnClickListener(v->{state.stage=0;render();});
      dialog.getButton(-1).setOnClickListener(v->{
        if(state.busy)return;
        if(state.stage==0){state.stage=1;state.entryIndex=0;render();}
        else commit();
      });});return dialog;
  }
  private void buttons(){
    setCancelable(!state.busy);
    if(form!=null)enable(form,!state.busy);
    if(getDialog() instanceof androidx.appcompat.app.AlertDialog){
      androidx.appcompat.app.AlertDialog d=(androidx.appcompat.app.AlertDialog)getDialog();
      if(d.getButton(-1)==null)return;
      d.getButton(-1).setText(state.busy?"Đang lưu…":state.stage==0?"Tiếp tục":"Thêm batch");
      boolean valid=!state.draft.entries.isEmpty();
      if(state.stage==1)try{state.draft.collect();if(error!=null)error.setVisibility(View.GONE);}
      catch(IllegalArgumentException bad){valid=false;if(error!=null){error.setText(bad.getMessage());error.setVisibility(View.VISIBLE);}}
      d.getButton(-1).setEnabled(valid&&!state.busy);d.getButton(-2).setEnabled(!state.busy);
      d.getButton(-3).setVisibility(state.stage==1?View.VISIBLE:View.GONE);d.getButton(-3).setEnabled(!state.busy);
    }
  }
  private void enable(View view,boolean enabled){
    view.setEnabled(enabled);
    if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++)enable(group.getChildAt(n),enabled);}
  }
  private TextView text(String value){TextView t=new TextView(form.getContext());t.setText(value);form.addView(t);return t;}
  private Spinner spinner(int id,String[] values){
    Spinner spinner=new Spinner(form.getContext(),Spinner.MODE_DROPDOWN);spinner.setId(id);
    spinner.setAdapter(new ArrayAdapter<>(form.getContext(),android.R.layout.simple_spinner_dropdown_item,values));form.addView(spinner);return spinner;
  }
  private ListView list(int id){
    ListView list=new ListView(form.getContext());list.setId(id);list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
    form.addView(list,new LinearLayout.LayoutParams(-1,(int)(230*getResources().getDisplayMetrics().density)));return list;
  }
  private void done(EditText field){
    field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
    field.setOnEditorActionListener((text,action,event)->{
      if(action!=android.view.inputmethod.EditorInfo.IME_ACTION_DONE)return false;
      ((android.view.inputmethod.InputMethodManager)field.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
          .hideSoftInputFromWindow(field.getWindowToken(),0);
      field.clearFocus();return true;
    });
  }
  private TextWatcher change(Runnable action){return new TextWatcher(){
    public void beforeTextChanged(CharSequence s,int a,int c,int d){}
    public void onTextChanged(CharSequence s,int a,int b,int c){if(!binding)action.run();}
    public void afterTextChanged(Editable e){}
  };}
  private void render(){
    if(form==null)return;binding=true;form.removeAllViews();
    text(getString(R.string.grocery_copy_hint));
    if(state.stage==0)sourceForm();else ingredientForm();
    error=text("");error.setId(R.id.grocery_error);error.setVisibility(View.GONE);
    binding=false;buttons();
  }
  private void sourceForm(){
    Spinner mode=spinner(R.id.batch_source_mode,new String[]{"Công thức","Bữa ăn đã lên lịch"});mode.setSelection(state.sourceMode);
    EditText query=new EditText(form.getContext());query.setId(R.id.batch_search);query.setSingleLine(true);
    done(query);query.setHint(R.string.grocery_search);query.setText(state.query);form.addView(query);
    ListView list=list(R.id.batch_sources);
    Runnable filter=()->{
      candidates.clear();List<Recipe> recipes=vm.recipes.getValue();if(recipes==null)recipes=Collections.emptyList();
      if(state.sourceMode==0){for(Recipe r:recipes)if(Units.key(r.title).contains(Units.key(state.query)))candidates.add(new Source("recipe:"+r.id,r.title,r,0));}
      else {
        List<Meal> meals=vm.meals.getValue();if(meals==null)meals=Collections.emptyList();
        for(Meal m:meals)for(Recipe r:recipes)if(r.id==m.recipeId){
          String label=m.day+" · "+m.type+" · "+r.title;
          if(Units.key(label).contains(Units.key(state.query)))candidates.add(new Source(m.extraId>0?"meal:"+m.extraId:"legacy:"+m.slot,label,r,m.servings));
        }
      }
      list.setAdapter(new ArrayAdapter<>(form.getContext(),android.R.layout.simple_list_item_multiple_choice,new ArrayList<>(candidates)));
      for(int n=0;n<candidates.size();n++)list.setItemChecked(n,state.draft.entries.containsKey(candidates.get(n).key));
      buttons();
    };
    mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
      public void onNothingSelected(AdapterView<?> p){}
      public void onItemSelected(AdapterView<?> p,View v,int position,long id){state.sourceMode=position;filter.run();}
    });
    query.addTextChangedListener(change(()->{state.query=query.getText().toString();filter.run();}));filter.run();
    list.setOnItemClickListener((p,v,position,id)->{
      Source source=candidates.get(position);
      if(list.isItemChecked(position)){
        try{state.draft.select(source.key,source.label,source.recipe,source.portions);}
        catch(IllegalArgumentException bad){list.setItemChecked(position,false);Toast.makeText(requireContext(),bad.getMessage(),Toast.LENGTH_LONG).show();}
      }else state.draft.entries.remove(source.key);
      buttons();
    });
  }
  private void ingredientForm(){
    List<GroceryBatchDraft.Entry> entries=new ArrayList<>(state.draft.entries.values());
    if(entries.isEmpty()){state.stage=0;sourceForm();return;}
    state.entryIndex=Math.min(state.entryIndex,entries.size()-1);
    Spinner source=spinner(R.id.batch_entry,entries.stream().map(e->e.label).toArray(String[]::new));source.setSelection(state.entryIndex);
    GroceryBatchDraft.Entry entry=entries.get(state.entryIndex);
    EditText portions=new EditText(form.getContext());portions.setId(R.id.batch_servings);portions.setInputType(8194);
    done(portions);portions.setHint(R.string.grocery_servings);portions.setText(entry.servings);form.addView(portions);
    ListView ingredients=list(R.id.batch_ingredients);
    Runnable refresh=()->{
      List<String> labels=new ArrayList<>();double ratio;
      try{ratio=entry.ratio();}catch(IllegalArgumentException bad){ratio=1;portions.setError("Khẩu phần phải lớn hơn 0");}
      for(Ingredient item:entry.ingredients)labels.add(item.scale(ratio).display());
      ingredients.setAdapter(new ArrayAdapter<>(form.getContext(),android.R.layout.simple_list_item_multiple_choice,labels));
      for(int n=0;n<entry.selected.length;n++)ingredients.setItemChecked(n,entry.selected[n]);buttons();
    };
    portions.addTextChangedListener(change(()->{entry.servings=portions.getText().toString();refresh.run();}));
    ingredients.setOnItemClickListener((p,v,position,id)->{entry.selected[position]=ingredients.isItemChecked(position);buttons();});
    source.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
      public void onNothingSelected(AdapterView<?> p){}
      public void onItemSelected(AdapterView<?> p,View v,int position,long id){if(position!=state.entryIndex){state.entryIndex=position;render();}}
    });refresh.run();
  }
  private void commit(){
    try {
      List<Ingredient> values=state.draft.collect();android.content.Context app=requireContext().getApplicationContext();
      double tsp=Ui.tsp(app),tbsp=Ui.tbsp(app),cup=Ui.cup(app);GroceryAggregator.merge(values,tsp,tbsp,cup);
      state.busy=true;buttons();GroceryBatchState target=state;com.example.recime.sql.RecipeRepository repo=vm.repository;
      repo.io.execute(()->{String result="saved";
        try{repo.dao.addGroceryBatch(values,tsp,tbsp,cup,null);}catch(Exception failure){result="Không thể thêm batch: "+failure.getMessage();}
        target.busy=false;target.outcome.postValue(result);
      });
    }catch(IllegalArgumentException bad){error.setText(bad.getMessage());error.setVisibility(View.VISIBLE);}
  }
  @Override public void onDestroyView(){form=null;error=null;super.onDestroyView();}
}
