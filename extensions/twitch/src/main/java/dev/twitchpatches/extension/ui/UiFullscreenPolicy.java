package dev.twitchpatches.extension.ui;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

final class UiFullscreenPolicy implements Application.ActivityLifecycleCallbacks {
    private static final WeakHashMap<Activity, Session> sessions = new WeakHashMap<>();

    static void initialize(Application app) { app.registerActivityLifecycleCallbacks(new UiFullscreenPolicy()); }

    static int filterInsets(View view, int types) {
        try {
            if (!"fullscreen_layout".equals(view.getResources().getResourceEntryName(view.getId()))) return types;
        } catch (android.content.res.Resources.NotFoundException missing) { return types; }
        Context context = view.getContext();
        for (int depth = 0; depth < 16; depth++) {
            if (context instanceof Activity) {
                Activity activity = (Activity) context;
                Session session = sessions.get(activity);
                if (session != null) session.container = new WeakReference<>(view);
                return filterInsets(activity, types);
            }
            if (!(context instanceof ContextWrapper)) break;
            Context base = ((ContextWrapper) context).getBaseContext();
            if (base == context) break;
            context = base;
        }
        return types;
    }

    static int filterInsets(Activity activity, int types) {
        Session session = sessions.get(activity);
        boolean active = session != null && session.subscription != null;
        boolean fullscreen = active && session.fullscreen(activity);
        return UiInsetTypes.filter(types, fullscreen && UiSettings.enabled("fullscreen_hide_status_bar"),
                fullscreen && UiSettings.enabled("fullscreen_hide_navigation_bar"));
    }

    @Override public void onActivityResumed(Activity activity) {
        if (!activity.getClass().getName().equals("tv.twitch.android.feature.viewer.landing.ViewerLandingActivity")) return;
        Session session = sessions.get(activity);
        if (session == null) { session = new Session(activity); sessions.put(activity, session); }
        session.start();
    }

    @Override public void onActivityPaused(Activity activity) { Session session = sessions.get(activity); if (session != null) session.stop(); }
    @Override public void onActivityDestroyed(Activity activity) { Session session = sessions.remove(activity); if (session != null) session.stop(); }
    @Override public void onActivityCreated(Activity activity, Bundle state) { }
    @Override public void onActivityStarted(Activity activity) { }
    @Override public void onActivityStopped(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }

    private static final class Session implements android.view.ViewTreeObserver.OnGlobalLayoutListener {
        private final WeakReference<Activity> reference;
        private final int containerId;
        private final int presenterId;
        private WeakReference<View> container = new WeakReference<>(null);
        private AutoCloseable subscription;
        private final UiSystemBars bars = new UiSystemBars();
        private int cutout;
        private boolean extended;
        private boolean hideStatus;
        private boolean hideNavigation;

        Session(Activity activity) {
            reference = new WeakReference<>(activity);
            containerId = activity.getResources().getIdentifier("fullscreen_layout", "id", activity.getPackageName());
            presenterId = activity.getResources().getIdentifier("video_presenter_container", "id", activity.getPackageName());
        }

        void start() {
            Activity activity = reference.get();
            if (activity == null || subscription != null) return;
            activity.getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(this);
            subscription = UiSettings.observe(() -> {
                Activity current = reference.get();
                if (current != null) current.getWindow().getDecorView().post(this::onGlobalLayout);
            });
            onGlobalLayout();
        }

        void stop() {
            if (subscription != null) {
                try { subscription.close(); }
                catch (Exception error) { android.util.Log.w("TwitchPatchesUI", "Fullscreen subscription cleanup failed"); }
                subscription = null;
            }
            Activity activity = reference.get();
            if (activity != null) {
                View decor = activity.getWindow().getDecorView();
                if (decor.getViewTreeObserver().isAlive()) decor.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                apply(activity.getWindow(), false, false);
            }
        }

        @Override public void onGlobalLayout() {
            Activity activity = reference.get();
            if (activity == null || subscription == null) return;
            boolean fullscreen = fullscreen(activity);
            apply(activity.getWindow(), fullscreen && UiSettings.enabled("fullscreen_hide_status_bar"),
                    fullscreen && UiSettings.enabled("fullscreen_hide_navigation_bar"));
        }

        private boolean fullscreen(Activity activity) {
            boolean landscape = activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
            int orientation = activity.getRequestedOrientation();
            boolean player = orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE || orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE ||
                    orientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE || orientation == ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE;
            Window window = activity.getWindow();
            player |= (window.getAttributes().flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0 ||
                    (bars.hostFlags(window.getDecorView().getSystemUiVisibility()) & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0;
            return landscape && (player || UiFullscreenViewport.covers(container(activity), presenterId));
        }

        private void apply(Window window, boolean status, boolean navigation) {
            View decor = window.getDecorView();
            boolean changed = status != hideStatus || navigation != hideNavigation;
            hideStatus = status;
            hideNavigation = navigation;
            int requested = (status ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN : 0) |
                    (navigation ? View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY : 0);
            int current = decor.getSystemUiVisibility();
            int next = bars.apply(current, requested);
            if (next != current) decor.setSystemUiVisibility(next);
            if (Build.VERSION.SDK_INT >= 28 && status != extended) {
                WindowManager.LayoutParams attributes = window.getAttributes();
                if (status) cutout = attributes.layoutInDisplayCutoutMode;
                if (status || attributes.layoutInDisplayCutoutMode == WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES) {
                    attributes.layoutInDisplayCutoutMode = status ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES : cutout;
                    window.setAttributes(attributes);
                }
                extended = status;
            }
            if (changed) {
                decor.requestApplyInsets();
                refreshInsets();
            }
        }

        private void refreshInsets() {
            Activity activity = reference.get();
            View view = activity == null ? null : container(activity);
            if (view == null || !view.isAttachedToWindow()) return;
            WindowInsets insets = view.getRootWindowInsets();
            // Preference changes can leave the window insets unchanged while Twitch retains old padding.
            if (insets != null) view.dispatchApplyWindowInsets(insets);
        }

        private View container(Activity activity) {
            View view = container.get();
            if ((view == null || !view.isAttachedToWindow()) && containerId != 0) {
                view = activity.getWindow().getDecorView().findViewById(containerId);
                container = new WeakReference<>(view);
            }
            return view;
        }
    }
}
