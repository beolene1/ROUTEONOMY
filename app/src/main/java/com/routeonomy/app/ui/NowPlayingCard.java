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
 * Figma "Now Playing" screen (P and D): app header, topic and hook line, chip picture,
 * study time / remaining drive time over a trip progress bar, and audio · play/pause · level row.
 * Behaviour stays in MainActivity.
 */
final class NowPlayingCard extends LinearLayout {
    private final UiStyle style;
    private final TextView backButton;
    private final TextView title;
    private final TextView subtitle;
    private final TextView studyTime;
    private final TextView remaining;
    private final View progressFill;
    private final View progressRest;
    private final TextView playbackStatus;
    private final TextView playButton;
    private final View settingsButton;
    private final TextView nextButton;

    NowPlayingCard(Context context, Runnable onBack, Runnable onPlayPause, Runnable onNext,
            Runnable onSettings) {
        super(context);
        style = new UiStyle(context, 1f);
        setOrientation(VERTICAL);

        addView(style.appHeader(false), matchWrap(0));
        backButton = style.action("← 홈으로", false, onBack);
        backButton.setContentDescription("홈 화면으로 가기");
        LayoutParams backParams = new LayoutParams(LayoutParams.WRAP_CONTENT, style.size(44));
        backParams.topMargin = style.size(12);
        addView(backButton, backParams);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(style.size(36), style.size(24), style.size(36), style.size(24));
        card.setBackground(style.shape(UiStyle.SURFACE, 22));
        card.setElevation(style.size(4));

        card.addView(style.text("•  Now Playing  •", 22, UiStyle.LIVE, false), style.wrapParams());
        title = style.text("", 32, UiStyle.INK, true);
        title.setGravity(Gravity.CENTER);
        card.addView(title, centered(12));
        subtitle = style.text("", 20, UiStyle.MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setMaxLines(2);
        card.addView(subtitle, centered(10));

        ImageView chip = style.picture(R.drawable.pre_drive_chip);
        LayoutParams chipParams = new LayoutParams(style.size(320), style.size(250));
        chipParams.topMargin = style.size(8);
        card.addView(chip, chipParams);

        LinearLayout labels = row();
        studyTime = style.text("", 20, UiStyle.MUTED, false);
        labels.addView(studyTime, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        remaining = style.text("", 20, UiStyle.INK, false);
        remaining.setGravity(Gravity.END);
        labels.addView(remaining, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        card.addView(labels, matchWrap(12));

        // Trip progress: thin track with a round knob at the current point.
        LinearLayout track = row();
        progressFill = new View(context);
        progressFill.setBackground(style.shape(UiStyle.SAGE, 3));
        track.addView(progressFill, new LayoutParams(0, style.size(5), 0f));
        View knob = new View(context);
        knob.setBackground(style.shape(UiStyle.SAGE, 11));
        track.addView(knob, new LayoutParams(style.size(22), style.size(22)));
        progressRest = new View(context);
        progressRest.setBackground(style.shape(UiStyle.NEUTRAL, 3));
        track.addView(progressRest, new LayoutParams(0, style.size(5), 1f));
        card.addView(track, matchWrap(10));

        LinearLayout controls = row();
        LinearLayout audio = row();
        audio.addView(waveIcon(), new LayoutParams(style.size(42), style.size(34)));
        LinearLayout audioText = new LinearLayout(context);
        audioText.setOrientation(VERTICAL);
        audioText.addView(style.text("Audio\nguidance", 20, UiStyle.INK, false));
        playbackStatus = style.text("브리핑 대기", 13, UiStyle.MUTED, false);
        playbackStatus.setMaxLines(2);
        playbackStatus.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        audioText.addView(playbackStatus, matchWrap(4));
        LayoutParams audioTextParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        audioTextParams.setMarginStart(style.size(12));
        audio.addView(audioText, audioTextParams);
        controls.addView(audio, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        playButton = style.text("", 40, Color.WHITE, true);
        playButton.setGravity(Gravity.CENTER);
        playButton.setBackground(style.shape(UiStyle.PLAYER, 64));
        playButton.setElevation(style.size(6));
        style.clickable(playButton, onPlayPause);
        controls.addView(playButton, new LayoutParams(style.size(128), style.size(128)));

        LinearLayout level = row();
        level.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        level.addView(style.text("Level. 1", 24, UiStyle.INK, false));
        settingsButton = style.iconButton(style.picture(R.drawable.pre_drive_settings), 28, "시뮬레이션 설정", onSettings);
        settingsButton.setBackground(style.shape(UiStyle.MINT, 25));
        LayoutParams settingsParams = new LayoutParams(style.size(48), style.size(48));
        settingsParams.setMarginStart(style.size(14));
        level.addView(settingsButton, settingsParams);
        controls.addView(level, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        card.addView(controls, matchWrap(28));
        addView(card, matchWrap(18));

        nextButton = style.action("다음 주제", false, onNext);
        LayoutParams nextParams = new LayoutParams(LayoutParams.MATCH_PARENT, style.size(48));
        nextParams.topMargin = style.size(12);
        addView(nextButton, nextParams);
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
        subtitle.setText(subtitleText == null ? "" : subtitleText);
        subtitle.setVisibility(subtitleText == null || subtitleText.isEmpty() ? GONE : VISIBLE);
        studyTime.setText("공부 진행 시간 " + studyMinutes + "분");
        remaining.setText("남은 주행 시간 " + etaMinutes + "분");
        float done = Math.max(0f, Math.min(1f, tripProgress));
        ((LayoutParams) progressFill.getLayoutParams()).weight = done;
        ((LayoutParams) progressRest.getLayoutParams()).weight = 1f - done;
        progressFill.requestLayout();
    }

    void setPlaying(boolean playing) {
        playButton.setText(playing ? "❚❚" : "▶");
        playButton.setContentDescription(playing ? "일시정지" : "재생");
    }

    /** D: play/pause and home only (F6). P: also next topic and tools. */
    void setDriving(boolean driving) {
        nextButton.setVisibility(driving ? GONE : VISIBLE);
        settingsButton.setVisibility(driving ? INVISIBLE : VISIBLE);
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
