package uk.prepper.app.logic

import uk.prepper.app.model.Category
import uk.prepper.app.model.Item
import uk.prepper.app.model.Place
import uk.prepper.app.model.Plan

data class Task(val id: String, val section: String, val title: String, val detail: String = "")

enum class PrepMode(val label: String) { STAY_HOME("Stay at home"), LEAVE_VAN("Leave in the van") }

object Checklists {

    fun build(mode: PrepMode, items: List<Item>, plan: Plan, month: Int): List<Task> =
        when (mode) {
            PrepMode.STAY_HOME -> stayHome(items, plan, month)
            PrepMode.LEAVE_VAN -> leaveVan(items, plan)
        }

    private fun fmtQty(i: Item): String {
        val q = if (i.quantity % 1.0 == 0.0) i.quantity.toLong().toString() else "%.2f".format(i.quantity)
        return "$q ${i.unit}"
    }

    fun stayHome(items: List<Item>, plan: Plan, month: Int): List<Task> {
        val t = mutableListOf<Task>()
        var n = 0
        fun add(section: String, title: String, detail: String = "") { t += Task("h${n++}", section, title, detail) }

        val s1 = "1. First hour - while services still work"
        add(s1, "Fill every water container, kettle and pan", "Then fill the bath - bath water is for flushing/washing only.")
        add(s1, "Charge the phone, power banks and AFERIY to 100%", "Use mains while it lasts.")
        add(s1, "Top up the van tank to 70 L", "Note the level in Settings.")
        add(s1, "Check propane: weigh or gauge both bottles", "Record kg remaining in Settings.")
        add(s1, "Get cash out / gather the cash you hold")
        add(s1, "Radio on: BBC local radio (FM) or Radio 4 (FM/DAB)", "Long wave 198 kHz ended in June 2026 - do not rely on it.")
        add(s1, "Eat fridge and freezer food first", "Keep doors shut; a full freezer stays cold longer.")

        val s2 = "2. Set up the home"
        add(s2, "Choose one warm room to live in", "Move bedding, duvets, sleeping bag and lighting there.")
        add(s2, "Lighting: torch by the bed, wind-up torch, candles in safe holders")
        add(s2, "Water station: labelled DIRTY and CLEAN containers", "Cloth strainer -> Sawyer -> tablets or rolling boil.")
        add(s2, "Toilet: garden twin pit ready - active pit open, cover material stocked, urine overflow to trench running")
        add(s2, "Backup toilet set up in the garage - separating box, urine bottle, lidded bucket, sawdust")
        add(s2, "Hand-wash station by the toilet and kitchen", "Water + soap; sanitiser as backup.")
        add(s2, "Move at least a week of dry firewood under cover")
        add(s2, "Plug the 12 V fridge into the van / power station; stop using mains fridge")

        val s3 = "3. Verify stock at home (from your inventory)"
        val home = items.filter { it.place == Place.HOME || it.place == Place.GARDEN }
        val cov = Readiness.coverage(home, plan, month)
        cov.filter { it.label != "Electricity" && it.label != "Propane" }.forEach {
            val d = if (it.days.isInfinite()) "∞" else "%.1f".format(it.days)
            add(s3, "${it.label}: ${it.have} → $d days (target ${it.target})", it.note)
        }
        Readiness.missingEssentials(home).forEach { add(s3, "Not at home: $it", "Find it or buy it.") }

        val s4 = "4. Daily rhythm"
        add(s4, "Start the activity schedule (Activities screen)")
        add(s4, "Each evening: log water used, gas used, food eaten")
        add(s4, "Each evening: check battery levels and plan tomorrow's power use")
        return t
    }

    fun leaveVan(items: List<Item>, plan: Plan): List<Task> {
        val t = mutableListOf<Task>()
        var n = 0
        fun add(section: String, title: String, detail: String = "") { t += Task("v${n++}", section, title, detail) }

        val s1 = "1. Before loading"
        add(s1, "Decide destination and two routes", "Offline maps on the phone; paper road atlas as backup.")
        add(s1, "Fuel: note the gauge and range - assume this is the only tank")
        add(s1, "Vehicle: tyres, oil, coolant, lights, wipers, screen wash")
        add(s1, "Fill the 70 L fresh-water tank")
        add(s1, "Both gas bottles: weigh, check regulator and hose, valve off for travel")
        add(s1, "Leisure batteries and AFERIY charged; 12 V fridge on and pre-cooled")

        val s2 = "2. Load (items marked 'take if leaving' or in the grab bag)"
        items.filter { it.quantity > 0 && (it.takeInVan || it.place == Place.GRAB_BAG) && it.place != Place.VAN }
            .sortedWith(compareBy({ it.category.ordinal }, { it.name }))
            .forEach { add(s2, "Load: ${it.name} - ${fmtQty(it)}", "From ${it.place.label} · ${it.category.label}") }

        val s3 = "3. Confirm already in the van"
        items.filter { it.quantity > 0 && it.place == Place.VAN }
            .sortedWith(compareBy({ it.category.ordinal }, { it.name }))
            .forEach { add(s3, "Check: ${it.name} - ${fmtQty(it)}", it.category.label) }
        val vanSide = items.filter { it.place == Place.VAN || it.takeInVan || it.place == Place.GRAB_BAG }
        Readiness.missingEssentials(vanSide).forEach { add(s3, "Not packed: $it", "Add it or mark an item 'take if leaving'.") }
        val water = Readiness.drinkingLitres(vanSide)
        val food = Readiness.foodKcal(vanSide) / (plan.people.coerceAtLeast(1) * plan.kcalPerPerson.toDouble())
        add(s3, "Packed drinking water: %.0f L, food: %.1f days".format(water, food), "Plus the 70 L tank for boiling/washing.")
        val ration = vanSide.filter { it.category == Category.RATION }.sumOf { it.quantity }
        if (ration > 0) add(s3, "Ration packs packed: %.0f meals".format(ration))

        val s4 = "4. Secure the house"
        add(s4, "Water off at the stopcock if leaving for days")
        add(s4, "Unplug appliances; switch off at the consumer unit if leaving long-term")
        add(s4, "Close and lock windows, doors, shed and garden store")

        val s5 = "5. Final checks"
        add(s5, "Documents, cash, keys, medication")
        add(s5, "Phone, cables, power banks")
        add(s5, "Printed copy of this app's checklists and reference pages")
        return t
    }
}
