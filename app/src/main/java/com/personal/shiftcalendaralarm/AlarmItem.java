package com.personal.shiftcalendaralarm;

public class AlarmItem {
    public static final String MODE_BASIC = "BASIC";
    public static final String MODE_SHIFT = "SHIFT";

    public static final String REPEAT_ONCE = "ONCE";
    public static final String REPEAT_DAILY = "DAILY";
    public static final String REPEAT_WEEKLY = "WEEKLY";
    public static final String REPEAT_MONTHLY = "MONTHLY";
    public static final String REPEAT_YEARLY = "YEARLY";

    public static final int HOLIDAY_ANY = 0;
    // 이제 UI에서는 "공휴일만" 의미로 사용. 토/일은 요일 조건으로 처리.
    public static final int HOLIDAY_ONLY = 1;
    // 이전 버전 호환용. 새 UI에서는 선택하지 않음.
    public static final int HOLIDAY_WEEKDAY_ONLY = 2;

    public static final long SHIFT_ANY = -1L;
    public static final long FOLDER_NONE = 0L;
    public static final int WEEKDAY_ALL_MASK = 0b1111111; // 월~일

    public long id;
    public String title;
    public String memo;
    public String alarmMode;
    public boolean enabled;
    public int hour;
    public int minute;
    public String startDate;
    public String repeatType;
    public long shiftTypeId;
    public int holidayFilter;
    public int weekdayMask;
    public boolean vibrate;
    public long nextTriggerMillis;
    public long folderId;
    public int sortOrder;

    public AlarmItem(long id, String title, String memo, String alarmMode, boolean enabled,
                     int hour, int minute, String startDate, String repeatType,
                     long shiftTypeId, int holidayFilter, int weekdayMask,
                     boolean vibrate, long nextTriggerMillis, long folderId, int sortOrder) {
        this.id = id;
        this.title = title;
        this.memo = memo;
        this.alarmMode = alarmMode;
        this.enabled = enabled;
        this.hour = hour;
        this.minute = minute;
        this.startDate = startDate;
        this.repeatType = repeatType;
        this.shiftTypeId = shiftTypeId;
        this.holidayFilter = holidayFilter;
        this.weekdayMask = weekdayMask;
        this.vibrate = vibrate;
        this.nextTriggerMillis = nextTriggerMillis;
        this.folderId = folderId;
        this.sortOrder = sortOrder;
    }

    public boolean isRepeatingOrConditional() {
        return MODE_SHIFT.equals(alarmMode) || !REPEAT_ONCE.equals(repeatType);
    }
}
