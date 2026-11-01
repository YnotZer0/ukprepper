package uk.prepper.app.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.prepper.app.Nav
import uk.prepper.app.Screen
import uk.prepper.app.data.Repo
import uk.prepper.app.logic.Readiness
import uk.prepper.app.model.Category
import uk.prepper.app.model.Item
import uk.prepper.app.model.Place
import java.io.File
import java.time.LocalDate

@Composable
fun InventoryScreen(nav: Nav) {
    val v = Repo.version.intValue
    val all = remember(v) { Repo.items() }
    var place by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    val today = LocalDate.now()

    val shown = all.filter { i ->
        (place == null || i.place.name == place) &&
            (search.isBlank() || i.name.contains(search, ignoreCase = true))
    }
    val grouped = shown.groupBy { it.category }.toSortedMap(compareBy<Category> { it.ordinal })

    LazyColumn(Modifier.fillMaxSize()) {
        item { Header("Stock (${all.size} lines)") }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { nav.go(Screen.EditItem()) }, modifier = Modifier.weight(1f)) { Text("+ Add") }
                Button(onClick = { nav.go(Screen.Scan) }, modifier = Modifier.weight(1f)) { Text("Scan") }
                OutlinedButton(onClick = { nav.go(Screen.EditItem(ration = true)) }, modifier = Modifier.weight(1.3f)) { Text("+ Rations") }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 12.dp)) {
                Field("Search", search, { search = it })
                Picker(
                    "Location",
                    listOf<Place?>(null) + Place.entries,
                    place?.let { Place.from(it) },
                    { it?.label ?: "All" },
                    { place = it?.name },
                    Modifier.fillMaxWidth(),
                )
            }
        }
        grouped.forEach { (cat, list) ->
            item(key = "h${cat.name}") { SectionLabel("${cat.label} (${list.size})") }
            items(list, key = { it.id }) { i -> ItemRow(i, today, onClick = { nav.go(Screen.EditItem(id = i.id)) }) }
        }
        if (shown.isEmpty()) item { InfoCard { Text("Nothing here yet. Add, scan, or add a ration-pack batch.") } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ItemRow(i: Item, today: LocalDate, onClick: () -> Unit) {
    val exp = Readiness.parseDate(i.expiry)
    val expColor = when {
        exp == null -> MaterialTheme.colorScheme.onSurfaceVariant
        exp.isBefore(today) -> Red
        !exp.isAfter(today.plusDays(Readiness.EXPIRY_WARNING_DAYS)) -> Amber
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    InfoCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Thumb(i.photoPath, 44.dp)
            if (i.photoPath != null) Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(i.name, fontWeight = FontWeight.SemiBold)
                val bits = listOfNotNull(i.place.label, i.expiry?.let { "BB $it" }, if (i.takeInVan) "→ van" else null)
                Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = expColor)
            }
            TextButton(onClick = { Repo.adjustQuantity(i.id, -1.0) }) { Text("−") }
            Text("${i.quantity.q()} ${i.unit}", fontWeight = FontWeight.Bold)
            TextButton(onClick = { Repo.adjustQuantity(i.id, 1.0) }) { Text("+") }
        }
    }
}

