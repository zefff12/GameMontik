# Инструкция по экспорту ассетов из Figma

## 📋 Список файлов для экспорта

### 1️⃣ Папка `raccoon/` — Енот и диалоги для игры про бартер
Экспортируйте из Figma в этот формат (PNG или WebP, 1x масштаб):

```
raccoon/
├── raccoon_happy.png         # Енот в хорошем настроении (для приветствия)
├── raccoon_thinking.png      # Енот задумался (для предложения торговли)
├── raccoon_satisfied.png     # Енот доволен (после удачной сделки)
├── dialog_bg.png             # Фон для диалога/всплывающего окна
├── dialog_bubble_left.png    # Мысль енота (слева)
├── dialog_bubble_right.png   # Ответ Монтика (справа)
└── market_bg.png             # Фоновая картина рынка
```

**Что это:** Необходимо для визуализации игры BarterUi.kt

---

### 2️⃣ Папка `barter/` — Товары для торговли
Иконки товаров для торговых сделок (32×32 или 64×64 px):

```
barter/
├── apple.png
├── orange.png
├── bread.png
├── fish.png
├── berries.png
├── honey.png
├── nuts.png
├── cheese.png
├── egg.png
├── milk.png
├── wheat.png
└── wood.png
```

**Что это:** Иконки для отображения в диалогах торговли (game/BarterGame.kt)

---

### 3️⃣ Папка `skins/` — Платные скины Монтика
Цветные варианты Монтика (тот же размер что и основной герой):

```
skins/
├── skin_royal_white.png      # Королевский
├── skin_sky_blue.png         # Небо
├── skin_cherry_pink.png      # Вишня
├── skin_forest_green.png     # Лес
├── skin_grape_purple.png     # Виноград
├── skin_sunset_orange.png    # Закат
├── skin_night_black.png      # Ночь
└── skin_magic_rainbow.png    # Радуга
```

**Что это:** Платные скины для SkinCatalog.kt (покупаются за монеты)

---

### 4️⃣ Папка `credits/` — UI системы кредитов
Элементы интерфейса кредитной системы:

```
credits/
├── credit_card.png           # Иконка кредитной карты
├── loan_button.png           # Кнопка "Взять кредит"
├── payment_icon.png          # Иконка платежа
├── interest_icon.png         # Иконка процентов
└── calendar_icon.png         # Иконка срока платежа
```

**Что это:** Для Credits.kt UI (экран в банке)

---

### 5️⃣ Папка `ui/` — Разное
Прочие UI элементы:

```
ui/
├── shop_button.png           # Кнопка "Лавка скинов"
├── market_button.png         # Кнопка "Рынок" (для енота)
├── raccoon_small.png         # Миниатюра енота для иконки дня
└── frame_33_raccoon.png      # Енот из Frame 33 (если используется)
```

---

## 🎬 Как экспортировать из Figma:

1. **Откройте Figma файл:** https://www.figma.com/design/fRE5bxFESOizTbv7now5aL/Монтик

2. **Для каждого элемента:**
   - Кликните на слой в Figma
   - Right-click → "Export" (или левая панель → Export)
   - Выберите формат: **PNG** (для растровых) или **WebP** (для сжатия)
   - Масштаб: **1x** (или 2x если хотите поддержку retina)
   - Скачайте

3. **Положите файлы** в соответствующие папки выше

4. **Создайте ZIP архив** всей папки `figma_assets/`

5. **Закиньте в GameMontik папку** (как обычно через device bridge)

---

## 📦 Структура для коммита:

После экспорта файлы должны быть в:
```
/app/src/main/res/drawable-nodpi/
├── raccoon_happy.webp
├── raccoon_thinking.webp
├── barter_apple.webp
├── barter_orange.webp
├── ...
├── skin_royal_white.webp
├── skin_sky_blue.webp
├── ...
```

**Или можно оставить в `figma_assets/` и я покопирую** 🚀

---

## 🔧 Форматы:

- **WebP** — рекомендуется (меньше размер)
- **PNG** — если WebP не работает
- **Размер:** тот же что в Figma (или примерно 200-500px по ширине для персонажей)

---

## ⏱️ Ускоренный способ:

Если у вас много экспортов, в Figma:
1. Выделите все элементы одной папки
2. Right-click → Select "Export" на все сразу
3. Figma может экспортировать несколько за раз

---

**После того как положите файлы в папку → скажите, и я их интегрирую в код!** ✨
