package com.routeonomy.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared look of the Figma screens (dashboard, Now Playing, quiz): palette, cards, pills, text. */
final class UiStyle {
    static final int BACKGROUND = 0xfff4f5ef;
    static final int INK = 0xff151815;
    static final int MUTED = 0xff5f665e;
    static final int NEUTRAL = 0xffe7e7e0;
    static final int GREEN = 0xff256a49;
    static final int MINT = 0xffd7ebe0;
    static final int LOGO = 0xff6c9181;

    private final Context context;
    private final float scale;

    UiStyle(Context context, float scale) {
        this.context = context;
        this.scale = scale;
    }

    int size(float dp) {
        return Math.round(dp * scale * context.getResources().getDisplayMetrics().density);
    }

    GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(size(radius));
        return drawable;
    }

    TextView text(String value, int size, int color, boolean bold) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(Math.max(11, size * scale));
        text.setTextColor(color);
        text.setIncludeFontPadding(false);
        text.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return text;
    }

    TextView pill(String label, int color, int fill, int textSize) {
        TextView pill = text(label, textSize, color, false);
        pill.setPadding(size(10), size(4), size(10), size(4));
        pill.setBackground(shape(fill, 17));
        return pill;
    }

    void card(View card) {
        card.setBackground(shape(Color.WHITE, 14));
        card.setElevation(size(3));
    }

    /** White rounded "ROUTEONOMY" badge used at the top of the Now Playing and quiz screens. */
    LinearLayout logoPill() {
        LinearLayout pill = new LinearLayout(context);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(size(12), size(8), size(16), size(8));
        card(pill);
        View mark = new View(context);
        mark.setBackground(shape(LOGO, 5));
        pill.addView(mark, new LinearLayout.LayoutParams(size(22), size(22)));
        TextView brand = text("ROUTEONOMY", 16, INK, false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(size(10));
        pill.addView(brand, params);
        return pill;
    }

    /** Makes a plain view behave as a button (ripple, focus, click, accessibility role). */
    void clickable(View view, Runnable click) {
        android.util.TypedValue value = new android.util.TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true)) {
            view.setForeground(context.getDrawable(value.resourceId));
        }
        view.setFocusable(true);
        view.setOnClickListener(v -> click.run());
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(
                    View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.Button.class.getName());
            }
        });
    }

    /** Rounded text button: filled green for the primary action, neutral otherwise. */
    TextView action(String title, boolean primary, Runnable click) {
        TextView action = text(title, 18, primary ? Color.WHITE : MUTED, primary);
        action.setGravity(Gravity.CENTER);
        action.setPadding(size(16), 0, size(16), 0);
        action.setBackground(shape(primary ? GREEN : NEUTRAL, 25));
        clickable(action, click);
        return action;
    }
}
