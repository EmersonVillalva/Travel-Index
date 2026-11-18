package com.travelindex.app.domain

import com.travelindex.app.data.db.FlightEntity
import com.travelindex.app.domain.forms.FieldError
import com.travelindex.app.domain.forms.FlightCodec
import com.travelindex.app.domain.forms.FormValues
import java.util.UUID

/**
 * Vuelos con escalas (p. ej. Madrid → Mánchester → Pekín). Cada tramo es un vuelo normal (con su aerolínea, número y horas)
 * y los tramos de un mismo viaje comparten [FlightEntity.connectionId]. Así el itinerario, los avisos, el calendario y el
 * PDF siguen tratando cada tramo como un vuelo, y aquí solo se enlazan para mostrarlos como un solo viaje.
 */
object FlightConnections {
    /** Máximo de escalas que se piden en el formulario (7 vuelos en total). */
    const val MAX_STOPS = 6

    /**
     * Campos de cada escala en el formulario, con el prefijo `s1_`, `s2_`… ("s1_airport"): el aeropuerto, cuándo llega el vuelo
     * anterior, cuándo sale el siguiente y la aerolínea y el número de ese siguiente vuelo.
     */
    val STOP_FIELDS = listOf("airport", "arrDate", "arrTime", "depDate", "depTime", "zone", "airline", "flightNumber")

    fun stopKey(index: Int, name: String): String = "s${index}_$name"

    private val STOP_KEY = Regex("s(\\d+)_(\\w+)")

    /** "s2_airport" → (2, "airport"); null si la clave no es de una escala. */
    fun parseStopKey(key: String): Pair<Int, String>? =
        STOP_KEY.matchEntire(key)?.let { it.groupValues[1].toInt() to it.groupValues[2] }

    /** Datos de un tramo dentro de su conexión, para enseñarlo en la lista. */
    data class LegInfo(
        /** Posición del tramo (1 = primero). */
        val number: Int,
        val count: Int,
        /** "Madrid → Mánchester → Pekín". */
        val route: String,
        /** Tiempo entre la llegada del tramo anterior y la salida de este; null en el primer tramo o si no se conoce. */
        val layoverMinutes: Long?,
        /** Aeropuerto donde se hace la escala (el de llegada del tramo anterior). */
        val layoverAt: String?,
    )

    fun newConnectionId(): String = UUID.randomUUID().toString()

    /** Tramos del viaje al que pertenece [flight], por hora de salida. Un vuelo suelto es un único tramo. */
    fun legsOf(flight: FlightEntity, all: List<FlightEntity>): List<FlightEntity> {
        if (flight.connectionId.isBlank()) return listOf(flight)
        return all.filter { it.connectionId == flight.connectionId }
            .sortedWith(compareBy({ it.departureEpochMs }, { it.id }))
            .ifEmpty { listOf(flight) }
    }

    /** null si el vuelo no tiene escalas (es un vuelo suelto o el único tramo que queda de su conexión). */
    fun infoFor(flight: FlightEntity, all: List<FlightEntity>): LegInfo? {
        val legs = legsOf(flight, all)
        if (legs.size < 2) return null
        val index = legs.indexOfFirst { it.id == flight.id }
        if (index < 0) return null
        val previous = legs.getOrNull(index - 1)
        return LegInfo(
            number = index + 1, count = legs.size, route = routeText(legs),
            layoverMinutes = previous?.let { layoverMinutes(it, flight) },
            layoverAt = previous?.let { Airports.displayName(it.arrivalAirport) },
        )
    }

    /** "Madrid → Mánchester → Pekín": la salida del primer tramo y la llegada de cada uno. */
    fun routeText(legs: List<FlightEntity>): String {
        val stops = ArrayList<String>()
        legs.forEachIndexed { i, leg ->
            val from = Airports.displayName(leg.departureAirport)
            // Si un tramo sale de un aeropuerto distinto al que llegó el anterior, también se nombra.
            if (i == 0 || !stops.last().equals(from.trim(), ignoreCase = true)) stops += from.trim()
            stops += Airports.displayName(leg.arrivalAirport).trim()
        }
        return stops.joinToString(" → ")
    }

    /** Minutos de espera entre dos tramos; null si no se conoce la llegada del primero o las horas no cuadran. */
    fun layoverMinutes(previous: FlightEntity, next: FlightEntity): Long? {
        if (!FlightCodec.hasArrival(previous)) return null
        val minutes = (next.departureEpochMs - previous.arrivalEpochMs) / 60_000L
        return minutes.takeIf { it >= 0 }
    }

