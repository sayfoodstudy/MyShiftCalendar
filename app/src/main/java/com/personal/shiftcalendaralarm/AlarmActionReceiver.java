package com.personal.shiftcalendaralarm;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        long alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L);

        if (AlarmForegroundService.ACTION_SNOOZE.equals(intent.getAction()) && alarmId > 0) {
            long next = System.currentTimeMillis() + 5 * 60_000L;
            AlarmScheduler.scheduleSnooze(context, alarmId, next);
        }

        Intent serviceIntent = new Intent(context, AlarmForegroundService.class);
        context.stopService(serviceIntent);

        if (alarmId > 0) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(AlarmReceiver.notificationId(alarmId));
        }
    }
}
