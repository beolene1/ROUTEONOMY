package com.routeonomy.app.data

import android.content.Context
import com.routeonomy.app.domain.QuizQuestion
import org.json.JSONArray

/** Loads arrival quiz questions bundled in assets. */
class AssetQuizRepository(private val context: Context) {
    fun load(): List<QuizQuestion> = context.assets.open("quizzes.json").bufferedReader().use { reader ->
        QuizJsonParser.questions(reader.readText())
    }
}

/** Parses quiz questions from local JSON text. One question per segment. */
object QuizJsonParser {
    fun questions(json: String): List<QuizQuestion> {
        val rows = JSONArray(json)
        val questions = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val choices = row.getJSONArray("choices")
            QuizQuestion(
                segmentId = row.getString("segmentId"),
                question = row.getString("question"),
                choices = (0 until choices.length()).map(choices::getString),
                answerIndex = row.getInt("answerIndex"),
                explanation = row.getString("explanation"),
            )
        }
        require(questions.map { it.segmentId }.toSet().size == questions.size) { "duplicate quiz segmentId" }
        return questions
    }
}
