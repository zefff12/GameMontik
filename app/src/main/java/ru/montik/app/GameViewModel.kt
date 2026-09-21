package ru.montik.app

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.montik.app.data.DrawingProcessor
import ru.montik.app.data.ProcessResult
import ru.montik.app.data.Storage
import ru.montik.app.game.Choice
import ru.montik.app.game.DayDiary
import ru.montik.app.game.GameEngine
import ru.montik.app.game.GameState
import ru.montik.app.game.Hero
import ru.montik.app.game.HeroPart
import ru.montik.app.game.Job
import ru.montik.app.game.Lesson
import ru.montik.app.game.Medal
import ru.montik.app.game.Outcome
import ru.montik.app.game.Payslip
import ru.montik.app.game.Rating
import ru.montik.app.game.Scenario
import ru.montik.app.game.Scenarios
import ru.montik.app.game.SleepPlace
import ru.montik.app.game.Slot
import ru.montik.app.game.WorkTask

enum class Screen { Home, Phone, Work, Shop, Travel, Sleep, Cushion, Bank, Diary, Parent }

/** Состояние экрана «Нарисуй Монтика». */
sealed interface CreateUi {
    data object Idle : CreateUi
    data object Working : CreateUi
    class Preview(val bitmap: Bitmap, val cutOk: Boolean) : CreateUi
    class Error(val message: String) : CreateUi
}

class ActiveShift(val job: Job, val task: WorkTask)
class ShiftUi(val slip: Payslip, val task: WorkTask?, val correct: Boolean?, val messages: List<String>)
class ScenarioResultUi(val scenario: Scenario, val choice: Choice, val rating: Rating, val messages: List<String>)
class MorningUi(val diary: DayDiary?, val messages: List<String>)

/**
 * Связывает игровое ядро ([GameEngine]) с интерфейсом. Логики экономики здесь нет:
 * каждое действие вызывает ядро, сохраняет результат и готовит диалоги для показа.
 */
