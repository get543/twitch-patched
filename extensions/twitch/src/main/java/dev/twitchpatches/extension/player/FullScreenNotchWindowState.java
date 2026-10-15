package dev.twitchpatches.extension.player;

import android.annotation.TargetApi;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

final class FullScreenNotchWindowState {
    private final int cutoutMode;
    private final int systemUiVisibility;
    private final int statusBarColor;
    private final int navigationBarColor;
    private final boolean decorFitsSystemWindows;
    private final boolean statusBarsVisible;
    private final boolean navigationBarsVisible;
    private final int systemBarsBehavior;
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsets rootInsets = decorView.getRootWindowInsets();
            statusBarsVisible = rootInsets == null || rootInsets.isVisible(WindowInsets.Type.statusBars());
            navigationBarsVisible = rootInsets == null || rootInsets.isVisible(WindowInsets.Type.navigationBars());
            WindowInsetsController controller = window.getInsetsController();
            systemBarsBehavior = controller == null ? WindowInsetsController.BEHAVIOR_DEFAULT
                    : controller.getSystemBarsBehavior();
        } else {
            statusBarsVisible = (systemUiVisibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0;
            navigationBarsVisible = (systemUiVisibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_DEFAULT;
        }
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            }
        } else {
            View decorView = window.getDecorView();
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decorView.setSystemUiVisibility(decorView.getSystemUiVisibility() | flags);
        }

        View decorView = window.getDecorView();
        decorView.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return WindowInsets.CONSUMED;
            } else {
                return insets.consumeDisplayCutout();
            }
        });

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(decorFitsSystemWindows);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(systemBarsBehavior);
                int bars = WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars();
                controller.show(bars);
            }
        } else {
            decorView.setSystemUiVisibility(systemUiVisibility);
        }
        applied = false;
    }
}
