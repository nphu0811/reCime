package com.example.recime.helper;
import android.app.*;
import android.content.*;
import android.os.*;
import com.example.recime.service.TimerReceiver;

/** One user-started timer. Monotonic deadline survives rotation and process recreation. */
public final class CookingTimer {
  private static PendingIntent alarm(Context c) {
    return PendingIntent.getBroadcast(c, 603, new Intent(c, TimerReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }
  public static boolean start(Context c, long recipe, String title, int minutes) {
    if (minutes < 1 || minutes > 1440) throw new IllegalArgumentException("Hẹn giờ từ 1 đến 1440 phút");
    AlarmManager manager = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    long deadline = SystemClock.elapsedRealtime() + minutes * 60000L;
    manager.cancel(alarm(c));
    boolean exact = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms();
    c.getSharedPreferences("cooking_timer", 0).edit().putLong("deadline", deadline)
        .putInt("boot", android.provider.Settings.Global.getInt(c.getContentResolver(), "boot_count", 0))
        .putLong("recipe", recipe).putString("title", title).putBoolean("exact", exact).commit();
    if (exact) {
      try { manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, alarm(c)); }
      catch (SecurityException revoked) { exact = false; manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, alarm(c)); }
    } else manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, alarm(c));
    c.getSharedPreferences("cooking_timer", 0).edit().putBoolean("exact", exact).apply();
    return exact;
  }
  public static long remaining(Context c) {
    android.content.SharedPreferences p = c.getSharedPreferences("cooking_timer", 0);
    if (p.getInt("boot", -1) != android.provider.Settings.Global.getInt(c.getContentResolver(), "boot_count", 0)) return -1;
    long deadline = p.getLong("deadline", 0);
    return deadline == 0 ? -1 : Math.max(0, deadline - SystemClock.elapsedRealtime());
  }
  public static void cancel(Context c) {
    ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).cancel(alarm(c));
    ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(603);
    c.getSharedPreferences("cooking_timer", 0).edit().clear().commit();
  }
  private CookingTimer() {}
}
