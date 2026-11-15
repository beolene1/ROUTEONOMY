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
    private static final int BUBBLE = 0xffcfe9b8;

    private final UiStyle style;
    private final boolean narrow;
    private final TextView topic;
    private TextView greetingTitle;
    private String stageName = "씨앗";
    private final TextView description;
    private final TextView status;
    private final TextView startButton;
    private final TextView changeButton;
    private final LinearLayout learning;
    private final TextView bubble;
    private final LinearLayout indexSlot;
    private final TextView indexCaption;
    private String boundIndex;
    private Boolean boundRising;
    private final Runnable onNext;
    private final Runnable onPrevious;
    private boolean canChangeTopic;

    /** @param windowWidth width in dp of the column the dashboard is shown in (not the whole window). */
    PreDriveDashboard(Context context, int windowWidth, Runnable onStart, Runnable onNext, Runnable onPrevious,
            Runnable onChangeTopic, Runnable onSettings, Runnable onFeatures) {
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
        addView(sectionTitle("OVER NIGHT MARKET SUMMARY", "밤사이 글로벌 시장의 주요 흐름을 확인해 보세요."), rowParams(26));
        addMarketRows();

        // Topic heading and linked index card on the left, the study mascot with its speech bubble on the right.
        LinearLayout topicHead = row();
        topicHead.setGravity(Gravity.BOTTOM);
        LinearLayout topicLeft = column();
        topicLeft.addView(sectionTitle("TODAY’S LEARNING TOPIC", "오늘의 경제 탐구"));
        indexSlot = row();
        topicLeft.addView(indexSlot, rowParams(14));
        topicHead.addView(topicLeft, new LayoutParams(0, LayoutParams.WRAP_CONTENT, narrow ? 1f : 1.15f));
        FrameLayout buddy = new FrameLayout(context);
        bubble = text("", narrow ? 14 : 16, GREEN, true);
        bubble.setGravity(Gravity.CENTER);
        bubble.setLineSpacing(size(2), 1f);
        bubble.setPadding(size(18), size(12), size(18), size(12));
        bubble.setBackground(shape(BUBBLE, 60));
        bubble.setMaxLines(3);
        FrameLayout.LayoutParams bubbleParams = new FrameLayout.LayoutParams(size(narrow ? 170 : 210), LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        bubbleParams.topMargin = size(4);
        buddy.addView(bubble, bubbleParams);
        place(buddy, picture(R.drawable.home_study_mascot), narrow ? 110 : 150, narrow ? 111 : 151, Gravity.BOTTOM | Gravity.END);
        topicHead.addView(buddy, new LayoutParams(0, size(narrow ? 150 : 190), 1f));
        addView(topicHead, rowParams(20));
        indexCaption = text("", 15, MUTED, false);
        addView(indexCaption, rowParams(10));

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
        description.setMaxLines(1);
        description.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(description, rowParams(4));
        LayoutParams copyParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        copyParams.setMarginStart(size(14));
        learning.addView(copy, copyParams);
        addView(learning, rowParams(10));
        enableSwipe(learning);

        LinearLayout actions = row();
        changeButton = action("Change topic", MUTED, NEUTRAL, () -> { if (canChangeTopic) onChangeTopic.run(); });
        startButton = action("Start today’s learning", Color.WHITE, GREEN, onStart);
        actions.addView(changeButton, new LayoutParams(0, size(49), 1f));
        LayoutParams startParams = new LayoutParams(0, size(49), 2f);
        startParams.setMarginStart(size(12));
        actions.addView(startButton, startParams);
        addActionIcon(actions, picture(R.drawable.pre_drive_settings), "시뮬레이션 설정", onSettings);
        addActionIcon(actions, gridIcon(), "브리핑과 퀴즈 화면", onFeatures);
        addView(actions, rowParams(22));

        status = text("", 16, MUTED, false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setVisibility(GONE);
        addView(status, rowParams(8));
    }

    void bindTopic(BriefingSegment segment, boolean canChangeTopic) {
        this.canChangeTopic = canChangeTopic;
        TopicVisuals visuals = TopicVisuals.of(segment);
        topic.setText(segment == null ? "브리핑 없음" : segment.getTopic());
        description.setText(visuals.question == null ? "" : "★  " + visuals.question);
        description.setVisibility(visuals.question == null ? GONE : VISIBLE);
        bubble.setText(TopicVisuals.bubble(segment));
        bindIndex(visuals.index, visuals.indexRising);
        indexCaption.setText(visuals.indexCaption == null ? "" : "오늘의 주제 연동 지표인 " + visuals.indexCaption);
        indexCaption.setVisibility(visuals.indexCaption == null ? GONE : VISIBLE);
        startButton.setEnabled(segment != null);
        startButton.setAlpha(segment == null ? 0.5f : 1f);
        changeButton.setEnabled(canChangeTopic);
        changeButton.setAlpha(canChangeTopic ? 1f : 0.55f);
    }

    /** Linked index card (example value); rebuilt only when the symbol changes (render() runs often). */
    private void bindIndex(String symbol, boolean rising) {
        if (java.util.Objects.equals(symbol, boundIndex) && Boolean.valueOf(rising).equals(boundRising)) return;
        boundIndex = symbol;
        boundRising = rising;
        indexSlot.removeAllViews();
        indexSlot.setVisibility(symbol == null ? GONE : VISIBLE);
        if (symbol == null) return;
        addMarketCard(indexSlot, symbol, rising);
        // Same width as one market card: the rest of the row stays empty.
        LayoutParams params = (LayoutParams) indexSlot.getChildAt(0).getLayoutParams();
        params.width = 0;
        params.weight = 1f;
        indexSlot.addView(new View(getContext()), new LayoutParams(0, 1, narrow ? 0.001f : 0.72f));
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

    /** Greets with the current growth stage name, e.g. "씨앗님". */
    void setStageName(String name) {
        if (name.equals(stageName)) return;
        stageName = name;
        if (greetingTitle != null) greetingTitle.setText(greetingText());
    }

    private String greetingText() {
        return "안녕하세요,\n" + stageName + "님 좋은 아침이에요!";
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
        TextView title = text(greetingText(), narrow ? 34 : 40, INK, true);
        title.setLineSpacing(size(2), 1f);
        greetingTitle = title;
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

    /** Bold uppercase heading with its Korean caption underneath. */
    private LinearLayout sectionTitle(String heading, String caption) {
        LinearLayout title = column();
        title.addView(text(heading, 22, INK, true));
        title.addView(text(caption, 18, MUTED, false), rowParams(6));
        return title;
    }

    private void addMarketRows() {
        LinearLayout market = row();
        addMarketCard(market, "S&P 500", true);
        addMarketCard(market, "NASDAQ", false);
        if (narrow) {
            addView(market, rowParams(16));
            market = row();
        }
        addMarketCard(market, "USD/KRW", true);
        if (narrow) market.addView(new View(getContext()), new LayoutParams(0, 1, 1f));
        addView(market, rowParams(narrow ? 12 : 16));
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
