package dev.twitchpatches.extension.player;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.List;

final class FullScreenPlayerViewState {
    final View view;
    Activity activity;
    private final FullScreenNotchRuntime runtime;
    private final View.OnAttachStateChangeListener attachListener;
    private final List<ClipState> clipStates = new ArrayList<>();
    private boolean applied;

    FullScreenPlayerViewState(FullScreenNotchRuntime runtime, View playerView) {
        this.runtime = runtime;
        view = playerView;
        activity = runtime.findActivity(playerView);
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
        view.addOnAttachStateChangeListener(attachListener);
    }

    void dispose() {
        view.removeOnAttachStateChangeListener(attachListener);
        restore();
    }

    void update() {
        if (activity == null) activity = runtime.findActivity(view);

        if (isPipMode()) {
            restore();
            return;
        }

        if (!runtime.shouldExtendPlayer(view) || !view.isAttachedToWindow()) {
            restore();
            return;
        }

        unclipAncestors(view);
        applied = true;
    }

    private boolean isPipMode() {
        return activity != null && activity.isInPictureInPictureMode();
    }

    private void unclipAncestors(View playerView) {
        // Start from the player view itself, not just its parent
        View current = playerView;
        while (current != null) {
            if (current instanceof ViewGroup group) {
                unclipGroup(group);
            } else {
                // Strip padding from non-ViewGroup views (like the actual SurfaceView/TextureView)
                if (current.getPaddingLeft() != 0 || current.getPaddingTop() != 0 ||
                    current.getPaddingRight() != 0 || current.getPaddingBottom() != 0) {
                    current.setPadding(0, 0, 0, 0);
                }
            }

            if (current == playerView.getRootView()) break;
            current = current.getParent() instanceof View parent ? parent : null;
        }
    }

    private void unclipGroup(ViewGroup group) {
        boolean exists = false;
        for (ClipState state : clipStates) {
            if (state.view == group) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            ClipState state = new ClipState(group);
            clipStates.add(state);
            state.apply(); // Apply immediately
        }
    }

    void restore() {
        if (applied) {
            applied = false;
        }
        for (int i = clipStates.size() - 1; i >= 0; i--) clipStates.get(i).restore();
        clipStates.clear();
    }

    private static final class ClipState {
        final ViewGroup view;
        private final boolean clipChildren;
        private final boolean clipToPadding;
        private final int paddingLeft, paddingTop, paddingRight, paddingBottom;

        ClipState(ViewGroup view) {
            this.view = view;
            clipChildren = view.getClipChildren();
            clipToPadding = view.getClipToPadding();
            paddingLeft = view.getPaddingLeft();
            paddingTop = view.getPaddingTop();
            paddingRight = view.getPaddingRight();
            paddingBottom = view.getPaddingBottom();
        }

        void apply() {
            view.setClipChildren(false);
            view.setClipToPadding(false);
            view.setPadding(0, 0, 0, 0);
        }

        void restore() {
            view.setClipChildren(clipChildren);
            view.setClipToPadding(clipToPadding);
            view.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
        }
    }
}
