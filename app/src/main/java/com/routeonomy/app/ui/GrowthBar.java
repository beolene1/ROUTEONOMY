package com.routeonomy.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Stage progress gauge on the arrival screen: a rounded track with a gradient fill drawn to
 * [progress] (XP earned inside the current stage ÷ XP the stage needs) and "128 / 300 XP" on it.
 * Changes animate, so a reward visibly fills the bar.
 */
final class GrowthBar extends View {
    private static final int TRACK = 0xff98aa9f;
    private static final int FILL_START = 0xff9be3a6;
    private static final int FILL_END = 0xffd7f3e4;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float progress;
    private float target = -1f;
    private String text = "";
    private ValueAnimator animator;

    GrowthBar(Context context, float textSizePx) {
        super(context);
        track.setColor(TRACK);
        label.setColor(0xffffffff);
        label.setTextSize(textSizePx);
        label.setFakeBoldText(true);
        label.setTextAlign(Paint.Align.RIGHT);
    }

    /** @param share 0..1 of the current stage; @param caption e.g. "128 / 300 XP" */
    void setProgress(float share, String caption) {
        float clamped = Math.max(0f, Math.min(1f, share));
        if (!caption.equals(text)) {
            text = caption;
            setContentDescription("다음 단계까지 진행 " + Math.round(clamped * 100) + "퍼센트, " + caption);
            invalidate();
        }
        if (clamped == target) return; // bound on every render(); animate only real changes
        float from = target < 0f ? 0f : progress;
        target = clamped;
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(from, clamped);
        animator.setDuration(900);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        fill.setShader(new LinearGradient(0, 0, w, 0, FILL_START, FILL_END, Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight(), r = h / 2f;
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, r, r, track);
        if (progress > 0f) {
            rect.set(0, 0, Math.max(h, w * progress), h); // at least a full round cap
            canvas.drawRoundRect(rect, r, r, fill);
        }
        float baseline = h / 2f - (label.descent() + label.ascent()) / 2f;
        canvas.drawText(text, w - r, baseline, label);
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
}