class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = Storage(app)

    var state by mutableStateOf(storage.loadState() ?: GameEngine.newGame(newSeed()))
        private set
    var sprite by mutableStateOf<ImageBitmap?>(storage.loadSprite()?.asImageBitmap())
        private set
    var screen by mutableStateOf(Screen.Home)
        private set
    var redrawing by mutableStateOf(false)
        private set
    var create by mutableStateOf<CreateUi>(CreateUi.Idle)
        private set

    var activeShift by mutableStateOf<ActiveShift?>(null)
        private set
    var shiftResult by mutableStateOf<ShiftUi?>(null)
        private set
    var scenarioResult by mutableStateOf<ScenarioResultUi?>(null)
        private set
    var morning by mutableStateOf<MorningUi?>(null)
        private set
    val lessons = mutableStateListOf<Lesson>()

    /** Медали, заработанные только что: показываются по очереди с поздравлением. */
    val medals = mutableStateListOf<Medal>()
    var toast by mutableStateOf<String?>(null)
        private set

    var hasPin by mutableStateOf(storage.hasPin())
        private set
    var parentUnlocked by mutableStateOf(false)
        private set

    /** Имя и раскраска, пока ребёнок их придумывает: в игру они попадают по кнопке «Готово». */
    var heroName by mutableStateOf(state.heroName)
        private set
    val heroDraft = mutableStateMapOf<HeroPart, Int>().apply {
        putAll(state.heroColors.mapNotNull { (k, v) -> HeroPart.byId(k)?.let { part -> part to v } }.toMap())
    }

    private var pendingPhoto: Bitmap? = null

    /** Можно ли предложить «оставить фото как есть», если вырезание не удалось. */
    val canKeepWhole: Boolean get() = pendingPhoto != null

    /** Ситуация, которая ждёт выбора ребёнка (вступление или событие дня). */
    val pendingScenario: Scenario? get() = GameEngine.pendingScenario(state)

    val needsCreation: Boolean get() = !state.created || redrawing

    // ───────────────────────── Навигация ─────────────────────────

    fun goTo(target: Screen) {
        screen = target
        if (target != Screen.Parent) parentUnlocked = false
        if (target != Screen.Work) activeShift = null
    }

    fun back() = goTo(Screen.Home)

    fun clearToast() {
        toast = null
    }

    // ───────────────────────── Общее применение результата ─────────────────────────

    private fun commit(outcome: Outcome, showMessages: Boolean = true) {
        if (outcome.ok) {
            state = outcome.state
            storage.saveState(state)
            for (lesson in outcome.lessons) if (lesson !in lessons) lessons.add(lesson)
            for (medal in outcome.medals) if (medal !in medals) medals.add(medal)
        }
        if (showMessages && outcome.messages.isNotEmpty()) toast = outcome.messages.joinToString("\n")
    }

    private fun updateState(s: GameState) {
        state = s
        storage.saveState(s)
    }

    // ───────────────────────── Магазин ─────────────────────────

    fun buyFood(id: String) = commit(GameEngine.buyFood(state, id))
    fun buyClothing(id: String) = commit(GameEngine.buyClothing(state, id))
    fun wear(id: String) = commit(GameEngine.wear(state, id), showMessages = false)
    fun takeOff(slot: Slot) = commit(GameEngine.takeOff(state, slot), showMessages = false)

    // ───────────────────────── Работа ─────────────────────────

    /** Почему Монтик сейчас не может работать (null — может). */
    val workBlocker: String? get() = GameEngine.workBlocker(state)

    /** Нужно ли предложить мелкое поручение: денег нет даже на еду. */
    val choreAvailable: Boolean get() = GameEngine.choreAvailable(state)

    fun doChore() = commit(GameEngine.doChore(state))

    fun startShift(job: Job) {
        val status = GameEngine.jobStatus(state, job)
        if (!status.unlocked) {
            toast = "Пока недоступно: ${status.hint}"
            return
        }
        val blocker = GameEngine.workBlocker(state)
        if (blocker != null) {
            toast = blocker
            return
        }
        activeShift = ActiveShift(job, GameEngine.makeTask(state, job))
    }

    fun answerTask(index: Int) {
        val shift = activeShift ?: return
        finishShift(shift, index == shift.task.correctIndex)
    }

    fun skipTask() {
        val shift = activeShift ?: return
        finishShift(shift, null)
    }

    fun cancelShift() {
        activeShift = null
    }

    private fun finishShift(shift: ActiveShift, correct: Boolean?) {
        val res = GameEngine.work(state, shift.job, correct)
        activeShift = null
        commit(res.outcome, showMessages = false)
        val slip = res.payslip
        if (res.outcome.ok && slip != null) {
            shiftResult = ShiftUi(slip, shift.task, correct, res.outcome.messages)
        } else {
            toast = res.outcome.messages.joinToString("\n")
        }
    }

    fun dismissShiftResult() {
        shiftResult = null
    }

    // ───────────────────────── Сон ─────────────────────────

    fun sleep(place: SleepPlace) {
        val outcome = GameEngine.sleep(state, place)
        commit(outcome, showMessages = false)
        morning = MorningUi(outcome.state.diary, outcome.messages)
        screen = Screen.Home
    }

    fun dismissMorning() {
        morning = null
    }

    // ───────────────────────── Подушка безопасности ─────────────────────────

    fun deposit(amount: Int) = commit(GameEngine.depositCushion(state, amount))
    fun withdraw(amount: Int) = commit(GameEngine.withdrawCushion(state, amount))

    // ───────────────────────── Банк ─────────────────────────

    fun bankDeposit(amount: Int) = commit(GameEngine.depositToBank(state, amount))
    fun bankWithdraw(amount: Int) = commit(GameEngine.withdrawFromBank(state, amount))
    fun takeLoan() = commit(GameEngine.takeLoan(state))
    fun repayLoan(amount: Int) = commit(GameEngine.repayLoan(state, amount))

    // ───────────────────────── Ситуации и путешествия ─────────────────────────

    fun choose(scenarioId: String, index: Int) {
        val res = GameEngine.choose(state, scenarioId, index)
        commit(res.outcome, showMessages = false)
        val scenario = Scenarios.byId(scenarioId)
        val choice = res.choice
        val rating = res.rating
        if (res.outcome.ok && scenario != null && choice != null && rating != null) {
            scenarioResult = ScenarioResultUi(scenario, choice, rating, res.outcome.messages)
        } else if (res.outcome.messages.isNotEmpty()) {
            toast = res.outcome.messages.joinToString("\n")
        }
    }

    fun dismissScenarioResult() {
        scenarioResult = null
    }

    fun startTrip(destinationId: String) = commit(GameEngine.startTrip(state, destinationId))

    fun dismissLesson() {
        if (lessons.isNotEmpty()) lessons.removeAt(0)
    }

    fun dismissMedal() {
        if (medals.isNotEmpty()) medals.removeAt(0)
    }

    fun finishStory() = updateState(state.copy(storySeen = true))

    // ───────────────────────── Создание Монтика ─────────────────────────

    /** Адрес файла, в который приложение «Камера» запишет снимок рисунка. */
    fun newCameraUri(): Uri {
        val file = storage.newPhotoFile()
        val context = getApplication<Application>()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun processPhoto(uri: Uri) {
        create = CreateUi.Working
        viewModelScope.launch {
            val app = getApplication<Application>()
            val result = withContext(Dispatchers.Default) {
                val photo = DrawingProcessor.loadPhoto(app, uri)
                if (photo == null) {
                    Pair<Bitmap?, ProcessResult>(null, ProcessResult.Error("Не удалось открыть фото. Попробуй ещё раз."))
                } else {
                    Pair<Bitmap?, ProcessResult>(photo, DrawingProcessor.cutout(photo))
                }
            }
            pendingPhoto = result.first
            create = when (val r = result.second) {
                is ProcessResult.Ok -> CreateUi.Preview(r.bitmap, cutOk = true)
                is ProcessResult.Error -> CreateUi.Error(r.message)
            }
        }
    }

    fun photoFailed() {
        create = CreateUi.Error("Фото не получилось. Попробуй ещё раз или выбери картинку из галереи.")
    }

    /** Если вырезание не удалось, можно оставить весь снимок как есть. */
    fun keepWholePhoto() {
        val photo = pendingPhoto ?: return
        create = CreateUi.Preview(DrawingProcessor.wholePhoto(photo), cutOk = false)
    }

    fun retake() {
        create = CreateUi.Idle
        pendingPhoto = null
    }

    // ───────────────────────── Раскраска героя ─────────────────────────

    /** Имя нельзя называть setHeroName: такое имя уже занято свойством heroName. */
    fun changeHeroName(raw: String) {
        heroName = raw.take(Hero.MAX_NAME)
    }

    fun paintHero(part: HeroPart, rgb: Int) {
        heroDraft[part] = rgb
    }

    fun randomColours() {
        for (part in HeroPart.values()) heroDraft[part] = Hero.PALETTE.random()
    }

    /** Раскраска готова: имя и цвета сохраняются, игра начинается. */
    fun confirmColouring() {
        storage.deleteSprite()
        sprite = null
        state = state.copy(
            heroName = Hero.cleanName(heroName),
            heroColors = heroDraft.entries.associate { it.key.id to it.value }
        )
        heroName = state.heroName
        finishCreation()
    }

    fun confirmSprite() {
        val preview = create as? CreateUi.Preview ?: return
        storage.saveSprite(preview.bitmap)
        sprite = preview.bitmap.asImageBitmap()
        finishCreation()
    }

    /** Готовый Монтик без фотографии (камера для обязательного сценария не нужна). */
    fun useDefaultMontik() {
        storage.deleteSprite()
        sprite = null
        finishCreation()
    }

    fun startRedraw() {
        create = CreateUi.Idle
        redrawing = true
    }

    fun cancelRedraw() {
        redrawing = false
        create = CreateUi.Idle
    }

    private fun finishCreation() {
        create = CreateUi.Idle
        pendingPhoto = null
        redrawing = false
        storage.clearPhotos()
        updateState(GameEngine.markCreated(state))
    }

    // ───────────────────────── Родитель ─────────────────────────

    fun openParent() {
        screen = Screen.Parent
        parentUnlocked = false
    }

    fun createPin(pin: String) {
        storage.setPin(pin)
        hasPin = true
        parentUnlocked = true
    }

    fun unlockParent(pin: String): Boolean {
        val ok = storage.checkPin(pin)
        if (ok) parentUnlocked = true
        return ok
    }

    fun changePin(pin: String) {
        storage.setPin(pin)
        toast = "Новый PIN сохранён."
    }

    /** Полный сброс прогресса (PIN родителя остаётся). */
    fun resetProgress() {
        storage.resetProgress()
        state = GameEngine.newGame(newSeed())
        sprite = null
        lessons.clear()
        medals.clear()
        activeShift = null
        shiftResult = null
        scenarioResult = null
        morning = null
        create = CreateUi.Idle
        pendingPhoto = null
        redrawing = false
        parentUnlocked = false
        heroName = state.heroName
        heroDraft.clear()
        screen = Screen.Home
        toast = "Прогресс удалён. Можно начать сначала."
    }

    private companion object {
        fun newSeed(): Long = System.nanoTime() xor System.currentTimeMillis()
    }
}
