package com.routeonomy.app.ui;

import com.routeonomy.app.R;
import com.routeonomy.app.domain.BriefingSegment;
import com.routeonomy.app.domain.IndicatorKind;

import java.util.HashMap;
import java.util.Map;

/**
 * Screen-only extras per topic for the Figma screens: hook question, linked indicator, mascot line
 * and pictures. Content (titles, scripts) stays in segments.json; unknown ids get neutral fallbacks.
 * A linked indicator is optional: only a topic with a matching live indicator shows one on the home screen.
 */
final class TopicVisuals {
    /** Live indicator linked to the topic, or null when it has none (the card is hidden). */
    final IndicatorKind indicator;
    /** One line on what the indicator means for this topic, shown under its card. */
    final String indicatorCaption;
    /** Speech bubble of the study mascot on the home screen. */
    final String bubble;
    /** Hook question shown under the title (home card, Now Playing). */
    final String question;

    private TopicVisuals(IndicatorKind indicator, String indicatorCaption, String bubble, String question) {
        this.indicator = indicator;
        this.indicatorCaption = indicatorCaption;
        this.bubble = bubble;
        this.question = question;
    }

    private static final Map<String, TopicVisuals> BY_ID = new HashMap<>();
    static {
        BY_ID.put("main-semiconductor", new TopicVisuals(null, null,
                "오늘은 AI 반도체 산업에 대해 알아볼거에요!", "AI 기술이 발전할수록 반도체 산업은 어떻게 달라질까요?"));
        BY_ID.put("main-grid", new TopicVisuals(null, null,
                "오늘은 전력망 투자에 대해 알아볼거에요!", "AI가 쓰는 전기가 늘어나면 전력망은 어떻게 바뀔까요?"));
        BY_ID.put("main-cooling", new TopicVisuals(null, null,
                "오늘은 데이터센터 냉각 산업에 대해 알아볼거에요!", "뜨거워진 데이터센터는 어떻게 열을 식힐까요?"));
        BY_ID.put("main-storage", new TopicVisuals(null, null,
                "오늘은 전력 저장에 대해 알아볼거에요!", "남는 전기를 저장해 두면 무엇이 달라질까요?"));
        BY_ID.put("main-efficiency", new TopicVisuals(null, null,
                "오늘은 효율 경쟁에 대해 알아볼거에요!", "같은 전기로 더 많이 계산하는 것이 왜 중요할까요?"));
        BY_ID.put("region-pangyo-realestate", new TopicVisuals(null, null, null,
                "판교의 오피스 건물에는 누가, 어떻게 투자할까요?"));
        BY_ID.put("region-pangyo-industry", new TopicVisuals(null, null, null,
                "IT 기업들은 왜 판교에 모였을까요?"));
        BY_ID.put("region-pangyo-newbiz", new TopicVisuals(null, null, null,
                "판교에서는 어떤 새 기술을 시험하고 있을까요?"));
        BY_ID.put("wrapup", new TopicVisuals(null, null, null,
                "오늘 들은 이야기를 한 번에 정리해 볼까요?"));
    }

    static TopicVisuals of(BriefingSegment segment) {
        if (segment == null) return new TopicVisuals(null, null, null, null);
        TopicVisuals known = BY_ID.get(segment.getId());
        if (known != null) return known;
        return new TopicVisuals(null, null, null, segment.getTerm().getName() + " · " + segment.getTerm().getExplanation());
    }

    /** Mascot line on the home screen; falls back to the topic title. */
    static String bubble(BriefingSegment segment) {
        TopicVisuals visuals = of(segment);
        if (visuals.bubble != null) return visuals.bubble;
        return segment == null ? "오늘의 주제를 골라 보세요!" : "오늘은 '" + segment.getTopic() + "'를 알아볼거에요!";
    }

    /** Big picture on the Now Playing card. */
    static int picture(BriefingSegment segment) {
        return segment != null && segment.getType() == BriefingSegment.Type.REGION ? R.drawable.np_pangyo : R.drawable.np_chip3d;
    }

    /** Picture strip under the Now Playing card: one wide strip for economy topics, three cards for a region. */
    static int[] strip(BriefingSegment segment) {
        if (segment != null && segment.getType() == BriefingSegment.Type.REGION) {
            return new int[] {R.drawable.np_strip_region_1, R.drawable.np_strip_region_2, R.drawable.np_strip_region_3};
        }
        return new int[] {R.drawable.np_strip_econ};
    }
}
