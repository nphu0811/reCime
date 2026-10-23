package com.example.recime.fragment;

import com.example.recime.helper.Motion;

import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.activity.result.*;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.recime.*;
import com.example.recime.AIservice.LocalAiClient;
import com.example.recime.activity.MainActivity;
import com.example.recime.helper.PublicWebExtractor;
import com.example.recime.helper.ImportDuplicateChecker;
import com.example.recime.helper.RecipeParser;
import com.example.recime.helper.Ui;
import com.example.recime.viewmodel.LabViewModel;
import com.example.recime.viewmodel.ImportWebState;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.*;
import org.json.*;

public class ImportFragment extends Fragment {
  private View root;
  private String image = "";
  private Uri cameraUri;
  private boolean busy;
  private boolean dishPhoto;
  private ImportWebState webState;
  private com.example.recime.viewmodel.ImportImageState imageState;
  private long appliedImage;
  private long appliedWebFetch;
  private final ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest> picker =
      registerForActivityResult(
          new ActivityResultContracts.PickVisualMedia(),
          uri -> {
            if (uri != null) ocr(uri);
          });
  private final ActivityResultLauncher<Uri> camera =
      registerForActivityResult(
          new ActivityResultContracts.TakePicture(),
          ok -> {
            if (ok && cameraUri != null) ocr(cameraUri);
            else status("Chưa chụp ảnh. Bạn có thể chọn ảnh hoặc dán văn bản.");
          });

  public ImportFragment() {
    super(R.layout.fragment_import);
  }

  @Override
  public void onViewCreated(View v, Bundle state) {
    Motion.enter(v);
    root = v;
    busy = false;
    webState = new ViewModelProvider(this).get(ImportWebState.class);
    imageState = new ViewModelProvider(this).get(com.example.recime.viewmodel.ImportImageState.class);
    if (state != null) {
      appliedWebFetch = state.getLong("appliedWebFetch", 0L);
      appliedImage = state.getLong("appliedImage", 0L);
      image = state.getString("image", "");
      dishPhoto = state.getBoolean("dishPhoto", false);
      String uri = state.getString("camera");
      if (uri != null) cameraUri = Uri.parse(uri);
    }
    if (state != null && state.getBoolean("interrupted") && !webState.isLoading() && !imageState.isLoading())
      status("Thao tác OCR/AI trước đó bị gián đoạn; văn bản nháp vẫn được giữ.");
    v.findViewById(R.id.manual).setOnClickListener(x -> open(null));
    v.findViewById(R.id.ai_settings)
        .setOnClickListener(x -> ((MainActivity) requireActivity()).open(new SettingsFragment()));
    v.findViewById(R.id.ai_pick)
        .setOnClickListener(
            x -> {
              dishPhoto = true;
              picker.launch(
                  new androidx.activity.result.PickVisualMediaRequest.Builder()
                      .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                      .build());
            });
    v.findViewById(R.id.ai_text)
        .setOnClickListener(
            x -> {
              String text = Ui.text(v, R.id.raw);
              if (text.length() < 2) {
                status("Dán caption hoặc mô tả món trước.");
                return;
              }
              aiRequest("text", text);
            });
    v.findViewById(R.id.pick)
        .setOnClickListener(
            x -> {
              dishPhoto = false;
              try {
                picker.launch(
                    new androidx.activity.result.PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build());
              } catch (Exception e) {
                status("Không mở được thư viện: " + e.getMessage());
              }
            });
    v.findViewById(R.id.camera)
        .setOnClickListener(
            x -> {
              new MaterialAlertDialogBuilder(requireContext())
                  .setTitle("Bạn muốn chụp gì?")
                  .setItems(
                      new String[] {"Trang công thức — đọc chữ OCR", "Món ăn — AI gợi ý món"},
                      (dialog, index) -> {
                        dishPhoto = index == 1;
                        try {
                          File dir = new File(requireContext().getCacheDir(), "camera");
                          dir.mkdirs();
                          File file = File.createTempFile("capture", ".jpg", dir);
                          cameraUri =
                              FileProvider.getUriForFile(
                                  requireContext(),
                                  requireContext().getPackageName() + ".files",
                                  file);
                          camera.launch(cameraUri);
                        } catch (Exception e) {
                          status("Camera không khả dụng; hãy chọn ảnh. " + e.getMessage());
                        }
                      })
                  .show();
            });
    v.findViewById(R.id.fetch).setOnClickListener(x -> fetch());
    v.findViewById(R.id.review)
        .setOnClickListener(
            x -> {
              String text = Ui.text(v, R.id.raw);
              if (text.isEmpty()) status("Hãy nhập văn bản hoặc chọn ảnh trước.");
              else open(text);
            });
    if (state == null && getArguments() != null) {
      String text = getArguments().getString("text", "");
      if (text != null) {
        if (text.trim().startsWith("https://") && !text.trim().contains("\n"))
          Ui.set(v, R.id.url, text);
        else Ui.set(v, R.id.raw, text);
      }
      String uri = getArguments().getString("uri");
      if (uri != null) ocr(Uri.parse(uri));
    }
  }

