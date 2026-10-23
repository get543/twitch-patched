package dev.twitchpatches.extension.settings;

import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import dev.twitchpatches.extension.ui.UiSettings;
import dev.twitchpatches.extension.ui.UiSystemBars;

public final class SettingsWindow {
    private SettingsWindow() { }

    public static void configure(Window window, int background) {
        window.setBackgroundDrawable(new ColorDrawable(background));
        window.setDimAmount(0);
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    public static AutoCloseable bind(Window window, int background) {
        configure(window, background);
        return new Session(window);
    }

    private static final class Session implements AutoCloseable, ViewTreeObserver.OnGlobalLayoutListener {
        private final Window window;
        private final UiSystemBars bars = new UiSystemBars();
        private final int originalCutout;
        private final AutoCloseable subscription;
        private boolean active = true;

        Session(Window window) {
            this.window = window;
            originalCutout = Build.VERSION.SDK_INT >= 28 ? window.getAttributes().layoutInDisplayCutoutMode : 0;
            window.getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(this);
            subscription = PatchSettings.group(UiSettings.KEY) == null ? null : UiSettings.observe(() ->
                    window.getDecorView().post(this::onGlobalLayout));
            onGlobalLayout();
        }

        @Override public void onGlobalLayout() {
            if (!active) return;
            boolean landscape = window.getContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
            apply(landscape && UiSettings.enabled("fullscreen_hide_status_bar"),
                    landscape && UiSettings.enabled("fullscreen_hide_navigation_bar"));
        }

        private void apply(boolean status, boolean navigation) {
            View decor = window.getDecorView();
            int requested = (status ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN : 0) |
                    (navigation ? View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY : 0);
            int flags = bars.apply(decor.getSystemUiVisibility(), requested);
            boolean changed = decor.getSystemUiVisibility() != flags;
            if (changed) decor.setSystemUiVisibility(flags);
            if (Build.VERSION.SDK_INT >= 28) {
                WindowManager.LayoutParams attributes = window.getAttributes();
                int cutout = status ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES : originalCutout;
                if (attributes.layoutInDisplayCutoutMode != cutout) {
                    attributes.layoutInDisplayCutoutMode = cutout;
                    window.setAttributes(attributes);
                    changed = true;
                }
            }
            if (changed) { decor.requestApplyInsets(); decor.requestLayout(); }
        }

        @Override public void close() throws Exception {
            active = false;
            apply(false, false);
            if (subscription != null) subscription.close();
            ViewTreeObserver observer = window.getDecorView().getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnGlobalLayoutListener(this);
        }
    }
}
