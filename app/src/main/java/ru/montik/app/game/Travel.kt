package ru.montik.app.game

/*
 * Путешествия Монтика — одна цепочка, где каждое действие связано со следующим:
 *
 *   выбор города → ✈️ перелёт → прогулка по городу (остановки-задания, кафе)
 *   → 🏆 «Ура! Открыт новый город!» (только в первый раз) → бизнес-конференция
 *   → +100 опыта → 🏆 «Получил новый опыт» → уровень профессионала растёт
 *   → заработок за каждую смену становится больше → ✈️ перелёт домой → дом.
 *
 * Главная идея для ребёнка: путешествия дают знания и опыт, а опыт помогает больше зарабатывать.
 */
object Travel {
    /** Опыт за одну бизнес-конференцию. */
    const val CONFERENCE_XP = 100

    /** Насколько (в процентах) растёт заработок за каждую посещённую конференцию. */
    const val CAREER_STEP_PERCENT = 25

    /** Больше этого числа конференций прибавка не растёт (уровень профессионала 6 — самый высокий). */
    const val MAX_CAREER_CONFERENCES = 5

    /** Ужин в кафе во время прогулки. */
    const val CAFE_PRICE = 45
    const val CAFE_FOOD = 45
    const val CAFE_WATER = 25

    /** Конференция немного утомляет. */
    const val CONFERENCE_ENERGY = 10

    // ───────────────────────── Уровень профессионала и заработок ─────────────────────────

    /** Уровень профессионала: 1 + число конференций (до 6). */
    fun careerLevel(conferences: Int): Int = 1 + conferences.coerceIn(0, MAX_CAREER_CONFERENCES)

    fun careerLevel(state: GameState): Int = careerLevel(state.conferences)

    /** Прибавка к заработку за опыт конференций, в процентах: 0, 25, 50, … 125. */
    fun careerPercent(conferences: Int): Int = conferences.coerceIn(0, MAX_CAREER_CONFERENCES) * CAREER_STEP_PERCENT

    fun careerPercent(state: GameState): Int = careerPercent(state.conferences)

    /** Пример для ребёнка: сколько теперь начисляют за смену со ставкой [base] при полных силах. */
    fun shiftExample(base: Int, conferences: Int): Int = base * (100 + careerPercent(conferences)) / 100

    /** Надпись на плашке самолёта: «До Сочи: 2 ч 15 мин» / «До дома: 2 ч 15 мин». */
    fun flightTitle(dest: Destination, home: Boolean): String = if (home) "До дома" else "До ${dest.toName}"

    /** «2 ч 15 мин», «1 ч 05 мин», «40 мин». */
    fun duration(minutes: Int): String {
        val m = minutes.coerceAtLeast(0)
        val h = m / 60
        val rest = m % 60
        return if (h == 0) "$rest мин" else "$h ч ${rest.toString().padStart(2, '0')} мин"
    }

    fun destination(state: GameState): Destination? = state.trip?.let { Destinations.byId(it.destinationId) }

    // ───────────────────────── Этапы ─────────────────────────

    /** Самолёт приземлился: Монтик в городе. В первый раз город «открывается» — достижение и магнит. */
    fun land(state: GameState): Outcome {
        val trip = state.trip ?: return GameEngine.fail(state, "Монтик сейчас дома.")
        if (trip.phase != TripPhase.FLY_OUT) return GameEngine.fail(state, "Самолёт уже приземлился.")
        val dest = Destinations.byId(trip.destinationId) ?: return GameEngine.fail(state, "Такого города нет.")
        val c = GameEngine.Ctx(state)
        c.s = c.s.copy(
            trip = trip.copy(phase = TripPhase.CITY),
            clockMinutes = c.s.clockMinutes + dest.flightMinutes
        )
        c.say("🛬 Самолёт приземлился. Здравствуй, ${dest.name}!")
        if (dest.id !in state.visitedCities) {
            c.s = c.s.copy(visitedCities = c.s.visitedCities + dest.id)
            c.say("🏆 Ура! Открыт новый город: ${dest.name}! Магнит из поездки — уже на холодильнике.")
            c.skill(Skill.TRAVELER, 1)
            c.mood(10, "Монтик впервые увидел ${dest.name}!")
            c.log("открыт новый город: ${dest.name}")
        }
        return c.done()
    }

