package dev.twitchpatches.extension.chat;

import android.app.Application;
import android.content.SharedPreferences;
import dev.twitchpatches.extension.settings.PatchSettings;
import dev.twitchpatches.extension.settings.SettingSection;
import dev.twitchpatches.extension.settings.ToggleSetting;

public final class UnifiedChatRuntime implements ToggleSetting {
    private static volatile UnifiedChatRuntime instance;
    private final SharedPreferences preferences;
    private volatile boolean enabled;

    private UnifiedChatRuntime(Application application) {
        preferences = application.getSharedPreferences("twitch_patches_chat", 0);
        enabled = preferences.getBoolean("unified_chat_animation", true);
    }

    public static synchronized void initialize(Application application) {
        if (instance != null) return;
        instance = new UnifiedChatRuntime(application);
        PatchSettings.register(instance);
    }

    public static boolean resolveMode(boolean originalValue) {
        UnifiedChatRuntime current = instance;
        return current == null || current.enabled || originalValue;
    }

    @Override public String key() { return "unified_chat_animation"; }
    @Override public String title() { return "Clip-style chat animation"; }
    @Override public String summary() { return "Uses the smooth clip-style chat toggle animation during stream playback."; }
    @Override public SettingSection section() { return SettingSection.PLAYBACK; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean value) {
        enabled = value;
        preferences.edit().putBoolean("unified_chat_animation", value).apply();
    }
}
