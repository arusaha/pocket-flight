package dev.pocketflight;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

/** Decorative, screen-only animation. It never starts work in the monitoring service. */
public final class FlightArtworkView extends View {
    private static final int GREEN = Color.rgb(21, 104, 92);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tile = new RectF(72, 72, 144, 144);
    private final Path cloud = new Path();
    private final Drawable plane;
    private boolean monitoring, foreground, initialized;
    private float position, angle = -70, pulse;
    private ValueAnimator orbit, lift;

    public FlightArtworkView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        plane = context.getDrawable(R.drawable.ic_plane_outline).mutate();
        plane.setBounds(91, 91, 125, 125);
        cloud.moveTo(3, 14); cloud.cubicTo(-1, 14, -1, 8, 3, 8);
        cloud.cubicTo(3, 2, 12, 1, 15, 7); cloud.cubicTo(22, 5, 25, 14, 19, 14); cloud.close();
    }
    public void setMonitoring(boolean value, boolean animate) {
        boolean changed = !initialized || monitoring != value;
        monitoring = value;
        if (changed) {
            if (lift != null) { lift.cancel(); lift = null; }
            float end = value ? 1 : 0;
            if (initialized && animate && canAnimate()) {
                lift = ValueAnimator.ofFloat(position, end);
                lift.setDuration(1050); lift.setInterpolator(new OvershootInterpolator(.8f));
                lift.addUpdateListener(animation -> {
                    position = (float) animation.getAnimatedValue();
                    pulse = (float) Math.sin(Math.PI * Math.max(0, Math.min(1, animation.getAnimatedFraction()))) * .07f;
                    invalidate();
                });
                lift.start();
            } else { position = end; pulse = 0; }
            initialized = true;
        }
        syncAnimation(); invalidate();
    }
    public void setForeground(boolean value) { foreground = value; syncAnimation(); }
    private boolean canAnimate() {
        return foreground && isAttachedToWindow() && isShown() && getWindowVisibility() == VISIBLE && ValueAnimator.areAnimatorsEnabled();
    }
    private void syncAnimation() {
        if (!canAnimate()) {
            stopAnimations(); position = monitoring ? 1 : 0; pulse = 0; invalidate(); return;
        }
        if (monitoring && orbit == null) {
            orbit = ValueAnimator.ofFloat(angle, angle + 360);
            orbit.setDuration(4200); orbit.setRepeatCount(ValueAnimator.INFINITE); orbit.setInterpolator(new LinearInterpolator());
            orbit.addUpdateListener(animation -> { angle = (float) animation.getAnimatedValue() % 360; invalidate(); });
            orbit.start();
        } else if (!monitoring && orbit != null) { orbit.cancel(); orbit = null; }
    }
    private void stopAnimations() {
        if (orbit != null) { orbit.cancel(); orbit = null; }
        if (lift != null) { lift.cancel(); lift = null; }
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); syncAnimation(); }
    @Override protected void onDetachedFromWindow() { stopAnimations(); super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); syncAnimation(); }
    @Override protected void onVisibilityChanged(View changedView, int visibility) { super.onVisibilityChanged(changedView, visibility); syncAnimation(); }
    private int blend(int from, int to, float fraction) {
        float f = Math.max(0, Math.min(1, fraction));
        return Color.rgb(Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * f),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * f),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f));
    }
    private void circle(Canvas canvas, float radius, int color, boolean outline) {
        paint.setColor(color); paint.setStyle(outline ? Paint.Style.STROKE : Paint.Style.FILL); paint.setStrokeWidth(1);
        canvas.drawCircle(108, 108, radius, paint);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = Math.min(getWidth(), getHeight()) / 216f;
        canvas.save(); canvas.translate((getWidth() - 216 * scale) / 2, (getHeight() - 216 * scale) / 2); canvas.scale(scale, scale);
        circle(canvas, 74, blend(0xffe7ece3, 0xffdcebd8, position), false);
        circle(canvas, 100 * (1 + pulse), monitoring ? 0x2b15685c : 0x1815685c, true);
        circle(canvas, 77 * (1 + pulse / 2), monitoring ? 0x2b15685c : 0x1315685c, true);
        paint.setStyle(Paint.Style.FILL); paint.setColor(monitoring ? GREEN : 0xffb8ccc2);
        double radians = Math.toRadians(angle - 90);
        canvas.drawCircle(108 + 100 * (float) Math.cos(radians), 108 + 100 * (float) Math.sin(radians), 3, paint);
        paint.setColor(0xff9aafa2); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1); paint.setStrokeJoin(Paint.Join.ROUND);
        canvas.save(); canvas.translate(23, 55); canvas.drawPath(cloud, paint); canvas.restore();
        canvas.save(); canvas.translate(169, 148); canvas.scale(.8f, .8f); canvas.drawPath(cloud, paint); canvas.restore();
        canvas.save(); canvas.translate(-16 + 32 * position, 16 - 32 * position); canvas.rotate(8 - 16 * position, 108, 108);
        paint.setStyle(Paint.Style.FILL); paint.setColor(blend(Color.WHITE, GREEN, position)); canvas.drawRoundRect(tile, 24, 24, paint);
        plane.setTint(blend(0xff688479, Color.WHITE, position)); canvas.rotate(45, 108, 108); plane.draw(canvas);
        canvas.restore(); canvas.restore();
    }
}
