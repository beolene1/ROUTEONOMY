package com.routeonomy.app.data

import android.content.Context
import com.routeonomy.app.domain.BriefingSegment
import com.routeonomy.app.domain.SegmentTerm
import org.json.JSONArray
import org.json.JSONObject

class AssetSegmentRepository(private val context: Context) {
    fun load(): List<BriefingSegment> = context.assets.open("segments.json").bufferedReader().use { reader ->
        SegmentJsonParser.segments(reader.readText())
    }
}

/** Parses briefing segments from local JSON text. */
object SegmentJsonParser {
    fun segments(json: String): List<BriefingSegment> {
        val rows = JSONArray(json)
        return (0 until rows.length()).map { index -> rows.getJSONObject(index).toSegment() }
    }

    private fun JSONObject.toSegment(): BriefingSegment {
        val termJson = getJSONObject("term")
        return BriefingSegment(
            id = getString("id"),
            type = BriefingSegment.Type.valueOf(getString("type")),
            topic = getString("topic"),
            script = getString("script"),
            durationSec = getInt("durationSec"),
            industries = getJSONArray("industries").strings(),
            keywords = getJSONArray("keywords").strings(),
            term = SegmentTerm(termJson.getString("name"), termJson.getString("explanation")),
            regionId = optString("regionId").takeIf { it.isNotEmpty() && it != "null" },
            sources = getJSONArray("sources").strings(),
        )
    }

    private fun JSONArray.strings(): List<String> = (0 until length()).map(::getString)
}
