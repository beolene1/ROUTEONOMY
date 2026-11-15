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
import com.routeonomy.app.domain.IndicatorKind;
import com.routeonomy.app.domain.IndicatorText;
import com.routeonomy.app.domain.MarketIndicator;

import java.util.EnumMap;
import java.util.Map;


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
    private TextView greetingTitle;
    private ImageView heroMascot;
    private int heroArt;
    private String stageName = "씨앗";
    private final TextView description;
    private final TextView badge;
    private final ImageView thumbnailImage;
    private int boundThumbnail = R.drawable.pre_drive_chip; // the topic's category, e.g. "생활 경제"
    private final TextView status;
    private final TextView startButton;
    private final TextView changeButton;
    private final LinearLayout learning;
    private final TextView bubble;
    private final LinearLayout indexSlot;
    private final TextView indexCaption;
    private IndicatorKind linkedKind;
    private IndicatorCard linkedCard;
    private final Map<IndicatorKind, MarketIndicator> latestIndicators = new EnumMap<>(IndicatorKind.class);
    private final Map<IndicatorKind, IndicatorCard> indicatorCards = new EnumMap<>(IndicatorKind.class);
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
        // Sizes are Figma dp (FigmaFrame scales the column).
        setPadding(size(narrow ? 24 : 46), size(41), size(narrow ? 24 : 46), size(48));

        addView(header(), rowParams(0));
        addView(greeting(), rowParams(36));
        addView(sectionTitle("LATEST ECONOMIC INDICATORS", "최근 공개 경제지표예요. 지표마다 발표 주기가 달라 기준일을 함께 보여 드려요."), rowParams(40));
        addMarketRows();

        // Topic heading and linked index card on the left, the study mascot with its speech bubble on the right.
        LinearLayout topicHead = row();
        topicHead.setGravity(Gravity.BOTTOM);
        LinearLayout topicLeft = column();
        topicLeft.addView(sectionTitle("TODAY’S LEARNING TOPIC", "오늘의 경제 탐구"));
        indexSlot = row();
        indexSlot.setVisibility(GONE); // shown only for a topic with a linked indicator
        topicLeft.addView(indexSlot, rowParams(20));
        topicHead.addView(topicLeft, new LayoutParams(0, LayoutParams.WRAP_CONTENT, narrow ? 1f : 1.15f));
        FrameLayout buddy = new FrameLayout(context);
        bubble = text("", narrow ? 16 : 20, GREEN, true);
        bubble.setGravity(Gravity.CENTER);
        bubble.setLineSpacing(size(3), 1f);
        // Speech bubble art (tail at the bottom right, toward the mascot); text sits inside the oval.
        int bubbleSize = narrow ? 190 : 245;
        bubble.setPadding(size(bubbleSize * 0.13f), size(bubbleSize * 0.2f), size(bubbleSize * 0.09f), size(bubbleSize * 0.3f));
        bubble.setBackgroundResource(R.drawable.home_bubble);
        bubble.setMaxLines(3);
        bubble.setEllipsize(android.text.TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams bubbleParams = new FrameLayout.LayoutParams(size(bubbleSize), size(bubbleSize),
                Gravity.TOP | Gravity.START);
        buddy.addView(bubble, bubbleParams);
        place(buddy, picture(R.drawable.home_study_mascot), narrow ? 130 : 190, narrow ? 131 : 191, Gravity.BOTTOM | Gravity.END);
        topicHead.addView(buddy, new LayoutParams(0, size(narrow ? 190 : 245), 1f));
        addView(topicHead, rowParams(24));
        indexCaption = text("", 19, MUTED, false);
        addView(indexCaption, rowParams(14));

        learning = row();
        styleCard(learning);
        learning.setPadding(size(16), size(14), size(16), size(14));
        FrameLayout thumbnail = new FrameLayout(context);
        thumbnail.setBackground(shape(MINT, 10));
        thumbnail.setClipToOutline(true);
        thumbnailImage = picture(R.drawable.pre_drive_chip);
        thumbnail.addView(thumbnailImage, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        learning.addView(thumbnail, new LayoutParams(size(narrow ? 140 : 184), size(102)));

        LinearLayout copy = column();
        badge = pill("Today’s learning", GREEN, MINT, 16);
        copy.addView(badge, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        topic = text("", 27, INK, true);
        topic.setMaxLines(1);
        topic.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(topic, rowParams(6));
        description = text("", 19, MUTED, false);
        description.setMaxLines(1);
        description.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(description, rowParams(6));
        LayoutParams copyParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        copyParams.setMarginStart(size(18));
        learning.addView(copy, copyParams);
        addView(learning, rowParams(14));
        enableSwipe(learning);

        // Extra height goes here so the buttons sit at the bottom, as in Figma.
        addView(new View(context), new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout actions = row();
        changeButton = action("Change topic", MUTED, NEUTRAL, () -> { if (canChangeTopic) onChangeTopic.run(); });
        startButton = action("Start today’s learning", Color.WHITE, GREEN, onStart);
        actions.addView(changeButton, new LayoutParams(0, size(72), 1f));
        LayoutParams startParams = new LayoutParams(0, size(72), 1.77f);
        startParams.setMarginStart(size(10));
        actions.addView(startButton, startParams);
        addActionIcon(actions, picture(R.drawable.pre_drive_settings), "시뮬레이션 설정", onSettings);
        addActionIcon(actions, gridIcon(), "브리핑과 퀴즈 화면", onFeatures);
        addView(actions, rowParams(29));

        status = text("", 18, MUTED, false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setVisibility(GONE);
        addView(status, rowParams(8));
    }

    void bindTopic(BriefingSegment segment, boolean canChangeTopic) {
        this.canChangeTopic = canChangeTopic;
        TopicVisuals visuals = TopicVisuals.of(segment);
        topic.setText(segment == null ? "브리핑 없음" : segment.getTopic());
        BriefingSegment.Category category = segment == null ? null : segment.getCategory();
        badge.setText(category == null ? "Today’s learning" : category.getLabel());
        int art = TopicVisuals.thumbnail(segment);
        if (art != boundThumbnail) {
            boundThumbnail = art;
            thumbnailImage.setImageResource(art);
        }
        description.setText(visuals.question == null ? "" : "★  " + visuals.question);
        description.setVisibility(visuals.question == null ? GONE : VISIBLE);
        bubble.setText(TopicVisuals.bubble(segment));
        bindLinkedIndicator(visuals.indicator);
        indexCaption.setText(visuals.indicatorCaption == null ? "" : visuals.indicatorCaption);
        indexCaption.setVisibility(visuals.indicator == null || visuals.indicatorCaption == null ? GONE : VISIBLE);
        startButton.setEnabled(segment != null);
        startButton.setAlpha(segment == null ? 0.5f : 1f);
        changeButton.setEnabled(canChangeTopic);
        changeButton.setAlpha(canChangeTopic ? 1f : 0.55f);
    }

    /** Card for the topic's linked indicator (same data as the home cards); rebuilt only when the kind changes. */
    private void bindLinkedIndicator(IndicatorKind kind) {
        if (kind == linkedKind && (kind == null || linkedCard != null)) return;
        linkedKind = kind;
        linkedCard = null;
        indexSlot.removeAllViews();
        indexSlot.setVisibility(kind == null ? GONE : VISIBLE);
        if (kind == null) return;
        linkedCard = new IndicatorCard(kind);
        MarketIndicator latest = latestIndicators.get(kind);
        if (latest != null) linkedCard.bind(latest);
        // Same width as one home card: the rest of the row stays empty.
        indexSlot.addView(linkedCard.root, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
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

    /** Shows the character of the current growth stage next to the greeting. */
    void setStageArt(int drawable) {
        if (drawable == heroArt || heroMascot == null) return;
        heroArt = drawable;
        heroMascot.setImageResource(drawable);
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
        hero.setMinimumHeight(size(245));
        LinearLayout copy = column();
        TextView title = text(greetingText(), narrow ? 40 : 56, INK, true);
        title.setLineSpacing(size(6), 1f);
        greetingTitle = title;
        copy.addView(title);
        copy.addView(text("오늘도 이동하는 시간을 성장의 시간으로 바꿔볼까요?", 25, MUTED, false),
                rowParams(16));
        hero.addView(copy, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        ImageView mascot = picture(R.drawable.char_seed);
        heroMascot = mascot;
        LayoutParams mascotParams = new LayoutParams(size(narrow ? 140 : 192), size(narrow ? 180 : 245));
        mascotParams.setMarginEnd(size(narrow ? 0 : 14));
        hero.addView(mascot, mascotParams);
        return hero;
    }

    /** Bold uppercase heading with its Korean caption underneath. */
    private LinearLayout sectionTitle(String heading, String caption) {
        LinearLayout title = column();
        title.addView(text(heading, 26, INK, true));
        title.addView(text(caption, 22, MUTED, false), rowParams(8));
        return title;
    }

    private void addMarketRows() {
        LinearLayout market = row();
        addIndicatorCard(market, IndicatorKind.USD_KRW);
        addIndicatorCard(market, IndicatorKind.US_10Y);
        if (narrow) {
            addView(market, rowParams(24));
            market = row();
        }
        addIndicatorCard(market, IndicatorKind.US_PPI);
        if (narrow) {
            // Same width as the cards above: the empty half has the same gap in front of it.
            LayoutParams rest = new LayoutParams(0, 1, 1f);
            rest.setMarginStart(size(20));
            market.addView(new View(getContext()), rest);
        }
        addView(market, rowParams(narrow ? 16 : 24));
    }

    /** One home indicator card; filled by {@link #bindIndicator} once a lookup (or the saved value) arrives. */
    private final class IndicatorCard {
        final LinearLayout root = column();
        final ImageView circle = picture(R.drawable.pre_drive_gain_circle);
        final ImageView arrow = picture(R.drawable.pre_drive_gain_arrow);
        final FrameLayout movement = new FrameLayout(getContext());
        final TextView value = text("…", 26, INK, false);
        final TextView detail = text("불러오는 중", 16, MUTED, false);
        final SparklineView chart = new SparklineView(getContext(), size(2.5f));

        IndicatorCard(IndicatorKind kind) {
            root.setPadding(size(18), size(16), size(18), size(14));
            styleCard(root);
            TextView title = text(kind.getTitle(), 20, INK, true);
            title.setMaxLines(1);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            root.addView(title);

            LinearLayout valueRow = row();
            place(movement, circle, 31, 31, Gravity.CENTER);
            place(movement, arrow, 15, 14, Gravity.CENTER);
            movement.setVisibility(INVISIBLE);
            valueRow.addView(movement, new LayoutParams(size(31), size(31)));
            value.setMaxLines(1);
            LayoutParams valueParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            valueParams.setMarginStart(size(6));
            valueRow.addView(value, valueParams);
            valueRow.addView(chart, new LayoutParams(size(narrow ? 64 : 80), size(40)));
            root.addView(valueRow, rowParams(8));

            detail.setMaxLines(1);
            detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
            root.addView(detail, rowParams(6));
            root.addView(text(kind.getSource(), 14, MUTED, false), rowParams(2));
            root.setContentDescription(kind.getTitle() + " 불러오는 중");
        }

        void bind(MarketIndicator indicator) {
            boolean has = indicator.getLatest() != null;
            boolean rising = indicator.getRising();
            value.setText(IndicatorText.INSTANCE.value(indicator));
            value.setTextColor(has ? INK : MUTED);
            detail.setText(has ? IndicatorText.INSTANCE.detail(indicator) : "네트워크 연결 후 다시 열어 주세요");
            movement.setVisibility(has ? VISIBLE : INVISIBLE);
            circle.setImageResource(rising ? R.drawable.pre_drive_gain_circle : R.drawable.pre_drive_loss_circle);
            arrow.setImageResource(rising ? R.drawable.pre_drive_gain_arrow : R.drawable.pre_drive_loss_arrow);
            arrow.setScaleY(rising ? 1 : -1);
            chart.setPoints(indicator.getPoints(), rising);
            root.setContentDescription(IndicatorText.INSTANCE.spoken(indicator));
        }
    }

    private void addIndicatorCard(LinearLayout market, IndicatorKind kind) {
        IndicatorCard card = new IndicatorCard(kind);
        indicatorCards.put(kind, card);
        LayoutParams params = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        if (market.getChildCount() > 0) params.setMarginStart(size(narrow ? 20 : 34));
        market.addView(card.root, params);
    }

    /** Shows a lookup result (fresh, saved, or empty) on its home card. */
    void bindIndicator(MarketIndicator indicator) {
        latestIndicators.put(indicator.getKind(), indicator);
        IndicatorCard card = indicatorCards.get(indicator.getKind());
        if (card != null) card.bind(indicator);
        if (linkedCard != null && linkedKind == indicator.getKind()) linkedCard.bind(indicator);
    }

    private TextView action(String title, int color, int fill, Runnable click) {
        TextView action = text(title, 27, color, fill == GREEN);
        action.setGravity(Gravity.CENTER);
        action.setBackground(shape(fill, 36));
        configureAction(action, click);
        return action;
    }

    private void addActionIcon(LinearLayout row, View icon, String label, Runnable click) {
        FrameLayout action = new FrameLayout(getContext());
        action.setBackground(shape(NEUTRAL, 36));
        action.setContentDescription(label);
        configureAction(action, click);
        place(action, icon, 36, 36, Gravity.CENTER);
        LayoutParams params = new LayoutParams(size(72), size(72));
        params.setMarginStart(size(7));
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
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size(14), size(14));
                params.topMargin = size(2 + row * 18);
                params.leftMargin = size(2 + column * 18);
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
