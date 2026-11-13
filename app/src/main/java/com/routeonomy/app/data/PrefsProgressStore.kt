package com.routeonomy.app.data

import android.content.Context
import androidx.core.content.edit
import com.routeonomy.app.domain.ProgressSnapshot
import com.routeonomy.app.domain.ProgressStore

/** Keeps points on the device (SharedPreferences) so the growth stage survives app restarts. */
class PrefsProgressStore(context: Context) : ProgressStore {
    private val prefs = context.applicationContext.getSharedPreferences("player_progress", Context.MODE_PRIVATE)

    override fun load() = ProgressSnapshot(prefs.getInt(KEY_EARNED, 0), prefs.getInt(KEY_BALANCE, 0))

    override fun save(snapshot: ProgressSnapshot) {
        prefs.edit { putInt(KEY_EARNED, snapshot.earnedTotal).putInt(KEY_BALANCE, snapshot.balance) }
    }

    private companion object {
        const val KEY_EARNED = "earned_total"
        const val KEY_BALANCE = "balance"
    }
}
