package dev.pocketflight;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
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
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MainActivityTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    @Before public void open() {
        controller = Robolectric.buildActivity(MainActivity.class).setup(); activity = controller.get();
    }
    @After public void close() { controller.pause().stop().destroy(); }
    private List<View> views(View root) {
        List<View> result = new ArrayList<>(); result.add(root);
        if (root instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) result.addAll(views(group.getChildAt(i)));
        return result;
    }
    private List<View> views() { return views(activity.getWindow().getDecorView()); }
    private View labelled(String label) {
        return views().stream().filter(v -> v.getVisibility() == View.VISIBLE &&
                (label.contentEquals(v.getContentDescription() == null ? "" : v.getContentDescription())
                || v instanceof TextView text && label.contentEquals(text.getText()))).findFirst().orElseThrow(() -> new AssertionError("Missing: " + label));
    }
    private void tap(String label) {
        View view = labelled(label);
        while (!view.isClickable()) view = (View) view.getParent();
        assertTrue(view.performClick());
    }
    private void screenshot(String name) throws Exception {
        screenshot(name, 360, 800);
    }
    private void screenshot(String name, int width, int height) throws Exception {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        View root = activity.getWindow().getDecorView();
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888); root.draw(new Canvas(bitmap));
        File target = new File("build/ui-preview", name + ".png"); target.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(target)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); }
    }
    @Test public void homeContainsOnlyStatusAnimationAndPrimaryControls() throws Exception {
        labelled("Paused"); labelled("Automation is off"); labelled("Start monitoring");
        assertEquals(1, views().stream().filter(v -> v instanceof ImageButton && v.getVisibility() == View.VISIBLE).count());
        assertEquals(2, views().stream().filter(v -> v.isClickable() && v.getVisibility() == View.VISIBLE).count());
        assertFalse(views().stream().anyMatch(v -> v instanceof TextView text && text.getText().toString().contains("Connect / grant")));
        screenshot("home");
    }
    @Test public void allSettingsSectionsAreReachableAndBackReturnsHome() throws Exception {
        tap("Settings"); screenshot("settings");
        for (String section : new String[]{"Shizuku", "Permissions & battery", "Connection exceptions", "Pocket detection", "Diagnostics", "Recent activity"}) {
            tap(section); labelled("Back to settings");
            if (section.equals("Diagnostics")) { labelled("Test proximity sensor"); labelled("Last pocket report"); screenshot("diagnostics"); }
            if (section.equals("Pocket detection")) screenshot("detection");
            activity.onBackPressed(); labelled("Back to main screen");
        }
        activity.onBackPressed(); labelled("Start monitoring");
    }
    @Test public void wifiDialogStillSavesExceptions() {
        tap("Settings"); tap("Connection exceptions"); tap("Choose Wi-Fi exceptions");
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        EditText input = (EditText) views(dialog.getWindow().getDecorView()).stream().filter(v -> v instanceof EditText).findFirst().orElseThrow();
        input.setText("Home Wi-Fi\nOffice Wi-Fi"); dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(java.util.Set.of("Home Wi-Fi", "Office Wi-Fi"), new AppPrefs(activity).wifi());
    }
    @Test public void detectionPreferencesSurviveNavigationAndRecreation() {
        tap("Settings"); tap("Pocket detection");
        Switch assist = (Switch) labelled("Pocket assist\nDarkness + tilt"); assist.setChecked(true);
        activity.onBackPressed(); tap("Pocket detection");
        assertTrue(((Switch) labelled("Pocket assist\nDarkness + tilt")).isChecked());
        assertTrue(((SeekBar) labelled("Maximum light level for Pocket assist, 1 to 50 lux")).isEnabled());
        controller.recreate(); activity = controller.get();
        labelled("Pocket assist\nDarkness + tilt"); labelled("Back to settings");
    }
    @Test public void stopStillRequestsRestorationAndManualModeIsNotClaimed() {
        AppPrefs prefs = new AppPrefs(activity); prefs.enabled(true); prefs.owned(true);
        tap("Start monitoring");
        assertFalse(prefs.enabled()); assertTrue(prefs.owned()); labelled("Retry restoring connectivity");
    }
    @Test public void pocketExitDefaultsAndConfigurationSurviveRecreation() {
        AppPrefs prefs = new AppPrefs(activity);
        assertTrue(prefs.restoreOnExit()); assertEquals(120000, prefs.exitMillis());
        tap("Settings"); tap("Pocket detection");
        SeekBar delay = (SeekBar) labelled("Out-of-pocket restore delay, 1 to 30 minutes");
        assertEquals(1, delay.getProgress()); assertTrue(delay.isEnabled());
        delay.setProgress(4);
        // A user finishes a slider gesture; preserve the chosen value across a new activity.
        android.widget.SeekBar.OnSeekBarChangeListener listener = org.robolectric.util.ReflectionHelpers.getField(delay, "mOnSeekBarChangeListener");
        listener.onStopTrackingTouch(delay);
        assertEquals(300000, prefs.exitMillis());
        ((Switch) labelled("Restore after\nleaving pocket")).setChecked(false);
        assertFalse(delay.isEnabled()); assertFalse(prefs.restoreOnExit());
        controller.recreate(); activity = controller.get();
        assertFalse(((Switch) labelled("Restore after\nleaving pocket")).isChecked());
        assertEquals(4, ((SeekBar) labelled("Out-of-pocket restore delay, 1 to 30 minutes")).getProgress());
        prefs.data.edit().putInt("exit_minutes", 0).commit(); assertEquals(1, prefs.exitMinutes());
        prefs.data.edit().putInt("exit_minutes", 99).commit(); assertEquals(30, prefs.exitMinutes());
    }
    @Test @Config(sdk = 30) public void android11CanOpenSettingsAndNavigateBack() {
        tap("Settings"); tap("Diagnostics"); labelled("Last pocket report");
        activity.onBackPressed(); labelled("Back to main screen");
        activity.onBackPressed(); labelled("Start monitoring");
    }
    @Test public void compactAndUnfoldedWidthsKeepControlsWithinTheScreen() throws Exception {
        for (int width : new int[]{320, 700}) {
            screenshot("home-" + width, width, 800);
            tap("Settings"); screenshot("settings-" + width, width, 800);
            tap("Pocket detection"); screenshot("detection-" + width, width, 800);
            int[] assistLocation = new int[2]; labelled("Pocket assist\nDarkness + tilt").getLocationInWindow(assistLocation);
            assertTrue("Keep card padding after resizing", assistLocation[0] >= 40);
            for (View view : views()) if (view.isShown() && (view instanceof TextView || view instanceof ImageButton || view instanceof SeekBar)) {
                int[] location = new int[2]; view.getLocationInWindow(location);
                assertTrue("Control starts on screen at width " + width, location[0] >= 0);
                assertTrue("Control ends on screen at width " + width, location[0] + view.getWidth() <= width);
            }
            activity.onBackPressed(); activity.onBackPressed();
        }
    }
}
