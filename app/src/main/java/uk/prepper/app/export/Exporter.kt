package uk.prepper.app.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import uk.prepper.app.data.PlanStore
import uk.prepper.app.data.Repo
import uk.prepper.app.logic.Checklists
import uk.prepper.app.logic.PrepMode
import uk.prepper.app.logic.Readiness
import uk.prepper.app.model.ActivityTask
import uk.prepper.app.model.Category
import uk.prepper.app.model.Item
import uk.prepper.app.model.Place
import uk.prepper.app.model.Product
import uk.prepper.app.reference.ReferenceLibrary
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Files go to Download/UKPrepper on the phone, so you can fetch them with:
 *   adb pull /sdcard/Download/UKPrepper/
 */
object Exporter {
    private const val FOLDER = "Download/UKPrepper"

    private fun stamp(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))

    private fun create(ctx: Context, name: String, mime: String, write: (OutputStream) -> Unit): String {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, FOLDER)
        }
        val resolver = ctx.contentResolver
        val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create $name")
        resolver.openOutputStream(uri)?.use(write) ?: error("Could not open $name")
        return "$FOLDER/$name"
    }

    // ---------------- JSON backup ----------------

    fun backupJson(): JSONObject {
        val items = JSONArray()
        Repo.items().forEach { i ->
            items.put(JSONObject().apply {
                put("name", i.name); put("category", i.category.name); put("place", i.place.name)
                put("quantity", i.quantity); put("unit", i.unit); put("expiry", i.expiry ?: "")
                put("barcode", i.barcode ?: ""); put("kcal", i.kcalPerUnit); put("litres", i.litresPerUnit)
                put("heatMinutes", i.heatMinutes); put("waterMl", i.waterMlPerUnit)
                put("takeInVan", i.takeInVan); put("notes", i.notes)
            })
        }
        val products = JSONArray()
        Repo.products().forEach { p ->
            products.put(JSONObject().apply {
                put("barcode", p.barcode); put("name", p.name); put("category", p.category.name); put("unit", p.unit)
                put("kcal", p.kcalPerUnit); put("litres", p.litresPerUnit); put("heatMinutes", p.heatMinutes); put("waterMl", p.waterMlPerUnit)
            })
        }
        val acts = JSONArray()
        Repo.activities().forEach { a -> acts.put(JSONObject().apply { put("time", a.time); put("title", a.title); put("minutes", a.minutes) }) }
        return JSONObject().apply {
            put("format", "ukprepper-backup"); put("version", 1); put("created", LocalDateTime.now().toString())
            put("plan", PlanStore.toJson(Repo.plan)); put("items", items); put("products", products); put("activities", acts)
        }
    }

    fun exportBackup(ctx: Context): String =
        create(ctx, "ukprepper-backup-${stamp()}.json", "application/json") { it.write(backupJson().toString(2).toByteArray()) }

    /** Replaces all data with the backup. Photos are not included in backups. */
    fun importBackup(ctx: Context, uri: Uri): Int {
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Could not read file")
        val o = JSONObject(text)
        require(o.optString("format") == "ukprepper-backup") { "Not a UK Prepper backup file" }
        val items = mutableListOf<Item>()
        val ia = o.getJSONArray("items")
        for (k in 0 until ia.length()) {
            val j = ia.getJSONObject(k)
            items += Item(
                name = j.getString("name"), category = Category.from(j.optString("category")), place = Place.from(j.optString("place")),
                quantity = j.optDouble("quantity", 0.0), unit = j.optString("unit"),
                expiry = j.optString("expiry").ifBlank { null }, barcode = j.optString("barcode").ifBlank { null },
                kcalPerUnit = j.optDouble("kcal", 0.0), litresPerUnit = j.optDouble("litres", 0.0),
                heatMinutes = j.optInt("heatMinutes", 0), waterMlPerUnit = j.optInt("waterMl", 0),
                takeInVan = j.optBoolean("takeInVan", false), notes = j.optString("notes"),
            )
        }
        val products = mutableListOf<Product>()
        val pa = o.optJSONArray("products") ?: JSONArray()
        for (k in 0 until pa.length()) {
            val j = pa.getJSONObject(k)
            products += Product(
                j.getString("barcode"), j.optString("name"), Category.from(j.optString("category")), j.optString("unit"),
                j.optDouble("kcal", 0.0), j.optDouble("litres", 0.0), j.optInt("heatMinutes", 0), j.optInt("waterMl", 0),
            )
        }
        val acts = mutableListOf<ActivityTask>()
        val aa = o.optJSONArray("activities") ?: JSONArray()
        for (k in 0 until aa.length()) {
            val j = aa.getJSONObject(k)
            acts += ActivityTask(time = j.optString("time"), title = j.optString("title"), minutes = j.optInt("minutes", 0))
        }
        val plan = o.optJSONObject("plan")?.let { PlanStore.fromJson(it) } ?: Repo.plan
        Repo.replaceAll(items, products, acts, plan)
        return items.size
    }

    // ---------------- PDF (A4, laid out for double-sided printing) ----------------

    private class PdfWriter(private val doc: PdfDocument) {
        val pageW = 595; val pageH = 842; val margin = 42f
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        var pageNo = 0; private set
        private var y = 0f
        private var section = ""

        val body = Paint().apply { textSize = 10f; isAntiAlias = true; typeface = Typeface.SERIF }
        val bold = Paint().apply { textSize = 10f; isAntiAlias = true; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
        val h1 = Paint().apply { textSize = 18f; isAntiAlias = true; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        val h2 = Paint().apply { textSize = 13f; isAntiAlias = true; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        val small = Paint().apply { textSize = 8f; isAntiAlias = true; typeface = Typeface.SANS_SERIF }

        private fun newPage() {
            finishPage()
            pageNo++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            page = p; canvas = p.canvas; y = margin
            canvas?.drawText("UK Prepper · $section", margin, pageH - 22f, small)
            val num = "Page $pageNo"
            canvas?.drawText(num, pageW - margin - small.measureText(num), pageH - 22f, small)
        }

        fun finishPage() { page?.let { doc.finishPage(it) }; page = null; canvas = null }

        /** Start a new section on a right-hand (odd) page, so each section prints on its own sheet front. */
        fun startSection(title: String) {
            section = title
            finishPage()
            if (pageNo % 2 == 1) { // next page would be even: insert a blank back side
                pageNo++
                doc.finishPage(doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create()))
            }
            newPage()
            line(title, h1, 26f)
        }

        private fun ensure(h: Float) { if (canvas == null || y + h > pageH - margin - 14f) newPage() }

        fun line(text: String, paint: Paint = body, lead: Float = 14f, indent: Float = 0f) {
            val maxW = pageW - 2 * margin - indent
            wrap(text, paint, maxW).forEach { l ->
                ensure(lead)
                y += lead
                canvas?.drawText(l, margin + indent, y, paint)
            }
        }

        fun gap(h: Float = 6f) { y += h }

        /** Draw a diagram at full text width (shrunk to fit a page if very tall), with a caption. */
        fun image(bmp: Bitmap, caption: String) {
            val maxW = pageW - 2 * margin
            val maxH = pageH - 2 * margin - 60f
            var w = maxW
            var h = bmp.height * (w / bmp.width)
            if (h > maxH) { h = maxH; w = bmp.width * (h / bmp.height) }
            ensure(h + 30f)
            val c = canvas ?: return
            val left = margin + (maxW - w) / 2f
            c.drawBitmap(bmp, null, RectF(left, y + 6f, left + w, y + 6f + h), Paint().apply { isFilterBitmap = true })
            y += h + 6f
            if (caption.isNotBlank()) line(caption, small, 12f)
            gap(8f)
        }

        fun checkbox(text: String, detail: String = "") {
            ensure(16f)
            val c = canvas
            if (c != null) {
                val box = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 0.8f }
                c.drawRect(margin, y + 4f, margin + 9f, y + 13f, box)
            }
            line(text, bold, 14f, 16f)
            if (detail.isNotBlank()) line(detail, body, 12f, 16f)
            gap(2f)
        }

        private fun wrap(text: String, paint: Paint, maxW: Float): List<String> {
            val out = mutableListOf<String>()
            var cur = StringBuilder()
            for (word in text.split(' ')) {
                val trial = if (cur.isEmpty()) word else "$cur $word"
                if (paint.measureText(trial) <= maxW) { cur = StringBuilder(trial) }
                else { if (cur.isNotEmpty()) out += cur.toString(); cur = StringBuilder(word) }
            }
            if (cur.isNotEmpty() || out.isEmpty()) out += cur.toString()
            return out
        }
    }

    fun exportPdf(ctx: Context): String {
        val doc = PdfDocument()
        val w = PdfWriter(doc)
        val items = Repo.items()
        val plan = Repo.plan
        val today = LocalDate.now()
        val month = today.monthValue
        val qtyFmt: (Double) -> String = { if (it % 1.0 == 0.0) it.toLong().toString() else "%.2f".format(it) }

        // Readiness summary
        w.startSection("Readiness summary - ${today}")
        w.line("Scenario: ${plan.scenarioDays} days · ${plan.people} person(s) · ${plan.kcalPerPerson} kcal/person/day")
        w.gap()
        Readiness.coverage(items, plan, month).forEach {
            val d = if (it.days.isInfinite()) "self-sustaining" else "%.1f days".format(it.days)
            w.line("${it.label}: ${it.have} → $d  [${it.rag}]", w.bold)
            w.line(it.note, w.body, 12f, 12f)
        }
        w.gap(10f)
        w.line("Shopping / gap list", w.h2, 20f)
        Readiness.gaps(items, plan, month, today).forEach { w.checkbox(it) }

        // Inventory
        w.startSection("Inventory by location")
        Place.entries.forEach { place ->
            val here = items.filter { it.place == place }
            if (here.isEmpty()) return@forEach
            w.line(place.label, w.h2, 22f)
            here.groupBy { it.category }.forEach { (cat, list) ->
                w.line(cat.label, w.bold, 16f)
                list.forEach { i ->
                    val exp = i.expiry?.let { " · BB $it" } ?: ""
                    val van = if (i.takeInVan) " · VAN" else ""
                    w.line("[  ] ${i.name} - ${qtyFmt(i.quantity)} ${i.unit}$exp$van", w.body, 13f, 10f)
                }
            }
        }

        // Checklists
        PrepMode.entries.forEach { mode ->
            w.startSection("Prepare now: ${mode.label}")
            var lastSection = ""
            Checklists.build(mode, items, plan, month).forEach { t ->
                if (t.section != lastSection) { w.line(t.section, w.h2, 22f); lastSection = t.section }
                w.checkbox(t.title, t.detail)
            }
        }

        // Daily routine
        w.startSection("Daily routine")
        Repo.activities().forEach { w.checkbox("${it.time}  ${it.title}", if (it.minutes > 0) "${it.minutes} min" else "") }

        // Reference library
        ReferenceLibrary.topics(ctx).forEach { topic ->
            w.startSection(topic.title)
            ReferenceLibrary.blocks(topic.body).forEach { b ->
                when (b.kind) {
                    ReferenceLibrary.Kind.H1 -> {}
                    ReferenceLibrary.Kind.H2 -> w.line(b.text, w.h2, 22f)
                    ReferenceLibrary.Kind.BULLET -> w.line("•  ${b.text}", w.body, 13f, 8f)
                    ReferenceLibrary.Kind.NUMBERED -> w.line(b.text, w.body, 13f, 8f)
                    ReferenceLibrary.Kind.WARNING -> w.line("WARNING: ${b.text}", w.bold, 14f)
                    ReferenceLibrary.Kind.PARA -> { w.line(b.text); w.gap(3f) }
                    ReferenceLibrary.Kind.IMAGE -> {
                        val bmp = b.image?.let { ReferenceLibrary.image(ctx, it) }
                        if (bmp != null) { w.image(bmp, b.text); bmp.recycle() }
                    }
                }
            }
        }

        w.finishPage()
        // Even page count so the last sheet is complete when printing double-sided.
        if (w.pageNo % 2 == 1) doc.finishPage(doc.startPage(PdfDocument.PageInfo.Builder(w.pageW, w.pageH, w.pageNo + 1).create()))

        return try {
            create(ctx, "ukprepper-print-${stamp()}.pdf", "application/pdf") { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }
}
