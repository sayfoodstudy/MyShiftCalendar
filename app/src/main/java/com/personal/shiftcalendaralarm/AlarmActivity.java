package com.personal.shiftcalendaralarm;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AlarmActivity extends Activity {
    private Ringtone ringtone;
    private Vibrator vibrator;
    private long alarmId;
    private AlarmItem alarm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        alarmId = getIntent().getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L);
        loadAlarm();
        prepareLockScreenDisplay();
        buildUi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void loadAlarm() {
        DatabaseHelper db = new DatabaseHelper(this);
        try {
            alarm = db.getAlarm(alarmId);
        } finally {
            db.close();
        }
    }

    private void prepareLockScreenDisplay() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
    }

    private void buildUi() {
        String title = alarm == null || alarm.title == null || alarm.title.trim().isEmpty() ? "알람" : alarm.title;
        String memo = alarm == null || alarm.memo == null || alarm.memo.trim().isEmpty() ? "예약한 시간이 되었습니다." : alarm.memo;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(0xFFFFF8E1);

        TextView alarmText = new TextView(this);
        alarmText.setText("⏰ 알람");
        alarmText.setTextSize(36);
        alarmText.setGravity(Gravity.CENTER);
        root.addView(alarmText, matchWrap());

        TextView titleText = new TextView(this);
        titleText.setText(title);
        titleText.setTextSize(28);
        titleText.setGravity(Gravity.CENTER);
        titleText.setPadding(0, dp(16), 0, dp(8));
        root.addView(titleText, matchWrap());

        TextView timeText = new TextView(this);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREAN);
        timeText.setText(sdf.format(new Date()));
        timeText.setTextSize(18);
        timeText.setGravity(Gravity.CENTER);
        root.addView(timeText, matchWrap());

        TextView memoText = new TextView(this);
        memoText.setText(memo);
        memoText.setTextSize(18);
        memoText.setGravity(Gravity.CENTER);
        memoText.setPadding(0, dp(18), 0, dp(18));
        root.addView(memoText, matchWrap());

        Button stopButton = new Button(this);
        stopButton.setText("끄기");
        stopButton.setTextSize(20);
        stopButton.setOnClickListener(v -> {
            stopAlarmService();
            cancelNotification();
            finish();
        });
        root.addView(stopButton, matchWrap());

        Button snoozeButton = new Button(this);
        snoozeButton.setText("5분 뒤 다시 알림");
        snoozeButton.setTextSize(20);
        snoozeButton.setOnClickListener(v -> {
            if (alarmId > 0) {
                long next = System.currentTimeMillis() + 5 * 60_000L;
                AlarmScheduler.scheduleSnooze(this, alarmId, next);
                Toast.makeText(this, "5분 뒤 다시 알림을 예약했습니다.", Toast.LENGTH_SHORT).show();
            }
            stopAlarmService();
            cancelNotification();
            finish();
        });
        root.addView(snoozeButton, matchWrap());

        setContentView(root);
    }

    private void startSoundAndVibration() {
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

        if (alarm == null || alarm.vibrate) {
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

    private void stopSoundAndVibration() {
        try {
            if (ringtone != null && ringtone.isPlaying()) ringtone.stop();
        } catch (Exception ignored) { }
        try {
            if (vibrator != null) vibrator.cancel();
        } catch (Exception ignored) { }
    }

    private void stopAlarmService() {
        try {
            Intent intent = new Intent(this, AlarmForegroundService.class);
            stopService(intent);
        } catch (Exception ignored) { }
    }

    private void cancelNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null && alarmId > 0) nm.cancel(AlarmReceiver.notificationId(alarmId));
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