    /** Ужин в кафе во время прогулки — желаемая трата: вкусно, но можно было дешевле. */
    fun eatAtCafe(state: GameState): Outcome {
        val trip = state.trip ?: return GameEngine.fail(state, "Кафе открыто только в путешествии.")
        if (trip.phase != TripPhase.CITY) return GameEngine.fail(state, "Сейчас не время для кафе.")
        if (trip.cafe) return GameEngine.fail(state, "Монтик уже поужинал в этом кафе.")
        if (state.coins < CAFE_PRICE) {
            return GameEngine.fail(state, "Ужин стоит $CAFE_PRICE монет, а в кошельке ${state.coins}. Можно погулять и без кафе.")
        }
        val c = GameEngine.Ctx(state)
        c.spend(CAFE_PRICE, SpendKind.WANT, "ужин в кафе")
        c.meters(food = CAFE_FOOD, water = CAFE_WATER)
        c.s = c.s.copy(trip = c.s.trip?.copy(cafe = true))
        c.mood(5, "Вкусный ужин в красивом кафе.")
        c.say("🍝 Ужин в кафе: −$CAFE_PRICE монет. Вкусно! Но помни: в поездке еда в кафе — желаемая трата, а не обязательная.")
        c.log("ужин в кафе (−$CAFE_PRICE)")
        c.teach(Lesson.WANT_NEED)
        return c.done()
    }

    /** Бизнес-конференция: +100 опыта, уровень профессионала растёт, заработок за смену тоже. */
    fun attendConference(state: GameState): Outcome {
        val trip = state.trip ?: return GameEngine.fail(state, "Конференция проходит только в поездке.")
        if (trip.phase != TripPhase.CONFERENCE) return GameEngine.fail(state, "Сначала погуляй по городу и пройди все остановки.")
        val dest = Destinations.byId(trip.destinationId) ?: return GameEngine.fail(state, "Такого города нет.")
        val c = GameEngine.Ctx(state)
        val levelBefore = careerLevel(state)
        val percentBefore = careerPercent(state)
        c.s = c.s.copy(conferences = c.s.conferences + 1, trip = trip.copy(phase = TripPhase.FLY_HOME))
        c.meters(energy = -CONFERENCE_ENERGY)
        c.xp(CONFERENCE_XP)
        c.skill(Skill.CREATOR, 2)
        c.say("🎤 Бизнес-конференция «${dest.conference}»: +$CONFERENCE_XP опыта.")
        c.say("🏆 Получил новый опыт!")
        val levelAfter = careerLevel(c.s)
        val percentAfter = careerPercent(c.s)
        if (levelAfter > levelBefore) {
            c.say(
                "⭐ Новый уровень профессионала: $levelAfter! Прибавка к заработку за опыт: " +
                    "+$percentBefore% → +$percentAfter%. Каждая смена теперь приносит больше монет."
            )
        } else {
            c.say("⭐ Опыт пополнился. Прибавка к заработку уже самая большая: +$percentAfter%.")
        }
        c.log("конференция «${dest.conference}» (${dest.name}): +$CONFERENCE_XP опыта, прибавка +$percentAfter%")
        c.teach(Lesson.WORK)
        return c.done()
    }

    /** Самолёт привёз Монтика домой: путешествие завершено. */
    fun flyHome(state: GameState): Outcome {
        val trip = state.trip ?: return GameEngine.fail(state, "Монтик уже дома.")
        if (trip.phase != TripPhase.FLY_HOME) return GameEngine.fail(state, "Домой — после конференции.")
        val dest = Destinations.byId(trip.destinationId) ?: return GameEngine.fail(state, "Такого города нет.")
        val c = GameEngine.Ctx(state)
        c.s = c.s.copy(
            trip = null,
            completedTrips = c.s.completedTrips + dest.id,
            clockMinutes = c.s.clockMinutes + dest.flightMinutes
        )
        c.skill(Skill.TRAVELER, 3)
        c.say("🏡 Монтик вернулся домой из города ${dest.name}: с новыми знаниями, опытом и магнитом на холодильник.")
        c.log("вернулся из путешествия: ${dest.name}")
        if (dest.restful) {
            c.s = c.s.copy(energy = 100, food = maxOf(c.s.food, 70), water = maxOf(c.s.water, 70))
            c.say("🌊 Отдых у моря пошёл на пользу: Монтик вернулся полным сил и готов к новым делам!")
            c.teach(Lesson.VACATION)
        }
        return c.done()
    }
}
