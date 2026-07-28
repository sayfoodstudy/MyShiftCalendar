package com.personal.shiftcalendaralarm;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.NumberPicker;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public class MainActivity extends Activity {
    private DatabaseHelper db;
    private LocalDate currentMonth;
    private TextView monthTitle;
    private TextView baseInfoText;
    private FrameLayout calendarFrame;
    private GridLayout calendarGrid;
    private GridLayout neighborCalendarGrid;
    private int neighborDelta;
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
        AlarmReceiver.createChannel(this);
        buildMainLayout();
        requestNotificationPermissionIfNeeded();
        renderMonth();
        if (!db.isBaseDateSet()) {
            showBaseDateDialog(true);
        }
    }

    private void buildMainLayout() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(6), getStatusBarHeight() + dp(8), dp(6), dp(8));
        scrollView.addView(root);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setPadding(0, 0, 0, dp(4));
        root.addView(topRow, matchWrap());

        TextView appTitle = new TextView(this);
        appTitle.setText("3교대달력");
        appTitle.setTextSize(24);
        appTitle.setTypeface(Typeface.DEFAULT_BOLD);
        appTitle.setTextColor(Color.rgb(28, 28, 30));
        appTitle.setGravity(Gravity.CENTER_VERTICAL);
        topRow.addView(appTitle, new LinearLayout.LayoutParams(0, dp(38), 1));

        Button alarmButton = makeCompactButton("⏰");
        alarmButton.setTextSize(18);
        alarmButton.setOnClickListener(v -> showAlarmManager());
        topRow.addView(alarmButton, new LinearLayout.LayoutParams(dp(42), dp(34)));

        Button settingsButton = makeCompactButton("설정");
        settingsButton.setOnClickListener(v -> showSettingsDialog());
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(dp(54), dp(34));
        settingsParams.setMargins(dp(5), 0, 0, 0);
        topRow.addView(settingsButton, settingsParams);

        LinearLayout navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        navRow.setGravity(Gravity.CENTER_VERTICAL);
        navRow.setPadding(0, 0, 0, dp(4));
        root.addView(navRow, matchWrap());

        Button prevButton = makeCompactButton("<");
        prevButton.setOnClickListener(v -> changeMonth(-1, true));
        navRow.addView(prevButton, new LinearLayout.LayoutParams(dp(38), dp(34)));

        monthTitle = new TextView(this);
        monthTitle.setTextSize(21);
        monthTitle.setTypeface(Typeface.DEFAULT_BOLD);
        monthTitle.setGravity(Gravity.CENTER);
        monthTitle.setSingleLine(true);
        monthTitle.setTextColor(Color.rgb(28, 28, 30));
        navRow.addView(monthTitle, new LinearLayout.LayoutParams(0, dp(34), 1));

        Button todayButton = makeCompactButton("오늘");
        todayButton.setOnClickListener(v -> {
            selectedDate = LocalDate.now();
            currentMonth = selectedDate.withDayOfMonth(1);
            renderMonth();
            updateSelectedMemoPanel(selectedDate);
        });
        navRow.addView(todayButton, new LinearLayout.LayoutParams(dp(52), dp(34)));

        Button nextButton = makeCompactButton(">");
        nextButton.setOnClickListener(v -> changeMonth(1, true));
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(dp(38), dp(34));
        nextParams.setMargins(dp(4), 0, 0, 0);
        navRow.addView(nextButton, nextParams);

        baseInfoText = new TextView(this);
        baseInfoText.setTextSize(11);
        baseInfoText.setSingleLine(true);
        baseInfoText.setTextColor(Color.rgb(142, 142, 147));
        baseInfoText.setPadding(dp(3), 0, dp(3), dp(4));
        root.addView(baseInfoText, matchWrap());

        calendarFrame = new FrameLayout(this);
        root.addView(calendarFrame, matchWrap());

        calendarGrid = new GridLayout(this);
        calendarGrid.setColumnCount(7);
        calendarGrid.setUseDefaultMargins(false);
        calendarFrame.addView(calendarGrid, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        neighborCalendarGrid = new GridLayout(this);
        neighborCalendarGrid.setColumnCount(7);
        neighborCalendarGrid.setUseDefaultMargins(false);
        neighborCalendarGrid.setVisibility(View.GONE);
        calendarFrame.addView(neighborCalendarGrid, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

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

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (calendarGrid == null || neighborCalendarGrid == null) return super.dispatchTouchEvent(event);

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            swipeStartX = event.getX();
            swipeStartY = event.getY();
            monthDragging = false;
            if (!monthAnimating) {
                calendarGrid.animate().cancel();
                neighborCalendarGrid.animate().cancel();
            }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (!monthAnimating) {
                float dx = event.getX() - swipeStartX;
                float dy = event.getY() - swipeStartY;
                if (monthDragging || (Math.abs(dy) > dp(26) && Math.abs(dy) > Math.abs(dx) * 1.45f)) {
                    monthDragging = true;
                    float height = Math.max(1, calendarGrid.getHeight());
                    float dragY = dy * 0.86f;
                    int delta = dragY < 0 ? 1 : -1;
                    prepareNeighborMonth(delta);
                    calendarGrid.setTranslationY(dragY);
                    neighborCalendarGrid.setTranslationY((delta > 0 ? height : -height) + dragY);
                    return true;
                }
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (monthDragging) {
                float dy = event.getY() - swipeStartY;
                float dragY = dy * 0.86f;
                int threshold = dp(120);
                long now = System.currentTimeMillis();
                if (Math.abs(dy) > threshold && now - lastMonthSwipeTime > 350) {
                    lastMonthSwipeTime = now;
                    changeMonthFromDrag(dy < 0 ? 1 : -1, dragY);
                } else {
                    cancelMonthDrag(dragY < 0 ? 1 : -1, dragY);
                }
                monthDragging = false;
                return true;
            }
        }
        return super.dispatchTouchEvent(event);
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

    private void prepareNeighborMonth(int delta) {
        if (neighborDelta == delta && neighborCalendarGrid.getVisibility() == View.VISIBLE) return;
        neighborDelta = delta;
        renderMonthIntoGrid(neighborCalendarGrid, currentMonth.plusMonths(delta));
        neighborCalendarGrid.setVisibility(View.VISIBLE);
        float height = Math.max(1, calendarGrid.getHeight());
        neighborCalendarGrid.setTranslationY(delta > 0 ? height : -height);
        neighborCalendarGrid.setTranslationX(0);
    }

    private void cancelMonthDrag(int delta, float dragTranslation) {
        float height = Math.max(1, calendarGrid.getHeight());
        calendarGrid.animate()
                .translationY(0)
                .setDuration(180)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        if (neighborCalendarGrid.getVisibility() == View.VISIBLE) {
            neighborCalendarGrid.animate()
                    .translationY(delta > 0 ? height : -height)
                    .setDuration(180)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> neighborCalendarGrid.setVisibility(View.GONE))
                    .start();
        }
    }

    private void animateMonthTransition(int delta, float startTranslation) {
        monthAnimating = true;
        prepareNeighborMonth(delta);
        float height = Math.max(1, calendarGrid.getHeight());
        float outY = delta > 0 ? -height : height;

        calendarGrid.animate().cancel();
        neighborCalendarGrid.animate().cancel();
        calendarGrid.setTranslationY(startTranslation);
        neighborCalendarGrid.setTranslationY((delta > 0 ? height : -height) + startTranslation);
        calendarGrid.setTranslationX(0);
        neighborCalendarGrid.setTranslationX(0);

        float remainingRatio = Math.abs(outY - startTranslation) / height;
        long duration = Math.max(120L, (long) (260L * remainingRatio));

        calendarGrid.animate()
                .translationY(outY)
                .setDuration(duration)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        neighborCalendarGrid.animate()
                .translationY(0)
                .setDuration(duration)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    currentMonth = currentMonth.plusMonths(delta);
                    selectedDate = currentMonth.withDayOfMonth(1);
                    calendarGrid.setTranslationX(0);
                    calendarGrid.setTranslationY(0);
                    neighborCalendarGrid.setTranslationX(0);
                    neighborCalendarGrid.setTranslationY(0);
                    renderMonth();
                    updateSelectedMemoPanel(selectedDate);
                    neighborCalendarGrid.setVisibility(View.GONE);
                    monthAnimating = false;
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

        renderMonthIntoGrid(calendarGrid, currentMonth);
        if (neighborCalendarGrid != null) neighborCalendarGrid.setVisibility(View.GONE);

        if (selectedDate == null || selectedDate.getYear() != currentMonth.getYear() ||
                selectedDate.getMonthValue() != currentMonth.getMonthValue()) {
            selectedDate = currentMonth.withDayOfMonth(1);
        }
        updateSelectedMemoPanel(selectedDate);
    }

    private void renderMonthIntoGrid(GridLayout grid, LocalDate displayMonth) {
        grid.removeAllViews();
        addWeekHeaders(grid);

        List<PeriodEvent> monthEvents = db.getEventsForMonth(displayMonth);
        LocalDate firstDay = displayMonth.withDayOfMonth(1);
        int startOffset = firstDay.getDayOfWeek().getValue() % 7;
        LocalDate gridStart = firstDay.minusDays(startOffset);
        LocalDate gridEnd = gridStart.plusDays(41);
        Map<Long, Integer> eventLaneMap = buildEventLaneMap(monthEvents, gridStart, gridEnd);

        for (int i = 0; i < 42; i++) {
            LocalDate date = gridStart.plusDays(i);
            grid.addView(createDayCell(date, monthEvents, eventLaneMap, displayMonth));
        }
    }

    private void addWeekHeaders(GridLayout grid) {
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
            grid.addView(tv);
        }
    }

    private View createDayCell(final LocalDate date, List<PeriodEvent> monthEvents, Map<Long, Integer> eventLaneMap, LocalDate displayMonth) {
        boolean inCurrentMonth = date.getMonthValue() == displayMonth.getMonthValue() && date.getYear() == displayMonth.getYear();
        boolean isToday = date.equals(LocalDate.now());
        boolean isSelected = selectedDate != null && date.equals(selectedDate);
        boolean isHoliday = db.isHoliday(date);
        String publicHolidayName = db.getHolidayLabel(date);

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
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(74);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(0, 0, 0, 0);
        cell.setLayoutParams(params);

        TextView dateText = new TextView(this);
        dateText.setText(String.valueOf(date.getDayOfMonth()));
        dateText.setTextSize(isToday ? 12 : 10);
        dateText.setTypeface(isToday ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        if (isToday) dateText.setPaintFlags(dateText.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
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

        PeriodEvent[] laneEvents = new PeriodEvent[3];
        int hiddenEventCount = 0;
        boolean hasAnyVisibleOrHiddenEvent = false;
        for (PeriodEvent event : monthEvents) {
            if (!event.covers(date)) continue;
            hasAnyVisibleOrHiddenEvent = true;
            Integer lane = eventLaneMap.get(event.id);
            if (lane != null && lane >= 0 && lane < 3) {
                laneEvents[lane] = event;
            } else {
                hiddenEventCount++;
            }
        }

        if (hasAnyVisibleOrHiddenEvent) {
            for (int lane = 0; lane < 3; lane++) {
                View bar = new View(this);
                if (laneEvents[lane] != null) {
                    int barColor = inCurrentMonth ? laneEvents[lane].color : fadeColor(laneEvents[lane].color);
                    bar.setBackgroundColor(barColor);
                } else {
                    bar.setBackgroundColor(Color.TRANSPARENT);
                }
                LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(4));
                barParams.setMargins(0, dp(1), 0, 0);
                cell.addView(bar, barParams);
            }
        }
        if (hiddenEventCount > 0) {
            TextView more = new TextView(this);
            more.setText("+" + hiddenEventCount);
            more.setTextSize(7);
            more.setGravity(Gravity.CENTER);
            more.setTextColor(inCurrentMonth ? Color.rgb(99, 99, 102) : Color.LTGRAY);
            cell.addView(more, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(9)));
        }
        return cell;
    }

    private Map<Long, Integer> buildEventLaneMap(List<PeriodEvent> events, LocalDate gridStart, LocalDate gridEnd) {
        Map<Long, Integer> result = new HashMap<>();
        List<PeriodEvent> sorted = new ArrayList<>(events);
        // 나중에 추가한 일정일수록 위쪽 라인을 우선 사용한다.
        sorted.sort((a, b) -> Long.compare(b.id, a.id));

        List<List<PeriodEvent>> lanes = new ArrayList<>();
        for (PeriodEvent event : sorted) {
            LocalDate clippedStart = event.startDate.isBefore(gridStart) ? gridStart : event.startDate;
            LocalDate clippedEnd = event.endDate.isAfter(gridEnd) ? gridEnd : event.endDate;
            int assignedLane = -1;

            for (int lane = 0; lane < lanes.size(); lane++) {
                boolean overlaps = false;
                for (PeriodEvent existing : lanes.get(lane)) {
                    LocalDate existingStart = existing.startDate.isBefore(gridStart) ? gridStart : existing.startDate;
                    LocalDate existingEnd = existing.endDate.isAfter(gridEnd) ? gridEnd : existing.endDate;
                    if (!clippedEnd.isBefore(existingStart) && !clippedStart.isAfter(existingEnd)) {
                        overlaps = true;
                        break;
                    }
                }
                if (!overlaps) {
                    assignedLane = lane;
                    break;
                }
            }

            if (assignedLane == -1) {
                assignedLane = lanes.size();
                lanes.add(new ArrayList<>());
            }
            lanes.get(assignedLane).add(event);
            result.put(event.id, assignedLane);
        }
        return result;
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
            sb.append("살짝 누르면 이 영역에 메모가 표시되고, 두 번 누르면 근무 변경/일정 추가로 들어갑니다.");
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
            sb.append("\n두 번 누르면 근무 변경/일정 추가.");
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
        return ShiftCalculator.calculate(db, date);
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
                .setView(wrapInScrollView(root))
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

    private void showAlarmManager() {
        requestNotificationPermissionIfNeeded();
        if (!AlarmScheduler.canScheduleExact(this)) {
            showExactAlarmPermissionDialog();
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(10), dp(18), dp(4));

        TextView guide = new TextView(this);
        guide.setText("알람 만들기와 알람 목록을 분리했습니다. 알람을 수정/삭제하려면 '알람 목록 보기'로 들어가세요.");
        guide.setTextSize(14);
        guide.setTextColor(Color.DKGRAY);
        root.addView(guide, matchWrap());

        Button listButton = new Button(this);
        listButton.setText("알람 목록 보기");
        root.addView(listButton, matchWrap());

        Button addBasic = new Button(this);
        addBasic.setText("기본 알람 만들기");
        root.addView(addBasic, matchWrap());

        Button addShift = new Button(this);
        addShift.setText("3교대 조건 알람 만들기");
        root.addView(addShift, matchWrap());

        Button permissionButton = new Button(this);
        permissionButton.setText("알람/배터리 권한 안내");
        root.addView(permissionButton, matchWrap());

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("알람")
                .setView(wrapInScrollView(root))
                .setNegativeButton("닫기", null)
                .create();

        listButton.setOnClickListener(v -> {
            dialog.dismiss();
            showAlarmList();
        });
        addBasic.setOnClickListener(v -> {
            dialog.dismiss();
            showAddBasicAlarmDialog();
        });
        addShift.setOnClickListener(v -> {
            dialog.dismiss();
            showAddShiftAlarmDialog();
        });
        permissionButton.setOnClickListener(v -> showAlarmPermissionGuide());
        dialog.show();
    }

    private void showAlarmList() {
        requestNotificationPermissionIfNeeded();

        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(8), dp(14), dp(8));
        scrollView.addView(root);

        TextView guide = new TextView(this);
        guide.setText("알람 목록입니다. 여기에서 알람을 켜기/끄기/수정/삭제할 수 있습니다.");
        guide.setTextSize(13);
        guide.setTextColor(Color.DKGRAY);
        root.addView(guide, matchWrap());

        List<AlarmItem> alarms = db.getAllAlarms();
        if (alarms.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("등록된 알람 없음");
            empty.setTextColor(Color.GRAY);
            empty.setPadding(0, dp(12), 0, dp(8));
            root.addView(empty, matchWrap());
        } else {
            for (AlarmItem alarm : alarms) addAlarmRow(root, alarm);
        }

        Button closeButton = new Button(this);
        closeButton.setText("닫기");
        root.addView(closeButton, matchWrap());

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("알람 목록")
                .setView(scrollView)
                .create();
        closeButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void addAlarmRow(LinearLayout root, final AlarmItem alarm) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(242, 242, 247));
        bg.setCornerRadius(dp(12));
        box.setBackground(bg);
        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        boxParams.setMargins(0, dp(8), 0, dp(4));
        root.addView(box, boxParams);

        TextView title = new TextView(this);
        title.setText((alarm.enabled ? "● " : "○ ") + alarm.title);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(alarm.enabled ? Color.rgb(28, 28, 30) : Color.GRAY);
        box.addView(title, matchWrap());

        TextView desc = new TextView(this);
        desc.setText(describeAlarm(alarm));
        desc.setTextSize(13);
        desc.setTextColor(Color.rgb(72, 72, 74));
        desc.setPadding(0, dp(4), 0, dp(4));
        box.addView(desc, matchWrap());

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(buttons, matchWrap());

        Button toggle = new Button(this);
        toggle.setText(alarm.enabled ? "끄기" : "켜기");
        buttons.addView(toggle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button edit = new Button(this);
        edit.setText("수정");
        buttons.addView(edit, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button delete = new Button(this);
        delete.setText("삭제");
        buttons.addView(delete, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        toggle.setOnClickListener(v -> {
            if (alarm.enabled) {
                db.updateAlarmEnabled(alarm.id, false);
                db.updateAlarmNextTrigger(alarm.id, -1L);
                AlarmScheduler.cancelAlarm(this, alarm.id);
                alarm.enabled = false;
                alarm.nextTriggerMillis = -1L;
                Toast.makeText(this, "알람을 껐습니다.", Toast.LENGTH_SHORT).show();
            } else {
                db.updateAlarmEnabled(alarm.id, true);
                alarm.enabled = true;
                AlarmScheduler.scheduleAlarm(this, alarm.id);
                AlarmItem updated = db.getAlarm(alarm.id);
                if (updated != null) alarm.nextTriggerMillis = updated.nextTriggerMillis;
                Toast.makeText(this, "알람을 켰습니다.", Toast.LENGTH_SHORT).show();
            }
            title.setText((alarm.enabled ? "● " : "○ ") + alarm.title);
            title.setTextColor(alarm.enabled ? Color.rgb(28, 28, 30) : Color.GRAY);
            desc.setText(describeAlarm(alarm));
            toggle.setText(alarm.enabled ? "끄기" : "켜기");
        });
        edit.setOnClickListener(v -> {
            if (AlarmItem.MODE_SHIFT.equals(alarm.alarmMode)) showEditShiftAlarmDialog(alarm, title, desc, toggle);
            else showEditBasicAlarmDialog(alarm, title, desc, toggle);
        });
        delete.setOnClickListener(v -> confirmDeleteAlarm(alarm, root, box));
    }

    private void confirmDeleteAlarm(final AlarmItem alarm, final LinearLayout listRoot, final View alarmBox) {
        new AlertDialog.Builder(this)
                .setTitle("알람 삭제")
                .setMessage("'" + alarm.title + "' 알람을 삭제할까요?")
                .setPositiveButton("삭제", (dialog, which) -> {
                    AlarmScheduler.cancelAlarm(this, alarm.id);
                    db.deleteAlarm(alarm.id);
                    listRoot.removeView(alarmBox);
                    Toast.makeText(this, "알람을 삭제했습니다.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showAddBasicAlarmDialog() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(10), dp(18), 0);

        final EditText titleInput = new EditText(this);
        titleInput.setHint("알람 제목 예: 약 먹기");
        titleInput.setSingleLine(true);
        titleInput.setText("알람");
        root.addView(titleInput, matchWrap());

        final EditText memoInput = new EditText(this);
        memoInput.setHint("메모 선택사항");
        memoInput.setMinLines(2);
        memoInput.setGravity(Gravity.TOP);
        root.addView(memoInput, matchWrap());

        java.time.LocalDateTime defaultDateTime = java.time.LocalDateTime.now().plusMinutes(2);
        final LocalDate[] selectedDate = new LocalDate[]{defaultDateTime.toLocalDate()};
        final TextView dateText = new TextView(this);
        dateText.setText("날짜: " + DateUtil.iso(selectedDate[0]));
        dateText.setTextSize(15);
        root.addView(dateText, matchWrap());

        Button dateButton = new Button(this);
        dateButton.setText("날짜 선택");
        dateButton.setOnClickListener(v -> pickDate(selectedDate[0], picked -> {
            selectedDate[0] = picked;
            dateText.setText("날짜: " + DateUtil.iso(selectedDate[0]));
        }));
        root.addView(dateButton, matchWrap());

        java.time.LocalTime defaultTime = defaultDateTime.toLocalTime();
        final int[] alarmTime = new int[]{defaultTime.getHour(), defaultTime.getMinute()};
        root.addView(createTimeWheelView(alarmTime), matchWrap());

        final String[] repeatType = new String[]{AlarmItem.REPEAT_ONCE};
        Button repeatButton = new Button(this);
        repeatButton.setText("반복: 한번만");
        repeatButton.setOnClickListener(v -> showRepeatPicker(repeatType, repeatButton));
        root.addView(repeatButton, matchWrap());

        new AlertDialog.Builder(this)
                .setTitle("기본 알람 추가")
                .setView(wrapInScrollView(root))
                .setPositiveButton("저장", (dialog, which) -> {
                    int hour = alarmTime[0];
                    int minute = alarmTime[1];
                    if (AlarmItem.REPEAT_ONCE.equals(repeatType[0])) {
                        java.time.LocalDateTime candidate = java.time.LocalDateTime.of(
                                selectedDate[0], java.time.LocalTime.of(hour, minute));
                        if (!candidate.isAfter(java.time.LocalDateTime.now())) {
                            Toast.makeText(this, "한번만 알람은 미래 시간을 선택해야 합니다.", Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                    long id = db.addAlarm(
                            titleInput.getText().toString().trim(),
                            memoInput.getText().toString().trim(),
                            AlarmItem.MODE_BASIC,
                            hour,
                            minute,
                            selectedDate[0],
                            repeatType[0],
                            AlarmItem.SHIFT_ANY,
                            AlarmItem.HOLIDAY_ANY,
                            AlarmItem.WEEKDAY_ALL_MASK,
                            true);
                    AlarmScheduler.scheduleAlarm(this, id);
                    Toast.makeText(this, "알람을 추가했습니다.", Toast.LENGTH_SHORT).show();
                    showAlarmList();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showAddShiftAlarmDialog() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(10), dp(18), 0);

        final EditText titleInput = new EditText(this);
        titleInput.setHint("알람 제목 예: 주간 출근 준비");
        titleInput.setSingleLine(true);
        titleInput.setText("3교대 알람");
        root.addView(titleInput, matchWrap());

        final EditText memoInput = new EditText(this);
        memoInput.setHint("메모 선택사항");
        memoInput.setMinLines(2);
        memoInput.setGravity(Gravity.TOP);
        root.addView(memoInput, matchWrap());

        final int[] alarmTime = new int[]{8, 0};
        root.addView(createTimeWheelView(alarmTime), matchWrap());

        final LocalDate[] startDate = new LocalDate[]{LocalDate.now()};
        final TextView startText = new TextView(this);
        startText.setText("적용 시작일: " + DateUtil.iso(startDate[0]));
        startText.setTextSize(15);
        root.addView(startText, matchWrap());

        Button startButton = new Button(this);
        startButton.setText("적용 시작일 선택");
        startButton.setOnClickListener(v -> pickDate(startDate[0], picked -> {
            startDate[0] = picked;
            startText.setText("적용 시작일: " + DateUtil.iso(startDate[0]));
        }));
        root.addView(startButton, matchWrap());

        final long[] selectedShiftId = new long[]{AlarmItem.SHIFT_ANY};
        Button shiftButton = new Button(this);
        shiftButton.setText("근무 조건: 전체");
        shiftButton.setOnClickListener(v -> showShiftConditionPicker(selectedShiftId, shiftButton));
        root.addView(shiftButton, matchWrap());

        final int[] holidayFilter = new int[]{AlarmItem.HOLIDAY_ANY};
        Button holidayButton = new Button(this);
        holidayButton.setText("휴일 조건: 상관없음");
        holidayButton.setOnClickListener(v -> showHolidayFilterPicker(holidayFilter, holidayButton));
        root.addView(holidayButton, matchWrap());

        final int[] weekdayMask = new int[]{AlarmItem.WEEKDAY_ALL_MASK};
        Button weekdayButton = new Button(this);
        weekdayButton.setText("요일 조건: 전체");
        weekdayButton.setOnClickListener(v -> showWeekdayPicker(weekdayMask, weekdayButton));
        root.addView(weekdayButton, matchWrap());

        TextView tip = new TextView(this);
        tip.setText("예: 당직 + 평일 공휴일 알람은 근무=당직, 휴일=휴일만, 요일=월~금으로 설정하세요.");
        tip.setTextSize(12);
        tip.setTextColor(Color.DKGRAY);
        tip.setPadding(0, dp(6), 0, 0);
        root.addView(tip, matchWrap());

        new AlertDialog.Builder(this)
                .setTitle("3교대 조건 알람 추가")
                .setView(wrapInScrollView(root))
                .setPositiveButton("저장", (dialog, which) -> {
                    int hour = alarmTime[0];
                    int minute = alarmTime[1];
                    long id = db.addAlarm(
                            titleInput.getText().toString().trim(),
                            memoInput.getText().toString().trim(),
                            AlarmItem.MODE_SHIFT,
                            hour,
                            minute,
                            startDate[0],
                            AlarmItem.REPEAT_DAILY,
                            selectedShiftId[0],
                            holidayFilter[0],
                            weekdayMask[0],
                            true);
                    AlarmScheduler.scheduleAlarm(this, id);
                    Toast.makeText(this, "3교대 조건 알람을 추가했습니다.", Toast.LENGTH_SHORT).show();
                    showAlarmList();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showEditBasicAlarmDialog(final AlarmItem alarm, final TextView rowTitle, final TextView rowDesc, final Button rowToggle) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(10), dp(18), 0);

        final EditText titleInput = new EditText(this);
        titleInput.setHint("알람 제목");
        titleInput.setSingleLine(true);
        titleInput.setText(alarm.title);
        root.addView(titleInput, matchWrap());

        final EditText memoInput = new EditText(this);
        memoInput.setHint("메모 선택사항");
        memoInput.setMinLines(2);
        memoInput.setGravity(Gravity.TOP);
        memoInput.setText(alarm.memo == null ? "" : alarm.memo);
        root.addView(memoInput, matchWrap());

        final LocalDate[] selectedDate = new LocalDate[]{DateUtil.parse(alarm.startDate)};
        final TextView dateText = new TextView(this);
        dateText.setText("날짜: " + DateUtil.iso(selectedDate[0]));
        dateText.setTextSize(15);
        root.addView(dateText, matchWrap());

        Button dateButton = new Button(this);
        dateButton.setText("날짜 선택");
        dateButton.setOnClickListener(v -> pickDate(selectedDate[0], picked -> {
            selectedDate[0] = picked;
            dateText.setText("날짜: " + DateUtil.iso(selectedDate[0]));
        }));
        root.addView(dateButton, matchWrap());

        final int[] alarmTime = new int[]{alarm.hour, alarm.minute};
        root.addView(createTimeWheelView(alarmTime), matchWrap());

        final String[] repeatType = new String[]{alarm.repeatType};
        Button repeatButton = new Button(this);
        repeatButton.setText("반복: " + repeatLabel(repeatType[0]));
        repeatButton.setOnClickListener(v -> showRepeatPicker(repeatType, repeatButton));
        root.addView(repeatButton, matchWrap());

        new AlertDialog.Builder(this)
                .setTitle("기본 알람 수정")
                .setView(wrapInScrollView(root))
                .setPositiveButton("저장", (dialog, which) -> {
                    int hour = alarmTime[0];
                    int minute = alarmTime[1];
                    if (AlarmItem.REPEAT_ONCE.equals(repeatType[0])) {
                        java.time.LocalDateTime candidate = java.time.LocalDateTime.of(
                                selectedDate[0], java.time.LocalTime.of(hour, minute));
                        if (!candidate.isAfter(java.time.LocalDateTime.now())) {
                            Toast.makeText(this, "한번만 알람은 미래 시간을 선택해야 합니다.", Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                    AlarmScheduler.cancelAlarm(this, alarm.id);
                    db.updateAlarm(
                            alarm.id,
                            titleInput.getText().toString().trim(),
                            memoInput.getText().toString().trim(),
                            AlarmItem.MODE_BASIC,
                            true,
                            hour,
                            minute,
                            selectedDate[0],
                            repeatType[0],
                            AlarmItem.SHIFT_ANY,
                            AlarmItem.HOLIDAY_ANY,
                            AlarmItem.WEEKDAY_ALL_MASK,
                            true);
                    AlarmScheduler.scheduleAlarm(this, alarm.id);
                    refreshAlarmRowAfterEdit(alarm, rowTitle, rowDesc, rowToggle);
                    Toast.makeText(this, "알람을 수정했습니다.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showEditShiftAlarmDialog(final AlarmItem alarm, final TextView rowTitle, final TextView rowDesc, final Button rowToggle) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(10), dp(18), 0);

        final EditText titleInput = new EditText(this);
        titleInput.setHint("알람 제목");
        titleInput.setSingleLine(true);
        titleInput.setText(alarm.title);
        root.addView(titleInput, matchWrap());

        final EditText memoInput = new EditText(this);
        memoInput.setHint("메모 선택사항");
        memoInput.setMinLines(2);
        memoInput.setGravity(Gravity.TOP);
        memoInput.setText(alarm.memo == null ? "" : alarm.memo);
        root.addView(memoInput, matchWrap());

        final int[] alarmTime = new int[]{alarm.hour, alarm.minute};
        root.addView(createTimeWheelView(alarmTime), matchWrap());

        final LocalDate[] startDate = new LocalDate[]{DateUtil.parse(alarm.startDate)};
        final TextView startText = new TextView(this);
        startText.setText("적용 시작일: " + DateUtil.iso(startDate[0]));
        startText.setTextSize(15);
        root.addView(startText, matchWrap());

        Button startButton = new Button(this);
        startButton.setText("적용 시작일 선택");
        startButton.setOnClickListener(v -> pickDate(startDate[0], picked -> {
            startDate[0] = picked;
            startText.setText("적용 시작일: " + DateUtil.iso(startDate[0]));
        }));
        root.addView(startButton, matchWrap());

        final long[] selectedShiftId = new long[]{alarm.shiftTypeId};
        Button shiftButton = new Button(this);
        shiftButton.setText("근무 조건: " + shiftNameForAlarm(selectedShiftId[0]));
        shiftButton.setOnClickListener(v -> showShiftConditionPicker(selectedShiftId, shiftButton));
        root.addView(shiftButton, matchWrap());

        final int[] holidayFilter = new int[]{alarm.holidayFilter};
        Button holidayButton = new Button(this);
        holidayButton.setText("휴일 조건: " + holidayFilterLabel(holidayFilter[0]));
        holidayButton.setOnClickListener(v -> showHolidayFilterPicker(holidayFilter, holidayButton));
        root.addView(holidayButton, matchWrap());

        final int[] weekdayMask = new int[]{alarm.weekdayMask};
        Button weekdayButton = new Button(this);
        weekdayButton.setText("요일 조건: " + weekdayMaskLabel(weekdayMask[0]));
        weekdayButton.setOnClickListener(v -> showWeekdayPicker(weekdayMask, weekdayButton));
        root.addView(weekdayButton, matchWrap());

        TextView tip = new TextView(this);
        tip.setText("예: 당직 + 평일 공휴일 알람은 근무=당직, 휴일=휴일만, 요일=월~금으로 설정하세요.");
        tip.setTextSize(12);
        tip.setTextColor(Color.DKGRAY);
        tip.setPadding(0, dp(6), 0, 0);
        root.addView(tip, matchWrap());

        new AlertDialog.Builder(this)
                .setTitle("3교대 조건 알람 수정")
                .setView(wrapInScrollView(root))
                .setPositiveButton("저장", (dialog, which) -> {
                    int hour = alarmTime[0];
                    int minute = alarmTime[1];
                    AlarmScheduler.cancelAlarm(this, alarm.id);
                    db.updateAlarm(
                            alarm.id,
                            titleInput.getText().toString().trim(),
                            memoInput.getText().toString().trim(),
                            AlarmItem.MODE_SHIFT,
                            true,
                            hour,
                            minute,
                            startDate[0],
                            AlarmItem.REPEAT_DAILY,
                            selectedShiftId[0],
                            holidayFilter[0],
                            weekdayMask[0],
                            true);
                    AlarmScheduler.scheduleAlarm(this, alarm.id);
                    refreshAlarmRowAfterEdit(alarm, rowTitle, rowDesc, rowToggle);
                    Toast.makeText(this, "조건 알람을 수정했습니다.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void refreshAlarmRowAfterEdit(AlarmItem alarm, TextView rowTitle, TextView rowDesc, Button rowToggle) {
        AlarmItem updated = db.getAlarm(alarm.id);
        if (updated == null) return;
        alarm.title = updated.title;
        alarm.memo = updated.memo;
        alarm.alarmMode = updated.alarmMode;
        alarm.enabled = updated.enabled;
        alarm.hour = updated.hour;
        alarm.minute = updated.minute;
        alarm.startDate = updated.startDate;
        alarm.repeatType = updated.repeatType;
        alarm.shiftTypeId = updated.shiftTypeId;
        alarm.holidayFilter = updated.holidayFilter;
        alarm.weekdayMask = updated.weekdayMask;
        alarm.vibrate = updated.vibrate;
        alarm.nextTriggerMillis = updated.nextTriggerMillis;
        rowTitle.setText((alarm.enabled ? "● " : "○ ") + alarm.title);
        rowTitle.setTextColor(alarm.enabled ? Color.rgb(28, 28, 30) : Color.GRAY);
        rowDesc.setText(describeAlarm(alarm));
        rowToggle.setText(alarm.enabled ? "끄기" : "켜기");
    }

    private void showRepeatPicker(final String[] repeatType, final Button button) {
        String[] labels = new String[]{"한번만", "매일", "매주", "매월", "매년"};
        String[] values = new String[]{AlarmItem.REPEAT_ONCE, AlarmItem.REPEAT_DAILY, AlarmItem.REPEAT_WEEKLY,
                AlarmItem.REPEAT_MONTHLY, AlarmItem.REPEAT_YEARLY};
        new AlertDialog.Builder(this)
                .setTitle("반복 선택")
                .setItems(labels, (dialog, which) -> {
                    repeatType[0] = values[which];
                    button.setText("반복: " + labels[which]);
                })
                .show();
    }

    private void showShiftConditionPicker(final long[] selectedShiftId, final Button button) {
        List<ShiftType> types = db.getAllShiftTypes();
        String[] items = new String[types.size() + 1];
        items[0] = "전체";
        for (int i = 0; i < types.size(); i++) items[i + 1] = types.get(i).name;
        new AlertDialog.Builder(this)
                .setTitle("근무 조건")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        selectedShiftId[0] = AlarmItem.SHIFT_ANY;
                        button.setText("근무 조건: 전체");
                    } else {
                        ShiftType type = types.get(which - 1);
                        selectedShiftId[0] = type.id;
                        button.setText("근무 조건: " + type.name);
                    }
                })
                .show();
    }

    private void showHolidayFilterPicker(final int[] filter, final Button button) {
        String[] labels = new String[]{"상관없음", "휴일만", "평일만"};
        int[] values = new int[]{AlarmItem.HOLIDAY_ANY, AlarmItem.HOLIDAY_ONLY, AlarmItem.HOLIDAY_WEEKDAY_ONLY};
        new AlertDialog.Builder(this)
                .setTitle("휴일 조건")
                .setItems(labels, (dialog, which) -> {
                    filter[0] = values[which];
                    button.setText("휴일 조건: " + labels[which]);
                })
                .show();
    }

    private void showWeekdayPicker(final int[] weekdayMask, final Button button) {
        String[] labels = new String[]{"월", "화", "수", "목", "금", "토", "일"};
        boolean[] checked = new boolean[7];
        for (int i = 0; i < 7; i++) checked[i] = (weekdayMask[0] & (1 << i)) != 0;
        new AlertDialog.Builder(this)
                .setTitle("요일 조건")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("확인", (dialog, which) -> {
                    int mask = 0;
                    for (int i = 0; i < 7; i++) if (checked[i]) mask |= (1 << i);
                    if (mask == 0) {
                        mask = AlarmItem.WEEKDAY_ALL_MASK;
                        Toast.makeText(this, "요일을 선택하지 않아 전체로 설정했습니다.", Toast.LENGTH_SHORT).show();
                    }
                    weekdayMask[0] = mask;
                    button.setText("요일 조건: " + weekdayMaskLabel(mask));
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private View createTimeWheelView(final int[] alarmTime) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(0, dp(8), 0, dp(8));

        TextView title = new TextView(this);
        title.setText("시간 설정");
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(28, 28, 30));
        wrapper.addView(title, matchWrap());

        final TextView preview = new TextView(this);
        preview.setText("선택 시간: " + AlarmCalculator.formatTime(alarmTime[0], alarmTime[1]));
        preview.setTextSize(14);
        preview.setTextColor(Color.rgb(0, 122, 255));
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(0, dp(4), 0, dp(4));
        wrapper.addView(preview, matchWrap());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, dp(2), 0, dp(2));

        NumberPicker hourPicker = createNumberWheel(0, 23, alarmTime[0]);
        NumberPicker minutePicker = createNumberWheel(0, 59, alarmTime[1]);

        hourPicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
            alarmTime[0] = newVal;
            preview.setText("선택 시간: " + AlarmCalculator.formatTime(alarmTime[0], alarmTime[1]));
        });
        minutePicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
            alarmTime[1] = newVal;
            preview.setText("선택 시간: " + AlarmCalculator.formatTime(alarmTime[0], alarmTime[1]));
        });

        LinearLayout hourBox = makeWheelBox("시", hourPicker);
        LinearLayout minuteBox = makeWheelBox("분", minutePicker);

        TextView colon = new TextView(this);
        colon.setText(":");
        colon.setTextSize(32);
        colon.setTypeface(Typeface.DEFAULT_BOLD);
        colon.setGravity(Gravity.CENTER);
        colon.setTextColor(Color.rgb(99, 99, 102));

        row.addView(hourBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(colon, new LinearLayout.LayoutParams(dp(28), LinearLayout.LayoutParams.MATCH_PARENT));
        row.addView(minuteBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        wrapper.addView(row, matchWrap());

        TextView guide = new TextView(this);
        guide.setText("시/분 숫자를 위아래로 드래그해서 맞추세요.");
        guide.setTextSize(12);
        guide.setGravity(Gravity.CENTER);
        guide.setTextColor(Color.GRAY);
        wrapper.addView(guide, matchWrap());

        return wrapper;
    }

    private NumberPicker createNumberWheel(int min, int max, int value) {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setValue(value);
        picker.setWrapSelectorWheel(true);
        picker.setFormatter(v -> String.format(Locale.KOREAN, "%02d", v));
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        picker.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
        return picker;
    }

    private LinearLayout makeWheelBox(String label, NumberPicker picker) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(13);
        labelView.setGravity(Gravity.CENTER);
        labelView.setTextColor(Color.rgb(99, 99, 102));
        box.addView(labelView, matchWrap());

        LinearLayout.LayoutParams pickerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(116));
        box.addView(picker, pickerParams);
        return box;
    }

    private String describeAlarm(AlarmItem alarm) {
        StringBuilder sb = new StringBuilder();
        sb.append(AlarmCalculator.formatTime(alarm.hour, alarm.minute)).append("  ");
        if (AlarmItem.MODE_SHIFT.equals(alarm.alarmMode)) {
            sb.append("3교대 조건");
            sb.append(" / 근무: ").append(shiftNameForAlarm(alarm.shiftTypeId));
            sb.append(" / 휴일: ").append(holidayFilterLabel(alarm.holidayFilter));
            sb.append(" / 요일: ").append(weekdayMaskLabel(alarm.weekdayMask));
        } else {
            sb.append("기본 알람 / ").append(repeatLabel(alarm.repeatType));
            sb.append(" / 시작: ").append(alarm.startDate);
        }
        sb.append("\n다음: ").append(formatTrigger(alarm.nextTriggerMillis));
        return sb.toString();
    }

    private String shiftNameForAlarm(long shiftTypeId) {
        if (shiftTypeId == AlarmItem.SHIFT_ANY) return "전체";
        ShiftType type = db.getShiftTypeById(shiftTypeId);
        return type == null ? "삭제된 근무" : type.name;
    }

    private String repeatLabel(String repeatType) {
        if (AlarmItem.REPEAT_DAILY.equals(repeatType)) return "매일";
        if (AlarmItem.REPEAT_WEEKLY.equals(repeatType)) return "매주";
        if (AlarmItem.REPEAT_MONTHLY.equals(repeatType)) return "매월";
        if (AlarmItem.REPEAT_YEARLY.equals(repeatType)) return "매년";
        return "한번만";
    }

    private String holidayFilterLabel(int filter) {
        if (filter == AlarmItem.HOLIDAY_ONLY) return "휴일만";
        if (filter == AlarmItem.HOLIDAY_WEEKDAY_ONLY) return "평일만";
        return "상관없음";
    }

    private String weekdayMaskLabel(int mask) {
        if (mask == AlarmItem.WEEKDAY_ALL_MASK) return "전체";
        String[] labels = new String[]{"월", "화", "수", "목", "금", "토", "일"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((mask & (1 << i)) != 0) {
                if (sb.length() > 0) sb.append(",");
                sb.append(labels[i]);
            }
        }
        return sb.length() == 0 ? "전체" : sb.toString();
    }

    private String formatTrigger(long triggerMillis) {
        if (triggerMillis <= 0) return "예약 없음";
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREAN);
        return sdf.format(new Date(triggerMillis));
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7001);
            }
        }
    }

    private void showExactAlarmPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("정확한 알람 권한 필요")
                .setMessage("삼성폰에서 알람을 안정적으로 울리려면 '알람 및 리마인더' 또는 정확한 알람 권한을 허용하는 것이 좋습니다.")
                .setPositiveButton("설정 열기", (dialog, which) -> {
                    try {
                        startActivity(AlarmScheduler.exactAlarmSettingsIntent());
                    } catch (Exception e) {
                        openAppSettings();
                    }
                })
                .setNegativeButton("나중에", null)
                .show();
    }

    private void showAlarmPermissionGuide() {
        String message = "알람 안정화 설정\n\n" +
                "1. 앱 알림 허용\n" +
                "2. 정확한 알람/알람 및 리마인더 허용\n" +
                "3. 가능하면 전체화면 알림 허용\n" +
                "4. 앱 정보 → 배터리 → 제한 없음\n" +
                "5. 방해금지 모드 확인\n\n" +
                "테스트는 2분 뒤 기본 알람을 만들어서 화면을 끄고 확인하는 것을 추천합니다.";
        new AlertDialog.Builder(this)
                .setTitle("알람/배터리 권한 안내")
                .setMessage(message)
                .setPositiveButton("앱 설정", (dialog, which) -> openAppSettings())
                .setNegativeButton("정확한 알람 설정", (dialog, which) -> {
                    try {
                        startActivity(AlarmScheduler.exactAlarmSettingsIntent());
                    } catch (Exception e) {
                        openAppSettings();
                    }
                })
                .setNeutralButton("닫기", null)
                .show();
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void showSettingsDialog() {
        String[] items = new String[]{
                "주간 시작일 설정",
                "근무 종류 관리",
                "휴일 수동 관리",
                "공휴일 보기",
                "앱 정보"
        };
        new AlertDialog.Builder(this)
                .setTitle("설정")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) showBaseDateDialog(false);
                    else if (which == 1) showShiftTypesManager();
                    else if (which == 2) showManualHolidayManager();
                    else if (which == 3) showHolidayInfo();
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
                .setView(wrapInScrollView(root))
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
        badge.setGravity(Gravity.CENTER);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        applyShiftBadgeStyle(badge, type);
        row.addView(badge, new LinearLayout.LayoutParams(dp(44), dp(34)));

        TextView info = new TextView(this);
        updateShiftTypeInfoText(info, type);
        info.setTextSize(14);
        info.setPadding(dp(8), 0, dp(8), 0);
        row.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button editButton = new Button(this);
        editButton.setText("수정");
        editButton.setTextSize(12);
        editButton.setOnClickListener(v -> showEditShiftTypeDialog(type, badge, info));
        row.addView(editButton, new LinearLayout.LayoutParams(dp(62), LinearLayout.LayoutParams.WRAP_CONTENT));

        if (!type.isDefault) {
            Button conditionButton = new Button(this);
            conditionButton.setText("조건");
            conditionButton.setTextSize(12);
            conditionButton.setOnClickListener(v -> showShiftTypeConditionDialog(type));
            row.addView(conditionButton, new LinearLayout.LayoutParams(dp(62), LinearLayout.LayoutParams.WRAP_CONTENT));

            Button deleteButton = new Button(this);
            deleteButton.setText("삭제");
            deleteButton.setTextSize(12);
            deleteButton.setOnClickListener(v -> confirmDeleteShiftType(type, root, row));
            row.addView(deleteButton, new LinearLayout.LayoutParams(dp(62), LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        root.addView(row, matchWrap());
    }

    private void applyShiftBadgeStyle(TextView badge, ShiftType type) {
        badge.setText(type.displayShortName());
        badge.setTextColor(contrastTextColor(type.color));
        badge.setBackground(makeRoundBackground(type.color, dp(6)));
    }

    private void updateShiftTypeInfoText(TextView info, ShiftType type) {
        String fixed = type.isDefault ? "기본" : "사용자";
        info.setText(type.name + "  /  " + type.category + "  /  " + fixed + describeShiftConditionShort(type));
    }

    private String describeShiftConditionShort(ShiftType type) {
        if (!type.autoConditionEnabled) return "";
        return "  /  자동조건: " + shiftNameForAlarm(type.conditionBaseShiftTypeId)
                + "+" + holidayFilterLabel(type.conditionHolidayFilter)
                + "+" + weekdayMaskLabel(type.conditionWeekdayMask);
    }

    private void showShiftTypeConditionDialog(final ShiftType type) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(10), dp(18), 0);

        TextView guide = new TextView(this);
        guide.setText("조건을 켜면 달력에서 해당 조건에 맞는 날이 이 근무 종류로 자동 표시됩니다. 수동 근무 변경이 있는 날짜는 수동 변경이 우선합니다.\n\n" +
                "예: '휴일당직' 근무를 만들고 근무 조건=당직, 휴일 조건=휴일만으로 설정하면 당직+휴일인 날이 자동으로 휴일당직으로 표시됩니다.");
        guide.setTextSize(13);
        guide.setTextColor(Color.DKGRAY);
        root.addView(guide, matchWrap());

        final boolean[] enabled = new boolean[]{type.autoConditionEnabled};
        Button enabledButton = new Button(this);
        enabledButton.setText(enabled[0] ? "자동 조건: 켜짐" : "자동 조건: 꺼짐");
        enabledButton.setOnClickListener(v -> {
            enabled[0] = !enabled[0];
            enabledButton.setText(enabled[0] ? "자동 조건: 켜짐" : "자동 조건: 꺼짐");
        });
        root.addView(enabledButton, matchWrap());

        final long[] baseShiftId = new long[]{type.conditionBaseShiftTypeId};
        Button baseButton = new Button(this);
        baseButton.setText("근무 조건: " + shiftNameForAlarm(baseShiftId[0]));
        baseButton.setOnClickListener(v -> showShiftConditionPicker(baseShiftId, baseButton));
        root.addView(baseButton, matchWrap());

        final int[] holidayFilter = new int[]{type.conditionHolidayFilter};
        Button holidayButton = new Button(this);
        holidayButton.setText("휴일 조건: " + holidayFilterLabel(holidayFilter[0]));
        holidayButton.setOnClickListener(v -> showHolidayFilterPicker(holidayFilter, holidayButton));
        root.addView(holidayButton, matchWrap());

        final int[] weekdayMask = new int[]{type.conditionWeekdayMask};
        Button weekdayButton = new Button(this);
        weekdayButton.setText("요일 조건: " + weekdayMaskLabel(weekdayMask[0]));
        weekdayButton.setOnClickListener(v -> showWeekdayPicker(weekdayMask, weekdayButton));
        root.addView(weekdayButton, matchWrap());

        new AlertDialog.Builder(this)
                .setTitle(type.name + " 자동 조건")
                .setView(wrapInScrollView(root))
                .setPositiveButton("저장", (dialog, which) -> {
                    db.updateShiftTypeCondition(type.id, enabled[0], baseShiftId[0], holidayFilter[0], weekdayMask[0]);
                    AlarmScheduler.scheduleAllEnabled(this);
                    Toast.makeText(this, "근무 자동 조건을 저장했습니다.", Toast.LENGTH_SHORT).show();
                    renderMonth();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showEditShiftTypeDialog(final ShiftType type, final TextView badge, final TextView info) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(10), dp(18), 0);

        final EditText nameInput = new EditText(this);
        nameInput.setHint("근무 이름");
        nameInput.setSingleLine(true);
        nameInput.setText(type.name);
        root.addView(nameInput, matchWrap());

        final EditText shortInput = new EditText(this);
        shortInput.setHint("짧은 표시 예: 주간, 당직, 휴일당직");
        shortInput.setSingleLine(true);
        shortInput.setText(type.shortName);
        root.addView(shortInput, matchWrap());

        final EditText categoryInput = new EditText(this);
        categoryInput.setHint("분류 예: 근무, 휴무, 기타");
        categoryInput.setSingleLine(true);
        categoryInput.setText(type.category);
        root.addView(categoryInput, matchWrap());

        final int[] selectedColor = new int[]{type.color};
        final int[] selectedBaseColor = new int[]{type.baseColor};
        final int[] selectedTone = new int[]{type.colorTone};
        final TextView colorPreview = new TextView(this);
        colorPreview.setGravity(Gravity.CENTER);
        colorPreview.setPadding(0, dp(8), 0, dp(8));
        updateColorPreview(colorPreview, selectedColor[0]);
        root.addView(colorPreview, matchWrap());

        addColorPickerViews(root, selectedColor, selectedBaseColor, selectedTone, colorPreview);

        new AlertDialog.Builder(this)
                .setTitle(type.name + " 수정")
                .setView(wrapInScrollView(root))
                .setPositiveButton("저장", (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String shortName = shortInput.getText().toString().trim();
                    String category = categoryInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "근무 이름을 입력하세요.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (shortName.isEmpty()) shortName = name.substring(0, Math.min(2, name.length()));
                    if (category.isEmpty()) category = "기타";
                    db.updateShiftTypeDetails(type.id, name, shortName, category, selectedColor[0], selectedBaseColor[0], selectedTone[0]);
                    type.name = name;
                    type.shortName = shortName;
                    type.category = category;
                    type.color = selectedColor[0];
                    type.baseColor = selectedBaseColor[0];
                    type.colorTone = selectedTone[0];
                    applyShiftBadgeStyle(badge, type);
                    updateShiftTypeInfoText(info, type);
                    renderMonth();
                    AlarmScheduler.scheduleAllEnabled(this);
                    Toast.makeText(this, "근무 종류를 수정했습니다.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void addColorPickerViews(LinearLayout root, final int[] selectedColor,
                                     final int[] selectedBaseColor, final int[] selectedTone,
                                     final TextView colorPreview) {
        final TextView toneLabel = new TextView(this);
        toneLabel.setTextSize(13);
        toneLabel.setTextColor(Color.DKGRAY);
        toneLabel.setGravity(Gravity.CENTER);

        final SeekBar toneSeek = new SeekBar(this);
        toneSeek.setMax(200);
        toneSeek.setProgress(Math.max(0, Math.min(200, selectedTone[0])));

        final Runnable[] updateTone = new Runnable[1];
        updateTone[0] = () -> {
            selectedTone[0] = toneSeek.getProgress();
            selectedColor[0] = adjustColorTone(selectedBaseColor[0], selectedTone[0]);
            updateColorPreview(colorPreview, selectedColor[0]);
            toneLabel.setText(toneLabelText(selectedTone[0]));
        };

        Button noColorButton = new Button(this);
        noColorButton.setText("색상 없음");
        noColorButton.setOnClickListener(v -> {
            selectedBaseColor[0] = Color.TRANSPARENT;
            toneSeek.setProgress(100);
            updateTone[0].run();
        });
        root.addView(noColorButton, matchWrap());

        root.addView(toneLabel, matchWrap());
        root.addView(toneSeek, matchWrap());
        toneSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateTone[0].run();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int color : eventPalette) {
            Button colorButton = new Button(this);
            colorButton.setText(" ");
            colorButton.setBackground(makeRoundBackground(color, dp(8)));
            colorButton.setOnClickListener(v -> {
                selectedBaseColor[0] = color;
                toneSeek.setProgress(100);
                updateTone[0].run();
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(46), 1);
            cp.setMargins(dp(2), dp(6), dp(2), dp(2));
            colorRow.addView(colorButton, cp);
        }
        root.addView(colorRow, matchWrap());
        updateTone[0].run();
    }

    private int adjustColorTone(int baseColor, int level) {
        if (isNoColor(baseColor)) return Color.TRANSPARENT;
        level = Math.max(0, Math.min(200, level));
        if (level == 100) return baseColor;
        if (level < 100) {
            float factor = level / 100f;
            return Color.rgb(
                    Math.round(Color.red(baseColor) * factor),
                    Math.round(Color.green(baseColor) * factor),
                    Math.round(Color.blue(baseColor) * factor));
        }
        float factor = (level - 100) / 100f;
        return Color.rgb(
                Math.round(Color.red(baseColor) + (255 - Color.red(baseColor)) * factor),
                Math.round(Color.green(baseColor) + (255 - Color.green(baseColor)) * factor),
                Math.round(Color.blue(baseColor) + (255 - Color.blue(baseColor)) * factor));
    }

    private String toneLabelText(int level) {
        int value = level - 100;
        if (value == 0) return "색상 농도: 기본";
        if (value < 0) return "색상 농도: " + (-value) + "% 진하게";
        return "색상 농도: " + value + "% 옅게";
    }

    private void updateColorPreview(TextView preview, int color) {
        if (isNoColor(color)) {
            preview.setText("선택된 색상: 없음");
            preview.setTextColor(Color.rgb(72, 72, 74));
        } else {
            preview.setText("선택된 색상");
            preview.setTextColor(contrastTextColor(color));
        }
        preview.setBackground(makeRoundBackground(color, dp(6)));
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
        final int[] selectedBaseColor = new int[]{eventPalette[1]};
        final int[] selectedTone = new int[]{100};
        final TextView colorPreview = new TextView(this);
        colorPreview.setGravity(Gravity.CENTER);
        colorPreview.setPadding(0, dp(8), 0, dp(8));
        updateColorPreview(colorPreview, selectedColor[0]);
        root.addView(colorPreview, matchWrap());
        addColorPickerViews(root, selectedColor, selectedBaseColor, selectedTone, colorPreview);

        new AlertDialog.Builder(this)
                .setTitle("근무 종류 추가")
                .setView(wrapInScrollView(root))
                .setPositiveButton("저장", (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String shortName = shortInput.getText().toString().trim();
                    String category = categoryInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "근무 이름을 입력하세요.", Toast.LENGTH_SHORT).show();
                        showShiftTypesManager();
                        return;
                    }
                    if (shortName.isEmpty()) shortName = name.substring(0, Math.min(2, name.length()));
                    if (category.isEmpty()) category = "기타";
                    db.addShiftType(name, shortName, selectedColor[0], selectedBaseColor[0], selectedTone[0], category);
                    Toast.makeText(this, "근무 종류를 추가했습니다.", Toast.LENGTH_SHORT).show();
                    renderMonth();
                    showShiftTypesManager();
                })
                .setNegativeButton("취소", (dialog, which) -> showShiftTypesManager())
                .show();
    }

    private void confirmDeleteShiftType(final ShiftType type, final LinearLayout listRoot, final View rowView) {
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
                    if (deleted) {
                        listRoot.removeView(rowView);
                        renderMonth();
                        AlarmScheduler.scheduleAllEnabled(this);
                    }
                    Toast.makeText(this, deleted ? "삭제했습니다." : "삭제하지 못했습니다.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showManualHolidayManager() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(8), dp(14), dp(8));
        scrollView.addView(root);

        TextView guide = new TextView(this);
        guide.setText("회사 휴일, 임시 휴일 등 내장 공휴일에 없는 날짜를 직접 휴일로 추가할 수 있습니다. 추가한 휴일도 주휴/조건알람/자동조건 계산에 반영됩니다.");
        guide.setTextSize(13);
        guide.setTextColor(Color.DKGRAY);
        root.addView(guide, matchWrap());

        int year = currentMonth.getYear();
        List<String> lines = db.getCustomHolidayLinesForYear(year);
        TextView yearTitle = new TextView(this);
        yearTitle.setText(year + "년 수동 휴일");
        yearTitle.setTextSize(16);
        yearTitle.setTypeface(Typeface.DEFAULT_BOLD);
        yearTitle.setPadding(0, dp(10), 0, dp(4));
        root.addView(yearTitle, matchWrap());

        if (lines.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("등록된 수동 휴일 없음");
            empty.setTextColor(Color.GRAY);
            root.addView(empty, matchWrap());
        } else {
            for (String line : lines) addManualHolidayRow(root, line);
        }

        Button addButton = new Button(this);
        addButton.setText("수동 휴일 추가");
        root.addView(addButton, matchWrap());

        Button closeButton = new Button(this);
        closeButton.setText("닫기");
        root.addView(closeButton, matchWrap());

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("휴일 수동 관리")
                .setView(scrollView)
                .create();
        addButton.setOnClickListener(v -> {
            dialog.dismiss();
            showAddManualHolidayDialog();
        });
        closeButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void addManualHolidayRow(LinearLayout root, String line) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView text = new TextView(this);
        text.setText(line);
        text.setTextSize(14);
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button delete = new Button(this);
        delete.setText("삭제");
        row.addView(delete, new LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.WRAP_CONTENT));

        String dateText = line.length() >= 10 ? line.substring(0, 10) : "";
        delete.setOnClickListener(v -> {
            try {
                LocalDate date = DateUtil.parse(dateText);
                db.deleteCustomHoliday(date);
                root.removeView(row);
                renderMonth();
                AlarmScheduler.scheduleAllEnabled(this);
                Toast.makeText(this, "수동 휴일을 삭제했습니다.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "삭제하지 못했습니다.", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(row, matchWrap());
    }

    private void showAddManualHolidayDialog() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(10), dp(18), 0);

        final LocalDate[] selected = new LocalDate[]{selectedDate == null ? LocalDate.now() : selectedDate};
        final TextView dateText = new TextView(this);
        dateText.setText("날짜: " + DateUtil.iso(selected[0]));
        dateText.setTextSize(15);
        root.addView(dateText, matchWrap());

        Button dateButton = new Button(this);
        dateButton.setText("날짜 선택");
        dateButton.setOnClickListener(v -> pickDate(selected[0], picked -> {
            selected[0] = picked;
            dateText.setText("날짜: " + DateUtil.iso(selected[0]));
        }));
        root.addView(dateButton, matchWrap());

        final EditText nameInput = new EditText(this);
        nameInput.setHint("휴일 이름 예: 회사 휴무, 임시 휴일");
        nameInput.setSingleLine(true);
        nameInput.setText("수동 휴일");
        root.addView(nameInput, matchWrap());

        new AlertDialog.Builder(this)
                .setTitle("수동 휴일 추가")
                .setView(wrapInScrollView(root))
                .setPositiveButton("저장", (dialog, which) -> {
                    db.addCustomHoliday(selected[0], nameInput.getText().toString().trim());
                    renderMonth();
                    AlarmScheduler.scheduleAllEnabled(this);
                    Toast.makeText(this, "수동 휴일을 추가했습니다.", Toast.LENGTH_SHORT).show();
                    showManualHolidayManager();
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
        List<String> customLines = db.getCustomHolidayLinesForYear(year);
        if (!customLines.isEmpty()) {
            sb.append(year).append("년 수동 추가 휴일\n");
            for (String line : customLines) sb.append(line).append("\n");
            sb.append("\n");
        }
        sb.append(year).append("년 내장 공휴일\n");
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
        String message = "3교대 달력알람 v0.13-main-ui-color-snooze\n\n" +
                "현재 버전 기능:\n" +
                "- 주간 → 당직 → 비번 반복 달력\n" +
                "- 주간 시작일 설정\n" +
                "- 토/일/공휴일 + 주간이면 주휴 표시\n" +
                "- 날짜별 수동 근무 변경\n" +
                "- 근무 종류 추가/자동 조건\n" +
                "- 수동 휴일 추가\n" +
                "- 기간 일정 색 라인 표시\n" +
                "- 기본 날짜/반복 알람\n" +
                "- 3교대 조건 알람\n\n" +
                "알람 소리 세부 선택과 볼륨 조절은 다음 단계에서 추가 예정입니다.";
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
            drawable.setStroke(dp(1), Color.rgb(90, 200, 250));
        } else if (isToday) {
            drawable.setStroke(1, Color.rgb(210, 235, 255));
        } else {
            drawable.setStroke(1, Color.rgb(248, 248, 250));
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

    private boolean isNoColor(int color) {
        return Color.alpha(color) == 0;
    }

    private int fadeColor(int color) {
        if (isNoColor(color)) return Color.TRANSPARENT;
        int r = (int) (Color.red(color) * 0.25f + 255 * 0.75f);
        int g = (int) (Color.green(color) * 0.25f + 255 * 0.75f);
        int b = (int) (Color.blue(color) * 0.25f + 255 * 0.75f);
        return Color.rgb(r, g, b);
    }

    private GradientDrawable makeCircleBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        if (isNoColor(color)) {
            drawable.setColor(Color.TRANSPARENT);
            drawable.setStroke(1, Color.rgb(229, 229, 234));
        } else {
            drawable.setColor(color);
        }
        return drawable;
    }

    private GradientDrawable makeRoundBackground(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(radius);
        if (isNoColor(color)) {
            drawable.setColor(Color.TRANSPARENT);
            drawable.setStroke(1, Color.rgb(210, 210, 215));
        } else {
            drawable.setColor(color);
        }
        return drawable;
    }

    private int contrastTextColor(int backgroundColor) {
        if (isNoColor(backgroundColor)) return Color.rgb(45, 45, 48);
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

    private ScrollView wrapInScrollView(View child) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(child);
        return scrollView;
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
