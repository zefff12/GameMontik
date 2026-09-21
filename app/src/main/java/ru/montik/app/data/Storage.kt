package ru.montik.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import ru.montik.app.game.GameState
import ru.montik.app.game.StateCodec
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Всё хранится локально в памяти приложения: прогресс, картинка Монтика и PIN родителя.
 * Интернет и учётная запись не нужны, прогресс не теряется при выходе из приложения.
 */
class Storage(context: Context) {
    private val appContext = context.applicationContext
    private val saveFile = File(appContext.filesDir, "save.properties")
    private val saveTmp = File(appContext.filesDir, "save.properties.tmp")
    private val spriteFile = File(appContext.filesDir, "montik.png")
    private val photoDir = File(appContext.filesDir, "photos")
    private val prefs = appContext.getSharedPreferences("parent", Context.MODE_PRIVATE)

    // ── Прогресс ──

    fun loadState(): GameState? = try {
        if (saveFile.exists()) StateCodec.decode(saveFile.readText(Charsets.UTF_8)) else null
    } catch (e: Exception) {
        null
    }

    fun saveState(state: GameState) {
        try {
            saveTmp.writeText(StateCodec.encode(state), Charsets.UTF_8)
            if (!saveTmp.renameTo(saveFile)) {
                saveFile.delete()
                saveTmp.renameTo(saveFile)
            }
        } catch (e: Exception) {
            // Сохранение — «best effort»: игра продолжается, даже если запись не удалась.
        }
    }

    // ── Рисунок Монтика ──

    fun loadSprite(): Bitmap? = try {
        if (spriteFile.exists()) BitmapFactory.decodeFile(spriteFile.absolutePath) else null
    } catch (e: Exception) {
        null
    }

    fun saveSprite(bitmap: Bitmap) {
        try {
            spriteFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (e: Exception) {
            // ignore
        }
    }

    fun deleteSprite() {
        spriteFile.delete()
    }

    /** Новый временный файл для снимка с камеры. */
    fun newPhotoFile(): File {
        photoDir.mkdirs()
        photoDir.listFiles()?.forEach { it.delete() }
        return File(photoDir, "drawing_${System.currentTimeMillis()}.jpg")
    }

    fun clearPhotos() {
        photoDir.listFiles()?.forEach { it.delete() }
    }

    // ── PIN родителя ──

    fun hasPin(): Boolean = prefs.contains(KEY_HASH)

    fun setPin(pin: String) {
        val salt = ByteArray(8).also { SecureRandom().nextBytes(it) }.toHex()
        prefs.edit().putString(KEY_SALT, salt).putString(KEY_HASH, hash(salt, pin)).apply()
    }

    fun checkPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_SALT, null) ?: return false
        val stored = prefs.getString(KEY_HASH, null) ?: return false
        return hash(salt, pin) == stored
    }

    /** Полный сброс прогресса. PIN родителя сохраняется. */
    fun resetProgress() {
        saveFile.delete()
        saveTmp.delete()
        spriteFile.delete()
        clearPhotos()
    }

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray(Charsets.UTF_8)).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val KEY_SALT = "pin_salt"
        const val KEY_HASH = "pin_hash"
    }
}
