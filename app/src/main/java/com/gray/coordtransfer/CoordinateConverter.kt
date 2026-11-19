package com.gray.coordtransfer

import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

enum class CoordinateFormat(val displayName: String) {
    DECIMAL_DEGREES("Decimal degrees (DD)"),
    DECIMAL_HEMISPHERE("Decimal degrees with hemisphere"),
    DEGREES_DECIMAL_MINUTES("Degrees and decimal minutes (DDM)"),
    DEGREES_MINUTES_SECONDS("Degrees, minutes and seconds (DMS)"),
    UTM("UTM / WGS 84"),
    MGRS("MGRS"),
    WEB_MERCATOR("Web Mercator (EPSG:3857)")
}

data class ParsedCoordinate(
    val point: GeoPoint,
    val format: CoordinateFormat
)

data class CoordinateOutput(
    val title: String,
    val value: String
)

data class Conversion(
    val point: GeoPoint,
    val detectedFormat: CoordinateFormat,
    val outputs: List<CoordinateOutput>
)

/**
 * Offline coordinate parsing and WGS 84 conversion.
 *
 * Supported input formats:
 * - decimal degrees: "49.8397, 24.0297" or "49.8397 24.0297"
 * - decimal degrees with hemisphere: "49.8397 N 24.0297 E"
 * - DDM: 49° 50.382' N 24° 1.782' E
 * - DMS: 49° 50' 22.9" N 24° 1' 43.1" E
 * - UTM: 35U 426000 5520000
 * - MGRS: 35U LV 26000 20000
 */
object CoordinateConverter {
    private const val A = 6378137.0
    private const val ECC_SQUARED = 0.00669437999014
    private const val K0 = 0.9996
    private const val FALSE_EASTING = 500000.0
    private const val FALSE_NORTHING = 10000000.0
    private const val WEB_MERCATOR_MAX_LAT = 85.0511287798066

    private const val LATITUDE_BANDS = "CDEFGHJKLMNPQRSTUVWX"
    private const val ROW_LETTERS = "ABCDEFGHJKLMNPQRSTUV"
    private val COLUMN_SETS = arrayOf(
        "ABCDEFGH",
        "JKLMNPQR",
        "STUVWXYZ"
    )

    private val minimumNorthingByBand = mapOf(
        'C' to 1100000.0,
        'D' to 2000000.0,
        'E' to 2800000.0,
        'F' to 3700000.0,
        'G' to 4600000.0,
        'H' to 5500000.0,
        'J' to 6400000.0,
        'K' to 7300000.0,
        'L' to 8200000.0,
        'M' to 9100000.0,
        'N' to 0.0,
        'P' to 800000.0,
        'Q' to 1700000.0,
        'R' to 2600000.0,
        'S' to 3500000.0,
        'T' to 4400000.0,
        'U' to 5300000.0,
        'V' to 6200000.0,
        'W' to 7000000.0,
        'X' to 7900000.0
    )

    private val dmsPattern = Regex(
        """([+-]?\d{1,3})\s*[°º]\s*(\d{1,2}(?:[.,]\d+)?)\s*['′](?:\s*(\d{1,2}(?:[.,]\d+)?)\s*["″])?\s*([NSEW])""",
        RegexOption.IGNORE_CASE
    )

    private val hemisphereDecimalPattern = Regex(
        """([+-]?\d+(?:[.,]\d+)?)\s*([NSEW])|([NSEW])\s*([+-]?\d+(?:[.,]\d+)?)""",
        RegexOption.IGNORE_CASE
    )

    private val utmPattern = Regex(
        """^(\d{1,2})\s*([C-HJ-NP-X])\s+([0-9]+(?:[.,][0-9]+)?)\s+([0-9]+(?:[.,][0-9]+)?)$""",
        RegexOption.IGNORE_CASE
    )

    private val mgrsPattern = Regex(
        """^(\d{1,2})([C-HJ-NP-X])([A-HJ-NP-Z])([A-HJ-NP-V])(\d{0,10})$""",
        RegexOption.IGNORE_CASE
    )

    fun parse(input: String): ParsedCoordinate {
        val value = input.trim()
        require(value.isNotEmpty()) { "Enter coordinates to convert." }

        parseDmsOrDdm(value)?.let { return it }
        parseMgrs(value)?.let { return it }
        parseUtm(value)?.let { return it }
        parseDecimalWithHemisphere(value)?.let { return it }
        parseWebMercator(value)?.let { return it }
        parsePlainDecimal(value)?.let { return it }

        throw IllegalArgumentException(
            "Format not recognized. Try DD, DDM, DMS, UTM, MGRS or Web Mercator."
        )
    }

