package com.nagi.hustle

import android.content.Context

object Colors {
    val INK = 0xFF101418.toInt()
    val PAPER = 0xFFF6F5F2.toInt()
    val ACCENT = 0xFFFF5B2E.toInt()
    val CARD = 0xFF1A2027.toInt()
    val CARD2 = 0xFF242C35.toInt()
    val MUTED = 0xFF9AA4AF.toInt()
    val GREEN = 0xFF3DDC84.toInt()
    val RED = 0xFFFF5252.toInt()
    val GOLD = 0xFFFFC857.toInt()
    val BLUE = 0xFF4DA3FF.toInt()
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun naira(v: Long): String = "₦" + String.format("%,d", v)

/** One place in the city. */
class Area(
    val id: Int,
    val name: String,
    val blurb: String,
    val foodCost: Int,
    val jobName: String,
    val pay: Int,
    val energyCost: Int
)

val AREAS = listOf(
    Area(0, "Oshodi", "Busy market and bus park", 800, "Market trader", 4500, 22),
    Area(1, "Yaba", "Campus and tech hub", 1200, "Campus freelancer", 6500, 24),
    Area(2, "Ikeja", "Offices and the airport road", 1500, "Office assistant", 7500, 25),
    Area(3, "Lekki", "Estates, malls and costly food", 3000, "Delivery rider", 11000, 30)
)

/** A city-wide challenge that affects every player for a few days. */
class CityEvent(
    val title: String,
    val text: String,
    val days: Int,
    val pay: DoubleArray,
    val travel: Double,
    val food: Double,
    val travelTime: Double,
    val blocked: Int,
    val rain: Boolean,
    val dark: Boolean
)

private val ONE = doubleArrayOf(1.0, 1.0, 1.0, 1.0)

val EVENTS = listOf(
    CityEvent(
        "Fuel scarcity", "Queues at every filling station. Transport costs double and food gets pricier.",
        3, ONE, 2.0, 1.15, 1.2, -1, false, false
    ),
    CityEvent(
        "Flood in Oshodi", "Water everywhere. You cannot work in Oshodi until it clears.",
        2, ONE, 1.3, 1.0, 1.4, 0, true, false
    ),
    CityEvent(
        "Gridlock", "Third Mainland is stuck. Every trip takes much longer.",
        2, ONE, 1.0, 1.0, 2.2, -1, false, false
    ),
    CityEvent(
        "Public holiday", "Offices are shut. Pay in Ikeja is halved, and others drop a little.",
        2, doubleArrayOf(0.85, 0.85, 0.5, 0.85), 1.0, 1.0, 0.9, -1, false, false
    ),
    CityEvent(
        "Tech meetup in Yaba", "Clients everywhere. Pay in Yaba is up by half.",
        3, doubleArrayOf(1.0, 1.5, 1.0, 1.0), 1.0, 1.0, 1.0, -1, false, false
    ),
    CityEvent(
        "NEPA took the light", "Blackout across the city. Shops cut hours, so pay drops everywhere.",
        2, doubleArrayOf(0.85, 0.85, 0.85, 0.85), 1.0, 1.0, 1.0, -1, false, true
    ),
    CityEvent(
        "Heavy rain", "Wet roads. Trips cost more and take longer.",
        2, ONE, 1.3, 1.0, 1.3, -1, true, false
    ),
    CityEvent(
        "Market day in Oshodi", "Crowds of buyers. Pay in Oshodi is up by half.",
        3, doubleArrayOf(1.5, 1.0, 1.0, 1.0), 1.0, 0.9, 1.0, -1, false, false
    )
)
