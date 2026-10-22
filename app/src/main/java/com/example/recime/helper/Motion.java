package com.example.recime.helper;

import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * Lightweight native micro-interactions. No animation library, Compose or WebView.
 * Animator duration scale = 0 disables all our explicit view animations.
 */
public final class Motion {
  private Motion() {}

  public static boolean enabled(View view) {
    try {
      return android.provider.Settings.Global.getFloat(
          view.getContext().getContentResolver(),
          android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
          1f) > 0f;
    } catch (Exception ignored) {
      return true;
    }
  }

  private static float dp(View view, float amount) {
    return view.getResources().getDisplayMetrics().density * amount;
  }

  /** One-time cinematic entrance for an entire screen. */
  public static void enter(View view) {
    if (!enabled(view)) return;
    view.setAlpha(0f);
    view.setTranslationY(dp(view, 22));
    view.animate()
        .alpha(1f)
        .translationY(0f)
        .setDuration(420)
        .setInterpolator(new DecelerateInterpolator(1.6f))
        .start();
  }

  /** Offset reveal for GridView items, only invoked for newly inflated cells. */
  public static void stagger(View view, int position) {
    if (!enabled(view)) return;
    view.setAlpha(0f);
    view.setTranslationY(dp(view, 24));
    view.setScaleX(.94f);
    view.setScaleY(.94f);
    view.animate()
        .alpha(1f)
        .translationY(0f)
        .scaleX(1f)
        .scaleY(1f)
        .setStartDelay(Math.min(position % 8, 7) * 35L)
        .setDuration(360)
        .setInterpolator(new OvershootInterpolator(.65f))
        .start();
  }

  /** Pop-in the editorial hero without continuous or battery-intensive animation. */
  public static void pop(View view) {
    if (!enabled(view)) return;
    view.setScaleX(.93f);
    view.setScaleY(.93f);
    view.setAlpha(.65f);
    view.animate()
        .scaleX(1f)
        .scaleY(1f)
        .alpha(1f)
        .setStartDelay(110)
        .setDuration(400)
        .setInterpolator(new OvershootInterpolator(.85f))
        .start();
  }

  /** Press feedback; return false so normal click and long-click handlers still fire. */
  public static void card(View view) {
    view.setOnTouchListener(
        (v, event) -> {
          if (!enabled(v)) return false;
          int action = event.getActionMasked();
          if (action == MotionEvent.ACTION_DOWN) {
            v.animate().cancel();
            v.animate().scaleX(.968f).scaleY(.968f).setDuration(110).start();
          } else if (action == MotionEvent.ACTION_CANCEL
              || action == MotionEvent.ACTION_UP) {
            v.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(240)
                .setInterpolator(new OvershootInterpolator(.6f))
                .start();
          }
          return false;
        });
  }
}
