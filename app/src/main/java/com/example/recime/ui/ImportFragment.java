package com.example.recime.ui;

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
import com.example.recime.importing.PublicWebExtractor;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.*;

public class ImportFragment extends Fragment {
  private View root;
  private String image = "";
  private Uri cameraUri;
  private boolean busy;
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
    root = v;
    busy = false;
    if (state != null) {
      image = state.getString("image", "");
      String uri = state.getString("camera");
      if (uri != null) cameraUri = Uri.parse(uri);
    }
    if (state != null && state.getBoolean("interrupted"))
      status("Màn hình đã được tạo lại. Hãy thử lại thao tác nhập ảnh/URL.");
    v.findViewById(R.id.manual).setOnClickListener(x -> open(null));
    v.findViewById(R.id.pick)
        .setOnClickListener(
            x -> {
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
              try {
                File dir = new File(requireContext().getCacheDir(), "camera");
                dir.mkdirs();
                File f = File.createTempFile("capture", ".jpg", dir);
                cameraUri =
                    FileProvider.getUriForFile(
                        requireContext(), requireContext().getPackageName() + ".files", f);
                camera.launch(cameraUri);
              } catch (Exception e) {
                status("Camera không khả dụng; hãy chọn ảnh. " + e.getMessage());
              }
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

  private void open(String raw) {
    ((MainActivity) requireActivity())
        .open(EditorFragment.create(0, raw, Ui.text(root, R.id.url), image));
  }

  private void status(String s) {
    if (root != null) Ui.set(root, R.id.status, s);
  }

  private void loading(boolean on) {
    busy = on;
    if (root == null) return;
    root.findViewById(R.id.progress).setVisibility(on ? View.VISIBLE : View.GONE);
    for (int id : new int[] {R.id.fetch, R.id.pick, R.id.camera, R.id.review, R.id.manual})
      root.findViewById(id).setEnabled(!on);
  }

  private void fetch() {
    if (busy) return;
    String url = Ui.text(root, R.id.url);
    loading(true);
    status("Đang đọc trang công khai…");
    final View target = root;
    new ViewModelProvider(requireActivity())
        .get(LabViewModel.class)
        .repository
        .io
        .execute(
            () -> {
              try {
                String text = new PublicWebExtractor().extract(url);
                target.post(
                    () -> {
                      if (root != target) return;
                      image = "";
                      Ui.set(root, R.id.raw, text);
                      loading(false);
                      status("Đã đọc Recipe JSON-LD. Kiểm tra văn bản rồi chọn Phân tích.");
                    });
              } catch (Exception e) {
                target.post(
                    () -> {
                      if (root != target) return;
                      loading(false);
                      status("Không nhập được: " + e.getMessage() + " Có thể sửa URL và thử lại.");
                    });
              }
            });
  }

  private void ocr(Uri uri) {
    if (root == null) return;
    loading(true);
    status("Đang đọc ảnh trên thiết bị…");
    final View target = root;
    final android.content.Context context = requireContext().getApplicationContext();
    new ViewModelProvider(requireActivity())
        .get(LabViewModel.class)
        .repository
        .io
        .execute(
            () -> {
              try {
                // Keep a private, bounded copy so a picked URI can expire without breaking recipe
                // photos.
                File dir = new File(context.getFilesDir(), "photos");
                dir.mkdirs();
                File file = File.createTempFile("recipe_", ".img", dir);
                try (InputStream in = context.getContentResolver().openInputStream(uri);
                    OutputStream out = new FileOutputStream(file)) {
                  if (in == null) throw new IOException("Không mở được ảnh");
                  byte[] buffer = new byte[8192];
                  int n, total = 0;
                  while ((n = in.read(buffer)) != -1) {
                    total += n;
                    if (total > 20 * 1024 * 1024) throw new IOException("Ảnh vượt giới hạn 20MB");
                    out.write(buffer, 0, n);
                  }
                } catch (Exception e) {
                  file.delete();
                  throw e;
                }
                android.graphics.BitmapFactory.Options options =
                    new android.graphics.BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                if (options.outWidth <= 0 || options.outHeight <= 0)
                  throw new IOException("Định dạng ảnh không hợp lệ");
                options.inSampleSize = 1;
                while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 2200)
                  options.inSampleSize *= 2;
                options.inJustDecodeBounds = false;
                android.graphics.Bitmap bitmap =
                    android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                if (bitmap == null) throw new IOException("Không giải mã được ảnh");
                int rotation = 0;
                try {
                  int orientation =
                      new android.media.ExifInterface(file.getAbsolutePath())
                          .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
                  if (orientation == 6) rotation = 90;
                  else if (orientation == 3) rotation = 180;
                  else if (orientation == 8) rotation = 270;
                } catch (IOException ignored) {
                }
                InputImage input = InputImage.fromBitmap(bitmap, rotation);
                TextRecognizer recognizer =
                    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                recognizer
                    .process(input)
                    .addOnSuccessListener(
                        result -> {
                          if (root == target) {
                            image = file.getAbsolutePath();
                            Ui.set(root, R.id.raw, result.getText());
                            loading(false);
                            status(
                                result.getText().trim().isEmpty()
                                    ? "Không tìm thấy chữ. Thử ảnh rõ hơn hoặc nhập thủ công."
                                    : "OCR hoàn tất. Hãy sửa dấu tiếng Việt, số lượng và phần thiếu"
                                          + " trước khi phân tích.");
                          }
                        })
                    .addOnFailureListener(
                        e -> {
                          if (root == target) {
                            loading(false);
                            status("OCR lỗi: " + e.getMessage() + ". Chọn ảnh khác để thử lại.");
                          }
                        })
                    .addOnCompleteListener(
                        task -> {
                          recognizer.close();
                          bitmap.recycle();
                        });
              } catch (Exception e) {
                target.post(
                    () -> {
                      if (root == target) {
                        loading(false);
                        status("Không đọc được ảnh: " + e.getMessage());
                      }
                    });
              }
            });
  }

  @Override
  public void onSaveInstanceState(Bundle b) {
    super.onSaveInstanceState(b);
    b.putString("image", image);
    b.putBoolean("interrupted", busy);
    if (cameraUri != null) b.putString("camera", cameraUri.toString());
  }

  @Override
  public void onDestroyView() {
    root = null;
    super.onDestroyView();
  }
}
