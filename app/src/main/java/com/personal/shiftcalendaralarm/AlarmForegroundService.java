package com.personal.shiftcalendaralarm;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class AlarmForegroundService extends Service {
    public static final String ACTION_START = "com.personal.shiftcalendaralarm.alarm.START";
    public static final String ACTION_STOP = "com.personal.shiftcalendaralarm.alarm.STOP";
    public static final String ACTION_SNOOZE = "com.personal.shiftcalendaralarm.alarm.SNOOZE";

    private Ringtone ringtone;
    private Vibrator vibrator;
    private long currentAlarmId = -1L;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        long alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L);

        if (ACTION_STOP.equals(action)) {
            stopAlarmSound();
            stopForegroundAndSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_SNOOZE.equals(action)) {
            if (alarmId > 0) {
                long next = System.currentTimeMillis() + 5 * 60_000L;
                AlarmScheduler.scheduleSnooze(this, alarmId, next);
            }
            stopAlarmSound();
            stopForegroundAndSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(action) && alarmId > 0) {
            startAlarm(alarmId);
            return START_STICKY;
        }
        return START_NOT_STICKY;
    }

    private void startAlarm(long alarmId) {
        currentAlarmId = alarmId;
        DatabaseHelper db = new DatabaseHelper(this);
        AlarmItem alarm;
        try {
            alarm = db.getAlarm(alarmId);
        } finally {
            db.close();
        }
        if (alarm == null) {
            stopSelf();
            return;
        }

        AlarmReceiver.createChannel(this);
        Notification notification = buildNotification(alarm);
        try {
            startForeground(AlarmReceiver.notificationId(alarmId), notification);
        } catch (Exception ignored) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(AlarmReceiver.notificationId(alarmId), notification);
        }
        startSoundAndVibration(alarm);
    }

    private Notification buildNotification(AlarmItem alarm) {
        String title = alarm.title == null || alarm.title.trim().isEmpty() ? "알람" : alarm.title;
        String memo = alarm.memo == null || alarm.memo.trim().isEmpty() ? "예약한 시간이 되었습니다." : alarm.memo;

        Intent alarmActivityIntent = new Intent(this, AlarmActivity.class);
        alarmActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        alarmActivityIntent.putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                this, 8000 + (int) (alarm.id % 100_000), alarmActivityIntent, flags);

        Intent stopIntent = new Intent(this, AlarmActionReceiver.class);
        stopIntent.setAction(ACTION_STOP);
        stopIntent.putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id);
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(
                this, 9100 + (int) (alarm.id % 100_000), stopIntent, flags);

        Intent snoozeIntent = new Intent(this, AlarmActionReceiver.class);
        snoozeIntent.setAction(ACTION_SNOOZE);
        snoozeIntent.putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id);
        PendingIntent snoozePendingIntent = PendingIntent.getBroadcast(
                this, 9200 + (int) (alarm.id % 100_000), snoozeIntent, flags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, AlarmReceiver.CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("알람: " + title)
                .setContentText(memo)
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true)
                .setAutoCancel(false)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "끄기", stopPendingIntent)
                .addAction(android.R.drawable.ic_popup_reminder, "5분 뒤", snoozePendingIntent)
                .build();
    }

    private void startSoundAndVibration(AlarmItem alarm) {
        stopAlarmSound();
        try {
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            ringtone = RingtoneManager.getRingtone(this, alarmUri);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && ringtone != null) {
                ringtone.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
            }
            if (ringtone != null) ringtone.play();
        } catch (Exception ignored) { }

        if (alarm.vibrate) {
            try {
                vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null) {
                    long[] pattern = new long[]{0, 700, 300, 700, 300, 700};
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                    } else {
                        vibrator.vibrate(pattern, 0);
                    }
                }
            } catch (Exception ignored) { }
        }
    }

    private void stopAlarmSound() {
        try {
            if (ringtone != null && ringtone.isPlaying()) ringtone.stop();
        } catch (Exception ignored) { }
        try {
            if (vibrator != null) vibrator.cancel();
        } catch (Exception ignored) { }
        ringtone = null;
        vibrator = null;
    }

    private void stopForegroundAndSelf() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE);
            else stopForeground(true);
        } catch (Exception ignored) { }
        if (currentAlarmId > 0) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(AlarmReceiver.notificationId(currentAlarmId));
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopAlarmSound();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
