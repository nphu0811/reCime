package com.example.recime.helper;

import android.content.*;
import android.view.*;
import android.widget.*;

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
    ImageHelper.load(view, path);
  }

  public static EditText field(Context c, LinearLayout parent, String hint, String text) {
    EditText e = new EditText(c);
    e.setHint(hint);
    e.setText(text);
    parent.addView(e);
    return e;
  }
}
