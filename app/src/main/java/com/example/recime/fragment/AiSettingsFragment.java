package com.example.recime.fragment;

import com.example.recime.helper.Motion;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import androidx.fragment.app.Fragment;
import com.example.recime.AIservice.LocalAiClient;
import com.example.recime.R;
import com.example.recime.helper.Ui;
import java.util.concurrent.Executors;

public final class AiSettingsFragment extends Fragment {
  public AiSettingsFragment() {
    super(R.layout.fragment_ai_settings);
  }

  @Override
  public void onViewCreated(View view, Bundle state) {
    Motion.enter(view);
    EditText endpoint = view.findViewById(R.id.ai_endpoint);
    endpoint.setText(
        requireContext()
            .getSharedPreferences("ai", 0)
            .getString("endpoint", "http://127.0.0.1:8765"));
    view.findViewById(R.id.back).setOnClickListener(v -> getParentFragmentManager().popBackStack());
    view.findViewById(R.id.ai_check)
        .setOnClickListener(
            v -> {
              String url = endpoint.getText().toString().trim();
              okhttp3.HttpUrl parsed = okhttp3.HttpUrl.parse(url);
              if (parsed == null
                  || parsed.encodedPath().length() > 1
                  || !parsed.username().isEmpty()
                  || !parsed.password().isEmpty()) {
                endpoint.setError("Nhập địa chỉ gốc, ví dụ http://127.0.0.1:8765");
                return;
              }
              requireContext()
                  .getSharedPreferences("ai", 0)
                  .edit()
                  .putString("endpoint", url)
                  .apply();
              v.setEnabled(false);
              Ui.set(view, R.id.ai_health, "Đang kết nối laptop…");
              final android.content.Context context = requireContext().getApplicationContext();
              java.util.concurrent.ExecutorService io = Executors.newSingleThreadExecutor();
              io.execute(
                  () -> {
                    try {
                      org.json.JSONObject result = new LocalAiClient(context).health();
                      view.post(
                          () -> {
                            if (getView() != view) return;
                            v.setEnabled(true);
                            Ui.set(
                                view,
                                R.id.ai_health,
                                "Đã kết nối · "
                                    + result.optInt("recipes")
                                    + " công thức\nModel ảnh: "
                                    + (result.optBoolean("visionModelDownloaded")
                                        ? "sẵn sàng"
                                        : "chưa tải")
                                    + "\nTra cứu công thức cục bộ trên laptop: không mất phí API."
                                    + "\nTrợ lý OpenAI từ xa: "
                                    + (result.optBoolean("assistantAvailable")
                                        ? "đã cấu hình; có thể tính phí theo nhà cung cấp."
                                        : "chưa cấu hình khóa trên backend."));
                          });
                    } catch (Exception e) {
                      view.post(
                          () -> {
                            if (getView() != view) return;
                            v.setEnabled(true);
                            Ui.set(
                                view,
                                R.id.ai_health,
                                "Chưa kết nối: "
                                    + e.getMessage()
                                    + "\n"
                                    + "Kiểm tra server và cáp USB/địa chỉ LAN. Thư viện offline vẫn"
                                    + " hoạt động.");
                          });
                    } finally {
                      io.shutdown();
                    }
                  });
            });
  }
}
