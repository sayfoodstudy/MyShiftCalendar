package com.personal.shiftcalendaralarm;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class ShiftCalculator {
    public static ShiftResult calculate(DatabaseHelper db, LocalDate date) {
        LocalDate baseDate = db.getBaseDate();
        if (baseDate == null) baseDate = LocalDate.now();

        long diff = ChronoUnit.DAYS.between(baseDate, date);
        int mod = (int) ((diff % 3 + 3) % 3);
        ShiftType baseShift;
        if (mod == 0) baseShift = db.getShiftTypeByCode(DatabaseHelper.CODE_DAY);
        else if (mod == 1) baseShift = db.getShiftTypeByCode(DatabaseHelper.CODE_DUTY);
        else baseShift = db.getShiftTypeByCode(DatabaseHelper.CODE_OFF);

        boolean holiday = db.isHoliday(date);
        String holidayLabel = db.getHolidayLabel(date);
        long overrideId = db.getOverrideShiftId(date);
        if (overrideId > 0) {
            ShiftType override = db.getShiftTypeById(overrideId);
            if (override != null) return new ShiftResult(baseShift, override, true, holiday, holidayLabel);
        }

        ShiftType calculatedShift = baseShift;
        if (baseShift != null && DatabaseHelper.CODE_DAY.equals(baseShift.code) && holiday) {
            ShiftType juhyu = db.getShiftTypeByCode(DatabaseHelper.CODE_JUHYU);
            if (juhyu != null) calculatedShift = juhyu;
        }

        ShiftType conditionalShift = findConditionalShift(db, calculatedShift, date, holiday);
        if (conditionalShift != null) {
            return new ShiftResult(baseShift, conditionalShift, false, holiday, holidayLabel);
        }
        return new ShiftResult(baseShift, calculatedShift, false, holiday, holidayLabel);
    }

    private static ShiftType findConditionalShift(DatabaseHelper db, ShiftType calculatedShift, LocalDate date, boolean holiday) {
        if (calculatedShift == null) return null;
        for (ShiftType type : db.getAutoConditionShiftTypes()) {
            if (type.conditionBaseShiftTypeId != AlarmItem.SHIFT_ANY && type.conditionBaseShiftTypeId != calculatedShift.id) {
                continue;
            }
            if (!matchesHolidayFilter(type.conditionHolidayFilter, holiday)) continue;
            if (!matchesWeekday(type.conditionWeekdayMask, date)) continue;
            return type;
        }
        return null;
    }

    private static boolean matchesHolidayFilter(int filter, boolean holiday) {
        if (filter == AlarmItem.HOLIDAY_ONLY) return holiday;
        if (filter == AlarmItem.HOLIDAY_WEEKDAY_ONLY) return !holiday;
        return true;
    }

    private static boolean matchesWeekday(int mask, LocalDate date) {
        int index = date.getDayOfWeek().getValue() - 1;
        return (mask & (1 << index)) != 0;
    }
}
