package uk.prepper.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.prepper.app.Nav
import uk.prepper.app.data.Repo
import uk.prepper.app.logic.MealPlanner
import uk.prepper.app.model.Category

@Composable
fun MealsScreen(nav: Nav) {
    val v = Repo.version.intValue
    val plan = Repo.plan
    var days by rememberSaveable { mutableIntStateOf(7) }
    val items = remember(v) { Repo.items() }
    val mp = remember(v, plan, days) { MealPlanner.plan(items, plan, days) }
    val rations = items.filter { it.category == Category.RATION }.sumOf { it.quantity }

    LazyColumn(Modifier.fillMaxSize()) {
        item { Header("Meal plan") }
        item {
            Toggles(listOf(3, 7, 14, plan.scenarioDays).distinct().sorted(), days, { "$it d" }) { days = it }
        }
        item {
            InfoCard {
                Text("${plan.people} person(s) × ${plan.kcalPerPerson} kcal/day", fontWeight = FontWeight.SemiBold)
                Text("Ration packs in stock: ${rations.q()} meals")
                val short = mp.firstShortDay
                Text(
                    if (short == null) "Stock covers all $days days." else "Stock runs short from day $short.",
                    color = if (short == null) Green else Red,
                )
                Text("Cooking gas for these meals: ${"%.0f".format(mp.totalGasGrams)} g")
                Text("Drinking water used in cooking: ${"%.1f".format(mp.totalWaterMl / 1000)} L")
                Text(
                    "Oldest best-before is used first. Tap 'Eaten' on today's meals to take them off stock; " +
                        "the plan then rebuilds from what's left.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        items(mp.meals, key = { "${it.day}-${it.slot}" }) { m -> Column {
            if (m.slot == "Breakfast") SectionLabel(if (m.day == 1) "Day 1 (today)" else "Day ${m.day}")
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.slot, fontWeight = FontWeight.Bold)
                        val pct = if (m.targetKcal > 0) m.kcal / m.targetKcal else 1.0
                        Text(
                            "${"%.0f".format(m.kcal)} / ${"%.0f".format(m.targetKcal)} kcal",
                            color = when { pct >= 0.8 -> Green; pct >= 0.5 -> Amber; else -> Red },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (m.day == 1 && m.portions.isNotEmpty()) OutlinedButton(onClick = {
                        m.portions.forEach { p -> Repo.adjustQuantity(p.itemId, -p.units) }
                    }) { Text("Eaten") }
                }
                if (m.portions.isEmpty()) Text("Nothing left in stock", color = Red)
                m.portions.forEach { p ->
                    Text("• ${p.name} - ${p.units.q()} ${p.unit}" + if (p.gasGrams > 0) " (hot)" else "")
                }
            }
        } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
