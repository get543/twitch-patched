package dev.twitchpatches.extension.player;

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

    @SuppressWarnings("deprecation")
    FullScreenNotchWindowState(Window window) {
        WindowManager.LayoutParams attributes = window.getAttributes();
        cutoutMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? attributes.layoutInDisplayCutoutMode : 0;
        View decorView = window.getDecorView();
        systemUiVisibility = decorView.getSystemUiVisibility();
        statusBarColor = window.getStatusBarColor();
        navigationBarColor = window.getNavigationBarColor();
        int layoutFlags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        decorFitsSystemWindows = (systemUiVisibility & layoutFlags) == 0;
    }

    void apply(Window window) {
        if (applied) return;
        configureCutoutMode(window);
        configureWindowFlags(window);
        hideSystemBars(window);
        consumeInsets(window);
        applied = true;
    }

    private static void configureCutoutMode(Window window) {
        WindowManager.LayoutParams attributes = window.getAttributes();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        window.setAttributes(attributes);
    }

    @SuppressWarnings("deprecation")
    private static void configureWindowFlags(Window window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
    }

    @SuppressWarnings("deprecation")
    private static void hideSystemBars(Window window) {
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
    }

    @SuppressWarnings("deprecation")
    private static void consumeInsets(Window window) {
        View.OnApplyWindowInsetsListener listener = (v, insets) -> {
            v.setPadding(0, 0, 0, 0);
            resetLayoutParams(v);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return WindowInsets.CONSUMED;
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return insets.consumeSystemWindowInsets().consumeDisplayCutout();
            } else {
                return insets.consumeSystemWindowInsets();
            }
        };

        window.getDecorView().setOnApplyWindowInsetsListener(listener);
        View contentView = window.findViewById(android.R.id.content);
        if (contentView != null) {
            contentView.setPadding(0, 0, 0, 0);
            resetLayoutParams(contentView);
            contentView.setOnApplyWindowInsetsListener(listener);
        }
    }

    private static void resetLayoutParams(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null) return;

        // Force the container to fill the entire screen space vertically and horizontally
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;

        if (params instanceof ViewGroup.MarginLayoutParams margins) {
            margins.setMargins(0, 0, 0, 0);
        }
        if (params instanceof FrameLayout.LayoutParams frameParams) {
            // FILL ensures the content stretches to the edges instead of floating in the center
            frameParams.gravity = Gravity.FILL;
        }
    }

    @SuppressWarnings("deprecation")
    void restore(Window window) {
        if (!applied) return;
        restoreCutoutMode(window);
        window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        window.setStatusBarColor(statusBarColor);
        window.setNavigationBarColor(navigationBarColor);
        showSystemBars(window);
        applied = false;
    }

    private void restoreCutoutMode(Window window) {
        WindowManager.LayoutParams attributes = window.getAttributes();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (attributes.layoutInDisplayCutoutMode != cutoutMode) {
                attributes.layoutInDisplayCutoutMode = cutoutMode;
                window.setAttributes(attributes);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void showSystemBars(Window window) {
        View decorView = window.getDecorView();
        decorView.setOnApplyWindowInsetsListener(null);

        View contentView = window.findViewById(android.R.id.content);
        if (contentView != null) contentView.setOnApplyWindowInsetsListener(null);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(decorFitsSystemWindows);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.show(WindowInsets.Type.systemBars());
            }
        } else {
            decorView.setSystemUiVisibility(systemUiVisibility);
        }
    }
}