    fun convert(input: String): Conversion {
        val parsed = parse(input)
        val point = parsed.point
        val outputs = mutableListOf<CoordinateOutput>()

        if (parsed.format != CoordinateFormat.DECIMAL_DEGREES &&
            parsed.format != CoordinateFormat.DECIMAL_HEMISPHERE
        ) {
            outputs += CoordinateOutput("Decimal degrees (DD)", formatDecimalDegrees(point))
        }
        if (parsed.format != CoordinateFormat.DEGREES_DECIMAL_MINUTES) {
            outputs += CoordinateOutput("Degrees and decimal minutes (DDM)", formatDdm(point))
        }
        if (parsed.format != CoordinateFormat.DEGREES_MINUTES_SECONDS) {
            outputs += CoordinateOutput("Degrees, minutes and seconds (DMS)", formatDms(point))
        }

        runCatching { toUtm(point) }.getOrNull()?.let { utm ->
            if (parsed.format != CoordinateFormat.UTM) {
                outputs += CoordinateOutput("UTM / WGS 84", formatUtm(utm))
            }
            if (parsed.format != CoordinateFormat.MGRS) {
                outputs += CoordinateOutput("MGRS", toMgrs(utm, precision = 5))
            }
        }

        if (parsed.format != CoordinateFormat.WEB_MERCATOR) {
            runCatching { toWebMercator(point) }.getOrNull()?.let { mercator ->
                outputs += CoordinateOutput(
                    "Web Mercator (EPSG:3857)",
                    "X " + formatMeters(mercator.first) + " m, Y " +
                        formatMeters(mercator.second) + " m"
                )
            }
        }

        return Conversion(point, parsed.format, outputs)
    }

    private fun parseDmsOrDdm(value: String): ParsedCoordinate? {
        val matches = dmsPattern.findAll(value).toList()
        if (matches.size != 2) return null

        var latitude: Double? = null
        var longitude: Double? = null
        var hasSeconds = false

        for (match in matches) {
            val degrees = match.groupValues[1].toDoubleOrNull() ?: return null
            val minutes = match.groupValues[2].replace(',', '.').toDoubleOrNull() ?: return null
            val secondsText = match.groupValues[3]
            val seconds = secondsText.takeIf { it.isNotEmpty() }
                ?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
            val direction = match.groupValues[4].uppercase(Locale.US).first()

            if (minutes >= 60.0 || seconds >= 60.0) return null
            if (degrees < 0.0) return null
            val magnitude = degrees + minutes / 60.0 + seconds / 3600.0
            val signed = if (direction == 'S' || direction == 'W') -magnitude else magnitude

            when (direction) {
                'N', 'S' -> {
                    if (latitude != null) return null
                    latitude = signed
                }
                'E', 'W' -> {
                    if (longitude != null) return null
                    longitude = signed
                }
            }
            if (secondsText.isNotEmpty()) hasSeconds = true
        }

        val point = GeoPoint(latitude ?: return null, longitude ?: return null)
        validatePoint(point)
        return ParsedCoordinate(
            point,
            if (hasSeconds) CoordinateFormat.DEGREES_MINUTES_SECONDS
            else CoordinateFormat.DEGREES_DECIMAL_MINUTES
        )
    }

    private fun parseMgrs(value: String): ParsedCoordinate? {
        val compact = value.replace(Regex("""\s+"""), "").uppercase(Locale.US)
        val match = mgrsPattern.matchEntire(compact) ?: return null
        val digits = match.groupValues[5]
        if (digits.length % 2 != 0) return null

        val zone = match.groupValues[1].toIntOrNull() ?: return null
        val band = match.groupValues[2].first()
        val column = match.groupValues[3].first()
        val row = match.groupValues[4].first()

        val point = runCatching {
            fromMgrs(zone, band, column, row, digits)
        }.getOrNull() ?: return null

        return ParsedCoordinate(point, CoordinateFormat.MGRS)
    }

    private fun parseUtm(value: String): ParsedCoordinate? {
        val match = utmPattern.matchEntire(value) ?: return null
        val zone = match.groupValues[1].toIntOrNull() ?: return null
        val band = match.groupValues[2].uppercase(Locale.US).first()
        val easting = parseNumber(match.groupValues[3]) ?: return null
        val northing = parseNumber(match.groupValues[4]) ?: return null
        if (zone !in 1..60) return null

        val point = runCatching {
            fromUtm(zone, band, easting, northing)
        }.getOrNull() ?: return null

        return ParsedCoordinate(point, CoordinateFormat.UTM)
    }