  @Override
  public void onViewStateRestored(Bundle state) {
    super.onViewStateRestored(state);
    // Observe after Android restores EditText state, so a previously applied
    // network result can never overwrite text the user subsequently edited.
    imageState.result.observe(getViewLifecycleOwner(), event -> {
      if (root == null || event == null) return;
      if (event.loading) { loading(true); status("Đang đọc ảnh trên thiết bị…"); return; }
      loading(false);
      if (event.id <= appliedImage) return;
      appliedImage = event.id;
      if (event.error != null) { status(event.error); return; }
      image = event.image;
      if (event.dish) { aiRequest("image", image); return; }
      Ui.set(root, R.id.raw, event.text);
      status(event.text.trim().isEmpty()
          ? "Không tìm thấy chữ. Chọn ảnh rõ hơn hoặc nhập văn bản; chưa có công thức để lưu."
          : "OCR hoàn tất. Kiểm tra dấu tiếng Việt, số lượng và phần thiếu trước khi phân tích.");
    });
    webState.result.observe(getViewLifecycleOwner(), event -> {
      if (root == null || event == null) return;
      if (event.loading) {
        loading(true);
        status("Đang đọc trang công khai…");
        return;
      }
      loading(false);
      if (event.id <= appliedWebFetch) return;
      appliedWebFetch = event.id;
      if (event.error != null) {
        status("Không nhập được: " + event.error + " Có thể sửa URL và thử lại.");
      } else {
        image = "";
        Ui.set(root, R.id.raw, event.text);
        status("Đã đọc Recipe JSON-LD. Kiểm tra văn bản rồi chọn Phân tích.");
      }
    });
  }

  private void open(String raw) {
    if (root == null || busy) return;
    final View target = root;
    final String source = Ui.text(target, R.id.url);
    final String photo = image;
    if (raw == null || raw.trim().isEmpty()) {
      ((MainActivity) requireActivity()).open(EditorFragment.create(0, raw, source, photo));
      return;
    }
    // Read SQLite off the UI thread; do not alter an existing recipe automatically.
    final com.example.recime.sql.RecipeRepository repository =
        new ViewModelProvider(requireActivity()).get(LabViewModel.class).repository;
    loading(true);
    repository.io.execute(() -> {
      com.example.recime.model.Recipe existing = null;
      String error = null;
      try {
        existing = ImportDuplicateChecker.find(
            repository.dao.recipeSnapshot(), RecipeParser.parse(raw), source);
      } catch (Exception e) {
        error = e.getMessage() == null ? "Không đọc được thư viện" : e.getMessage();
      }
      final com.example.recime.model.Recipe duplicate = existing;
      final String failure = error;
      target.post(() -> {
        if (root != target || !isAdded()) return;
        loading(false);
        if (failure != null) {
          status("Không kiểm tra được công thức trùng: " + failure);
          return;
        }
        if (duplicate == null) {
          ((MainActivity) requireActivity())
              .open(EditorFragment.create(0, raw, source, photo));
          return;
        }
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Có thể bạn đã lưu công thức này")
            .setMessage("Đã tìm thấy \"" + duplicate.title
                + "\" trong Recipes. Bạn muốn mở bản cũ hay nhập bản sao?")
            .setNeutralButton("Hủy", null)
            .setNegativeButton("Mở bản đã lưu", (dialog, which) ->
                ((MainActivity) requireActivity()).open(DetailFragment.create(duplicate.id)))
            .setPositiveButton("Nhập bản sao", (dialog, which) ->
                ((MainActivity) requireActivity())
                    .open(EditorFragment.create(0, raw, source, photo)))
            .show();
      });
    });
  }

  private void status(String s) {
    if (root != null) Ui.set(root, R.id.status, s);
  }

  private void loading(boolean on) {
    busy = on;
    if (root == null) return;
    root.findViewById(R.id.progress).setVisibility(on ? View.VISIBLE : View.GONE);
    for (int id :
        new int[] {
          R.id.fetch, R.id.pick, R.id.camera, R.id.review, R.id.manual, R.id.ai_pick, R.id.ai_text,
          R.id.url, R.id.raw
        }) root.findViewById(id).setEnabled(!on);
  }

  private void fetch() {
    if (busy || root == null) return;
    String url = Ui.text(root, R.id.url).trim();
    com.example.recime.helper.ImportProviders.Provider provider =
        com.example.recime.helper.ImportProviders.forUrl(url);
    if (provider.status != com.example.recime.helper.ImportProviders.Status.SUPPORTED) {
      status(provider.name + ": chưa có adapter trích xuất được phép. Dán caption, transcript bạn có quyền sử dụng hoặc chọn ảnh; không đọc nội dung video từ URL.");
      return;
    }
    if (provider.metadataOnly) { aiRequest("tiktok", url); return; }
    com.example.recime.sql.RecipeRepository repository =
        new ViewModelProvider(requireActivity()).get(LabViewModel.class).repository;
    webState.fetch(url, repository.extraction, new PublicWebExtractor());
  }

