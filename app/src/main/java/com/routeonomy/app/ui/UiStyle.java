package com.routeonomy.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.routeonomy.app.R;

import java.time.LocalDate;

/** Shared look of the Figma screens (dashboard, Now Playing, quiz): palette, cards, pills, text. */
final class UiStyle {
    static final int BACKGROUND = 0xfff4f5ef;
    static final int INK = 0xff151815;
    static final int MUTED = 0xff5f665e;
    static final int NEUTRAL = 0xffe7e7e0;
    static final int GREEN = 0xff256a49;
    static final int MINT = 0xffd7ebe0;
    static final int LOGO = 0xff6c9181;
    /** Sage green of progress bars and the level gear in the Korean mocks. */
    static final int SAGE = 0xff6c9181;
    static final int SURFACE = 0xfff7f7f2;
    static final int LIVE = 0xffd0473d;
    static final int PLAYER = 0xff333532;

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

    ImageView picture(int resource) {
        ImageView image = new ImageView(context);
        image.setImageResource(resource);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return image;
    }

    /** Round neutral button holding an icon or a short glyph. */
    FrameLayout iconButton(View icon, int iconSize, String label, Runnable click) {
        FrameLayout button = new FrameLayout(context);
        button.setBackground(shape(NEUTRAL, 25));
        button.setContentDescription(label);
        clickable(button, click);
        button.addView(icon, new FrameLayout.LayoutParams(size(iconSize), size(iconSize), Gravity.CENTER));
        return button;
    }

    /** Top bar shared by the dashboard, Now Playing and quiz: logo, Morning mode, date, profile. */
    LinearLayout appHeader(boolean narrow) {
        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        View logo = new View(context);
        logo.setBackground(shape(LOGO, 5));
        header.addView(logo, new LinearLayout.LayoutParams(size(34), size(34)));
        TextView brand = text("ROUTEONOMY", 20, MUTED, false);
        LinearLayout.LayoutParams brandParams = wrapParams();
        brandParams.setMarginStart(size(12));
        header.addView(brand, brandParams);
        header.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1f));

        LinearLayout morning = new LinearLayout(context);
        morning.setGravity(Gravity.CENTER_VERTICAL);
        morning.setBackground(shape(NEUTRAL, 17));
        morning.setPadding(size(10), 0, size(10), 0);
        morning.addView(picture(R.drawable.pre_drive_morning), new LinearLayout.LayoutParams(size(26), size(26)));
        morning.addView(text(narrow ? "Morning" : "Morning mode", 20, MUTED, false));
        header.addView(morning, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, size(34)));
        header.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1f));

        if (!narrow) header.addView(pill(LocalDate.now().toString(), MUTED, NEUTRAL, 20));
        FrameLayout profile = new FrameLayout(context);
        profile.setBackground(shape(NEUTRAL, 17));
        profile.addView(picture(R.drawable.pre_drive_profile),
                new FrameLayout.LayoutParams(size(13), size(13), Gravity.CENTER));
        LinearLayout.LayoutParams profileParams = new LinearLayout.LayoutParams(size(34), size(34));
        profileParams.setMarginStart(size(16));
        header.addView(profile, profileParams);
        return header;
    }

    LinearLayout.LayoutParams wrapParams() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
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
