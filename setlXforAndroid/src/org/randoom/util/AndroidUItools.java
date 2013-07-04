package org.randoom.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

public class AndroidUItools {

    public static boolean hideKeyboard(final View v) {
        final Object result = v.getContext().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (result != null && (result instanceof InputMethodManager)) {
            final InputMethodManager imm = (InputMethodManager) result;
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            return true;
        }
        return false;
    }

    public static boolean isTablet(final DisplayMetrics dm) {
        // Compute screen size
        final float screenWidth = dm.widthPixels / dm.xdpi;
        final float screenHeight = dm.heightPixels / dm.ydpi;
        final double size = Math.sqrt(Math.pow(screenWidth, 2)
                + Math.pow(screenHeight, 2));
        // Tablet devices should have a screen size greater than 6 inches
        return size >= 6;
    }

    public static boolean isTablet(final View v) {
        final DisplayMetrics dm = v.getContext().getResources().getDisplayMetrics();
        return isTablet(dm);
    }

    public static boolean isInPortrait(final DisplayMetrics dm) {
        return dm.heightPixels > dm.widthPixels;
    }

    public static boolean isInPortrait(final View v) {
        final DisplayMetrics dm = v.getContext().getResources().getDisplayMetrics();
        return isInPortrait(dm);
    }

    public static boolean createDirIfNotExists(final String path) {
        final File file = new File(path);
        if (!file.exists()) {
            if (!file.mkdirs()) {
                return false;
            }
        }
        return true;
    }

    public static long getUsedMemory() {
        try {
            final Runtime info = Runtime.getRuntime();
            return info.totalMemory() - info.freeMemory();
        } catch (final Exception e) {
            e.printStackTrace();
            return 0L;
        }
    }

    public static float getCPUusage(final int timeBetweenBothSamples) throws InterruptedException {
        try {
            final RandomAccessFile reader = new RandomAccessFile("/proc/stat", "r");

            // take first sample
            String[] toks = reader.readLine().split(" ");

            final long idle1 = Long.parseLong(toks[5]);
            final long cpu1  = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[4])
                             + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);

            Thread.sleep(timeBetweenBothSamples);

            // take second sample
            reader.seek(0);
            toks = reader.readLine().split(" ");
            reader.close();

            final long idle2 = Long.parseLong(toks[5]);
            final long cpu2  = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[4])
                             + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);

            // return average CPU usage
            return Math.abs((float) cpu2 - cpu1) / Math.abs((cpu2 + idle2) - (cpu1 + idle1));

        } catch (final IOException ex) {
            ex.printStackTrace();
        }

        return 0;
    }
}