  private void ocr(Uri uri) {
    if (root == null) return;
    imageState.read(uri, dishPhoto, new ViewModelProvider(requireActivity())
        .get(LabViewModel.class).repository.extraction);
  }

  private void aiRequest(String kind, String input) {
    if (busy || root == null) return;
    final View target = root;
    final android.content.Context context = requireContext().getApplicationContext();
    loading(true);
    status(
        kind.equals("image")
            ? "AI trên laptop đang so khớp ảnh món ăn…"
            : "AI đang đọc caption và tìm công thức có nguồn…");
    new ViewModelProvider(requireActivity())
        .get(LabViewModel.class)
        .repository
        .extraction
        .execute(
            () -> {
              try {
                LocalAiClient client = new LocalAiClient(context);
                JSONObject result =
                    kind.equals("image")
                        ? client.image(new File(input))
                        : kind.equals("tiktok") ? client.tiktok(input) : client.text(input);
                target.post(
                    () -> {
                      if (root != target) return;
                      loading(false);
                      if (result.has("caption"))
                        Ui.set(root, R.id.raw, result.optString("caption"));
                      status(
                          result.optString(
                              "warning",
                              "Chọn món để xem công thức nguồn; luôn kiểm tra biến thể và định"
                                  + " lượng."));
                      if (kind.equals("tiktok")) {
                        status("Đã đọc caption công khai; chưa đọc video/âm thanh. Bổ sung nguyên liệu và các bước còn thiếu, rồi chọn Phân tích. Kết quả tra cứu catalog là công thức khác.");
                      } else showCandidates(result);
                    });
              } catch (Exception e) {
                target.post(
                    () -> {
                      if (root != target) return;
                      loading(false);
                      status(
                          "AI chưa xử lý được: "
                              + e.getMessage()
                              + "\n"
                              + "Kiểm tra kết nối laptop trong Cài đặt AI, hoặc tiếp tục bằng"
                              + " OCR/nhập tay.");
                    });
              }
            });
  }

  private void showCandidates(JSONObject result) {
    JSONArray candidates = result.optJSONArray("candidates");
    if (candidates == null || candidates.length() == 0) {
      status(
          "Chưa có công thức phù hợp trong thư viện. Hãy bổ sung mô tả hoặc dùng OCR; AI không tự"
              + " bịa công thức.");
      return;
    }
    java.util.LinkedHashMap<String, String> options = new java.util.LinkedHashMap<>();
    for (int i = 0; i < candidates.length(); i++) {
      JSONObject candidate = candidates.optJSONObject(i);
      if (candidate == null) continue;
      options.put(candidate.optString("catalogKey"), candidate.optString("title"));
      JSONArray related = candidate.optJSONArray("relatedRecipes");
      if (related != null)
        for (int j = 0; j < Math.min(4, related.length()); j++) {
          JSONObject r = related.optJSONObject(j);
          if (r != null) options.put(r.optString("catalogKey"), r.optString("title"));
        }
    }
    java.util.List<String> keys = new java.util.ArrayList<>(options.keySet());
    String[] titles = options.values().toArray(new String[0]);
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle("Món nào giống ảnh/caption của bạn?")
        .setItems(
            titles,
            (dialog, index) -> {
              final View target = root;
              com.example.recime.sql.RecipeRepository repository =
                  new ViewModelProvider(requireActivity()).get(LabViewModel.class).repository;
              repository.io.execute(
                  () -> {
                    com.example.recime.model.Recipe recipe =
                        repository.dao.findCatalog(keys.get(index));
                    if (target != null)
                      target.post(
                          () -> {
                            if (root != target) return;
                            if (recipe == null) {
                              status(
                                  "Chưa có món này trên điện thoại. Kiểm tra phiên bản dataset giữa"
                                      + " laptop và app.");
                              return;
                            }
                            ((MainActivity) requireActivity())
                                .open(DetailFragment.create(recipe.id));
                          });
                  });
            })
        .setNegativeButton("Chưa đúng / nhập tay", null)
        .show();
  }

  @Override
  public void onSaveInstanceState(Bundle b) {
    super.onSaveInstanceState(b);
    b.putString("image", image);
    b.putBoolean("interrupted", busy && !webState.isLoading() && !imageState.isLoading());
    b.putLong("appliedWebFetch", appliedWebFetch);
    b.putLong("appliedImage", appliedImage);
    b.putBoolean("dishPhoto", dishPhoto);
    if (cameraUri != null) b.putString("camera", cameraUri.toString());
  }

  @Override
  public void onDestroyView() {
    root = null;
    super.onDestroyView();
  }
}
