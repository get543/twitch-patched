import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

final class UiFullscreenViewportProbe {
    static void verify(Context context) throws Exception {
        ProbeActivity activity = new ProbeActivity(context);
        BoundsView container = new BoundsView(activity);
        container.setId(1234);
        BoundsView presenter = new BoundsView(activity);
        presenter.setId(1236);
        container.addView(presenter);
        activity.getWindow().setContentView(container);
        Class<?> type = Class.forName("dev.twitchpatches.extension.ui.UiFullscreenPolicy$Session");
        Constructor<?> constructor = type.getDeclaredConstructor(Activity.class);
        constructor.setAccessible(true);
        Object session = constructor.newInstance(activity);
        Method fullscreen = type.getDeclaredMethod("fullscreen", Activity.class);
        fullscreen.setAccessible(true);
        check(fullscreen, session, activity, true, "unlocked fullscreen player");
        container.setPadding(65, 68, 135, 0);
        presenter.bounds.set(65, 68, 2273, 1080);
        check(fullscreen, session, activity, true, "native inset padding");
        presenter.bounds.set(65, 68, 500, 400);
        check(fullscreen, session, activity, false, "miniplayer");
        presenter.bounds.set(200, 68, 2408, 1080);
        check(fullscreen, session, activity, false, "translated player");
        presenter.bounds.set(0, 0, 2408, 1080);
        presenter.shown = false;
        check(fullscreen, session, activity, false, "hidden presenter");
        presenter.shown = true;
        container.shown = false;
        check(fullscreen, session, activity, false, "hidden container");
        container.shown = true;
        container.attached = false;
        check(fullscreen, session, activity, false, "detached container");
        container.attached = true;
        activity.orientation = Configuration.ORIENTATION_PORTRAIT;
        check(fullscreen, session, activity, false, "portrait player");
        activity.orientation = Configuration.ORIENTATION_LANDSCAPE;
        container.removeView(presenter);
        check(fullscreen, session, activity, false, "empty fullscreen container on Home");
        System.out.println("PASS fullscreen viewport: unlocked landscape, native padding, miniplayer, translation, visibility, attachment, portrait and Home");
    }

    private static void check(Method method, Object session, Activity activity, boolean expected, String label) throws Exception {
        if (!Boolean.valueOf(expected).equals(method.invoke(session, activity))) throw new AssertionError(label);
    }

    private static final class BoundsView extends FrameLayout {
        final Rect bounds = new Rect(0, 0, 2408, 1080);
        boolean shown = true;
        boolean attached = true;
        BoundsView(Context context) { super(context); }
        @Override public boolean isAttachedToWindow() { return attached; }
        @Override public boolean isShown() { return shown; }
        @Override public boolean getGlobalVisibleRect(Rect result, android.graphics.Point offset) {
            result.set(bounds);
            return !bounds.isEmpty();
        }
    }

    private static final class ProbeActivity extends Activity {
        private final Window window;
        private final Resources resources;
        int orientation = Configuration.ORIENTATION_LANDSCAPE;
        ProbeActivity(Context context) {
            attachBaseContext(context);
            window = new UiFullscreenWindowProbe.ProbeWindow(context);
            Resources original = context.getResources();
            resources = new Resources(original.getAssets(), original.getDisplayMetrics(), original.getConfiguration()) {
                @Override public int getIdentifier(String name, String kind, String pkg) {
                    if ("fullscreen_layout".equals(name)) return 1234;
                    if ("video_presenter_container".equals(name)) return 1236;
                    return 0;
                }
            };
        }
        @Override public Window getWindow() { return window; }
        @Override public int getRequestedOrientation() { return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED; }
        @Override public Resources getResources() {
            resources.getConfiguration().orientation = orientation;
            return resources;
        }
    }
}
