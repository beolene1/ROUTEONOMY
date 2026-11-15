package com.routeonomy.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.routeonomy.app.R;
import com.routeonomy.app.domain.PointRules;
import com.routeonomy.app.domain.QuizQuestion;
import com.routeonomy.app.domain.QuizSession;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Figma "Today's Economic Quiz": app header, title and intro, answered/total bar with the XP on offer,
 * "Q n." card with a red pen grading mark once answered, one-column choices, hint / result note and
 * the Need a hint? (Learn why) · Check answer (Next question) · tools · close row.
 * The pending (not yet submitted) choice lives only here; grading stays in [QuizSession].
 */
final class QuizScreen extends LinearLayout {
    /** XP shown on the quiz; implemented by the activity over PlayerProgress. */
    interface Rewards {
        /** Pays for the current question's hint (once); false when there is not enough XP. */
        boolean payHint();
        boolean canShowHint();
        boolean isHintPaid();
        /** XP given for the answer to the current question (0 on "다시 풀기"). */
        int answerPoints();
        /** Result screen line, e.g. "이번 퀴즈 +7 XP · 🌱 새싹 Lv.2". */
        String resultLine();
    }

    private static final int PICKED_FILL = 0xffcdf7cf;
    private static final int PICKED_BADGE = 0xff9bd8a8;
    private static final int WRONG_FILL = 0xffffcfcf;
    private static final int WRONG_BADGE = 0xffe58b8b;
    private static final int WRONG = 0xffd23f31;
    private static final int HINT_FILL = 0xffeef8cf;
    private static final int PEN = 0xffe0484a;

    private final UiStyle style;
    private final IntConsumer onSubmit;
    private final Runnable onNext;
    private final Runnable onReview;
    private final TextView title;
    private final TextView intro;
    private final TextView progressLabel;
    private final TextView xpLabel;
    private final View progressFill;
    private final View progressRest;
    private final TextView badge;
    private final TextView number;
    private final TextView question;
    private final GradeMark mark;
    private final LinearLayout choices;
    private final TextView feedback;
    private final TextView leftButton;
    private final TextView primary;
    private QuizSession quiz;
    private Rewards rewards;
    private String hint;
    private Integer pending;
    private boolean hintShown;
    private boolean whyShown;

