package com.alibaba.jbox.utils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * @author jifang@alibaba-inc.co
 * @since 16/5/24
 */
public class DateUtils {

    public static final long MS_PER_DAY = 24 * 60 * 60 * 1000;

    /* --------- #DATE: ----- yyyy-MM-dd ------------------ */

    private static final String DATE_PATTERN = "yyyy-MM-dd";

    private static ThreadLocal<DateFormat> dateFormatter = ThreadLocal.withInitial(
        () -> new SimpleDateFormat(DATE_PATTERN));

    public static String dateFormatFromMillis(long millis) {
        return dateFormat(new Date(millis));
    }

    public static String dateFormat(Object obj) {
        if (obj == null) {
            return null;
        }

        return dateFormatter.get().format(obj);
    }

    public static Date dateParse(String source) throws ParseException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(source), "parse source can not be empty");

        return dateFormatter.get().parse(source);
    }

    /* --------- #TIME: ----- yyyy-MM-dd HH:mm:ss ------------------ */

    private static final String TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private static ThreadLocal<DateFormat> timeFormatter = ThreadLocal.withInitial(
        () -> new SimpleDateFormat(TIME_PATTERN));

    public static String timeFormatFromMillis(long millis) {
        return timeFormat(new Date(millis));
    }

    public static String timeFormat(Object obj) {
        if (obj == null) {
            return null;
        }

        return timeFormatter.get().format(obj);
    }

    public static Date timeParse(String source) throws ParseException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(source), "parse source can not be empty");

        return timeFormatter.get().parse(source);
    }

    /* --------- #MILLIS: ----- yyyy-MM-dd HH:mm:ss,SSS ------------------ */

    private static final String MILLIS_PATTERN = "yyyy-MM-dd HH:mm:ss,SSS";

    private static ThreadLocal<DateFormat> millisFormatter = ThreadLocal.withInitial(
        () -> new SimpleDateFormat(MILLIS_PATTERN));

    public static String millisFormatFromMillis(long millis) {
        return millisFormat(new Date(millis));
    }

    public static String millisFormat(Object obj) {
        if (obj == null) {
            return null;
        }

        return millisFormatter.get().format(obj);
    }

    public static Date millsParse(String source) throws ParseException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(source), "parse source can not be empty");

        return millisFormatter.get().parse(source);
    }
}
