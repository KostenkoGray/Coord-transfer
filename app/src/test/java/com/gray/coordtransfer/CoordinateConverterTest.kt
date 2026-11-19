package com.gray.coordtransfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateConverterTest {

    @Test
    fun parsesDecimalDegrees() {
        val parsed = CoordinateConverter.parse("49.8397, 24.0297")
        assertEquals(CoordinateFormat.DECIMAL_DEGREES, parsed.format)
        assertEquals(49.8397, parsed.point.latitude, 0.0000001)
        assertEquals(24.0297, parsed.point.longitude, 0.0000001)
    }

    @Test
    fun parsesDecimalDegreesWithHemisphere() {
        val parsed = CoordinateConverter.parse("49.8397 N 24.0297 E")
        assertEquals(CoordinateFormat.DECIMAL_HEMISPHERE, parsed.format)
        assertEquals(49.8397, parsed.point.latitude, 0.0000001)
        assertEquals(24.0297, parsed.point.longitude, 0.0000001)
    }

    @Test
    fun parsesDdmAndDms() {
        val ddm = CoordinateConverter.parse("49° 50.382' N 24° 1.782' E")
        val dms = CoordinateConverter.parse("49° 50' 22.92\" N 24° 1' 46.92\" E")
        assertEquals(CoordinateFormat.DEGREES_DECIMAL_MINUTES, ddm.format)
        assertEquals(CoordinateFormat.DEGREES_MINUTES_SECONDS, dms.format)
        assertEquals(ddm.point.latitude, dms.point.latitude, 0.000001)
        assertEquals(ddm.point.longitude, dms.point.longitude, 0.000001)
    }

    @Test
    fun utmConversionRoundTrips() {
        val original = GeoPoint(49.8397, 24.0297)
        val converted = CoordinateConverter.convert("49.8397, 24.0297")
        val utm = converted.outputs.first { it.title == "UTM / WGS 84" }.value
        val restored = CoordinateConverter.parse(utm).point
        assertEquals(original.latitude, restored.latitude, 0.00001)
        assertEquals(original.longitude, restored.longitude, 0.00001)
    }

    @Test
    fun mgrsConversionRoundTripsWithinGridPrecision() {
        val original = GeoPoint(49.8397, 24.0297)
        val converted = CoordinateConverter.convert("49.8397, 24.0297")
        val mgrs = converted.outputs.first { it.title == "MGRS" }.value
        val restored = CoordinateConverter.parse(mgrs).point
        assertTrue(kotlin.math.abs(original.latitude - restored.latitude) < 0.00005)
        assertTrue(kotlin.math.abs(original.longitude - restored.longitude) < 0.00005)
    }

    @Test
    fun rejectsOutOfRangeLatitude() {
        val result = runCatching { CoordinateConverter.parse("91.0, 24.0") }
        assertTrue(result.isFailure)
    }
    @Test
    fun webMercatorOutputCanBeUsedAsInput() {
        val original = GeoPoint(49.8397, 24.0297)
        val converted = CoordinateConverter.convert("49.8397, 24.0297")
        val mercator = converted.outputs.first { it.title == "Web Mercator (EPSG:3857)" }.value
        val restored = CoordinateConverter.parse(mercator)

        assertEquals(CoordinateFormat.WEB_MERCATOR, restored.format)
        assertEquals(original.latitude, restored.point.latitude, 0.00001)
        assertEquals(original.longitude, restored.point.longitude, 0.00001)
    }


    @Test
    fun conversionOmitsTheSameFormatUsedForInput() {
        val dd = CoordinateConverter.convert("49.8397, 24.0297")
        assertTrue(dd.outputs.none { it.title == "Decimal degrees (DD)" })

        val ddm = CoordinateConverter.convert("49° 50.382' N 24° 1.782' E")
        assertTrue(ddm.outputs.none { it.title == "Degrees and decimal minutes (DDM)" })

        val dms = CoordinateConverter.convert("""49° 50' 22.92" N 24° 1' 46.92" E""")
        assertTrue(dms.outputs.none { it.title == "Degrees, minutes and seconds (DMS)" })

        val utm = CoordinateConverter.convert("35U 426000 5520000")
        assertTrue(utm.outputs.none { it.title == "UTM / WGS 84" })

        val mgrs = CoordinateConverter.convert("35U LV 26000 20000")
        assertTrue(mgrs.outputs.none { it.title == "MGRS" })

        val mercatorValue = CoordinateConverter.convert("49.8397, 24.0297")
            .outputs.first { it.title == "Web Mercator (EPSG:3857)" }.value
        val mercatorInput = CoordinateConverter.convert(mercatorValue)
        assertTrue(mercatorInput.outputs.none { it.title == "Web Mercator (EPSG:3857)" })
    }

}