    // ---- Formulario: un solo formulario para todo el viaje, con las escalas entre la salida y la llegada ----------------

    /** Número de escalas que pide el formulario (0 = vuelo directo). */
    fun stopsOf(values: FormValues): Int = values["stops"]?.trim()?.toIntOrNull()?.coerceIn(0, MAX_STOPS) ?: 0

    /**
     * Pone [count] escalas en el formulario (0 las quita). Las escalas que aparecen por primera vez empiezan el mismo día que
     * el vuelo anterior; lo que el usuario ya hubiera escrito en ellas no se toca, aunque estén ocultas un rato.
     */
    fun withStops(values: FormValues, count: Int): FormValues {
        val n = count.coerceIn(0, MAX_STOPS)
        val next = values.toMutableMap()
        if (n == 0) { next.remove("stops"); return next }
        next["stops"] = n.toString()
        var date = values["depDate"].orEmpty().trim()
        for (i in 1..n) {
            val arr = stopKey(i, "arrDate"); val dep = stopKey(i, "depDate")
            if (next[arr].isNullOrBlank() && date.isNotEmpty()) next[arr] = date
            if (next[dep].isNullOrBlank() && date.isNotEmpty()) next[dep] = next[arr].orEmpty().ifBlank { date }
            date = next[dep].orEmpty().trim().ifEmpty { date }
        }
        return next
    }

    /** Claves del formulario que describen el tramo [k] (1 = el primero) de un viaje con [n] escalas. */
    private class LegKeys(k: Int, n: Int) {
        private val first = k == 1
        private val last = k == n + 1
        private val dep = k - 1   // la escala de la que sale este tramo
        private val arr = k       // la escala a la que llega

        fun formKey(canonical: String): String = when (canonical) {
            "airline" -> if (first) "airline" else stopKey(dep, "airline")
            "flightNumber" -> if (first) "flightNumber" else stopKey(dep, "flightNumber")
            "departureAirport" -> if (first) "departureAirport" else stopKey(dep, "airport")
            "depDate" -> if (first) "depDate" else stopKey(dep, "depDate")
            "depTime" -> if (first) "depTime" else stopKey(dep, "depTime")
            "depZone" -> if (first) "depZone" else stopKey(dep, "zone")
            "arrivalAirport" -> if (last) "arrivalAirport" else stopKey(arr, "airport")
            "arrDate" -> if (last) "arrDate" else stopKey(arr, "arrDate")
            "arrTime" -> if (last) "arrTime" else stopKey(arr, "arrTime")
            "arrZone" -> if (last) "arrZone" else stopKey(arr, "zone")
            else -> canonical
        }
    }

    private val LEG_FIELDS = listOf(
        "airline", "flightNumber", "departureAirport", "depDate", "depTime", "depZone",
        "arrivalAirport", "arrDate", "arrTime", "arrZone",
    )

    /** Los datos de la reserva valen para todos los tramos; el resto (terminal, asiento, precio, notas) es del primero. */
    private val SHARED_FIELDS = listOf("status", "locator", "ticketNumber", "cabinClass", "baggage", "currency")
    private val FIRST_LEG_FIELDS = listOf("terminal", "seat", "price", "notes")

    /** Valores del tramo [k] con los nombres de campo de [FlightCodec], sacados del formulario del viaje entero. */
    private fun rawLeg(v: FormValues, k: Int, n: Int): MutableMap<String, String> {
        val keys = LegKeys(k, n)
        val out = LinkedHashMap<String, String>()
        LEG_FIELDS.forEach { out[it] = v[keys.formKey(it)].orEmpty().trim() }
        SHARED_FIELDS.forEach { out[it] = v[it].orEmpty() }
        if (k == 1) FIRST_LEG_FIELDS.forEach { out[it] = v[it].orEmpty() }
        // Llegada a una escala: la hora es opcional; una fecha sola (la que se rellena de serie) se ignora.
        if (k <= n && out["arrTime"].isNullOrBlank()) out["arrDate"] = ""
        return out
    }

