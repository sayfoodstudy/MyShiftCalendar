package com.personal.shiftcalendaralarm;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class DateUtil {
    public static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    public static final DateTimeFormatter KOREAN_DATE = DateTimeFormatter.ofPattern("yyyy년 M월 d일 E요일", Locale.KOREAN);
    public static final DateTimeFormatter MONTH_TITLE = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN);
    public static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.KOREAN);

    public static String iso(LocalDate date) {
        return date.format(ISO);
    }

    public static LocalDate parse(String value) {
        return LocalDate.parse(value, ISO);
    }
}
