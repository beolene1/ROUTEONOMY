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

/**
 * Arrival quiz over segments heard to the end this session.
 * Each question is answered once; [next] moves on only after an answer. [restart] clears answers and score.
 */
class QuizSession(val questions: List<QuizQuestion>) {
    private var index = 0
    private var selected: Int? = null
    private var correctCount = 0

    fun total(): Int = questions.size
    fun isEmpty(): Boolean = questions.isEmpty()
    fun isFinished(): Boolean = index >= questions.size
    /** 0-based index of the current question; equals [total] once finished. */
    fun position(): Int = index
    fun current(): QuizQuestion? = questions.getOrNull(index)
    fun selectedChoice(): Int? = selected
    fun isAnswered(): Boolean = selected != null
    fun score(): Int = correctCount

    /** Records the first answer for the current question and returns whether it was correct. Later taps are ignored. */
    fun answer(choice: Int): Boolean {
        val question = checkNotNull(current()) { "quiz already finished" }
        require(choice in question.choices.indices) { "choice out of range" }
        selected?.let { return it == question.answerIndex }
        selected = choice
        val correct = choice == question.answerIndex
        if (correct) correctCount++
        return correct
    }

    fun next() {
        check(isAnswered()) { "answer before moving on" }
        index++
        selected = null
    }

    fun restart() {
        index = 0
        selected = null
        correctCount = 0
    }

    companion object {
        const val MAX_QUESTIONS = 3

        /** Questions for completed segments, in the order they were completed, at most [max]. */
        @JvmStatic
        @JvmOverloads
        fun fromCompleted(all: List<QuizQuestion>, completedIds: Collection<String>, max: Int = MAX_QUESTIONS): QuizSession {
            val bySegment = all.associateBy { it.segmentId }
            return QuizSession(completedIds.distinct().mapNotNull { bySegment[it] }.take(max))
        }
    }
}
