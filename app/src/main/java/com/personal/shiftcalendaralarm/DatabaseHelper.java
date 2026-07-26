package com.personal.shiftcalendaralarm;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "shift_calendar_alarm.db";
    private static final int DB_VERSION = 1;

    public static final String SETTING_BASE_DATE = "base_day_shift_date";

    public static final String CODE_DAY = "DAY";
    public static final String CODE_DUTY = "DUTY";
    public static final String CODE_OFF = "OFF";
    public static final String CODE_JUHYU = "JUHYU";

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT)");
        db.execSQL("CREATE TABLE shift_types (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "code TEXT UNIQUE, " +
                "name TEXT NOT NULL, " +
                "short_name TEXT NOT NULL, " +
                "color INTEGER NOT NULL, " +
                "category TEXT NOT NULL, " +
                "is_default INTEGER NOT NULL, " +
                "alarm_enabled INTEGER NOT NULL, " +
                "sort_order INTEGER NOT NULL, " +
                "active INTEGER NOT NULL DEFAULT 1" +
                ")");
        db.execSQL("CREATE TABLE shift_overrides (" +
                "date TEXT PRIMARY KEY, " +
                "shift_type_id INTEGER NOT NULL" +
                ")");
        db.execSQL("CREATE TABLE period_events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "title TEXT NOT NULL, " +
                "start_date TEXT NOT NULL, " +
                "end_date TEXT NOT NULL, " +
                "color INTEGER NOT NULL, " +
                "memo TEXT" +
                ")");
        seedDefaultShiftTypes(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS settings");
        db.execSQL("DROP TABLE IF EXISTS shift_types");
        db.execSQL("DROP TABLE IF EXISTS shift_overrides");
        db.execSQL("DROP TABLE IF EXISTS period_events");
        onCreate(db);
    }

    private void seedDefaultShiftTypes(SQLiteDatabase db) {
        insertShiftType(db, CODE_DAY, "주간", "주", Color.rgb(25, 118, 210), "근무", true, true, 1);
        insertShiftType(db, CODE_DUTY, "당직", "당", Color.rgb(230, 81, 0), "근무", true, true, 2);
        insertShiftType(db, CODE_OFF, "비번", "비", Color.rgb(97, 97, 97), "휴무", true, true, 3);
        insertShiftType(db, CODE_JUHYU, "주휴", "휴", Color.rgb(123, 31, 162), "휴무", true, true, 4);
    }

    private long insertShiftType(SQLiteDatabase db, String code, String name, String shortName, int color,
                                 String category, boolean isDefault, boolean alarmEnabled, int sortOrder) {
        ContentValues values = new ContentValues();
        values.put("code", code);
        values.put("name", name);
        values.put("short_name", shortName);
        values.put("color", color);
        values.put("category", category);
        values.put("is_default", isDefault ? 1 : 0);
        values.put("alarm_enabled", alarmEnabled ? 1 : 0);
        values.put("sort_order", sortOrder);
        values.put("active", 1);
        return db.insert("shift_types", null, values);
    }

    public boolean isBaseDateSet() {
        return getSetting(SETTING_BASE_DATE) != null;
    }

    public LocalDate getBaseDate() {
        String value = getSetting(SETTING_BASE_DATE);
        if (value == null || value.trim().isEmpty()) return null;
        return DateUtil.parse(value);
    }

    public void setBaseDate(LocalDate date) {
        setSetting(SETTING_BASE_DATE, DateUtil.iso(date));
    }

    public String getSetting(String key) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query("settings", new String[]{"value"}, "key=?", new String[]{key}, null, null, null);
        try {
            if (cursor.moveToFirst()) return cursor.getString(0);
            return null;
        } finally {
            cursor.close();
        }
    }

    public void setSetting(String key, String value) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("key", key);
        values.put("value", value);
        db.insertWithOnConflict("settings", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<ShiftType> getAllShiftTypes() {
        List<ShiftType> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query("shift_types", null, "active=1", null, null, null, "sort_order ASC, id ASC");
        try {
            while (cursor.moveToNext()) result.add(shiftTypeFromCursor(cursor));
        } finally {
            cursor.close();
        }
        return result;
    }

    public ShiftType getShiftTypeById(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query("shift_types", null, "id=? AND active=1", new String[]{String.valueOf(id)}, null, null, null);
        try {
            if (cursor.moveToFirst()) return shiftTypeFromCursor(cursor);
            return null;
        } finally {
            cursor.close();
        }
    }

    public ShiftType getShiftTypeByCode(String code) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query("shift_types", null, "code=? AND active=1", new String[]{code}, null, null, null);
        try {
            if (cursor.moveToFirst()) return shiftTypeFromCursor(cursor);
            return null;
        } finally {
            cursor.close();
        }
    }

    private ShiftType shiftTypeFromCursor(Cursor cursor) {
        return new ShiftType(
                cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("code")),
                cursor.getString(cursor.getColumnIndexOrThrow("name")),
                cursor.getString(cursor.getColumnIndexOrThrow("short_name")),
                cursor.getInt(cursor.getColumnIndexOrThrow("color")),
                cursor.getString(cursor.getColumnIndexOrThrow("category")),
                cursor.getInt(cursor.getColumnIndexOrThrow("is_default")) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow("alarm_enabled")) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow("sort_order"))
        );
    }

    public long addShiftType(String name, String shortName, int color, String category) {
        SQLiteDatabase db = getWritableDatabase();
        int sortOrder = getNextShiftTypeSortOrder(db);
        String code = "CUSTOM_" + System.currentTimeMillis();
        return insertShiftType(db, code, name, shortName, color, category, false, true, sortOrder);
    }

    private int getNextShiftTypeSortOrder(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM shift_types", null);
        try {
            if (cursor.moveToFirst()) return cursor.getInt(0);
            return 1;
        } finally {
            cursor.close();
        }
    }

    public boolean isShiftTypeUsed(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM shift_overrides WHERE shift_type_id=?", new String[]{String.valueOf(id)});
        try {
            return cursor.moveToFirst() && cursor.getInt(0) > 0;
        } finally {
            cursor.close();
        }
    }

    public boolean deleteCustomShiftType(long id) {
        ShiftType type = getShiftTypeById(id);
        if (type == null || type.isDefault || isShiftTypeUsed(id)) return false;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("active", 0);
        return db.update("shift_types", values, "id=?", new String[]{String.valueOf(id)}) > 0;
    }

    public long getOverrideShiftId(LocalDate date) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query("shift_overrides", new String[]{"shift_type_id"}, "date=?",
                new String[]{DateUtil.iso(date)}, null, null, null);
        try {
            if (cursor.moveToFirst()) return cursor.getLong(0);
            return -1L;
        } finally {
            cursor.close();
        }
    }

    public void setShiftOverride(LocalDate date, long shiftTypeId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("date", DateUtil.iso(date));
        values.put("shift_type_id", shiftTypeId);
        db.insertWithOnConflict("shift_overrides", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void clearShiftOverride(LocalDate date) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("shift_overrides", "date=?", new String[]{DateUtil.iso(date)});
    }

    public long addPeriodEvent(String title, LocalDate startDate, LocalDate endDate, int color, String memo) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("start_date", DateUtil.iso(startDate));
        values.put("end_date", DateUtil.iso(endDate));
        values.put("color", color);
        values.put("memo", memo);
        return db.insert("period_events", null, values);
    }

    public List<PeriodEvent> getEventsForDate(LocalDate date) {
        return getEventsOverlapping(date, date);
    }

    public List<PeriodEvent> getEventsForMonth(LocalDate month) {
        LocalDate start = month.withDayOfMonth(1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        return getEventsOverlapping(start, end);
    }

    private List<PeriodEvent> getEventsOverlapping(LocalDate start, LocalDate end) {
        List<PeriodEvent> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query("period_events", null,
                "start_date <= ? AND end_date >= ?",
                new String[]{DateUtil.iso(end), DateUtil.iso(start)},
                null, null, "start_date ASC, id ASC");
        try {
            while (cursor.moveToNext()) result.add(periodEventFromCursor(cursor));
        } finally {
            cursor.close();
        }
        return result;
    }

    public void deletePeriodEvent(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("period_events", "id=?", new String[]{String.valueOf(id)});
    }

    private PeriodEvent periodEventFromCursor(Cursor cursor) {
        return new PeriodEvent(
                cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("title")),
                DateUtil.parse(cursor.getString(cursor.getColumnIndexOrThrow("start_date"))),
                DateUtil.parse(cursor.getString(cursor.getColumnIndexOrThrow("end_date"))),
                cursor.getInt(cursor.getColumnIndexOrThrow("color")),
                cursor.getString(cursor.getColumnIndexOrThrow("memo"))
        );
    }
}
