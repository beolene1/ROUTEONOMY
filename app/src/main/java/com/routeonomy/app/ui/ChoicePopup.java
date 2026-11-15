package com.routeonomy.app.ui;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Figma pick-one popup (P1 topic choice, P3 today's review): title, green option rows,
 * a primary green button and a neutral one. The card sits low so the greeting above stays visible.
 */
final class ChoicePopup extends FrameLayout {
    interface Handler {
        /** Tap on a row; return false to keep it unselected (e.g. a feature not ready yet). */
        boolean onPick(int index);
        /** Primary button, with the selected row (or -1). */
        void onPrimary(int selected);
        void onSecondary();
    }

    private static final int ROW = 0xff8fb592;
    private static final int ROW_SELECTED = UiStyle.GREEN;

    private final UiStyle style;
    private final TextView title;
    private final LinearLayout rows;
    private final TextView primary;
    private final TextView secondary;
    private Handler handler;
    private int selected = -1;
    private boolean needsSelection;

    ChoicePopup(Context context) {
        super(context);
        style = new UiStyle(context, 1f);
        setBackgroundColor(0x33151815);
        setClickable(true); // swallow taps meant for the screen underneath
        setVisibility(GONE);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(style.size(36), style.size(26), style.size(36), style.size(26));
        card.setBackground(style.shape(0xf7f7f7f2, 18));
        card.setElevation(style.size(10));

        title = style.text("", 30, UiStyle.INK, false);
        title.setGravity(Gravity.CENTER);
        card.addView(title, style.wrapParams());
        rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        card.addView(rows, matchWrap(18));

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.CENTER);
        primary = style.action("", true, this::onPrimary);
        primary.setTextSize(22);
        actions.addView(primary, new LinearLayout.LayoutParams(0, style.size(58), 1.6f));
        secondary = style.action("", false, () -> {
            if (handler != null) handler.onSecondary();
        });
        secondary.setTextSize(22);
        LinearLayout.LayoutParams secondaryParams = new LinearLayout.LayoutParams(0, style.size(58), 1f);
        secondaryParams.setMarginStart(style.size(14));
        actions.addView(secondary, secondaryParams);
        LinearLayout.LayoutParams actionsParams = matchWrap(24);
        actionsParams.setMarginStart(style.size(40));
        actionsParams.setMarginEnd(style.size(40));
        card.addView(actions, actionsParams);

        LayoutParams cardParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        cardParams.leftMargin = style.size(28);
        cardParams.rightMargin = style.size(28);
        cardParams.bottomMargin = style.size(24);
        addView(card, cardParams);
    }

    /**
     * @param options row labels (may carry spans)
     * @param initial preselected row, or -1
     * @param requireSelection primary stays disabled until a row is selected
     */
    void show(String heading, List<CharSequence> options, boolean centered, int initial, boolean requireSelection,
            String primaryLabel, String secondaryLabel, Handler handler) {
        this.handler = handler;
        this.selected = initial;
        this.needsSelection = requireSelection;
        title.setText(heading);
        primary.setText(primaryLabel);
        secondary.setText(secondaryLabel);
        rows.removeAllViews();
        for (int i = 0; i < options.size(); i++) {
            final int index = i;
            TextView row = style.text("", 20, 0xffffffff, centered);
            row.setText(options.get(i));
            row.setGravity(centered ? Gravity.CENTER : Gravity.CENTER_VERTICAL | Gravity.START);
            row.setPadding(style.size(24), style.size(12), style.size(24), style.size(12));
            row.setMinHeight(style.size(58));
            style.clickable(row, () -> {
                if (this.handler != null && this.handler.onPick(index)) {
                    selected = index;
                    refreshRows();
                }
            });
            rows.addView(row, matchWrap(i == 0 ? 0 : 12));
        }
        refreshRows();
        setVisibility(VISIBLE);
        announceForAccessibility(heading);
    }

    private void refreshRows() {
        for (int i = 0; i < rows.getChildCount(); i++) {
            TextView row = (TextView) rows.getChildAt(i);
            boolean on = i == selected;
            row.setBackground(style.shape(on ? ROW_SELECTED : ROW, 12));
            row.setElevation(style.size(on ? 6 : 2));
            row.setSelected(on);
        }
        boolean enabled = !needsSelection || selected >= 0;
        primary.setEnabled(enabled);
        primary.setAlpha(enabled ? 1f : 0.5f);
    }

    private void onPrimary() {
        if (handler != null && (!needsSelection || selected >= 0)) handler.onPrimary(selected);
    }

    void hide() {
        setVisibility(GONE);
        handler = null;
    }

    boolean isShowing() {
        return getVisibility() == VISIBLE;
    }

    private LinearLayout.LayoutParams matchWrap(int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = style.size(top);
        return params;
    }
}
