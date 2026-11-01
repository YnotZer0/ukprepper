package uk.prepper.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.prepper.app.Nav
import uk.prepper.app.Screen
import uk.prepper.app.data.Repo
import uk.prepper.app.logic.Readiness
import java.time.LocalDate

@Composable
fun DashboardScreen(nav: Nav) {
    val v = Repo.version.intValue
    val plan = Repo.plan
    val today = LocalDate.now()
    val items = remember(v) { Repo.items() }
    val coverage = remember(v, plan) { Readiness.coverage(items, plan, today.monthValue) }
    val gaps = remember(v, plan) { Readiness.gaps(items, plan, today.monthValue, today) }
    val expiring = remember(v) { Readiness.expiringSoon(items, today) }

    LazyColumn(Modifier.fillMaxSize()) {
        item { Header("Readiness") }
        item {
            Button(
                onClick = { nav.tab(Screen.Prepare) },
                colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(64.dp),
            ) { Text("I NEED TO PREPARE NOW", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
        item { Column {
            SectionLabel("Scenario")
            Toggles(listOf(3, 10, 30, 90), plan.scenarioDays, { "$it days" }) { Repo.updatePlan(plan.copy(scenarioDays = it)) }
            Spacer(Modifier.height(6.dp))
            Toggles(listOf(1, 2), plan.people, { if (it == 1) "1 person" else "2 people" }) { Repo.updatePlan(plan.copy(people = it)) }
        } }
        item { SectionLabel("Days of cover (target ${plan.scenarioDays})") }
        items(coverage, key = { it.label }) { c ->
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(ragColor(c.rag))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.label, fontWeight = FontWeight.SemiBold)
                        Text(c.have, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${c.days.d1()} d", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ragColor(c.rag))
                }
                Text(c.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (expiring.isNotEmpty()) {
            item { SectionLabel("Best-before within 6 weeks") }
            items(expiring, key = { "e${it.id}" }) { i ->
                InfoCard(Modifier) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("${i.name} · ${i.quantity.q()} ${i.unit}")
                        Text(i.expiry ?: "", color = Amber)
                    }
                }
            }
        }
        item { SectionLabel("Gaps to close") }
        if (gaps.isEmpty()) item { InfoCard { Text("No gaps for this scenario.") } }
        items(gaps, key = { "g$it" }) { g -> InfoCard { Text(g) } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
