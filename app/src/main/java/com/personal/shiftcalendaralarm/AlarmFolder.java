package com.personal.shiftcalendaralarm;

public class AlarmFolder {
    public long id;
    public String name;
    public int sortOrder;

    public AlarmFolder(long id, String name, int sortOrder) {
        this.id = id;
        this.name = name;
        this.sortOrder = sortOrder;
    }
}
