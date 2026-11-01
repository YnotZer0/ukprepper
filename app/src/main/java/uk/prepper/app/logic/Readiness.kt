package uk.prepper.app.logic

import uk.prepper.app.model.Category
import uk.prepper.app.model.Item
import uk.prepper.app.model.Plan
import java.time.LocalDate
import kotlin.math.ceil

enum class Rag { GREEN, AMBER, RED }

data class Coverage(
    val label: String,
    val have: String,
    val days: Double,      // POSITIVE_INFINITY = self-sustaining
    val target: Int,
    val note: String = "",
) {
    val rag: Rag
        get() = when {
            days >= target -> Rag.GREEN
            days >= target / 2.0 -> Rag.AMBER
            else -> Rag.RED
        }
}

object Readiness {
    /** Rough planning rate (rolls per person per day). Adjust to your own use. */
    const val TOILET_ROLLS_PER_PERSON_DAY = 0.3
    const val EXPIRY_WARNING_DAYS = 42L

    /** Essentials that should exist somewhere. Matched by keywords in item names. */
    val essentials: List<Pair<String, List<String>>> = listOf(
        "Wind-up / battery radio" to listOf("radio"),
        "Torch" to listOf("torch", "head torch", "headlamp", "lantern"),
        "First aid kit" to listOf("first aid"),
        "Water filter (Sawyer)" to listOf("sawyer", "filter"),
        "Water purification tablets" to listOf("purif", "water tablet", "chlorine", "aquatab"),
        "Matches / lighter / fire steel" to listOf("match", "lighter", "fire steel", "ferro"),
        "Tin opener" to listOf("opener"),
        "Toilet bucket with lid" to listOf("bucket"),
        "Urine container (jerrycan / bottle)" to listOf("urine", "jerrycan"),
        "Vinegar / citric acid (toilet cleaning)" to listOf("vinegar", "citric"),
        "Cover material (sawdust / shavings)" to listOf("sawdust", "shavings", "cover material"),
        "Hand sanitiser" to listOf("sanitiser", "sanitizer"),
        "Bin bags" to listOf("bin bag", "rubbish bag", "refuse sack"),
        "Spare batteries" to listOf("batter"),
        "Sleeping bag" to listOf("sleeping bag"),
        "Cash" to listOf("cash"),
    )

    fun drinkingLitres(items: List<Item>): Double =
        items.filter { it.category == Category.WATER }.sumOf { it.quantity * it.litresPerUnit }

    fun foodKcal(items: List<Item>): Double =
        items.filter { it.category.isFood }.sumOf { it.quantity * it.kcalPerUnit }

    fun toiletRolls(items: List<Item>): Double =
        items.filter {
            val n = it.name.lowercase()
            val u = it.unit.lowercase()
            n.contains("toilet roll") || n.contains("toilet paper") || u.startsWith("roll")
        }.sumOf { it.quantity }

    fun coverage(items: List<Item>, plan: Plan, month: Int): List<Coverage> {
        val people = plan.people.coerceAtLeast(1)
        val target = plan.scenarioDays
        val drink = drinkingLitres(items)
        val kcal = foodKcal(items)
        val rolls = toiletRolls(items)
        val power = Power.budget(plan, month)

        return listOf(
            Coverage(
                "Drinking water",
                "%.0f L stored".format(drink),
                drink / (people * plan.drinkLitres),
                target,
                "At ${plan.drinkLitres} L/person/day. Filtered + treated river/rain water extends this.",
            ),
            Coverage(
                "All water (incl. van tank)",
                "%.0f L".format(drink + plan.vanTankLitres),
                (drink + plan.vanTankLitres) / (people * plan.comfortLitres),
                target,
                "At ${plan.comfortLitres} L/person/day for cooking & hygiene. Boil tank water before drinking.",
            ),
            Coverage(
                "Food",
                "%,.0f kcal".format(kcal),
                kcal / (people * plan.kcalPerPerson.toDouble()),
                target,
                "At ${plan.kcalPerPerson} kcal/person/day.",
            ),
            Coverage(
                "Propane",
                "%.1f kg".format(plan.gasKg),
                plan.gasKg * 1000.0 / plan.gasGramsPerDay.coerceAtLeast(1.0),
                target,
                "At ${plan.gasGramsPerDay.toInt()} g/day (estimate - log real use to refine).",
            ),
            Coverage(
                "Electricity",
                "%,.0f Wh stored".format(power.storedWh),
                power.days,
                target,
                if (power.netWhDay >= 0) "Solar covers daily load this month."
                else "Net %.0f Wh/day this month.".format(power.netWhDay),
            ),
            Coverage(
                "Toilet roll",
                "%.0f rolls".format(rolls),
                rolls / (people * TOILET_ROLLS_PER_PERSON_DAY),
                target,
                "At $TOILET_ROLLS_PER_PERSON_DAY rolls/person/day.",
            ),
        )
    }

