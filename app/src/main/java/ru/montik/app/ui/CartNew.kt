package ru.montik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import ru.montik.app.GameViewModel
import ru.montik.app.game.Grocery
import ru.montik.app.game.GroceryItem

/*
 * Корзина по кадрам «Пустая корзина» и «Заполненная корзина»: шапка и нижняя панель — картинки макета,
 * строки товаров и итог — живые, в том же стиле (белые карточки, тёмно-зелёная «Оплатить»).
 */

private val CartBg = Color(0xFFEFF8F1)
private val CartInk = Color(0xFF14261A)
private val CartGrey = Color(0xFF8A938C)
private val CartGreen = Color(0xFF2A7A33)
private val CartQty = Color(0xFFE5F2E4)

@Composable
fun hasNewCart(): Boolean = rememberHasArt("fg4_cart_head", "fg4_cart_nav")

@Composable
fun NewCartScreen(vm: GameViewModel, onBack: () -> Unit, onHome: () -> Unit, onTab: (Int) -> Unit) {
    val fb = rememberFeedback()
    val cart = vm.cart
    val lines = cart.entries.mapNotNull { (id, n) -> Grocery.item(id)?.let { it to n } }
    val total = Grocery.total(cart)
    val count = Grocery.count(cart)
    DesignCanvas(background = { Box(Modifier.fillMaxSize().background(CartBg)) }) {
        Box(Modifier.at(0f, 0f, DESIGN_W, DESIGN_H).background(CartBg))
        Art("fg4_cart_head", 0f, 0f, 412f, 150f, ContentScale.FillBounds)
        PressHit(8f, 30f, 56f, 56f, 28f, onClick = onBack)
        PressHit(356f, 30f, 56f, 56f, 28f) { if (count > 0) { fb.tap(); vm.cartClear() } }
        Box(Modifier.at(17f, 120f, 380f, 26f), contentAlignment = Alignment.CenterStart) {
            Text(
                if (count == 0) "Пока пусто" else "${goodsCount(count)} на сумму $total ${coinWord(total)}",
                color = Color(0xFF6E7781), fontSize = fs(15.5f, false), maxLines = 1
            )
        }

        Box(Modifier.at(0f, 150f, DESIGN_W, 654f)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = d(16f), vertical = d(4f)),
                verticalArrangement = Arrangement.spacedBy(d(12f))
            ) {
                if (lines.isEmpty()) {
                    CartCard {
                        Text("Корзина пуста", color = CartInk, fontSize = fs(18f, false), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(d(6f)))
                        Text("Нажимай «+» на карточках продуктов — они появятся здесь.", color = CartGrey, fontSize = fs(13.5f, false), lineHeight = fs(18f, false))
                        Spacer(Modifier.height(d(14f)))
                        CartButton("На главную", true, onHome)
                    }
                }
                for ((item, n) in lines) key(item.id) {
                    CartLine(item, n,
                        onMinus = { fb.tap(); vm.cartRemove(item.id) },
                        onPlus = { fb.tap(); vm.cartAdd(item.id) },
                        onDrop = { vm.cartDrop(item.id) })
                }
                if (lines.isNotEmpty()) {
                    val (needs, wants) = Grocery.split(cart)
                    CartCard {
                        SumLine("Итого", total, big = true)
                        SumLine("Товары ($count)", total)
                        SumLine("Нужное (еда и вода)", needs)
                        if (wants > 0) SumLine("Желаемое (вкусности)", wants)
                        Row(Modifier.fillMaxWidth().padding(vertical = d(5f)), verticalAlignment = Alignment.CenterVertically) {
                            Text("Доставка", color = CartGrey, fontSize = fs(14.5f, false), modifier = Modifier.weight(1f))
                            Text("Бесплатно", color = CartGreen, fontSize = fs(14.5f, false))
                        }
                        Box(Modifier.fillMaxWidth().padding(vertical = d(6f)).height(d(1f)).background(Color(0xFFDDE8DC)))
                        SumLine("К оплате", total, big = true)
                        SumLine("В кошельке", vm.state.coins)
                        Spacer(Modifier.height(d(12f)))
                        val enough = vm.state.coins >= total
                        CartButton(if (enough) "Оплатить" else "Не хватает ${total - vm.state.coins} ${coinWord(total - vm.state.coins)}", enough) {
                            if (vm.checkout()) fb.coin() else fb.bad()
                        }
                    }
                }
                Spacer(Modifier.height(d(8f)))
            }
        }

        // Нижняя панель: «Главная», «Корзина», «Монетки», «Профиль».
        Art("fg4_cart_nav", 0f, 806f, 412f, 96f, ContentScale.FillBounds)
        Hit(0f, 812f, 103f, 80f, onHome)
        Hit(206f, 812f, 103f, 80f) { onTab(2) }
        Hit(309f, 812f, 103f, 80f) { onTab(3) }
        if (count > 0) {
            Box(
                Modifier.at(160f, 812f, 22f, 22f).clip(CircleShape).background(CartGreen),
                contentAlignment = Alignment.Center
            ) { Text("$count", color = Color.White, fontSize = fs(11f, false), fontWeight = FontWeight.Bold) }
        }
    }
}

