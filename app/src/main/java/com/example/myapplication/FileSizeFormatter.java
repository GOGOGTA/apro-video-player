package com.example.myapplication;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

import java.util.Locale;

public final class FileSizeFormatter {

    private static final double UNIT = 1024.0;
    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB", "PB"};

    private FileSizeFormatter() {
    }

    public static String formatDetailedSize(Context context, long bytes) {
        long safeBytes = Math.max(bytes, 0L);
        if (safeBytes < 1024L) {
            return safeBytes + " B";
        }

        double value = safeBytes;
        int unitIndex = 0;
        while (value >= UNIT && unitIndex < UNITS.length - 1) {
            value /= UNIT;
            unitIndex++;
        }

        return String.format(getLocale(context), "%.2f %s", value, UNITS[unitIndex]);
    }

    private static Locale getLocale(Context context) {
        Configuration configuration = context.getResources().getConfiguration();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && configuration.getLocales() != null
                && !configuration.getLocales().isEmpty()) {
            return configuration.getLocales().get(0);
        }
        return Locale.getDefault();
    }
}
