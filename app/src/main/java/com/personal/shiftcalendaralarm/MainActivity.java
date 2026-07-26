package com.personal.shiftcalendaralarm;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private DatabaseHelper db;
    private LocalDate currentMonth;
    private TextView monthTitle;
    private TextView baseInfoText;
    private GridLayout calendarGrid;
    private float swipeStartX;
    private float swipeStartY;
    private long lastMonthSwipeTime;
    private boolean monthAnimating;
    private boolean monthDragging;
    private LocalDate selectedDate;
    private LocalDate lastTappedDate;
    private long lastTapTime;
    private LinearLayout memoPanel;
    private TextView selectedMemoTitle;
    private TextView selectedMemoContent;

    private final int[] eventPalette = new int[]{
            Color.rgb(255, 59, 48),
            Color.rgb(255, 149, 0),
            Color.rgb(255, 204, 0),
            Color.rgb(52, 199, 89),
            Color.rgb(48, 209, 88),
            Color.rgb(99, 230, 226),
            Color.rgb(64, 200, 224),
            Color.rgb(0, 122, 255),
            Color.rgb(88, 86, 214),
            Color.rgb(175, 82, 222),
            Color.rgb(255, 45, 85),
            Color.rgb(162, 132, 94),
            Color.rgb(142, 142, 147),
            Color.rgb(90, 200, 250)
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DatabaseHelper(this);
        selectedDate = LocalDate.now();
        currentMonth = selectedDate.withDayOfMonth(1);
        buildMainLayout();
        renderMonth();
        if (!db.isBaseDateSet()) {
            showBaseDateDialog(true);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (calendarGrid == null) return super.dispatchTouchEvent(event);

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            swipeStartX = event.getX();
            swipeStartY = event.getY();
            monthDragging = false;
            if (!monthAnimating) calendarGrid.animate().cancel();
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (!monthAnimating) {
                float dx = event.getX() - swipeStartX;
                float dy = event.getY() - swipeStartY;
                if (monthDragging || (Math.abs(dx) > dp(26) && Math.abs(dx) > Math.abs(dy) * 1.45f)) {
                    monthDragging = true;
                    float width = Math.max(1, calendarGrid.getWidth());
                    float dragX = dx * 0.78f;
                    calendarGrid.setTranslationX(dragX);
                    calendarGrid.setAlpha(1f - Math.min(0.10f, Math.abs(dragX) / width * 0.10f));
                    return true;
                }
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (monthDragging) {
                float dx = event.getX() - swipeStartX;
                float dragX = dx * 0.78f;
                int threshold = dp(120); // 낮은 민감도: 충분히 길게 넘겨야 월 이동
                long now = System.currentTimeMillis();
                if (Math.abs(dx) > threshold && now - lastMonthSwipeTime > 350) {
                    lastMonthSwipeTime = now;
                    changeMonthFromDrag(dx < 0 ? 1 : -1, dragX);
                } else {
                    calendarGrid.animate()
                            .translationX(0)
                            .alpha(1f)
                            .setDuration(180)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                }
                monthDragging = false;
                return true;
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void buildMainLayout() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(6), getStatusBarHeight() + dp(8), dp(6), dp(8));
        scrollView.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(6));
        root.addView(header, matchWrap());

        Button settingsButton = makeCompactButton("설정");
        settingsButton.setOnClickListener(v -> showSettingsDialog());
        header.addView(settingsButton, new LinearLayout.LayoutParams(dp(54), dp(34)));

        Button prevButton = makeCompactButton("<");
        prevButton.setOnClickListener(v -> changeMonth(-1, true));
        LinearLayout.LayoutParams prevParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        prevParams.setMargins(dp(4), 0, 0, 0);
        header.addView(prevButton, prevParams);

        monthTitle = new TextView(this);
        monthTitle.setTextSize(21);
        monthTitle.setTypeface(Typeface.DEFAULT_BOLD);
        monthTitle.setGravity(Gravity.CENTER);
        monthTitle.setSingleLine(true);
        monthTitle.setTextColor(Color.rgb(28, 28, 30));
        header.addView(monthTitle, new LinearLayout.LayoutParams(0, dp(34), 1));

        Button todayButton = makeCompactButton("오늘");
        todayButton.setOnClickListener(v -> {
            selectedDate = LocalDate.now();
            currentMonth = selectedDate.withDayOfMonth(1);
            renderMonth();
            updateSelectedMemoPanel(selectedDate);
        });
        header.addView(todayButton, new LinearLayout.LayoutParams(dp(50), dp(34)));

        Button nextButton = makeCompactButton(">");
        nextButton.setOnClickListener(v -> changeMonth(1, true));
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        nextParams.setMargins(dp(4), 0, 0, 0);
        header.addView(nextButton, nextParams);

        baseInfoText = new TextView(this);
        baseInfoText.setTextSize(11);
        baseInfoText.setSingleLine(true);
        baseInfoText.setTextColor(Color.rgb(142, 142, 147));
        baseInfoText.setPadding(dp(3), 0, dp(3), dp(4));
        root.addView(baseInfoText, matchWrap());

        calendarGrid = new GridLayout(this);
        calendarGrid.setColumnCount(7);
        calendarGrid.setUseDefaultMargins(false);
        root.addView(calendarGrid, matchWrap());

        memoPanel = new LinearLayout(this);
        memoPanel.setOrientation(LinearLayout.VERTICAL);
        memoPanel.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable memoBg = new GradientDrawable();
        memoBg.setColor(Color.rgb(242, 242, 247));
        memoBg.setCornerRadius(dp(16));
        memoPanel.setBackground(memoBg);
        LinearLayout.LayoutParams memoParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        memoParams.setMargins(0, dp(10), 0, 0);
        root.addView(memoPanel, memoParams);

        selectedMemoTitle = new TextView(this);
        selectedMemoTitle.setTextSize(16);
        selectedMemoTitle.setTypeface(Typeface.DEFAULT_BOLD);
        selectedMemoTitle.setTextColor(Color.rgb(28, 28, 30));
        memoPanel.addView(selectedMemoTitle, matchWrap());

        selectedMemoContent = new TextView(this);
        selectedMemoContent.setTextSize(13);
        selectedMemoContent.setTextColor(Color.rgb(72, 72, 74));
        selectedMemoContent.setPadding(0, dp(4), 0, 0);
        memoPanel.addView(selectedMemoContent, matchWrap());

        setContentView(scrollView);
    }

    private void changeMonth(int delta, boolean animate) {
        if (monthAnimating) return;
        if (!animate || calendarGrid == null || calendarGrid.getWidth() == 0) {
            currentMonth = currentMonth.plusMonths(delta);
            selectedDate = currentMonth.withDayOfMonth(1);
            renderMonth();
            updateSelectedMemoPanel(selectedDate);
            return;
        }
        animateMonthTransition(delta, 0f);
    }

    private void changeMonthFromDrag(int delta, float dragTranslation) {
        if (monthAnimating) return;
        animateMonthTransition(delta, dragTranslation);
    }

    private void animateMonthTransition(int delta, float startTranslation) {
        monthAnimating = true;
        float width = Math.max(1, calendarGrid.getWidth());
        float outX = delta > 0 ? -width : width;
        float inX = delta > 0 ? width : -width;

        calendarGrid.animate().cancel();
        calendarGrid.setTranslationX(startTranslation);
        calendarGrid.setAlpha(1f);

        float remainingRatio = Math.abs(outX - startTranslation) / width;
        long outDuration = Math.max(90L, (long) (210L * remainingRatio));

        calendarGrid.animate()
                .translationX(outX)
                .alpha(0.96f)
                .setDuration(outDuration)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    currentMonth = currentMonth.plusMonths(delta);
                    selectedDate = currentMonth.withDayOfMonth(1);
                    renderMonth();
                    updateSelectedMemoPanel(selectedDate);
                    calendarGrid.setTranslationX(inX);
                    calendarGrid.setAlpha(0.96f);
                    calendarGrid.animate()
                            .translationX(0)
                            .alpha(1f)
                            .setDuration(260)
                            .setInterpolator(new DecelerateInterpolator())
                            .withEndAction(() -> monthAnimating = false)
                            .start();
                })
                .start();
    }

    private void renderMonth() {
        monthTitle.setText(currentMonth.format(DateUtil.MONTH_TITLE));

        LocalDate baseDate = db.getBaseDate();
        if (baseDate == null) {
            baseInfoText.setText("주간 시작일 미설정");
        } else {
            baseInfoText.setText("주간 시작일 " + DateUtil.iso(baseDate) + " / 주→당→비 / 주간+휴일=주휴");
        }

        calendarGrid.removeAllViews();
        addWeekHeaders();

        List<PeriodEvent> monthEvents = db.getEventsForMonth(currentMonth);
        LocalDate firstDay = currentMonth.withDayOfMonth(1);
        int startOffset = firstDay.getDayOfWeek().getValue() % 7;
        LocalDate gridStart = firstDay.minusDays(startOffset);

        for (int i = 0; i < 42; i++) {
            LocalDate date = gridStart.plusDays(i);
            calendarGrid.addView(createDayCell(date, monthEvents));
        }

        if (selectedDate == null || selectedDate.getYear() != currentMonth.getYear() ||
                selectedDate.getMonthValue() != currentMonth.getMonthValue()) {
            selectedDate = currentMonth.withDayOfMonth(1);
        }
        updateSelectedMemoPanel(selectedDate);
    }

    private void addWeekHeaders() {
        String[] headers = new String[]{"일", "월", "화", "수", "목", "금", "토"};
        for (int i = 0; i < headers.length; i++) {
            TextView tv = new TextView(this);
            tv.setText(headers[i]);
            tv.setGravity(Gravity.CENTER);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setTextSize(12);
            tv.setPadding(0, dp(3), 0, dp(3));
            if (i == 0) tv.setTextColor(Color.rgb(211, 47, 47));
            else if (i == 6) tv.setTextColor(Color.rgb(21, 101, 192));
            else tv.setTextColor(Color.DKGRAY);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            tv.setLayoutParams(params);
            calendarGrid.addView(tv);
        }
    }

    private View createDayCell(final LocalDate date, List<PeriodEvent> monthEvents) {
        boolean inCurrentMonth = date.getMonthValue() == currentMonth.getMonthValue() && date.getYear() == currentMonth.getYear();
        boolean isToday = date.equals(LocalDate.now());
        boolean isSelected = selectedDate != null && date.equals(selectedDate);
        boolean isHoliday = HolidayProvider.isHoliday(date);
        String publicHolidayName = HolidayProvider.getPublicHolidayName(date);

        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        cell.setPadding(0, dp(2), 0, dp(1));
        cell.setBackground(makeCellBackground(inCurrentMonth, isToday, isSelected));
        cell.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            boolean doubleTap = lastTappedDate != null && lastTappedDate.equals(date) && now - lastTapTime < 420;
            selectedDate = date;
            updateSelectedMemoPanel(date);
            renderMonth();
            if (doubleTap) {
                lastTappedDate = null;
                lastTapTime = 0L;
                showDayDetail(date);
            } else {
                lastTappedDate = date;
                lastTapTime = now;
            }
        });
        cell.setOnLongClickListener(v -> {
            showDayDetail(date);
            return true;
        });

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(74);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(0, 0, 0, 0);
        cell.setLayoutParams(params);

        TextView dateText = new TextView(this);
        dateText.setText(String.valueOf(date.getDayOfMonth()));
        dateText.setTextSize(10);
        dateText.setTypeface(Typeface.DEFAULT_BOLD);
        dateText.setGravity(Gravity.CENTER);
        if (!inCurrentMonth) dateText.setTextColor(Color.rgb(190, 190, 195));
        else if (isHoliday || date.getDayOfWeek() == DayOfWeek.SUNDAY) dateText.setTextColor(Color.rgb(255, 59, 48));
        else if (date.getDayOfWeek() == DayOfWeek.SATURDAY) dateText.setTextColor(Color.rgb(0, 122, 255));
        else dateText.setTextColor(Color.rgb(28, 28, 30));
        cell.addView(dateText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(13)));

        ShiftResult result = calculateShift(date);
        if (result.finalShift != null) {
            int badgeColor = inCurrentMonth ? result.finalShift.color : fadeColor(result.finalShift.color);
            TextView shiftBadge = new TextView(this);
            shiftBadge.setText(result.finalShift.displayShortName());
            shiftBadge.setSingleLine(true);
            shiftBadge.setGravity(Gravity.CENTER);
            shiftBadge.setTextSize(14);
            shiftBadge.setTypeface(Typeface.DEFAULT_BOLD);
            shiftBadge.setTextColor(inCurrentMonth ? Color.rgb(45, 45, 48) : Color.rgb(150, 150, 155));
            shiftBadge.setBackground(makeCircleBackground(badgeColor));
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(38), dp(38));
            badgeParams.gravity = Gravity.CENTER_HORIZONTAL;
            badgeParams.setMargins(0, 0, 0, 0);
            cell.addView(shiftBadge, badgeParams);
            shiftBadge.post(() -> {
                int size = (int) (cell.getWidth() * 0.70f);
                if (size > dp(30)) {
                    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) shiftBadge.getLayoutParams();
                    lp.width = size;
                    lp.height = size;
                    lp.gravity = Gravity.CENTER_HORIZONTAL;
                    shiftBadge.setLayoutParams(lp);
                }
            });
        }

        if (publicHolidayName != null && inCurrentMonth) {
            TextView holidayText = new TextView(this);
            holidayText.setText(publicHolidayName);
            holidayText.setTextSize(7);
            holidayText.setSingleLine(true);
            holidayText.setGravity(Gravity.CENTER);
            holidayText.setTextColor(Color.rgb(255, 59, 48));
            cell.addView(holidayText, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(8)));
        }

        View spacer = new View(this);
        cell.addView(spacer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        List<PeriodEvent> dayEvents = eventsCovering(date, monthEvents);
        int shown = Math.min(dayEvents.size(), 3);
        for (int i = 0; i < shown; i++) {
            View bar = new View(this);
            int barColor = inCurrentMonth ? dayEvents.get(i).color : fadeColor(dayEvents.get(i).color);
            bar.setBackgroundColor(barColor);
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(4));
            barParams.setMargins(0, dp(1), 0, 0);
            cell.addView(bar, barParams);
        }
        if (dayEvents.size() > 3) {
            TextView more = new TextView(this);
            more.setText("+" + (dayEvents.size() - 3));
            more.setTextSize(7);
            more.setGravity(Gravity.CENTER);
            more.setTextColor(inCurrentMonth ? Color.rgb(99, 99, 102) : Color.LTGRAY);
            cell.addView(more, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(9)));
        }
        return cell;
    }

    private void updateSelectedMemoPanel(LocalDate date) {
        if (selectedMemoTitle == null || selectedMemoContent == null || date == null) return;

        ShiftResult shift = calculateShift(date);
        List<PeriodEvent> events = db.getEventsForDate(date);

        selectedMemoTitle.setText(date.format(DateUtil.KOREAN_DATE));

        StringBuilder sb = new StringBuilder();
        if (shift.finalShift != null) {
            sb.append("근무: ").append(shift.finalShift.name);
            if (shift.manualOverride) sb.append(" (수동 변경)");
            sb.append("\n");
        }
        if (shift.holidayLabel != null) {
            sb.append("휴일: ").append(shift.holidayLabel).append("\n");
        }

        if (events.isEmpty()) {
            sb.append("메모/일정 없음\n");
            sb.append("살짝 누르면 이 영역에 메모가 표시되고, 길게 누르면 근무 변경/일정 추가로 들어갑니다.");
        } else {
            sb.append("메모/일정\n");
            for (PeriodEvent event : events) {
                sb.append("• ").append(event.title)
                        .append("  ")
                        .append(DateUtil.iso(event.startDate))
                        .append("~")
                        .append(DateUtil.iso(event.endDate));
                if (event.memo != null && !event.memo.trim().isEmpty()) {
                    sb.append("\n  ").append(event.memo.trim());
                }
                sb.append("\n");
            }
            sb.append("\n길게 누르면 근무 변경/일정 추가.");
        }
        selectedMemoContent.setText(sb.toString().trim());
    }

    private List<PeriodEvent> eventsCovering(LocalDate date, List<PeriodEvent> events) {
        List<PeriodEvent> result = new ArrayList<>();
        for (PeriodEvent event : events) {
            if (event.covers(date)) result.add(event);
        }
        return result;
    }

    private ShiftResult calculateShift(LocalDate date) {
        LocalDate baseDate = db.getBaseDate();
        if (baseDate == null) baseDate = LocalDate.now();

        long diff = ChronoUnit.DAYS.between(baseDate, date);
        int mod = (int) ((diff % 3 + 3) % 3);
        ShiftType baseShift;
        if (mod == 0) baseShift = db.getShiftTypeByCode(DatabaseHelper.CODE_DAY);
        else if (mod == 1) baseShift = db.getShiftTypeByCode(DatabaseHelper.CODE_DUTY);
        else baseShift = db.getShiftTypeByCode(DatabaseHelper.CODE_OFF);

        boolean holiday = HolidayProvider.isHoliday(date);
        String holidayLabel = HolidayProvider.getHolidayLabel(date);
        long overrideId = db.getOverrideShiftId(date);
        if (overrideId > 0) {
            ShiftType override = db.getShiftTypeById(overrideId);
            if (override != null) return new ShiftResult(baseShift, override, true, holiday, holidayLabel);
        }

        if (baseShift != null && DatabaseHelper.CODE_DAY.equals(baseShift.code) && holiday) {
            ShiftType juhyu = db.getShiftTypeByCode(DatabaseHelper.CODE_JUHYU);
            if (juhyu != null) return new ShiftResult(baseShift, juhyu, false, true, holidayLabel);
        }
        return new ShiftResult(baseShift, baseShift, false, holiday, holidayLabel);
    }

    private void showDayDetail(final LocalDate date) {
        ShiftResult result = calculateShift(date);
        List<PeriodEvent> events = db.getEventsForDate(date);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(10));
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText(date.format(DateUtil.KOREAN_DATE));
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        addInfoLine(root, "휴일", result.holidayLabel == null ? "아님" : result.holidayLabel);
        addInfoLine(root, "기본 순번", result.baseShift == null ? "-" : result.baseShift.name);
        addInfoLine(root, "최종 표시", result.finalShift == null ? "-" : result.finalShift.name + (result.manualOverride ? "  (수동 변경)" : ""));

        TextView eventTitle = new TextView(this);
        eventTitle.setText("\n기간 일정");
        eventTitle.setTypeface(Typeface.DEFAULT_BOLD);
        eventTitle.setTextSize(16);
        root.addView(eventTitle, matchWrap());

        if (events.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("등록된 기간 일정 없음");
            empty.setTextColor(Color.GRAY);
            root.addView(empty, matchWrap());
        } else {
            for (PeriodEvent event : events) {
                addEventRow(root, event, date);
            }
        }

        Button shiftButton = new Button(this);
        shiftButton.setText("이 날짜 근무 변경");
        root.addView(shiftButton, matchWrap());

        Button eventButton = new Button(this);
        eventButton.setText("기간 일정 추가");
        root.addView(eventButton, matchWrap());

        Button closeButton = new Button(this);
        closeButton.setText("닫기");
        root.addView(closeButton, matchWrap());

        final AlertDialog dialog = new AlertDialog.Builder(this).setView(scrollView).create();
        shiftButton.setOnClickListener(v -> {
            dialog.dismiss();
            showShiftOverrideDialog(date);
        });
        eventButton.setOnClickListener(v -> {
            dialog.dismiss();
            showAddEventDialog(date);
        });
        closeButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void addInfoLine(LinearLayout root, String label, String value) {
        TextView tv = new TextView(this);
        tv.setText(label + ": " + value);
        tv.setTextSize(15);
        tv.setPadding(0, dp(5), 0, 0);
        root.addView(tv, matchWrap());
    }

    private void addEventRow(LinearLayout root, final PeriodEvent event, final LocalDate selectedDate) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        View colorView = new View(this);
        colorView.setBackgroundColor(event.color);
        row.addView(colorView, new LinearLayout.LayoutParams(dp(12), dp(36)));

        TextView text = new TextView(this);
        String memoPart = event.memo == null || event.memo.trim().isEmpty() ? "" : "\n" + event.memo;
        text.setText(event.title + "\n" + DateUtil.iso(event.startDate) + " ~ " + DateUtil.iso(event.endDate) + memoPart);
        text.setTextSize(13);
        text.setPadding(dp(8), 0, dp(8), 0);
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button delete = new Button(this);
        delete.setText("삭제");
        delete.setTextSize(12);
        row.addView(delete, new LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.WRAP_CONTENT));

        delete.setOnClickListener(v -> confirmDeleteEvent(event, selectedDate));
        root.addView(row, matchWrap());
    }

    private void showShiftOverrideDialog(final LocalDate date) {
        final List<ShiftType> types = db.getAllShiftTypes();
        String[] items = new String[types.size() + 1];
        items[0] = "자동 계산으로 되돌리기";
        for (int i = 0; i < types.size(); i++) {
            ShiftType type = types.get(i);
            items[i + 1] = type.name + " (" + type.category + ")";
        }

        new AlertDialog.Builder(this)
                .setTitle(date.format(DateUtil.SHORT_DATE) + " 근무 변경")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        db.clearShiftOverride(date);
                        Toast.makeText(this, "자동 계산으로 되돌렸습니다.", Toast.LENGTH_SHORT).show();
                    } else {
                        ShiftType selected = types.get(which - 1);
                        db.setShiftOverride(date, selected.id);
                        Toast.makeText(this, selected.name + "으로 변경했습니다.", Toast.LENGTH_SHORT).show();
                    }
                    renderMonth();
                    showDayDetail(date);
                })
                .show();
    }

    private void showAddEventDialog(final LocalDate selectedDate) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(10), dp(18), 0);

        final EditText titleInput = new EditText(this);
        titleInput.setHint("일정 제목 예: 일주일 작업");
        titleInput.setSingleLine(true);
        root.addView(titleInput, matchWrap());

        final LocalDate[] startDate = new LocalDate[]{selectedDate};
        final LocalDate[] endDate = new LocalDate[]{selectedDate};

        final TextView startText = new TextView(this);
        startText.setText("시작: " + DateUtil.iso(startDate[0]));
        startText.setTextSize(15);
        root.addView(startText, matchWrap());

        Button startButton = new Button(this);
        startButton.setText("시작 날짜 선택");
        startButton.setOnClickListener(v -> pickDate(startDate[0], picked -> {
            startDate[0] = picked;
            startText.setText("시작: " + DateUtil.iso(startDate[0]));
        }));
        root.addView(startButton, matchWrap());

        final TextView endText = new TextView(this);
        endText.setText("종료: " + DateUtil.iso(endDate[0]));
        endText.setTextSize(15);
        root.addView(endText, matchWrap());

        Button endButton = new Button(this);
        endButton.setText("종료 날짜 선택");
        endButton.setOnClickListener(v -> pickDate(endDate[0], picked -> {
            endDate[0] = picked;
            endText.setText("종료: " + DateUtil.iso(endDate[0]));
        }));
        root.addView(endButton, matchWrap());

        final EditText memoInput = new EditText(this);
        memoInput.setHint("메모 선택사항");
        memoInput.setMinLines(2);
        memoInput.setGravity(Gravity.TOP);
        root.addView(memoInput, matchWrap());

        TextView colorLabel = new TextView(this);
        colorLabel.setText("색상 선택");
        colorLabel.setTypeface(Typeface.DEFAULT_BOLD);
        colorLabel.setPadding(0, dp(8), 0, dp(4));
        root.addView(colorLabel, matchWrap());

        final int[] selectedColor = new int[]{eventPalette[0]};
        final TextView colorPreview = new TextView(this);
        colorPreview.setText("선택된 색상");
        colorPreview.setGravity(Gravity.CENTER);
        colorPreview.setTextColor(Color.WHITE);
        colorPreview.setBackground(makeRoundBackground(selectedColor[0], dp(6)));
        root.addView(colorPreview, matchWrap());

        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int color : eventPalette) {
            Button colorButton = new Button(this);
            colorButton.setText(" ");
            colorButton.setBackground(makeRoundBackground(color, dp(8)));
            colorButton.setOnClickListener(v -> {
                selectedColor[0] = color;
                colorPreview.setBackground(makeRoundBackground(selectedColor[0], dp(6)));
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(46), 1);
            cp.setMargins(dp(2), dp(2), dp(2), dp(2));
            colorRow.addView(colorButton, cp);
        }
        root.addView(colorRow, matchWrap());

        new AlertDialog.Builder(this)
                .setTitle("기간 일정 추가")
                .setView(root)
                .setPositiveButton("저장", (dialog, which) -> {
                    String title = titleInput.getText().toString().trim();
                    String memo = memoInput.getText().toString().trim();
                    if (title.isEmpty()) {
                        title = memo.isEmpty() ? "일정" : "메모";
                    }
                    if (endDate[0].isBefore(startDate[0])) {
                        Toast.makeText(this, "종료 날짜가 시작 날짜보다 빠릅니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    db.addPeriodEvent(title, startDate[0], endDate[0], selectedColor[0], memo);
                    Toast.makeText(this, "기간 일정을 추가했습니다.", Toast.LENGTH_SHORT).show();
                    renderMonth();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private interface DatePickedCallback {
        void onPicked(LocalDate date);
    }

    private void pickDate(LocalDate initialDate, final DatePickedCallback callback) {
        DatePickerDialog dialog = new DatePickerDialog(this,
                (DatePicker view, int year, int month, int dayOfMonth) -> callback.onPicked(LocalDate.of(year, month + 1, dayOfMonth)),
                initialDate.getYear(), initialDate.getMonthValue() - 1, initialDate.getDayOfMonth());
        dialog.show();
    }

    private void confirmDeleteEvent(final PeriodEvent event, final LocalDate selectedDate) {
        new AlertDialog.Builder(this)
                .setTitle("기간 일정 삭제")
                .setMessage("'" + event.title + "' 일정을 삭제할까요?")
                .setPositiveButton("삭제", (dialog, which) -> {
                    db.deletePeriodEvent(event.id);
                    Toast.makeText(this, "삭제했습니다. 날짜 상세 창을 닫고 다시 열면 반영됩니다.", Toast.LENGTH_SHORT).show();
                    renderMonth();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showSettingsDialog() {
        String[] items = new String[]{
                "주간 시작일 설정",
                "근무 종류 관리",
                "공휴일 보기",
                "앱 정보"
        };
        new AlertDialog.Builder(this)
                .setTitle("설정")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) showBaseDateDialog(false);
                    else if (which == 1) showShiftTypesManager();
                    else if (which == 2) showHolidayInfo();
                    else showAboutDialog();
                })
                .show();
    }

    private void showBaseDateDialog(boolean firstRun) {
        LocalDate initial = db.getBaseDate();
        if (initial == null) initial = LocalDate.now();
        final LocalDate[] selected = new LocalDate[]{initial};

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(10), dp(16), 0);

        TextView guide = new TextView(this);
        guide.setText("주간 근무가 시작되는 날짜를 선택하세요. 이 날짜를 기준으로 주간 → 당직 → 비번이 반복됩니다.");
        guide.setTextSize(15);
        root.addView(guide, matchWrap());

        final TextView selectedText = new TextView(this);
        selectedText.setText("선택: " + selected[0].format(DateUtil.KOREAN_DATE));
        selectedText.setTextSize(16);
        selectedText.setTypeface(Typeface.DEFAULT_BOLD);
        selectedText.setPadding(0, dp(10), 0, dp(8));
        root.addView(selectedText, matchWrap());

        Button pickButton = new Button(this);
        pickButton.setText("날짜 선택");
        pickButton.setOnClickListener(v -> pickDate(selected[0], picked -> {
            selected[0] = picked;
            selectedText.setText("선택: " + selected[0].format(DateUtil.KOREAN_DATE));
        }));
        root.addView(pickButton, matchWrap());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(firstRun ? "첫 설정: 주간 시작일" : "주간 시작일 설정")
                .setView(root)
                .setPositiveButton("저장", (d, which) -> {
                    db.setBaseDate(selected[0]);
                    Toast.makeText(this, "주간 시작일을 저장했습니다.", Toast.LENGTH_SHORT).show();
                    renderMonth();
                })
                .create();
        if (!firstRun) dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "취소", (d, which) -> d.dismiss());
        dialog.setCancelable(!firstRun);
        dialog.show();
    }

    private void showShiftTypesManager() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(8), dp(14), dp(8));
        scrollView.addView(root);

        TextView guide = new TextView(this);
        guide.setText("기본 3교대 순번은 주간/당직/비번만 사용합니다. 여기서 추가한 근무 종류는 날짜별 수동 변경에서 선택할 수 있습니다.");
        guide.setTextSize(14);
        guide.setTextColor(Color.DKGRAY);
        root.addView(guide, matchWrap());

        List<ShiftType> types = db.getAllShiftTypes();
        for (ShiftType type : types) {
            addShiftTypeRow(root, type);
        }

        Button addButton = new Button(this);
        addButton.setText("근무 종류 추가");
        root.addView(addButton, matchWrap());

        Button closeButton = new Button(this);
        closeButton.setText("닫기");
        root.addView(closeButton, matchWrap());

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("근무 종류 관리")
                .setView(scrollView)
                .create();
        addButton.setOnClickListener(v -> {
            dialog.dismiss();
            showAddShiftTypeDialog();
        });
        closeButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void addShiftTypeRow(LinearLayout root, final ShiftType type) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));

        TextView badge = new TextView(this);
        badge.setText(type.displayShortName());
        badge.setTextColor(contrastTextColor(type.color));
        badge.setGravity(Gravity.CENTER);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setBackground(makeRoundBackground(type.color, dp(6)));
        row.addView(badge, new LinearLayout.LayoutParams(dp(44), dp(34)));

        TextView info = new TextView(this);
        String fixed = type.isDefault ? "기본" : "사용자";
        info.setText(type.name + "  /  " + type.category + "  /  " + fixed);
        info.setTextSize(14);
        info.setPadding(dp(8), 0, dp(8), 0);
        row.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        if (!type.isDefault) {
            Button deleteButton = new Button(this);
            deleteButton.setText("삭제");
            deleteButton.setTextSize(12);
            deleteButton.setOnClickListener(v -> confirmDeleteShiftType(type));
            row.addView(deleteButton, new LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        root.addView(row, matchWrap());
    }

    private void showAddShiftTypeDialog() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(10), dp(18), 0);

        final EditText nameInput = new EditText(this);
        nameInput.setHint("근무 이름 예: 연차, 교육, 출장");
        nameInput.setSingleLine(true);
        root.addView(nameInput, matchWrap());

        final EditText shortInput = new EditText(this);
        shortInput.setHint("짧은 표시 예: 연, 교, 출");
        shortInput.setSingleLine(true);
        root.addView(shortInput, matchWrap());

        final EditText categoryInput = new EditText(this);
        categoryInput.setHint("분류 예: 근무, 휴무, 기타");
        categoryInput.setSingleLine(true);
        categoryInput.setText("기타");
        root.addView(categoryInput, matchWrap());

        final int[] selectedColor = new int[]{eventPalette[1]};
        final TextView colorPreview = new TextView(this);
        colorPreview.setText("선택된 색상");
        colorPreview.setGravity(Gravity.CENTER);
        colorPreview.setTextColor(Color.WHITE);
        colorPreview.setBackground(makeRoundBackground(selectedColor[0], dp(6)));
        colorPreview.setPadding(0, dp(8), 0, dp(8));
        root.addView(colorPreview, matchWrap());

        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int color : eventPalette) {
            Button colorButton = new Button(this);
            colorButton.setText(" ");
            colorButton.setBackground(makeRoundBackground(color, dp(8)));
            colorButton.setOnClickListener(v -> {
                selectedColor[0] = color;
                colorPreview.setBackground(makeRoundBackground(selectedColor[0], dp(6)));
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(46), 1);
            cp.setMargins(dp(2), dp(6), dp(2), dp(2));
            colorRow.addView(colorButton, cp);
        }
        root.addView(colorRow, matchWrap());

        new AlertDialog.Builder(this)
                .setTitle("근무 종류 추가")
                .setView(root)
                .setPositiveButton("저장", (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String shortName = shortInput.getText().toString().trim();
                    String category = categoryInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "근무 이름을 입력하세요.", Toast.LENGTH_SHORT).show();
                        showShiftTypesManager();
                        return;
                    }
                    if (shortName.isEmpty()) shortName = name.substring(0, Math.min(1, name.length()));
                    if (category.isEmpty()) category = "기타";
                    db.addShiftType(name, shortName, selectedColor[0], category);
                    Toast.makeText(this, "근무 종류를 추가했습니다.", Toast.LENGTH_SHORT).show();
                    renderMonth();
                    showShiftTypesManager();
                })
                .setNegativeButton("취소", (dialog, which) -> showShiftTypesManager())
                .show();
    }

    private void confirmDeleteShiftType(final ShiftType type) {
        if (db.isShiftTypeUsed(type.id)) {
            new AlertDialog.Builder(this)
                    .setTitle("삭제 불가")
                    .setMessage("'" + type.name + "'은 이미 수동 변경 날짜에서 사용 중이라 삭제할 수 없습니다. 해당 날짜를 자동 계산으로 되돌린 뒤 삭제하세요.")
                    .setPositiveButton("확인", null)
                    .show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("근무 종류 삭제")
                .setMessage("'" + type.name + "' 근무 종류를 삭제할까요?")
                .setPositiveButton("삭제", (dialog, which) -> {
                    boolean deleted = db.deleteCustomShiftType(type.id);
                    Toast.makeText(this, deleted ? "삭제했습니다. 근무 종류 관리 창을 닫고 다시 열면 반영됩니다." : "삭제하지 못했습니다.", Toast.LENGTH_SHORT).show();
                    renderMonth();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showHolidayInfo() {
        int year = currentMonth.getYear();
        List<String> lines = HolidayProvider.getPublicHolidayLinesForYear(year);
        StringBuilder sb = new StringBuilder();
        sb.append(HolidayProvider.getDataRangeText()).append("\n");
        sb.append("토요일/일요일은 자동으로 휴일 처리됩니다.\n\n");
        sb.append(year).append("년 공휴일\n");
        if (lines.isEmpty()) {
            sb.append("내장 공휴일 데이터가 없습니다.\n");
        } else {
            for (String line : lines) sb.append(line).append("\n");
        }
        sb.append("\n주의: 임시공휴일과 법 변경은 앱 업데이트가 필요할 수 있습니다.");
        new AlertDialog.Builder(this)
                .setTitle("공휴일 보기")
                .setMessage(sb.toString())
                .setPositiveButton("확인", null)
                .show();
    }

    private void showAboutDialog() {
        String message = "3교대 달력알람 v0.4-smooth-calendar\n\n" +
                "현재 버전 기능:\n" +
                "- 주간 → 당직 → 비번 반복 달력\n" +
                "- 주간 시작일 설정\n" +
                "- 토/일/공휴일 + 주간이면 주휴 표시\n" +
                "- 날짜별 수동 근무 변경\n" +
                "- 근무 종류 추가\n" +
                "- 기간 일정 색 라인 표시\n\n" +
                "알람 기능은 다음 단계에서 추가 예정입니다.";
        new AlertDialog.Builder(this)
                .setTitle("앱 정보")
                .setMessage(message)
                .setPositiveButton("확인", null)
                .show();
    }

    private GradientDrawable makeCellBackground(boolean inCurrentMonth, boolean isToday, boolean isSelected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(inCurrentMonth ? Color.WHITE : Color.rgb(250, 250, 250));
        if (isSelected) {
            drawable.setStroke(dp(2), Color.rgb(90, 200, 250));
        } else if (isToday) {
            drawable.setStroke(dp(1), Color.rgb(174, 220, 255));
        } else {
            drawable.setStroke(dp(1), Color.rgb(242, 242, 247));
        }
        return drawable;
    }

    private Button makeCompactButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setTextColor(Color.rgb(0, 122, 255));
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(1), 0, dp(1), 0);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(242, 242, 247));
        bg.setCornerRadius(dp(14));
        button.setBackground(bg);
        return button;
    }

    private int fadeColor(int color) {
        int r = (int) (Color.red(color) * 0.25f + 255 * 0.75f);
        int g = (int) (Color.green(color) * 0.25f + 255 * 0.75f);
        int b = (int) (Color.blue(color) * 0.25f + 255 * 0.75f);
        return Color.rgb(r, g, b);
    }

    private GradientDrawable makeCircleBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private GradientDrawable makeRoundBackground(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int contrastTextColor(int backgroundColor) {
        int r = Color.red(backgroundColor);
        int g = Color.green(backgroundColor);
        int b = Color.blue(backgroundColor);
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b);
        return luminance > 150 ? Color.BLACK : Color.WHITE;
    }

    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
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
