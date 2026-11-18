package com.travelindex.app.domain

import com.travelindex.app.data.db.FlightEntity
import com.travelindex.app.domain.forms.FlightCodec
import java.util.UUID

/**
 * Vuelos con escalas (p. ej. Madrid → Mánchester → Pekín). Cada tramo es un vuelo normal (con su aerolínea, número y horas)
 * y los tramos de un mismo viaje comparten [FlightEntity.connectionId]. Así el itinerario, los avisos, el calendario y el
 * PDF siguen tratando cada tramo como un vuelo, y aquí solo se enlazan para mostrarlos como un solo viaje.
 */
object FlightConnections {
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

    /** ¿Es [flight] el último tramo de su conexión? (Solo desde el último se añade otro tramo a continuación.) */
    fun isLastLeg(flight: FlightEntity, all: List<FlightEntity>): Boolean = legsOf(flight, all).last().id == flight.id

    /**
     * Valores iniciales del formulario del tramo siguiente a [saved]: sale de donde llegó el anterior, el mismo día y en la
     * misma zona horaria, y conserva los datos de la reserva (localizador, billete, clase, equipaje, moneda). Aerolínea,
     * número, horas, asiento y precio quedan vacíos: son de cada tramo.
     */
    fun nextLegValues(saved: Map<String, String>, connectionId: String): Map<String, String> {
        fun s(key: String) = saved[key].orEmpty()
        val date = s("arrDate").ifBlank { s("depDate") }
        val zone = s("arrZone").ifBlank { s("depZone") }
        return mapOf(
            "connectionId" to connectionId,
            "departureAirport" to s("arrivalAirport"),
            "depDate" to date, "depZone" to zone,
            "arrDate" to date, "arrZone" to zone,
            "locator" to s("locator"), "ticketNumber" to s("ticketNumber"),
            "cabinClass" to s("cabinClass"), "baggage" to s("baggage"),
            "currency" to s("currency"), "status" to s("status").ifBlank { "CONFIRMED" },
        )
    }
}
