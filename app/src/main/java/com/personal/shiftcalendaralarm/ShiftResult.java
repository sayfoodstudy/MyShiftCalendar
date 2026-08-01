package com.personal.shiftcalendaralarm;

public class ShiftResult {
    public ShiftType baseShift;
    public ShiftType finalShift;
    public boolean manualOverride;
    public boolean holiday;
    public String holidayLabel;

    public ShiftResult(ShiftType baseShift, ShiftType finalShift, boolean manualOverride, boolean holiday, String holidayLabel) {
        this.baseShift = baseShift;
        this.finalShift = finalShift;
        this.manualOverride = manualOverride;
        this.holiday = holiday;
        this.holidayLabel = holidayLabel;
    }
}
