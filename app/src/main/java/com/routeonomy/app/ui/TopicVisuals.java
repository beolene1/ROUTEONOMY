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
    /** Topic picture (home thumbnail and Now Playing), or 0 for the default chip / Pangyo art. */
    final int picture;

    private TopicVisuals(IndicatorKind indicator, String indicatorCaption, String bubble, String question) {
        this(indicator, indicatorCaption, bubble, question, 0);
    }

    private TopicVisuals(IndicatorKind indicator, String indicatorCaption, String bubble, String question, int picture) {
        this.picture = picture;
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
        BY_ID.put("main-rates", new TopicVisuals(IndicatorKind.US_10Y,
                "미국 10년물 국채금리는 세계에서 가장 많이 참고하는 시장금리예요!",
                "오늘은 금리가 내 지갑에 닿는 길을 알아볼거에요!", "미국 금리가 오르면, 내 대출 이자도 오를까요?", R.drawable.topic_rates));
        BY_ID.put("main-fx", new TopicVisuals(IndicatorKind.USD_KRW,
                "원·달러 카드는 유럽중앙은행 기준환율로 계산해 서울 종가와 조금 달라요!",
                "오늘은 환율과 장바구니의 관계를 알아볼거에요!", "환율이 오르면 내 지갑엔 무슨 일이 생길까요?", R.drawable.topic_fx));
        BY_ID.put("main-ppi", new TopicVisuals(IndicatorKind.US_PPI,
                "생산자물가는 기업이 물건을 팔 때 받는 가격의 변화를 보여 줘요!",
                "오늘은 미국 물가 뉴스가 중요한 이유를 알아볼거에요!", "미국 물가 뉴스가 왜 우리에게 중요할까요?", R.drawable.topic_ppi));
        BY_ID.put("main-deposit-token", new TopicVisuals(null, null,
                "오늘은 전기차 충전기 설치에 쓰이는 예금 토큰에 대해 알아볼거에요!", "충전기 설치 보조금에 사용 조건을 붙이면 뭐가 달라질까요?", R.drawable.topic_token));
        BY_ID.put("main-euljiro", new TopicVisuals(null, null,
                "오늘은 을지로가 힙지로로 불리게 된 이유에 대해 알아볼거에요!", "인쇄소와 철공소가 모인 골목에 왜 카페 손님들이 찾아올까요?", R.drawable.topic_euljiro));
        BY_ID.put("main-space", new TopicVisuals(null, null,
                "오늘은 위성이 보내는 정보가 어떻게 상품이 되는지 알아볼거에요!", "오늘 본 날씨 예보에 민간 위성 회사의 일이 들어 있을까요?", R.drawable.topic_space));
        BY_ID.put("main-defense", new TopicVisuals(null, null,
                "오늘은 한국 방산 수출 계약에 어떤 일이 담기는지 알아볼거에요!", "군용 장비를 수출할 때 왜 정비와 현지 생산도 계약에 들어갈까요?", R.drawable.topic_defense));
        BY_ID.put("main-nvidia", new TopicVisuals(null, null,
                "오늘은 젠슨 황 사임 뒤 반도체 시장이 주목하는 지표에 대해 알아볼거에요!", "엔비디아 대표가 바뀌면 반도체 시장은 어떤 숫자에 주목할까요?"));
        BY_ID.put("main-etf", new TopicVisuals(null, null,
                "오늘은 ETF가 어떻게 코스피 상장주식 거래대금을 앞질렀는지 알아볼거에요!", "사람들은 왜 개별 주식 대신 ETF를 사고팔까요?", R.drawable.topic_etf));
        BY_ID.put("main-nodogang", new TopicVisuals(null, null,
                "오늘은 노원·도봉·강북에서 집값과 거래량이 왜 엇갈렸는지 알아볼거에요!", "집값이 10퍼센트 넘게 올랐는데 왜 계약은 줄었을까요?", R.drawable.topic_housing));
        BY_ID.put("region-pangyo-housing", new TopicVisuals(null, null, null,
                "판교에서 일한다면, 집에 드는 돈은 얼마일까요?", R.drawable.topic_housing));
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
        int own = of(segment).picture;
        if (own != 0) return own;
        return segment != null && segment.getType() == BriefingSegment.Type.REGION ? R.drawable.np_pangyo : R.drawable.np_chip3d;
    }

    /** Home topic card thumbnail: the topic's own picture, else the AI chip. */
    static int thumbnail(BriefingSegment segment) {
        int own = of(segment).picture;
        return own != 0 ? own : R.drawable.pre_drive_chip;
    }

    /** Picture strip under the Now Playing card: one wide strip for economy topics, three cards for a region. */
    static int[] strip(BriefingSegment segment) {
        if (segment != null && segment.getType() == BriefingSegment.Type.REGION) {
            return new int[] {R.drawable.np_strip_region_1, R.drawable.np_strip_region_2, R.drawable.np_strip_region_3};
        }
        return new int[] {R.drawable.np_strip_econ};
    }
}
