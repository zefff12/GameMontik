package ru.montik.app.game

/**
 * Сон и виртуальное время Монтика.
 *
 * Монтик живёт по виртуальным часам: одна настоящая минута = [Rules.VIRTUAL_PER_REAL] виртуальных.
 * Часы хранятся как «опорная точка» (виртуальная минута + настоящее время в этот момент), поэтому
 * они идут и тогда, когда игра закрыта: при следующем запуске время просто досчитывается.
 */

/** Как Монтик выспался в последний раз — от этого зависит, как быстро он устанет сегодня. */
enum class SleepQuality {
    /** Обычный день. */
    NORMAL,

    /** Хорошо выспался (8 часов и больше): смена отнимает меньше сил. */
    RESTED,

    /** Не выспался (меньше 6 часов): смена отнимает больше сил. */
    TIRED
}

/**
 * Идущий сон. Времена — в абсолютных виртуальных минутах (от 00:00 первого дня).
 * [alarmV] сдвигается, когда будильник откладывают.
 */
data class SleepSession(
    val startV: Long,
    val alarmV: Long,
    val place: SleepPlace,
    val snoozes: Int = 0
)

/** Что показать на экране сна: сколько проспал, сколько осталось, звонит ли будильник. */
data class SleepStatus(
    val virtualNow: Long,
    val sleptMinutes: Int,
    val remainingMinutes: Int,
    val totalMinutes: Int,
    val ringing: Boolean,
    /** Время звонка будильника, минут от полуночи. */
    val alarmTimeOfDay: Int
) {
    /** Доля сна от 0 до 1 для полоски. */
    val progress: Float
        get() = if (totalMinutes <= 0) 1f else (sleptMinutes.toFloat() / totalMinutes).coerceIn(0f, 1f)
}

/** Когда Монтик ляжет и когда прозвенит будильник. */
data class SleepPlan(val bedV: Long, val alarmV: Long) {
    val minutes: Int get() = (alarmV - bedV).toInt()
}

object VirtualClock {
    const val DAY = 1440

    /** Виртуальная минута «сейчас» при настоящем времени [realNowMs]. */
    fun now(state: GameState, realNowMs: Long): Long {
        if (state.clockStamp <= 0L) return state.clockMinutes
        val elapsedMs = (realNowMs - state.clockStamp).coerceAtLeast(0L)
        return state.clockMinutes + elapsedMs * Rules.VIRTUAL_PER_REAL / 60_000L
    }

    /** Минут от полуночи (0..1439). */
    fun timeOfDay(v: Long): Int = Math.floorMod(v, DAY.toLong()).toInt()

    /** «07:05» из минут от полуночи. */
    fun format(minutesOfDay: Int): String {
        val m = Math.floorMod(minutesOfDay, DAY)
        return "${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}"
    }

    fun formatV(v: Long): String = format(timeOfDay(v))

    /** «3 ч 20 мин», «45 мин», «8 ч». */
    fun duration(minutes: Int): String {
        val m = minutes.coerceAtLeast(0)
        val h = m / 60
        val r = m % 60
        return when {
            h == 0 -> "$r мин"
            r == 0 -> "$h ч"
            else -> "$h ч $r мин"
        }
    }

    /**
     * Если Монтика укладывают днём (с 07:00 до 21:00), он ложится вечером, в 21:00;
     * если уже вечер или ночь — прямо сейчас. Будильник — на ближайшие 07:00.
     */
    fun planSleep(nowV: Long): SleepPlan {
        val tod = timeOfDay(nowV)
        val bed = if (tod >= Rules.WAKE_MINUTES && tod < Rules.BEDTIME_MINUTES) {
            nowV - tod + Rules.BEDTIME_MINUTES
        } else {
            nowV
        }
        val bedTod = timeOfDay(bed)
        val bedDay = bed - bedTod
        val alarm = if (bedTod < Rules.WAKE_MINUTES) bedDay + Rules.WAKE_MINUTES else bedDay + DAY + Rules.WAKE_MINUTES
        return SleepPlan(bed, alarm)
    }
}
