package com.example.recime.fragment;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.fragment.app.Fragment;
import com.example.recime.R;

/** Independent welcome; Guest never needs keys or a remote account. */
public final class WelcomeFragment extends Fragment {
  @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle saved) {
    android.content.Context c = new ContextThemeWrapper(requireContext(), R.style.Theme_RecipeEditor);
    ScrollView scroll = new ScrollView(c); scroll.setBackgroundColor(0xFFFAF8F2);
    LinearLayout content = new LinearLayout(c); content.setOrientation(LinearLayout.VERTICAL); int pad = (int)(24 * getResources().getDisplayMetrics().density); content.setPadding(pad, pad, pad, pad); scroll.addView(content);
    TextView title = new TextView(c); title.setText("Chào mừng đến reCime"); title.setTextSize(30); content.addView(title);
    TextView text = new TextView(c); text.setText("Lưu công thức của bạn, lên lịch 7 ngày và chuẩn bị giỏ đi chợ.\n\nNhập từ website có Recipe JSON-LD, ảnh có chữ hoặc văn bản bạn có quyền sử dụng. Luôn kiểm tra trước khi lưu.\n\nGuest hoạt động offline. Backend riêng và các API tùy chọn chỉ được dùng khi bạn cấu hình và chọn tính năng tương ứng. Đây là ứng dụng độc lập, không dùng tài khoản ReciMe chính thức."); text.setTextSize(18); text.setPadding(0, pad, 0, pad); content.addView(text);
    Button guest = new Button(c); guest.setText("Bắt đầu / Tiếp tục Guest"); guest.setMinHeight((int)(48 * getResources().getDisplayMetrics().density)); content.addView(guest);
    guest.setOnClickListener(v -> {
      requireContext().getSharedPreferences("onboarding", 0).edit().putBoolean("completed", true).apply();
      getParentFragmentManager().popBackStack();
    });
    Button account = new Button(c); account.setText("Đăng nhập / Cấu hình backend riêng"); content.addView(account);
    account.setOnClickListener(v -> ((com.example.recime.activity.MainActivity) requireActivity()).open(new SettingsFragment()));
    return scroll;
  }
}
