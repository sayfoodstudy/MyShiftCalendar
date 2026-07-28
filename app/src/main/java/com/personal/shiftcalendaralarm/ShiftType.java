package com.personal.shiftcalendaralarm;

public class ShiftType {
    public long id;
    public String code;
    public String name;
    public String shortName;
    public int color;
    public int baseColor;
    public int colorTone;
    public String category;
    public boolean isDefault;
    public boolean alarmEnabled;
    public int sortOrder;
    public boolean autoConditionEnabled;
    public long conditionBaseShiftTypeId;
    public int conditionHolidayFilter;
    public int conditionWeekdayMask;

    public ShiftType(long id, String code, String name, String shortName, int color,
                     int baseColor, int colorTone, String category, boolean isDefault,
                     boolean alarmEnabled, int sortOrder, boolean autoConditionEnabled,
                     long conditionBaseShiftTypeId, int conditionHolidayFilter, int conditionWeekdayMask) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.shortName = shortName;
        this.color = color;
        this.baseColor = baseColor;
        this.colorTone = colorTone;
        this.category = category;
        this.isDefault = isDefault;
        this.alarmEnabled = alarmEnabled;
        this.sortOrder = sortOrder;
        this.autoConditionEnabled = autoConditionEnabled;
        this.conditionBaseShiftTypeId = conditionBaseShiftTypeId;
        this.conditionHolidayFilter = conditionHolidayFilter;
        this.conditionWeekdayMask = conditionWeekdayMask;
    }

    public String displayShortName() {
        if (shortName != null && !shortName.trim().isEmpty()) return shortName;
        return name == null ? "" : name;
    }
}
