package com.travelindex.app.domain

import com.travelindex.app.data.db.TravelDocEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class DocWarning { NONE, PENDING, EXPIRES_SOON, EXPIRES_BEFORE_TRIP_END, EXPIRED, REJECTED }

object DocWarnings {
    /**
     * @param tripEnd fecha de regreso del viaje al que pertenece (si procede): un pasaporte que caduca
     * antes de volver es un problema aunque aún no haya caducado.
     */
    fun evaluate(doc: TravelDocEntity, tripEnd: LocalDate?, today: LocalDate, soonDays: Int = 60): DocWarning {
        if (doc.status == "REJECTED") return DocWarning.REJECTED
        val expiry = doc.expiryDate
        if (expiry != null) {
            if (expiry.isBefore(today)) return DocWarning.EXPIRED
            if (tripEnd != null && expiry.isBefore(tripEnd)) return DocWarning.EXPIRES_BEFORE_TRIP_END
            if (ChronoUnit.DAYS.between(today, expiry) <= soonDays) return DocWarning.EXPIRES_SOON
        }
        if (doc.status == "PENDING" || doc.status == "APPLIED") return DocWarning.PENDING
        return DocWarning.NONE
    }

    fun daysLeft(doc: TravelDocEntity, today: LocalDate): Long? =
        doc.expiryDate?.let { ChronoUnit.DAYS.between(today, it) }

    /** Enmascara un dato sensible: "AB123456" → "••••3456". */
    fun mask(value: String, visible: Int = 4): String {
        val v = value.trim()
        if (v.isEmpty()) return ""
        if (v.length <= visible) return "•".repeat(v.length)
        return "••••" + v.takeLast(visible)
    }
}
