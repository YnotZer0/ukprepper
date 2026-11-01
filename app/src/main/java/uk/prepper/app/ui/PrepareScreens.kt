package uk.prepper.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import uk.prepper.app.Nav
import uk.prepper.app.Screen
import uk.prepper.app.data.Repo
import uk.prepper.app.logic.Checklists
import uk.prepper.app.logic.PrepMode
import uk.prepper.app.model.DrillRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PrepareScreen(nav: Nav) {
    Column(Modifier.fillMaxSize()) {
        Header("Prepare now")
        Text(
            "Pick the situation. Run it for real, or as a timed drill to test that what you need is where you need it.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        PrepMode.entries.forEach { mode ->
            InfoCard {
                Text(mode.label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (mode == PrepMode.STAY_HOME) "No mains power, water or fuel - you stay put."
                    else "You leave with the camper van, possibly on one tank of diesel.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = { nav.go(Screen.RunChecklist(mode, drill = false)) }, modifier = Modifier.weight(1f)) { Text("Start") }
                    OutlinedButton(onClick = { nav.go(Screen.RunChecklist(mode, drill = true)) }, modifier = Modifier.weight(1f)) { Text("Timed drill") }
                }
            }
        }
        TextButton(onClick = { nav.go(Screen.DrillHistory) }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Drill history ›") }
    }
}

@Composable
fun ChecklistRunScreen(nav: Nav, mode: PrepMode, drill: Boolean) {
    val tasks = remember(mode) { Checklists.build(mode, Repo.items(), Repo.plan, LocalDate.now().monthValue) }
    var done by rememberSaveable { mutableStateOf(listOf<String>()) }
    val started by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var finished by remember { mutableStateOf(false) }

    if (drill) LaunchedEffect(finished) {
        while (!finished) {
            elapsed = (System.currentTimeMillis() - started) / 1000
            delay(1000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Header(if (drill) "Drill: ${mode.label}" else mode.label, onBack = { nav.back() }) {
            if (drill) Text("%d:%02d".format(elapsed / 60, elapsed % 60), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Amber)
        }
        LinearProgressIndicator(
            progress = { if (tasks.isEmpty()) 1f else done.size.toFloat() / tasks.size },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
        Text("${done.size} of ${tasks.size} done", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        LazyColumn(Modifier.weight(1f)) {
            var last = ""
            tasks.forEach { t ->
                if (t.section != last) {
                    last = t.section
                    item(key = "s${t.section}") { SectionLabel(t.section) }
                }
                item(key = t.id) {
                    val checked = t.id in done
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { done = if (checked) done - t.id else done + t.id }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { done = if (it) done + t.id else done - t.id })
                        Column(Modifier.padding(top = 10.dp)) {
                            Text(
                                t.title,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
                            )
                            if (t.detail.isNotBlank()) Text(t.detail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
        Button(
            onClick = {
                finished = true
                if (drill) {
                    val missing = tasks.filter { it.id !in done }.joinToString("\n") { it.title }
                    Repo.addDrill(
                        DrillRecord(
                            mode = mode.label, startedAt = started,
                            seconds = (System.currentTimeMillis() - started) / 1000,
                            total = tasks.size, done = done.size, missing = missing,
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) { Text(if (drill) "Finish drill" else "Finish") }
    }

    if (finished) {
        val missing = tasks.filter { it.id !in done }
        AlertDialog(
            onDismissRequest = { nav.back() },
            title = { Text(if (missing.isEmpty()) "All done" else "${missing.size} item(s) not done") },
            text = {
                LazyColumn(Modifier.height(300.dp)) {
                    if (drill) item { Text("Time: %d:%02d".format(elapsed / 60, elapsed % 60), fontWeight = FontWeight.Bold) }
                    items(missing, key = { it.id }) { Text("• ${it.title}") }
                }
            },
            confirmButton = { TextButton(onClick = { nav.back() }) { Text("Close") } },
        )
    }
}

@Composable
fun DrillHistoryScreen(nav: Nav) {
    val v = Repo.version.intValue
    val drills = remember(v) { Repo.drills() }
    val fmt = remember { DateTimeFormatter.ofPattern("EEE d MMM yyyy HH:mm") }
    LazyColumn(Modifier.fillMaxSize()) {
        item { Header("Drill history", onBack = { nav.back() }) }
        if (drills.isEmpty()) item { InfoCard { Text("No drills yet. Run one from Prepare.") } }
        items(drills, key = { it.id }) { d ->
            InfoCard {
                val time = Instant.ofEpochMilli(d.startedAt).atZone(ZoneId.systemDefault()).format(fmt)
                Text("${d.mode} · $time", fontWeight = FontWeight.Bold)
                Text("${d.done}/${d.total} done in %d:%02d".format(d.seconds / 60, d.seconds % 60))
                if (d.missing.isNotBlank()) Text("Not done:\n${d.missing}", style = MaterialTheme.typography.bodySmall, color = Amber)
            }
        }
    }
}
