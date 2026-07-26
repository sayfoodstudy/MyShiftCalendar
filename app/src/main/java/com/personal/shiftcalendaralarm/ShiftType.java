package com.personal.shiftcalendaralarm;

public class ShiftType {
    public long id;
    public String code;
    public String name;
    public String shortName;
    public int color;
    public String category;
    public boolean isDefault;
    public boolean alarmEnabled;
    public int sortOrder;

    public ShiftType(long id, String code, String name, String shortName, int color,
                     String category, boolean isDefault, boolean alarmEnabled, int sortOrder) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.shortName = shortName;
        this.color = color;
        this.category = category;
        this.isDefault = isDefault;
        this.alarmEnabled = alarmEnabled;
        this.sortOrder = sortOrder;
    }

    public String displayShortName() {
        if (shortName != null && !shortName.trim().isEmpty()) return shortName;
        return name == null ? "" : name;
    }
}
