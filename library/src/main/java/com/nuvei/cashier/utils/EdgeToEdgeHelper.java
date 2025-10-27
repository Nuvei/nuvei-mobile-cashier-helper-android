package com.nuvei.cashier.utils;

import android.os.Build;
import android.view.View;
import android.view.Window;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public class EdgeToEdgeHelper {
    // Full: top + bottom (system bars + keyboard)
    public static void applyFullInsets(Window window, View rootView) {
        applyInsets(window, rootView, true, true);
    }

    // Top-only (status bar / cutout)
    public static void applyTopInsets(Window window, View rootView) {
        applyInsets(window, rootView, true, false);
    }

    // Bottom-only (nav bar / keyboard)
    public static void applyBottomInsets(Window window, View rootView) {
        applyInsets(window, rootView, false, true);
    }

    // Core logic
    private static void applyInsets(Window window, View rootView, boolean topEnabled, boolean bottomEnabled) {
        if (Build.VERSION.SDK_INT >= 35) {
            WindowCompat.setDecorFitsSystemWindows(window, false);

            ViewCompat.setOnApplyWindowInsetsListener(rootView, (view, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());

                int bottomInset = Math.max(systemBars.bottom, ime.bottom);

                int topPadding = topEnabled ? systemBars.top : 0;
                int bottomPadding = bottomEnabled ? bottomInset : 0;

                view.setPadding(
                        systemBars.left,
                        topPadding,
                        systemBars.right,
                        bottomPadding
                );

                return insets;
            });
        }
    }
}