    private fun parseDecimalWithHemisphere(value: String): ParsedCoordinate? {
        val matches = hemisphereDecimalPattern.findAll(value).toList()
        if (matches.size != 2) return null

        var latitude: Double? = null
        var longitude: Double? = null

        for (match in matches) {
            val numberText: String
            val direction: Char
            if (match.groupValues[1].isNotEmpty()) {
                numberText = match.groupValues[1]
                direction = match.groupValues[2].uppercase(Locale.US).first()
            } else {
                direction = match.groupValues[3].uppercase(Locale.US).first()
                numberText = match.groupValues[4]
            }

            val magnitude = parseNumber(numberText) ?: return null
            val signed = when (direction) {
                'N', 'E' -> abs(magnitude)
                'S', 'W' -> -abs(magnitude)
                else -> return null
            }

            when (direction) {
                'N', 'S' -> {
                    if (latitude != null) return null
                    latitude = signed
                }
                'E', 'W' -> {
                    if (longitude != null) return null
                    longitude = signed
                }
            }
        }

        val point = GeoPoint(latitude ?: return null, longitude ?: return null)
        validatePoint(point)
        return ParsedCoordinate(point, CoordinateFormat.DECIMAL_HEMISPHERE)
    }


    private val webMercatorLabeledPattern = Regex(
        """^\s*X\s*[:=]?\s*(-?\d+(?:[.,]\d+)?)\s*m?\s*[,; ]+\s*Y\s*[:=]?\s*(-?\d+(?:[.,]\d+)?)\s*m?\s*$""",
        RegexOption.IGNORE_CASE
    )

    private fun parseWebMercator(value: String): ParsedCoordinate? {
        val labeled = webMercatorLabeledPattern.matchEntire(value)
        val x: Double
        val y: Double

        if (labeled != null) {
            x = parseNumber(labeled.groupValues[1]) ?: return null
            y = parseNumber(labeled.groupValues[2]) ?: return null
        } else {
            val parts = when {
                value.contains(';') -> value.split(';')
                value.count { it == ',' } == 1 -> value.split(',')
                else -> value.trim().split(Regex("""\s+"""))
            }.map { it.trim() }

            if (parts.size != 2 || parts.any { it.isEmpty() }) return null
            x = parseNumber(parts[0]) ?: return null
            y = parseNumber(parts[1]) ?: return null
        }

        // Prefer ordinary decimal degrees when both values are in geographic ranges.
        if (x in -180.0..180.0 && y in -90.0..90.0) return null

        val maxCoordinate = PI * A
        if (x !in -maxCoordinate..maxCoordinate || y !in -maxCoordinate..maxCoordinate) {
            return null
        }

        val point = GeoPoint(
            latitude = Math.toDegrees(atan(sinh(y / A))),
            longitude = Math.toDegrees(x / A)
        )
        validatePoint(point)
        return ParsedCoordinate(point, CoordinateFormat.WEB_MERCATOR)
    }

    private fun parsePlainDecimal(value: String): ParsedCoordinate? {
        val parts = when {
            value.contains(';') -> value.split(';')
            value.count { it == ',' } == 1 -> value.split(',')
            else -> value.trim().split(Regex("""\s+"""))
        }.map { it.trim() }

        if (parts.size != 2 || parts.any { it.isEmpty() }) return null
        val latitude = parseNumber(parts[0]) ?: return null
        val longitude = parseNumber(parts[1]) ?: return null
        val point = GeoPoint(latitude, longitude)
        validatePoint(point)
        return ParsedCoordinate(point, CoordinateFormat.DECIMAL_DEGREES)
    }

    private fun parseNumber(value: String): Double? =
        value.trim().replace(',', '.').toDoubleOrNull()

    private fun validatePoint(point: GeoPoint) {
        require(point.latitude.isFinite() && point.longitude.isFinite()) {
            "Coordinates must be finite numbers."
        }
        require(point.latitude in -90.0..90.0) {
            "Latitude must be between -90° and 90°."
        }
        require(point.longitude in -180.0..180.0) {
            "Longitude must be between -180° and 180°."
        }
    }

    private data class UtmCoordinate(
        val zone: Int,
        val band: Char,
        val easting: Double,
        val northing: Double
    )

