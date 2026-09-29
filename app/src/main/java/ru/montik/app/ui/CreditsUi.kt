package ru.montik.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.montik.app.GameViewModel
import ru.montik.app.game.Credits
import ru.montik.app.game.LoanProduct

/**
 * Кредиты в банке: активные кредиты с платежами, выбор нового кредита (в том числе ипотека)
 * и кредитная история с рейтингом. Логика — game/Credits.kt.
 */
@Composable
fun CreditsSection(vm: GameViewModel) {
    val s = vm.state

    // ── Мои кредиты ──
    for (credit in s.credits) {
        val product = Credits.product(credit.product)
        val overdue = Credits.isOverdue(s, credit)
        val daysLeft = Credits.daysLeft(s, credit)
        MontikCard {
            Text("${product?.emoji ?: "💳"} ${product?.title ?: "Кредит"}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Взято ${coinsText(credit.amount)} · вернуть ${coinsText(credit.total)}" +
                    if (credit.penalty > 0) " + штраф ${coinsText(credit.penalty)}" else "",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                when {
                    overdue -> "⚠️ Просрочен на ${-daysLeft} дн.! Каждый день — штраф ${Credits.PENALTY_PER_DAY} монеты."
                    daysLeft == 0 -> "⏰ Вернуть нужно сегодня!"
                    else -> "Срок: ещё $daysLeft дн. (до ${credit.dueDay}-го дня)"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (overdue || daysLeft <= 2) MontikColors.Bad else MontikColors.InkSoft
            )
            Spacer(Modifier.height(8.dp))
            val total = (credit.total + credit.penalty).coerceAtLeast(1)
            LinearProgressIndicator(
                progress = { (credit.paid.toFloat() / total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(10.dp),
                color = MontikColors.Good,
                trackColor = MontikColors.Track
            )
            Spacer(Modifier.height(4.dp))
            Text("Внесено ${coinsText(credit.paid)}, осталось ${coinsText(credit.left)}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.payCredit(credit.id, 20) },
                    enabled = s.coins > 0,
                    shape = MontikShapes.Chip,
                    modifier = Modifier.weight(1f)
                ) { Text("Внести 20") }
                OutlinedButton(
                    onClick = { vm.payCredit(credit.id, credit.left) },
                    enabled = s.coins >= credit.left,
                    shape = MontikShapes.Chip,
                    modifier = Modifier.weight(1f)
                ) { Text("Вернуть всё") }
            }
        }
    }

    // ── Взять кредит ──
    MontikCard(color = MontikColors.SurfaceTint) {
        Text("💳 Кредиты", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Банк даёт деньги сразу, но вернуть нужно больше — это плата банку. " +
                "Опоздаешь — будет штраф и плохая кредитная история. Если можно подождать и накопить — накопить дешевле.",
            style = MaterialTheme.typography.bodyMedium,
            color = MontikColors.InkSoft
        )
    }
    for (product in LoanProduct.values()) {
        val blocker = Credits.blocker(s, product)
        MontikCard {
            Text("${product.emoji} ${product.title}", style = MaterialTheme.typography.titleMedium)
            Text(product.note, style = MaterialTheme.typography.bodyMedium, color = MontikColors.InkSoft)
            Spacer(Modifier.height(4.dp))
            Text(
                "Получишь ${coinsText(product.amount)} · вернуть ${coinsText(product.total)} за ${product.days} дн. " +
                    "(переплата ${product.overpay})",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(8.dp))
            BigButton("Взять ${product.amount}", onClick = { vm.takeCredit(product.id) }, enabled = blocker == null, primary = false)
            if (blocker != null) {
                Spacer(Modifier.height(4.dp))
                Text("🔒 $blocker", style = MaterialTheme.typography.bodyMedium, color = MontikColors.InkSoft)
            }
        }
    }

    // ── Кредитная история ──
    val score = Credits.score(s)
    MontikCard {
        Text("📜 Кредитная история", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Рейтинг: $score из 100 — ${Credits.scoreTitle(score).lowercase()}. " +
                "Вовремя вернул — рейтинг растёт, опоздал — падает. По нему банк решает, дать ли ипотеку.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { score / 100f },
            modifier = Modifier.fillMaxWidth().height(10.dp),
            color = if (score >= 60) MontikColors.Good else MontikColors.Bad,
            trackColor = MontikColors.Track
        )
        Spacer(Modifier.height(8.dp))
        if (s.creditHistory.isEmpty()) {
            Text("Пока пусто: закрытые кредиты появятся здесь.", style = MaterialTheme.typography.bodyMedium, color = MontikColors.InkSoft)
        } else {
            for (r in s.creditHistory.takeLast(10).reversed()) {
                val title = Credits.product(r.product)?.title ?: "Кредит"
                Text(
                    (if (r.onTime) "✅ " else "⚠️ ") + "$title: взял ${r.amount}, вернул ${r.totalPaid} " +
                        "(дни ${r.dayTaken}–${r.dayClosed})" + if (r.onTime) "" else " — с опозданием",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
