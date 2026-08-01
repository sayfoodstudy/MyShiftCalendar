package com.personal.shiftcalendaralarm;

import java.time.LocalDate;

public class PeriodEvent {
    public long id;
    public String title;
    public LocalDate startDate;
    public LocalDate endDate;
    public int color;
    public String memo;

    public PeriodEvent(long id, String title, LocalDate startDate, LocalDate endDate, int color, String memo) {
        this.id = id;
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.color = color;
        this.memo = memo;
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }
}
