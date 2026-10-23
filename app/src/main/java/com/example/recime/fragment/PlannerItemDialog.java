package com.example.recime.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.recime.R;
import com.example.recime.helper.PlannerWeek;
import com.example.recime.model.Meal;
import com.example.recime.viewmodel.LabViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.*;

/** Pending move/portion fields survive recreation; only confirmation writes the DB. */
public class PlannerItemDialog extends DialogFragment {
  private DatePicker date;
  private Spinner type;
  private EditText servings;
  public static PlannerItemDialog create(Meal meal,boolean move) {
    PlannerItemDialog dialog=new PlannerItemDialog();Bundle b=new Bundle();
    b.putBoolean("move",move);b.putLong("extra",meal.extraId);b.putLong("recipe",meal.recipeId);
    b.putString("slot",meal.slot);b.putString("day",meal.day);b.putString("type",meal.type);
    b.putDouble("servings",meal.servings);dialog.setArguments(b);return dialog;
  }
  @Override public Dialog onCreateDialog(Bundle state) {
    Bundle b=requireArguments();boolean move=b.getBoolean("move");
    android.content.Context context=new ContextThemeWrapper(requireContext(),R.style.Theme_PlannerDialog);
    View form=getLayoutInflater().cloneInContext(context).inflate(R.layout.dialog_plan_item,null);
    date=form.findViewById(R.id.plan_move_date);type=form.findViewById(R.id.plan_move_type);
    servings=form.findViewById(R.id.plan_item_servings);
    Calendar initial=PlannerWeek.parse(b.getString("day"));
    date.init(state==null?initial.get(Calendar.YEAR):state.getInt("year"),
        state==null?initial.get(Calendar.MONTH):state.getInt("month"),
        state==null?initial.get(Calendar.DAY_OF_MONTH):state.getInt("date"),null);
    type.setAdapter(new ArrayAdapter<>(context,android.R.layout.simple_spinner_dropdown_item,PlannerWeek.LABELS));
    type.setSelection(state==null?PlannerWeek.typeIndex(b.getString("type")):state.getInt("type_index"));
    servings.setText(state==null?(b.getDouble("servings")==0?"":String.valueOf(b.getDouble("servings"))):state.getString("quantity"));
    date.setVisibility(move?View.VISIBLE:View.GONE);type.setVisibility(move?View.VISIBLE:View.GONE);
    form.findViewById(R.id.plan_servings_hint).setVisibility(move?View.GONE:View.VISIBLE);
    servings.setVisibility(move?View.GONE:View.VISIBLE);
    androidx.appcompat.app.AlertDialog dialog=new MaterialAlertDialogBuilder(context)
        .setTitle(move?"Chuyển món":"Khẩu phần của món")
        .setView(form).setNegativeButton("Hủy",null).setPositiveButton("Lưu",null).create();
    dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
      try {
        Meal original=new Meal();original.extraId=b.getLong("extra");original.recipeId=b.getLong("recipe");
        original.slot=b.getString("slot");original.day=b.getString("day");original.type=b.getString("type");
        original.servings=b.getDouble("servings");
        Calendar target=Calendar.getInstance();target.set(date.getYear(),date.getMonth(),date.getDayOfMonth(),12,0,0);
        String day=move?PlannerWeek.day(target):original.day;
        String mealType=move?PlannerWeek.TYPES[type.getSelectedItemPosition()]:original.type;
        String text=servings.getText().toString().trim().replace(',','.');
        double quantity=move?original.servings:(text.isEmpty()?0:Double.parseDouble(text));
        PlannerWeek.validate(day,mealType,quantity);
        LabViewModel vm=new ViewModelProvider(requireActivity()).get(LabViewModel.class);
        vm.repository.run(()->{
          if(move)vm.repository.dao.moveMeal(original,day,mealType);
          else vm.repository.dao.editMeal(original,day,mealType,original.recipeId,quantity);
        });
        dialog.getButton(-1).setEnabled(false);dismiss();
      }catch(IllegalArgumentException error){servings.setError("Nhập khẩu phần từ 0 đến 10000");}
    }));
    return dialog;
  }
  @Override public void onSaveInstanceState(Bundle state){
    super.onSaveInstanceState(state);
    if(date!=null){state.putInt("year",date.getYear());state.putInt("month",date.getMonth());state.putInt("date",date.getDayOfMonth());}
    if(type!=null)state.putInt("type_index",type.getSelectedItemPosition());
    if(servings!=null)state.putString("quantity",servings.getText().toString());
  }
}
