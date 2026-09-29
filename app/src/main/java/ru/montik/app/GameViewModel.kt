package ru.montik.app

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.montik.app.data.DrawStroke
import ru.montik.app.data.DrawingExport
import ru.montik.app.data.Storage
import ru.montik.app.data.TrustedClock
import ru.montik.app.game.Ads
import ru.montik.app.game.BankWork
import ru.montik.app.game.Barter
import ru.montik.app.game.ConsoleGame
import ru.montik.app.game.Credits
import ru.montik.app.game.Grocery
import ru.montik.app.game.Advice
import ru.montik.app.game.Praise
import ru.montik.app.game.Progress
import ru.montik.app.game.Travel
import ru.montik.app.game.Talk
import ru.montik.app.game.Tip
import ru.montik.app.game.BusinessEngine
import ru.montik.app.game.Choice
import ru.montik.app.game.DayDiary
import ru.montik.app.game.GameEngine
import ru.montik.app.game.GameState
import ru.montik.app.game.Goals
import ru.montik.app.game.Hero
import ru.montik.app.game.HeroPart
import ru.montik.app.game.HeroPreset
import ru.montik.app.game.Job
import ru.montik.app.game.Lesson
import ru.montik.app.game.Life
import ru.montik.app.game.Medal
import ru.montik.app.game.Outcome
import ru.montik.app.game.Payslip
import ru.montik.app.game.Rating
import ru.montik.app.game.Scenario
import ru.montik.app.game.Scenarios
import ru.montik.app.game.ShopGame
import ru.montik.app.game.ShopWork
import ru.montik.app.game.SleepPlace
import ru.montik.app.game.SleepPlan
import ru.montik.app.game.SleepQuality
import ru.montik.app.game.Skins
import ru.montik.app.game.Slot
import ru.montik.app.game.TaskOutcome
import ru.montik.app.game.Tasks
import ru.montik.app.game.WorkTask

enum class Screen {
    Home, Kitchen, Phone, Work, Store, Business, Shop, Travel, Sleep, Cushion, Bank, Diary, Parent,
    Budget, Goals, Tasks, Housing, Glossary,
    /** Магазин продуктов, холодильник, работа в банке, приставка. */
    Grocery, Fridge, BankJob, Console,
    /** Закрытый холодильник с магнитами из поездок (кадр Frame 38). */
    FridgeDoor,
    /** Енот на рынке: учим справедливому обмену. */
    Barter
}

/** Состояние экрана «Нарисуй Монтика». */
sealed interface CreateUi {
    data object Idle : CreateUi
    data object Working : CreateUi
    /** Свободное рисование пальцем. */
    data object Drawing : CreateUi
    /** Предпросмотр героя, нарисованного в приложении. */
    class Preview(val bitmap: Bitmap, val cutOk: Boolean, val drawn: Boolean = false) : CreateUi
    class Error(val message: String) : CreateUi
}

class ActiveShift(val job: Job, val task: WorkTask)

/** Что празднуем на экране путешествия. */
sealed interface TripCelebration {
    /** 🏆 «Ура! Открыт новый город!» — только при первом прилёте. */
    class NewCity(val destinationId: String) : TripCelebration

    /** 🏆 «Получил новый опыт»: +100 опыта, уровень профессионала и заработок до и после. */
    class Conference(
        val destinationId: String,
        val conferencesBefore: Int,
        val conferencesAfter: Int,
        val xpLevelBefore: Int,
        val xpLevelAfter: Int
    ) : TripCelebration
}
class ShiftUi(val slip: Payslip, val task: WorkTask?, val correct: Boolean?, val messages: List<String>)
class ScenarioResultUi(val scenario: Scenario, val choice: Choice, val rating: Rating, val messages: List<String>)
class MorningUi(val diary: DayDiary?, val messages: List<String>)

/** Итог сна для экрана «Монтик проснулся»: сколько проспал и как это сказалось на силах. */
class WakeUi(
    val sleptMinutes: Int,
    val quality: SleepQuality,
    val energy: Int,
    val day: Int,
    val diary: DayDiary?,
    val messages: List<String>
)

