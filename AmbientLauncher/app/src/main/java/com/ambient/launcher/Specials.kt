package com.ambient.launcher

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class Special(val emoji: String, val name: String, val blurb: String)

data class NextSpecial(val days: Int, val date: LocalDate, val special: Special)

/** Built-in calendar of observances. Keys are "month-day". */
private val FIXED: Map<String, Special> = mapOf(
    "1-1" to Special("🎆", "New Year’s Day", "A fresh start for the whole year."),
    "2-4" to Special("🇱🇰", "Independence Day", "Sri Lanka marks its independence from 1948."),
    "2-14" to Special("❤️", "Valentine’s Day", "A day for sharing love and appreciation."),
    "3-8" to Special("💜", "International Women’s Day", "Celebrating the achievements of women everywhere."),
    "3-14" to Special("🥧", "Pi Day", "3.14 on the calendar. Time for pie."),
    "3-20" to Special("😊", "International Day of Happiness", "A reminder to make someone smile today."),
    "3-22" to Special("💧", "World Water Day", "Focuses on clean water for everyone."),
    "4-14" to Special("🌼", "Sinhala and Tamil New Year", "Families gather for traditional foods, games and customs."),
    "4-22" to Special("🌍", "Earth Day", "A day to look after the planet."),
    "5-1" to Special("🛠️", "May Day", "International Workers’ Day."),
    "6-5" to Special("🌱", "World Environment Day", "Action to protect nature, led by the UN."),
    "7-18" to Special("🤝", "Nelson Mandela Day", "A day to give 67 minutes to helping others."),
    "8-12" to Special("🌟", "International Youth Day", "Recognizing the voices of young people."),
    "9-21" to Special("🕊️", "International Day of Peace", "A global call for ceasefire and goodwill."),
    "9-27" to Special("✈️", "World Tourism Day", "Celebrating travel and the places we explore."),
    "10-1" to Special("🧒", "Children’s Day", "Celebrated in Sri Lanka, with World Elders’ Day."),
    "10-5" to Special("🍎", "World Teachers’ Day", "Thank a teacher who shaped you."),
    "10-16" to Special("🍽️", "World Food Day", "Working toward a world without hunger."),
    "10-31" to Special("🎃", "Halloween", "Costumes, lanterns and sweets."),
    "11-1" to Special("🥦", "World Vegan Day", "A day to try a plant-based meal."),
    "11-13" to Special("💛", "World Kindness Day", "Do one small kind thing today."),
    "11-19" to Special("🧔", "International Men’s Day", "Focuses on men’s health and positive role models."),
    "11-20" to Special("🧸", "Universal Children’s Day", "Promoting the welfare and rights of children."),
    "12-10" to Special("⚖️", "Human Rights Day", "Marks the Universal Declaration of Human Rights."),
    "12-25" to Special("🎄", "Christmas Day", "Celebrated by Christians around the world."),
    "12-31" to Special("🥂", "New Year’s Eve", "The last day of the year.")
)

fun nthWeekday(year: Int, month: Int, dow: DayOfWeek, n: Int): LocalDate {
    return LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, dow))
}

fun easterDate(y: Int): LocalDate {
    val a = y % 19
    val b = y / 100
    val c = y % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val t = h + l - 7 * m + 114
    return LocalDate.of(y, t / 31, t % 31 + 1)
}

fun specialFor(date: LocalDate): Special? {
    val fixed = FIXED["${date.monthValue}-${date.dayOfMonth}"]
    if (fixed != null) return fixed

    val y = date.year
    if (date == nthWeekday(y, 5, DayOfWeek.SUNDAY, 2)) {
        return Special("💐", "Mother’s Day", "A day to thank the people who raised us.")
    }
    if (date == nthWeekday(y, 6, DayOfWeek.SUNDAY, 3)) {
        return Special("👔", "Father’s Day", "A day to celebrate fathers and father figures.")
    }
    if (date == nthWeekday(y, 11, DayOfWeek.THURSDAY, 4)) {
        return Special("🦃", "Thanksgiving", "A US holiday for gratitude, family and a big meal.")
    }
    val easter = easterDate(y)
    if (date == easter) {
        return Special("🐣", "Easter Sunday", "Celebrated by Christians around the world.")
    }
    if (date == easter.minusDays(2)) {
        return Special("✝️", "Good Friday", "Observed on the Friday before Easter.")
    }
    return null
}

fun nextSpecial(from: LocalDate): NextSpecial? {
    for (k in 1..370) {
        val day = from.plusDays(k.toLong())
        val s = specialFor(day)
        if (s != null) return NextSpecial(k, day, s)
    }
    return null
}

private val WORDS = listOf(
    "Serendipity" to "Finding something good by accident. It comes from Serendip, an old name for Sri Lanka.",
    "Petrichor" to "The earthy smell after rain falls on dry ground.",
    "Halcyon" to "A calm, peaceful time, as in halcyon days.",
    "Ephemeral" to "Lasting for a very short time.",
    "Luminous" to "Giving off a soft, steady light.",
    "Solace" to "Comfort in a time of sadness or stress.",
    "Mellifluous" to "Sweet and smooth to hear.",
    "Equanimity" to "Calm and even temper under pressure.",
    "Sonder" to "The sense that everyone around you has a life as full as yours.",
    "Verdant" to "Green with grass or plants.",
    "Eloquent" to "Fluent and persuasive in speech.",
    "Wanderlust" to "A strong urge to travel."
)

fun wordOf(date: LocalDate): Pair<String, String> = WORDS[date.dayOfYear % WORDS.size]

fun monthDay(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("MMMM d", Locale.getDefault()))

data class MoonInfo(val name: String, val lit: Int)

fun moonInfo(date: LocalDate): MoonInfo {
    val jd = date.toEpochDay() + 2440587.5 + 0.5
    var phase = ((jd - 2451550.1) / 29.530588853) % 1.0
    if (phase < 0) phase += 1.0
    val names = listOf(
        "New moon", "Waxing crescent", "First quarter", "Waxing gibbous",
        "Full moon", "Waning gibbous", "Last quarter", "Waning crescent"
    )
    val idx = Math.floor(phase * 8 + 0.5).toInt() % 8
    val lit = Math.round((1 - Math.cos(phase * 2 * Math.PI)) / 2 * 100).toInt()
    return MoonInfo(names[idx], lit)
}
