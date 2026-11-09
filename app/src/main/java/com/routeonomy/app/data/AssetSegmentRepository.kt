package com.routeonomy.app.data

import android.content.Context
import com.routeonomy.app.domain.BriefingSegment
import com.routeonomy.app.domain.SegmentTerm
import org.json.JSONArray
import org.json.JSONObject

class AssetSegmentRepository(private val context: Context) {
    fun load(): List<BriefingSegment> = context.assets.open("segments.json").bufferedReader().use { reader ->
        val rows = JSONArray(reader.readText())
        (0 until rows.length()).map { index -> rows.getJSONObject(index).toSegment() }
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
            regionId = optString("regionId").takeIf(String::isNotEmpty),
            sources = getJSONArray("sources").strings(),
        )
    }

    private fun JSONArray.strings(): List<String> = (0 until length()).map(::getString)
}
