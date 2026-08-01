package com.personal.shiftcalendaralarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.PowerManager;

public class AlarmReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "shift_calendar_alarm_fullscreen_v2";

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
            postFullScreenNotification(context, alarmId);

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
            // 백그라운드/잠금 상태에서는 full-screen 알림이 처리한다.
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

    private void postFullScreenNotification(Context context, long alarmId) {
        DatabaseHelper db = new DatabaseHelper(context);
        AlarmItem alarm;
        try {
            alarm = db.getAlarm(alarmId);
        } finally {
            db.close();
        }
        if (alarm == null) return;

        String title = alarm.title == null || alarm.title.trim().isEmpty() ? "알람" : alarm.title;
        String memo = alarm.memo == null || alarm.memo.trim().isEmpty() ? "예약한 시간이 되었습니다." : alarm.memo;

        Intent alarmActivityIntent = new Intent(context, AlarmActivity.class);
        alarmActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        alarmActivityIntent.putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                context, 8000 + (int) (alarmId % 100_000), alarmActivityIntent, flags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) builder = new Notification.Builder(context, CHANNEL_ID);
        else builder = new Notification.Builder(context);

        Notification notification = builder
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("알람: " + title)
                .setContentText(memo)
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                .build();

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(notificationId(alarmId), notification);
    }

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "3교대 알람 전체화면",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("3교대 달력알람 알림");
            channel.enableLights(true);
            channel.setLightColor(Color.RED);
            channel.enableVibration(true);
            channel.setBypassDnd(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    public static int notificationId(long alarmId) {
        return 50_000 + (int) (alarmId % 100_000);
    }
}
