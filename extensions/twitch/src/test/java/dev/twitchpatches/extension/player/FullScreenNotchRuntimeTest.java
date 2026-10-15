package dev.twitchpatches.extension.player;

import android.content.res.Configuration;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FullScreenNotchRuntimeTest {
    @Test public void appliesOnlyForActiveLandscapePlayback() {
        assertTrue(FullScreenNotchRuntime.shouldApply(
                true, Configuration.ORIENTATION_LANDSCAPE, true));
        assertFalse(FullScreenNotchRuntime.shouldApply(
                false, Configuration.ORIENTATION_LANDSCAPE, true));
        assertFalse(FullScreenNotchRuntime.shouldApply(
                true, Configuration.ORIENTATION_LANDSCAPE, false));
        assertFalse(FullScreenNotchRuntime.shouldApply(
                true, Configuration.ORIENTATION_PORTRAIT, true));
        assertFalse(FullScreenNotchRuntime.shouldApply(
                true, Configuration.ORIENTATION_UNDEFINED, true));
    }

    @Test public void extensionIsLimitedToAvailableGapAndCutoutSafeInset() {
        assertEquals(24, FullScreenNotchRuntime.cutoutExtension(40, 24));
        assertEquals(12, FullScreenNotchRuntime.cutoutExtension(12, 24));
        assertEquals(0, FullScreenNotchRuntime.cutoutExtension(-4, 24));
        assertEquals(0, FullScreenNotchRuntime.cutoutExtension(24, -4));
    }
}
