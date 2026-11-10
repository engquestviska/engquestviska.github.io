package com.engquest.app.domain

/** Where a student is on the level curve: [into] XP into this level, [needForNext] XP this level takes. */
data class LevelInfo(val level: Int, val into: Int, val needForNext: Int) {
    val toNext: Int get() = needForNext - into
    val progress: Float get() = if (needForNext > 0) into.toFloat() / needForNext else 0f
}

private val TITLES = listOf("Beginner", "Explorer", "Achiever", "Rising Star", "Champion", "Legend")

/** Same curve as the website (grade10/index.html levelFromXP) and the server: level N→N+1 needs 5 + 5N XP. */
fun levelFromXp(xp: Int): LevelInfo {
    val x = xp.coerceAtLeast(0)
    var level = 1
    var need = 10
    var floor = 0
    while (x >= floor + need) {
        floor += need
        level++
        need += 5
    }
    return LevelInfo(level, x - floor, need)
}

fun levelTitle(level: Int): String = TITLES[(level - 1).coerceIn(0, TITLES.lastIndex)]
