package com.chmouel.liseur.ui.share

import android.content.Context
import java.time.LocalDate

/**
 * Liburuville: three small missions a day, the same three all day long, each worth 25 points of
 * level. Done is remembered per day, so the points stay once earned.
 */
object LiburuMissions {
    const val XP = 25

    data class State(
        val minutes: Int, val sessions: Int, val marks: Int,
        val early: Boolean, val late: Boolean, val advance: Double,
    )

    data class Mission(val id: String, val title: String, val target: Double, val value: (State) -> Double)

    private val POOL = listOf(
        Mission("m20", "Lee 20 minutos", 20.0) { it.minutes.toDouble() },
        Mission("m45", "Lee 45 minutos", 45.0) { it.minutes.toDouble() },
        Mission("s2", "Haz 2 sesiones de lectura", 2.0) { it.sessions.toDouble() },
        Mission("hl", "Subraya una frase", 1.0) { it.marks.toDouble() },
        Mission("hl3", "Subraya 3 frases", 3.0) { it.marks.toDouble() },
        Mission("early", "Lee antes de las 9", 1.0) { if (it.early) 1.0 else 0.0 },
        Mission("late", "Lee a partir de las 22", 1.0) { if (it.late) 1.0 else 0.0 },
        Mission("adv5", "Avanza un 5 % en un libro", 5.0) { it.advance * 100 },
    )

    @Volatile var app: Context? = null
    private fun prefs(c: Context) = c.getSharedPreferences("liburu-missions", Context.MODE_PRIVATE)

    fun today(date: LocalDate): List<Mission> {
        val r = java.util.Random(date.toEpochDay() * 7919)
        // Always one about time, then two others.
        val time = POOL.filter { it.id.startsWith("m") }.let { it[r.nextInt(it.size)] }
        val rest = POOL.filter { !it.id.startsWith("m") }.shuffled(r).take(2)
        return listOf(time) + rest
    }

    fun done(c: Context, date: LocalDate): Set<String> = prefs(c).getStringSet("d:$date", emptySet()).orEmpty()

    /** Records newly finished missions; returns how many were new. */
    fun record(c: Context, date: LocalDate, ids: Set<String>): Int {
        val before = done(c, date)
        val new = ids - before
        if (new.isEmpty()) return 0
        prefs(c).edit().putStringSet("d:$date", before + new).putInt("total", total(c) + new.size).apply()
        return new.size
    }

    fun total(c: Context) = prefs(c).getInt("total", 0)
    fun xp(): Double = app?.let { total(it) * XP.toDouble() } ?: 0.0
}