/**
 * Связывает игровое ядро ([GameEngine]) с интерфейсом. Логики экономики здесь нет:
 * каждое действие вызывает ядро, сохраняет результат и готовит диалоги для показа.
 */
class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = Storage(app)

    init {
        // «Честные» часы: время игры нельзя ускорить, переведя часы телефона.
        TrustedClock.init(app)
    }

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
    /** Экран после пробуждения: Монтик стоит возле кровати. Показывается, пока его не закроют. */
    var wake by mutableStateOf<WakeUi?>(null)
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
    /** Заготовка, с которой ребёнок начинает раскраску (сначала — белый человечек). */
    var heroPresetDraft by mutableStateOf(state.preset)
        private set
    /** Выбранный на экране создания готовый Монтик-картинка (null — раскраска заготовки). */
    var skinDraft by mutableStateOf(state.heroSkin)
        private set

    val heroDraft = mutableStateMapOf<HeroPart, Int>().apply {
        putAll(state.heroColors.mapNotNull { (k, v) -> HeroPart.byId(k)?.let { part -> part to v } }.toMap())
    }

    /** Ситуация, которая ждёт выбора ребёнка (вступление или событие дня). */
    val pendingScenario: Scenario? get() = GameEngine.pendingScenario(state)

    val needsCreation: Boolean get() = !state.created || redrawing

    init {
        // Виртуальные часы Монтика идут по настоящему времени, в том числе пока игра закрыта.
        updateState(GameEngine.startClock(state, nowMs()))
    }

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

    /** Показать короткое сообщение внизу экрана. */
    fun say(text: String) {
        toast = text
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

    fun buyJoy(id: String) = commit(GameEngine.buyJoy(state, id))
    fun eatMeal(id: String) = commit(GameEngine.eatMeal(state, id))

    // ───────────────────────── Магазин продуктов и холодильник ─────────────────────────

    /** Корзина магазина продуктов — черновик покупки, не сохраняется. */
    var cart by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    fun cartAdd(id: String) { cart = Grocery.add(cart, id) }
    fun cartRemove(id: String) { cart = Grocery.remove(cart, id) }
    fun cartDrop(id: String) { cart = cart - id }
    fun cartClear() { cart = emptyMap() }

    /** Оплатить корзину: true — куплено (продукты в холодильнике). */
    fun checkout(): Boolean {
        val out = Grocery.checkout(state, cart)
        commit(out)
        if (out.ok) cart = emptyMap()
        return out.ok
    }

    /** Взять продукт из холодильника: true — съеден. */
    fun eatFromFridge(id: String): Boolean {
        val out = Grocery.eat(state, id)
        commit(out)
        return out.ok
    }

    // ───────────────────────── Банк и приставка ─────────────────────────

    fun finishBankShift(performance: Int, details: List<String>) {
        val res = BankWork.work(state, performance, details)
        commit(res.outcome, showMessages = false)
        val slip = res.payslip
        if (res.outcome.ok && slip != null) {
            shiftResult = ShiftUi(slip, null, null, res.outcome.messages)
        } else {
            toast = res.outcome.messages.joinToString("\n")
        }
    }

    fun finishRace(score: Int) = commit(ConsoleGame.finishRace(state, score))
    fun skipMeal() = commit(GameEngine.skipMeal(state))

    // ───────────────────────── Квартира, план, уровни ─────────────────────────

    fun payRentAhead() = commit(Life.payRentAhead(state))
    fun payRentDebt() = commit(Life.payRentDebt(state))
    fun moveTo(level: Int) = commit(Life.moveTo(state, level))

    /** Сохранить план (черновик или подтверждённый). Возвращает true, если получилось. */
    fun setPlan(needs: Int, wants: Int, savings: Int, confirm: Boolean): Boolean {
        val outcome = Life.setPlan(state, needs, wants, savings, confirm)
        commit(outcome, showMessages = confirm || !outcome.ok)
        return outcome.ok
    }

    fun markPeriodSeen() = updateState(GameEngine.markPeriodSeen(state))
    fun markHelpSeen() = updateState(GameEngine.markHelpSeen(state))

    /** Подсказку «три решения» можно открыть в любой момент кнопкой «?». */
    var helpOpen by mutableStateOf(false)
        private set

    fun openHelp() {
        helpOpen = true
    }

    fun closeHelp() {
        helpOpen = false
        if (!state.helpSeen) markHelpSeen()
    }

    // ───────────────────────── Копилка ─────────────────────────

    fun chooseGoal(id: String) = commit(Goals.choose(state, id))
    fun piggyIn(amount: Int) = commit(Goals.deposit(state, amount))
    fun piggyOut(amount: Int) = commit(Goals.withdraw(state, amount))
    fun reachGoal() = commit(Goals.reach(state))

    // ───────────────────────── Задания и реклама ─────────────────────────

    fun completeTask(taskId: String, result: TaskOutcome) = commit(Tasks.complete(state, taskId, result))

    /** Реклама показывается на главном экране и на кухне, когда Монтик не спит. */
    val adVisible: Boolean
        get() = state.pendingAd != null && state.sleep == null && wake == null &&
            (screen == Screen.Home || screen == Screen.Kitchen)

    fun declineAd() = commit(Ads.decline(state))
    fun buyAd() = commit(Ads.buy(state))

    // ───────────────────────── Демо-режим ─────────────────────────

    fun setDemo(on: Boolean) {
        updateState(GameEngine.setDemo(state, on))
        toast = if (on) "Демо-режим включён: на главном экране появилась кнопка «Следующий период»." else "Демо-режим выключен."
    }

    fun skipPeriod() = commit(GameEngine.skipToNextPeriod(state))

    // ───────────────────────── Живость: уровень, советы, похвала, звук ─────────────────────────

    /** Сколько монет уже показано на главном экране: если стало больше — монетки «летят» в кошелёк. */
    var hudCoins: Int = state.coins

    /** Новый уровень Монтика, который ещё не поздравили (null — нет). */
    val levelUp: Int? get() = Progress.levelUpPending(state)

    fun markLevelSeen() = updateState(Progress.markLevelSeen(state))

    /** Пора ли показать «Совет от Лобачевского» (раз в игровой день). */
    val tipDue: Boolean get() = Advice.due(state)

    fun dismissTip(tip: Tip) = updateState(Advice.markSeen(state, tip))

    /** Что Монтик скажет, если на него нажать. */
    fun phrases(): List<String> = Talk.phrases(state)

    /** Взрослый оставляет сообщение ребёнку (из раздела для взрослых). */
    fun sendPraise(text: String) {
        val next = Praise.send(state, text)
        if (next != state) {
            updateState(next)
            toast = "Сообщение отправлено: ребёнок увидит его на главном экране."
        }
    }

    fun readPraise() = commit(Praise.read(state), showMessages = false)

    fun setSound(on: Boolean) = updateState(state.copy(soundOn = on))

    /** Тестовый профиль для экспертов: чистая игра без вступления, сразу с героем, демо-режим включён. */
    fun resetTestProfile() {
        resetProgress()
        val fresh = GameEngine.markCreated(state).copy(storySeen = true, demo = true, coins = 300)
        updateState(fresh)
        hudCoins = fresh.coins
        resetDraft()
        toast = "Тестовый профиль готов: 300 монет, демо-режим включён."
    }

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

    // ───────────────────────── Работа в магазине ─────────────────────────

    /** Почему в магазин сейчас нельзя (null — можно): не набрано смен или Монтик не в форме. */
    val storeBlocker: String?
        get() = ShopWork.lockHint(state)?.let { "Магазин откроется позже: $it." } ?: GameEngine.workBlocker(state)

    /** Мини-игра закончена: ядро считает смену по оценке [performance] (0..100), а ребёнок видит расчётный листок. */
    fun finishShopShift(game: ShopGame, performance: Int, details: List<String>) {
        val res = GameEngine.workShop(state, game, performance, details)
        commit(res.outcome, showMessages = false)
        val slip = res.payslip
        if (res.outcome.ok && slip != null) {
            shiftResult = ShiftUi(slip, null, null, res.outcome.messages)
        } else {
            toast = res.outcome.messages.joinToString("\n")
        }
    }

    // ───────────────────────── Свой бизнес ─────────────────────────

    /** «Открыть свой бизнес»: путь к магазину начинается, открывается экран бизнеса. */
    fun bizBegin() {
        commit(BusinessEngine.begin(state))
        if (state.business != null) screen = Screen.Business
    }

    /** «Пока рано»: идея больше не всплывает сама, но остаётся на экране «Работа». */
    fun bizPostpone() = commit(BusinessEngine.postpone(state), showMessages = false)

    fun bizRentPlace(id: String) = commit(BusinessEngine.rentPlace(state, id))
    fun bizChooseType(id: String) = commit(BusinessEngine.chooseType(state, id))
    fun bizBuild() = commit(BusinessEngine.build(state))
    fun bizEquip(level: Int) = commit(BusinessEngine.buyEquipment(state, level))
    fun bizStock(baskets: Int) = commit(BusinessEngine.buyStock(state, baskets))
    fun bizExpand() = commit(BusinessEngine.expand(state))
    fun bizAdvertise() = commit(BusinessEngine.advertise(state))
    fun bizRepair() = commit(BusinessEngine.repair(state))
    fun bizCollect() = commit(BusinessEngine.collect(state))

    // ───────────────────────── Сон ─────────────────────────

    /** Настоящее время по «честным» часам (см. [TrustedClock]): перевод часов телефона не помогает. */
    private fun nowMs(): Long = TrustedClock.now()

    /** Когда Монтик ляжет и когда прозвенит будильник, если уложить его сейчас (для экрана выбора ночлега). */
    fun sleepPlan(nowMs: Long): SleepPlan = GameEngine.planSleep(state, nowMs)

    /** Уложить Монтика: дальше сон идёт по виртуальным часам, даже когда игра закрыта. */
    fun goToBed(place: SleepPlace) {
        val outcome = GameEngine.beginSleep(state, place, nowMs())
        commit(outcome)
        if (outcome.ok) {
            wake = null
            screen = Screen.Home
        }
    }

    /** «Отложить на 15 минут»: Монтик спит дальше, будильник зазвонит снова. */
    fun snoozeAlarm() = commit(GameEngine.snooze(state, nowMs()), showMessages = false)

    /**
     * «Выключить» (или «Разбудить сейчас», если Монтика поднимают раньше будильника):
     * считаем, сколько он проспал, и показываем экран, где он уже стоит возле кровати.
     */
    fun wakeUp() {
        val now = nowMs()
        val slept = GameEngine.sleepStatus(state, now)?.sleptMinutes ?: return
        val outcome = GameEngine.wakeUp(state, now)
        commit(outcome, showMessages = false)
        if (!outcome.ok) return
        val s = outcome.state
        wake = WakeUi(
            sleptMinutes = slept,
            quality = s.sleepQuality,
            energy = s.energy,
            day = s.day,
            diary = s.diary,
            messages = outcome.messages
        )
        screen = Screen.Home
    }

    fun dismissWake() {
        wake = null
    }

    // ───────────────────────── Подушка безопасности ─────────────────────────

    fun deposit(amount: Int) = commit(GameEngine.depositCushion(state, amount))
    fun withdraw(amount: Int) = commit(GameEngine.withdrawCushion(state, amount))

    // ───────────────────────── Банк ─────────────────────────

    fun bankDeposit(amount: Int) = commit(GameEngine.depositToBank(state, amount))
    fun bankWithdraw(amount: Int) = commit(GameEngine.withdrawFromBank(state, amount))
    fun takeLoan() = commit(GameEngine.takeLoan(state))
    fun repayLoan(amount: Int) = commit(GameEngine.repayLoan(state, amount))

    // ───────────────────────── Рынок енота Сергеевича (бартер) ─────────────────────────

    /** «Позже» на приглашении: до перезапуска игры сообщение не всплывает (рынок открыт из телефона). */
    var barterInviteHidden by mutableStateOf(false)
        private set

    /** Показать ли приглашение на рынок (раз в 10 игровых дней). */
    val barterInvite: Boolean
        get() = Barter.due(state) && !barterInviteHidden && screen != Screen.Barter

    fun hideBarterInvite() {
        barterInviteHidden = true
    }

    fun openMarket() {
        barterInviteHidden = true
        goTo(Screen.Barter)
    }

    /** Предложить еноту обмен. true — сделка состоялась. Ответ енота показывает сам экран рынка. */
    fun barterTrade(wantId: String, offer: Map<String, Int>): Boolean {
        val out = Barter.trade(state, wantId, offer)
        commit(out, showMessages = false)
        return out.ok
    }

    /** Уйти с рынка: если обмена не было, следующее приглашение — через 10 дней. */
    fun leaveMarket(traded: Boolean) {
        if (!traded && Barter.due(state)) updateState(Barter.leave(state))
        back()
    }

    // ───────────────────────── Кредиты ─────────────────────────

    fun takeCredit(productId: String) = commit(Credits.take(state, productId))
    fun payCredit(creditId: String, amount: Int) = commit(Credits.pay(state, creditId, amount))

    // ───────────────────────── Чит-панель (раздел для взрослых) ─────────────────────────

    fun cheatCoins(amount: Int) {
        updateState(state.copy(coins = (state.coins + amount).coerceAtLeast(0)))
        hudCoins = state.coins
        toast = "Чит: монет теперь ${state.coins}."
    }

    /** Промотать игровые дни: Монтик ночует дома сытым, всё остальное (аренда, кредиты) считается честно. */
    fun cheatSkipDays(days: Int) {
        if (state.sleep != null) {
            toast = "Монтик спит — сначала разбуди его."
            return
        }
        val now = nowMs()
        // Опорная точка часов — «сейчас», чтобы мгновенные ночи не прибавили лишнего времени.
        var s = state.copy(clockMinutes = ru.montik.app.game.VirtualClock.now(state, now), clockStamp = now)
        repeat(days) {
            val night = GameEngine.sleep(s.copy(food = 100, water = 100, energy = 100, pendingEvent = null), SleepPlace.CABIN)
            if (night.ok) s = night.state
        }
        updateState(s)
        toast = "Чит: сейчас день ${state.day}."
    }

    fun cheatBarterNow() {
        barterInviteHidden = false
        updateState(state.copy(barterDay = state.day - Barter.INTERVAL_DAYS))
        toast = "Чит: енот Сергеевич ждёт на рынке."
    }

    fun cheatClearCredits() {
        updateState(state.copy(credits = emptyList(), debt = 0))
        toast = "Чит: кредиты и долг обнулены."
    }

    fun cheatUnlockSkins() {
        val all = ru.montik.app.game.HeroPreset.values().map { it.id } + Hero.PALETTE.map { Skins.colorKey(it) } +
            Skins.pictures.map { it.id }
        updateState(state.copy(ownedSkins = state.ownedSkins + all))
        toast = "Чит: все скины и краски открыты."
    }

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

    fun startTrip(destinationId: String) {
        val out = GameEngine.startTrip(state, destinationId)
        commit(out, showMessages = false)
        if (!out.ok) toast = out.messages.joinToString("\n")
    }

    /** Праздник на экране путешествия: открыт новый город или пройдена конференция. */
    var tripCelebration by mutableStateOf<TripCelebration?>(null)
        private set

    fun dismissTripCelebration() {
        tripCelebration = null
    }

    /** Самолёт приземлился в городе. */
    fun landTrip() {
        val trip = state.trip ?: return
        val isNew = trip.destinationId !in state.visitedCities
        val out = Travel.land(state)
        commit(out, showMessages = false)
        if (out.ok && isNew) tripCelebration = TripCelebration.NewCity(trip.destinationId)
    }

    /** Ужин в кафе на прогулке; true — поужинал. */
    fun eatAtCafe(): Boolean {
        val out = Travel.eatAtCafe(state)
        commit(out)
        return out.ok
    }

    /** Бизнес-конференция: опыт, уровень профессионала и рост заработка. */
    fun attendConference() {
        val trip = state.trip ?: return
        val before = state.conferences
        val xpLevelBefore = Progress.levelOf(state.xp)
        val out = Travel.attendConference(state)
        commit(out, showMessages = false)
        if (out.ok) {
            tripCelebration = TripCelebration.Conference(
                destinationId = trip.destinationId,
                conferencesBefore = before,
                conferencesAfter = state.conferences,
                xpLevelBefore = xpLevelBefore,
                xpLevelAfter = Progress.levelOf(state.xp)
            )
        } else {
            toast = out.messages.joinToString("\n")
        }
    }

    /** Самолёт привёз Монтика домой. */
    fun flyHome() {
        val out = Travel.flyHome(state)
        commit(out)
        if (out.ok) goTo(Screen.Home)
    }

    fun dismissLesson() {
        if (lessons.isNotEmpty()) lessons.removeAt(0)
    }

    fun dismissMedal() {
        if (medals.isNotEmpty()) medals.removeAt(0)
    }

    fun finishStory() = updateState(state.copy(storySeen = true))

    // ───────────────────────── Создание Монтика ─────────────────────────

    /** Вернуться с предпросмотра к раскраске. */
    fun retake() {
        create = CreateUi.Idle
    }

    // ───────────────────────── Рисование героя ─────────────────────────

    /** Мазки рисунка. Хранятся здесь, чтобы не пропасть, если экран повернули или закрыли на минуту. */
    val drawing = mutableStateListOf<DrawStroke>()

    fun startDrawing() {
        create = CreateUi.Drawing
    }

    /** Вернуться к раскраске; нарисованное остаётся, к нему можно вернуться. */
    fun cancelDrawing() {
        create = CreateUi.Idle
    }

    fun addStroke(stroke: DrawStroke) {
        drawing.add(stroke)
    }

    fun undoStroke() {
        if (drawing.isNotEmpty()) drawing.removeAt(drawing.lastIndex)
    }

    fun clearDrawing() {
        drawing.clear()
    }

    /**
     * Готовит героя из рисунка и открывает предпросмотр.
     * Возвращает false, если рисовать было нечего (пустой холст).
     */
    fun finishDrawing(): Boolean {
        val bitmap = DrawingExport.render(drawing.toList()) ?: return false
        create = CreateUi.Preview(bitmap, cutOk = true, drawn = true)
        return true
    }

    // ───────────────────────── Раскраска героя ─────────────────────────

    /** Имя нельзя называть setHeroName: такое имя уже занято свойством heroName. */
    fun changeHeroName(raw: String) {
        heroName = raw.take(Hero.MAX_NAME)
    }

    fun paintHero(part: HeroPart, rgb: Int) {
        // Раскрашивать можно заготовку: если выбран готовый Монтик-картинка, переходим к раскраске.
        skinDraft = null
        heroDraft[part] = rgb
    }

    /** Выбор заготовки (белый человечек, синий, зелёный…): раскраска начинается с неё заново. */
    fun choosePreset(preset: HeroPreset) {
        if (!Skins.owns(state, preset)) {
            toast = "Этот Монтик стоит ${Skins.price(preset)} монет — сначала открой его."
            return
        }
        heroPresetDraft = preset
        heroDraft.clear()
        skinDraft = null
    }

    /** Выбрать готового Монтика-картинку (если куплен) или предложить купить. */
    fun chooseSkin(p: Skins.Picture) {
        if (!Skins.ownsPicture(state, p)) {
            toast = "Этот Монтик стоит ${p.price} монет — сначала открой его."
            return
        }
        skinDraft = p.id
    }

    fun buySkin(p: Skins.Picture) {
        val out = Skins.buyPicture(state, p)
        commit(out)
        if (out.ok) {
            hudCoins = state.coins
            skinDraft = p.id
        }
    }

    fun presetOwned(preset: HeroPreset): Boolean = Skins.owns(state, preset)
    fun colourOwned(rgb: Int): Boolean = Skins.ownsColor(state, rgb)

    /** Купить заготовку Монтика за монеты и сразу выбрать её. */
    fun buyPreset(preset: HeroPreset) {
        val out = Skins.buy(state, preset)
        commit(out)
        if (out.ok) {
            hudCoins = state.coins
            heroPresetDraft = preset
            heroDraft.clear()
        }
    }

    /** Купить краску. true — куплена. */
    fun buyColour(rgb: Int): Boolean {
        val out = Skins.buyColor(state, rgb)
        commit(out)
        if (out.ok) hudCoins = state.coins
        return out.ok
    }

    /** Возвращает имя и раскраску к тому, что сохранено в игре (например, при отмене перерисовки). */
    private fun resetDraft() {
        heroName = state.heroName
        heroPresetDraft = state.preset
        skinDraft = state.heroSkin
        heroDraft.clear()
        for ((id, rgb) in state.heroColors) HeroPart.byId(id)?.let { heroDraft[it] = rgb }
    }

    fun randomColours() {
        val colours = Hero.PALETTE.filter { Skins.ownsColor(state, it) }
        for (part in HeroPart.values()) heroDraft[part] = colours.random()
    }

    /** Раскраска готова: имя и цвета сохраняются, игра начинается. */
    fun confirmColouring() {
        if (!Skins.owns(state, heroPresetDraft)) {
            toast = "Этот Монтик ещё не открыт."
            return
        }
        storage.deleteSprite()
        sprite = null
        state = state.copy(
            heroName = Hero.cleanName(heroName),
            heroPreset = heroPresetDraft.id,
            heroColors = heroDraft.entries.associate { it.key.id to it.value },
            heroSkin = skinDraft?.takeIf { id -> Skins.picture(id)?.let { Skins.ownsPicture(state, it) } == true }
        )
        heroName = state.heroName
        finishCreation()
    }

    fun confirmSprite() {
        val preview = create as? CreateUi.Preview ?: return
        storage.saveSprite(preview.bitmap)
        sprite = preview.bitmap.asImageBitmap()
        // Имя, которое ребёнок придумал на экране создания, сохраняется и с рисунком.
        state = state.copy(heroName = Hero.cleanName(heroName), heroSkin = null)
        heroName = state.heroName
        drawing.clear()
        finishCreation()
    }

    /** Готовый Монтик без фотографии (камера для обязательного сценария не нужна). */
    fun useDefaultMontik() {
        storage.deleteSprite()
        sprite = null
        finishCreation()
    }

    fun startRedraw() {
        resetDraft()
        create = CreateUi.Idle
        redrawing = true
    }

    fun cancelRedraw() {
        redrawing = false
        create = CreateUi.Idle
        resetDraft()
    }

    private fun finishCreation() {
        create = CreateUi.Idle
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
        state = GameEngine.startClock(GameEngine.newGame(newSeed()), nowMs())
        hudCoins = state.coins
        sprite = null
        lessons.clear()
        medals.clear()
        activeShift = null
        shiftResult = null
        scenarioResult = null
        wake = null
        create = CreateUi.Idle
        redrawing = false
        parentUnlocked = false
        barterInviteHidden = false
        helpOpen = false
        resetDraft()
        screen = Screen.Home
        toast = "Прогресс удалён. Можно начать сначала."
    }

    private companion object {
        fun newSeed(): Long = System.nanoTime() xor System.currentTimeMillis()
    }
}
