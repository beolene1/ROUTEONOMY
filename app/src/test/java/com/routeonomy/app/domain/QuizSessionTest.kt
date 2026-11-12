package com.routeonomy.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizSessionTest {
    private fun q(id: String, answer: Int = 0) = QuizQuestion(id, "Q-$id", listOf("a", "b", "c"), answer, "E-$id")
    private val all = listOf(q("s1"), q("s2", 1), q("s3", 2), q("s4"))

    @Test fun picksCompletedSegmentsInCompletionOrderUpToThree() {
        val quiz = QuizSession.fromCompleted(all, listOf("s4", "wrapup", "s2", "s1", "s3"))
        assertEquals(listOf("s4", "s2", "s1"), quiz.questions.map { it.segmentId })
    }

    @Test fun noCompletedSegmentsMeansEmptyQuiz() {
        val quiz = QuizSession.fromCompleted(all, emptyList())
        assertTrue(quiz.isEmpty())
        assertTrue(quiz.isFinished())
        assertNull(quiz.current())
    }

    @Test fun completedSegmentsWithoutQuestionsAreSkipped() {
        assertTrue(QuizSession.fromCompleted(all, listOf("wrapup")).isEmpty())
    }

    @Test fun gradesAnswersAndFinishes() {
        val quiz = QuizSession.fromCompleted(all, listOf("s1", "s2"))
        assertTrue(quiz.answer(0))
        quiz.next()
        assertFalse(quiz.answer(0))
        assertEquals(0, quiz.selectedChoice())
        quiz.next()
        assertTrue(quiz.isFinished())
        assertEquals(1, quiz.score())
        assertEquals(2, quiz.total())
    }

    @Test fun secondAnswerOnSameQuestionIsIgnored() {
        val quiz = QuizSession.fromCompleted(all, listOf("s1"))
        assertFalse(quiz.answer(1))
        assertFalse(quiz.answer(0))
        assertEquals(1, quiz.selectedChoice())
        assertEquals(0, quiz.score())
    }

    @Test(expected = IllegalStateException::class)
    fun cannotSkipUnansweredQuestion() {
        QuizSession.fromCompleted(all, listOf("s1")).next()
    }

    @Test(expected = IllegalArgumentException::class)
    fun choiceOutOfRangeIsRejected() {
        QuizSession.fromCompleted(all, listOf("s1")).answer(3)
    }

    @Test fun restartClearsProgressAndScore() {
        val quiz = QuizSession.fromCompleted(all, listOf("s1", "s2"))
        quiz.answer(0); quiz.next(); quiz.answer(1); quiz.next()
        quiz.restart()
        assertEquals(0, quiz.position())
        assertEquals(0, quiz.score())
        assertFalse(quiz.isAnswered())
        assertEquals("s1", quiz.current()?.segmentId)
    }
}
