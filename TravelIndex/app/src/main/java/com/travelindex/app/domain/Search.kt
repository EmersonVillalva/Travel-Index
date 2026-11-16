package com.travelindex.app.domain

import java.text.Normalizer

/** Dónde encaja un resultado del buscador: el viaje entero, una reserva o un archivo. */
enum class SearchKind { TRIP, ITEM, FILE }

/**
 * Un resultado: lo justo para pintarlo y navegar hasta él. Para una reserva, [route] es el nombre de ruta
 * del módulo o apartado (FLIGHT, TRAIN, LODGING...) e [itemId] su id; para un archivo, [itemId] es el id
 * del adjunto y [mime] permite decidir si lo abre el visor interno.
 */
data class SearchHit(
    val tripId: Long,
    val tripName: String,
    val kind: SearchKind,
    val itemId: Long = 0,
    val title: String,
    val subtitle: String? = null,
    val route: String? = null,
    val mime: String? = null,
)

/**
 * Buscador global: compara sin acentos ni mayúsculas ("via" encuentra "Viaje a Edimburgo") sobre los campos
 * con nombre de cada cosa. Lógica pura, probada con JUnit; el límite evita listas absurdas con una letra.
 */
object TripSearch {
    const val MIN_QUERY = 2
    const val MAX_HITS = 60

    fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase().trim(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    private fun matches(q: String, vararg fields: String?): Boolean =
        fields.any { !it.isNullOrBlank() && normalize(it).contains(q) }

    fun search(query: String, bundles: List<TripBundle>): List<SearchHit> {
        val q = normalize(query)
        if (q.length < MIN_QUERY) return emptyList()
        val out = mutableListOf<SearchHit>()
        bundles.forEach { b ->
            val t = b.trip
            fun hit(kind: SearchKind, title: String, subtitle: String?, route: String? = null, itemId: Long = 0, mime: String? = null) {
                out += SearchHit(t.id, t.name, kind, itemId, title, subtitle?.takeIf { it.isNotBlank() }, route, mime)
            }
            if (matches(q, t.name, t.city, t.country, t.description)) {
                hit(SearchKind.TRIP, t.name, listOf(t.city, t.country).filter { it.isNotBlank() }.joinToString(", "))
            }
            b.flights.forEach { f ->
                if (matches(q, f.airline, f.flightNumber, f.departureAirport, f.arrivalAirport, Airports.displayName(f.departureAirport), Airports.displayName(f.arrivalAirport))) {
                    hit(SearchKind.ITEM, "${f.airline} ${f.flightNumber}".trim(),
                        Airports.displayName(f.departureAirport) + " → " + Airports.displayName(f.arrivalAirport),
                        route = "FLIGHT", itemId = f.id)
                }
            }
            b.lodgings.forEach { l ->
                if (matches(q, l.name, l.address)) hit(SearchKind.ITEM, l.name, l.address, route = ModuleType.LODGING.name, itemId = l.id)
            }
            b.activities.forEach { a ->
                if (matches(q, a.name, a.location)) hit(SearchKind.ITEM, a.name, a.location, route = ModuleType.ACTIVITY.name, itemId = a.id)
            }
            b.transports.forEach { tr ->
                if (matches(q, tr.company, tr.model, tr.originPlace, tr.destinationPlace)) {
                    val routeText = listOf(tr.originPlace, tr.destinationPlace).filter { it.isNotBlank() }.joinToString(" → ")
                    hit(SearchKind.ITEM, tr.company.ifBlank { tr.model }.ifBlank { routeText },
                        routeText.takeIf { it.isNotBlank() && it != tr.company },
                        route = TransportSection.forTransport(tr.type).routeName, itemId = tr.id)
                }
            }
            b.restaurants.forEach { r ->
                if (matches(q, r.name, r.address)) hit(SearchKind.ITEM, r.name, r.address, route = ModuleType.RESTAURANT.name, itemId = r.id)
            }
            b.docs.forEach { d ->
                if (matches(q, d.title, d.docType, d.issuingCountry)) {
                    hit(SearchKind.ITEM, d.title.ifBlank { d.docType }, d.issuingCountry, route = ModuleType.TRAVEL_DOC.name, itemId = d.id)
                }
            }
            b.attachments.forEach { a ->
                if (matches(q, a.displayName)) hit(SearchKind.FILE, a.displayName, null, itemId = a.id, mime = a.mimeType)
            }
        }
        return out.take(MAX_HITS)
    }
}
