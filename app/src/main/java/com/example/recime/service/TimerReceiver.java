package com.example.recime.service;
import android.app.*;
import android.content.*;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.example.recime.R;

public final class TimerReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context context, Intent intent) {
    android.content.SharedPreferences p = context.getSharedPreferences("cooking_timer", 0);
    if (p.getLong("deadline", 0) == 0) return;
    NotificationManager notifications = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    if (Build.VERSION.SDK_INT >= 26) notifications.createNotificationChannel(new NotificationChannel("cooking", "Hẹn giờ nấu ăn", NotificationManager.IMPORTANCE_HIGH));
    if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
    Intent open = new Intent(context, com.example.recime.activity.MainActivity.class)
        .putExtra("cookingRecipe", p.getLong("recipe", 0)).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent activity = PendingIntent.getActivity(context, 603, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    notifications.notify(603, new NotificationCompat.Builder(context, "cooking").setSmallIcon(R.drawable.ic_leaf)
        .setContentTitle("Hết giờ nấu ăn").setContentText(p.getString("title", "Công thức"))
        .setContentIntent(activity).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build());
  }
}
