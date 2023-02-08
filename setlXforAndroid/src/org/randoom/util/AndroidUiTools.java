package org.randoom.util;

import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import org.randoom.setlxUI.android.R;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Some simple utility functions.
 */
public class AndroidUiTools {

    /**
     * Hide the soft-keyboard.
     *
     * @param  v Current view.
     * @return True on success.
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean hideKeyboard(final View v) {
        final Object result = v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (result instanceof InputMethodManager) {
            final InputMethodManager imm = (InputMethodManager) result;
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            return true;
        }
        return false;
    }

    /**
     * Test if the currently loaded layout is the tablet version.
     *
     * Relies on a string value resource called "layoutID", that is
     * set to "tablet" on a tablet-device.
     *
     * @param context The current UI context.
     * @return True if a tablet layout is loaded.
     */
    public static boolean isTablet(final Context context) {
        return context.getString(R.string.layoutID).equals("tablet");
    }

    /**
     * Check if the device currently is in portrait mode.
     *
     * @param dm DisplayMetrics of the current view.
     * @return   True if the view is higher than wide.
     */
    public static boolean isInPortrait(final DisplayMetrics dm) {
        return dm.heightPixels > dm.widthPixels;
    }

    /**
     * Check if the device currently is in portrait mode.
     *
     * @param v The current view.
     * @return  True if the view is higher than wide.
     */
    @SuppressWarnings("unused")
    public static boolean isInPortrait(final View v) {
        final DisplayMetrics dm = v.getContext().getResources().getDisplayMetrics();
        return isInPortrait(dm);
    }

    /**
     * Get the amount of currently used memory of this program in Bytes.
     *
     * @return Amount of currently used memory in Bytes.
     */
    public static long getUsedMemory() {
        try {
            final Runtime info = Runtime.getRuntime();
            return info.totalMemory() - info.freeMemory();
        } catch (final Exception e) {
            e.printStackTrace();
            return 0L;
        }
    }

    /**
     * Get the average CPU utilization of the system, sampling two times.
     *
     * @param timeBetweenBothSamples How long to wait between samples.
     * @return                       Average CPU utilization between 0.0 and 1.0.
     * @throws InterruptedException  Thread was interrupted while waiting.
     */
    public static float getCpuUsage(final int timeBetweenBothSamples) throws InterruptedException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return -1;
        }
        try {
            final RandomAccessFile reader = new RandomAccessFile("/proc/stat", "r");

            // take first sample
            String[] tokens = reader.readLine().split(" ");

            final long idle1 = Long.parseLong(tokens[5]);
            final long cpu1  = Long.parseLong(tokens[2]) + Long.parseLong(tokens[3]) + Long.parseLong(tokens[4])
                             + Long.parseLong(tokens[6]) + Long.parseLong(tokens[7]) + Long.parseLong(tokens[8]);

            Thread.sleep(timeBetweenBothSamples);

            // take second sample
            reader.seek(0);
            tokens = reader.readLine().split(" ");
            reader.close();

            final long idle2 = Long.parseLong(tokens[5]);
            final long cpu2  = Long.parseLong(tokens[2]) + Long.parseLong(tokens[3]) + Long.parseLong(tokens[4])
                             + Long.parseLong(tokens[6]) + Long.parseLong(tokens[7]) + Long.parseLong(tokens[8]);

            // return average CPU usage
            return Math.abs((float) cpu2 - cpu1) / Math.abs((cpu2 + idle2) - (cpu1 + idle1));

        } catch (final IOException ex) {
            ex.printStackTrace();
        }

        return 0;
    }
}
