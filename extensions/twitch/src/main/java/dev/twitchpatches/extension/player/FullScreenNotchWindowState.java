package dev.twitchpatches.extension.player;

import android.annotation.TargetApi;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;

final class FullScreenNotchWindowState {
    private final int cutoutMode;
    private final int systemUiVisibility;
    private final int statusBarColor;
    private final int navigationBarColor;
    private final boolean decorFitsSystemWindows;
    private boolean applied;

    FullScreenNotchWindowState(Window window) {
        WindowManager.LayoutParams attributes = window.getAttributes();
        cutoutMode = attributes.layoutInDisplayCutoutMode;
        View decorView = window.getDecorView();
        systemUiVisibility = decorView.getSystemUiVisibility();
        statusBarColor = window.getStatusBarColor();
        navigationBarColor = window.getNavigationBarColor();
        int layoutFlags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        decorFitsSystemWindows = (systemUiVisibility & layoutFlags) == 0;
    }

    @TargetApi(28)
    @SuppressWarnings("deprecation")
    void apply(Window window) {
        if (applied) return;
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        window.setAttributes(attributes);

        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        View decorView = window.getDecorView();
        decorView.setPadding(0, 0, 0, 0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decorView.setSystemUiVisibility(decorView.getSystemUiVisibility() | flags);
        }

        View.OnApplyWindowInsetsListener zeroInsetsListener = (v, insets) -> {
            v.setPadding(0, 0, 0, 0);
            if (v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams margins) {
                if (margins.topMargin > 0 || margins.bottomMargin > 0 || margins.leftMargin > 0 || margins.rightMargin > 0) {
                    margins.setMargins(0, 0, 0, 0);
                    v.setLayoutParams(margins);
                }
            }
            if (v.getLayoutParams() instanceof FrameLayout.LayoutParams frameParams) {
                if (frameParams.gravity != Gravity.CENTER) {
                    frameParams.gravity = Gravity.CENTER;
                    v.setLayoutParams(frameParams);
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return WindowInsets.CONSUMED;
            } else {
                return insets.consumeSystemWindowInsets().consumeDisplayCutout();
            }
        };

        decorView.setOnApplyWindowInsetsListener(zeroInsetsListener);

        View contentView = window.findViewById(android.R.id.content);
        if (contentView != null) {
            contentView.setPadding(0, 0, 0, 0);
            if (contentView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams margins) {
                margins.setMargins(0, 0, 0, 0);
                contentView.setLayoutParams(margins);
            }
            if (contentView.getLayoutParams() instanceof FrameLayout.LayoutParams frameParams) {
                frameParams.gravity = Gravity.CENTER;
                contentView.setLayoutParams(frameParams);
            }
            contentView.setOnApplyWindowInsetsListener(zeroInsetsListener);
        }

        applied = true;
    }

    @SuppressWarnings("deprecation")
    void restore(Window window) {
        if (!applied) return;
        WindowManager.LayoutParams attributes = window.getAttributes();
        if (attributes.layoutInDisplayCutoutMode != cutoutMode) {
            attributes.layoutInDisplayCutoutMode = cutoutMode;
            window.setAttributes(attributes);
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        window.setStatusBarColor(statusBarColor);
        window.setNavigationBarColor(navigationBarColor);

        View decorView = window.getDecorView();
        decorView.setOnApplyWindowInsetsListener(null);

        View contentView = window.findViewById(android.R.id.content);
        if (contentView != null) contentView.setOnApplyWindowInsetsListener(null);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(decorFitsSystemWindows);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int bars = WindowInsets.Type.systemBars();
                controller.show(bars);
            }
        } else {
            decorView.setSystemUiVisibility(systemUiVisibility);
        }
        applied = false;
    }
}
