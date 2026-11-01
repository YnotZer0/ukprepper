package uk.prepper.app.data

import android.content.Context
import org.json.JSONObject
import uk.prepper.app.model.Plan

/** Plan <-> JSON. Used for SharedPreferences and for the backup file. */
object PlanStore {
    private const val PREFS = "plan"
    private const val KEY = "json"

    fun toJson(p: Plan): JSONObject = JSONObject().apply {
        put("people", p.people); put("scenarioDays", p.scenarioDays); put("kcalPerPerson", p.kcalPerPerson)
        put("drinkLitres", p.drinkLitres); put("comfortLitres", p.comfortLitres); put("vanTankLitres", p.vanTankLitres)
        put("gasKg", p.gasKg); put("gasGramsPerDay", p.gasGramsPerDay); put("gasGramsPerMinute", p.gasGramsPerMinute)
        put("stationWh", p.stationWh); put("stationChargePct", p.stationChargePct)
        put("vanBankAh", p.vanBankAh); put("gardenBankAh", p.gardenBankAh); put("bankVolts", p.bankVolts)
        put("vanUsable", p.vanUsable); put("gardenUsable", p.gardenUsable)
        put("vanSolarW", p.vanSolarW); put("gardenSolarW", p.gardenSolarW)
        put("fridgeWhDay", p.fridgeWhDay); put("phoneWhDay", p.phoneWhDay); put("lightsWhDay", p.lightsWhDay)
        put("laptopW", p.laptopW); put("laptopHoursDay", p.laptopHoursDay); put("otherWhDay", p.otherWhDay)
    }

    fun fromJson(o: JSONObject): Plan {
        val d = Plan()
        return Plan(
            people = o.optInt("people", d.people),
            scenarioDays = o.optInt("scenarioDays", d.scenarioDays),
            kcalPerPerson = o.optInt("kcalPerPerson", d.kcalPerPerson),
            drinkLitres = o.optDouble("drinkLitres", d.drinkLitres),
            comfortLitres = o.optDouble("comfortLitres", d.comfortLitres),
            vanTankLitres = o.optDouble("vanTankLitres", d.vanTankLitres),
            gasKg = o.optDouble("gasKg", d.gasKg),
            gasGramsPerDay = o.optDouble("gasGramsPerDay", d.gasGramsPerDay),
            gasGramsPerMinute = o.optDouble("gasGramsPerMinute", d.gasGramsPerMinute),
            stationWh = o.optDouble("stationWh", d.stationWh),
            stationChargePct = o.optDouble("stationChargePct", d.stationChargePct),
            vanBankAh = o.optDouble("vanBankAh", d.vanBankAh),
            gardenBankAh = o.optDouble("gardenBankAh", d.gardenBankAh),
            bankVolts = o.optDouble("bankVolts", d.bankVolts),
            vanUsable = o.optDouble("vanUsable", d.vanUsable),
            gardenUsable = o.optDouble("gardenUsable", d.gardenUsable),
            vanSolarW = o.optDouble("vanSolarW", d.vanSolarW),
            gardenSolarW = o.optDouble("gardenSolarW", d.gardenSolarW),
            fridgeWhDay = o.optDouble("fridgeWhDay", d.fridgeWhDay),
            phoneWhDay = o.optDouble("phoneWhDay", d.phoneWhDay),
            lightsWhDay = o.optDouble("lightsWhDay", d.lightsWhDay),
            laptopW = o.optDouble("laptopW", d.laptopW),
            laptopHoursDay = o.optDouble("laptopHoursDay", d.laptopHoursDay),
            otherWhDay = o.optDouble("otherWhDay", d.otherWhDay),
        )
    }

    fun load(ctx: Context): Plan {
        val s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return Plan()
        return runCatching { fromJson(JSONObject(s)) }.getOrElse { Plan() }
    }

    fun save(ctx: Context, p: Plan) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, toJson(p).toString()).apply()
    }
}
