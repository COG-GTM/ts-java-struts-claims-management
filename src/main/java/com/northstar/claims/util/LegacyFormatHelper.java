package com.northstar.claims.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/** Provides the date and display conversions used by batch and screen code. */
public class LegacyFormatHelper {

    private static final Log log = LogFactory.getLog(LegacyFormatHelper.class);
    private static final String DATABASE_PATTERN = "yyyy-MM-dd";
    private static final String DISPLAY_PATTERN = "MM/dd/yyyy";

    public static String databaseDate(Date date) {
        if (date == null) {
            return "";
        }
        SimpleDateFormat format = new SimpleDateFormat(DATABASE_PATTERN);
        return format.format(date);
    }

    public static String displayDate(Date date) {
        if (date == null) {
            return "";
        }
        SimpleDateFormat format = new SimpleDateFormat(DISPLAY_PATTERN);
        return format.format(date);
    }

    public static Date parseDatabaseDate(String text) throws ParseException {
        SimpleDateFormat format = new SimpleDateFormat(DATABASE_PATTERN);
        return format.parse(text);
    }

    public static Date parseDisplayDate(String text) throws ParseException {
        SimpleDateFormat format = new SimpleDateFormat(DISPLAY_PATTERN);
        return format.parse(text);
    }

    public static String monthName(Date date) {
        SimpleDateFormat format = new SimpleDateFormat("MMMM");
        return format.format(date);
    }

    public static String yearMonth(Date date) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM");
        return format.format(date);
    }

    public static Date beginningOfMonth(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        return calendar.getTime();
    }

    public static Date endOfMonth(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.DAY_OF_MONTH,
                calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        return calendar.getTime();
    }

    public static Date addBusinessDays(Date date, int number) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int remaining = number;
        while (remaining > 0) {
            calendar.add(Calendar.DATE, 1);
            int day = calendar.get(Calendar.DAY_OF_WEEK);
            if (day != Calendar.SATURDAY && day != Calendar.SUNDAY) {
                remaining--;
            }
        }
        return calendar.getTime();
    }

    public static String money(double amount) {
        return String.format("%.2f", new Object[] { Double.valueOf(amount) });
    }

    public static String percentage(double amount) {
        return String.format("%.2f%%", new Object[] { Double.valueOf(amount * 100) });
    }

    public static Map parseParameters(String text) {
        Map values = new Hashtable();
        if (text == null) {
            return values;
        }
        String[] parts = text.split("&");
        for (int i = 0; i < parts.length; i++) {
            String[] pair = parts[i].split("=", 2);
            if (pair.length == 2) {
                values.put(pair[0], pair[1]);
            }
        }
        return values;
    }

    public static String normalizeCode(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase();
    }

    public static String claimLabel(int claimId, String number) {
        StringBuffer text = new StringBuffer();
        text.append(claimId);
        text.append(" - ");
        text.append(number == null ? "" : number);
        return text.toString();
    }

    public static void logDate(String label, Date date) {
        String text = label + "=" + databaseDate(date);
        log.debug(text);
        System.out.println(text);
    }
}
