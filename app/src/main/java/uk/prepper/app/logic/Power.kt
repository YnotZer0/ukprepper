package uk.prepper.app.logic

import uk.prepper.app.model.Plan

object Power {
    /**
     * Approximate average daily peak-sun-hours for central/southern England by month
     * (Jan..Dec). These are rough planning figures, not measurements: real output
     * depends on panel angle, shading and weather. Winter is a fraction of summer.
     */
    val ukPeakSunHours = doubleArrayOf(0.8, 1.4, 2.4, 3.6, 4.5, 4.7, 4.6, 3.9, 2.9, 1.8, 1.0, 0.6)

    /** Losses in controller, wiring, battery round trip, panel temperature. */
    const val SYSTEM_EFFICIENCY = 0.75

    data class Budget(
        val storedWh: Double,
        val loadWhDay: Double,
        val solarWhDay: Double,
        val netWhDay: Double,
        /** Days until storage is empty; POSITIVE_INFINITY if solar covers the load. */
        val days: Double,
        val loads: List<Pair<String, Double>>,
        val stores: List<Pair<String, Double>>,
    )

    fun budget(p: Plan, month: Int): Budget {
        val m = (month - 1).coerceIn(0, 11)
        val stores = listOf(
            "AFERIY P210 (${p.stationChargePct.toInt()}%)" to p.stationWh * p.stationChargePct / 100.0,
            "Van LiFePO4 bank" to p.vanBankAh * p.bankVolts * p.vanUsable,
            "Garden battery bank" to p.gardenBankAh * p.bankVolts * p.gardenUsable,
        )
        val loads = listOf(
            "12 V fridge" to p.fridgeWhDay,
            "Phone charging" to p.phoneWhDay,
            "LED lighting" to p.lightsWhDay,
            "ZBook (NOMAD / Ollama)" to p.laptopW * p.laptopHoursDay,
            "Other" to p.otherWhDay,
        )
        val stored = stores.sumOf { it.second }
        val load = loads.sumOf { it.second }
        val solar = (p.vanSolarW + p.gardenSolarW) * ukPeakSunHours[m] * SYSTEM_EFFICIENCY
        val net = solar - load
        val days = if (net >= 0) Double.POSITIVE_INFINITY else stored / -net
        return Budget(stored, load, solar, net, days, loads, stores)
    }
}
