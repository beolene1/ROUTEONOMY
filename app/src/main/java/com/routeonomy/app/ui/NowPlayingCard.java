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
 * Figma "Now Playing" screen (P and D): topic, chip picture, sentence progress,
 * play/pause in the middle and remaining minutes on the right. Behaviour stays in MainActivity.
 */
final class NowPlayingCard extends LinearLayout {
    private final UiStyle style;
    private final TextView backButton;
    private final TextView title;
    private final TextView subtitle;
    private final TextView routeLine;
    private final TextView progressLabel;
    private final View progressFill;
    private final View progressRest;
    private final TextView playbackStatus;
    private final TextView playButton;
    private final TextView remaining;
    private final TextView nextButton;

    NowPlayingCard(Context context, Runnable onBack, Runnable onPlayPause, Runnable onNext) {
        super(context);
        style = new UiStyle(context, 1f);
        setOrientation(VERTICAL);

        LinearLayout header = row();
        header.addView(style.logoPill(), wrap());
        header.addView(new View(context), new LayoutParams(0, 1, 1f));
        backButton = style.action("주행 전 화면", false, onBack);
        header.addView(backButton, new LayoutParams(LayoutParams.WRAP_CONTENT, style.size(44)));
        addView(header, matchWrap(0));

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(style.size(24), style.size(22), style.size(24), style.size(22));
        style.card(card);

        card.addView(style.text("•  Now Playing  •", 16, UiStyle.MUTED, false), wrap());
        title = style.text("", 30, UiStyle.INK, true);
        title.setGravity(Gravity.CENTER);
        card.addView(title, centered(10));
        subtitle = style.text("", 17, UiStyle.MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        card.addView(subtitle, centered(8));

        ImageView chip = new ImageView(context);
        chip.setImageResource(R.drawable.pre_drive_chip);
        chip.setScaleType(ImageView.ScaleType.FIT_CENTER);
        chip.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutParams chipParams = new LayoutParams(style.size(220), style.size(150));
        chipParams.topMargin = style.size(14);
        card.addView(chip, chipParams);

        routeLine = style.pill("", UiStyle.GREEN, UiStyle.MINT, 15);
        routeLine.setGravity(Gravity.CENTER);
        routeLine.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        card.addView(routeLine, centered(14));

        progressLabel = style.text("", 14, UiStyle.MUTED, false);
        card.addView(progressLabel, matchWrap(18));
        LinearLayout track = new LinearLayout(context);
        track.setBackground(style.shape(UiStyle.NEUTRAL, 3));
        track.setClipToOutline(true);
        progressFill = new View(context);
        progressFill.setBackground(style.shape(UiStyle.GREEN, 3));
        progressRest = new View(context);
        track.addView(progressFill, new LayoutParams(0, LayoutParams.MATCH_PARENT, 0f));
        track.addView(progressRest, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        LayoutParams trackParams = new LayoutParams(LayoutParams.MATCH_PARENT, style.size(6));
        trackParams.topMargin = style.size(8);
        card.addView(track, trackParams);

        LinearLayout controls = row();
        playbackStatus = style.text("브리핑 대기", 15, UiStyle.MUTED, false);
        playbackStatus.setMaxLines(3);
        playbackStatus.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        controls.addView(playbackStatus, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        playButton = style.text("▶", 26, Color.WHITE, true);
        playButton.setGravity(Gravity.CENTER);
        playButton.setBackground(style.shape(UiStyle.INK, 36));
        style.clickable(playButton, onPlayPause);
        LayoutParams playParams = new LayoutParams(style.size(72), style.size(72));
        playParams.setMarginStart(style.size(12));
        playParams.setMarginEnd(style.size(12));
        controls.addView(playButton, playParams);
        remaining = style.text("", 17, UiStyle.INK, true);
        remaining.setGravity(Gravity.END);
        controls.addView(remaining, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        card.addView(controls, matchWrap(18));
        addView(card, matchWrap(16));

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

    void bind(BriefingSegment segment, String route, int etaMinutes, int sentence, int sentences) {
        title.setText(segment == null ? "브리핑 없음" : segment.getTopic());
        subtitle.setText(segment == null ? "" : "새 용어 · " + segment.getTerm().getName());
        subtitle.setVisibility(segment == null ? GONE : VISIBLE);
        routeLine.setText(route);
        remaining.setText("목적지까지\n" + etaMinutes + "분");
        float done = sentences > 0 ? Math.min(1f, (float) sentence / sentences) : 0f;
        ((LayoutParams) progressFill.getLayoutParams()).weight = done;
        ((LayoutParams) progressRest.getLayoutParams()).weight = 1f - done;
        progressFill.requestLayout();
        progressLabel.setText(sentences > 0 ? "문장 " + Math.min(sentence + 1, sentences) + " / " + sentences : "재생 전");
    }

    void setPlaying(boolean playing) {
        playButton.setText(playing ? "❚❚" : "▶");
        playButton.setContentDescription(playing ? "일시정지" : "재생");
    }

    /** D: only play/pause stays (F6). P: back to dashboard (when allowed) and next topic. */
    void setDriving(boolean driving, boolean canGoBack) {
        nextButton.setVisibility(driving ? GONE : VISIBLE);
        backButton.setVisibility(!driving && canGoBack ? VISIBLE : GONE);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private LayoutParams wrap() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    private LayoutParams centered(int top) {
        LayoutParams params = wrap();
        params.topMargin = style.size(top);
        return params;
    }

    private LayoutParams matchWrap(int top) {
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = style.size(top);
        return params;
    }
}
