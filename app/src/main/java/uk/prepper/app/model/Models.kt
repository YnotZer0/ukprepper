package uk.prepper.app.model

// Pure Kotlin (no Android imports) so the logic can be unit-tested on any JVM.

enum class Category(val label: String) {
    WATER("Water (drinking)"),
    FOOD("Food"),
    RATION("Ration packs (boil-in-bag)"),
    WATER_TREATMENT("Water treatment"),
    COOKING("Cooking & fuel"),
    FIRE("Fire making"),
    LIGHTING("Lighting"),
    POWER("Batteries & power"),
    COMMS("Radio & comms"),
    HYGIENE("Toiletries & hygiene"),
    SANITATION("Toilet & sanitation"),
    MEDICAL("First aid & medical"),
    CLOTHING("Clothing"),
    BEDDING("Bedding & shelter"),
    KITCHEN("Kitchen & cutlery"),
    TOOLS("Tools"),
    DOCS("Documents & cash"),
    OTHER("Other");

    val isFood: Boolean get() = this == FOOD || this == RATION

    companion object {
        fun from(name: String?): Category = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

enum class Place(val label: String) {
    HOME("Home"),
    VAN("Camper van"),
    GARDEN("Garden store"),
    GRAB_BAG("Grab bag");

    companion object {
        fun from(name: String?): Place = entries.firstOrNull { it.name == name } ?: HOME
    }
}

/**
 * One stock line. Batches of the same product with different best-before
 * dates should be separate lines so the oldest is used first.
 *
 * kcalPerUnit   - food energy per unit (from the label)
 * litresPerUnit - drinking water per unit (e.g. 2.0 for a 2 L bottle)
 * heatMinutes   - minutes on the hob to prepare one portion (0 = eat cold)
 * waterMlPerUnit- drinking-quality water consumed to prepare a unit (e.g. pasta).
 *                 Boil-in-bag water is re-usable for washing, so use 0 for those.
 */
data class Item(
    val id: Long = 0,
    val name: String,
    val category: Category,
    val place: Place,
    val quantity: Double,
    val unit: String,
    val expiry: String? = null,          // ISO yyyy-MM-dd
    val barcode: String? = null,
    val kcalPerUnit: Double = 0.0,
    val litresPerUnit: Double = 0.0,
    val heatMinutes: Int = 0,
    val waterMlPerUnit: Int = 0,
    val takeInVan: Boolean = false,
    val photoPath: String? = null,
    val notes: String = "",
)

/** Remembered product details keyed by barcode, so a second scan fills the form. */
data class Product(
    val barcode: String,
    val name: String,
    val category: Category,
    val unit: String,
    val kcalPerUnit: Double,
    val litresPerUnit: Double,
    val heatMinutes: Int,
    val waterMlPerUnit: Int,
)

data class ActivityTask(
    val id: Long = 0,
    val time: String,        // "HH:mm"
    val title: String,
    val minutes: Int,
    val lastDone: String? = null, // ISO date it was last ticked
)

data class DrillRecord(
    val id: Long = 0,
    val mode: String,
    val startedAt: Long,
    val seconds: Long,
    val total: Int,
    val done: Int,
    val missing: String,
)

/** All user-adjustable planning figures. Defaults reflect the kit you described. */
data class Plan(
    val people: Int = 1,
    val scenarioDays: Int = 10,
    val kcalPerPerson: Int = 2500,
    val drinkLitres: Double = 3.0,       // UK gov / WHO minimum for drinking
    val comfortLitres: Double = 10.0,    // drinking + basic cooking & hygiene
    val vanTankLitres: Double = 70.0,    // current fill of the van tank
    val gasKg: Double = 12.0,            // propane remaining (2 x 6 kg full)
    val gasGramsPerDay: Double = 150.0,  // estimate: hot drinks, washing water, cooking
    val gasGramsPerMinute: Double = 2.0, // estimate: one burner on full
    val stationWh: Double = 2048.0,      // AFERIY P210
    val stationChargePct: Double = 100.0,
    val vanBankAh: Double = 100.0,       // 2 x 50 Ah LiFePO4
    val gardenBankAh: Double = 200.0,    // 2 x 100 Ah
    val bankVolts: Double = 12.8,
    val vanUsable: Double = 0.9,         // LiFePO4 usable fraction
    val gardenUsable: Double = 0.8,
    val vanSolarW: Double = 0.0,         // ENTER: total panel watts on the van
    val gardenSolarW: Double = 0.0,      // ENTER: total panel watts in the garden
    val fridgeWhDay: Double = 360.0,     // small 12 V compressor fridge, estimate
    val phoneWhDay: Double = 20.0,
    val lightsWhDay: Double = 30.0,
    val laptopW: Double = 90.0,          // HP ZBook under load, estimate - measure it
    val laptopHoursDay: Double = 1.0,
    val otherWhDay: Double = 0.0,
)
