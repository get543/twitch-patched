package dev.twitchpatches.extension.player;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import java.util.ArrayList;
import java.util.List;

final class FullScreenPlayerViewState {
    final View view;
    Activity activity;
    private final FullScreenNotchRuntime runtime;
    private final ViewGroup.LayoutParams originalLayoutParams;
    private final int originalWidth;
    private final int originalHeight;
    private final int originalLeftMargin;
    private final int originalTopMargin;
    private final int originalRightMargin;
    private final int originalBottomMargin;
    private final View.OnLayoutChangeListener layoutListener;
    private final View.OnAttachStateChangeListener attachListener;
    private final List<ClipState> clipStates = new ArrayList<>();
    private int appliedLeft;
    private int appliedTop;
    private int appliedRight;
    private int appliedBottom;

    FullScreenPlayerViewState(FullScreenNotchRuntime runtime, View playerView) {
        this.runtime = runtime;
        view = playerView;
        activity = runtime.findActivity(playerView);
        ViewGroup.LayoutParams params = playerView.getLayoutParams();
        if (params == null) throw new IllegalStateException("Live player view has no layout parameters.");
        originalLayoutParams = params;
        originalWidth = params.width;
        originalHeight = params.height;
        if (params instanceof ViewGroup.MarginLayoutParams margins) {
            originalLeftMargin = margins.leftMargin;
            originalTopMargin = margins.topMargin;
            originalRightMargin = margins.rightMargin;
            originalBottomMargin = margins.bottomMargin;
        } else {
            originalLeftMargin = 0;
            originalTopMargin = 0;
            originalRightMargin = 0;
            originalBottomMargin = 0;
        }
        layoutListener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> update();
        attachListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                FullScreenPlayerViewState.this.runtime.onPlayerViewAttached(FullScreenPlayerViewState.this);
            }
            @Override public void onViewDetachedFromWindow(View v) {
                FullScreenPlayerViewState.this.runtime.onPlayerViewDetached(FullScreenPlayerViewState.this);
            }
        };
    }

    void install() {
        view.addOnLayoutChangeListener(layoutListener);
        view.addOnAttachStateChangeListener(attachListener);
    }

    void dispose() {
        view.removeOnLayoutChangeListener(layoutListener);
        view.removeOnAttachStateChangeListener(attachListener);
        restore();
    }

    void update() {
        if (!runtime.shouldExtendPlayer(view) || !view.isAttachedToWindow()) {
            restore();
            return;
        }

        View root = view.getRootView();
        WindowInsets insets = view.getRootWindowInsets();
        if (insets == null || insets.getDisplayCutout() == null ||
                root.getWidth() == 0 || root.getHeight() == 0) return;

        int[] playerLocation = new int[2];
        int[] rootLocation = new int[2];
        view.getLocationOnScreen(playerLocation);
        root.getLocationOnScreen(rootLocation);

        int baseLeft = playerLocation[0] + appliedLeft;
        int baseTop = playerLocation[1] + appliedTop;
        int baseRight = playerLocation[0] + view.getWidth() - appliedRight;
        int baseBottom = playerLocation[1] + view.getHeight() - appliedBottom;
        int rootLeft = rootLocation[0];
        int rootTop = rootLocation[1];
        int rootRight = rootLeft + root.getWidth();
        int rootBottom = rootTop + root.getHeight();
        int left = extension(baseLeft - rootLeft, insets.getDisplayCutout().getSafeInsetLeft());
        int top = extension(baseTop - rootTop, insets.getDisplayCutout().getSafeInsetTop());
        int right = extension(rootRight - baseRight, insets.getDisplayCutout().getSafeInsetRight());
        int bottom = extension(rootBottom - baseBottom, insets.getDisplayCutout().getSafeInsetBottom());

        if (left == appliedLeft && top == appliedTop && right == appliedRight && bottom == appliedBottom) return;
        if (left + top + right + bottom == 0) {
            restore();
            return;
        }

        ensureAncestorsDoNotClip();
        appliedLeft = left;
        appliedTop = top;
        appliedRight = right;
        appliedBottom = bottom;
        applyMarginsAndSize();
    }

    private int extension(int gap, int inset) {
        return FullScreenNotchRuntime.cutoutExtension(gap, inset);
    }

    private void applyMarginsAndSize() {
        originalLayoutParams.width = originalWidth;
        originalLayoutParams.height = originalHeight;
        if (originalLayoutParams instanceof ViewGroup.MarginLayoutParams margins) {
            margins.setMargins(originalLeftMargin - appliedLeft, originalTopMargin - appliedTop,
                    originalRightMargin - appliedRight, originalBottomMargin - appliedBottom);
        }
        view.setLayoutParams(originalLayoutParams);
    }

    private void ensureAncestorsDoNotClip() {
        if (!clipStates.isEmpty()) return;
        View current = view.getParent() instanceof View parent ? parent : null;
        while (current != null) {
            if (current instanceof ViewGroup group) {
                clipStates.add(new ClipState(group));
                group.setClipChildren(false);
                group.setClipToPadding(false);
            }
            if (current == view.getRootView()) break;
            current = current.getParent() instanceof View parent ? parent : null;
        }
    }

    void restore() {
        if (appliedLeft + appliedTop + appliedRight + appliedBottom != 0) {
            appliedLeft = 0;
            appliedTop = 0;
            appliedRight = 0;
            appliedBottom = 0;
            originalLayoutParams.width = originalWidth;
            originalLayoutParams.height = originalHeight;
            if (originalLayoutParams instanceof ViewGroup.MarginLayoutParams margins) {
                margins.setMargins(originalLeftMargin, originalTopMargin,
                        originalRightMargin, originalBottomMargin);
            }
            view.setLayoutParams(originalLayoutParams);
        }
        for (int i = clipStates.size() - 1; i >= 0; i--) clipStates.get(i).restore();
        clipStates.clear();
    }

    private static final class ClipState {
        private final ViewGroup view;
        private final boolean clipChildren;
        private final boolean clipToPadding;

        ClipState(ViewGroup view) {
            this.view = view;
            clipChildren = view.getClipChildren();
            clipToPadding = view.getClipToPadding();
        }

        void restore() {
            view.setClipChildren(clipChildren);
            view.setClipToPadding(clipToPadding);
        }
    }
}
