package com.example.recime.helper;

import android.view.*;
import android.view.animation.DecelerateInterpolator;

/** Native Material motion: respects disabled system animation and never blocks interaction. */
public final class Motion {
  private static boolean enabled(View view) {
    return android.provider.Settings.Global.getFloat(
            view.getContext().getContentResolver(),
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f)
        > 0;
  }

  public static void enter(View view) {
    if (!enabled(view)) return;
    view.setAlpha(0f);
    view.setTranslationY(16 * view.getResources().getDisplayMetrics().density);
    view.animate()
        .alpha(1)
        .translationY(0)
        .setDuration(240)
        .setInterpolator(new DecelerateInterpolator())
        .start();
  }

  public static void card(View view) {
    view.setOnTouchListener(
        (v, event) -> {
          if (enabled(v)) {
            boolean down = event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN;
            if (down
                || event.getActionMasked() == android.view.MotionEvent.ACTION_UP
                || event.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL)
              v.animate()
                  .scaleX(down ? .975f : 1f)
                  .scaleY(down ? .975f : 1f)
                  .setDuration(110)
                  .start();
          }
          return false;
        });
  }
}
