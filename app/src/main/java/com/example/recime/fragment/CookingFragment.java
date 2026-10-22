package com.example.recime.fragment;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.recime.R;
import com.example.recime.helper.Motion;
import com.example.recime.helper.Ui;
import com.example.recime.model.Recipe;
import com.example.recime.viewmodel.LabViewModel;
import java.util.ArrayList;
import java.util.List;

/** Distraction-free, stepwise cook-along. Keep screen awake only while visible. */
public final class CookingFragment extends Fragment {
  private final List<String> steps=new ArrayList<>();
  private int index;
  private View screen;
  private String name="";

  public CookingFragment(){super(R.layout.fragment_cooking);}

  public static CookingFragment create(long recipeId){
    CookingFragment f=new CookingFragment();
    Bundle b=new Bundle();b.putLong("recipe",recipeId);f.setArguments(b);
    return f;
  }

  @Override public void onViewCreated(View v,Bundle savedState){
    screen=v;
    Motion.enter(v);
    v.setKeepScreenOn(true);
    if(savedState!=null)index=savedState.getInt("step",0);
    v.findViewById(R.id.cook_back).setOnClickListener(x->getParentFragmentManager().popBackStack());
    v.findViewById(R.id.cook_previous).setOnClickListener(x->{if(index>0)index--;render();});
    v.findViewById(R.id.cook_next).setOnClickListener(x->{
      if(index+1<steps.size())index++;
      else getParentFragmentManager().popBackStack();
      render();
    });
    new ViewModelProvider(requireActivity()).get(LabViewModel.class).recipes.observe(
      getViewLifecycleOwner(),list->{
        for(Recipe recipe:list){
          if(recipe.id!=requireArguments().getLong("recipe"))continue;
          name=recipe.title;
          steps.clear();
          for(String step:recipe.steps.split("\n")){
            String trimmed=step.trim();
            if(!trimmed.isEmpty())steps.add(trimmed.replaceFirst("^\\d+[.)]\\s*",""));
          }
          if(steps.isEmpty())steps.add("Chưa có hướng dẫn, vui lòng quay lại công thức.");
          index=Math.min(index,steps.size()-1);
          render();
          return;
        }
      });
  }

  private void render(){
    if(screen==null || steps.isEmpty())return;
    Ui.set(screen,R.id.cook_title,name);
    Ui.set(screen,R.id.cook_step_number,"BƯỚC "+(index+1)+" / "+steps.size());
    Ui.set(screen,R.id.cook_step,steps.get(index));
    ProgressBar p=screen.findViewById(R.id.cook_progress);
    p.setMax(steps.size());p.setProgress(index+1);
    screen.findViewById(R.id.cook_previous).setEnabled(index>0);
    Ui.set(screen,R.id.cook_next,index+1==steps.size()?"HOÀN THÀNH ✓":"TIẾP →");
    Motion.pop(screen.findViewById(R.id.cook_step));
  }

  @Override public void onSaveInstanceState(Bundle out){
    super.onSaveInstanceState(out);out.putInt("step",index);
  }
  @Override public void onDestroyView(){
    if(screen!=null)screen.setKeepScreenOn(false);
    screen=null;
    super.onDestroyView();
  }
}
