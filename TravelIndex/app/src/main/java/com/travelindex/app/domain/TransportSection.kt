package com.travelindex.app.domain

/**
 * Apartado de transporte de un viaje. Los vuelos tienen módulo propio; tren, autobús, barco y coche usan el módulo de
 * transporte filtrado por su tipo, con campos adaptados a cada medio (un tren no pide número de vuelo).
 *
 * [routeName] es el nombre que viaja en la ruta de navegación; no choca con los nombres de [ModuleType].
 */
enum class TransportSection(val routeName: String, val module: ModuleType, val transportType: String?, val mode: TravelMode?) {
    FLIGHTS("FLIGHT", ModuleType.FLIGHT, null, TravelMode.PLANE),
    TRAINS("TRAIN", ModuleType.TRANSPORT, "TRAIN", TravelMode.TRAIN),
    BUSES("BUS", ModuleType.TRANSPORT, "BUS", TravelMode.BUS),
    SHIPS("FERRY", ModuleType.TRANSPORT, "FERRY", TravelMode.SHIP),
    CARS("CAR_RENTAL", ModuleType.TRANSPORT, "CAR_RENTAL", TravelMode.CAR),

    /** Transporte en general, con selector de tipo: todos los transportes (viajes sin medios elegidos). */
    TRANSPORT_ALL("TRANSPORT", ModuleType.TRANSPORT, null, null),

    /** Taxis, traslados y otros tipos que no tienen apartado propio (solo si el viaje los tiene). */
    TRANSPORT_OTHER("TRANSPORT_OTHER", ModuleType.TRANSPORT, null, null);

    /** ¿Pertenece a este apartado un elemento con este tipo de transporte? (Los vuelos no tienen tipo.) */
    fun matches(itemTransportType: String?): Boolean = when {
        this == TRANSPORT_OTHER -> itemTransportType !in KNOWN_TRANSPORT_TYPES
        transportType != null -> itemTransportType == transportType
        else -> true
    }

    companion object {
        private val KNOWN_TRANSPORT_TYPES: Set<String> = setOf("TRAIN", "BUS", "FERRY", "CAR_RENTAL")

        fun fromRoute(name: String?): TransportSection? = entries.firstOrNull { it.routeName == name }

        /** Apartado con el que se abre un transporte guardado, para que salgan los campos de su medio. */
        fun forTransport(type: String?): TransportSection =
            entries.firstOrNull { it.module == ModuleType.TRANSPORT && it.transportType != null && it.transportType == type }
                ?: TRANSPORT_ALL
    }
}

/** Lo que indica una ruta de módulo: el módulo y, si es transporte o vuelos, el apartado concreto. */
data class ModuleTarget(val type: ModuleType, val section: TransportSection?) {
    /** Nombre que va en la ruta de navegación. */
    val routeName: String get() = section?.routeName ?: type.name

    companion object {
        fun parse(route: String?): ModuleTarget {
            val section = TransportSection.fromRoute(route)
            val type = section?.module ?: ModuleType.fromName(route) ?: ModuleType.FLIGHT
            return ModuleTarget(type, section)
        }
    }
}

object TransportSections {
    private val FIXED_SECTIONS = listOf(TransportSection.FLIGHTS, TransportSection.TRANSPORT_ALL)

    /**
     * Apartados de transporte que se ven en el panel del viaje. Sin medios elegidos (viajes antiguos o sin rellenar)
     * salen los de siempre: vuelos y transporte. Con medios elegidos, solo esos: si desmarcas uno, su apartado
     * desaparece aunque tenga reservas (siguen guardadas y vuelven al marcarlo otra vez). La excepción son los
     * transportes sin medio propio (taxi, traslado, otros): no hay casilla que marcar, así que se ven si existen.
     */
    fun shown(modes: List<TravelMode>, transportTypes: List<String>): List<TransportSection> {
        if (modes.isEmpty()) return FIXED_SECTIONS
        return TransportSection.entries.filter { s ->
            when (s) {
                TransportSection.TRANSPORT_ALL -> false
                TransportSection.TRANSPORT_OTHER -> transportTypes.any { s.matches(it) }
                else -> s.mode != null && s.mode in modes
            }
        }
    }

    /** Apartados que se ofrecen en "Agregar": los medios elegidos (o vuelos y transporte si no se eligió ninguno). */
    fun offered(modes: List<TravelMode>): List<TransportSection> =
        if (modes.isEmpty()) FIXED_SECTIONS else TransportSection.entries.filter { it.mode != null && it.mode in modes }

    fun count(section: TransportSection, flightCount: Int, transportTypes: List<String>): Int =
        if (section == TransportSection.FLIGHTS) flightCount else transportTypes.count { section.matches(it) }
}
