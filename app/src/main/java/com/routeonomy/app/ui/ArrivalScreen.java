package com.routeonomy.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.routeonomy.app.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Figma S3 "도착 정산" (arrived and in P): greeting, today's keywords and study record,
 * XP earned this drive on the growth bar, then Review lesson · Today's Economic Quiz · tools · details.
 */
final class ArrivalScreen extends LinearLayout {
    /** Everything the screen shows; built by the activity from the briefing and PlayerProgress. */
    static final class Summary {
        String stageName;
        List<String> keywords = new ArrayList<>();
        int studyMinutes;
        int heardCount;
        int earnedThisRun;
        /** e.g. "다음 단계 '줄기'까지 20XP 남았어요!" */
        String growthLine;
        /** 0..1 progress inside the current stage. */
        float stageShare;
        /** XP the quiz can still give, 0 when there is no quiz. */
        int quizXp;
        boolean quizCanLevelUp;
        boolean hasQuiz;
    }

    private static final int[][] CHIP_COLORS = {
            {0xffbfe9c4, 0xff9fdcae}, {0xffc9ece0, 0xfff3d6cc}, {0xfff6d2c9, 0xffd7edc8}, {0xffa8e2b5, 0xffd3f0d8}};
    private static final int RECORD = 0xff5aa67a;
    private static final int XP_TEXT = 0xff6fae8b;

    private final UiStyle style;
    private final TextView greeting;
    private final LinearLayout chips;
    private final TextView record;
    private final TextView growthLine;
    private final TextView earned;
    private final View growthFill;
    private final View growthRest;
    private final TextView quizNote;
    private final TextView quizButton;
    private String boundKeywords;

