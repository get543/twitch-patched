package dev.twitchpatches.extension.ui;

import android.graphics.Rect;
import android.view.View;

final class UiFullscreenViewport {
    private UiFullscreenViewport() { }

    static boolean covers(View container, int presenterId) {
        if (container == null || presenterId == 0 || !container.isAttachedToWindow() || !container.isShown()) return false;
        View presenter = container.findViewById(presenterId);
        if (presenter == null || !presenter.isShown()) return false;
        Rect viewport = new Rect(), player = new Rect();
        if (!container.getGlobalVisibleRect(viewport) || !presenter.getGlobalVisibleRect(player)) return false;
        viewport.left += container.getPaddingLeft();
        viewport.top += container.getPaddingTop();
        viewport.right -= container.getPaddingRight();
        viewport.bottom -= container.getPaddingBottom();
        return !viewport.isEmpty() && player.contains(viewport);
    }
}
