package dev.twitchpatches.extension.player;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
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
    private boolean applied;

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
        if (activity == null) activity = runtime.findActivity(view);

        if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInPictureInPictureMode()) {
            restore();
            return;
        }

        if (!runtime.shouldExtendPlayer(view) || !view.isAttachedToWindow()) {
            restore();
            return;
        }

        ensureAncestorsExpandedAndUnclipped();
        applied = true;
    }

    private void ensureAncestorsExpandedAndUnclipped() {
        if (!clipStates.isEmpty()) return;

        if (originalLayoutParams instanceof ViewGroup.MarginLayoutParams margins) {
            margins.setMargins(0, 0, 0, 0);
        }
        originalLayoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        originalLayoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        view.setLayoutParams(originalLayoutParams);
        view.setPadding(0, 0, 0, 0);

        View current = view.getParent() instanceof View parent ? parent : null;
        while (current != null) {
            if (current instanceof ViewGroup group) {
                clipStates.add(new ClipState(group));
                group.setClipChildren(false);
                group.setClipToPadding(false);
                group.setPadding(0, 0, 0, 0);
                if (group.getLayoutParams() instanceof ViewGroup.MarginLayoutParams margins) {
                    if (margins.topMargin != 0 || margins.bottomMargin != 0 || margins.leftMargin != 0 || margins.rightMargin != 0) {
                        margins.setMargins(0, 0, 0, 0);
                        group.setLayoutParams(margins);
                    }
                }
            }
            if (current == view.getRootView()) break;
            current = current.getParent() instanceof View parent ? parent : null;
        }
    }

    void restore() {
        if (applied) {
            applied = false;
            originalLayoutParams.width = originalWidth;
            originalLayoutParams.height = originalHeight;
            if (originalLayoutParams instanceof ViewGroup.MarginLayoutParams margins) {
                margins.setMargins(originalLeftMargin, originalTopMargin, originalRightMargin, originalBottomMargin);
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
        private final int paddingLeft;
        private final int paddingTop;
        private final int paddingRight;
        private final int paddingBottom;

        ClipState(ViewGroup view) {
            this.view = view;
            clipChildren = view.getClipChildren();
            clipToPadding = view.getClipToPadding();
            paddingLeft = view.getPaddingLeft();
            paddingTop = view.getPaddingTop();
            paddingRight = view.getPaddingRight();
            paddingBottom = view.getPaddingBottom();
        }

        void restore() {
            view.setClipChildren(clipChildren);
            view.setClipToPadding(clipToPadding);
            view.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
        }
    }
}
