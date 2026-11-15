package com.routeonomy.app.data

import android.content.Context
import androidx.core.content.edit
import com.routeonomy.app.domain.LessonReview
import com.routeonomy.app.domain.ReviewStore

/** Keeps the latest lesson review and a count per answer on the device (SharedPreferences). */
class PrefsReviewStore(context: Context) : ReviewStore {
    private val prefs = context.applicationContext.getSharedPreferences("lesson_review", Context.MODE_PRIVATE)

    override fun save(review: LessonReview) {
        prefs.edit {
            putString(KEY_LAST, review.name)
            putLong(KEY_LAST_AT, System.currentTimeMillis())
            putInt(KEY_COUNT + review.name, prefs.getInt(KEY_COUNT + review.name, 0) + 1)
        }
    }

    override fun last(): LessonReview? =
        prefs.getString(KEY_LAST, null)?.let { name -> LessonReview.entries.firstOrNull { it.name == name } }

    private companion object {
        const val KEY_LAST = "last"
        const val KEY_LAST_AT = "last_at"
        const val KEY_COUNT = "count_"
    }
}
