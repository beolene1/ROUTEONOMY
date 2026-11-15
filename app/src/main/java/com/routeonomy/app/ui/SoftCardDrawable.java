package com.routeonomy.app.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * White rounded card with a soft drop shadow drawn around it (no elevation, so it looks the same under
 * the FigmaFrame scale). The shadow spills outside the view: its parents must not clip children.
 */
final class SoftCardDrawable extends Drawable {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float radius;

    SoftCardDrawable(int color, float radius, float blur, float offsetY, int shadowColor) {
        this.radius = radius;
        fill.setColor(color);
        fill.setShadowLayer(blur, 0f, offsetY, shadowColor);
    }

    @Override public void draw(Canvas canvas) {
        rect.set(getBounds());
        canvas.drawRoundRect(rect, radius, radius, fill);
    }

    @Override public void getOutline(Outline outline) {
        outline.setRoundRect(getBounds(), radius);
    }

    @Override public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter filter) {
        fill.setColorFilter(filter);
        invalidateSelf();
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
