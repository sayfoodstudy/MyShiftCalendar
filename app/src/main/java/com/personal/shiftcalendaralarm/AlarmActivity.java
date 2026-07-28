package com.personal.shiftcalendaralarm;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
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
        String now = new SimpleDateFormat("HH:mm", Locale.KOREAN).format(new Date());
        String date = new SimpleDateFormat("yyyy년 M월 d일 E요일", Locale.KOREAN).format(new Date());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(32), dp(24), dp(32));
        root.setBackgroundColor(Color.rgb(242, 242, 247));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(24), dp(28), dp(24), dp(24));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setCornerRadius(dp(28));
        card.setBackground(cardBg);
        root.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView smallLabel = new TextView(this);
        smallLabel.setText("3교대 달력알람");
        smallLabel.setTextSize(15);
        smallLabel.setTextColor(Color.rgb(142, 142, 147));
        smallLabel.setGravity(Gravity.CENTER);
        card.addView(smallLabel, matchWrap());

        TextView timeText = new TextView(this);
        timeText.setText(now);
        timeText.setTextSize(58);
        timeText.setTypeface(Typeface.DEFAULT_BOLD);
        timeText.setGravity(Gravity.CENTER);
        timeText.setTextColor(Color.rgb(28, 28, 30));
        timeText.setPadding(0, dp(8), 0, 0);
        card.addView(timeText, matchWrap());

        TextView dateText = new TextView(this);
        dateText.setText(date);
        dateText.setTextSize(15);
        dateText.setGravity(Gravity.CENTER);
        dateText.setTextColor(Color.rgb(99, 99, 102));
        card.addView(dateText, matchWrap());

        TextView titleText = new TextView(this);
        titleText.setText(title);
        titleText.setTextSize(28);
        titleText.setTypeface(Typeface.DEFAULT_BOLD);
        titleText.setGravity(Gravity.CENTER);
        titleText.setTextColor(Color.rgb(28, 28, 30));
        titleText.setPadding(0, dp(24), 0, dp(6));
        card.addView(titleText, matchWrap());

        TextView memoText = new TextView(this);
        memoText.setText(memo);
        memoText.setTextSize(17);
        memoText.setGravity(Gravity.CENTER);
        memoText.setTextColor(Color.rgb(72, 72, 74));
        memoText.setPadding(0, 0, 0, dp(26));
        card.addView(memoText, matchWrap());

        Button snoozeButton = makeIosButton("5분 뒤 다시 알림", Color.rgb(242, 242, 247), Color.rgb(0, 122, 255));
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
        card.addView(snoozeButton, matchWrapWithTopMargin(0));

        Button stopButton = makeIosButton("끄기", Color.rgb(255, 59, 48), Color.WHITE);
        stopButton.setOnClickListener(v -> {
            stopAlarmService();
            cancelNotification();
            finish();
        });
        card.addView(stopButton, matchWrapWithTopMargin(dp(10)));

        setContentView(root);
    }

    private Button makeIosButton(String text, int bgColor, int textColor) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(18);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(textColor);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(0, dp(12), 0, dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(16));
        button.setBackground(bg);
        return button;
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

    private LinearLayout.LayoutParams matchWrapWithTopMargin(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, topMargin, 0, 0);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
