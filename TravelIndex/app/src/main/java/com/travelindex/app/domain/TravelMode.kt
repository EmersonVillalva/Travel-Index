package com.travelindex.app.domain

/** Medio de transporte con el que se hace un viaje. Un viaje puede combinar varios (p. ej. avión + tren). */
enum class TravelMode { PLANE, SHIP, TRAIN, BUS, CAR }

/**
 * Los medios elegidos se guardan en una sola columna de texto: las claves separadas por comas, siempre en el
 * orden fijo del enum y sin repetir ("PLANE,CAR"). Las claves desconocidas se ignoran, así un viaje recibido de
 * una versión futura con un medio nuevo se abre igualmente.
 */
object TravelModes {
    fun parse(csv: String?): List<TravelMode> {
        if (csv.isNullOrBlank()) return emptyList()
        val wanted = csv.split(',').map { it.trim().uppercase() }.toSet()
        return TravelMode.entries.filter { it.name in wanted }
    }

    fun encode(modes: Collection<TravelMode>): String =
        TravelMode.entries.filter { it in modes }.joinToString(",") { it.name }

    /** Texto limpio y ordenado: lo que se guarda en la base de datos y en los viajes compartidos. */
    fun normalize(csv: String?): String = encode(parse(csv))

    /** Marca o desmarca [mode] en la selección [csv] y devuelve la nueva selección. */
    fun toggle(csv: String?, mode: TravelMode): String {
        val current = parse(csv).toMutableSet()
        if (!current.add(mode)) current.remove(mode)
        return encode(current)
    }
}