    /**
     * Los vuelos que salen del formulario: uno si no hay escalas, y [stopsOf] + 1 si las hay (todos con [connectionId]).
     * Los aeropuertos se guardan con su nombre ("Madrid"), no con el código.
     */
    fun legValues(values: FormValues, connectionId: String): List<FormValues> {
        val n = stopsOf(values)
        return (1..n + 1).map { k ->
            rawLeg(values, k, n).also {
                it["departureAirport"] = Airports.displayName(it["departureAirport"].orEmpty())
                it["arrivalAirport"] = Airports.displayName(it["arrivalAirport"].orEmpty())
                it["connectionId"] = if (n == 0) "" else connectionId
            }
        }
    }

    /**
     * Los vuelos que se guardan para este formulario. [existing] son los tramos ya guardados de este viaje, en orden: cada
     * tramo del formulario actualiza el que ocupa su misma posición (y conserva su terminal, asiento y notas); los que sobran
     * se añaden y los que ya no hacen falta quedan fuera de la lista (el que guarda los borra).
     */
    fun buildLegs(tripId: Long, connectionId: String, values: FormValues, existing: List<FlightEntity>): List<FlightEntity> {
        val legs = legValues(values, connectionId).mapIndexed { i, leg -> FlightCodec.build(tripId, existing.getOrNull(i)?.id ?: 0L, leg) }
        return legs.mapIndexed { i, built ->
            val old = existing.getOrNull(i)
            when {
                old == null || i == 0 -> built
                else -> built.copy(
                    terminal = old.terminal, seat = old.seat, notes = old.notes,
                    // Un precio en otra moneda no se puede sumar al del primer tramo: se queda donde estaba.
                    priceMinor = if (old.currency != built.currency) old.priceMinor else built.priceMinor,
                    currency = if (old.currency != built.currency && old.priceMinor != null) old.currency else built.currency,
                )
            }
        }
    }

    /** El formulario de un viaje ya guardado: los valores del primer tramo, las escalas y la llegada del último. */
    fun formValues(legs: List<FlightEntity>): FormValues {
        val used = legs.take(MAX_STOPS + 1)
        val first = used.first()
        val n = used.size - 1
        val out = LinkedHashMap(FlightCodec.values(first))
        if (n == 0) return out
        val lastValues = FlightCodec.values(used.last())
        listOf("arrivalAirport", "arrDate", "arrTime", "arrZone").forEach { out[it] = lastValues[it].orEmpty() }
        out["stops"] = n.toString()
        for (i in 1..n) {
            val before = FlightCodec.values(used[i - 1]); val after = FlightCodec.values(used[i])
            out[stopKey(i, "airport")] = used[i - 1].arrivalAirport
            out[stopKey(i, "arrDate")] = before["arrDate"].orEmpty(); out[stopKey(i, "arrTime")] = before["arrTime"].orEmpty()
            out[stopKey(i, "zone")] = used[i - 1].arrivalZoneId
            out[stopKey(i, "depDate")] = after["depDate"].orEmpty(); out[stopKey(i, "depTime")] = after["depTime"].orEmpty()
            out[stopKey(i, "airline")] = used[i].airline; out[stopKey(i, "flightNumber")] = used[i].flightNumber
        }
        // Un solo precio para todo el viaje: la suma de los tramos que están en la misma moneda que el primero.
        val same = used.filter { it.currency == first.currency && it.priceMinor != null }
        out["price"] = if (same.isEmpty()) "" else Money.formatPlain(same.sumOf { it.priceMinor ?: 0L }, first.currency)
        return out
    }

    /**
     * Errores que dependen de varios campos: cada llegada no puede ser anterior a su salida, y cada vuelo no puede salir antes
     * de que llegue el anterior. Las claves son las del formulario (p. ej. "s1_depDate").
     */
    fun cross(v: FormValues): Map<String, FieldError> {
        val n = stopsOf(v)
        if (n == 0) return FlightCodec.cross(v)
        val errors = LinkedHashMap<String, FieldError>()
        var previous: Map<String, String>? = null
        for (k in 1..n + 1) {
            val keys = LegKeys(k, n)
            val leg = rawLeg(v, k, n)
            FlightCodec.cross(leg).forEach { (canonical, e) -> errors.putIfAbsent(keys.formKey(canonical), e) }
            previous?.let { prev ->
                val reached = FlightCodec.arrivalMs(prev) ?: FlightCodec.departureMs(prev)
                val left = FlightCodec.departureMs(leg)
                if (reached != null && left != null && left < reached) {
                    errors.putIfAbsent(keys.formKey("depDate"), FieldError.DEPARTS_BEFORE_ARRIVAL)
                }
            }
            previous = leg
        }
        return errors
    }
}
