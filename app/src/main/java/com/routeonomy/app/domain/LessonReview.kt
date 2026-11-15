package com.routeonomy.app.domain

/** Answer to "오늘 이야기는 어땠나요?" after a drive. Using it to shape the next script is not built yet. */
enum class LessonReview(val emoji: String, val label: String, val promise: String) {
    JUST_RIGHT("👍", "딱 좋았어요", "다음에도 오늘처럼 들려드릴게요"),
    TOO_EASY("😎", "너무 쉬웠어요", "다음엔 더 깊이 있는 주제로!"),
    TOO_HARD("🤔", "조금 어려웠어요", "다음엔 더 가볍게 풀어드릴게요"),
    BORING("😴", "지루했어요", "다음엔 더 흥미로운 주제로!"),
}

interface ReviewStore {
    fun save(review: LessonReview)
    fun last(): LessonReview?
}
