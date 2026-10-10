package dev.pocketflight;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.widget.TextView;
import java.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowChoreographer;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FlightArtworkViewTest {
    private ActivityController<Activity> controller;
    private FlightArtworkView view;
    @Before public void open() {
        controller = Robolectric.buildActivity(Activity.class).setup();
        view = new FlightArtworkView(controller.get()); controller.get().setContentView(view, new android.view.ViewGroup.LayoutParams(216, 216));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        // Advance frames explicitly; Robolectric otherwise auto-drains animations to their end.
        ShadowChoreographer.setPaused(true); ShadowChoreographer.setFrameDelay(Duration.ofMillis(16));
        view.layout(0, 0, 216, 216); view.setForeground(true);
        assertTrue("View attached", view.isAttachedToWindow());
        assertTrue("View shown", view.isShown());
        assertEquals("Window visible", android.view.View.VISIBLE, view.getWindowVisibility());
        assertTrue("System animations enabled", ValueAnimator.areAnimatorsEnabled());
    }
    @After public void close() {
        view.setForeground(false);
        ReflectionHelpers.callStaticMethod(ValueAnimator.class, "setDurationScale", ClassParameter.from(float.class, 1f));
        controller.pause().stop().destroy();
    }
    private Bitmap draw() {
        view.layout(0, 0, 216, 216);
        Bitmap bitmap = Bitmap.createBitmap(216, 216, Bitmap.Config.ARGB_8888); view.draw(new Canvas(bitmap)); return bitmap;
    }
    private void advance(long millis) {
        for (long elapsed = 0; elapsed < millis; elapsed += 16)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(Math.min(16, millis - elapsed)));
    }
    @Test public void dotKeepsMovingAfterTakeoffAndFreezesOnStop() {
        view.setMonitoring(false, false); view.setMonitoring(true, true); advance(1600);
        Bitmap first = draw(); advance(500); assertFalse(first.sameAs(draw()));
        advance(4200); first = draw(); advance(500); assertFalse(first.sameAs(draw()));
        view.setMonitoring(false, true); advance(1600); first = draw(); advance(500); assertTrue(first.sameAs(draw()));
    }
    @Test public void leavingTheScreenStopsAnimationAndReturningResumesIt() {
        view.setMonitoring(true, false); advance(500); view.setForeground(false);
        Bitmap first = draw(); advance(600); assertTrue(first.sameAs(draw()));
        view.setForeground(true); advance(600); assertFalse(first.sameAs(draw()));
        controller.get().setContentView(new TextView(controller.get()));
        first = draw(); advance(600); assertTrue(first.sameAs(draw()));
    }
    @Test public void disabledSystemAnimationsKeepMonitoringArtworkStill() {
        ReflectionHelpers.callStaticMethod(ValueAnimator.class, "setDurationScale", ClassParameter.from(float.class, 0f));
        view.setMonitoring(true, true); Bitmap first = draw(); advance(800); assertTrue(first.sameAs(draw()));
        view.setMonitoring(false, true); assertFalse(first.sameAs(draw()));
    }
}
