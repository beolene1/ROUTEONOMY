package com.routeonomy.app.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.routeonomy.app.R;

/**
 * Shown once when arrival and P are both confirmed: praise, how many briefings were heard,
 * and "퀴즈 풀러 가기" / "취소". Covers the app column only; the route panel stays visible.
 */
final class ArrivalPopup extends FrameLayout {
    private final UiStyle style;
    private final TextView message;
    private final TextView quizButton;
    private final TextView cancelButton;

    ArrivalPopup(Context context, Runnable onQuiz, Runnable onCancel) {
        super(context);
        style = new UiStyle(context, 1f);
        setBackgroundColor(0x66151815);
        setClickable(true); // swallow taps meant for the screen underneath
        setVisibility(GONE);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(style.size(32), style.size(28), style.size(32), style.size(28));
        card.setBackground(style.shape(UiStyle.SURFACE, 24));
        card.setElevation(style.size(8));

        card.addView(style.picture(R.drawable.pre_drive_mascot),
                new LinearLayout.LayoutParams(style.size(150), style.size(165)));
        TextView title = style.text("참 잘했어요!", 32, UiStyle.INK, true);
        card.addView(title, topMargin(style.wrapParams(), 14));
        message = style.text("", 19, UiStyle.MUTED, false);
        message.setGravity(Gravity.CENTER);
        message.setLineSpacing(style.size(4), 1f);
        card.addView(message, topMargin(style.wrapParams(), 10));

        LinearLayout actions = new LinearLayout(context);
        cancelButton = style.action("취소", false, onCancel);
        actions.addView(cancelButton, new LinearLayout.LayoutParams(0, style.size(52), 1f));
        quizButton = style.action("퀴즈 풀러 가기", true, onQuiz);
        LinearLayout.LayoutParams quizParams = new LinearLayout.LayoutParams(0, style.size(52), 1.6f);
        quizParams.setMarginStart(style.size(12));
        actions.addView(quizButton, quizParams);
        card.addView(actions, topMargin(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT), 24));

        LayoutParams cardParams = new LayoutParams(style.size(480), LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        addView(card, cardParams);
    }

    /**
     * @param hasQuiz false when no briefing was heard to the end: only a close button is offered.
     * @param pointsLine points earned this drive and the growth stage
     */
    void show(int heardCount, boolean hasQuiz, String pointsLine) {
        message.setText((heardCount > 0
                ? "목적지에 도착했어요.\n오늘 " + heardCount + "개의 이야기를 끝까지 들었어요."
                        + (hasQuiz ? "\n들은 내용으로 퀴즈를 풀어 볼까요?" : "")
                : "목적지에 도착했어요.\n끝까지 들은 이야기가 없어 퀴즈는 다음에 풀어요.")
                + "\n\n" + pointsLine);
        quizButton.setVisibility(hasQuiz ? VISIBLE : GONE);
        cancelButton.setText(hasQuiz ? "취소" : "확인");
        setVisibility(VISIBLE);
        announceForAccessibility("참 잘했어요! " + message.getText());
    }

    void hide() {
        setVisibility(GONE);
    }

    boolean isShowing() {
        return getVisibility() == VISIBLE;
    }

    private LinearLayout.LayoutParams topMargin(LinearLayout.LayoutParams params, int dp) {
        params.topMargin = style.size(dp);
        return params;
    }
}
