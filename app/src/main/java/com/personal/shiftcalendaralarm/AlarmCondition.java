package com.personal.shiftcalendaralarm;

public class AlarmCondition {
    public long id;
    public long alarmId;
    public int groupOrder;
    public long shiftTypeId;
    public String shiftTypeIds;
    public int holidayFilter;
    public int weekdayMask;

    public AlarmCondition(long id, long alarmId, int groupOrder, long shiftTypeId, int holidayFilter, int weekdayMask) {
        this(id, alarmId, groupOrder, shiftTypeId, String.valueOf(shiftTypeId), holidayFilter, weekdayMask);
    }

    public AlarmCondition(long id, long alarmId, int groupOrder, long shiftTypeId, String shiftTypeIds, int holidayFilter, int weekdayMask) {
        this.id = id;
        this.alarmId = alarmId;
        this.groupOrder = groupOrder;
        this.shiftTypeId = shiftTypeId;
        this.shiftTypeIds = shiftTypeIds == null || shiftTypeIds.trim().isEmpty() ? String.valueOf(shiftTypeId) : shiftTypeIds;
        this.holidayFilter = holidayFilter;
        this.weekdayMask = weekdayMask;
    }

    public AlarmCondition copyForAlarm(long newAlarmId, int newOrder) {
        return new AlarmCondition(-1L, newAlarmId, newOrder, shiftTypeId, shiftTypeIds, holidayFilter, weekdayMask);
    }
}
