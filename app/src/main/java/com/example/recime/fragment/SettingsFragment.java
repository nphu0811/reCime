package com.example.recime.fragment;

import android.os.Bundle;
import android.net.Uri;
import android.view.*;
import android.widget.EditText;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.recime.R;
import com.example.recime.AIservice.AccountClient;
import com.example.recime.helper.*;
import com.example.recime.sql.RecipeRepository;
import com.example.recime.viewmodel.SettingsState;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public final class SettingsFragment extends Fragment {
  private SettingsState state;
  private final int[] operations = {R.id.settings_connect, R.id.settings_login, R.id.settings_register,
      R.id.settings_profile, R.id.settings_logout, R.id.settings_export, R.id.settings_restore,
      R.id.settings_upload, R.id.settings_download, R.id.settings_cancel_pending, R.id.settings_delete};
  private final ActivityResultLauncher<String> create = registerForActivityResult(
      new ActivityResultContracts.CreateDocument("application/json"), uri -> { if (uri != null) export(uri); });
  private final ActivityResultLauncher<String[]> restore = registerForActivityResult(
      new ActivityResultContracts.OpenDocument(), uri -> { if (uri != null) restore(uri); });
  @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle saved) {
    return inflater.cloneInContext(new ContextThemeWrapper(requireContext(), R.style.Theme_RecipeEditor)).inflate(R.layout.fragment_settings, parent, false);
  }
  @Override public void onViewCreated(View v, Bundle saved) {
    state = new ViewModelProvider(this).get(SettingsState.class);
    if (saved == null) Ui.set(v, R.id.settings_endpoint, requireContext().getSharedPreferences("ai", 0).getString("endpoint", "http://127.0.0.1:8765"));
    state.status.observe(getViewLifecycleOwner(), value -> { Ui.set(v, R.id.settings_status, value); identity(v); });
    state.busy.observe(getViewLifecycleOwner(), value -> { for (int id : operations) v.findViewById(id).setEnabled(!Boolean.TRUE.equals(value)); identity(v); });
    v.findViewById(R.id.settings_back).setOnClickListener(x -> getParentFragmentManager().popBackStack());
    v.findViewById(R.id.settings_connect).setOnClickListener(x -> {
      String endpoint = Ui.text(v, R.id.settings_endpoint).trim().replaceAll("/+$", "");
      state.run(c -> {
        String old = c.getSharedPreferences("ai", 0).getString("endpoint", "http://127.0.0.1:8765");
        c.getSharedPreferences("ai", 0).edit().putString("endpoint", endpoint).commit();
        try { AccountClient.endpoint(c); } catch (Exception error) { c.getSharedPreferences("ai", 0).edit().putString("endpoint", old).commit(); throw error; }
        JSONObject health = new AccountClient(c).request("GET", "/health", null);
        return "Đã kết nối · " + health.optInt("recipes") + " công thức\nOpenAI: " + health.optBoolean("assistantAvailable")
            + " · USDA: " + health.optBoolean("nutritionAvailable") + "\nGoogle Login: chưa tích hợp; dùng email/password hoặc Guest";
      });
    });
    v.findViewById(R.id.settings_login).setOnClickListener(x -> login(v, false));
    v.findViewById(R.id.settings_register).setOnClickListener(x -> login(v, true));
    v.findViewById(R.id.settings_logout).setOnClickListener(x -> state.run(c -> {
      try { new AccountClient(c).logout(); return "Đã thu hồi phiên đăng nhập trên server; dữ liệu local được giữ"; }
      catch (Exception offline) { return "Đã xóa phiên trên thiết bị; server chưa xác nhận đăng xuất do mất kết nối. Phiên server hết hạn sau 7 ngày."; }
    }));
    v.findViewById(R.id.settings_profile).setOnClickListener(x -> {
      EditText name = new EditText(v.getContext()); name.setHint("Tên hiển thị");
      new MaterialAlertDialogBuilder(v.getContext()).setTitle("Hồ sơ").setView(name).setNegativeButton("Hủy", null)
          .setPositiveButton("Lưu", (d, which) -> { String value = name.getText().toString().trim(); state.run(c -> "Đã lưu hồ sơ: " + new AccountClient(c).request("PATCH", "/v1/account", new JSONObject().put("name", value)).getString("name")); }).show();
    });
    v.findViewById(R.id.settings_export).setOnClickListener(x -> create.launch("recime-backup-" + System.currentTimeMillis() + ".json"));
    v.findViewById(R.id.settings_restore).setOnClickListener(x -> new MaterialAlertDialogBuilder(v.getContext()).setTitle("Khôi phục bổ sung")
        .setMessage("Tệp phải do reCime xuất, tối đa 32MB. Công thức cá nhân được thêm thành bản sao, cookbook được hợp nhất. Không xóa dữ liệu hiện có. Giữ đơn vị đo hiện tại.")
        .setNegativeButton("Hủy", null).setPositiveButton("Chọn tệp", (d, which) -> restore.launch(new String[] {"application/json", "application/octet-stream"})).show());
    v.findViewById(R.id.settings_upload).setOnClickListener(x -> state.run(c -> {
      if (!CloudBackupQueue.pending(c)) CloudBackupQueue.stage(c, RecipeRepository.get(c).db);
      CloudBackupQueue.schedule(c);
      return CloudBackupQueue.retry(c);
    }));
    v.findViewById(R.id.settings_cancel_pending).setOnClickListener(x -> new MaterialAlertDialogBuilder(v.getContext()).setTitle("Hủy backup đang chờ?")
        .setMessage("Chỉ bỏ yêu cầu gửi; giữ toàn bộ dữ liệu local và cloud đã lưu.").setNegativeButton("Giữ", null)
        .setPositiveButton("Hủy bản chờ", (d, which) -> state.run(c -> { CloudBackupQueue.cancel(c); return "Đã hủy bản chờ"; })).show());
    v.findViewById(R.id.settings_download).setOnClickListener(x -> new MaterialAlertDialogBuilder(v.getContext()).setTitle("Hợp nhất cloud backup?")
        .setMessage("Thêm công thức bản sao, cookbook, lịch, groceries và ảnh từ cloud. Giữ dữ liệu đang có; cùng backup sẽ chỉ nhập một lần.")
        .setNegativeButton("Hủy", null).setPositiveButton("Tải và hợp nhất", (d, which) -> state.run(c -> "Đã hợp nhất " + CloudBackupQueue.downloadAndMerge(c, RecipeRepository.get(c).db) + " công thức; dữ liệu cũ được giữ")).show());
    v.findViewById(R.id.settings_units).setOnClickListener(x -> {
      new MaterialAlertDialogBuilder(v.getContext()).setTitle("Hiển thị đơn vị trong chi tiết")
          .setSingleChoiceItems(new String[] {"Giữ đơn vị công thức", "Quy đổi metric khi biết lượng"}, requireContext().getSharedPreferences("units", 0).getBoolean("metric", false) ? 1 : 0,
              (dialog, which) -> { requireContext().getSharedPreferences("units", 0).edit().putBoolean("metric", which == 1).apply(); dialog.dismiss(); }).setNegativeButton("Đóng", null).show();
    });
    v.findViewById(R.id.settings_privacy).setOnClickListener(x -> new MaterialAlertDialogBuilder(v.getContext()).setTitle("Dữ liệu và quyền riêng tư")
        .setMessage("Chế độ Guest lưu SQLite và ảnh trong vùng riêng ứng dụng. OCR chạy trên thiết bị bằng ML Kit; cần kiểm tra dấu tiếng Việt. Website import kết nối tới URL công khai bạn nhập. AI ảnh/tra cứu gửi nội dung tới backend bạn cấu hình; OpenAI/USDA tùy chọn có thể nhận nội dung khi bạn chọn dùng. Backup tệp chứa ảnh và dữ liệu cá nhân; chỉ chia sẻ với bên bạn tin tưởng. Không thu thập analytics hoặc crash report. Quyền Photo Picker chỉ cho ảnh bạn chọn. Google Login/collaboration chưa được tích hợp.")
        .setPositiveButton("Đã hiểu", null).show());
    v.findViewById(R.id.settings_welcome).setOnClickListener(x -> ((com.example.recime.activity.MainActivity) requireActivity()).open(new WelcomeFragment()));
    v.findViewById(R.id.settings_delete).setOnClickListener(x -> {
      EditText password = new EditText(v.getContext()); password.setHint("Xác nhận mật khẩu"); password.setInputType(129); password.setSaveEnabled(false);
      new MaterialAlertDialogBuilder(v.getContext()).setTitle("Xóa tài khoản và toàn bộ dữ liệu cloud?")
          .setMessage("Xóa profile, phiên, cloud records và backup trên backend riêng. Giữ dữ liệu trên điện thoại. Không thể hoàn tác dữ liệu cloud.")
          .setView(password).setNegativeButton("Hủy", null).setPositiveButton("Xóa cloud", (d, which) -> {
            String value = password.getText().toString(); password.setText(""); state.run(c -> {
              new AccountClient(c).request("DELETE", "/v1/account", new JSONObject().put("password", value));
              new SessionStore(c).clear(); CloudBackupQueue.cancel(c); return "Đã xóa tài khoản cloud; thiết bị tiếp tục ở Guest";
            });
          }).show();
    });
  }
  private void identity(View v) {
    SessionStore session = new SessionStore(requireContext());
    boolean signedIn;
    try { signedIn = session.belongsTo(AccountClient.endpoint(requireContext())); } catch (Exception invalid) { signedIn = false; }
    Ui.set(v, R.id.settings_identity, (signedIn ? session.email() : "Guest / Local-only")
        + (CloudBackupQueue.pending(requireContext()) ? "\nCó cloud backup đang chờ gửi" : ""));
  }
  private void login(View v, boolean register) {
    String email = Ui.text(v, R.id.settings_email), password = Ui.text(v, R.id.settings_password);
    Ui.set(v, R.id.settings_password, "");
    state.run(c -> "Đã đăng nhập: " + new AccountClient(c).login(email, password, register).getString("email") + "\nCloud backup có thể sử dụng; thư viện local vẫn độc lập.");
  }
  private void export(Uri uri) {
    state.run(c -> {
      byte[] bytes = BackupStore.export(c, RecipeRepository.get(c).db).toString().getBytes(StandardCharsets.UTF_8);
      try (OutputStream out = c.getContentResolver().openOutputStream(uri, "wt")) {
        if (out == null) throw new IOException("Không mở được tệp đích"); out.write(bytes);
      }
      return "Đã xuất backup có checksum, ảnh riêng, công thức, cookbooks, lịch và groceries";
    });
  }
  private void restore(Uri uri) {
    state.run(c -> {
      JSONObject envelope;
      try (InputStream in = c.getContentResolver().openInputStream(uri)) { envelope = new JSONObject(new String(BackupStore.read(in, BackupStore.MAX_BYTES), StandardCharsets.UTF_8)); }
      int count = BackupStore.restore(c, RecipeRepository.get(c).db, envelope);
      return "Đã khôi phục bổ sung " + count + " công thức; dữ liệu cũ và tùy chọn được giữ";
    });
  }
}
