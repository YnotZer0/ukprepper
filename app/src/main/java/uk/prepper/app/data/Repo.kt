package uk.prepper.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uk.prepper.app.model.ActivityTask
import uk.prepper.app.model.Category
import uk.prepper.app.model.DrillRecord
import uk.prepper.app.model.Item
import uk.prepper.app.model.Place
import uk.prepper.app.model.Plan
import uk.prepper.app.model.Product

private class DbHelper(ctx: Context) : SQLiteOpenHelper(ctx, "prepper.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE items(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL, category TEXT NOT NULL, place TEXT NOT NULL,
                quantity REAL NOT NULL, unit TEXT NOT NULL, expiry TEXT, barcode TEXT,
                kcal REAL NOT NULL DEFAULT 0, litres REAL NOT NULL DEFAULT 0,
                heat_min INTEGER NOT NULL DEFAULT 0, water_ml INTEGER NOT NULL DEFAULT 0,
                take_van INTEGER NOT NULL DEFAULT 0, photo TEXT, notes TEXT NOT NULL DEFAULT '')"""
        )
        db.execSQL(
            """CREATE TABLE products(
                barcode TEXT PRIMARY KEY, name TEXT NOT NULL, category TEXT NOT NULL, unit TEXT NOT NULL,
                kcal REAL NOT NULL, litres REAL NOT NULL, heat_min INTEGER NOT NULL, water_ml INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE activities(
                id INTEGER PRIMARY KEY AUTOINCREMENT, time TEXT NOT NULL, title TEXT NOT NULL,
                minutes INTEGER NOT NULL, last_done TEXT)"""
        )
        db.execSQL(
            """CREATE TABLE drills(
                id INTEGER PRIMARY KEY AUTOINCREMENT, mode TEXT NOT NULL, started INTEGER NOT NULL,
                seconds INTEGER NOT NULL, total INTEGER NOT NULL, done INTEGER NOT NULL, missing TEXT NOT NULL)"""
        )
        DefaultRoutine.tasks.forEach { (time, title, minutes) ->
            db.insert("activities", null, ContentValues().apply {
                put("time", time); put("title", title); put("minutes", minutes)
            })
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future schema changes go here. Never drop user data.
    }
}

object DefaultRoutine {
    val tasks: List<Triple<String, String, Int>> = listOf(
        Triple("07:00", "Get up, wash, check water and battery levels", 30),
        Triple("07:30", "Breakfast and a hot drink", 30),
        Triple("08:30", "Collect and treat water (strain, Sawyer, tablets/boil)", 60),
        Triple("10:00", "Firewood: saw, split and stack under cover", 90),
        Triple("12:30", "Lunch", 30),
        Triple("13:30", "Exercise: brisk walk or bodyweight circuit", 30),
        Triple("14:15", "Maintenance: toilet cover material, bins, van check", 45),
        Triple("15:00", "Skill / learning time (Kiwix, books, repairs)", 60),
        Triple("17:30", "Dinner", 45),
        Triple("18:30", "Radio news and weather", 20),
        Triple("19:00", "Log stock used today; plan tomorrow", 15),
        Triple("21:00", "Wind down, warm bed, lights out", 0),
    )
}

/**
 * Single source of truth. Database calls are small and fast (hundreds of rows),
 * so they run synchronously. [version] increments on every change so screens
 * re-read their data.
 */
object Repo {
    private lateinit var helper: SQLiteOpenHelper
    private lateinit var appContext: Context

    val version = mutableIntStateOf(0)
    var plan by mutableStateOf(Plan())
        private set

    fun init(ctx: Context) {
        if (::helper.isInitialized) return
        appContext = ctx.applicationContext
        helper = DbHelper(appContext)
        plan = PlanStore.load(appContext)
    }

    private val db: SQLiteDatabase get() = helper.writableDatabase
    private fun bump() { version.intValue = version.intValue + 1 }

    fun updatePlan(p: Plan) {
        plan = p
        PlanStore.save(appContext, p)
        bump()
    }

    // ---------- items ----------
    private fun Cursor.str(c: String): String? = getColumnIndex(c).let { if (it < 0 || isNull(it)) null else getString(it) }
    private fun Cursor.dbl(c: String): Double = getColumnIndex(c).let { if (it < 0 || isNull(it)) 0.0 else getDouble(it) }
    private fun Cursor.lng(c: String): Long = getColumnIndex(c).let { if (it < 0 || isNull(it)) 0L else getLong(it) }

    private fun Cursor.toItem() = Item(
        id = lng("id"),
        name = str("name") ?: "",
        category = Category.from(str("category")),
        place = Place.from(str("place")),
        quantity = dbl("quantity"),
        unit = str("unit") ?: "",
        expiry = str("expiry"),
        barcode = str("barcode"),
        kcalPerUnit = dbl("kcal"),
        litresPerUnit = dbl("litres"),
        heatMinutes = lng("heat_min").toInt(),
        waterMlPerUnit = lng("water_ml").toInt(),
        takeInVan = lng("take_van") != 0L,
        photoPath = str("photo"),
        notes = str("notes") ?: "",
    )

    fun items(): List<Item> = db.rawQuery("SELECT * FROM items ORDER BY category, name, expiry", null).use { c ->
        val out = ArrayList<Item>(c.count)
        while (c.moveToNext()) out += c.toItem()
        out
    }

    fun item(id: Long): Item? = db.rawQuery("SELECT * FROM items WHERE id=?", arrayOf(id.toString())).use { c ->
        if (c.moveToFirst()) c.toItem() else null
    }

    private fun Item.values() = ContentValues().apply {
        put("name", name); put("category", category.name); put("place", place.name)
        put("quantity", quantity); put("unit", unit); put("expiry", expiry); put("barcode", barcode)
        put("kcal", kcalPerUnit); put("litres", litresPerUnit); put("heat_min", heatMinutes)
        put("water_ml", waterMlPerUnit); put("take_van", if (takeInVan) 1 else 0)
        put("photo", photoPath); put("notes", notes)
    }

    fun saveItem(item: Item): Long {
        val id = if (item.id == 0L) db.insert("items", null, item.values())
        else { db.update("items", item.values(), "id=?", arrayOf(item.id.toString())); item.id }
        val code = item.barcode
        if (!code.isNullOrBlank()) saveProduct(
            Product(code, item.name, item.category, item.unit, item.kcalPerUnit, item.litresPerUnit, item.heatMinutes, item.waterMlPerUnit)
        )
        bump()
        return id
    }

    fun deleteItem(id: Long) { db.delete("items", "id=?", arrayOf(id.toString())); bump() }

    fun adjustQuantity(id: Long, delta: Double) {
        db.execSQL("UPDATE items SET quantity = MAX(0, quantity + ?) WHERE id = ?", arrayOf<Any>(delta, id))
        bump()
    }

    // ---------- products (barcode memory) ----------
    fun product(barcode: String): Product? =
        db.rawQuery("SELECT * FROM products WHERE barcode=?", arrayOf(barcode)).use { c ->
            if (!c.moveToFirst()) null else Product(
                barcode = c.str("barcode") ?: barcode, name = c.str("name") ?: "",
                category = Category.from(c.str("category")), unit = c.str("unit") ?: "",
                kcalPerUnit = c.dbl("kcal"), litresPerUnit = c.dbl("litres"),
                heatMinutes = c.lng("heat_min").toInt(), waterMlPerUnit = c.lng("water_ml").toInt(),
            )
        }

    fun products(): List<Product> = db.rawQuery("SELECT barcode FROM products", null).use { c ->
        val codes = ArrayList<String>()
        while (c.moveToNext()) codes += c.getString(0)
        codes
    }.mapNotNull { product(it) }

    private fun saveProduct(p: Product) {
        db.insertWithOnConflict("products", null, ContentValues().apply {
            put("barcode", p.barcode); put("name", p.name); put("category", p.category.name); put("unit", p.unit)
            put("kcal", p.kcalPerUnit); put("litres", p.litresPerUnit); put("heat_min", p.heatMinutes); put("water_ml", p.waterMlPerUnit)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ---------- activities ----------
    fun activities(): List<ActivityTask> = db.rawQuery("SELECT * FROM activities ORDER BY time", null).use { c ->
        val out = ArrayList<ActivityTask>()
        while (c.moveToNext()) out += ActivityTask(
            c.lng("id"), c.str("time") ?: "", c.str("title") ?: "", c.lng("minutes").toInt(), c.str("last_done")
        )
        out
    }

    fun saveActivity(a: ActivityTask) {
        val v = ContentValues().apply { put("time", a.time); put("title", a.title); put("minutes", a.minutes); put("last_done", a.lastDone) }
        if (a.id == 0L) db.insert("activities", null, v) else db.update("activities", v, "id=?", arrayOf(a.id.toString()))
        bump()
    }

    fun deleteActivity(id: Long) { db.delete("activities", "id=?", arrayOf(id.toString())); bump() }

    // ---------- drills ----------
    fun drills(): List<DrillRecord> = db.rawQuery("SELECT * FROM drills ORDER BY started DESC", null).use { c ->
        val out = ArrayList<DrillRecord>()
        while (c.moveToNext()) out += DrillRecord(
            c.lng("id"), c.str("mode") ?: "", c.lng("started"), c.lng("seconds"),
            c.lng("total").toInt(), c.lng("done").toInt(), c.str("missing") ?: ""
        )
        out
    }

    fun addDrill(d: DrillRecord) {
        db.insert("drills", null, ContentValues().apply {
            put("mode", d.mode); put("started", d.startedAt); put("seconds", d.seconds)
            put("total", d.total); put("done", d.done); put("missing", d.missing)
        })
        bump()
    }

    // ---------- restore ----------
    fun replaceAll(items: List<Item>, products: List<Product>, activities: List<ActivityTask>, plan: Plan) {
        val d = db
        d.beginTransaction()
        try {
            d.delete("items", null, null)
            d.delete("products", null, null)
            d.delete("activities", null, null)
            items.forEach { d.insert("items", null, it.copy(id = 0).values()) }
            products.forEach { saveProduct(it) }
            activities.forEach {
                d.insert("activities", null, ContentValues().apply {
                    put("time", it.time); put("title", it.title); put("minutes", it.minutes)
                })
            }
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
        updatePlan(plan)
    }
}
