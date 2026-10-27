package com.example.swipecleaner

object ReviewLogic {
    fun nextUndecided(ids: List<String>, decisions: Map<String, String>, current: Int): Int? =
        (1..ids.size).map { (current + it) % ids.size }.firstOrNull { ids[it] !in decisions }
    fun restore(decisions: Map<String, String>, id: String, old: String?): Map<String, String> =
        if (old == null) decisions - id else decisions + (id to old)
}
