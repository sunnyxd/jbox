package com.alibaba.jbox.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author jifang
 * @since 16/5/24
 */
public class DateUtils {

    public static final long MS_PER_DAY = 86400 * 1000;

    private static final String pattern = "yyyy-MM-dd HH:mm:ss";
    
    private static final ThreadLocal<SimpleDateFormat> formatterMap = ThreadLocal.withInitial(() -> new SimpleDateFormat(pattern));

    public static String format(Object obj) {
        return formatterMap.get().format(obj);
    }

    public static Date parse(String source) throws ParseException {
        return formatterMap.get().parse(source);
    }
}