    private fun toUtm(point: GeoPoint): UtmCoordinate {
        require(point.latitude in -80.0..84.0) {
            "UTM and MGRS are defined here only from 80°S to 84°N."
        }

        val latitude = Math.toRadians(point.latitude)
        val longitude = Math.toRadians(point.longitude)
        val zone = zoneFor(point.latitude, point.longitude)
        val longitudeOrigin = Math.toRadians((zone - 1) * 6.0 - 180.0 + 3.0)
        val eccentricityPrimeSquared = ECC_SQUARED / (1.0 - ECC_SQUARED)

        val sinLatitude = sin(latitude)
        val cosLatitude = cos(latitude)
        val tanLatitude = tan(latitude)
        val n = A / sqrt(1.0 - ECC_SQUARED * sinLatitude.pow(2))
        val t = tanLatitude.pow(2)
        val c = eccentricityPrimeSquared * cosLatitude.pow(2)
        val a = cosLatitude * (longitude - longitudeOrigin)

        val e2 = ECC_SQUARED
        val m = A * (
            (1.0 - e2 / 4.0 - 3.0 * e2.pow(2) / 64.0 - 5.0 * e2.pow(3) / 256.0) * latitude -
                (3.0 * e2 / 8.0 + 3.0 * e2.pow(2) / 32.0 + 45.0 * e2.pow(3) / 1024.0) * sin(2.0 * latitude) +
                (15.0 * e2.pow(2) / 256.0 + 45.0 * e2.pow(3) / 1024.0) * sin(4.0 * latitude) -
                (35.0 * e2.pow(3) / 3072.0) * sin(6.0 * latitude)
            )

        val easting = FALSE_EASTING + K0 * n * (
            a + (1.0 - t + c) * a.pow(3) / 6.0 +
                (5.0 - 18.0 * t + t.pow(2) + 72.0 * c -
                    58.0 * eccentricityPrimeSquared) * a.pow(5) / 120.0
            )

        var northing = K0 * (
            m + n * tanLatitude * (
                a.pow(2) / 2.0 +
                    (5.0 - t + 9.0 * c + 4.0 * c.pow(2)) * a.pow(4) / 24.0 +
                    (61.0 - 58.0 * t + t.pow(2) + 600.0 * c -
                        330.0 * eccentricityPrimeSquared) * a.pow(6) / 720.0
                )
            )

        if (point.latitude < 0.0) northing += FALSE_NORTHING
        return UtmCoordinate(zone, latitudeBand(point.latitude), easting, northing)
    }

    private fun fromUtm(
        zone: Int,
        band: Char,
        easting: Double,
        northingInput: Double
    ): GeoPoint {
        require(zone in 1..60) { "UTM zone must be between 1 and 60." }
        require(band in LATITUDE_BANDS) { "Invalid UTM latitude band." }
        require(easting in 100000.0..900000.0) { "UTM easting is outside its valid range." }
        require(northingInput in 0.0..10000000.0) { "UTM northing is outside its valid range." }

        val northernHemisphere = band >= 'N'
        val northing = if (northernHemisphere) northingInput
        else northingInput - FALSE_NORTHING

        val eccentricityPrimeSquared = ECC_SQUARED / (1.0 - ECC_SQUARED)
        val e1 = (1.0 - sqrt(1.0 - ECC_SQUARED)) /
            (1.0 + sqrt(1.0 - ECC_SQUARED))
        val x = easting - FALSE_EASTING
        val y = northing / K0

        val m = y
        val mu = m / (A * (
            1.0 - ECC_SQUARED / 4.0 -
                3.0 * ECC_SQUARED.pow(2) / 64.0 -
                5.0 * ECC_SQUARED.pow(3) / 256.0
            ))

        val phi1 = mu +
            (3.0 * e1 / 2.0 - 27.0 * e1.pow(3) / 32.0) * sin(2.0 * mu) +
            (21.0 * e1.pow(2) / 16.0 - 55.0 * e1.pow(4) / 32.0) * sin(4.0 * mu) +
            (151.0 * e1.pow(3) / 96.0) * sin(6.0 * mu) +
            (1097.0 * e1.pow(4) / 512.0) * sin(8.0 * mu)

        val sinPhi1 = sin(phi1)
        val cosPhi1 = cos(phi1)
        val tanPhi1 = tan(phi1)
        val n1 = A / sqrt(1.0 - ECC_SQUARED * sinPhi1.pow(2))
        val r1 = A * (1.0 - ECC_SQUARED) /
            (1.0 - ECC_SQUARED * sinPhi1.pow(2)).pow(1.5)
        val t1 = tanPhi1.pow(2)
        val c1 = eccentricityPrimeSquared * cosPhi1.pow(2)
        val d = x / (n1 * K0)

        val latitude = phi1 - (n1 * tanPhi1 / r1) * (
            d.pow(2) / 2.0 -
                (5.0 + 3.0 * t1 + 10.0 * c1 - 4.0 * c1.pow(2) -
                    9.0 * eccentricityPrimeSquared) * d.pow(4) / 24.0 +
                (61.0 + 90.0 * t1 + 298.0 * c1 + 45.0 * t1.pow(2) -
                    252.0 * eccentricityPrimeSquared - 3.0 * c1.pow(2)) *
                d.pow(6) / 720.0
            )

        val longitudeOrigin = Math.toRadians((zone - 1) * 6.0 - 180.0 + 3.0)
        val longitude = longitudeOrigin + (
            d - (1.0 + 2.0 * t1 + c1) * d.pow(3) / 6.0 +
                (5.0 - 2.0 * c1 + 28.0 * t1 - 3.0 * c1.pow(2) +
                    8.0 * eccentricityPrimeSquared + 24.0 * t1.pow(2)) *
                d.pow(5) / 120.0
            ) / cosPhi1

        val point = GeoPoint(Math.toDegrees(latitude), Math.toDegrees(longitude))
        validatePoint(point)
        return point
    }

