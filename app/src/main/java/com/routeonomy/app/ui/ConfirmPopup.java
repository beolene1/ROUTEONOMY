package com.routeonomy.app.ui;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Two-button confirmation over the app column (e.g. "go home stops the audio"). */
final class ConfirmPopup extends FrameLayout {
    private Runnable onConfirm = () -> { };
    private final TextView body;

    ConfirmPopup(Context context, String title, String message, String confirmLabel) {
        super(context);
        UiStyle style = new UiStyle(context, 1f);
        setBackgroundColor(0x66151815);
        setClickable(true); // swallow taps meant for the screen underneath
        setVisibility(GONE);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(style.size(32), style.size(28), style.size(32), style.size(28));
        card.setBackground(style.shape(UiStyle.SURFACE, 24));
        card.setElevation(style.size(8));

        card.addView(style.text(title, 26, UiStyle.INK, true), style.wrapParams());
        body = style.text(message, 19, UiStyle.MUTED, false);
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(style.size(4), 1f);
        LinearLayout.LayoutParams bodyParams = style.wrapParams();
        bodyParams.topMargin = style.size(12);
        card.addView(body, bodyParams);

        LinearLayout actions = new LinearLayout(context);
        actions.addView(style.action("취소", false, this::hide), new LinearLayout.LayoutParams(0, style.size(52), 1f));
        TextView confirm = style.action(confirmLabel, true, () -> {
            hide();
            onConfirm.run();
        });
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(0, style.size(52), 1f);
        confirmParams.setMarginStart(style.size(12));
        actions.addView(confirm, confirmParams);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = style.size(24);
        card.addView(actions, actionParams);

        addView(card, new LayoutParams(style.size(480), LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }

    void show(Runnable confirmAction) {
        onConfirm = confirmAction;
        setVisibility(VISIBLE);
        announceForAccessibility(body.getText());
    }

    void hide() {
        setVisibility(GONE);
    }

    boolean isShowing() {
        return getVisibility() == VISIBLE;
    }
}
