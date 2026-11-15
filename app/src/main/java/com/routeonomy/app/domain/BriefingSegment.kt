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
    /** Content group shown as a tag; independent of [type], which decides when a segment plays. */
    val category: Category? = Category.defaultFor(type),
) {
    enum class Type { MAIN, REGION, WRAPUP }

    enum class Category(val label: String) {
        TECH("신산업·기술"),
        LIVING("생활 경제"),
        REGION("지역·부동산");

        companion object {
            /** Older JSON without a category: region segments are regional, other main ones are tech. */
            fun defaultFor(type: Type): Category? = when (type) {
                Type.MAIN -> TECH
                Type.REGION -> REGION
                Type.WRAPUP -> null
            }
        }
    }
}

data class BriefingPlan(val segments: List<BriefingSegment>)
