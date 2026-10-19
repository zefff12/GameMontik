package ru.montik.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import ru.montik.app.CreateUi
import ru.montik.app.GameViewModel
import ru.montik.app.Screen
import ru.montik.app.game.Ads
import ru.montik.app.game.Advice
import ru.montik.app.game.Barter
import ru.montik.app.game.BusinessEngine
import ru.montik.app.game.Scenario
import ru.montik.app.game.Scenarios

/**
 * Корень приложения: вступление → «Нарисуй Монтика» → игра.
 * Логики экономики здесь нет, только выбор экрана и показ диалогов.
 */
@Composable
fun MontikApp(vm: GameViewModel = viewModel()) {
    // Звуки включаются и выключаются в разделе для взрослых.
    SideEffect { Sfx.enabled = vm.state.soundOn }
    when {
        !vm.state.storySeen -> OnboardingFlow(onFinished = { vm.finishStory() })
        vm.needsCreation -> CreationHost(vm)
        else -> GameHost(vm)
    }
}

/** Ключи сцены: сон и пробуждение — не экраны, поэтому у них свои метки. */
private object SceneSleep
private object SceneWake

/** Экран создания героя и запуск камеры / галереи. */
@Composable
private fun CreationHost(vm: GameViewModel) {
    BackHandler(enabled = vm.redrawing) { vm.cancelRedraw() }
    // «Назад» во время рисования возвращает к раскраске (этот обработчик важнее предыдущего).
    BackHandler(enabled = vm.create is CreateUi.Drawing) { vm.cancelDrawing() }

    CreateScreen(vm = vm)
}

/** Игра: текущий экран + диалоги поверх него. */
@Composable
private fun GameHost(vm: GameViewModel) {
    BackHandler(enabled = vm.screen != Screen.Home) { vm.back() }

    Box(Modifier.fillMaxSize()) {
        // Экран меняется плавно: новый проявляется и чуть вырастает, старый тает.
        // Ключ — сон, пробуждение или сам экран; меняется только ключ, поэтому переход срабатывает один раз.
        val wake = vm.wake
        val scene: Any = when {
            vm.state.sleep != null -> SceneSleep
            wake != null -> SceneWake
            else -> vm.screen
        }
        AnimatedContent(
            targetState = scene,
            transitionSpec = { sceneFade() },
            label = "scene"
        ) { key ->
            when (key) {
                // Монтик спит: таймер сна, а потом будильник. Пока он спит, остальная игра ждёт.
                SceneSleep -> SleepFlow(vm)
                // Только что проснулся: стоит возле кровати, показываем итоги сна.
                SceneWake -> vm.wake?.let { WakeScreen(vm, it) }
                is Screen -> when (key) {
                    // В путешествии Монтик не дома: вместо квартиры — экран поездки.
                    Screen.Home -> if (vm.state.trip != null) TripHomeScreen(vm) else HomeScreen(vm)
                    Screen.Kitchen -> KitchenScreen(vm)
                    Screen.Budget -> BudgetScreen(vm)
                    Screen.Wallet -> NewWalletScreen(vm)
                    Screen.Goals -> GoalsScreen(vm)
                    Screen.Tasks -> TasksScreen(vm)
                    Screen.Housing -> HousingScreen(vm)
                    Screen.Glossary -> GlossaryScreen(vm)
                    Screen.Phone -> PhoneScreen(vm)
                    Screen.Work -> WorkScreen(vm)
                    Screen.Store -> StoreScreen(vm)
                    Screen.Business -> BusinessScreen(vm)
                    Screen.Shop -> ShopScreen(vm)
                    Screen.Travel -> TravelScreen(vm)
                    Screen.Sleep -> SleepScreen(vm)
                    Screen.Cushion -> CushionScreen(vm)
                    Screen.Bank -> BankScreen(vm)
                    Screen.Diary -> DiaryScreen(vm)
                    Screen.Parent -> ParentScreen(vm)
                    Screen.Grocery -> GroceryScreen(vm)
                    Screen.Fridge -> FridgeScreen(vm)
                    Screen.FridgeDoor -> FridgeDoorScreen(vm)
                    Screen.BankJob -> BankJobScreen(vm)
                    Screen.Console -> ConsoleScreen(vm)
                    Screen.Barter -> BarterScreen(vm)
                    Screen.Market -> MarketScreen(vm)
                }
            }
        }

        Overlays(vm)

        val toast = vm.toast
        if (toast != null) {
            LaunchedEffect(toast) {
                delay(4000)
                vm.clearToast()
            }
            ToastBanner(
                text = toast,
                onClick = { vm.clearToast() },
                modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding()
            )
        }
    }
}