@Composable
fun EditItemScreen(nav: Nav, id: Long, barcode: String?, ration: Boolean) {
    val existing = remember(id) { if (id != 0L) Repo.item(id) else null }
    val product = remember(barcode) { barcode?.let { Repo.product(it) } }

    var name by rememberSaveable { mutableStateOf(existing?.name ?: product?.name ?: if (ration) "Ration pack meal (boil-in-bag)" else "") }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: product?.category ?: if (ration) Category.RATION else Category.FOOD) }
    var place by rememberSaveable { mutableStateOf(existing?.place ?: if (ration) Place.GARDEN else Place.HOME) }
    var qty by rememberSaveable { mutableStateOf(existing?.quantity?.q() ?: "1") }
    var unit by rememberSaveable { mutableStateOf(existing?.unit ?: product?.unit ?: if (ration) "meal" else "each") }
    var expiry by rememberSaveable { mutableStateOf(existing?.expiry ?: if (ration) "2027-07-31" else null) }
    var code by rememberSaveable { mutableStateOf(existing?.barcode ?: barcode ?: "") }
    var kcal by rememberSaveable { mutableStateOf((existing?.kcalPerUnit ?: product?.kcalPerUnit)?.takeIf { it > 0 }?.q() ?: "") }
    var litres by rememberSaveable { mutableStateOf((existing?.litresPerUnit ?: product?.litresPerUnit)?.takeIf { it > 0 }?.q() ?: "") }
    var heat by rememberSaveable { mutableStateOf((existing?.heatMinutes ?: product?.heatMinutes ?: if (ration) 8 else 0).toString()) }
    var waterMl by rememberSaveable { mutableStateOf((existing?.waterMlPerUnit ?: product?.waterMlPerUnit ?: 0).toString()) }
    var takeVan by rememberSaveable { mutableStateOf(existing?.takeInVan ?: ration) }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    var photo by rememberSaveable { mutableStateOf(existing?.photoPath) }
    var camera by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    if (camera) {
        PhotoCapture(onDone = { p -> photo?.let { old -> if (old != existing?.photoPath) File(old).delete() }; photo = p; camera = false }, onCancel = { camera = false })
        return
    }

    val title = when { existing != null -> "Edit item"; ration -> "Add ration-pack batch"; product != null -> "Add (known barcode)"; else -> "Add item" }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        Header(title, onBack = { nav.back() })
        Column(Modifier.padding(horizontal = 12.dp)) {
            if (ration) Text(
                "One line per best-before batch. Enter kcal per meal from the pack label.",
                style = MaterialTheme.typography.bodySmall,
            )
            Field("Name", name, { name = it })
            Picker("Category", Category.entries, category, { it.label }, { category = it }, Modifier.fillMaxWidth())
            Picker("Location", Place.entries, place, { it.label }, { place = it }, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Quantity", qty, { qty = it }, Modifier.weight(1f), number = true)
                Field("Unit (tin, meal, kg, roll…)", unit, { unit = it }, Modifier.weight(1.4f))
            }
            DateField("Best before", expiry) { expiry = it }

            if (category.isFood) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("kcal per $unit", kcal, { kcal = it }, Modifier.weight(1f), number = true)
                    Field("Heat mins (0 = cold)", heat, { heat = it }, Modifier.weight(1f), number = true)
                }
                Field("Drinking water used per $unit (ml)", waterMl, { waterMl = it }, number = true)
            }
            if (category == Category.WATER) Field("Litres per $unit", litres, { litres = it }, number = true)

            Field("Barcode", code, { code = it }, number = false)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                Text("Take if leaving in the van", Modifier.weight(1f))
                Switch(checked = takeVan, onCheckedChange = { takeVan = it })
            }
            Field("Notes", notes, { notes = it }, singleLine = false)

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                Thumb(photo, 72.dp, sample = 8)
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = { camera = true }) { Text(if (photo == null) "Take photo" else "Retake photo") }
                if (photo != null) TextButton(onClick = { photo = null }) { Text("Remove") }
            }

            error?.let { Text(it, color = Red) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 10.dp)) {
                Button(onClick = {
                    val q = qty.replace(',', '.').toDoubleOrNull()
                    when {
                        name.isBlank() -> error = "Name is required"
                        q == null || q < 0 -> error = "Quantity must be a number"
                        unit.isBlank() -> error = "Unit is required"
                        else -> {
                            Repo.saveItem(
                                Item(
                                    id = existing?.id ?: 0,
                                    name = name.trim(), category = category, place = place,
                                    quantity = q, unit = unit.trim(), expiry = expiry,
                                    barcode = code.trim().ifBlank { null },
                                    kcalPerUnit = kcal.replace(',', '.').toDoubleOrNull() ?: 0.0,
                                    litresPerUnit = litres.replace(',', '.').toDoubleOrNull() ?: 0.0,
                                    heatMinutes = heat.toIntOrNull() ?: 0,
                                    waterMlPerUnit = waterMl.toIntOrNull() ?: 0,
                                    takeInVan = takeVan, photoPath = photo, notes = notes.trim(),
                                )
                            )
                            nav.back()
                        }
                    }
                }, modifier = Modifier.weight(1f)) { Text("Save") }
                if (existing != null) OutlinedButton(onClick = { confirmDelete = true }) { Text("Delete") }
            }
        }
        Spacer(Modifier.height(40.dp))
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${existing.name}?") },
            text = { Text("This removes the stock line. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    existing.photoPath?.let { File(it).delete() }
                    Repo.deleteItem(existing.id)
                    confirmDelete = false
                    nav.back()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
