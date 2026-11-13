package com.travelindex.app.domain

import java.text.Normalizer

/** Un aeropuerto conocido. En la app solo se muestra el [name]; el código IATA se usa para buscar y como alias. */
data class Airport(val code: String, val name: String, val zone: String)

/**
 * Catálogo de los aeropuertos más habituales, para sugerir su nombre y rellenar la zona horaria automáticamente.
 * Si el aeropuerto no está aquí, el usuario puede escribir su nombre libremente.
 */
object Airports {
    private val LIST: List<Airport> = buildList {
        /** Cada elemento es "CÓDIGO=Nombre". */
        fun z(zone: String, vararg items: String) = items.forEach {
            val i = it.indexOf('=')
            add(Airport(it.substring(0, i), it.substring(i + 1), zone))
        }
        z("Europe/Madrid", "MAD=Madrid-Barajas", "BCN=Barcelona-El Prat", "AGP=Málaga-Costa del Sol", "VLC=Valencia", "SVQ=Sevilla",
            "PMI=Palma de Mallorca", "IBZ=Ibiza", "MAH=Menorca", "BIO=Bilbao", "ALC=Alicante-Elche", "SCQ=Santiago de Compostela",
            "OVD=Asturias", "VGO=Vigo", "GRX=Granada-Jaén", "SDR=Santander", "ZAZ=Zaragoza", "LEI=Almería", "REU=Reus",
            "GRO=Girona-Costa Brava")
        z("Atlantic/Canary", "TFN=Tenerife Norte", "TFS=Tenerife Sur", "LPA=Gran Canaria", "ACE=Lanzarote", "FUE=Fuerteventura",
            "SPC=La Palma", "VDE=El Hierro", "GMZ=La Gomera")
        z("Europe/Lisbon", "LIS=Lisboa", "OPO=Oporto", "FAO=Faro", "FNC=Madeira (Funchal)")
        z("Europe/Paris", "CDG=París Charles de Gaulle", "ORY=París Orly", "NCE=Niza", "LYS=Lyon", "MRS=Marsella", "TLS=Toulouse",
            "BOD=Burdeos", "NTE=Nantes", "BVA=París Beauvais")
        z("Europe/London", "LHR=Londres Heathrow", "LGW=Londres Gatwick", "STN=Londres Stansted", "LTN=Londres Luton",
            "LCY=Londres City", "MAN=Mánchester", "EDI=Edimburgo", "BHX=Birmingham", "GLA=Glasgow", "BRS=Bristol")
        z("Europe/Dublin", "DUB=Dublín", "ORK=Cork", "SNN=Shannon")
        z("Europe/Berlin", "FRA=Fráncfort", "MUC=Múnich", "BER=Berlín Brandeburgo", "DUS=Düsseldorf", "HAM=Hamburgo",
            "CGN=Colonia/Bonn", "STR=Stuttgart")
        z("Europe/Amsterdam", "AMS=Ámsterdam Schiphol", "EIN=Eindhoven")
        z("Europe/Brussels", "BRU=Bruselas", "CRL=Charleroi (Bruselas Sur)")
        z("Europe/Zurich", "ZRH=Zúrich", "GVA=Ginebra", "BSL=Basilea-Mulhouse")
        z("Europe/Vienna", "VIE=Viena", "SZG=Salzburgo")
        z("Europe/Rome", "FCO=Roma Fiumicino", "CIA=Roma Ciampino", "MXP=Milán Malpensa", "LIN=Milán Linate", "BGY=Milán Bérgamo",
            "VCE=Venecia Marco Polo", "TSF=Venecia Treviso", "NAP=Nápoles", "BLQ=Bolonia", "FLR=Florencia", "PSA=Pisa",
            "CTA=Catania", "PMO=Palermo", "BRI=Bari", "TRN=Turín", "CAG=Cagliari")
        z("Europe/Athens", "ATH=Atenas", "SKG=Salónica", "HER=Heraclión (Creta)", "JTR=Santorini", "JMK=Míconos", "RHO=Rodas", "CFU=Corfú")
        z("Europe/Istanbul", "IST=Estambul", "SAW=Estambul Sabiha Gökçen", "AYT=Antalya", "ESB=Ankara Esenboğa")
        z("Europe/Warsaw", "WAW=Varsovia Chopin", "KRK=Cracovia")
        z("Europe/Prague", "PRG=Praga")
        z("Europe/Budapest", "BUD=Budapest")
        z("Europe/Copenhagen", "CPH=Copenhague")
        z("Europe/Stockholm", "ARN=Estocolmo Arlanda")
        z("Europe/Oslo", "OSL=Oslo")
        z("Europe/Helsinki", "HEL=Helsinki")
        z("Atlantic/Reykjavik", "KEF=Reikiavik Keflavík")
        z("Europe/Zagreb", "ZAG=Zagreb", "SPU=Split", "DBV=Dubrovnik")
        z("Europe/Malta", "MLA=Malta")
        z("Asia/Nicosia", "LCA=Lárnaca")
        z("Europe/Moscow", "SVO=Moscú Sheremétievo", "DME=Moscú Domodédovo", "VKO=Moscú Vnúkovo")
        z("America/New_York", "JFK=Nueva York JFK", "LGA=Nueva York LaGuardia", "EWR=Newark", "BOS=Boston Logan", "MIA=Miami",
            "ATL=Atlanta", "IAD=Washington Dulles", "DCA=Washington Reagan", "PHL=Filadelfia", "MCO=Orlando",
            "FLL=Fort Lauderdale", "CLT=Charlotte", "DTW=Detroit")
        z("America/Chicago", "ORD=Chicago O'Hare", "MDW=Chicago Midway", "DFW=Dallas-Fort Worth", "IAH=Houston Intercontinental",
            "MSY=Nueva Orleans", "AUS=Austin")
        z("America/Denver", "DEN=Denver", "SLC=Salt Lake City")
        z("America/Los_Angeles", "LAX=Los Ángeles", "SFO=San Francisco", "SEA=Seattle-Tacoma", "SAN=San Diego", "LAS=Las Vegas",
            "PDX=Portland")
        z("America/Phoenix", "PHX=Phoenix")
        z("Pacific/Honolulu", "HNL=Honolulu")
        z("America/Toronto", "YYZ=Toronto Pearson", "YUL=Montreal Trudeau", "YOW=Ottawa")
        z("America/Vancouver", "YVR=Vancouver")
        z("America/Mexico_City", "MEX=Ciudad de México", "GDL=Guadalajara")
        z("America/Cancun", "CUN=Cancún")
        z("America/Bogota", "BOG=Bogotá El Dorado", "MDE=Medellín José María Córdova", "CTG=Cartagena de Indias")
        z("America/Lima", "LIM=Lima Jorge Chávez", "CUZ=Cusco")
        z("America/Santiago", "SCL=Santiago de Chile")
        z("America/Argentina/Buenos_Aires", "EZE=Buenos Aires Ezeiza", "AEP=Buenos Aires Aeroparque")
        z("America/Sao_Paulo", "GRU=São Paulo Guarulhos", "GIG=Río de Janeiro Galeão", "CGH=São Paulo Congonhas", "BSB=Brasilia")
        z("America/Havana", "HAV=La Habana")
        z("America/Panama", "PTY=Panamá Tocumen")
        z("America/Puerto_Rico", "SJU=San Juan (Puerto Rico)")
        z("America/Santo_Domingo", "PUJ=Punta Cana", "SDQ=Santo Domingo Las Américas")
        z("America/Guayaquil", "UIO=Quito", "GYE=Guayaquil")
        z("America/Caracas", "CCS=Caracas Maiquetía")
        z("America/Montevideo", "MVD=Montevideo")
        z("Asia/Dubai", "DXB=Dubái", "AUH=Abu Dabi", "DWC=Dubái Al Maktoum")
        z("Asia/Qatar", "DOH=Doha Hamad")
        z("Asia/Bangkok", "BKK=Bangkok Suvarnabhumi", "DMK=Bangkok Don Mueang", "HKT=Phuket", "CNX=Chiang Mai")
        z("Asia/Singapore", "SIN=Singapur Changi")
        z("Asia/Hong_Kong", "HKG=Hong Kong")
        z("Asia/Tokyo", "NRT=Tokio Narita", "HND=Tokio Haneda", "KIX=Osaka Kansai", "NGO=Nagoya", "FUK=Fukuoka", "CTS=Sapporo Nueva Chitose")
        z("Asia/Seoul", "ICN=Seúl Incheon", "GMP=Seúl Gimpo")
        z("Asia/Shanghai", "PEK=Pekín Capital", "PKX=Pekín Daxing", "PVG=Shanghái Pudong", "SHA=Shanghái Hongqiao",
            "CAN=Cantón Baiyun", "CTU=Chengdu Shuangliu", "SZX=Shenzhen Bao'an")
        z("Asia/Kolkata", "DEL=Nueva Delhi", "BOM=Bombay", "BLR=Bangalore", "MAA=Chennai", "CCU=Calcuta", "HYD=Hyderabad")
        z("Asia/Kuala_Lumpur", "KUL=Kuala Lumpur")
        z("Asia/Jakarta", "CGK=Yakarta Soekarno-Hatta")
        z("Asia/Makassar", "DPS=Bali Denpasar")
        z("Asia/Manila", "MNL=Manila", "CEB=Cebú")
        z("Asia/Taipei", "TPE=Taipéi Taoyuan")
        z("Asia/Ho_Chi_Minh", "SGN=Ho Chi Minh", "HAN=Hanói", "DAD=Da Nang")
        z("Asia/Jerusalem", "TLV=Tel Aviv Ben Gurión")
        z("Asia/Amman", "AMM=Amán Reina Alia")
        z("Africa/Cairo", "CAI=El Cairo", "HRG=Hurghada", "SSH=Sharm el-Sheij")
        z("Africa/Casablanca", "CMN=Casablanca Mohamed V", "RAK=Marrakech", "TNG=Tánger", "AGA=Agadir", "FEZ=Fez")
        z("Africa/Johannesburg", "JNB=Johannesburgo", "CPT=Ciudad del Cabo")
        z("Africa/Nairobi", "NBO=Nairobi")
        z("Africa/Addis_Ababa", "ADD=Adís Abeba")
        z("Africa/Lagos", "LOS=Lagos")
        z("Africa/Algiers", "ALG=Argel")
        z("Africa/Tunis", "TUN=Túnez-Cartago")
        z("Australia/Sydney", "SYD=Sídney")
        z("Australia/Melbourne", "MEL=Melbourne")
        z("Australia/Brisbane", "BNE=Brisbane")
        z("Australia/Perth", "PER=Perth")
        z("Pacific/Auckland", "AKL=Auckland")
    }

