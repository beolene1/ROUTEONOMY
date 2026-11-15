package com.routeonomy.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.routeonomy.app.R;
import com.routeonomy.app.domain.BriefingSegment;

/**
 * Figma "Now Playing" screen (P and D): app header, topic and hook question, topic picture,
 * study time / remaining drive time over a trip progress bar, audio · ⏮ play/pause ⏭ · level row,
 * a picture strip under the card and the home / next topic / tools / details row.
 * Behaviour stays in MainActivity.
 */
final class NowPlayingCard extends LinearLayout {
    private final UiStyle style;
    private final TextView title;
    private final ImageView picture;
    private final LinearLayout strip;
    private final View bottomSettings;
    private final View detailsButton;
    private int[] boundStrip;
    private int boundPicture;
    private final TextView subtitle;
    private final TextView studyTime;
    private final TextView remaining;
    private final View progressFill;
    private final View progressRest;
    private final TextView playbackStatus;
    private final TextView playButton;
    private final LinearLayout transport;
    private final TextView homeAction;
    private final TextView nextAction;
    private boolean reviewOnly;
    private final View settingsButton;
    private final TextView levelLabel;
    private final TextView levelDetail;

    NowPlayingCard(Context context, Runnable onBack, Runnable onPlayPause, Runnable onPrevious, Runnable onNext,
            Runnable onSettings, Runnable onDetails) {
        super(context);
        style = new UiStyle(context, 1f);
        setOrientation(VERTICAL);

        addView(style.appHeader(false), matchWrap(0));

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        // Sizes are Figma dp (FigmaFrame scales the column).
        card.setPadding(style.size(32), style.size(14), style.size(32), style.size(40));
        card.setBackground(style.shape(UiStyle.SURFACE, 22));
        card.setElevation(style.size(4));

        card.addView(style.text("•  Now Playing  •", 21, UiStyle.LIVE, false), style.wrapParams());
        title = style.text("", 36, UiStyle.INK, true);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(title, centered(14));
        subtitle = style.text("", 23, UiStyle.MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setMaxLines(1);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(subtitle, centered(10));

        picture = style.picture(R.drawable.np_chip3d);
        LayoutParams pictureParams = new LayoutParams(style.size(376), style.size(298));
        pictureParams.topMargin = style.size(18);
        card.addView(picture, pictureParams);

        LinearLayout labels = row();
        studyTime = style.text("", 20, UiStyle.MUTED, false);
        labels.addView(studyTime, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        remaining = style.text("", 20, UiStyle.INK, false);
        remaining.setGravity(Gravity.END);
        labels.addView(remaining, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        card.addView(labels, matchWrap(16));

        // Trip progress: thin track with a round knob at the current point.
        LinearLayout track = row();
        progressFill = new View(context);
        progressFill.setBackground(style.shape(UiStyle.SAGE, 3));
        track.addView(progressFill, new LayoutParams(0, style.size(5), 0f));
        View knob = new View(context);
        knob.setBackground(style.shape(UiStyle.SAGE, 11));
        track.addView(knob, new LayoutParams(style.size(18), style.size(18)));
        progressRest = new View(context);
        progressRest.setBackground(style.shape(UiStyle.NEUTRAL, 3));
        track.addView(progressRest, new LayoutParams(0, style.size(5), 1f));
        card.addView(track, matchWrap(10));

        LinearLayout controls = row();
        LinearLayout audio = row();
        audio.addView(waveIcon(), new LayoutParams(style.size(36), style.size(34)));
        LinearLayout audioText = new LinearLayout(context);
        audioText.setOrientation(VERTICAL);
        audioText.addView(style.text("Audio\nguidance", 19, UiStyle.INK, false));
        playbackStatus = style.text("브리핑 대기", 13, UiStyle.MUTED, false);
        playbackStatus.setMaxLines(2);
        playbackStatus.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        audioText.addView(playbackStatus, matchWrap(4));
        LayoutParams audioTextParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        audioTextParams.setMarginStart(style.size(12));
        audio.addView(audioText, audioTextParams);
        controls.addView(audio, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        playButton = style.text("", 34, Color.WHITE, true);
        playButton.setGravity(Gravity.CENTER);
        playButton.setBackground(style.shape(UiStyle.PLAYER, 48));
        playButton.setElevation(style.size(6));
        style.clickable(playButton, onPlayPause);
        // Previous · play/pause · next, usable in P and D.
        transport = row();
        transport.addView(skipButton("⏮\uFE0E", "이전 주제", onPrevious), new LayoutParams(style.size(56), style.size(56)));
        LayoutParams playParams = new LayoutParams(style.size(88), style.size(88));
        playParams.setMarginStart(style.size(14));
        playParams.setMarginEnd(style.size(14));
        transport.addView(playButton, playParams);
        transport.addView(skipButton("⏭\uFE0E", "다음 주제", onNext), new LayoutParams(style.size(56), style.size(56)));
        controls.addView(transport, style.wrapParams());

        LinearLayout level = row();
        level.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        LinearLayout levelText = new LinearLayout(context);
        levelText.setOrientation(VERTICAL);
        levelText.setGravity(Gravity.END);
        levelLabel = style.text("", 21, UiStyle.INK, false);
        levelLabel.setGravity(Gravity.END);
        levelText.addView(levelLabel);
        levelDetail = style.text("", 16, UiStyle.MUTED, false);
        levelDetail.setGravity(Gravity.END); // each line right-aligned
        levelDetail.setLineSpacing(style.size(3), 1f);
        levelText.addView(levelDetail, matchWrap(4));
        level.addView(levelText, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        settingsButton = style.iconButton(style.picture(R.drawable.pre_drive_settings), 28, "시뮬레이션 설정", onSettings);
        settingsButton.setBackground(style.shape(UiStyle.MINT, 25));
        LayoutParams settingsParams = new LayoutParams(style.size(44), style.size(44));
        settingsParams.setMarginStart(style.size(12));
        level.addView(settingsButton, settingsParams);
        controls.addView(level, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        card.addView(controls, matchWrap(40));
        addView(card, matchWrap(30));

        // Picture strip under the card (one wide strip, or three cards for a region story).
        strip = row();
        strip.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(strip, new LayoutParams(LayoutParams.MATCH_PARENT, style.size(221)));
        ((LayoutParams) strip.getLayoutParams()).topMargin = style.size(26);
        // Extra height goes here so the buttons sit at the bottom, as in Figma.
        addView(new View(context), new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        // Bottom row in the shape of the home screen: home · next topic · tools (P) · details (P).
        LinearLayout actions = row();
        TextView home = style.action("← 홈으로", false, onBack);
        homeAction = home;
        home.setTextSize(27);
        home.setBackground(style.shape(UiStyle.NEUTRAL, 36));
        home.setContentDescription("홈 화면으로 가기");
        actions.addView(home, new LayoutParams(0, style.size(72), 1f));
        TextView next = style.action("다음 주제 듣기", true, onNext);
        nextAction = next;
        next.setTextSize(27);
        next.setBackground(style.shape(UiStyle.GREEN, 36));
        LayoutParams nextParams = new LayoutParams(0, style.size(72), 1.77f);
        nextParams.setMarginStart(style.size(10));
        actions.addView(next, nextParams);
        bottomSettings = style.iconButton(style.picture(R.drawable.pre_drive_settings), 36, "시뮬레이션 설정", onSettings);
        bottomSettings.setBackground(style.shape(UiStyle.NEUTRAL, 36));
        LayoutParams gearParams = new LayoutParams(style.size(72), style.size(72));
        gearParams.setMarginStart(style.size(7));
        actions.addView(bottomSettings, gearParams);
        detailsButton = style.iconButton(gridIcon(), 36, "원고와 오늘의 발견 보기", onDetails);
        detailsButton.setBackground(style.shape(UiStyle.NEUTRAL, 36));
        LayoutParams detailsParams = new LayoutParams(style.size(72), style.size(72));
        detailsParams.setMarginStart(style.size(7));
        actions.addView(detailsButton, detailsParams);
        addView(actions, matchWrap(36));

        setPlaying(false);
    }

    /** Text view MainActivity writes playback status into (it also reads it back as state). */
    TextView playbackStatusView() {
        return playbackStatus;
    }

    /**
     * @param subtitleText short hook line under the topic (first sentence of the script)
     * @param tripProgress 0..1 share of the trip already driven
     */
    void bind(BriefingSegment segment, String subtitleText, int studyMinutes, int etaMinutes, float tripProgress) {
        title.setText(segment == null ? "브리핑 없음" : segment.getTopic());
        int pictureRes = TopicVisuals.picture(segment);
        if (pictureRes != boundPicture) {
            boundPicture = pictureRes;
            picture.setImageResource(pictureRes);
        }
        bindStrip(TopicVisuals.strip(segment));
        subtitle.setText(subtitleText == null ? "" : subtitleText);
        subtitle.setVisibility(subtitleText == null || subtitleText.isEmpty() ? GONE : VISIBLE);
        studyTime.setText("공부 진행 시간 " + studyMinutes + "분");
        remaining.setText("남은 주행 시간 " + etaMinutes + "분");
        float done = Math.max(0f, Math.min(1f, tripProgress));
        ((LayoutParams) progressFill.getLayoutParams()).weight = done;
        ((LayoutParams) progressRest.getLayoutParams()).weight = 1f - done;
        progressFill.requestLayout();
    }

    /** e.g. "🌱 새싹 Lv.2" and "다음 단계까지 45XP\n보유 120XP" (two right-aligned lines). */
    void setLevel(String label, String detail) {
        if (!label.contentEquals(levelLabel.getText())) levelLabel.setText(label);
        if (!detail.contentEquals(levelDetail.getText())) levelDetail.setText(detail);
    }

    void setPlaying(boolean playing) {
        playButton.setText(playing ? "❚❚" : "▶");
        playButton.setContentDescription(playing ? "일시정지" : "재생");
    }

    /**
     * After arrival the screen is for reading today's discoveries: playback controls are dimmed (the
     * activity also ignores them) and the back action returns to the arrival summary.
     */
    void setReviewOnly(boolean on) {
        if (on == reviewOnly) return;
        reviewOnly = on;
        transport.setAlpha(on ? 0.35f : 1f);
        nextAction.setAlpha(on ? 0.45f : 1f);
        homeAction.setText(on ? "← 도착 정리로" : "← 홈으로");
        homeAction.setContentDescription(on ? "도착 정리 화면으로 가기" : "홈 화면으로 가기");
    }

    /** D: previous/play-pause/next and home (F6). P: also the tools gear. */
    void setDriving(boolean driving) {
        settingsButton.setVisibility(driving ? INVISIBLE : VISIBLE);
        bottomSettings.setVisibility(driving ? GONE : VISIBLE);
        detailsButton.setVisibility(driving ? GONE : VISIBLE); // the details only show in P
    }

    private void bindStrip(int[] pictures) {
        if (java.util.Arrays.equals(pictures, boundStrip)) return; // bind() runs on every location fix
        boundStrip = pictures;
        strip.removeAllViews();
        for (int i = 0; i < pictures.length; i++) {
            ImageView image = style.picture(pictures[i]);
            image.setScaleType(pictures.length == 1 ? ImageView.ScaleType.FIT_XY : ImageView.ScaleType.FIT_CENTER);
            LayoutParams params = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
            if (i > 0) params.setMarginStart(style.size(10));
            strip.addView(image, params);
        }
    }

    /** Four outlined squares, as on the home screen. */
    private View gridIcon() {
        android.widget.FrameLayout grid = new android.widget.FrameLayout(getContext());
        grid.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 2; c++) {
                View cell = new View(getContext());
                android.graphics.drawable.GradientDrawable outline = style.shape(Color.TRANSPARENT, 1);
                outline.setStroke(Math.max(1, style.size(2)), UiStyle.MUTED);
                cell.setBackground(outline);
                android.widget.FrameLayout.LayoutParams params =
                        new android.widget.FrameLayout.LayoutParams(style.size(14), style.size(14));
                params.topMargin = style.size(2 + r * 18);
                params.leftMargin = style.size(2 + c * 18);
                grid.addView(cell, params);
            }
        }
        return grid;
    }

    private TextView skipButton(String glyph, String label, Runnable click) {
        TextView button = style.text(glyph, 24, UiStyle.INK, false);
        button.setGravity(Gravity.CENTER);
        button.setBackground(style.shape(UiStyle.NEUTRAL, 32));
        button.setContentDescription(label);
        style.clickable(button, click);
        return button;
    }

    /** Audio wave mark: five rounded bars. */
    private View waveIcon() {
        LinearLayout wave = row();
        wave.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        int[] heights = {12, 24, 34, 24, 12};
        for (int i = 0; i < heights.length; i++) {
            View bar = new View(getContext());
            bar.setBackground(style.shape(UiStyle.INK, 2));
            LayoutParams params = new LayoutParams(style.size(4), style.size(heights[i]));
            if (i > 0) params.setMarginStart(style.size(4));
            wave.addView(bar, params);
        }
        return wave;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private LayoutParams centered(int top) {
        LayoutParams params = style.wrapParams();
        params.topMargin = style.size(top);
        return params;
    }

    private LayoutParams matchWrap(int top) {
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = style.size(top);
        return params;
    }
}
