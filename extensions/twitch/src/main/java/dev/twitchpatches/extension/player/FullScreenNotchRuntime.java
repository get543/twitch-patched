package dev.twitchpatches.extension.player;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.ViewTreeObserver;
import dev.twitchpatches.extension.settings.PatchSettings;
import dev.twitchpatches.extension.settings.SettingSection;
import dev.twitchpatches.extension.settings.ToggleSetting;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class FullScreenNotchRuntime implements ToggleSetting {
    private static volatile FullScreenNotchRuntime instance;
    private final SharedPreferences preferences;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<Activity, FullScreenNotchWindowState> windowStates = new IdentityHashMap<>();
    private final Map<Activity, ViewTreeObserver.OnGlobalLayoutListener> layoutListeners = new IdentityHashMap<>();
    private final Map<Activity, Boolean> activities = new IdentityHashMap<>();
    private final Map<View, FullScreenPlayerViewState> playerViews = new IdentityHashMap<>();
    private final Map<View, WeakReference<FullScreenPlayerViewState>> playerViewRegistry = new WeakHashMap<>();
    private volatile boolean enabled;

    private FullScreenNotchRuntime(Application application) {
        preferences = application.getSharedPreferences("twitch_patches_notch", 0);
        enabled = preferences.getBoolean("enabled", true);
    }

    public static synchronized void initialize(Application application) {
        if (instance != null) return;
        instance = new FullScreenNotchRuntime(application);
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                instance.track(activity);
            }
            @Override public void onActivityStarted(Activity activity) { instance.scheduleUpdate(activity); }
            @Override public void onActivityResumed(Activity activity) { instance.scheduleUpdate(activity); }
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) { instance.untrack(activity); }
        });
        PatchSettings.register(instance);
    }

    private void track(Activity activity) {
        activities.put(activity, Boolean.TRUE);
        View decorView = activity.getWindow().getDecorView();
        ViewTreeObserver.OnGlobalLayoutListener listener = () -> update(activity);
        layoutListeners.put(activity, listener);
        decorView.getViewTreeObserver().addOnGlobalLayoutListener(listener);

        decorView.setOnSystemUiVisibilityChangeListener(visibility -> {
            int orientation = activity.getResources().getConfiguration().orientation;
            if (enabled && orientation == Configuration.ORIENTATION_LANDSCAPE) {
                scheduleUpdate(activity);
            }
        });

        try {
            activity.registerComponentCallbacks(new ComponentCallbacks2() {
                @Override public void onConfigurationChanged(Configuration newConfig) {
                    scheduleUpdate(activity);
                }
                @Override public void onLowMemory() {}
                @Override public void onTrimMemory(int level) {}
            });
        } catch (Exception ignored) {}

        scheduleUpdate(activity);
    }

    private void untrack(Activity activity) {
        ViewTreeObserver.OnGlobalLayoutListener listener = layoutListeners.remove(activity);
        View decorView = activity.getWindow().getDecorView();
        if (listener != null) {
            ViewTreeObserver observer = decorView.getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnGlobalLayoutListener(listener);
        }
        activities.remove(activity);
        FullScreenNotchWindowState state = windowStates.remove(activity);
        if (state != null) state.restore(activity.getWindow());
        for (WeakReference<FullScreenPlayerViewState> reference :
                new ArrayList<>(playerViewRegistry.values())) {
            FullScreenPlayerViewState playerState = reference.get();
            if (playerState == null) continue;
            if (playerState.activity == activity) {
                playerViews.remove(playerState.view);
                playerViewRegistry.remove(playerState.view);
                playerState.dispose();
            }
        }
    }

    void scheduleUpdate(Activity activity) {
        update(activity);
        mainHandler.post(() -> update(activity));
        mainHandler.postDelayed(() -> update(activity), 100);
        mainHandler.postDelayed(() -> update(activity), 300);
        mainHandler.postDelayed(() -> update(activity), 600);
    }

    private void update(Activity activity) {
        Window window = activity.getWindow();
        if (window == null) return;

        int orientation = activity.getResources().getConfiguration().orientation;
        boolean landscape = orientation == Configuration.ORIENTATION_LANDSCAPE;
        boolean hasPlayer = hasAttachedPlayerView(activity);
        boolean shouldApply = enabled && landscape && (hasPlayer || isVideoActivity(activity));

        FullScreenNotchWindowState state = windowStates.get(activity);

        if (shouldApply && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (state == null) {
                state = new FullScreenNotchWindowState(window);
                windowStates.put(activity, state);
            }
            state.apply(window);
        } else if (state != null) {
            state.restore(window);
            windowStates.remove(activity);
        }
    }

    private boolean isVideoActivity(Activity activity) {
        String name = activity.getClass().getName();
        return name.contains("Player") || name.contains("Viewer") || name.contains("Stream") || name.contains("Landing");
    }

    private boolean hasAttachedPlayerView(Activity activity) {
        for (FullScreenPlayerViewState state : playerViews.values()) {
            if (state.activity == activity && state.view.isAttachedToWindow() && state.view.isShown()) return true;
        }
        return false;
    }

    public static void onPlayerViewAttached(View playerView) {
        FullScreenNotchRuntime current = instance;
        if (current != null) current.registerPlayerView(playerView);
    }

    private void registerPlayerView(View playerView) {
        WeakReference<FullScreenPlayerViewState> reference = playerViewRegistry.get(playerView);
        FullScreenPlayerViewState existing = reference == null ? null : reference.get();
        if (existing != null) {
            if (playerView.isAttachedToWindow()) onPlayerViewAttached(existing);
            return;
        }
        FullScreenPlayerViewState state = new FullScreenPlayerViewState(this, playerView);
        playerViewRegistry.put(playerView, new WeakReference<>(state));
        state.install();
        if (playerView.isAttachedToWindow()) onPlayerViewAttached(state);
        else state.update();
    }

    void onPlayerViewAttached(FullScreenPlayerViewState state) {
        state.activity = findActivity(state.view);
        if (state.activity != null) {
            playerViews.put(state.view, state);
            scheduleUpdate(state.activity);
        }
        state.update();
    }

    void onPlayerViewDetached(FullScreenPlayerViewState state) {
        Activity activity = state.activity;
        playerViews.remove(state.view);
        state.restore();
        if (activity != null) scheduleUpdate(activity);
    }

    boolean shouldExtendPlayer(View playerView) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                playerView.isShown() &&
                enabled &&
                playerView.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    Activity findActivity(View view) {
        Context context = view.getContext();
        while (context instanceof ContextWrapper wrapper) {
            if (context instanceof Activity activity) return activity;
            context = wrapper.getBaseContext();
        }
        View root = view.getRootView();
        for (Activity activity : activities.keySet()) {
            if (activity.getWindow().getDecorView() == root) return activity;
        }
        return null;
    }

    static int cutoutExtension(int availableGap, int safeInset) {
        return Math.min(Math.max(0, availableGap), Math.max(0, safeInset));
    }

    @Override public String key() { return "fullscreen_notch"; }
    @Override public String title() { return "Landscape edge-to-edge playback"; }
    @Override public String summary() { return "Extends landscape stream playback into the display cutout while keeping status/nav bars in portrait."; }
    @Override public SettingSection section() { return SettingSection.PLAYBACK; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean value) {
        enabled = value;
        preferences.edit().putBoolean("enabled", value).apply();
        for (Activity activity : activities.keySet().toArray(new Activity[0])) scheduleUpdate(activity);
        for (FullScreenPlayerViewState state : playerViews.values().toArray(new FullScreenPlayerViewState[0])) {
            state.update();
        }
    }
}