    private val BY_CODE: Map<String, Airport> = LIST.associateBy { it.code }
    private val BY_NAME: Map<String, Airport> = LIST.associateBy { fold(it.name) }

    /** Minúsculas y sin acentos, para buscar "malaga" y encontrar "Málaga". */
    private fun fold(s: String): String =
        Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

    val all: List<Airport> get() = LIST
    val codes: Set<String> get() = BY_CODE.keys

    /** Aeropuerto por código IATA ("MAD"), o null si no está en la lista. */
    fun byCode(code: String): Airport? = BY_CODE[code.trim().uppercase()]

    /** Aeropuerto cuyo nombre coincide exactamente con el texto (sin distinguir mayúsculas ni acentos), o cuyo código IATA es el texto. */
    fun exact(text: String): Airport? {
        val t = text.trim()
        if (t.isEmpty()) return null
        BY_NAME[fold(t)]?.let { return it }
        return BY_CODE[t.uppercase()]
    }

    /**
     * Sugerencias para lo que el usuario está escribiendo. Busca en el nombre y en el código.
     * Orden: coincidencia exacta de código, nombre que empieza por el texto, nombre que lo contiene.
     */
    fun search(query: String, limit: Int = 5): List<Airport> {
        val q = fold(query)
        if (q.length < 2) return emptyList()
        val starts = ArrayList<Airport>()
        val contains = ArrayList<Airport>()
        val byCode = BY_CODE[q.uppercase()]
        for (a in LIST) {
            if (a === byCode) continue
            val n = fold(a.name)
            when {
                n.startsWith(q) -> starts += a
                n.contains(q) -> contains += a
            }
        }
        return (listOfNotNull(byCode) + starts + contains).take(limit)
    }

