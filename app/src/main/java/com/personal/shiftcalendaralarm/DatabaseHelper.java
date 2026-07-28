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
    private static final int DB_VERSION = 5;

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
                "base_color INTEGER NOT NULL DEFAULT 0, " +
                "color_tone INTEGER NOT NULL DEFAULT 100, " +
                "category TEXT NOT NULL, " +
                "is_default INTEGER NOT NULL, " +
                "alarm_enabled INTEGER NOT NULL, " +
                "sort_order INTEGER NOT NULL, " +
                "auto_condition_enabled INTEGER NOT NULL DEFAULT 0, " +
                "condition_base_shift_type_id INTEGER NOT NULL DEFAULT -1, " +
                "condition_holiday_filter INTEGER NOT NULL DEFAULT 0, " +
                "condition_weekday_mask INTEGER NOT NULL DEFAULT 127, " +
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
        createAlarmsTable(db);
        createCustomHolidaysTable(db);
        seedDefaultShiftTypes(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 4) {
            addShiftConditionColumns(db);
            createCustomHolidaysTable(db);
        }
        if (oldVersion < 5) {
            addShiftColorToneColumns(db);
        }
        if (oldVersion < 2) {
            updateDefaultShiftTypes(db);
        }
        if (oldVersion < 3) {
            createAlarmsTable(db);
        }
    }

    private void addShiftConditionColumns(SQLiteDatabase db) {
        try { db.execSQL("ALTER TABLE shift_types ADD COLUMN auto_condition_enabled INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) { }
        try { db.execSQL("ALTER TABLE shift_types ADD COLUMN condition_base_shift_type_id INTEGER NOT NULL DEFAULT -1"); } catch (Exception ignored) { }
        try { db.execSQL("ALTER TABLE shift_types ADD COLUMN condition_holiday_filter INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) { }
        try { db.execSQL("ALTER TABLE shift_types ADD COLUMN condition_weekday_mask INTEGER NOT NULL DEFAULT 127"); } catch (Exception ignored) { }
    }

    private void addShiftColorToneColumns(SQLiteDatabase db) {
        try { db.execSQL("ALTER TABLE shift_types ADD COLUMN base_color INTEGER NOT NULL DEFAULT 0"); } catch (Exception ignored) { }
        try { db.execSQL("ALTER TABLE shift_types ADD COLUMN color_tone INTEGER NOT NULL DEFAULT 100"); } catch (Exception ignored) { }
        try { db.execSQL("UPDATE shift_types SET base_color=color WHERE base_color=0 AND color!=0"); } catch (Exception ignored) { }
        try { db.execSQL("UPDATE shift_types SET color_tone=100 WHERE color_tone<=0"); } catch (Exception ignored) { }
    }

    private void createAlarmsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS alarms (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "title TEXT NOT NULL, " +
                "memo TEXT, " +
                "alarm_mode TEXT NOT NULL, " +
                "enabled INTEGER NOT NULL, " +
                "hour INTEGER NOT NULL, " +
                "minute INTEGER NOT NULL, " +
                "start_date TEXT NOT NULL, " +
                "repeat_type TEXT NOT NULL, " +
                "shift_type_id INTEGER NOT NULL, " +
                "holiday_filter INTEGER NOT NULL, " +
                "weekday_mask INTEGER NOT NULL, " +
                "vibrate INTEGER NOT NULL, " +
                "next_trigger_ms INTEGER NOT NULL DEFAULT -1" +
                ")");
    }

    private void createCustomHolidaysTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS custom_holidays (" +
                "date TEXT PRIMARY KEY, " +
                "name TEXT NOT NULL" +
                ")");
    }

    private void seedDefaultShiftTypes(SQLiteDatabase db) {
        insertShiftType(db, CODE_DAY, "주간", "주간", Color.rgb(187, 222, 251), "근무", true, true, 1);
        insertShiftType(db, CODE_DUTY, "당직", "당직", Color.rgb(255, 224, 178), "근무", true, true, 2);
        insertShiftType(db, CODE_OFF, "비번", "비번", Color.rgb(200, 230, 201), "휴무", true, true, 3);
        insertShiftType(db, CODE_JUHYU, "주휴", "주휴", Color.rgb(225, 190, 231), "휴무", true, true, 4);
    }

    private void updateDefaultShiftTypes(SQLiteDatabase db) {
        updateDefaultShiftType(db, CODE_DAY, "주간", "주간", Color.rgb(187, 222, 251), "근무", 1);
        updateDefaultShiftType(db, CODE_DUTY, "당직", "당직", Color.rgb(255, 224, 178), "근무", 2);
        updateDefaultShiftType(db, CODE_OFF, "비번", "비번", Color.rgb(200, 230, 201), "휴무", 3);
        updateDefaultShiftType(db, CODE_JUHYU, "주휴", "주휴", Color.rgb(225, 190, 231), "휴무", 4);
    }

    private void updateDefaultShiftType(SQLiteDatabase db, String code, String name, String shortName,
                                        int color, String category, int sortOrder) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("short_name", shortName);
        values.put("color", color);
        values.put("base_color", color);
        values.put("color_tone", 100);
        values.put("category", category);
        values.put("is_default", 1);
        values.put("alarm_enabled", 1);
        values.put("sort_order", sortOrder);
        values.put("active", 1);
        int updated = db.update("shift_types", values, "code=?", new String[]{code});
        if (updated == 0) {
            insertShiftType(db, code, name, shortName, color, category, true, true, sortOrder);
        }
    }

    private long insertShiftType(SQLiteDatabase db, String code, String name, String shortName, int color,
                                 String category, boolean isDefault, boolean alarmEnabled, int sortOrder) {
        ContentValues values = new ContentValues();
        values.put("code", code);
        values.put("name", name);
        values.put("short_name", shortName);
        values.put("color", color);
        values.put("base_color", color);
        values.put("color_tone", 100);
        values.put("category", category);
        values.put("is_default", isDefault ? 1 : 0);
        values.put("alarm_enabled", alarmEnabled ? 1 : 0);
        values.put("sort_order", sortOrder);
        values.put("auto_condition_enabled", 0);
        values.put("condition_base_shift_type_id", AlarmItem.SHIFT_ANY);
        values.put("condition_holiday_filter", AlarmItem.HOLIDAY_ANY);
        values.put("condition_weekday_mask", AlarmItem.WEEKDAY_ALL_MASK);
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
                cursor.getInt(cursor.getColumnIndexOrThrow("base_color")),
                cursor.getInt(cursor.getColumnIndexOrThrow("color_tone")),
                cursor.getString(cursor.getColumnIndexOrThrow("category")),
                cursor.getInt(cursor.getColumnIndexOrThrow("is_default")) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow("alarm_enabled")) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow("sort_order")),
                cursor.getInt(cursor.getColumnIndexOrThrow("auto_condition_enabled")) == 1,
                cursor.getLong(cursor.getColumnIndexOrThrow("condition_base_shift_type_id")),
                cursor.getInt(cursor.getColumnIndexOrThrow("condition_holiday_filter")),
                cursor.getInt(cursor.getColumnIndexOrThrow("condition_weekday_mask"))
        );
    }

    public long addShiftType(String name, String shortName, int color, int baseColor, int colorTone, String category) {
        SQLiteDatabase db = getWritableDatabase();
        int sortOrder = getNextShiftTypeSortOrder(db);
        String code = "CUSTOM_" + System.currentTimeMillis();
        long id = insertShiftType(db, code, name, shortName, color, category, false, true, sortOrder);
        ContentValues values = new ContentValues();
        values.put("base_color", baseColor);
        values.put("color_tone", colorTone);
        db.update("shift_types", values, "id=?", new String[]{String.valueOf(id)});
        return id;
    }

    public void updateShiftTypeDetails(long id, String name, String shortName, String category,
                                       int color, int baseColor, int colorTone) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", name == null || name.trim().isEmpty() ? "근무" : name.trim());
        values.put("short_name", shortName == null || shortName.trim().isEmpty() ?
                (name == null || name.trim().isEmpty() ? "근" : name.trim().substring(0, Math.min(2, name.trim().length()))) : shortName.trim());
        values.put("category", category == null || category.trim().isEmpty() ? "기타" : category.trim());
        values.put("color", color);
        values.put("base_color", baseColor);
        values.put("color_tone", colorTone);
        db.update("shift_types", values, "id=?", new String[]{String.valueOf(id)});
    }

    public void updateShiftTypeCondition(long id, boolean enabled, long baseShiftTypeId,
                                         int holidayFilter, int weekdayMask) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("auto_condition_enabled", enabled ? 1 : 0);
        values.put("condition_base_shift_type_id", baseShiftTypeId);
        values.put("condition_holiday_filter", holidayFilter);
        values.put("condition_weekday_mask", weekdayMask);
        db.update("shift_types", values, "id=?", new String[]{String.valueOf(id)});
    }

    public List<ShiftType> getAutoConditionShiftTypes() {
        List<ShiftType> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query("shift_types", null,
                "active=1 AND auto_condition_enabled=1", null, null, null, "sort_order ASC, id ASC");
        try {
            while (cursor.moveToNext()) result.add(shiftTypeFromCursor(cursor));
        } finally {
            cursor.close();
        }
        return result;
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

    public void addCustomHoliday(LocalDate date, String name) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("date", DateUtil.iso(date));
        values.put("name", name == null || name.trim().isEmpty() ? "수동 휴일" : name.trim());
        db.insertWithOnConflict("custom_holidays", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void deleteCustomHoliday(LocalDate date) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("custom_holidays", "date=?", new String[]{DateUtil.iso(date)});
    }

    public String getCustomHolidayName(LocalDate date) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query("custom_holidays", new String[]{"name"}, "date=?",
                new String[]{DateUtil.iso(date)}, null, null, null);
        try {
            if (cursor.moveToFirst()) return cursor.getString(0);
            return null;
        } finally {
            cursor.close();
        }
    }

    public boolean isHoliday(LocalDate date) {
        return HolidayProvider.isWeekend(date) || HolidayProvider.isPublicHoliday(date) || getCustomHolidayName(date) != null;
    }

    public String getHolidayLabel(LocalDate date) {
        String custom = getCustomHolidayName(date);
        if (custom != null) return custom;
        return HolidayProvider.getHolidayLabel(date);
    }

    public List<String> getCustomHolidayLinesForYear(int year) {
        List<String> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String start = year + "-01-01";
        String end = year + "-12-31";
        Cursor cursor = db.query("custom_holidays", null, "date>=? AND date<=?",
                new String[]{start, end}, null, null, "date ASC");
        try {
            while (cursor.moveToNext()) {
                result.add(cursor.getString(cursor.getColumnIndexOrThrow("date")) + "  " +
                        cursor.getString(cursor.getColumnIndexOrThrow("name")));
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    public long addAlarm(String title, String memo, String alarmMode, int hour, int minute,
                         LocalDate startDate, String repeatType, long shiftTypeId,
                         int holidayFilter, int weekdayMask, boolean vibrate) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("title", title == null || title.trim().isEmpty() ? "알람" : title.trim());
        values.put("memo", memo == null ? "" : memo);
        values.put("alarm_mode", alarmMode);
        values.put("enabled", 1);
        values.put("hour", hour);
        values.put("minute", minute);
        values.put("start_date", DateUtil.iso(startDate));
        values.put("repeat_type", repeatType);
        values.put("shift_type_id", shiftTypeId);
        values.put("holiday_filter", holidayFilter);
        values.put("weekday_mask", weekdayMask);
        values.put("vibrate", vibrate ? 1 : 0);
        values.put("next_trigger_ms", -1L);
        return db.insert("alarms", null, values);
    }

    public AlarmItem getAlarm(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query("alarms", null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        try {
            if (cursor.moveToFirst()) return alarmFromCursor(cursor);
            return null;
        } finally {
            cursor.close();
        }
    }

    public List<AlarmItem> getAllAlarms() {
        List<AlarmItem> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query("alarms", null, null, null, null, null, "enabled DESC, next_trigger_ms ASC, id DESC");
        try {
            while (cursor.moveToNext()) result.add(alarmFromCursor(cursor));
        } finally {
            cursor.close();
        }
        return result;
    }

    public List<AlarmItem> getEnabledAlarms() {
        List<AlarmItem> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query("alarms", null, "enabled=1", null, null, null, "next_trigger_ms ASC, id ASC");
        try {
            while (cursor.moveToNext()) result.add(alarmFromCursor(cursor));
        } finally {
            cursor.close();
        }
        return result;
    }

    public void updateAlarm(long id, String title, String memo, String alarmMode, boolean enabled,
                            int hour, int minute, LocalDate startDate, String repeatType,
                            long shiftTypeId, int holidayFilter, int weekdayMask, boolean vibrate) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("title", title == null || title.trim().isEmpty() ? "알람" : title.trim());
        values.put("memo", memo == null ? "" : memo);
        values.put("alarm_mode", alarmMode);
        values.put("enabled", enabled ? 1 : 0);
        values.put("hour", hour);
        values.put("minute", minute);
        values.put("start_date", DateUtil.iso(startDate));
        values.put("repeat_type", repeatType);
        values.put("shift_type_id", shiftTypeId);
        values.put("holiday_filter", holidayFilter);
        values.put("weekday_mask", weekdayMask);
        values.put("vibrate", vibrate ? 1 : 0);
        values.put("next_trigger_ms", -1L);
        db.update("alarms", values, "id=?", new String[]{String.valueOf(id)});
    }

    public void updateAlarmEnabled(long id, boolean enabled) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("enabled", enabled ? 1 : 0);
        db.update("alarms", values, "id=?", new String[]{String.valueOf(id)});
    }

    public void updateAlarmNextTrigger(long id, long nextTriggerMillis) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("next_trigger_ms", nextTriggerMillis);
        db.update("alarms", values, "id=?", new String[]{String.valueOf(id)});
    }

    public void deleteAlarm(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("alarms", "id=?", new String[]{String.valueOf(id)});
    }

    private AlarmItem alarmFromCursor(Cursor cursor) {
        return new AlarmItem(
                cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("title")),
                cursor.getString(cursor.getColumnIndexOrThrow("memo")),
                cursor.getString(cursor.getColumnIndexOrThrow("alarm_mode")),
                cursor.getInt(cursor.getColumnIndexOrThrow("enabled")) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow("hour")),
                cursor.getInt(cursor.getColumnIndexOrThrow("minute")),
                cursor.getString(cursor.getColumnIndexOrThrow("start_date")),
                cursor.getString(cursor.getColumnIndexOrThrow("repeat_type")),
                cursor.getLong(cursor.getColumnIndexOrThrow("shift_type_id")),
                cursor.getInt(cursor.getColumnIndexOrThrow("holiday_filter")),
                cursor.getInt(cursor.getColumnIndexOrThrow("weekday_mask")),
                cursor.getInt(cursor.getColumnIndexOrThrow("vibrate")) == 1,
                cursor.getLong(cursor.getColumnIndexOrThrow("next_trigger_ms"))
        );
    }
}
