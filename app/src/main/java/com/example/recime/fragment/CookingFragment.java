package com.example.recime.fragment;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.*;
import android.view.*;
import androidx.activity.result.contract.ActivityResultContracts;
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
  private double portions = -1;
  private Recipe selected;
  private final java.util.HashSet<String> checked = new java.util.HashSet<>();
  private final android.os.Handler clock = new android.os.Handler(android.os.Looper.getMainLooper());
  private final Runnable tick = new Runnable() { public void run() { timerLabel(); if (screen != null) clock.postDelayed(this, 1000); } };
  private final androidx.activity.result.ActivityResultLauncher<String> notifications = registerForActivityResult(new ActivityResultContracts.RequestPermission(), allowed -> timerLabel());

  public CookingFragment(){super(R.layout.fragment_cooking);}

  public static CookingFragment create(long recipeId){
    return create(recipeId, -1);
  }
  public static CookingFragment create(long recipeId, double portions){
    CookingFragment f=new CookingFragment();
    Bundle b=new Bundle();b.putLong("recipe",recipeId);b.putDouble("portions", portions);f.setArguments(b);
    return f;
  }
  @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle saved) {
    return inflater.cloneInContext(new ContextThemeWrapper(requireContext(), R.style.Theme_RecipeEditor)).inflate(R.layout.fragment_cooking, parent, false);
  }

  @Override public void onViewCreated(View v,Bundle savedState){
    screen=v;
    Motion.enter(v);
    v.setKeepScreenOn(true);
    if(savedState!=null)index=savedState.getInt("step",0);
    portions = savedState == null ? requireArguments().getDouble("portions", -1) : savedState.getDouble("portions", -1);
    if (savedState != null && savedState.getStringArrayList("checked") != null) checked.addAll(savedState.getStringArrayList("checked"));
    v.findViewById(R.id.cook_timer).setOnClickListener(x -> timerDialog());
    v.findViewById(R.id.cook_timer_cancel).setOnClickListener(x -> { com.example.recime.helper.CookingTimer.cancel(requireContext()); timerLabel(); });
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
          selected = recipe;
          if (portions <= 0) portions = recipe.servings;
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
  @Override public void onResume() { super.onResume(); clock.post(tick); if (screen != null) screen.setKeepScreenOn(true); }
  @Override public void onPause() { clock.removeCallbacks(tick); if (screen != null) screen.setKeepScreenOn(false); super.onPause(); }

  private void timerDialog() {
    EditText minutes = new EditText(requireContext()); minutes.setHint("Số phút (1–1440)"); minutes.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); minutes.setText("10");
    androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
        .setTitle("Hẹn giờ nấu · thay hẹn giờ hiện tại").setView(minutes)
        .setNegativeButton("Hủy", null).setNeutralButton("Quyền giờ chính xác", (d, which) -> {
          if (android.os.Build.VERSION.SDK_INT >= 31) startActivity(new android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:" + requireContext().getPackageName())));
        }).setPositiveButton("Bắt đầu", null).create();
    dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
      try {
        int value = Integer.parseInt(minutes.getText().toString());
        com.example.recime.helper.CookingTimer.start(requireContext(), requireArguments().getLong("recipe"), name, value);
        dialog.dismiss(); timerLabel();
        if (android.os.Build.VERSION.SDK_INT >= 33 && requireContext().checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
          notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS);
      } catch (Exception error) { minutes.setError("Nhập từ 1 đến 1440 phút"); }
    })); dialog.show();
  }
  private void timerLabel() {
    if (screen == null) return;
    long remaining = com.example.recime.helper.CookingTimer.remaining(requireContext());
    android.content.SharedPreferences p = requireContext().getSharedPreferences("cooking_timer", 0);
    String text = remaining < 0 ? "Chưa có hẹn giờ" : remaining == 0 ? "Hết giờ · kiểm tra món ăn" : String.format(java.util.Locale.ROOT, "%02d:%02d", (remaining + 999) / 60000, ((remaining + 999) / 1000) % 60) + " · " + p.getString("title", "");
    if (remaining > 0 && !p.getBoolean("exact", false)) text += "\nThông báo có thể trễ; cấp quyền giờ chính xác nếu cần";
    if (remaining >= 0 && !androidx.core.app.NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()) text += "\nThông báo đang tắt; đồng hồ vẫn chạy trong màn này";
    Ui.set(screen, R.id.cook_timer_status, text);
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
    Ui.set(screen, R.id.cook_servings, com.example.recime.helper.Units.format(portions) + (selected.servingsKnown ? " khẩu phần" : " mẻ công thức") + " · Nguyên liệu");
    LinearLayout ingredients = screen.findViewById(R.id.cook_ingredients); ingredients.removeAllViews();
    int ordinal = 0;
    for (com.example.recime.model.Ingredient item : com.example.recime.helper.RecipeParser.ingredients(selected.ingredients)) {
      String key = (ordinal++) + ":" + item.name;
      CheckBox box = new CheckBox(ingredients.getContext());
      box.setMinHeight((int)(48 * getResources().getDisplayMetrics().density));
      String amount = item.amount == null ? "" : com.example.recime.helper.Units.format(item.amount * portions / selected.servings) + " " + item.unit + " ";
      box.setText(amount + item.name); box.setChecked(checked.contains(key));
      box.setOnCheckedChangeListener((button, on) -> { if (on) checked.add(key); else checked.remove(key); }); ingredients.addView(box);
    }
    Motion.pop(screen.findViewById(R.id.cook_step));
  }

  @Override public void onSaveInstanceState(Bundle out){
    super.onSaveInstanceState(out);out.putInt("step",index);
    out.putDouble("portions", portions); out.putStringArrayList("checked", new ArrayList<>(checked));
  }
  @Override public void onDestroyView(){
    if(screen!=null)screen.setKeepScreenOn(false);
    screen=null;
    super.onDestroyView();
  }
}
