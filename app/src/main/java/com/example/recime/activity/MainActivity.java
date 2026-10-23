package com.example.recime.activity;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.*;
import androidx.fragment.app.Fragment;
import com.example.recime.R;
import com.example.recime.fragment.BoardFragment;
import com.example.recime.fragment.ImportFragment;
import com.example.recime.fragment.RecipesFragment;
import com.example.recime.viewmodel.LabViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
  private static final int[] TABS = {R.id.nav_recipes, R.id.nav_plan, R.id.nav_grocery, R.id.nav_discover};
  private int selectedTab = R.id.nav_recipes;
  private final java.util.Map<Integer, Fragment.SavedState> tabStates = new java.util.HashMap<>();
  @Override protected void onSaveInstanceState(Bundle out) {
    out.putInt("selectedTab", selectedTab);
    for (Integer id : tabStates.keySet()) out.putParcelable("tab:" + id, tabStates.get(id));
    super.onSaveInstanceState(out);
  }
  public void openImport() { open(new ImportFragment()); }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    boolean newInstall = !getDatabasePath(com.example.recime.sql.LabDatabase.DATABASE_NAME).exists();
    setContentView(R.layout.activity_main);
    androidx.core.view.WindowInsetsControllerCompat bars = androidx.core.view.WindowCompat
        .getInsetsController(getWindow(), getWindow().getDecorView());
    bars.setAppearanceLightStatusBars(true);
    bars.setAppearanceLightNavigationBars(true);
    if (android.os.Build.VERSION.SDK_INT < 26) getWindow().setNavigationBarColor(0xFF101323);
    ViewCompat.setOnApplyWindowInsetsListener(
        findViewById(R.id.main),
        (v, insets) -> {
          androidx.core.graphics.Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars());
          v.setPadding(b.left, b.top, b.right, b.bottom);
          // Root already applies the system bars; do not add them again inside the dock.
          return WindowInsetsCompat.CONSUMED;
        });
    BottomNavigationView bottom = findViewById(R.id.bottom);
    bottom.setItemActiveIndicatorColor(android.content.res.ColorStateList.valueOf(0xFFEAF1FA));
    if (state != null) {
      selectedTab = state.getInt("selectedTab", R.id.nav_recipes);
      for (int id : TABS) {
        androidx.fragment.app.Fragment.SavedState saved = state.getParcelable("tab:" + id);
        if (saved != null) tabStates.put(id, saved);
      }
    }
    bottom.setOnItemSelectedListener(item -> {
      int target = item.getItemId();
      getSupportFragmentManager().popBackStackImmediate(null,
          androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
      Fragment old = getSupportFragmentManager().findFragmentById(R.id.host);
      if (old != null && old.isAdded()) tabStates.put(selectedTab,
          getSupportFragmentManager().saveFragmentInstanceState(old));
      Fragment next = target == R.id.nav_plan ? BoardFragment.create(true)
          : target == R.id.nav_grocery ? BoardFragment.create(false)
          : RecipesFragment.create(target == R.id.nav_discover);
      if (tabStates.containsKey(target)) next.setInitialSavedState(tabStates.get(target));
      selectedTab = target;
      getSupportFragmentManager().beginTransaction().replace(R.id.host, next).commitNow();
      return true;
    });
    bottom.setOnItemReselectedListener(item -> getSupportFragmentManager()
        .popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE));
    if (state == null) {
      getSupportFragmentManager().beginTransaction()
          .replace(R.id.host, RecipesFragment.create(false)).commitNow();
      if (Intent.ACTION_SEND.equals(getIntent().getAction())) handleShare(getIntent());
      else if (getIntent().hasExtra("cookingRecipe")) open(com.example.recime.fragment.CookingFragment.create(getIntent().getLongExtra("cookingRecipe", 0)));
      else if (newInstall && !getSharedPreferences("onboarding", 0).getBoolean("completed", false))
        open(new com.example.recime.fragment.WelcomeFragment());
    }
    new androidx.lifecycle.ViewModelProvider(this)
        .get(LabViewModel.class)
        .repository
        .message
        .observe(
            this,
            s -> {
              if (s != null) {
                android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show();
                new androidx.lifecycle.ViewModelProvider(this)
                    .get(LabViewModel.class)
                    .repository
                    .message
                    .setValue(null);
              }
            });
    com.example.recime.sql.RecipeRepository repository = com.example.recime.sql.RecipeRepository.get(this);
    final android.content.Context appContext = getApplicationContext();
    repository.io.execute(() -> {
      if (com.example.recime.helper.CloudBackupQueue.pending(appContext)) com.example.recime.helper.CloudBackupQueue.schedule(appContext);
    });
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    handleShare(intent);
    if (intent.hasExtra("cookingRecipe")) open(com.example.recime.fragment.CookingFragment.create(intent.getLongExtra("cookingRecipe", 0)));
  }

  private void handleShare(Intent i) {
    if (!Intent.ACTION_SEND.equals(i.getAction())) return;
    if (getSupportFragmentManager().findFragmentById(R.id.host) == null)
      ((BottomNavigationView) findViewById(R.id.bottom)).setSelectedItemId(R.id.nav_recipes);
    Bundle b = new Bundle();
    b.putString("text", i.getStringExtra(Intent.EXTRA_TEXT));
    android.net.Uri uri = i.getParcelableExtra(Intent.EXTRA_STREAM);
    if (uri != null && "content".equals(uri.getScheme())) b.putString("uri", uri.toString());
    ImportFragment f = new ImportFragment();
    f.setArguments(b);
    open(f);
    // Saved instance state restores the fragment; keep the original intent identity.
  }

  public void open(Fragment f) {
    // Fade is a native resource animation; no web runtime or scripting dependency.
    getSupportFragmentManager()
        .beginTransaction()
        .setCustomAnimations(
            R.anim.recime_slide_up,
            R.anim.recime_fade_out,
            R.anim.recime_slide_down,
            R.anim.recime_fade_out)
        .replace(R.id.host, f)
        .addToBackStack(null)
        .commit();
  }
}
