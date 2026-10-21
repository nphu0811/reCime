package com.example.recime.ui;

import android.content.*;
import android.view.*;
import android.widget.*;
import com.example.recime.R;

public final class Ui {
  public static String text(View v, int id) {
    return ((TextView) v.findViewById(id)).getText().toString().trim();
  }

  public static void set(View v, int id, Object value) {
    ((TextView) v.findViewById(id)).setText(value == null ? "" : String.valueOf(value));
  }

  public static android.content.SharedPreferences prefs(Context c) {
    return c.getSharedPreferences("units", 0);
  }

  public static double tsp(Context c) {
    return prefs(c).getFloat("tsp", 5);
  }

  public static double tbsp(Context c) {
    return prefs(c).getFloat("tbsp", 15);
  }

  public static double cup(Context c) {
    return prefs(c).getFloat("cup", 240);
  }

  public static void image(ImageView view, String path) {
    view.setImageResource(R.drawable.food_art);
    if (path == null || path.isEmpty()) return;
    try {
      android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
      options.inJustDecodeBounds = true;
      android.graphics.BitmapFactory.decodeFile(path, options);
      if (options.outWidth <= 0 || options.outHeight <= 0) return;
      options.inSampleSize = 1;
      while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 800)
        options.inSampleSize *= 2;
      options.inJustDecodeBounds = false;
      android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(path, options);
      if (bitmap != null) {
        int orientation = 1;
        try {
          orientation =
              new android.media.ExifInterface(path)
                  .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
        } catch (java.io.IOException ignored) {
        }
        int degrees = orientation == 6 ? 90 : orientation == 3 ? 180 : orientation == 8 ? 270 : 0;
        if (degrees != 0) {
          android.graphics.Matrix matrix = new android.graphics.Matrix();
          matrix.postRotate(degrees);
          android.graphics.Bitmap rotated =
              android.graphics.Bitmap.createBitmap(
                  bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
          if (rotated != bitmap) bitmap.recycle();
          bitmap = rotated;
        }
        view.setImageBitmap(bitmap);
      }
    } catch (Exception ignored) {
      view.setImageResource(R.drawable.food_art);
    }
  }

  public static EditText field(Context c, LinearLayout parent, String hint, String text) {
    EditText e = new EditText(c);
    e.setHint(hint);
    e.setText(text);
    parent.addView(e);
    return e;
  }
}
