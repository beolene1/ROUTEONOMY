package com.routeonomy.app.domain

/** One multiple-choice question about a briefing segment. */
data class QuizQuestion(
    val segmentId: String,
    val question: String,
    val choices: List<String>,
    val answerIndex: Int,
    val explanation: String,
) {
    init {
        require(choices.size >= 2) { "quiz $segmentId needs at least two choices" }
        require(answerIndex in choices.indices) { "quiz $segmentId answerIndex out of range" }
    }
}
