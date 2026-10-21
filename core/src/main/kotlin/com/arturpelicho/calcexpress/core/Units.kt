package com.arturpelicho.calcexpress.core

enum class UnitSystem { METRIC, IMPERIAL }

/** A unit converts to/from its category's base unit: base = value * factor + offset. */
data class MeasureUnit(
    val symbol: String,
    val name: String,
    val factor: Double,
    val offset: Double = 0.0,
    val system: UnitSystem? = null,
) {
    fun toBase(v: Double) = v * factor + offset
    fun fromBase(b: Double) = (b - offset) / factor
}

enum class UnitCategory { LENGTH, AREA, VOLUME, MASS, TEMPERATURE, SPEED, TIME, PRESSURE, ENERGY, POWER, FORCE, ANGLE, DATA }

object Units {
    private val M = UnitSystem.METRIC
    private val I = UnitSystem.IMPERIAL

    val all: Map<UnitCategory, List<MeasureUnit>> = mapOf(
        UnitCategory.LENGTH to listOf(
            MeasureUnit("mm", "millimetre", 1e-3, system = M),
            MeasureUnit("cm", "centimetre", 1e-2, system = M),
            MeasureUnit("m", "metre", 1.0, system = M),
            MeasureUnit("km", "kilometre", 1e3, system = M),
            MeasureUnit("in", "inch", 0.0254, system = I),
            MeasureUnit("ft", "foot", 0.3048, system = I),
            MeasureUnit("yd", "yard", 0.9144, system = I),
            MeasureUnit("mi", "mile", 1609.344, system = I),
            MeasureUnit("nmi", "nautical mile", 1852.0),
            MeasureUnit("µm", "micrometre", 1e-6, system = M),
        ),
        UnitCategory.AREA to listOf(
            MeasureUnit("mm²", "square millimetre", 1e-6, system = M),
            MeasureUnit("cm²", "square centimetre", 1e-4, system = M),
            MeasureUnit("m²", "square metre", 1.0, system = M),
            MeasureUnit("ha", "hectare", 1e4, system = M),
            MeasureUnit("km²", "square kilometre", 1e6, system = M),
            MeasureUnit("in²", "square inch", 6.4516e-4, system = I),
            MeasureUnit("ft²", "square foot", 0.09290304, system = I),
            MeasureUnit("yd²", "square yard", 0.83612736, system = I),
            MeasureUnit("ac", "acre", 4046.8564224, system = I),
            MeasureUnit("mi²", "square mile", 2589988.110336, system = I),
        ),
        UnitCategory.VOLUME to listOf(
            MeasureUnit("mL", "millilitre", 1e-6, system = M),
            MeasureUnit("L", "litre", 1e-3, system = M),
            MeasureUnit("m³", "cubic metre", 1.0, system = M),
            MeasureUnit("cm³", "cubic centimetre", 1e-6, system = M),
            MeasureUnit("in³", "cubic inch", 1.6387064e-5, system = I),
            MeasureUnit("ft³", "cubic foot", 0.028316846592, system = I),
            MeasureUnit("fl oz", "fluid ounce (UK)", 2.84130625e-5, system = I),
            MeasureUnit("pt", "pint (UK)", 5.6826125e-4, system = I),
            MeasureUnit("gal", "gallon (UK)", 4.54609e-3, system = I),
            MeasureUnit("gal US", "gallon (US)", 3.785411784e-3),
        ),
        UnitCategory.MASS to listOf(
            MeasureUnit("mg", "milligram", 1e-6, system = M),
            MeasureUnit("g", "gram", 1e-3, system = M),
            MeasureUnit("kg", "kilogram", 1.0, system = M),
            MeasureUnit("t", "tonne", 1e3, system = M),
            MeasureUnit("oz", "ounce", 0.028349523125, system = I),
            MeasureUnit("lb", "pound", 0.45359237, system = I),
            MeasureUnit("st", "stone", 6.35029318, system = I),
            MeasureUnit("ton UK", "long ton", 1016.0469088, system = I),
        ),
        UnitCategory.TEMPERATURE to listOf(
            MeasureUnit("°C", "Celsius", 1.0, 273.15, system = M),
            MeasureUnit("K", "kelvin", 1.0, 0.0, system = M),
            MeasureUnit("°F", "Fahrenheit", 5.0 / 9.0, 273.15 - 32 * 5.0 / 9.0, system = I),
            MeasureUnit("°R", "Rankine", 5.0 / 9.0, 0.0),
        ),
        UnitCategory.SPEED to listOf(
            MeasureUnit("m/s", "metre per second", 1.0, system = M),
            MeasureUnit("km/h", "kilometre per hour", 1 / 3.6, system = M),
            MeasureUnit("mph", "mile per hour", 0.44704, system = I),
            MeasureUnit("ft/s", "foot per second", 0.3048, system = I),
            MeasureUnit("kn", "knot", 1852.0 / 3600.0),
        ),
        UnitCategory.TIME to listOf(
            MeasureUnit("ms", "millisecond", 1e-3),
            MeasureUnit("s", "second", 1.0),
            MeasureUnit("min", "minute", 60.0),
            MeasureUnit("h", "hour", 3600.0),
            MeasureUnit("d", "day", 86400.0),
            MeasureUnit("wk", "week", 604800.0),
            MeasureUnit("yr", "year (365.25 d)", 31557600.0),
        ),
        UnitCategory.PRESSURE to listOf(
            MeasureUnit("Pa", "pascal", 1.0, system = M),
            MeasureUnit("kPa", "kilopascal", 1e3, system = M),
            MeasureUnit("MPa", "megapascal", 1e6, system = M),
            MeasureUnit("bar", "bar", 1e5, system = M),
            MeasureUnit("atm", "atmosphere", 101325.0),
            MeasureUnit("psi", "pound per square inch", 6894.757293168, system = I),
            MeasureUnit("mmHg", "millimetre of mercury", 133.322387415),
            MeasureUnit("inHg", "inch of mercury", 3386.389, system = I),
        ),
        UnitCategory.ENERGY to listOf(
            MeasureUnit("J", "joule", 1.0, system = M),
            MeasureUnit("kJ", "kilojoule", 1e3, system = M),
            MeasureUnit("cal", "calorie", 4.184),
            MeasureUnit("kcal", "kilocalorie", 4184.0),
            MeasureUnit("Wh", "watt hour", 3600.0, system = M),
            MeasureUnit("kWh", "kilowatt hour", 3.6e6, system = M),
            MeasureUnit("BTU", "British thermal unit", 1055.05585262, system = I),
            MeasureUnit("ft·lbf", "foot pound-force", 1.3558179483314, system = I),
            MeasureUnit("eV", "electronvolt", 1.602176634e-19),
        ),
        UnitCategory.POWER to listOf(
            MeasureUnit("W", "watt", 1.0, system = M),
            MeasureUnit("kW", "kilowatt", 1e3, system = M),
            MeasureUnit("MW", "megawatt", 1e6, system = M),
            MeasureUnit("hp", "horsepower (mech.)", 745.69987158227, system = I),
            MeasureUnit("PS", "metric horsepower", 735.49875),
            MeasureUnit("BTU/h", "BTU per hour", 0.29307107017, system = I),
        ),
        UnitCategory.FORCE to listOf(
            MeasureUnit("N", "newton", 1.0, system = M),
            MeasureUnit("kN", "kilonewton", 1e3, system = M),
            MeasureUnit("kgf", "kilogram-force", 9.80665, system = M),
            MeasureUnit("lbf", "pound-force", 4.4482216152605, system = I),
            MeasureUnit("dyn", "dyne", 1e-5),
        ),
        UnitCategory.ANGLE to listOf(
            MeasureUnit("°", "degree", Math.PI / 180),
            MeasureUnit("rad", "radian", 1.0),
            MeasureUnit("grad", "gradian", Math.PI / 200),
            MeasureUnit("′", "arcminute", Math.PI / 10800),
            MeasureUnit("″", "arcsecond", Math.PI / 648000),
            MeasureUnit("rev", "revolution", 2 * Math.PI),
        ),
        UnitCategory.DATA to listOf(
            MeasureUnit("bit", "bit", 0.125),
            MeasureUnit("B", "byte", 1.0),
            MeasureUnit("kB", "kilobyte", 1e3),
            MeasureUnit("MB", "megabyte", 1e6),
            MeasureUnit("GB", "gigabyte", 1e9),
            MeasureUnit("TB", "terabyte", 1e12),
            MeasureUnit("KiB", "kibibyte", 1024.0),
            MeasureUnit("MiB", "mebibyte", 1048576.0),
            MeasureUnit("GiB", "gibibyte", 1073741824.0),
        ),
    )

