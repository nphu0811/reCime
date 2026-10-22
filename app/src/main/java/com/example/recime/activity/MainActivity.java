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
  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.activity_main);
    ViewCompat.setOnApplyWindowInsetsListener(
        findViewById(R.id.main),
        (v, insets) -> {
          androidx.core.graphics.Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars());
          v.setPadding(b.left, b.top, b.right, b.bottom);
          return insets;
        });
    BottomNavigationView bottom = findViewById(R.id.bottom);
    bottom.setOnItemSelectedListener(
        item -> {
          getSupportFragmentManager()
              .popBackStackImmediate(
                  null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
          Fragment f;
          if (item.getItemId() == R.id.nav_import) f = new ImportFragment();
          else if (item.getItemId() == R.id.nav_plan) f = BoardFragment.create(true);
          else if (item.getItemId() == R.id.nav_grocery) f = BoardFragment.create(false);
          else f = RecipesFragment.create(item.getItemId() == R.id.nav_discover);
          getSupportFragmentManager().beginTransaction().replace(R.id.host, f).commit();
          return true;
        });
    if (state == null) {
      if (Intent.ACTION_SEND.equals(getIntent().getAction())) handleShare(getIntent());
      else bottom.setSelectedItemId(R.id.nav_recipes);
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
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    handleShare(intent);
  }

  private void handleShare(Intent i) {
    if (!Intent.ACTION_SEND.equals(i.getAction())) return;
    ((BottomNavigationView) findViewById(R.id.bottom)).setSelectedItemId(R.id.nav_import);
    Bundle b = new Bundle();
    b.putString("text", i.getStringExtra(Intent.EXTRA_TEXT));
    android.net.Uri uri = i.getParcelableExtra(Intent.EXTRA_STREAM);
    if (uri != null) b.putString("uri", uri.toString());
    ImportFragment f = new ImportFragment();
    f.setArguments(b);
    getSupportFragmentManager().beginTransaction().replace(R.id.host, f).commit();
    // Saved instance state restores the fragment; keep the original intent identity.
  }

  public void open(Fragment f) {
    // Fade is a native resource animation; no web runtime or scripting dependency.
    getSupportFragmentManager()
        .beginTransaction()
        .setCustomAnimations(
            android.R.anim.fade_in,
            android.R.anim.fade_out,
            android.R.anim.fade_in,
            android.R.anim.fade_out)
        .replace(R.id.host, f)
        .addToBackStack(null)
        .commit();
  }
}
