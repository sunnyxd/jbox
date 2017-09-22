package com.alibaba.jbox.utils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.google.common.base.Preconditions;

/**
 * @author jifang
 * @since 16/5/24
 */
public class DateUtils {

    public static final long MS_PER_DAY = 24 * 60 * 60 * 1000;

    private static final String timePattern = "yyyy-MM-dd HH:mm:ss";

    private static final String datePattern = "yyyy-MM-dd";

    private static final ThreadLocal<DateFormat> timeFormatterMap = ThreadLocal.withInitial(
        () -> new SimpleDateFormat(timePattern));

    private static final ThreadLocal<DateFormat> dateFormatterMap = ThreadLocal.withInitial(
        () -> new SimpleDateFormat(datePattern));

    public static String millisTimeFormat(long millis) {
        return timeFormat(new Date(millis));
    }

    public static String timeFormat(Object obj) {
        return timeFormatterMap.get().format(obj);
    }

    public static Date timeParse(String source) throws ParseException {
        Preconditions.checkNotNull(source);
        return timeFormatterMap.get().parse(source);
    }

    public static String millisDateFormat(long millis) {
        return dateFormat(new Date(millis));
    }

    public static String dateFormat(Object obj) {
        return dateFormatterMap.get().format(obj);
    }

    public static Date dateParse(String source) throws ParseException {
        Preconditions.checkNotNull(source);
        return dateFormatterMap.get().parse(source);
    }
}