/** Показывается один диалог за раз, по важности. */
@Composable
private fun Overlays(vm: GameViewModel) {
    // Во время сна и на экране пробуждения диалоги ждут: они появятся, когда Монтик начнёт день.
    if (vm.state.sleep != null || vm.wake != null) return
    val scenarioResult = vm.scenarioResult
    val shiftResult = vm.shiftResult
    val medal = vm.medals.firstOrNull()
    val lesson = vm.lessons.firstOrNull()
    val pending = vm.pendingScenario
    val s = vm.state
    val period = s.periods.lastOrNull()?.takeIf { it.period > s.periodSeen }
    val ad = Ads.pending(s)
    val levelUp = vm.levelUp
    // Совет дня и похвала взрослого — только на главных экранах (комната и кухня).
    val onMain = vm.screen == Screen.Home || vm.screen == Screen.Kitchen

    when {
        // Итоги первого кошелька — окна из макета (обед, игрушка, рюкзак) с настоящими суммами.
        scenarioResult != null && scenarioResult.scenario.id == Scenarios.INTRO ->
            NewWalletResult(scenarioResult, s.coins) { vm.dismissScenarioResult() }
        scenarioResult != null -> ScenarioResultDialog(scenarioResult) { vm.dismissScenarioResult() }
        shiftResult != null -> NewPayslipPopup(shiftResult) { vm.dismissShiftResult() }
        medal != null -> NewMedalPopup(medal) { vm.dismissMedal() }
        lesson != null -> NewLessonPopup(lesson) { vm.dismissLesson() }
        pending != null -> PendingScenarioOverlay(vm, pending)
        // Знакомство «три решения» — сразу после первой ситуации; потом — по кнопке «?».
        vm.helpOpen || !s.helpSeen -> HelpDialog(vm)
        // Знакомство по шагам — на главном экране, пока не пройдено (шаги хранятся в состоянии игры).
        onMain && vm.screen == Screen.Home && s.tourStep < TOUR_STEPS.size -> FirstRunTour(vm)
        period != null -> PeriodSummaryDialog(vm, period)
        levelUp != null -> NewLevelPopup(vm, levelUp)
        onMain && s.parentNoteNew -> ParentNoteOverlay(vm)
        onMain && vm.tipDue -> Advice.forToday(s).let { tip -> NewTipPopup(tip) { vm.dismissTip(tip) } }
        ad != null && vm.adVisible -> AdOverlay(vm, ad)
        BusinessEngine.offerPending(s) -> BusinessOfferDialog(vm)
        // Раз в 10 игровых дней: сообщение от енота Сергеевича — сходить на рынок.
        onMain && vm.barterInvite -> BarterInviteDialog(vm)
    }
}

/**
 * Ситуация, на которую нужно ответить до продолжения игры (вступление или событие дня).
 * Закрыть её кнопкой «назад» нельзя: выбор — часть игры.
 */
@Composable
private fun PendingScenarioOverlay(vm: GameViewModel, scenario: Scenario) {
    BackHandler(enabled = true) { }
    // «Первый кошелёк Монтика» — окно из нового макета: три варианта — кнопки из картинки.
    if (scenario.id == Scenarios.INTRO) {
        NewWalletPopup(scenario) { i -> vm.choose(scenario.id, i) }
        return
    }
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            ScenarioCard(scenario, vm.state) { index -> vm.choose(scenario.id, index) }
        }
    }
}