    private fun toMgrs(utm: UtmCoordinate, precision: Int): String {
        require(precision in 0..5)
        val columnSet = COLUMN_SETS[(utm.zone - 1) % 3]
        val columnNumber = floor(utm.easting / 100000.0).toInt()
        require(columnNumber in 1..8) { "Easting cannot be represented as MGRS." }
        val columnLetter = columnSet[columnNumber - 1]

        val rowNumber = floor(utm.northing / 100000.0).toInt() % 20
        val rowOffset = if (utm.zone % 2 == 0) 5 else 0
        val rowLetter = ROW_LETTERS[(rowNumber + rowOffset) % 20]

        val resolution = 10.0.pow(5 - precision)
        val eastingRemainder = utm.easting - columnNumber * 100000.0
        val northingRemainder = utm.northing - floor(utm.northing / 100000.0) * 100000.0

        val eastingDigits = if (precision == 0) "" else
            String.format(Locale.US, "%0" + precision + "d",
                floor(eastingRemainder / resolution).toInt())
        val northingDigits = if (precision == 0) "" else
            String.format(Locale.US, "%0" + precision + "d",
                floor(northingRemainder / resolution).toInt())

        return utm.zone.toString() + utm.band + " " +
            columnLetter + rowLetter + " " + eastingDigits + " " + northingDigits
    }

    private fun fromMgrs(
        zone: Int,
        band: Char,
        column: Char,
        row: Char,
        digits: String
    ): GeoPoint {
        require(zone in 1..60) { "MGRS zone must be between 1 and 60." }
        require(band in LATITUDE_BANDS) { "Invalid MGRS latitude band." }
        require(digits.length % 2 == 0 && digits.length <= 10) {
            "MGRS precision must contain an even number of digits."
        }

        val columnSet = COLUMN_SETS[(zone - 1) % 3]
        val columnIndex = columnSet.indexOf(column)
        require(columnIndex >= 0) { "MGRS column letter is invalid for this zone." }

        var rowIndex = ROW_LETTERS.indexOf(row)
        require(rowIndex >= 0) { "MGRS row letter is invalid." }
        if (zone % 2 == 0) rowIndex = (rowIndex - 5 + 20) % 20

        val precision = digits.length / 2
        val resolution = 10.0.pow(5 - precision)
        val eastingPart = if (precision == 0) 0.0
        else digits.substring(0, precision).toDouble() * resolution
        val northingPart = if (precision == 0) 0.0
        else digits.substring(precision).toDouble() * resolution

        val cellCenterOffset = resolution / 2.0
        val easting = (columnIndex + 1) * 100000.0 +
            eastingPart + cellCenterOffset

        var northing = rowIndex * 100000.0
        val bandMinimum = minimumNorthingByBand[band]
            ?: throw IllegalArgumentException("Invalid MGRS latitude band.")
        while (northing < bandMinimum) northing += 2000000.0
        northing += northingPart + cellCenterOffset

        val lowerLatitude = bandLowerLatitude(band)
        val upperLatitude = bandUpperLatitude(band)
        var point = fromUtm(zone, band, easting, northing)

        var attempts = 0
        while (point.latitude < lowerLatitude - 0.00001 && attempts < 5) {
            northing += 2000000.0
            point = fromUtm(zone, band, easting, northing)
            attempts++
        }

        require(point.latitude >= lowerLatitude - 0.0001 &&
            point.latitude <= upperLatitude + 0.0001) {
            "MGRS grid square does not match its latitude band."
        }
        return point
    }

