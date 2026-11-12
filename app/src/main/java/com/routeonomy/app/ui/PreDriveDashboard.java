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
import com.routeonomy.app.domain.BriefingSegment;

import java.time.LocalDate;

final class PreDriveDashboard extends LinearLayout {
    private static final int BACKGROUND = 0xfff4f5ef;
    private static final int INK = 0xff151815;
    private static final int MUTED = 0xff5f665e;
    private static final int NEUTRAL = 0xffe7e7e0;
    private static final int GREEN = 0xff256a49;
    private static final int MINT = 0xffd7ebe0;

    private final float scale;
    private final boolean narrow;
    private final TextView topic;
    private final TextView description;
    private final TextView status;
    private final TextView startButton;
    private final TextView changeButton;

    /** @param windowWidth width in dp of the column the dashboard is shown in (not the whole window). */
    PreDriveDashboard(Context context, int windowWidth, Runnable onStart, Runnable onChange,
            Runnable onSettings, Runnable onFeatures) {
        super(context);
        narrow = windowWidth > 0 && windowWidth < 640;
        scale = windowWidth > 0 ? Math.max(0.65f, Math.min(1f, windowWidth / 960f)) : 1f;
        setOrientation(VERTICAL);
        setBackgroundColor(BACKGROUND);
        setPadding(size(narrow ? 24 : 49), size(40), size(narrow ? 24 : 49), size(48));

        addView(header(), rowParams(0));
        addView(greeting(), rowParams(32));
        addView(sectionTitle("OVER NIGHT MARKET SUMMARY", "예시 데이터"), rowParams(26));
        addMarketRows();
        addView(sectionTitle("TODAY’S LEARNING TOPIC", "오늘의 경제 탐구"), rowParams(28));

        LinearLayout learning = row();
        styleCard(learning);
        learning.setPadding(size(15), size(11), size(15), size(11));
        FrameLayout thumbnail = new FrameLayout(context);
        thumbnail.setBackground(shape(MINT, 10));
        thumbnail.setClipToOutline(true);
        place(thumbnail, picture(R.drawable.pre_drive_chip), 119, 78, Gravity.CENTER);
        learning.addView(thumbnail, new LayoutParams(size(narrow ? 124 : 168), size(85)));

        LinearLayout copy = column();
        TextView badge = pill("Today’s learning", GREEN, MINT, 15);
        copy.addView(badge, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        topic = text("", 20, INK, true);
        copy.addView(topic, rowParams(4));
        description = text("", 15, MUTED, false);
        description.setMaxLines(2);
        copy.addView(description, rowParams(4));
        LayoutParams copyParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        copyParams.setMarginStart(size(14));
        learning.addView(copy, copyParams);
        addView(learning, rowParams(10));

        LinearLayout actions = row();
        changeButton = action("Change topic", MUTED, NEUTRAL, onChange);
        startButton = action("Start today’s learning", Color.WHITE, GREEN, onStart);
        actions.addView(changeButton, new LayoutParams(0, size(49), 1f));
        LayoutParams startParams = new LayoutParams(0, size(49), 2f);
        startParams.setMarginStart(size(12));
        actions.addView(startButton, startParams);
        addActionIcon(actions, picture(R.drawable.pre_drive_settings), "시뮬레이션 설정", onSettings);
        addActionIcon(actions, gridIcon(), "브리핑과 퀴즈 화면", onFeatures);
        addView(actions, rowParams(16));

        status = text("", 16, MUTED, false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setVisibility(GONE);
        addView(status, rowParams(8));
    }

    void bindTopic(BriefingSegment segment, boolean canChangeTopic) {
        topic.setText(segment == null ? "브리핑 없음" : segment.getTopic());
        description.setText(segment == null ? "" : "★  " + segment.getTerm().getName()
                + " · " + segment.getTerm().getExplanation());
        description.setVisibility(segment == null ? GONE : VISIBLE);
        startButton.setEnabled(segment != null);
        startButton.setAlpha(segment == null ? 0.5f : 1f);
        changeButton.setEnabled(canChangeTopic);
        changeButton.setAlpha(canChangeTopic ? 1f : 0.55f);
    }

    void showStatus(CharSequence message) {
        status.setText(message);
        status.setVisibility(message.length() == 0 ? GONE : VISIBLE);
    }

    private LinearLayout header() {
        LinearLayout header = row();
        View logo = new View(getContext());
        logo.setBackground(shape(0xff6c9181, 5));
        header.addView(logo, new LayoutParams(size(34), size(34)));
        TextView brand = text("ROUTEONOMY", 20, MUTED, false);
        LayoutParams brandParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        brandParams.setMarginStart(size(12));
        header.addView(brand, brandParams);
        header.addView(new View(getContext()), new LayoutParams(0, 1, 1f));

        LinearLayout morning = row();
        morning.setBackground(shape(NEUTRAL, 17));
        morning.setPadding(size(10), 0, size(10), 0);
        morning.addView(picture(R.drawable.pre_drive_morning), new LayoutParams(size(26), size(26)));
        TextView morningLabel = text(narrow ? "Morning" : "Morning mode", 20, MUTED, false);
        morning.addView(morningLabel);
        header.addView(morning, new LayoutParams(LayoutParams.WRAP_CONTENT, size(34)));
        header.addView(new View(getContext()), new LayoutParams(0, 1, 1f));

        if (!narrow) {
            header.addView(pill(LocalDate.now().toString(), MUTED, NEUTRAL, 20));
        }
        FrameLayout profile = new FrameLayout(getContext());
        profile.setBackground(shape(NEUTRAL, 17));
        place(profile, picture(R.drawable.pre_drive_profile), 13, 13, Gravity.CENTER);
        LayoutParams profileParams = new LayoutParams(size(34), size(34));
        profileParams.setMarginStart(size(16));
        header.addView(profile, profileParams);
        return header;
    }

    private LinearLayout greeting() {
        LinearLayout hero = row();
        hero.setMinimumHeight(size(180));
        LinearLayout copy = column();
        TextView title = text("안녕하세요,\n새싹님 좋은 아침입니다!", narrow ? 34 : 40, INK, true);
        title.setLineSpacing(size(2), 1f);
        copy.addView(title);
        copy.addView(text("오늘도 이동하는 시간을 성장의 시간으로 바꿔볼까요?", 20, MUTED, false),
                rowParams(8));
        hero.addView(copy, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        ImageView mascot = picture(R.drawable.pre_drive_mascot);
        LayoutParams mascotParams = new LayoutParams(size(narrow ? 108 : 145), size(narrow ? 120 : 160));
        mascotParams.setMarginEnd(size(narrow ? 0 : 50));
        hero.addView(mascot, mascotParams);
        return hero;
    }

    private LinearLayout sectionTitle(String heading, String caption) {
        LinearLayout headingRow = row();
        headingRow.addView(text(heading, 20, INK, false));
        TextView supporting = text(caption, 17, MUTED, false);
        LayoutParams params = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        params.setMarginStart(size(14));
        headingRow.addView(supporting, params);
        return headingRow;
    }

    private void addMarketRows() {
        LinearLayout market = row();
        addMarketCard(market, "S&P 500", true);
        addMarketCard(market, "USD/KRW", true);
        if (narrow) {
            addView(market, rowParams(12));
            market = row();
        }
        addMarketCard(market, "NASDAQ", false);
        addMarketCard(market, "S&P 500", false);
        addView(market, rowParams(12));
    }

    private void addMarketCard(LinearLayout market, String symbol, boolean rising) {
        LinearLayout card = column();
        card.setPadding(size(15), size(12), size(15), size(11));
        styleCard(card);
        card.addView(text(symbol, 15, INK, true));

        LinearLayout valueRow = row();
        FrameLayout movement = new FrameLayout(getContext());
        place(movement, picture(rising ? R.drawable.pre_drive_gain_circle : R.drawable.pre_drive_loss_circle),
                24, 24, Gravity.CENTER);
        ImageView arrow = picture(rising ? R.drawable.pre_drive_gain_arrow : R.drawable.pre_drive_loss_arrow);
        if (!rising) arrow.setScaleY(-1);
        place(movement, arrow, 12, 11, Gravity.CENTER);
        valueRow.addView(movement, new LayoutParams(size(24), size(24)));
        TextView value = text("0.05%", 20, INK, false);
        value.setContentDescription(rising ? "0.05% 상승, 예시" : "0.05% 하락, 예시");
        LayoutParams valueParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        valueParams.setMarginStart(size(3));
        valueRow.addView(value, valueParams);

        FrameLayout chart = new FrameLayout(getContext());
        place(chart, picture(rising ? R.drawable.pre_drive_gain_fill : R.drawable.pre_drive_loss_fill),
                66, rising ? 32 : 30, Gravity.TOP | Gravity.START);
        place(chart, picture(rising ? R.drawable.pre_drive_gain_line : R.drawable.pre_drive_loss_line),
                67, rising ? 31 : 24, Gravity.TOP | Gravity.START);
        valueRow.addView(chart, new LayoutParams(size(67), size(32)));
        card.addView(valueRow, rowParams(4));

        LayoutParams params = new LayoutParams(0, size(87), 1f);
        if (market.getChildCount() > 0) params.setMarginStart(size(25));
        market.addView(card, params);
    }

    private TextView action(String title, int color, int fill, Runnable click) {
        TextView action = text(title, 20, color, fill == GREEN);
        action.setGravity(Gravity.CENTER);
        action.setBackground(shape(fill, 25));
        configureAction(action, click);
        return action;
    }

    private void addActionIcon(LinearLayout row, View icon, String label, Runnable click) {
        FrameLayout action = new FrameLayout(getContext());
        action.setBackground(shape(NEUTRAL, 25));
        action.setContentDescription(label);
        configureAction(action, click);
        place(action, icon, 30, 30, Gravity.CENTER);
        LayoutParams params = new LayoutParams(size(49), size(49));
        params.setMarginStart(size(9));
        row.addView(action, params);
    }

    private void configureAction(View view, Runnable click) {
        android.util.TypedValue value = new android.util.TypedValue();
        if (getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true)) {
            view.setForeground(getContext().getDrawable(value.resourceId));
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

    private View gridIcon() {
        FrameLayout grid = new FrameLayout(getContext());
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 2; column++) {
                View cell = new View(getContext());
                GradientDrawable outline = shape(Color.TRANSPARENT, 1);
                outline.setStroke(Math.max(1, size(2)), MUTED);
                cell.setBackground(outline);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size(11), size(11));
                params.topMargin = size(3 + row * 13);
                params.leftMargin = size(3 + column * 13);
                grid.addView(cell, params);
            }
        }
        return grid;
    }

    private TextView pill(String label, int color, int fill, int textSize) {
        TextView pill = text(label, textSize, color, false);
        pill.setPadding(size(10), size(4), size(10), size(4));
        pill.setBackground(shape(fill, 17));
        return pill;
    }

    private void styleCard(View card) {
        card.setBackground(shape(Color.WHITE, 14));
        card.setElevation(size(3));
    }

    private ImageView picture(int resource) {
        ImageView image = new ImageView(getContext());
        image.setImageResource(resource);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return image;
    }

    private void place(FrameLayout parent, View child, int width, int height, int gravity) {
        parent.addView(child, new FrameLayout.LayoutParams(size(width), size(height), gravity));
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView text = new TextView(getContext());
        text.setText(value);
        text.setTextSize(Math.max(11, size * scale));
        text.setTextColor(color);
        text.setIncludeFontPadding(false);
        text.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return text;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        return row;
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(getContext());
        column.setOrientation(VERTICAL);
        return column;
    }

    private LayoutParams rowParams(int top) {
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = size(top);
        return params;
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(size(radius));
        return drawable;
    }

    private int size(float dp) {
        return Math.round(dp * scale * getResources().getDisplayMetrics().density);
    }
}
