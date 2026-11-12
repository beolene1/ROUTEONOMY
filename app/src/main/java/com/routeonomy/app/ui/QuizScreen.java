package com.routeonomy.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.routeonomy.app.R;
import com.routeonomy.app.domain.QuizQuestion;
import com.routeonomy.app.domain.QuizSession;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Figma quiz screen: progress, question card, A–C choice cards and a "정답 확인" button.
 * The pending (not yet checked) choice lives only here; grading stays in [QuizSession].
 */
final class QuizScreen extends LinearLayout {
    private static final int WRONG_FILL = 0xfff7e4df;
    private static final int WRONG = 0xffb4533f;

    private final UiStyle style;
    private final IntConsumer onCheck;
    private final Runnable onNext;
    private final TextView category;
    private final TextView progressLabel;
    private final View progressFill;
    private final View progressRest;
    private final TextView question;
    private final ImageView chip;
    private final LinearLayout choices;
    private final TextView feedback;
    private final TextView primary;
    private QuizSession quiz;
    private Integer pending;

    QuizScreen(Context context, Runnable onClose, IntConsumer onCheck, Runnable onNext) {
        super(context);
        this.onCheck = onCheck;
        this.onNext = onNext;
        style = new UiStyle(context, 1f);
        setOrientation(VERTICAL);

        LinearLayout header = row();
        header.addView(style.logoPill(), wrap());
        category = style.pill("", UiStyle.GREEN, UiStyle.MINT, 15);
        category.setSingleLine(true);
        category.setEllipsize(TextUtils.TruncateAt.END);
        // Compact pill centred in the space between logo and close button.
        android.widget.FrameLayout categorySlot = new android.widget.FrameLayout(context);
        categorySlot.addView(category, new android.widget.FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        LayoutParams categoryParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        categoryParams.setMarginStart(style.size(16));
        categoryParams.setMarginEnd(style.size(16));
        header.addView(categorySlot, categoryParams);
        TextView close = style.text("✕", 22, UiStyle.INK, false);
        close.setGravity(Gravity.CENTER);
        close.setBackground(style.shape(Color.WHITE, 24));
        close.setElevation(style.size(3));
        close.setContentDescription("퀴즈 닫기");
        style.clickable(close, onClose);
        header.addView(close, new LayoutParams(style.size(48), style.size(48)));
        addView(header, matchWrap(0));

        LinearLayout progress = row();
        progressLabel = style.text("", 16, UiStyle.INK, true);
        progress.addView(progressLabel, wrap());
        LinearLayout track = new LinearLayout(context);
        track.setBackground(style.shape(UiStyle.NEUTRAL, 4));
        track.setClipToOutline(true);
        progressFill = new View(context);
        progressFill.setBackground(style.shape(UiStyle.GREEN, 4));
        progressRest = new View(context);
        track.addView(progressFill, new LayoutParams(0, LayoutParams.MATCH_PARENT, 0f));
        track.addView(progressRest, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        LayoutParams trackParams = new LayoutParams(0, style.size(8), 1f);
        trackParams.setMarginStart(style.size(16));
        progress.addView(track, trackParams);
        addView(progress, matchWrap(24));
        TextView basis = style.text("오늘 끝까지 들은 브리핑에서 출제", 14, UiStyle.MUTED, false);
        basis.setGravity(Gravity.CENTER);
        addView(basis, matchWrap(8));

        LinearLayout card = row();
        card.setPadding(style.size(22), style.size(20), style.size(16), style.size(20));
        style.card(card);
        question = style.text("", 24, UiStyle.INK, true);
        question.setLineSpacing(style.size(4), 1f);
        card.addView(question, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        chip = new ImageView(context);
        chip.setImageResource(R.drawable.pre_drive_chip);
        chip.setScaleType(ImageView.ScaleType.FIT_CENTER);
        chip.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutParams chipParams = new LayoutParams(style.size(120), style.size(90));
        chipParams.setMarginStart(style.size(12));
        card.addView(chip, chipParams);
        addView(card, matchWrap(16));

        choices = new LinearLayout(context);
        choices.setOrientation(VERTICAL);
        addView(choices, matchWrap(8));

        feedback = style.text("", 17, UiStyle.INK, false);
        feedback.setLineSpacing(style.size(4), 1f);
        feedback.setPadding(style.size(20), style.size(14), style.size(20), style.size(14));
        feedback.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        addView(feedback, matchWrap(16));

        primary = style.action("", true, this::onPrimary);
        LayoutParams primaryParams = new LayoutParams(LayoutParams.MATCH_PARENT, style.size(54));
        primaryParams.topMargin = style.size(16);
        addView(primary, primaryParams);
    }

    /** Called when the quiz object, question or answer changed; drops any unchecked selection. */
    void bind(QuizSession quiz, String categoryLabel) {
        this.quiz = quiz;
        pending = null;
        category.setText(categoryLabel);
        category.setVisibility(categoryLabel == null || categoryLabel.isEmpty() ? INVISIBLE : VISIBLE);
        refresh();
    }

    private void refresh() {
        int total = quiz.total();
        boolean finished = quiz.isFinished();
        int done = finished ? total : quiz.position() + (quiz.isAnswered() ? 1 : 0);
        progressLabel.setText(Math.min(quiz.position() + 1, total) + " / " + total);
        float share = total == 0 ? 0f : (float) done / total;
        ((LayoutParams) progressFill.getLayoutParams()).weight = share;
        ((LayoutParams) progressRest.getLayoutParams()).weight = 1f - share;
        progressFill.requestLayout();
        choices.removeAllViews();

        if (finished) {
            question.setText("퀴즈 결과\n" + total + "문제 중 " + quiz.score() + "개 정답");
            showFeedback(quiz.score() == total ? "모두 맞혔어요! 오늘 브리핑을 잘 기억하고 계시네요."
                    : "틀린 문제는 오늘의 발견에서 다시 확인해 보세요.", quiz.score() == total);
            setPrimary("다시 풀기", true);
            return;
        }
        QuizQuestion current = quiz.current();
        question.setText(current.getQuestion());
        Integer selected = quiz.selectedChoice();
        List<String> options = current.getChoices();
        LinearLayout line = null;
        for (int i = 0; i < options.size(); i++) {
            if (i % 2 == 0) {
                line = row();
                line.setBaselineAligned(false);
                choices.addView(line, matchWrap(12));
            }
            View choice = choiceCard(i, options.get(i), current.getAnswerIndex(), selected);
            LayoutParams params = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
            if (i % 2 == 1) params.setMarginStart(style.size(12));
            line.addView(choice, params);
        }
        if (options.size() % 2 == 1) {
            LayoutParams filler = new LayoutParams(0, 1, 1f);
            filler.setMarginStart(style.size(12));
            line.addView(new View(getContext()), filler);
        }

        if (selected == null) {
            feedback.setVisibility(GONE);
            setPrimary("정답 확인", pending != null);
        } else {
            boolean correct = selected == current.getAnswerIndex();
            showFeedback((correct ? "정답입니다!" : "아쉬워요. 정답은 " + letter(current.getAnswerIndex()) + "입니다.")
                    + "\n" + current.getExplanation(), correct);
            setPrimary(quiz.position() + 1 < total ? "다음 문제" : "결과 보기", true);
        }
    }

    private View choiceCard(int index, String label, int answer, Integer selected) {
        boolean checked = selected != null;
        boolean isAnswer = checked && index == answer;
        boolean isWrongPick = checked && index == selected && index != answer;
        boolean isPending = !checked && pending != null && pending == index;

        LinearLayout card = row();
        card.setPadding(style.size(16), style.size(16), style.size(16), style.size(16));
        card.setMinimumHeight(style.size(72));
        GradientDrawable background = style.shape(isAnswer || isPending ? UiStyle.MINT
                : isWrongPick ? WRONG_FILL : Color.WHITE, 14);
        if (isAnswer || isPending) background.setStroke(style.size(2), UiStyle.GREEN);
        else if (isWrongPick) background.setStroke(style.size(2), WRONG);
        card.setBackground(background);
        card.setElevation(style.size(2));
        if (checked && !isAnswer && !isWrongPick) card.setAlpha(0.6f);

        String mark = isAnswer ? "✓" : isWrongPick ? "✗" : letter(index);
        TextView badge = style.text(mark, 16, isWrongPick ? WRONG : UiStyle.INK, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(style.shape(isAnswer || isPending ? Color.WHITE : UiStyle.NEUTRAL, 18));
        badge.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(badge, new LayoutParams(style.size(36), style.size(36)));
        TextView text = style.text(label, 18, UiStyle.INK, isPending || isAnswer);
        text.setLineSpacing(style.size(3), 1f);
        LayoutParams textParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMarginStart(style.size(12));
        card.addView(text, textParams);

        String state = isAnswer ? ", 정답" : isWrongPick ? ", 선택한 오답" : isPending ? ", 선택됨" : "";
        card.setContentDescription(letter(index) + ". " + label + state);
        if (!checked) {
            style.clickable(card, () -> {
                pending = index;
                refresh();
            });
        }
        return card;
    }

    private void onPrimary() {
        if (quiz == null) return;
        if (!quiz.isFinished() && !quiz.isAnswered()) {
            if (pending != null) onCheck.accept(pending);
        } else {
            onNext.run();
        }
    }

    private void showFeedback(String message, boolean good) {
        feedback.setText(message);
        feedback.setBackground(style.shape(good ? UiStyle.MINT : WRONG_FILL, 14));
        feedback.setVisibility(VISIBLE);
    }

    private void setPrimary(String label, boolean enabled) {
        primary.setText(label);
        primary.setEnabled(enabled);
        primary.setAlpha(enabled ? 1f : 0.45f);
    }

    private static String letter(int index) {
        return String.valueOf((char) ('A' + index));
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private LayoutParams wrap() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    private LayoutParams matchWrap(int top) {
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = style.size(top);
        return params;
    }
}
