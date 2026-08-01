package com.personal.shiftcalendaralarm;

public class AlarmCondition {
    public long id;
    public long alarmId;
    public int groupOrder;
    public long shiftTypeId;
    public int holidayFilter;
    public int weekdayMask;

    public AlarmCondition(long id, long alarmId, int groupOrder, long shiftTypeId, int holidayFilter, int weekdayMask) {
        this.id = id;
        this.alarmId = alarmId;
        this.groupOrder = groupOrder;
        this.shiftTypeId = shiftTypeId;
        this.holidayFilter = holidayFilter;
        this.weekdayMask = weekdayMask;
    }

    public AlarmCondition copyForAlarm(long newAlarmId, int newOrder) {
        return new AlarmCondition(-1L, newAlarmId, newOrder, shiftTypeId, holidayFilter, weekdayMask);
    }
}
