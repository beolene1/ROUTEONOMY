package com.routeonomy.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.routeonomy.app.R;
import com.routeonomy.app.domain.QuizQuestion;
import com.routeonomy.app.domain.QuizSession;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Korean Figma quiz screen: app header, intro, answered/total bar, "Q n." card,
 * 2×2 choice cards and the 힌트 / 정답 제출하기 / ? / settings / close row.
 * The pending (not yet submitted) choice lives only here; grading stays in [QuizSession].
 */
final class QuizScreen extends LinearLayout {
    private static final int WRONG_FILL = 0xfff7e4df;
    private static final int WRONG = 0xffb4533f;

    private final UiStyle style;
    private final IntConsumer onSubmit;
    private final Runnable onNext;
    private final TextView intro;
    private final TextView progressLabel;
    private final View progressFill;
    private final View progressRest;
    private final TextView number;
    private final TextView question;
    private final LinearLayout choices;
    private final TextView feedback;
    private final TextView hintButton;
    private final TextView primary;
    private QuizSession quiz;
    private String hint;
    private Integer pending;
    private String note; // hint or help text kept while the user picks a choice

    QuizScreen(Context context, Runnable onHome, Runnable onClose, Runnable onSettings, IntConsumer onSubmit, Runnable onNext) {
        super(context);
        this.onSubmit = onSubmit;
        this.onNext = onNext;
        style = new UiStyle(context, 1f);
        setOrientation(VERTICAL);

        addView(style.appHeader(false), matchWrap(0));
        TextView home = style.action("← 홈으로", false, onHome);
        home.setContentDescription("홈 화면으로 가기");
        LayoutParams homeParams = new LayoutParams(LayoutParams.WRAP_CONTENT, style.size(44));
        homeParams.topMargin = style.size(12);
        addView(home, homeParams);
        intro = style.text("", 19, UiStyle.INK, false);
        intro.setGravity(Gravity.CENTER);
        intro.setLineSpacing(style.size(4), 1f);
        addView(intro, matchWrap(12));

        LinearLayout progress = row();
        progressLabel = style.text("", 18, UiStyle.MUTED, false);
        progress.addView(progressLabel, style.wrapParams());
        LinearLayout track = new LinearLayout(context);
        track.setBackground(style.shape(UiStyle.NEUTRAL, 8));
        track.setClipToOutline(true);
        progressFill = new View(context);
        progressFill.setBackground(style.shape(UiStyle.SAGE, 8));
        progressRest = new View(context);
        track.addView(progressFill, new LayoutParams(0, LayoutParams.MATCH_PARENT, 0f));
        track.addView(progressRest, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        LayoutParams trackParams = new LayoutParams(0, style.size(16), 1f);
        trackParams.setMarginStart(style.size(12));
        progress.addView(track, trackParams);
        addView(progress, matchWrap(14));

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(VERTICAL);
        card.setPadding(style.size(22), style.size(14), style.size(22), style.size(20));
        style.card(card);
        TextView badge = style.pill("Today’s learning", UiStyle.GREEN, UiStyle.MINT, 13);
        badge.setPadding(style.size(8), style.size(2), style.size(8), style.size(2));
        LayoutParams badgeParams = style.wrapParams();
        badgeParams.setMarginStart(style.size(46));
        card.addView(badge, badgeParams);
        LinearLayout questionRow = row();
        number = style.text("", 26, UiStyle.SAGE, true);
        questionRow.addView(number, style.wrapParams());
        question = style.text("", 19, UiStyle.INK, true);
        question.setLineSpacing(style.size(3), 1f);
        LayoutParams questionParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        questionParams.setMarginStart(style.size(8));
        questionRow.addView(question, questionParams);
        card.addView(questionRow, matchWrap(4));
        addView(card, matchWrap(20));

        choices = new LinearLayout(context);
        choices.setOrientation(VERTICAL);
        addView(choices, matchWrap(6));

        feedback = style.text("", 17, UiStyle.INK, false);
        feedback.setLineSpacing(style.size(4), 1f);
        feedback.setPadding(style.size(20), style.size(14), style.size(20), style.size(14));
        feedback.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        addView(feedback, matchWrap(18));

        LinearLayout actions = row();
        hintButton = style.action("힌트 확인하기", false, this::showHint);
        actions.addView(hintButton, new LayoutParams(0, style.size(50), 1f));
        primary = style.action("", true, this::onPrimary);
        LayoutParams primaryParams = new LayoutParams(0, style.size(50), 1.4f);
        primaryParams.setMarginStart(style.size(12));
        actions.addView(primary, primaryParams);
        addIcon(actions, glyph("?"), "퀴즈 사용법", this::showHelp);
        addIcon(actions, style.picture(R.drawable.pre_drive_settings), "시뮬레이션 설정", onSettings);
        addIcon(actions, gridIcon(), "퀴즈 닫고 브리핑 화면으로", onClose);
        addView(actions, matchWrap(24));
    }

    /** Called when the quiz object, question or answer changed; drops any unsubmitted selection. */
    void bind(QuizSession quiz, String hintText) {
        this.quiz = quiz;
        this.hint = hintText;
        pending = null;
        note = null;
        refresh();
    }

    private void refresh() {
        int total = quiz.total();
        boolean finished = quiz.isFinished();
        int done = finished ? total : quiz.position() + (quiz.isAnswered() ? 1 : 0);
        intro.setText("오늘 출근길에 배운 내용을 기억하고 있나요?\n" + total + "개의 질문으로 오늘의 경제 지식을 확인해 보세요.");
        progressLabel.setText(done + " / " + total);
        float share = total == 0 ? 0f : (float) done / total;
        ((LayoutParams) progressFill.getLayoutParams()).weight = share;
        ((LayoutParams) progressRest.getLayoutParams()).weight = 1f - share;
        progressFill.requestLayout();
        choices.removeAllViews();

        if (finished) {
            number.setText("");
            question.setText("퀴즈 결과 · " + total + "문제 중 " + quiz.score() + "개 정답");
            showFeedback(quiz.score() == total ? "모두 맞혔어요! 오늘 브리핑을 잘 기억하고 계시네요."
                    : "틀린 문제는 오늘의 발견에서 다시 확인해 보세요.", quiz.score() == total);
            hintButton.setVisibility(INVISIBLE);
            setPrimary("다시 풀기", true);
            return;
        }
        QuizQuestion current = quiz.current();
        number.setText("Q" + (quiz.position() + 1) + ".");
        question.setText(current.getQuestion());
        Integer selected = quiz.selectedChoice();
        List<String> options = current.getChoices();
        int cells = options.size() + options.size() % 2;
        LinearLayout line = null;
        for (int i = 0; i < cells; i++) {
            if (i % 2 == 0) {
                line = row();
                line.setBaselineAligned(false);
                choices.addView(line, matchWrap(14));
            }
            boolean filler = i >= options.size();
            View cell = filler ? new View(getContext())
                    : choiceCard(i, options.get(i), current.getAnswerIndex(), selected);
            // Cards match the row height; the empty D slot must not (a plain View given MATCH_PARENT
            // inside a ScrollView takes the whole remaining height and pushes the buttons off screen).
            LayoutParams params = new LayoutParams(0, filler ? 0 : LayoutParams.MATCH_PARENT, 1f);
            if (i % 2 == 1) params.setMarginStart(style.size(22));
            line.addView(cell, params);
        }

        hintButton.setVisibility(selected == null && hint != null && !hint.isEmpty() ? VISIBLE : INVISIBLE);
        if (selected == null) {
            if (note == null) feedback.setVisibility(GONE);
            else showFeedback(note, true);
            setPrimary("정답 제출하기", pending != null);
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
        card.setPadding(style.size(16), style.size(14), style.size(16), style.size(14));
        card.setMinimumHeight(style.size(66));
        GradientDrawable background = style.shape(isAnswer || isPending ? UiStyle.MINT
                : isWrongPick ? WRONG_FILL : Color.WHITE, 12);
        if (isAnswer || isPending) background.setStroke(style.size(2), UiStyle.GREEN);
        else if (isWrongPick) background.setStroke(style.size(2), WRONG);
        card.setBackground(background);
        card.setElevation(style.size(3));
        if (checked && !isAnswer && !isWrongPick) card.setAlpha(0.6f);

        String mark = isAnswer ? "✓" : isWrongPick ? "✗" : letter(index);
        TextView badge = style.text(mark, 19, isWrongPick ? WRONG : UiStyle.MUTED, false);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(style.shape(isAnswer || isPending ? Color.WHITE : UiStyle.NEUTRAL, 18));
        badge.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(badge, new LayoutParams(style.size(36), style.size(36)));
        TextView text = style.text(label, 17, UiStyle.INK, false);
        text.setLineSpacing(style.size(3), 1f);
        LayoutParams textParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMarginStart(style.size(14));
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
            if (pending != null) onSubmit.accept(pending);
        } else {
            onNext.run();
        }
    }

    private void showHint() {
        if (hint == null || hint.isEmpty()) return;
        note = "힌트 · " + hint;
        showFeedback(note, true);
    }

    private void showHelp() {
        if (quiz == null || quiz.isFinished()) return;
        if (quiz.isAnswered()) return; // keep the explanation visible
        note = "선택지를 하나 고른 뒤 '정답 제출하기'를 누르세요. 막히면 '힌트 확인하기'로 새 용어를 볼 수 있어요.";
        showFeedback(note, true);
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

    private void addIcon(LinearLayout row, View icon, String label, Runnable click) {
        View button = style.iconButton(icon, 26, label, click);
        LayoutParams params = new LayoutParams(style.size(50), style.size(50));
        params.setMarginStart(style.size(12));
        row.addView(button, params);
    }

    private TextView glyph(String value) {
        TextView glyph = style.text(value, 20, UiStyle.MUTED, true);
        glyph.setGravity(Gravity.CENTER);
        glyph.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        return glyph;
    }

    private View gridIcon() {
        android.widget.FrameLayout grid = new android.widget.FrameLayout(getContext());
        grid.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 2; c++) {
                View cell = new View(getContext());
                GradientDrawable outline = style.shape(Color.TRANSPARENT, 1);
                outline.setStroke(Math.max(1, style.size(2)), UiStyle.MUTED);
                cell.setBackground(outline);
                android.widget.FrameLayout.LayoutParams params =
                        new android.widget.FrameLayout.LayoutParams(style.size(10), style.size(10));
                params.topMargin = style.size(2 + r * 13);
                params.leftMargin = style.size(2 + c * 13);
                grid.addView(cell, params);
            }
        }
        return grid;
    }

    private static String letter(int index) {
        return String.valueOf((char) ('A' + index));
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private LayoutParams matchWrap(int top) {
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = style.size(top);
        return params;
    }
}
