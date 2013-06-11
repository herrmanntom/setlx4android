package org.randoom.util;

import java.io.File;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

public class AndroidUItools {

    public static boolean hideKeyboard(View v) {
        Object result = v.getContext().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (result != null && (result instanceof InputMethodManager)) {
            InputMethodManager imm = (InputMethodManager) result;
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            return true;
        }
        return false;
    }

    public static boolean isTablet(DisplayMetrics dm) {
        // Compute screen size
        float screenWidth = dm.widthPixels / dm.xdpi;
        float screenHeight = dm.heightPixels / dm.ydpi;
        double size = Math.sqrt(Math.pow(screenWidth, 2)
                + Math.pow(screenHeight, 2));
        // Tablet devices should have a screen size greater than 6 inches
        return size >= 6;
    }

    public static boolean isTablet(View v) {
        DisplayMetrics dm = v.getContext().getResources().getDisplayMetrics();
        return isTablet(dm);
    }

    public static boolean isInPortrait(DisplayMetrics dm) {
        return dm.heightPixels > dm.widthPixels;
    }

    public static boolean isInPortrait(View v) {
        DisplayMetrics dm = v.getContext().getResources().getDisplayMetrics();
        return isInPortrait(dm);
    }

    public static boolean createDirIfNotExists(String path) {
        File file = new File(path);
        if (!file.exists()) {
            if (!file.mkdirs()) {
                return false;
            }
        }
        return true;
    }
}