    fun convert(value: Double, from: MeasureUnit, to: MeasureUnit) = to.fromBase(from.toBase(value))

    /** Default source unit of a category for the preferred unit system. */
    fun defaultUnit(category: UnitCategory, system: UnitSystem): MeasureUnit {
        val units = all.getValue(category)
        val preferred = when (system) {
            UnitSystem.METRIC -> mapOf(
                UnitCategory.LENGTH to "m", UnitCategory.AREA to "m²", UnitCategory.VOLUME to "L",
                UnitCategory.MASS to "kg", UnitCategory.TEMPERATURE to "°C", UnitCategory.SPEED to "km/h",
                UnitCategory.PRESSURE to "bar", UnitCategory.ENERGY to "kJ", UnitCategory.POWER to "kW",
                UnitCategory.FORCE to "N",
            )
            UnitSystem.IMPERIAL -> mapOf(
                UnitCategory.LENGTH to "ft", UnitCategory.AREA to "ft²", UnitCategory.VOLUME to "gal",
                UnitCategory.MASS to "lb", UnitCategory.TEMPERATURE to "°F", UnitCategory.SPEED to "mph",
                UnitCategory.PRESSURE to "psi", UnitCategory.ENERGY to "BTU", UnitCategory.POWER to "hp",
                UnitCategory.FORCE to "lbf",
            )
        }[category]
        return units.firstOrNull { it.symbol == preferred } ?: units.first()
    }
}