private fun goodsCount(n: Int): String {
    val m100 = n % 100
    val m10 = n % 10
    return when {
        m100 in 11..14 -> "$n товаров"
        m10 == 1 -> "$n товар"
        m10 in 2..4 -> "$n товара"
        else -> "$n товаров"
    }
}

@Composable
private fun DesignScope.CartCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().shadow(d(3f), RoundedCornerShape(d(22f))).clip(RoundedCornerShape(d(22f)))
            .background(Color.White).padding(d(16f))
    ) { content() }
}

@Composable
private fun DesignScope.SumLine(label: String, value: Int, big: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = d(if (big) 4f else 3f)), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, color = if (big) CartInk else CartGrey, fontSize = fs(if (big) 19f else 14.5f, false),
            fontWeight = if (big) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f)
        )
        Text("$value", color = CartInk, fontSize = fs(if (big) 20f else 14.5f, false), fontWeight = if (big) FontWeight.Bold else FontWeight.Normal)
        Spacer(Modifier.width(d(5f)))
        ArtImage("fg4_coin", Modifier.size(d(if (big) 24f else 18f)), ContentScale.Fit) {}
    }
}

@Composable
private fun DesignScope.CartButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(d(56f)).clip(RoundedCornerShape(d(28f)))
            .background(if (enabled) CartGreen else Color(0xFFB9C9B9))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color.White, fontSize = fs(18f, false), fontWeight = FontWeight.Bold, maxLines = 1) }
}

@Composable
private fun DesignScope.CartLine(item: GroceryItem, n: Int, onMinus: () -> Unit, onPlus: () -> Unit, onDrop: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(d(84f)).shadow(d(3f), RoundedCornerShape(d(22f))).clip(RoundedCornerShape(d(22f)))
            .background(Color.White).padding(horizontal = d(10f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtImage(groceryArt(item.id), Modifier.size(d(64f)), ContentScale.Fit) { Text(item.emoji, fontSize = fs(34f, false)) }
        Spacer(Modifier.width(d(6f)))
        Column(Modifier.weight(1f)) {
            Text(item.name, color = CartInk, fontSize = fs(14.5f, false), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.amount, color = CartGrey, fontSize = fs(12f, false), maxLines = 1)
        }
        Row(
            Modifier.clip(RoundedCornerShape(d(18f))).background(CartQty).padding(horizontal = d(4f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QtyTap("−", onMinus)
            Text("$n", color = CartGreen, fontSize = fs(16f, false), fontWeight = FontWeight.Bold)
            QtyTap("+", onPlus)
        }
        Spacer(Modifier.width(d(6f)))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${item.price * n}", color = CartInk, fontSize = fs(17f, false), fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(d(3f)))
                ArtImage("fg4_coin", Modifier.size(d(20f)), ContentScale.Fit) {}
            }
        }
        Box(Modifier.size(d(30f)).clickable(onClick = onDrop), contentAlignment = Alignment.Center) {
            Text("✕", color = CartGrey, fontSize = fs(16f, false), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DesignScope.QtyTap(sign: String, onClick: () -> Unit) {
    Box(Modifier.size(d(30f)).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(sign, color = CartGreen, fontSize = fs(20f, false), fontWeight = FontWeight.Bold)
    }
}
