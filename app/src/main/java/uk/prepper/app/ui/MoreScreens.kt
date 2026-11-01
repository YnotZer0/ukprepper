package uk.prepper.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.prepper.app.Nav
import uk.prepper.app.Screen
import uk.prepper.app.data.Repo
import uk.prepper.app.export.Exporter
import uk.prepper.app.logic.Power
import uk.prepper.app.model.ActivityTask
import uk.prepper.app.model.Plan
import uk.prepper.app.reference.ReferenceLibrary
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MoreScreen(nav: Nav) {
    val entries = listOf(
        Triple("Daily activities", "Routine, chores, exercise - tick off each day", Screen.Activities),
        Triple("Energy & fuel", "Batteries, solar by month, loads, propane", Screen.Energy),
        Triple("Reference library", "Water, fire, warmth, compost toilet, radio…", Screen.Reference),
        Triple("Plan settings", "People, calories, water targets", Screen.PlanSettings),
        Triple("Print & backup", "Double-sided PDF, JSON backup / restore", Screen.Backup),
        Triple("Drill history", "Past timed drills and what was missing", Screen.DrillHistory),
    )
    LazyColumn(Modifier.fillMaxSize()) {
        item { Header("More") }
        items(entries, key = { it.first }) { (title, sub, screen) ->
            InfoCard(Modifier.clickable { nav.go(screen) }) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(sub, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Text(
                "Offline only: this app has no internet permission. Data stays on this phone.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

// ---------------- Activities ----------------

@Composable
fun ActivitiesScreen(nav: Nav) {
    val v = Repo.version.intValue
    val list = remember(v) { Repo.activities() }
    val today = LocalDate.now().toString()
    var time by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var mins by rememberSaveable { mutableStateOf("30") }
    val doneCount = list.count { it.lastDone == today }

    LazyColumn(Modifier.fillMaxSize().imePadding()) {
        item { Header("Daily activities", onBack = { nav.back() }) }
        item { Text("$doneCount of ${list.size} done today", modifier = Modifier.padding(horizontal = 16.dp)) }
        items(list, key = { it.id }) { a ->
            val done = a.lastDone == today
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = done, onCheckedChange = { Repo.saveActivity(a.copy(lastDone = if (it) today else null)) })
                Text(a.time, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.title)
                    if (a.minutes > 0) Text("${a.minutes} min", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { Repo.deleteActivity(a.id) }) { Text("✕") }
            }
        }
        item { Column {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Column(Modifier.padding(horizontal = 12.dp)) {
                Text("Add activity", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Time HH:mm", time, { time = it }, Modifier.weight(1f))
                    Field("Minutes", mins, { mins = it }, Modifier.weight(1f), number = true)
                }
                Field("What", title, { title = it })
                Button(onClick = {
                    val ok = Regex("^([01]?\\d|2[0-3]):[0-5]\\d$").matches(time.trim())
                    if (ok && title.isNotBlank()) {
                        val t = time.trim().let { if (it.length == 4) "0$it" else it }
                        Repo.saveActivity(ActivityTask(time = t, title = title.trim(), minutes = mins.toIntOrNull() ?: 0))
                        time = ""; title = ""
                    }
                }) { Text("Add") }
            }
            Spacer(Modifier.height(30.dp))
        } }
    }
}

// ---------------- Energy & fuel ----------------

@Composable
private fun PlanNumber(label: String, value: Double, onSet: (Double) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    var text by remember(label) { mutableStateOf(value.q()) }
    Field(label, text, {
        text = it
        it.replace(',', '.').toDoubleOrNull()?.let(onSet)
    }, modifier, number = true)
}

@Composable
fun EnergyScreen(nav: Nav) {
    val p = Repo.plan
    var month by rememberSaveable { mutableIntStateOf(LocalDate.now().monthValue) }
    val b = remember(p, month) { Power.budget(p, month) }
    fun set(f: (Plan) -> Plan) = Repo.updatePlan(f(Repo.plan))

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        Header("Energy & fuel", onBack = { nav.back() })
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { month = if (month == 1) 12 else month - 1 }) { Text("‹") }
            Text(
                Month.of(month).getDisplayName(TextStyle.FULL, Locale.UK) +
                    " · ~${Power.ukPeakSunHours[month - 1]} sun-hours/day",
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = { month = if (month == 12) 1 else month + 1 }) { Text("›") }
        }
        InfoCard {
            Text("Stored: %,.0f Wh".format(b.storedWh), fontWeight = FontWeight.Bold)
            b.stores.forEach { (n, wh) -> Text("  $n: %,.0f Wh".format(wh), style = MaterialTheme.typography.bodySmall) }
            Text("Daily load: %,.0f Wh".format(b.loadWhDay), fontWeight = FontWeight.Bold)
            b.loads.forEach { (n, wh) -> Text("  $n: %,.0f Wh".format(wh), style = MaterialTheme.typography.bodySmall) }
            Text("Solar in: %,.0f Wh/day (estimate)".format(b.solarWhDay), fontWeight = FontWeight.Bold)
            Text(
                if (b.days.isInfinite()) "Solar covers the load this month."
                else "Storage lasts about ${b.days.d1()} days at this load.",
                color = if (b.days.isInfinite() || b.days >= p.scenarioDays) Green else Amber,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Sun-hours are rough UK averages and include no allowance for a run of grey days. " +
                    "Measure real figures and adjust.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Column(Modifier.padding(horizontal = 12.dp)) {
            SectionLabel("Solar panels (total watts)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlanNumber("Van W", p.vanSolarW, { v -> set { it.copy(vanSolarW = v) } }, Modifier.weight(1f))
                PlanNumber("Garden W", p.gardenSolarW, { v -> set { it.copy(gardenSolarW = v) } }, Modifier.weight(1f))
            }
            SectionLabel("Storage")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlanNumber("AFERIY Wh", p.stationWh, { v -> set { it.copy(stationWh = v) } }, Modifier.weight(1f))
                PlanNumber("AFERIY charge %", p.stationChargePct, { v -> set { it.copy(stationChargePct = v.coerceIn(0.0, 100.0)) } }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlanNumber("Van bank Ah", p.vanBankAh, { v -> set { it.copy(vanBankAh = v) } }, Modifier.weight(1f))
                PlanNumber("Garden bank Ah", p.gardenBankAh, { v -> set { it.copy(gardenBankAh = v) } }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlanNumber("Van usable 0-1", p.vanUsable, { v -> set { it.copy(vanUsable = v.coerceIn(0.0, 1.0)) } }, Modifier.weight(1f))
                PlanNumber("Garden usable 0-1", p.gardenUsable, { v -> set { it.copy(gardenUsable = v.coerceIn(0.0, 1.0)) } }, Modifier.weight(1f))
            }
            Text(
                "Use 0.9 for LiFePO4. If the garden batteries are lead-acid/AGM, use 0.5.",
                style = MaterialTheme.typography.bodySmall,
            )
            SectionLabel("Daily loads")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlanNumber("Fridge Wh/day", p.fridgeWhDay, { v -> set { it.copy(fridgeWhDay = v) } }, Modifier.weight(1f))
                PlanNumber("Phone Wh/day", p.phoneWhDay, { v -> set { it.copy(phoneWhDay = v) } }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlanNumber("ZBook watts", p.laptopW, { v -> set { it.copy(laptopW = v) } }, Modifier.weight(1f))
                PlanNumber("ZBook hours/day", p.laptopHoursDay, { v -> set { it.copy(laptopHoursDay = v) } }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlanNumber("Lights Wh/day", p.lightsWhDay, { v -> set { it.copy(lightsWhDay = v) } }, Modifier.weight(1f))
                PlanNumber("Other Wh/day", p.otherWhDay, { v -> set { it.copy(otherWhDay = v) } }, Modifier.weight(1f))
            }
            Text(
                "Read the ZBook's real draw from the AFERIY display while Ollama is answering, and enter it here.",
                style = MaterialTheme.typography.bodySmall,
            )
            SectionLabel("Propane & van water")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlanNumber("Gas left kg", p.gasKg, { v -> set { it.copy(gasKg = v) } }, Modifier.weight(1f))
                PlanNumber("Gas g/day", p.gasGramsPerDay, { v -> set { it.copy(gasGramsPerDay = v) } }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlanNumber("Burner g/min", p.gasGramsPerMinute, { v -> set { it.copy(gasGramsPerMinute = v) } }, Modifier.weight(1f))
                PlanNumber("Van tank L now", p.vanTankLitres, { v -> set { it.copy(vanTankLitres = v) } }, Modifier.weight(1f))
            }
            Text(
                "Weigh a bottle before and after a day's use to replace the g/day estimate with your real figure.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}

// ---------------- Plan settings ----------------

@Composable
fun PlanSettingsScreen(nav: Nav) {
    val p = Repo.plan
    fun set(f: (Plan) -> Plan) = Repo.updatePlan(f(Repo.plan))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        Header("Plan settings", onBack = { nav.back() })
        SectionLabel("People")
        Toggles(listOf(1, 2), p.people, { if (it == 1) "1 person" else "2 people" }) { v -> set { it.copy(people = v) } }
        SectionLabel("Scenario length")
        Toggles(listOf(3, 10, 30, 90), p.scenarioDays, { "$it d" }) { v -> set { it.copy(scenarioDays = v) } }
        Column(Modifier.padding(horizontal = 12.dp)) {
            SectionLabel("Targets per person per day")
            PlanNumber("kcal", p.kcalPerPerson.toDouble(), { v -> set { it.copy(kcalPerPerson = v.toInt().coerceAtLeast(500)) } })
            PlanNumber("Drinking water (L)", p.drinkLitres, { v -> set { it.copy(drinkLitres = v) } })
            PlanNumber("All water incl. cooking & hygiene (L)", p.comfortLitres, { v -> set { it.copy(comfortLitres = v) } })
            Text(
                "UK guidance: at least 3 L drinking water per person per day; about 10 L covers basic cooking and hygiene. " +
                    "2,500 kcal suits an active adult doing physical work such as cutting wood - adjust for yourself.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}

// ---------------- Reference ----------------

@Composable
fun ReferenceListScreen(nav: Nav) {
    val ctx = LocalContext.current
    val topics = remember { ReferenceLibrary.topics(ctx) }
    LazyColumn(Modifier.fillMaxSize()) {
        item { Header("Reference library", onBack = { nav.back() }) }
        items(topics, key = { it.file }) { t ->
            InfoCard(Modifier.clickable { nav.go(Screen.ReferencePage(t.file)) }) { Text(t.title, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun ReferencePageScreen(nav: Nav, file: String) {
    val ctx = LocalContext.current
    val topic = remember(file) { ReferenceLibrary.topics(ctx).firstOrNull { it.file == file } }
    val blocks = remember(file) { topic?.let { ReferenceLibrary.blocks(it.body) } ?: emptyList() }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { Header(topic?.title ?: "Not found", onBack = { nav.back() }) }
        items(blocks.size) { i ->
            val b = blocks[i]
            when (b.kind) {
                ReferenceLibrary.Kind.H1 -> {}
                ReferenceLibrary.Kind.H2 -> Text(
                    b.text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                ReferenceLibrary.Kind.BULLET -> Text("•  ${b.text}", modifier = Modifier.padding(start = 6.dp, bottom = 3.dp))
                ReferenceLibrary.Kind.NUMBERED -> Text(b.text, modifier = Modifier.padding(start = 6.dp, bottom = 3.dp))
                ReferenceLibrary.Kind.WARNING -> Text(
                    "⚠ ${b.text}", color = Amber, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp),
                )
                ReferenceLibrary.Kind.PARA -> Text(b.text, modifier = Modifier.padding(bottom = 8.dp))
                ReferenceLibrary.Kind.IMAGE -> DiagramBlock(b.image ?: "", b.text)
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun DiagramBlock(file: String, caption: String) {
    val ctx = LocalContext.current
    val bmp = remember(file) { ReferenceLibrary.image(ctx, file) }
    var zoom by remember { mutableStateOf(false) }
    if (bmp == null) {
        Text("[diagram missing: $file]", color = Red)
        return
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { zoom = true }) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = caption,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height),
        )
        Text(
            if (caption.isBlank()) "Tap to zoom" else "$caption  ·  tap to zoom",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (zoom) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Dialog(onDismissRequest = { zoom = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                Modifier.fillMaxSize().background(Color.White).pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoomChange, _ ->
                        scale = (scale * zoomChange).coerceIn(1f, 6f)
                        offset = if (scale <= 1f) Offset.Zero else offset + pan
                    }
                },
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = caption,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offset.x; translationY = offset.y
                    },
                )
                TextButton(onClick = { zoom = false }, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                    Text("Close ✕", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Pinch to zoom, drag to move",
                    color = Color.DarkGray,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            }
        }
    }
}

// ---------------- Print & backup ----------------

@Composable
fun BackupScreen(nav: Nav) {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingRestore = uri }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("Print & backup", onBack = { nav.back() })
        InfoCard {
            Text("Printable PDF", fontWeight = FontWeight.Bold)
            Text(
                "A4, each section starts on a new sheet, even page count - print double-sided (flip on long edge). " +
                    "Includes readiness, inventory by location, both checklists, routine and the full reference library.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = {
                status = runCatching { "Saved: " + Exporter.exportPdf(ctx) }.getOrElse { "PDF failed: ${it.message}" }
            }, modifier = Modifier.padding(top = 8.dp)) { Text("Create PDF") }
        }
        InfoCard {
            Text("Backup", fontWeight = FontWeight.Bold)
            Text("Stock, barcode memory, routine and settings as JSON. Photos are not included.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = {
                    status = runCatching { "Saved: " + Exporter.exportBackup(ctx) }.getOrElse { "Backup failed: ${it.message}" }
                }) { Text("Create backup") }
                OutlinedButton(onClick = { picker.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }) { Text("Restore…") }
            }
        }
        InfoCard {
            Text("Getting files to and from your laptop", fontWeight = FontWeight.Bold)
            Text("adb pull /sdcard/Download/UKPrepper/ ./prepper-files/", style = MaterialTheme.typography.bodySmall)
            Text("adb push backup.json /sdcard/Download/   (then Restore…)", style = MaterialTheme.typography.bodySmall)
        }
        status?.let { InfoCard { Text(it) } }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Replace all data?") },
            text = { Text("Restoring replaces your current stock, routine and settings with the backup. Make a backup first if unsure.") },
            confirmButton = {
                TextButton(onClick = {
                    status = runCatching { "Restored ${Exporter.importBackup(ctx, uri)} stock lines." }.getOrElse { "Restore failed: ${it.message}" }
                    pendingRestore = null
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
}
