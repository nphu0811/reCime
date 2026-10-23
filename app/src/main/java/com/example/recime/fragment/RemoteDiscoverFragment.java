package com.example.recime.fragment;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.recime.R;
import com.example.recime.viewmodel.DiscoverState;
import org.json.*;

/** Optional server catalog. Each result has a real source and is reviewed before saving. */
public final class RemoteDiscoverFragment extends Fragment {
  @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle saved) {
    android.content.Context c = new ContextThemeWrapper(requireContext(), R.style.Theme_RecipeEditor);
    LinearLayout root = new LinearLayout(c); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xFFFAF8F2);
    int pad = (int)(16 * getResources().getDisplayMetrics().density); root.setPadding(pad, pad, pad, 0);
    Button back = new Button(c); back.setText("← Discover offline"); root.addView(back); back.setOnClickListener(v -> getParentFragmentManager().popBackStack());
    EditText query = new EditText(c); query.setId(R.id.remote_search); query.setSingleLine(true); query.setHint("Tìm catalog backend"); root.addView(query);
    Button search = new Button(c); search.setText("Tìm / thử lại"); root.addView(search);
    TextView status = new TextView(c); status.setId(R.id.remote_status); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); root.addView(status);
    ScrollView scroll = new ScrollView(c); root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
    LinearLayout rows = new LinearLayout(c); rows.setOrientation(LinearLayout.VERTICAL); scroll.addView(rows);
    Button more = new Button(c); more.setText("Tải thêm"); root.addView(more);
    DiscoverState state = new ViewModelProvider(this).get(DiscoverState.class); query.setText(state.query);
    search.setOnClickListener(v -> state.search(query.getText().toString(),false));
    query.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
    query.setOnEditorActionListener((v, action, event) -> { if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) { state.search(query.getText().toString(), false); return true; } return false; });
    more.setOnClickListener(v -> state.search(state.query,true));
    // The viewLifecycleOwner becomes available only after onCreateView returns.
    root.post(() -> {
      if (getView() != root) return;
      state.status.observe(getViewLifecycleOwner(),status::setText);
      state.loading.observe(getViewLifecycleOwner(),busy -> { search.setEnabled(!busy); query.setEnabled(!busy); more.setEnabled(!busy && state.nextOffset >= 0); });
      state.results.observe(getViewLifecycleOwner(),items -> {
        rows.removeAllViews();
        for (JSONObject recipe : items) {
          Button button = new Button(c); button.setAllCaps(false); button.setText(recipe.optString("title") + "\n" + recipe.optString("license")); rows.addView(button);
          button.setOnClickListener(v -> {
            String raw = recipe.optString("title") + "\n" + (recipe.optBoolean("servingsKnown") ? "Servings: " + recipe.optDouble("servings") + "\n" : "")
                + "Attribution: " + recipe.optString("attribution") + " " + recipe.optString("license")
                + "\nIngredients\n" + recipe.optString("ingredients") + "\nInstructions\n" + recipe.optString("steps");
            ((com.example.recime.activity.MainActivity) requireActivity()).open(EditorFragment.create(0, raw, recipe.optString("source"), ""));
          });
        }
      });
    });
    return root;
  }
}
