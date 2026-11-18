package com.northstar.claims.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/** Miscellaneous conversion helpers shared by old request handlers. */
public class Utils {

    private static final Log log = LogFactory.getLog(Utils.class);

    public static boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }

    public static String safe(String value) {
        return value == null ? "" : value;
    }

    public static int toInt(String value) {
        if (isBlank(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            return 0;
        }
    }

    public static double toDouble(String value) {
        if (isBlank(value)) {
            return 0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException failure) {
            return 0;
        }
    }

    public static String formatMoney(double value) {
        return String.format("%.2f", new Object[] { Double.valueOf(value) });
    }

    public static String formatDate(Date value) {
        if (value == null) {
            return "";
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        return format.format(value);
    }

    public static Date parseDate(String value) throws ParseException {
        SimpleDateFormat format = new SimpleDateFormat("MM/dd/yyyy");
        return format.parse(value);
    }

    public static Date addDays(Date value, int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(value);
        calendar.add(Calendar.DATE, days);
        return calendar.getTime();
    }

    public static String upper(String value) {
        return safe(value).toUpperCase();
    }

    public static String lower(String value) {
        return safe(value).toLowerCase();
    }

    public static String trim(String value) {
        return safe(value).trim();
    }

    public static boolean equalsText(String left, String right) {
        return safe(left).equals(safe(right));
    }

    public static String defaultValue(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    public static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public static List newList() {
        return new ArrayList();
    }

    public static Map newMap() {
        return new Hashtable();
    }

    public static Vector newVector() {
        return new Vector();
    }

    public static String join(List values, String separator) {
        StringBuffer text = new StringBuffer();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                text.append(separator);
            }
            text.append(values.get(i));
        }
        return text.toString();
    }

    public static String pad(int number, int width) {
        StringBuffer text = new StringBuffer(String.valueOf(number));
        while (text.length() < width) {
            text.insert(0, "0");
        }
        return text.toString();
    }

    public static String claimNumber(int id) {
        return "CLM-" + pad(id, 5);
    }

    public static boolean isPositive(double value) {
        return value > 0;
    }

    public static String logValue(String name, Object value) {
        String text = name + "=" + value;
        log.debug(text);
        System.out.println(text);
        return text;
    }

    public static Calendar calendar(String date) throws ParseException {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(parseDate(date));
        return calendar;
    }

    public static int dayOfYear(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar.get(Calendar.DAY_OF_YEAR);
    }
}