    private fun toWebMercator(point: GeoPoint): Pair<Double, Double> {
        require(abs(point.latitude) <= WEB_MERCATOR_MAX_LAT) {
            "Web Mercator cannot represent latitudes beyond ±85.0511°."
        }
        val x = A * Math.toRadians(point.longitude)
        val latitude = Math.toRadians(point.latitude)
        val y = A * ln(tan(PI / 4.0 + latitude / 2.0))
        return x to y
    }

    private fun zoneFor(latitude: Double, longitude: Double): Int {
        var zone = floor((longitude + 180.0) / 6.0).toInt() + 1
        zone = zone.coerceIn(1, 60)

        // Norway exception.
        if (latitude >= 56.0 && latitude < 64.0 &&
            longitude >= 3.0 && longitude < 12.0
        ) {
            zone = 32
        }

        // Svalbard exceptions.
        if (latitude >= 72.0 && latitude < 84.0) {
            zone = when {
                longitude >= 0.0 && longitude < 9.0 -> 31
                longitude < 21.0 && longitude >= 9.0 -> 33
                longitude < 33.0 && longitude >= 21.0 -> 35
                longitude < 42.0 && longitude >= 33.0 -> 37
                else -> zone
            }
        }
        return zone
    }

    private fun latitudeBand(latitude: Double): Char {
        val index = floor((latitude + 80.0) / 8.0).toInt().coerceIn(0, 19)
        return LATITUDE_BANDS[index]
    }

    private fun bandLowerLatitude(band: Char): Double {
        val index = LATITUDE_BANDS.indexOf(band)
        require(index >= 0)
        return -80.0 + index * 8.0
    }

    private fun bandUpperLatitude(band: Char): Double =
        if (band == 'X') 84.0 else bandLowerLatitude(band) + 8.0

    private fun formatDecimalDegrees(point: GeoPoint): String =
        String.format(Locale.US, "%.6f, %.6f", point.latitude, point.longitude)

    private fun formatDdm(point: GeoPoint): String =
        formatDdmAxis(point.latitude, true) + ", " +
            formatDdmAxis(point.longitude, false)

    private fun formatDdmAxis(value: Double, isLatitude: Boolean): String {
        val absolute = abs(value)
        val degrees = floor(absolute).toInt()
        val minutes = (absolute - degrees) * 60.0
        val hemisphere = hemisphere(value, isLatitude)
        val degreeWidth = if (isLatitude) 2 else 3
        return String.format(
            Locale.US,
            "%0" + degreeWidth + "d° %.4f′ %s",
            degrees,
            minutes,
            hemisphere
        )
    }

    private fun formatDms(point: GeoPoint): String =
        formatDmsAxis(point.latitude, true) + ", " +
            formatDmsAxis(point.longitude, false)

    private fun formatDmsAxis(value: Double, isLatitude: Boolean): String {
        val absolute = abs(value)
        val degrees = floor(absolute).toInt()
        val totalMinutes = (absolute - degrees) * 60.0
        val minutes = floor(totalMinutes).toInt()
        val seconds = (totalMinutes - minutes) * 60.0
        val hemisphere = hemisphere(value, isLatitude)
        val degreeWidth = if (isLatitude) 2 else 3
        return String.format(
            Locale.US,
            "%0" + degreeWidth + "d° %02d′ %05.2f″ %s",
            degrees,
            minutes,
            seconds,
            hemisphere
        )
    }

    private fun hemisphere(value: Double, isLatitude: Boolean): String = when {
        isLatitude && value < 0.0 -> "S"
        isLatitude -> "N"
        value < 0.0 -> "W"
        else -> "E"
    }

    private fun formatUtm(utm: UtmCoordinate): String =
        utm.zone.toString() + utm.band + " " +
            formatMeters(utm.easting) + " " + formatMeters(utm.northing)

    private fun formatMeters(value: Double): String =
        String.format(Locale.US, "%.0f", value.roundToInt().toDouble())
}
