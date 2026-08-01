package com.personal.shiftcalendaralarm;

import android.content.Context;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

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

        List<AlarmCondition> conditions = db.getAlarmConditions(alarm.id);
        if (conditions.isEmpty()) {
            conditions = new ArrayList<>();
            conditions.add(new AlarmCondition(-1L, alarm.id, 1, alarm.shiftTypeId, alarm.holidayFilter, alarm.weekdayMask));
        }

        for (LocalDate date = scanStart; !date.isAfter(limit); date = date.plusDays(1)) {
            if (date.isBefore(start)) continue;
            LocalDateTime candidate = LocalDateTime.of(date, time);
            if (!candidate.isAfter(after)) continue;

            ShiftResult result = ShiftCalculator.calculate(db, date);
            if (result.finalShift == null) continue;

            for (AlarmCondition condition : conditions) {
                if (matchesCondition(db, result, condition, date)) return toMillis(candidate);
            }
        }
        return -1L;
    }

    private static boolean matchesCondition(DatabaseHelper db, ShiftResult result, AlarmCondition condition, LocalDate date) {
        if (!matchesWeekday(condition.weekdayMask, date)) return false;
        if (!matchesHolidayFilter(db, condition.holidayFilter, date)) return false;
        return matchesShiftTypes(condition.shiftTypeIds, result.finalShift.id);
    }

    private static boolean matchesShiftTypes(String shiftTypeIds, long actualShiftId) {
        if (shiftTypeIds == null || shiftTypeIds.trim().isEmpty()) return false;
        String[] parts = shiftTypeIds.split(",");
        for (String part : parts) {
            try {
                long id = Long.parseLong(part.trim());
                if (id == AlarmItem.SHIFT_ANY || id == actualShiftId) return true;
            } catch (Exception ignored) { }
        }
        return false;
    }

    private static boolean matchesWeekday(int mask, LocalDate date) {
        int index = date.getDayOfWeek().getValue() - 1;
        return (mask & (1 << index)) != 0;
    }

    private static boolean matchesHolidayFilter(DatabaseHelper db, int filter, LocalDate date) {
        if (filter == AlarmItem.HOLIDAY_ONLY) return db.isPublicOrCustomHoliday(date);
        if (filter == AlarmItem.HOLIDAY_EXCEPT_PUBLIC) return !db.isPublicOrCustomHoliday(date);
        if (filter == AlarmItem.HOLIDAY_WEEKDAY_ONLY) return !db.isHoliday(date);
        return true;
    }

    private static long toMillis(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static String formatTime(int hour, int minute) {
        return String.format(java.util.Locale.KOREAN, "%02d:%02d", hour, minute);
    }
}
