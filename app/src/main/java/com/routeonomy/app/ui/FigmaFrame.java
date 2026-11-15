package com.routeonomy.app.ui;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Lays its child out on the Figma app column (960 × 1210 dp, the right part of the 1682 × 1210 frame)
 * and scales it uniformly to the real column, so every screen keeps the Figma proportions whatever
 * the window size. A column wider or taller than that ratio gives the child the extra room instead.
 * Sizes inside the screens are therefore written in Figma dp.
 */
final class FigmaFrame extends FrameLayout {
    static final float DESIGN_WIDTH_DP = 960f;
    static final float DESIGN_HEIGHT_DP = 1210f;

    private float fit = 1f;

    FigmaFrame(Context context) {
        super(context);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = MeasureSpec.getSize(heightSpec);
        float density = getResources().getDisplayMetrics().density;
        float scale = Math.min(width / (DESIGN_WIDTH_DP * density), height / (DESIGN_HEIGHT_DP * density));
        fit = scale > 0f ? scale : 1f;
        int childWidth = Math.round(width / fit);
        int childHeight = Math.round(height / fit);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
            child.setPivotX(0f);
            child.setPivotY(0f);
            child.setScaleX(fit);
            child.setScaleY(fit);
        }
    }
}
