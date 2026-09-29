package ru.montik.app.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/**
 * «Честные» часы игры: время, которое нельзя подкрутить, переведя часы в настройках телефона.
 *
 * Виртуальные часы Монтика считаются от настоящего времени. Раньше бралось время телефона
 * (System.currentTimeMillis), и если перевести его вперёд, Монтик мгновенно высыпался, а дни
 * пролетали. Теперь время идёт по монотонному счётчику SystemClock.elapsedRealtime(): он
 * отсчитывает время с включения телефона (в том числе во сне) и не меняется от настроек часов.
 *
 * Между запусками игры сохраняется пара «наше время + показание счётчика». Если телефон не
 * перезагружали, прошедшее время = разница показаний счётчика. Если перезагружали (счётчик начался
 * заново), берём время телефона, но не больше [MAX_GAP_AFTER_REBOOT_MS] и не меньше, чем телефон
 * проработал после включения. Назад время не идёт никогда.
 */
object TrustedClock {
    private const val PREFS = "trusted_clock"
    private const val K_TRUSTED = "trusted"
    private const val K_ELAPSED = "elapsed"
    private const val K_WALL = "wall"
    private const val K_BOOT = "boot"

    /** Сколько времени может «пройти» за выключенный телефон: не больше суток. */
    private const val MAX_GAP_AFTER_REBOOT_MS = 24L * 60 * 60 * 1000

    /** Как часто сохранять опорную точку (мс). */
    private const val SAVE_EVERY_MS = 10_000L

    private var baseTrusted = 0L
    private var baseElapsed = 0L
    private var lastSaved = 0L
    private var ready = false
    private var appContext: Context? = null

    @Synchronized
    fun init(context: Context) {
        if (ready) return
        val ctx = context.applicationContext
        appContext = ctx
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wall = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val boot = bootCount(ctx)
        val lastTrusted = prefs.getLong(K_TRUSTED, 0L)
        val lastElapsed = prefs.getLong(K_ELAPSED, -1L)
        val lastWall = prefs.getLong(K_WALL, 0L)
        val lastBoot = prefs.getInt(K_BOOT, -1)

        val trusted = when {
            // Первый запуск: доверяем часам телефона.
            lastTrusted <= 0L -> wall
            // Та же загрузка телефона: считаем по монотонному счётчику.
            boot >= 0 && boot == lastBoot && elapsed >= lastElapsed -> lastTrusted + (elapsed - lastElapsed)
            // Счётчик не мог уменьшиться без перезагрузки, даже если номер загрузки неизвестен.
            boot < 0 && elapsed >= lastElapsed && lastElapsed >= 0 -> lastTrusted + (elapsed - lastElapsed)
            // Телефон перезагружали: время телефона, но в разумных пределах.
            else -> {
                val gap = (wall - lastWall).coerceIn(0L, MAX_GAP_AFTER_REBOOT_MS)
                lastTrusted + maxOf(gap, elapsed)
            }
        }
        baseTrusted = maxOf(trusted, lastTrusted)
        baseElapsed = elapsed
        ready = true
        save(force = true)
    }

    /** Текущее «честное» время в миллисекундах. До [init] — время телефона. */
    fun now(): Long {
        if (!ready) return System.currentTimeMillis()
        val t = baseTrusted + (SystemClock.elapsedRealtime() - baseElapsed)
        save(force = false)
        return t
    }

    @Synchronized
    private fun save(force: Boolean) {
        val ctx = appContext ?: return
        val elapsed = SystemClock.elapsedRealtime()
        if (!force && elapsed - lastSaved < SAVE_EVERY_MS) return
        lastSaved = elapsed
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(K_TRUSTED, baseTrusted + (elapsed - baseElapsed))
                .putLong(K_ELAPSED, elapsed)
                .putLong(K_WALL, System.currentTimeMillis())
                .putInt(K_BOOT, bootCount(ctx))
                .apply()
        } catch (e: Exception) {
            // Не смогли сохранить — не страшно: при следующем запуске сработает защита по перезагрузке.
        }
    }

    private fun bootCount(ctx: Context): Int = try {
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT, -1)
    } catch (e: Exception) {
        -1
    }
}
