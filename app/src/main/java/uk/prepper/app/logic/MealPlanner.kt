package uk.prepper.app.logic

import uk.prepper.app.model.Category
import uk.prepper.app.model.Item
import uk.prepper.app.model.Plan
import kotlin.math.ceil
import kotlin.math.min

data class Portion(
    val itemId: Long,
    val name: String,
    val units: Double,
    val unit: String,
    val kcal: Double,
    val gasGrams: Double,
    val waterMl: Double,
)

data class Meal(val day: Int, val slot: String, val portions: List<Portion>, val targetKcal: Double) {
    val kcal: Double get() = portions.sumOf { it.kcal }
    val gasGrams: Double get() = portions.sumOf { it.gasGrams }
    val waterMl: Double get() = portions.sumOf { it.waterMl }
}

data class MealPlan(val meals: List<Meal>, val days: Int) {
    val totalGasGrams: Double get() = meals.sumOf { it.gasGrams }
    val totalWaterMl: Double get() = meals.sumOf { it.waterMl }
    val shortfallKcal: Double get() = meals.sumOf { (it.targetKcal - it.kcal).coerceAtLeast(0.0) }

    /** First day on which at least one meal could not be filled to 80 % of target, or null. */
    val firstShortDay: Int?
        get() = meals.firstOrNull { it.kcal < it.targetKcal * 0.8 }?.day
}

/**
 * Builds a meal plan from stock:
 *  - oldest best-before first (undated items last)
 *  - dinner prefers boil-in-bag ration packs, breakfast/lunch prefer other food
 *  - whole units for tins/packs; fractional amounts for bulk lines (e.g. pasta per kg)
 */
object MealPlanner {
    private val slots = listOf("Breakfast" to 0.25, "Lunch" to 0.30, "Dinner" to 0.45)

    fun plan(items: List<Item>, plan: Plan, days: Int = plan.scenarioDays): MealPlan {
        val people = plan.people.coerceAtLeast(1)
        val dailyKcal = people * plan.kcalPerPerson.toDouble()

        val stock = items
            .filter { it.category.isFood && it.kcalPerUnit > 0 && it.quantity > 0 }
            .sortedWith(compareBy<Item> { Readiness.parseDate(it.expiry) == null }
                .thenBy { Readiness.parseDate(it.expiry) }
                .thenBy { it.id })
        val remaining = stock.associate { it.id to it.quantity }.toMutableMap()

        val meals = mutableListOf<Meal>()
        for (day in 1..days) {
            for ((slot, share) in slots) {
                val target = dailyKcal * share
                var need = target
                val portions = mutableListOf<Portion>()
                val preferRation = slot == "Dinner"
                val ordered = stock.sortedBy { if ((it.category == Category.RATION) == preferRation) 0 else 1 }

                for (item in ordered) {
                    if (need <= 50) break
                    val left = remaining[item.id] ?: 0.0
                    if (left <= 0.0) continue
                    if (portions.size >= 3) break
                    val bulk = item.kcalPerUnit > target * 1.5
                    val units = when {
                        // one boil-in-bag meal per person per sitting
                        item.category == Category.RATION -> min(left, people.toDouble())
                        bulk -> min(left, roundUp(need / item.kcalPerUnit, 0.05))
                        else -> min(left, Math.round(need / item.kcalPerUnit).toDouble().coerceAtLeast(1.0))
                    }
                    if (units <= 0.0) continue
                    remaining[item.id] = left - units
                    val kcal = units * item.kcalPerUnit
                    need -= kcal
                    portions += Portion(
                        itemId = item.id,
                        name = item.name,
                        units = units,
                        unit = item.unit,
                        kcal = kcal,
                        // one pot per item per meal: heat time counts once
                        gasGrams = item.heatMinutes * plan.gasGramsPerMinute,
                        waterMl = units * item.waterMlPerUnit,
                    )
                }
                meals += Meal(day, slot, portions, target)
            }
        }
        return MealPlan(meals, days)
    }

    private fun roundUp(v: Double, step: Double): Double = ceil(v / step) * step
}