    /** Nombre para mostrar: si el texto es un código IATA conocido ("MAD") se muestra su nombre; si no, el texto tal cual. */
    fun displayName(text: String): String {
        val t = text.trim()
        BY_CODE[t.uppercase()]?.let { if (t.length == 3) return it.name }
        // "Madrid (MAD)" → "Madrid"
        return t.replace(Regex("\\s*\\([A-Za-z]{3}\\)\\s*$"), "").ifEmpty { t }
    }

    /** Zona horaria si el texto es un aeropuerto conocido (por nombre o por código IATA, solo o entre paréntesis), o null. */
    fun zoneFor(text: String): String? {
        val t = text.trim()
        if (t.isEmpty()) return null
        exact(t)?.let { return it.zone }
        // Solo "Nombre (XXX)" o "XXX - Nombre": una palabra suelta de 3 letras ("Las Palmas", "San Antonio") NO es un código.
        val code = Regex("\\(([A-Za-z]{3})\\)").find(t)?.groupValues?.get(1)
            ?: Regex("^([A-Za-z]{3})\\s*[-–—,:]").find(t)?.groupValues?.get(1)
        return code?.uppercase()?.let { BY_CODE[it]?.zone }
    }

    fun zones(): Collection<String> = LIST.map { it.zone }.toSet()
}
