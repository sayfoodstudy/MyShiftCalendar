package com.personal.shiftcalendaralarm;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.PowerManager;

public class AlarmReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "shift_calendar_alarm_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        long alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L);
        if (alarmId <= 0) return;

        PowerManager.WakeLock wakeLock = null;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ShiftCalendarAlarm:AlarmWakeLock");
                wakeLock.acquire(30_000L);
            }

            createChannel(context);

            Intent serviceIntent = new Intent(context, AlarmForegroundService.class);
            serviceIntent.setAction(AlarmForegroundService.ACTION_START);
            serviceIntent.putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } catch (Exception ignored) { }

            // 잠금 해제/화면 켜짐 상태에서는 즉시 알람 화면을 띄우고,
            // 백그라운드/잠금 상태에서는 Foreground Service의 full-screen 알림이 처리한다.
            try {
                Intent alarmActivityIntent = new Intent(context, AlarmActivity.class);
                alarmActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                alarmActivityIntent.putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId);
                context.startActivity(alarmActivityIntent);
            } catch (Exception ignored) { }

            AlarmScheduler.scheduleNextAfterFire(context, alarmId);
        } finally {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }
    }

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "3교대 알람",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("3교대 달력알람 알림");
            channel.enableLights(true);
            channel.setLightColor(Color.RED);
            channel.enableVibration(true);
            channel.setBypassDnd(true);
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    public static int notificationId(long alarmId) {
        return 50_000 + (int) (alarmId % 100_000);
    }
}
