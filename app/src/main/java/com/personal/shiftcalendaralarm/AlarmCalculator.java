package com.personal.shiftcalendaralarm;

import android.content.Context;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public class AlarmCalculator {
    public static long computeNextTriggerMillis(Context context, AlarmItem alarm, long afterMillis) {
        DatabaseHelper db = new DatabaseHelper(context);
        try {
            LocalDateTime after = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(afterMillis), ZoneId.systemDefault()).plusSeconds(1);

            if (AlarmItem.MODE_SHIFT.equals(alarm.alarmMode)) {
                return computeNextShiftAlarmMillis(db, alarm, after);
            }
            return computeNextBasicAlarmMillis(alarm, after);
        } finally {
            db.close();
        }
    }

    private static long computeNextBasicAlarmMillis(AlarmItem alarm, LocalDateTime after) {
        LocalDate start = DateUtil.parse(alarm.startDate);
        LocalTime time = LocalTime.of(alarm.hour, alarm.minute);

        if (AlarmItem.REPEAT_ONCE.equals(alarm.repeatType)) {
            LocalDateTime candidate = LocalDateTime.of(start, time);
            return candidate.isAfter(after) ? toMillis(candidate) : -1L;
        }

        // 단순하고 안정적인 계산을 위해 향후 20년 범위에서 다음 후보를 찾는다.
        LocalDate scanStart = start.isAfter(after.toLocalDate().minusDays(1)) ? start : after.toLocalDate().minusDays(1);
        LocalDate limit = after.toLocalDate().plusYears(20);
        for (LocalDate date = scanStart; !date.isAfter(limit); date = date.plusDays(1)) {
            if (date.isBefore(start)) continue;
            if (!matchesRepeatDate(alarm.repeatType, start, date)) continue;
            LocalDateTime candidate = LocalDateTime.of(date, time);
            if (candidate.isAfter(after)) return toMillis(candidate);
        }
        return -1L;
    }

    private static boolean matchesRepeatDate(String repeatType, LocalDate start, LocalDate date) {
        if (AlarmItem.REPEAT_DAILY.equals(repeatType)) return true;
        if (AlarmItem.REPEAT_WEEKLY.equals(repeatType)) return date.getDayOfWeek() == start.getDayOfWeek();
        if (AlarmItem.REPEAT_MONTHLY.equals(repeatType)) return date.getDayOfMonth() == start.getDayOfMonth();
        if (AlarmItem.REPEAT_YEARLY.equals(repeatType)) {
            return date.getMonthValue() == start.getMonthValue() && date.getDayOfMonth() == start.getDayOfMonth();
        }
        return false;
    }

    private static long computeNextShiftAlarmMillis(DatabaseHelper db, AlarmItem alarm, LocalDateTime after) {
        LocalTime time = LocalTime.of(alarm.hour, alarm.minute);
        LocalDate start = DateUtil.parse(alarm.startDate);
        LocalDate scanStart = start.isAfter(after.toLocalDate().minusDays(1)) ? start : after.toLocalDate().minusDays(1);
        LocalDate limit = after.toLocalDate().plusYears(20);

        for (LocalDate date = scanStart; !date.isAfter(limit); date = date.plusDays(1)) {
            if (date.isBefore(start)) continue;
            LocalDateTime candidate = LocalDateTime.of(date, time);
            if (!candidate.isAfter(after)) continue;
            if (!matchesWeekday(alarm.weekdayMask, date)) continue;
            if (!matchesHolidayFilter(alarm.holidayFilter, date)) continue;

            ShiftResult result = ShiftCalculator.calculate(db, date);
            if (result.finalShift == null) continue;
            if (alarm.shiftTypeId == AlarmItem.SHIFT_ANY || result.finalShift.id == alarm.shiftTypeId) {
                return toMillis(candidate);
            }
        }
        return -1L;
    }

    private static boolean matchesWeekday(int mask, LocalDate date) {
        int index = date.getDayOfWeek().getValue() - 1; // 월=0, 일=6
        return (mask & (1 << index)) != 0;
    }

    private static boolean matchesHolidayFilter(int filter, LocalDate date) {
        boolean holiday = HolidayProvider.isHoliday(date);
        if (filter == AlarmItem.HOLIDAY_ONLY) return holiday;
        if (filter == AlarmItem.HOLIDAY_WEEKDAY_ONLY) return !holiday;
        return true;
    }

    private static long toMillis(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static String formatTime(int hour, int minute) {
        return String.format(java.util.Locale.KOREAN, "%02d:%02d", hour, minute);
    }
}
