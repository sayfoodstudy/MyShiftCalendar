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
}