    fun missingEssentials(items: List<Item>): List<String> {
        val names = items.filter { it.quantity > 0 }.map { it.name.lowercase() }
        return essentials.filter { (_, keys) -> names.none { n -> keys.any { k -> n.contains(k) } } }
            .map { it.first }
    }

    fun expiringSoon(items: List<Item>, today: LocalDate, days: Long = EXPIRY_WARNING_DAYS): List<Item> {
        val limit = today.plusDays(days)
        return items.filter { it.quantity > 0 }
            .mapNotNull { item -> parseDate(item.expiry)?.let { item to it } }
            .filter { (_, d) -> !d.isAfter(limit) }
            .sortedBy { it.second }
            .map { it.first }
    }

    fun parseDate(s: String?): LocalDate? =
        if (s.isNullOrBlank()) null else runCatching { LocalDate.parse(s.trim()) }.getOrNull()

    /** Shopping list to reach the scenario target. */
    fun gaps(items: List<Item>, plan: Plan, month: Int, today: LocalDate): List<String> {
        val out = mutableListOf<String>()
        val people = plan.people.coerceAtLeast(1)
        val days = plan.scenarioDays

        val needDrink = days * people * plan.drinkLitres - drinkingLitres(items)
        if (needDrink > 0) out += "Drinking water: %.0f L more (or a reliable treated source)".format(needDrink)

        val needKcal = days * people * plan.kcalPerPerson - foodKcal(items)
        if (needKcal > 0) out += "Food: %,.0f kcal more (≈ %d tins at 400 kcal)".format(needKcal, ceil(needKcal / 400.0).toInt())

        val needRolls = days * people * TOILET_ROLLS_PER_PERSON_DAY - toiletRolls(items)
        if (needRolls > 0) out += "Toilet roll: %d more".format(ceil(needRolls).toInt())

        val gasDays = plan.gasKg * 1000.0 / plan.gasGramsPerDay.coerceAtLeast(1.0)
        if (gasDays < days) {
            val kg = (days - gasDays) * plan.gasGramsPerDay / 1000.0
            out += "Propane: %.1f kg more, or plan wood-fire cooking".format(kg)
        }

        val power = Power.budget(plan, month)
        if (power.days < days) out += "Electricity: cut daily load or add solar (lasts %.1f days this month)".format(power.days)
        if (plan.vanSolarW <= 0 && plan.gardenSolarW <= 0) out += "Enter your solar panel watts in Power settings"

        missingEssentials(items).forEach { out += "Missing: $it" }

        val noKcal = items.count { it.category.isFood && it.kcalPerUnit <= 0 && it.quantity > 0 }
        if (noKcal > 0) out += "$noKcal food line(s) have no kcal recorded - they don't count yet"

        val expired = items.count { it.quantity > 0 && (parseDate(it.expiry)?.isBefore(today) == true) }
        if (expired > 0) out += "$expired line(s) past best-before - check and rotate"

        return out
    }
}
