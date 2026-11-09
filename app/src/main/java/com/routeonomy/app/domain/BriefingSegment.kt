package com.routeonomy.app.domain

data class SegmentTerm(val name: String, val explanation: String)

data class BriefingSegment(
    val id: String,
    val type: Type,
    val topic: String,
    val script: String,
    val durationSec: Int,
    val industries: List<String>,
    val keywords: List<String>,
    val term: SegmentTerm,
    val regionId: String?,
    val sources: List<String>,
) {
    enum class Type { MAIN, REGION, WRAPUP }
}

data class BriefingPlan(val segments: List<BriefingSegment>)
