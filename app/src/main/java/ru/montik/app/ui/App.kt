package ru.montik.app.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import ru.montik.app.GameViewModel
import ru.montik.app.Screen
import ru.montik.app.game.Scenario

/**
 * Корень приложения: вступление → «Нарисуй Монтика» → игра.
 * Логики экономики здесь нет, только выбор экрана и показ диалогов.
 */
@Composable
fun MontikApp(vm: GameViewModel = viewModel()) {
    when {
        !vm.state.storySeen -> OnboardingFlow(onFinished = { vm.finishStory() })
        vm.needsCreation -> CreationHost(vm)
        else -> GameHost(vm)
    }
}

/** Экран создания героя и запуск камеры / галереи. */
@Composable
private fun CreationHost(vm: GameViewModel) {
    // Адрес снимка переживает пересоздание, пока открыта «Камера».
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        // Если ребёнок закрыл камеру без снимка, просто остаёмся на экране выбора.
        if (saved && uri != null) vm.processPhoto(uri)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.processPhoto(uri)
    }

    BackHandler(enabled = vm.redrawing) { vm.cancelRedraw() }

    CreateScreen(
        vm = vm,
        onCamera = {
            try {
                val uri = vm.newCameraUri()
                cameraUri = uri
                camera.launch(uri)
            } catch (e: Exception) {
                // Нет приложения «Камера» или не удалось создать файл: предлагаем галерею.
                vm.photoFailed()
            }
        },
        onGallery = {
            try {
                gallery.launch("image/*")
            } catch (e: Exception) {
                vm.photoFailed()
            }
        }
    )
}

/** Игра: текущий экран + диалоги поверх него. */
@Composable
private fun GameHost(vm: GameViewModel) {
    BackHandler(enabled = vm.screen != Screen.Home) { vm.back() }

    Box(Modifier.fillMaxSize()) {
        when (vm.screen) {
            Screen.Home -> HomeScreen(vm)
            Screen.Phone -> PhoneScreen(vm)
            Screen.Work -> WorkScreen(vm)
            Screen.Shop -> ShopScreen(vm)
            Screen.Travel -> TravelScreen(vm)
            Screen.Sleep -> SleepScreen(vm)
            Screen.Cushion -> CushionScreen(vm)
            Screen.Bank -> BankScreen(vm)
            Screen.Diary -> DiaryScreen(vm)
            Screen.Parent -> ParentScreen(vm)
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
    val scenarioResult = vm.scenarioResult
    val shiftResult = vm.shiftResult
    val morning = vm.morning
    val medal = vm.medals.firstOrNull()
    val lesson = vm.lessons.firstOrNull()
    val pending = vm.pendingScenario

    when {
        scenarioResult != null -> ScenarioResultDialog(scenarioResult) { vm.dismissScenarioResult() }
        shiftResult != null -> PayslipDialog(shiftResult) { vm.dismissShiftResult() }
        morning != null -> MorningDialog(morning, vm.state.day) { vm.dismissMorning() }
        medal != null -> MedalDialog(medal) { vm.dismissMedal() }
        lesson != null -> LessonDialog(lesson) { vm.dismissLesson() }
        pending != null -> PendingScenarioOverlay(vm, pending)
    }
}

/**
 * Ситуация, на которую нужно ответить до продолжения игры (вступление или событие дня).
 * Закрыть её кнопкой «назад» нельзя: выбор — часть игры.
 */
@Composable
private fun PendingScenarioOverlay(vm: GameViewModel, scenario: Scenario) {
    BackHandler(enabled = true) { }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
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
