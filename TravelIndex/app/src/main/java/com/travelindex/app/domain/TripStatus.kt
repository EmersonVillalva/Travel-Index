package com.travelindex.app.domain

import java.time.LocalDate

enum class TripStatus { UPCOMING, ONGOING, COMPLETED, CANCELLED }

fun tripStatus(start: LocalDate, end: LocalDate, cancelled: Boolean, today: LocalDate): TripStatus = when {
    cancelled -> TripStatus.CANCELLED
    today.isBefore(start) -> TripStatus.UPCOMING
    today.isAfter(end) -> TripStatus.COMPLETED
    else -> TripStatus.ONGOING
}

/** Duración inclusiva: salida y regreso el mismo día = 1 día. */
fun tripDurationDays(start: LocalDate, end: LocalDate): Int =
    (java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1).toInt()

/** Días hasta la salida; negativo si ya ha empezado. */
fun daysUntil(start: LocalDate, today: LocalDate): Long =
    java.time.temporal.ChronoUnit.DAYS.between(today, start)
