package com.routeonomy.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

import com.routeonomy.app.domain.IndicatorPoint;

import java.util.Collections;
import java.util.List;

/** Small line chart of an indicator's latest observations, with a fading fill under the line. */
final class SparklineView extends View {
    static final int RISE = 0xffe0464a; // red = up, blue = down (Korean market convention, as in the Figma art)
    static final int FALL = 0xff3f7fd6;

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();
    private List<IndicatorPoint> points = Collections.emptyList();
    private int color = RISE;

    SparklineView(Context context, float strokePx) {
        super(context);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(strokePx);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setPoints(List<IndicatorPoint> points, boolean rising) {
        this.points = points;
        this.color = rising ? RISE : FALL;
        updateShader();
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        updateShader();
    }

    /** Fill fades from the line color to transparent; rebuilt only when the color or height changes. */
    private void updateShader() {
        fill.setShader(new LinearGradient(0, 0, 0, Math.max(1, getHeight()),
                Color.argb(70, Color.red(color), Color.green(color), Color.blue(color)),
                Color.argb(0, Color.red(color), Color.green(color), Color.blue(color)), Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas canvas) {
        int n = points.size();
        if (n < 2 || getWidth() == 0) return;
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (IndicatorPoint p : points) { min = Math.min(min, p.getValue()); max = Math.max(max, p.getValue()); }
        double range = max - min;
        float pad = line.getStrokeWidth();
        float w = getWidth() - 2 * pad, h = getHeight() - 2 * pad;
        linePath.reset();
        for (int i = 0; i < n; i++) {
            float x = pad + w * i / (n - 1);
            // A flat series sits in the middle instead of on the bottom edge.
            float y = range == 0 ? pad + h / 2 : pad + (float) (h * (max - points.get(i).getValue()) / range);
            if (i == 0) linePath.moveTo(x, y); else linePath.lineTo(x, y);
        }
        fillPath.set(linePath);
        fillPath.lineTo(pad + w, getHeight());
        fillPath.lineTo(pad, getHeight());
        fillPath.close();
        canvas.drawPath(fillPath, fill);
        line.setColor(color);
        canvas.drawPath(linePath, line);
    }
}
