package com.routeonomy.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.routeonomy.app.R;
import com.routeonomy.app.domain.BriefingSegment;


final class PreDriveDashboard extends LinearLayout {
    private static final int BACKGROUND = UiStyle.BACKGROUND;
    private static final int INK = UiStyle.INK;
    private static final int MUTED = UiStyle.MUTED;
    private static final int NEUTRAL = UiStyle.NEUTRAL;
    private static final int GREEN = UiStyle.GREEN;
    private static final int MINT = UiStyle.MINT;

    private final UiStyle style;
    private final boolean narrow;
    private final TextView topic;
    private final TextView description;
    private final TextView status;
    private final TextView startButton;
    private final TextView changeButton;
    private final LinearLayout learning;
    private final LinearLayout dots;
    private final Runnable onNext;
    private final Runnable onPrevious;
    private boolean canChangeTopic;

    /** @param windowWidth width in dp of the column the dashboard is shown in (not the whole window). */
    PreDriveDashboard(Context context, int windowWidth, Runnable onStart, Runnable onNext, Runnable onPrevious,
            Runnable onSettings, Runnable onFeatures) {
        super(context);
        this.onNext = onNext;
        this.onPrevious = onPrevious;
        narrow = windowWidth > 0 && windowWidth < 640;
        style = new UiStyle(context, windowWidth > 0 ? Math.max(0.65f, Math.min(1f, windowWidth / 960f)) : 1f);
        setOrientation(VERTICAL);
        setBackgroundColor(BACKGROUND);
        setPadding(size(narrow ? 24 : 49), size(40), size(narrow ? 24 : 49), size(48));

        addView(header(), rowParams(0));
        addView(greeting(), rowParams(32));
        addView(sectionTitle("OVER NIGHT MARKET SUMMARY", "예시 데이터"), rowParams(26));
        addMarketRows();
        addView(sectionTitle("TODAY’S LEARNING TOPIC", "오늘의 경제 탐구"), rowParams(28));

        learning = row();
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
        enableSwipe(learning);
        dots = row();
        dots.setGravity(Gravity.CENTER);
        dots.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(dots, rowParams(10));

        LinearLayout actions = row();
        changeButton = action("Change topic", MUTED, NEUTRAL, () -> slide(1));
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

    /** @param position index of the shown topic among [count] swipeable topics (dots); count 0 hides them. */
    void bindTopic(BriefingSegment segment, boolean canChangeTopic, int position, int count) {
        this.canChangeTopic = canChangeTopic;
        bindDots(position, count);
        topic.setText(segment == null ? "브리핑 없음" : segment.getTopic());
        description.setText(segment == null ? "" : "★  " + segment.getTerm().getName()
                + " · " + segment.getTerm().getExplanation());
        description.setVisibility(segment == null ? GONE : VISIBLE);
        startButton.setEnabled(segment != null);
        startButton.setAlpha(segment == null ? 0.5f : 1f);
        changeButton.setEnabled(canChangeTopic);
        changeButton.setAlpha(canChangeTopic ? 1f : 0.55f);
    }

    private void bindDots(int position, int count) {
        dots.removeAllViews();
        dots.setVisibility(count > 1 ? VISIBLE : GONE);
        for (int i = 0; i < count && count > 1; i++) {
            View dot = new View(getContext());
            boolean on = i == position;
            dot.setBackground(shape(on ? GREEN : NEUTRAL, 4));
            LayoutParams params = new LayoutParams(size(on ? 22 : 8), size(8));
            if (i > 0) params.setMarginStart(size(6));
            dots.addView(dot, params);
        }
    }

    /** Banner-style change: the card slides out, the topic changes, the new card slides in. */
    private void slide(int direction) {
        if (!canChangeTopic) return;
        float distance = learning.getWidth() > 0 ? learning.getWidth() : size(400);
        learning.animate().cancel();
        learning.animate().translationX(-direction * distance).alpha(0f).setDuration(160).withEndAction(() -> {
            (direction > 0 ? onNext : onPrevious).run();
            learning.setTranslationX(direction * distance);
            learning.animate().translationX(0f).alpha(1f).setDuration(160).start();
        }).start();
    }

    /** Horizontal drag on the topic card: past a threshold it changes topic, otherwise it snaps back. */
    private void enableSwipe(View card) {
        final int slop = android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();
        final float[] start = new float[2];
        final boolean[] dragging = new boolean[1];
        card.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    start[0] = event.getRawX();
                    start[1] = event.getRawY();
                    dragging[0] = false;
                    return true;
                case android.view.MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - start[0], dy = event.getRawY() - start[1];
                    if (!dragging[0] && canChangeTopic && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)) {
                        dragging[0] = true;
                        view.getParent().requestDisallowInterceptTouchEvent(true); // keep the vertical scroll out
                    }
                    if (dragging[0]) view.setTranslationX(dx * 0.6f);
                    return true;
                }
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL: {
                    float dx = event.getRawX() - start[0];
                    if (dragging[0] && Math.abs(dx) > size(80)) slide(dx < 0 ? 1 : -1);
                    else view.animate().translationX(0f).setDuration(120).start();
                    dragging[0] = false;
                    return true;
                }
                default:
                    return false;
            }
        });
    }

    void showStatus(CharSequence message) {
        status.setText(message);
        status.setVisibility(message.length() == 0 ? GONE : VISIBLE);
    }

    private LinearLayout header() {
        return style.appHeader(narrow);
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
        style.clickable(view, click);
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
        return style.pill(label, color, fill, textSize);
    }

    private void styleCard(View card) {
        style.card(card);
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
        return style.text(value, size, color, bold);
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
        return style.shape(color, radius);
    }

    private int size(float dp) {
        return style.size(dp);
    }
}
