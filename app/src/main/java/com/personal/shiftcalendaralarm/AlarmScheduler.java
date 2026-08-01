package com.personal.shiftcalendaralarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

public class AlarmScheduler {
    public static final String EXTRA_ALARM_ID = "extra_alarm_id";
    public static final String EXTRA_SNOOZE = "extra_snooze";

    public static PendingIntent alarmPendingIntent(Context context, long alarmId) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra(EXTRA_ALARM_ID, alarmId);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, requestCode(alarmId), intent, flags);
    }

    public static PendingIntent openAppPendingIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(context, 9001, intent, flags);
    }

    public static void scheduleAlarm(Context context, long alarmId) {
        DatabaseHelper db = new DatabaseHelper(context);
        try {
            AlarmItem alarm = db.getAlarm(alarmId);
            if (alarm == null || !alarm.enabled) return;
            long next = AlarmCalculator.computeNextTriggerMillis(context, alarm, System.currentTimeMillis());
            if (next <= 0) {
                db.updateAlarmEnabled(alarmId, false);
                db.updateAlarmNextTrigger(alarmId, -1L);
                cancelAlarm(context, alarmId);
                return;
            }
            scheduleAt(context, alarmId, next);
            db.updateAlarmNextTrigger(alarmId, next);
        } finally {
            db.close();
        }
    }

    public static void scheduleAllEnabled(Context context) {
        DatabaseHelper db = new DatabaseHelper(context);
        try {
            for (AlarmItem alarm : db.getEnabledAlarms()) {
                scheduleAlarm(context, alarm.id);
            }
        } finally {
            db.close();
        }
    }

    public static void scheduleSnooze(Context context, long alarmId, long triggerMillis) {
        scheduleAt(context, alarmId, triggerMillis);
        DatabaseHelper db = new DatabaseHelper(context);
        try {
            db.updateAlarmNextTrigger(alarmId, triggerMillis);
        } finally {
            db.close();
        }
    }

    public static void scheduleNextAfterFire(Context context, long alarmId) {
        DatabaseHelper db = new DatabaseHelper(context);
        try {
            AlarmItem alarm = db.getAlarm(alarmId);
            if (alarm == null) return;
            if (!alarm.isRepeatingOrConditional()) {
                db.updateAlarmEnabled(alarmId, false);
                db.updateAlarmNextTrigger(alarmId, -1L);
                return;
            }
            if (!alarm.enabled) return;
            long next = AlarmCalculator.computeNextTriggerMillis(context, alarm, System.currentTimeMillis() + 60_000L);
            if (next > 0) {
                scheduleAt(context, alarmId, next);
                db.updateAlarmNextTrigger(alarmId, next);
            } else {
                db.updateAlarmEnabled(alarmId, false);
                db.updateAlarmNextTrigger(alarmId, -1L);
            }
        } finally {
            db.close();
        }
    }

    public static void cancelAlarm(Context context, long alarmId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) alarmManager.cancel(alarmPendingIntent(context, alarmId));
    }

    private static void scheduleAt(Context context, long alarmId, long triggerMillis) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(triggerMillis, openAppPendingIntent(context));
        alarmManager.setAlarmClock(info, alarmPendingIntent(context, alarmId));
    }

    public static boolean canScheduleExact(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return alarmManager != null && alarmManager.canScheduleExactAlarms();
    }

    public static Intent exactAlarmSettingsIntent() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
        }
        return new Intent(Settings.ACTION_SETTINGS);
    }

    private static int requestCode(long alarmId) {
        long safe = alarmId % 1_000_000_000L;
        return (int) safe + 10_000;
    }
}
