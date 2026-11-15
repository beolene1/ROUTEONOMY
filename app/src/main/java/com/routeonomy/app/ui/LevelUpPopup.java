package com.routeonomy.app.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.routeonomy.app.domain.GrowthStage;

/**
 * P2 level-up popup (no Figma yet): "LEVEL UP" chip, the celebration character popping in over a soft glow,
 * old → new stage chips in the arrival screen's pastel gradients, XP lines, then 오늘의 정리 (P3) / 닫기.
 * Sizes are Figma dp (FigmaFrame scales the column).
 */
final class LevelUpPopup extends FrameLayout {
    private static final int[] CHIP_OLD = {0xffe7e7e0, 0xfff1ece4};
    private static final int[] CHIP_NEW = {0xffbfe9c4, 0xff9fdcae};
    private static final int[] BADGE = {0xffc9ece0, 0xfff3d6cc};

    private final UiStyle style;
    private final TextView title;
    private final ImageView character;
    private final LinearLayout path;
    private final TextView total;
    private final TextView next;
    private final TextView primary;
    private final TextView secondary;
    private Runnable onPrimary;
    private Runnable onSecondary;
    private AnimatorSet pop;
    private ObjectAnimator bob;

    LevelUpPopup(Context context) {
        super(context);
        style = new UiStyle(context, 1f);
        setBackgroundColor(0x40151815);
        setClickable(true); // swallow taps meant for the screen underneath
        setVisibility(GONE);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(style.size(44), style.size(36), style.size(44), style.size(36));
        card.setBackground(style.shape(0xeef7f7f2, 28));
        card.setElevation(style.size(12));
        card.setClipChildren(false);

        TextView badge = style.text("LEVEL UP", 20, UiStyle.GREEN, true);
        badge.setLetterSpacing(0.12f);
        badge.setPadding(style.size(22), style.size(8), style.size(22), style.size(8));
        badge.setBackground(gradient(BADGE, 24));
        card.addView(badge, style.wrapParams());

        title = style.text("", 44, UiStyle.INK, true);
        title.setGravity(Gravity.CENTER);
        card.addView(title, topMargin(style.wrapParams(), 18));

        // Celebration character over a soft round glow.
        FrameLayout stage = new FrameLayout(context);
        stage.setClipChildren(false);
        View glow = new View(context);
        GradientDrawable halo = new GradientDrawable();
        halo.setShape(GradientDrawable.OVAL);
        halo.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        halo.setGradientRadius(style.size(170));
        halo.setColors(new int[] {0xcce3f6d9, 0x00f7f7f2});
        glow.setBackground(halo);
        stage.addView(glow, new LayoutParams(style.size(340), style.size(340), Gravity.CENTER));
        character = style.picture(com.routeonomy.app.R.drawable.levelup_sprout);
        stage.addView(character, new LayoutParams(style.size(300), style.size(300), Gravity.CENTER));
        card.addView(stage, topMargin(new LinearLayout.LayoutParams(style.size(340), style.size(330)), 6));

        path = new LinearLayout(context);
        path.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(path, topMargin(style.wrapParams(), 6));

        total = style.text("", 22, UiStyle.MUTED, false);
        total.setGravity(Gravity.CENTER);
        card.addView(total, topMargin(style.wrapParams(), 20));
        next = style.text("", 22, UiStyle.GREEN, true);
        next.setGravity(Gravity.CENTER);
        card.addView(next, topMargin(style.wrapParams(), 8));

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.CENTER);
        primary = style.action("오늘의 정리", true, () -> { if (onPrimary != null) onPrimary.run(); });
        primary.setTextSize(26);
        primary.setBackground(style.shape(UiStyle.GREEN, 37));
        actions.addView(primary, new LinearLayout.LayoutParams(0, style.size(74), 2f));
        secondary = style.action("닫기", false, () -> { if (onSecondary != null) onSecondary.run(); });
        secondary.setTextSize(26);
        secondary.setBackground(style.shape(UiStyle.NEUTRAL, 37));
        LinearLayout.LayoutParams secondaryParams = new LinearLayout.LayoutParams(0, style.size(74), 1f);
        secondaryParams.setMarginStart(style.size(16));
        actions.addView(secondary, secondaryParams);
        card.addView(actions, topMargin(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT), 32));

        LayoutParams cardParams = new LayoutParams(style.size(720), LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        addView(card, cardParams);
    }

    /**
     * @param earnedTotal XP earned so far; @param toNext XP still needed for the stage after [to] (null at the last)
     */
    void show(GrowthStage from, GrowthStage to, int earnedTotal, Integer toNext, int art,
            Runnable onReview, Runnable onClose) {
        this.onPrimary = onReview;
        this.onSecondary = onClose;
        title.setText(to.getLabel() + "(으)로 자랐어요!");
        character.setImageResource(art);
        path.removeAllViews();
        path.addView(chip(from.getEmoji() + " " + from.getLabel(), CHIP_OLD, UiStyle.MUTED), style.wrapParams());
        TextView arrow = style.text("→", 28, UiStyle.MUTED, true);
        LinearLayout.LayoutParams arrowParams = style.wrapParams();
        arrowParams.setMarginStart(style.size(14));
        arrowParams.setMarginEnd(style.size(14));
        path.addView(arrow, arrowParams);
        path.addView(chip(to.getEmoji() + " " + to.getLabel() + "  Lv." + to.getLevel(), CHIP_NEW, UiStyle.INK),
                style.wrapParams());
        total.setText("지금까지 모은 XP " + earnedTotal + "XP");
        GrowthStage after = to.getNext();
        next.setText(after == null || toNext == null ? "마지막 단계에 도착했어요!"
                : "다음 단계 '" + after.getLabel() + "'까지 " + toNext + "XP 남았어요!");
        setVisibility(VISIBLE);
        animateIn();
        announceForAccessibility("레벨업! " + title.getText());
    }

    void hide() {
        if (pop != null) pop.cancel();
        if (bob != null) bob.cancel();
        setVisibility(GONE);
        onPrimary = null;
        onSecondary = null;
    }

    boolean isShowing() {
        return getVisibility() == VISIBLE;
    }

    /** Growth animation: the character pops in with a little overshoot and a turn, then keeps bobbing. */
    private void animateIn() {
        if (pop != null) pop.cancel();
        if (bob != null) bob.cancel();
        character.setScaleX(0.3f);
        character.setScaleY(0.3f);
        character.setRotation(-12f);
        character.setTranslationY(0f);
        character.setAlpha(0f);
        pop = new AnimatorSet();
        pop.playTogether(
                ObjectAnimator.ofFloat(character, View.SCALE_X, 0.3f, 1f),
                ObjectAnimator.ofFloat(character, View.SCALE_Y, 0.3f, 1f),
                ObjectAnimator.ofFloat(character, View.ROTATION, -12f, 0f),
                ObjectAnimator.ofFloat(character, View.ALPHA, 0f, 1f));
        pop.setDuration(650);
        pop.setInterpolator(new OvershootInterpolator(2.2f));
        pop.start();
        bob = ObjectAnimator.ofFloat(character, View.TRANSLATION_Y, 0f, -style.size(10));
        bob.setStartDelay(650);
        bob.setDuration(900);
        bob.setRepeatCount(ValueAnimator.INFINITE);
        bob.setRepeatMode(ValueAnimator.REVERSE);
        bob.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (pop != null) pop.cancel();
        if (bob != null) bob.cancel();
        super.onDetachedFromWindow();
    }

    private TextView chip(String label, int[] colors, int textColor) {
        TextView chip = style.text(label, 24, textColor, true);
        chip.setPadding(style.size(24), style.size(12), style.size(24), style.size(12));
        chip.setBackground(gradient(colors, 32));
        return chip;
    }

    private GradientDrawable gradient(int[] colors, int radius) {
        GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        fill.setCornerRadius(style.size(radius));
        return fill;
    }

    private LinearLayout.LayoutParams topMargin(LinearLayout.LayoutParams params, int dp) {
        params.topMargin = style.size(dp);
        return params;
    }
}