    QuizScreen(Context context, Runnable onClose, Runnable onSettings, IntConsumer onSubmit, Runnable onNext,
            Runnable onReview) {
        super(context);
        this.onSubmit = onSubmit;
        this.onNext = onNext;
        this.onReview = onReview;
        style = new UiStyle(context, 1f);
        setOrientation(VERTICAL);
        setClipChildren(false);

        addView(style.appHeader(false), matchWrap(0));
        // Sizes are Figma dp (FigmaFrame scales the column).
        title = style.text("Today’s Economic Quiz", 54, UiStyle.INK, true);
        title.setGravity(Gravity.CENTER);
        addView(title, matchWrap(64));
        intro = style.text("", 19, UiStyle.INK, false);
        intro.setGravity(Gravity.CENTER);
        intro.setLineSpacing(style.size(8), 1f);
        addView(intro, matchWrap(30));

        LinearLayout progress = row();
        progressLabel = style.text("", 19, UiStyle.MUTED, false);
        progress.addView(progressLabel, style.wrapParams());
        LinearLayout track = new LinearLayout(context);
        track.setBackground(style.shape(UiStyle.NEUTRAL, 8));
        track.setClipToOutline(true);
        progressFill = new View(context);
        progressFill.setBackground(style.shape(UiStyle.SAGE, 8));
        progressRest = new View(context);
        track.addView(progressFill, new LayoutParams(0, LayoutParams.MATCH_PARENT, 0f));
        track.addView(progressRest, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        LayoutParams trackParams = new LayoutParams(0, style.size(19), 1f);
        trackParams.setMarginStart(style.size(12));
        trackParams.setMarginEnd(style.size(10));
        progress.addView(track, trackParams);
        xpLabel = style.text("", 19, UiStyle.MUTED, false);
        progress.addView(xpLabel, style.wrapParams());
        addView(progress, matchWrap(20));

        // Question card; the grading mark is drawn over its left end like a red pen.
        FrameLayout cardFrame = new FrameLayout(context);
        cardFrame.setClipChildren(false);
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(VERTICAL);
        card.setPadding(style.size(24), style.size(12), style.size(24), style.size(20));
        card.setMinimumHeight(style.size(100));
        style.card(card);
        badge = style.pill("Today’s learning", UiStyle.GREEN, UiStyle.MINT, 15);
        badge.setPadding(style.size(8), style.size(2), style.size(8), style.size(2));
        LayoutParams badgeParams = style.wrapParams();
        badgeParams.setMarginStart(style.size(46));
        card.addView(badge, badgeParams);
        LinearLayout questionRow = row();
        number = style.text("", 28, UiStyle.SAGE, true);
        questionRow.addView(number, style.wrapParams());
        question = style.text("", 22, UiStyle.INK, true);
        question.setLineSpacing(style.size(3), 1f);
        LayoutParams questionParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        questionParams.setMarginStart(style.size(10));
        questionRow.addView(question, questionParams);
        card.addView(questionRow, matchWrap(4));
        cardFrame.addView(card, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        mark = new GradeMark(context);
        FrameLayout.LayoutParams markParams = new FrameLayout.LayoutParams(style.size(190), style.size(200),
                Gravity.START | Gravity.CENTER_VERTICAL);
        markParams.leftMargin = -style.size(44);
        cardFrame.addView(mark, markParams);
        addView(cardFrame, matchWrap(12));

        choices = new LinearLayout(context);
        choices.setOrientation(VERTICAL);
        LayoutParams choicesParams = matchWrap(-18); // the mark frame is taller than the card
        choicesParams.setMarginStart(style.size(49));
        choicesParams.setMarginEnd(style.size(29));
        addView(choices, choicesParams);

        feedback = style.text("", 19, UiStyle.INK, false);
        feedback.setLineSpacing(style.size(5), 1f);
        feedback.setPadding(style.size(22), style.size(14), style.size(22), style.size(14));
        feedback.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        LayoutParams feedbackParams = matchWrap(28);
        feedbackParams.setMarginStart(style.size(26));
        feedbackParams.setMarginEnd(style.size(29));
        addView(feedback, feedbackParams);

        // Pushes the buttons to the bottom when the screen is taller than the content.
        addView(new View(context), new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout actions = row();
        leftButton = style.action("Need a hint?", false, this::onLeft);
        leftButton.setTextSize(27);
        leftButton.setBackground(style.shape(UiStyle.NEUTRAL, 36));
        actions.addView(leftButton, new LayoutParams(0, style.size(72), 1f));
        primary = style.action("", true, this::onPrimary);
        primary.setTextSize(27);
        primary.setBackground(style.shape(UiStyle.GREEN, 36));
        LayoutParams primaryParams = new LayoutParams(0, style.size(72), 1.77f);
        primaryParams.setMarginStart(style.size(10));
        actions.addView(primary, primaryParams);
        addIcon(actions, style.picture(R.drawable.pre_drive_settings), "시뮬레이션 설정", onSettings);
        addIcon(actions, gridIcon(), "퀴즈 닫기", onClose);
        addView(actions, matchWrap(20));
    }

    /** Called when the quiz object, question, answer or XP changed; a new question drops the unsubmitted selection. */
    void bind(QuizSession quiz, String hintText, Rewards rewards) {
        boolean sameQuestion = quiz == this.quiz && this.quiz != null && quiz.position() == shownPosition;
        this.quiz = quiz;
        this.rewards = rewards;
        this.hint = hintText;
        // A rebind for the same unanswered question (e.g. XP changed after a hint) keeps the picked choice.
        if (!sameQuestion || quiz.isAnswered()) pending = null;
        if (!sameQuestion) hintShown = false;
        whyShown = quiz.isAnswered() && quiz.current() != null
                && quiz.selectedChoice() != quiz.current().getAnswerIndex(); // a wrong answer explains itself
        shownPosition = quiz.position();
        refresh();
    }

    private int shownPosition = -1;

    private void refresh() {
        int total = quiz.total();
        boolean finished = quiz.isFinished();
        int done = finished ? total : quiz.position() + (quiz.isAnswered() ? 1 : 0);
        intro.setText("오늘 출근길에 배운 내용을 기억하고 있나요?\n" + total + "개의 질문으로 오늘의 경제 지식을 확인해 보세요.");
        progressLabel.setText(done + " / " + total);
        xpLabel.setText("+ " + total * PointRules.QUIZ_CORRECT + "XP");
        xpLabel.setContentDescription("모두 맞히면 " + total * PointRules.QUIZ_CORRECT + " XP");
        float share = total == 0 ? 0f : (float) done / total;
        ((LayoutParams) progressFill.getLayoutParams()).weight = share;
        ((LayoutParams) progressRest.getLayoutParams()).weight = 1f - share;
        progressFill.requestLayout();
        choices.removeAllViews();

        if (finished) {
            badge.setVisibility(VISIBLE);
            number.setText("");
            question.setText("퀴즈 결과 · " + total + "문제 중 " + quiz.score() + "개 정답");
            mark.show(quiz.score() == total ? GradeMark.CORRECT : GradeMark.NONE);
            boolean perfect = quiz.score() == total;
            showNote(perfect ? "● 모두 맞혔어요!" : "● 수고했어요!", UiStyle.GREEN,
                    (perfect ? "오늘 브리핑을 잘 기억하고 계시네요." : "틀린 문제는 오늘의 발견에서 다시 확인해 보세요.")
                            + "\n" + rewards.resultLine(), UiStyle.MINT);
            setLeft("오늘의 정리", UiStyle.MUTED, true);
            setPrimary("다시 풀기", true);
            return;
        }
        QuizQuestion current = quiz.current();
        badge.setVisibility(quiz.position() == 0 ? VISIBLE : GONE);
        number.setText("Q" + (quiz.position() + 1) + ".");
        question.setText(current.getQuestion());
        Integer selected = quiz.selectedChoice();
        List<String> options = current.getChoices();
        for (int i = 0; i < options.size(); i++) {
            choices.addView(choiceCard(i, options.get(i), current.getAnswerIndex(), selected), matchWrap(i == 0 ? 0 : 26));
        }

        if (selected == null) {
            mark.show(GradeMark.NONE);
            boolean paid = rewards.isHintPaid();
            boolean hintAvailable = hint != null && !hint.isEmpty() && rewards.canShowHint();
            setLeft(hintAvailable ? "Need a hint?" : "Hint · XP 부족", UiStyle.GREEN, hintAvailable);
            leftButton.setContentDescription(paid ? "힌트 다시 보기" : hintAvailable ? "힌트 보기, 1 XP 사용" : "XP가 부족해 힌트를 볼 수 없어요");
            if (hintShown && paid) showNote("💡 Hint", UiStyle.INK, hint, HINT_FILL);
            else feedback.setVisibility(GONE);
            setPrimary("Check answer", pending != null);
        } else {
            boolean correct = selected == current.getAnswerIndex();
            mark.show(correct ? GradeMark.CORRECT : GradeMark.WRONG);
            int points = rewards.answerPoints();
            String earned = points > 0 ? "   +" + points + " XP" : "";
            if (correct) {
                showNote("● 정답이에요!" + earned, UiStyle.GREEN, whyShown ? current.getExplanation() : null,
                        whyShown ? UiStyle.MINT : Color.TRANSPARENT);
            } else {
                showNote("▲ Almost there !" + earned, WRONG, "정답은 " + letter(current.getAnswerIndex()) + "예요. "
                        + current.getExplanation(), Color.TRANSPARENT);
                if (!whyShown) feedback.setVisibility(GONE);
            }
            setLeft("Learn why", correct ? UiStyle.MUTED : WRONG, true);
            leftButton.setContentDescription(whyShown ? "해설 숨기기" : "해설 보기");
            setPrimary(quiz.position() + 1 < total ? "Next question" : "결과 보기", true);
        }
    }

    private View choiceCard(int index, String label, int answer, Integer selected) {
        boolean checked = selected != null;
        boolean isAnswer = checked && index == answer;
        boolean isWrongPick = checked && index == selected && index != answer;
        boolean isPending = !checked && pending != null && pending == index;
        boolean green = isAnswer || isPending;

        LinearLayout card = row();
        card.setPadding(style.size(18), style.size(12), style.size(18), style.size(12));
        card.setMinimumHeight(style.size(84));
        card.setBackground(style.shape(green ? PICKED_FILL : isWrongPick ? WRONG_FILL : Color.WHITE, 12));
        card.setElevation(style.size(green || isWrongPick ? 6 : 3));
        if (checked && !isAnswer && !isWrongPick) card.setAlpha(0.7f);

        TextView letterBadge = style.text(letter(index), 23, isWrongPick ? Color.WHITE : green ? UiStyle.GREEN : UiStyle.MUTED, false);
        letterBadge.setGravity(Gravity.CENTER);
        letterBadge.setBackground(style.shape(green ? PICKED_BADGE : isWrongPick ? WRONG_BADGE : UiStyle.NEUTRAL, 24));
        letterBadge.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(letterBadge, new LayoutParams(style.size(48), style.size(48)));
        TextView text = style.text(label, 20, UiStyle.INK, false);
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

    /** Need a hint? (pays once, then shows it again for free) · Learn why (toggles the explanation) · 오늘의 정리. */
    private void onLeft() {
        if (quiz == null) return;
        if (quiz.isFinished()) {
            onReview.run();
            return;
        }
        if (quiz.isAnswered()) {
            whyShown = !whyShown;
            refresh();
            return;
        }
        if (hint == null || hint.isEmpty()) return;
        if (!rewards.isHintPaid() && !rewards.payHint()) return;
        hintShown = true;
        refresh(); // shows the hint; the activity rebinds after the XP change and keeps it shown
    }

    /** Bold coloured heading line and an optional body, on an optional tinted box. */
    private void showNote(String heading, int headingColor, String body, int fill) {
        SpannableStringBuilder text = new SpannableStringBuilder(heading);
        text.setSpan(new StyleSpan(Typeface.BOLD), 0, heading.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(headingColor), 0, heading.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (body != null && !body.isEmpty()) text.append("\n").append(body);
        feedback.setText(text);
        feedback.setBackground(fill == Color.TRANSPARENT ? null : style.shape(fill, 12));
        feedback.setVisibility(VISIBLE);
    }

    private void setLeft(String label, int color, boolean enabled) {
        leftButton.setText(label);
        leftButton.setTextColor(color);
        leftButton.setEnabled(enabled);
        leftButton.setAlpha(enabled ? 1f : 0.5f);
        leftButton.setContentDescription(null);
    }

    private void setPrimary(String label, boolean enabled) {
        primary.setText(label);
        primary.setEnabled(enabled);
        primary.setAlpha(enabled ? 1f : 0.45f);
    }

    private void addIcon(LinearLayout row, View icon, String label, Runnable click) {
        View button = style.iconButton(icon, 36, label, click);
        button.setBackground(style.shape(UiStyle.NEUTRAL, 36));
        LayoutParams params = new LayoutParams(style.size(72), style.size(72));
        params.setMarginStart(style.size(7));
        row.addView(button, params);
    }

    private View gridIcon() {
        FrameLayout grid = new FrameLayout(getContext());
        grid.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 2; c++) {
                View cell = new View(getContext());
                GradientDrawable outline = style.shape(Color.TRANSPARENT, 1);
                outline.setStroke(Math.max(1, style.size(2)), UiStyle.MUTED);
                cell.setBackground(outline);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(style.size(14), style.size(14));
                params.topMargin = style.size(2 + r * 18);
                params.leftMargin = style.size(2 + c * 18);
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

    /** Hand-drawn red pen mark: a loose circle for a right answer, a scribbled star for a wrong one. */
    private static final class GradeMark extends View {
        static final int NONE = 0, CORRECT = 1, WRONG = 2;
        private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF oval = new RectF();
        private final RectF tail = new RectF();
        private int kind = NONE;

        GradeMark(Context context) {
            super(context);
            pen.setStyle(Paint.Style.STROKE);
            pen.setStrokeCap(Paint.Cap.ROUND);
            pen.setStrokeJoin(Paint.Join.ROUND);
            pen.setColor(PEN);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            setVisibility(INVISIBLE); // keeps the card frame height, so the choices do not jump on answer
        }

        void show(int kind) {
            if (this.kind == kind) return;
            this.kind = kind;
            setVisibility(kind == NONE ? INVISIBLE : VISIBLE);
            if (kind != NONE) {
                setScaleX(0.6f);
                setScaleY(0.6f);
                setAlpha(0f);
                animate().scaleX(1f).scaleY(1f).alpha(0.9f).setDuration(220).start();
            }
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            pen.setStrokeWidth(Math.max(4f, w / 26f));
            path.reset();
            if (kind == CORRECT) {
                // A bit more than a full turn, slightly tilted, so the ends overlap like a pen stroke.
                canvas.save();
                canvas.rotate(-18f, w / 2f, h / 2f);
                oval.set(w * 0.12f, h * 0.2f, w * 0.88f, h * 0.8f);
                tail.set(oval.left + w * 0.02f, oval.top - h * 0.03f, oval.right + w * 0.02f, oval.bottom - h * 0.03f);
                path.addArc(oval, 200f, 345f);
                path.arcTo(tail, 185f, 40f, false);
                canvas.drawPath(path, pen);
                canvas.restore();
            } else if (kind == WRONG) {
                // Five-point star drawn in one stroke.
                double cx = w / 2.0, cy = h / 2.0, r = Math.min(w, h) * 0.42;
                for (int i = 0; i <= 5; i++) {
                    double angle = Math.toRadians(-90 + i * 144);
                    float x = (float) (cx + r * Math.cos(angle)), y = (float) (cy + r * Math.sin(angle));
                    if (i == 0) path.moveTo(x, y);
                    else path.lineTo(x, y);
                }
                canvas.drawPath(path, pen);
            }
        }
    }
}
