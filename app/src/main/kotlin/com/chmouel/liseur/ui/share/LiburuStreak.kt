package com.chmouel.liseur.ui.share

import java.time.LocalDate

/**
 * Liburuville: the run of days read, with the streak protector — every 7 days in a row earns a
 * day of grace (two at most), and a single day without reading spends one instead of breaking it.
 */
object LiburuStreak {
    data class Run(val current: Int, val record: Int, val protectors: Int)

    fun of(days: Set<LocalDate>, today: LocalDate): Run {
        if (days.isEmpty()) return Run(0, 0, 0)
        var d = days.min()
        var run = 0; var bank = 0; var best = 0
        while (!d.isAfter(today)) {
            if (d in days) {
                run++
                if (run % 7 == 0) bank = minOf(bank + 1, 2)
            } else if (d != today) {
                if (bank > 0 && run > 0) bank-- else { run = 0; bank = 0 }
            }
            best = maxOf(best, run)
            d = d.plusDays(1)
        }
        return Run(run, best, bank)
    }
}
