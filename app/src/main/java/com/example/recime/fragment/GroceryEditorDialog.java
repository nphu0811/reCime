package com.example.recime.fragment;
import android.app.Dialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.recime.R;
import com.example.recime.helper.*;
import com.example.recime.model.Grocery;
import com.example.recime.sql.RecipeRepository;
import com.example.recime.viewmodel.GroceryMutationState;

public class GroceryEditorDialog extends DialogFragment {
  private EditText name,amount,unit,category;
  private TextView error;
  private GroceryMutationState state;
  public static GroceryEditorDialog create(Grocery original){
    GroceryEditorDialog dialog=new GroceryEditorDialog();Bundle b=new Bundle();
    if(original!=null){b.putLong("id",original.id);b.putString("name",original.name);
      b.putString("amount",original.amount==null?"":Double.toString(original.amount));b.putString("unit",original.unit);b.putString("category",original.category);}
    dialog.setArguments(b);return dialog;
  }
  @Override public void onCreate(Bundle saved){
    super.onCreate(saved);state=new ViewModelProvider(this).get(GroceryMutationState.class);
    state.outcome.observe(this,result->{
      if(result==null)return;state.outcome.setValue(null);bindBusy();
      if(result.equals("saved")){Toast.makeText(requireContext(),"Đã lưu nguyên liệu",Toast.LENGTH_SHORT).show();dismiss();}
      else if(error!=null){error.setText(result);error.setVisibility(View.VISIBLE);}
    });
  }
  private EditText field(android.content.Context context,LinearLayout form,int id,int hint,String text){
    EditText e=Ui.field(context,form,getString(hint),text);e.setId(id);return e;
  }
  @Override public Dialog onCreateDialog(Bundle saved){
    android.content.Context context=new ContextThemeWrapper(requireContext(),R.style.Theme_PlannerDialog);
    LinearLayout form=new LinearLayout(context);form.setOrientation(LinearLayout.VERTICAL);
    int pad=(int)(16*getResources().getDisplayMetrics().density);form.setPadding(pad,0,pad,0);
    Bundle values=saved==null?requireArguments():saved;
    name=field(context,form,R.id.grocery_name,R.string.grocery_name,values.getString("name",""));
    amount=field(context,form,R.id.grocery_amount,R.string.grocery_amount,values.getString("amount",""));amount.setInputType(8194);
    unit=field(context,form,R.id.grocery_unit,R.string.grocery_unit,values.getString("unit",""));
    category=field(context,form,R.id.grocery_category,R.string.grocery_category,values.getString("category",""));
    error=new TextView(context);error.setId(R.id.grocery_error);error.setVisibility(View.GONE);form.addView(error);
    androidx.appcompat.app.AlertDialog dialog=new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
        .setTitle(requireArguments().getLong("id")==0?"Thêm nguyên liệu":"Sửa nguyên liệu")
        .setView(form).setNegativeButton("Hủy",null).setPositiveButton("Lưu",null).create();
    dialog.setOnShowListener(d->{bindBusy();dialog.getButton(-1).setOnClickListener(v->{
      if(state.busy)return;
      try {
        Grocery draft=new Grocery();draft.id=requireArguments().getLong("id");draft.name=name.getText().toString().trim();
        String text=amount.getText().toString().trim();draft.amount=text.isEmpty()?null:Double.valueOf(text.replace(',','.'));
        draft.unit=Units.canonical(unit.getText().toString());draft.category=category.getText().toString().trim();
        if(draft.category.isEmpty())draft.category=GroceryAggregator.category(draft.name);
        double tsp=Ui.tsp(context),tbsp=Ui.tbsp(context),cup=Ui.cup(context);
        GroceryAggregator.merge(java.util.Collections.singletonList(new com.example.recime.model.Ingredient(draft.name,draft.amount,draft.unit)),tsp,tbsp,cup);
        state.busy=true;bindBusy();GroceryMutationState target=state;
        RecipeRepository repo=RecipeRepository.get(context.getApplicationContext());
        repo.io.execute(()->{
          String result="saved";try{repo.dao.saveManualGrocery(draft,tsp,tbsp,cup);}catch(Exception failure){result="Không thể lưu: "+failure.getMessage();}
          target.busy=false;target.outcome.postValue(result);
        });
      }catch(IllegalArgumentException failure){name.setError("Tên không trống; số lượng không âm, hữu hạn");}
    });});return dialog;
  }
  private void bindBusy(){
    setCancelable(!state.busy);
    if(name!=null)for(EditText field:new EditText[]{name,amount,unit,category})field.setEnabled(!state.busy);
    if(getDialog() instanceof androidx.appcompat.app.AlertDialog){
      androidx.appcompat.app.AlertDialog dialog=(androidx.appcompat.app.AlertDialog)getDialog();
      if(dialog.getButton(-1)!=null){dialog.getButton(-1).setEnabled(!state.busy);dialog.getButton(-2).setEnabled(!state.busy);}
    }
  }
  @Override public void onSaveInstanceState(Bundle saved){
    super.onSaveInstanceState(saved);
    if(name!=null){saved.putString("name",name.getText().toString());saved.putString("amount",amount.getText().toString());
      saved.putString("unit",unit.getText().toString());saved.putString("category",category.getText().toString());}
  }
  @Override public void onDestroyView(){name=null;amount=null;unit=null;category=null;error=null;super.onDestroyView();}
}