    ArrivalScreen(Context context, Runnable onReview, Runnable onQuiz, Runnable onSettings, Runnable onDetails) {
        super(context);
        style = new UiStyle(context, 1f);
        setOrientation(VERTICAL);
        setClipChildren(false);

        addView(style.appHeader(false), matchWrap(0));

        LinearLayout hero = row();
        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(VERTICAL);
        greeting = style.text("", 40, UiStyle.INK, true);
        greeting.setLineSpacing(style.size(2), 1f);
        copy.addView(greeting);
        copy.addView(style.text("오늘도 이동하는 시간을 성장의 시간으로 바꿨어요!", 19, UiStyle.MUTED, false), matchWrap(10));
        hero.addView(copy, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        hero.addView(style.picture(R.drawable.arrival_mascot), new LayoutParams(style.size(200), style.size(172)));
        addView(hero, matchWrap(18));

        addView(heading("TODAY’S LEARNING ACHIEVEMENT"), matchWrap(14));
        addView(caption("오늘의 발견 키워드"), matchWrap(6));
        HorizontalScrollView chipScroll = new HorizontalScrollView(context);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setClipToPadding(false);
        chips = row();
        chips.setPadding(0, style.size(4), 0, style.size(6));
        chipScroll.addView(chips);
        addView(chipScroll, matchWrap(8));
        addView(caption("오늘의 학습 기록"), matchWrap(12));
        record = style.text("", 26, RECORD, true);
        addView(record, matchWrap(6));

        addView(heading("TODAY’S GROWTH"), matchWrap(22));
        growthLine = caption("");
        addView(growthLine, matchWrap(6));
        addView(growthPanel(context), new LayoutParams(LayoutParams.MATCH_PARENT, style.size(150)));
        earned = (TextView) findViewWithTag("earned");
        growthFill = findViewWithTag("fill");
        growthRest = findViewWithTag("rest");

        addView(new View(context), new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        quizNote = style.text("", 16, UiStyle.GREEN, true);
        quizNote.setGravity(Gravity.CENTER);
        LayoutParams noteParams = matchWrap(12);
        // Roughly centred over the quiz button (second of the bottom actions).
        noteParams.setMarginStart(style.size(160));
        noteParams.setMarginEnd(style.size(130));
        addView(quizNote, noteParams);
        LinearLayout actions = row();
        TextView review = style.action("Review lesson", false, onReview);
        review.setTextSize(21);
        actions.addView(review, new LayoutParams(0, style.size(56), 1f));
        quizButton = style.action("Today’s Economic Quiz", true, onQuiz);
        quizButton.setTextSize(21);
        LayoutParams quizParams = new LayoutParams(0, style.size(56), 1.75f);
        quizParams.setMarginStart(style.size(12));
        actions.addView(quizButton, quizParams);
        addIcon(actions, style.picture(R.drawable.pre_drive_settings), "시뮬레이션 설정", onSettings);
        addIcon(actions, gridIcon(), "브리핑 화면과 오늘의 발견 보기", onDetails);
        addView(actions, matchWrap(6));
    }

    void bind(Summary s) {
        String text = "도착했어요,\n" + s.stageName + "님 수고하셨어요!";
        if (!text.contentEquals(greeting.getText())) greeting.setText(text);
        String keys = String.join("|", s.keywords);
        if (!keys.equals(boundKeywords)) {
            boundKeywords = keys;
            chips.removeAllViews();
            if (s.keywords.isEmpty()) chips.addView(chip("#아직 끝까지 들은 이야기가 없어요", 0), style.wrapParams());
            for (int i = 0; i < s.keywords.size(); i++) {
                LayoutParams params = style.wrapParams();
                if (i > 0) params.setMarginStart(style.size(12));
                chips.addView(chip("#" + s.keywords.get(i), i), params);
            }
        }
        record.setText("학습 시간 " + s.studyMinutes + "분 · 끝까지 들은 이야기 " + s.heardCount + "개");
        growthLine.setText(s.growthLine);
        earned.setText("+" + s.earnedThisRun + "XP");
        earned.setContentDescription("이번 주행 " + s.earnedThisRun + " XP 획득");
        float share = Math.max(0f, Math.min(1f, s.stageShare));
        ((LayoutParams) growthFill.getLayoutParams()).weight = share;
        ((LayoutParams) growthRest.getLayoutParams()).weight = 1f - share;
        growthFill.setVisibility(share > 0f ? VISIBLE : INVISIBLE);
        growthFill.requestLayout();
        quizNote.setText(!s.hasQuiz ? "끝까지 들은 이야기가 있어야 퀴즈를 풀 수 있어요"
                : s.quizXp == 0 ? "오늘 퀴즈를 다시 풀어 볼 수 있어요"
                : "+" + s.quizXp + " XP" + (s.quizCanLevelUp ? " · 레벨업 가능!" : " 획득 가능!"));
        quizButton.setEnabled(s.hasQuiz);
        quizButton.setAlpha(s.hasQuiz ? 1f : 0.45f);
    }

    /** Current-stage mascot, "+N XP", the stage progress bar and the next-stage mascot. */
    private FrameLayout growthPanel(Context context) {
        FrameLayout panel = new FrameLayout(context);
        panel.setClipChildren(false);

        LinearLayout track = new LinearLayout(context);
        track.setBackground(style.shape(0xff98aa9f, 22));
        track.setClipToOutline(true);
        View fill = new View(context);
        fill.setTag("fill");
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {0xff9be3a6, 0xffd7f3e4});
        gradient.setCornerRadius(style.size(22));
        fill.setBackground(gradient);
        View rest = new View(context);
        rest.setTag("rest");
        track.addView(fill, new LayoutParams(0, LayoutParams.MATCH_PARENT, 0f));
        track.addView(rest, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        FrameLayout.LayoutParams trackParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                style.size(44), Gravity.BOTTOM);
        trackParams.leftMargin = style.size(70);
        trackParams.rightMargin = style.size(70);
        trackParams.bottomMargin = style.size(16);
        panel.addView(track, trackParams);

        TextView xp = style.text("", 64, XP_TEXT, true);
        xp.setTag("earned");
        xp.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams xpParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        xpParams.topMargin = style.size(4);
        panel.addView(xp, xpParams);

        panel.addView(style.picture(R.drawable.growth_now), new FrameLayout.LayoutParams(style.size(84), style.size(110),
                Gravity.BOTTOM | Gravity.START));
        panel.addView(style.picture(R.drawable.growth_next), new FrameLayout.LayoutParams(style.size(92), style.size(130),
                Gravity.BOTTOM | Gravity.END));
        return panel;
    }

    private TextView chip(String label, int index) {
        TextView chip = style.text(label, 22, UiStyle.INK, false);
        chip.setPadding(style.size(20), style.size(10), style.size(20), style.size(10));
        int[] colors = CHIP_COLORS[index % CHIP_COLORS.length];
        GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        fill.setCornerRadius(style.size(26));
        chip.setBackground(fill);
        return chip;
    }

    private TextView heading(String value) {
        return style.text(value, 22, UiStyle.INK, true);
    }

    private TextView caption(String value) {
        return style.text(value, 17, UiStyle.MUTED, false);
    }

    private void addIcon(LinearLayout row, View icon, String label, Runnable click) {
        View button = style.iconButton(icon, 28, label, click);
        LayoutParams params = new LayoutParams(style.size(56), style.size(56));
        params.setMarginStart(style.size(10));
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
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(style.size(11), style.size(11));
                params.topMargin = style.size(2 + r * 14);
                params.leftMargin = style.size(2 + c * 14);
                grid.addView(cell, params);
            }
        }
        return grid;
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
