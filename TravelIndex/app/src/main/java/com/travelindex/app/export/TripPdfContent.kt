package com.travelindex.app.export

import android.content.Context
import androidx.annotation.StringRes
import com.travelindex.app.R
import com.travelindex.app.data.db.AttachmentEntity
import com.travelindex.app.domain.Airports
import com.travelindex.app.domain.AttachmentCategory
import com.travelindex.app.domain.CostBreakdown
import com.travelindex.app.domain.CostCategory
import com.travelindex.app.domain.DocWarnings
import com.travelindex.app.domain.EventType
import com.travelindex.app.domain.FileGroup
import com.travelindex.app.domain.FileGroups
import com.travelindex.app.domain.Formats
import com.travelindex.app.domain.ItineraryBuilder
import com.travelindex.app.domain.ItineraryEvent
import com.travelindex.app.domain.ModuleType
import com.travelindex.app.domain.Money
import com.travelindex.app.domain.NoteKind
import com.travelindex.app.domain.TransportSection
import com.travelindex.app.domain.TravelModes
import com.travelindex.app.domain.TripBundle
import com.travelindex.app.domain.forms.Durations
import com.travelindex.app.domain.forms.FieldDef
import com.travelindex.app.domain.forms.FieldType
import com.travelindex.app.domain.forms.FlightCodec
import com.travelindex.app.domain.forms.ModuleItem
import com.travelindex.app.domain.forms.ModuleItems
import com.travelindex.app.domain.pdf.PdfBlock
import com.travelindex.app.domain.pdf.PdfBlocks
import com.travelindex.app.domain.pdf.PdfStyle
import com.travelindex.app.domain.pdf.TripPdf
import com.travelindex.app.domain.tripDurationDays
import com.travelindex.app.ui.components.formatMoneyValue
import com.travelindex.app.ui.components.labelRes
import com.travelindex.app.ui.modules.Choices
import com.travelindex.app.ui.modules.ModuleSpec
import com.travelindex.app.ui.modules.ModuleSpecs
import com.travelindex.app.ui.modules.activityEndClock
import java.time.LocalDate
import java.time.LocalTime

/**
 * Contenido del PDF de un viaje, en este orden: resumen, destinos, itinerario día a día, vuelos, trenes, autobuses, barcos,
 * coche, alojamientos, actividades, restaurantes, documentos, gastos, notas y, al final, los archivos adjuntos abiertos (una
 * página por página del archivo, con su tipo y de qué reserva es). Los datos salen de las mismas especificaciones que los
 * formularios (mismas etiquetas y mismo formato que en la app). Los números de documentos (pasaporte, visado...) salen
 * ocultos, como en la pantalla, porque el PDF se puede compartir; por lo mismo, los archivos de visados y documentos de viaje
 * (pasaportes escaneados...) no se meten en el PDF.
 */
class TripPdfContent(
    private val context: Context,
    private val f: Formats,
    private val pages: AttachmentPages,
    /** false si el móvil se quedó sin memoria al meter los archivos: el PDF lleva solo un aviso en su lugar. */
    private val includeFiles: Boolean = true,
) {

    private fun s(@StringRes id: Int): String = context.getString(id)
    private fun days(n: Int): String = context.resources.getQuantityString(R.plurals.duration_days, n, n)
    private fun join(sep: String, vararg parts: String?): String = parts.filter { !it.isNullOrBlank() }.joinToString(sep)

    fun build(b: TripBundle): List<PdfBlock> = buildList<PdfBlock> {
        header(b)
        summary(b)
        destinations(b)
        itinerary(b)
        flights(b)
        transports(b)
        simple(b, ModuleType.LODGING, R.string.mod_lodgings) { it.values["checkInDate"].orEmpty() }
        simple(b, ModuleType.ACTIVITY, R.string.mod_activities) { it.values["date"].orEmpty() + it.values["startTime"].orEmpty() }
        simple(b, ModuleType.RESTAURANT, R.string.mod_restaurants) { it.values["date"].orEmpty() + it.values["time"].orEmpty() }
        documents(b)
        costs(b)
        notes(b)
        files(b)
    }

    // ---- Cabecera y resumen ---------------------------------------------------------------------

    private fun MutableList<PdfBlock>.header(b: TripBundle) {
        val t = b.trip
        add(PdfBlock.Paragraph(t.name, PdfStyle.TITLE))
        add(
            PdfBlock.Paragraph(
                join(" · ", join(", ", t.city, t.country), f.range(t.startDate, t.endDate), days(tripDurationDays(t.startDate, t.endDate))),
                PdfStyle.SUBTITLE,
            ),
        )
        if (t.cancelled) add(PdfBlock.Paragraph(s(R.string.pdf_cancelled), PdfStyle.ENTRY_TITLE))
        add(PdfBlock.Gap(6f))
        add(PdfBlock.Rule)
    }

    private fun MutableList<PdfBlock>.summary(b: TripBundle) {
        val t = b.trip
        val modes = TravelModes.parse(t.travelModes).joinToString(", ") { s(it.labelRes()) }
        val rows = listOf(
            s(R.string.pdf_facts_dates) to join(" · ", f.range(t.startDate, t.endDate), days(tripDurationDays(t.startDate, t.endDate))),
            s(R.string.pdf_facts_destination) to join(", ", t.city, t.country),
            s(R.string.field_travelers) to t.travelers.toString(),
            s(R.string.pdf_facts_transport) to modes,
            s(R.string.pdf_budget_planned) to (t.budgetMinor?.let { Money.format(it, t.currency) }.orEmpty()),
            s(R.string.field_description) to t.description.trim(),
            s(R.string.field_notes) to t.notes.trim(),
        ).filter { it.second.isNotBlank() }
        if (rows.isEmpty()) return
        addAll(PdfBlocks.section(s(R.string.pdf_summary), listOf(PdfBlocks.entry("", "", rows))))
    }

    private fun MutableList<PdfBlock>.destinations(b: TripBundle) {
        if (b.destinations.isEmpty()) return
        val rows = b.destinations.sortedBy { it.position }.map { d ->
            join(", ", d.city, d.country) to (f.range(d.startDate, d.endDate) + " · " + days(tripDurationDays(d.startDate, d.endDate)))
        }
        addAll(PdfBlocks.section(s(R.string.mod_destinations), listOf(PdfBlocks.entry("", "", rows))))
    }

    // ---- Itinerario -----------------------------------------------------------------------------

    private fun eventLabel(e: ItineraryEvent): String = when (e.type) {
        EventType.FLIGHT -> s(R.string.ev_flight)
        EventType.LODGING_CHECK_IN -> s(R.string.ev_check_in)
        EventType.LODGING_CHECK_OUT -> s(R.string.ev_check_out)
        EventType.ACTIVITY -> s(R.string.ev_activity)
        EventType.TRANSPORT_START -> Choices.label(Choices.transportType, e.kind.orEmpty())?.let { s(it) } ?: s(R.string.ev_transport)
        EventType.TRANSPORT_END -> s(R.string.ev_transport_end)
        EventType.RESTAURANT -> s(R.string.ev_restaurant)
        EventType.NOTE_DUE -> s(R.string.ev_reminder)
    }

    private fun MutableList<PdfBlock>.itinerary(b: TripBundle) {
        val daysList = ItineraryBuilder.group(b.events)
        if (daysList.isEmpty()) return
        val entries = daysList.map { day ->
            val rows = day.events.map { e ->
                val time = if (!e.timeKnown) s(R.string.time_unknown)
                else f.time(e.start) + if (e.type == EventType.ACTIVITY && e.end != null) " – " + f.time(e.end) else ""
                val text = join(" · ", eventLabel(e), e.title) + (e.subtitle?.let { "\n$it" } ?: "")
                time to text
            }
            PdfBlocks.entry(f.full(day.date).replaceFirstChar { it.uppercase() }, "", rows)
        }
        addAll(PdfBlocks.section(s(R.string.mod_itinerary), entries))
    }

    // ---- Reservas -------------------------------------------------------------------------------

    private fun MutableList<PdfBlock>.flights(b: TripBundle) {
        val items = ModuleItems.of(ModuleType.FLIGHT, b).sortedBy { it.values["depDate"].orEmpty() + it.values["depTime"].orEmpty() }
        val spec = ModuleSpecs.of(ModuleType.FLIGHT)
        addAll(PdfBlocks.section(s(R.string.mod_flights), items.map { entryFor(spec, it) }))
    }

    private fun MutableList<PdfBlock>.transports(b: TripBundle) {
        val all = ModuleItems.of(ModuleType.TRANSPORT, b).sortedBy { it.values["startDate"].orEmpty() + it.values["startTime"].orEmpty() }
        listOf(
            TransportSection.TRAINS to R.string.mod_trains, TransportSection.BUSES to R.string.mod_buses,
            TransportSection.SHIPS to R.string.mod_ships, TransportSection.CARS to R.string.mod_cars,
            TransportSection.TRANSPORT_ALL to R.string.files_group_transport_other,
        ).forEach { (section, title) ->
            val items = all.filter { TransportSection.forTransport(it.values["type"]) == section }
            val spec = ModuleSpecs.of(ModuleType.TRANSPORT, section)
            addAll(PdfBlocks.section(s(title), items.map { entryFor(spec, it) }))
        }
    }

    private fun MutableList<PdfBlock>.simple(b: TripBundle, type: ModuleType, @StringRes title: Int, order: (ModuleItem) -> String) {
        val spec = ModuleSpecs.of(type)
        addAll(PdfBlocks.section(s(title), ModuleItems.of(type, b).sortedBy(order).map { entryFor(spec, it) }))
    }

    private fun MutableList<PdfBlock>.documents(b: TripBundle) {
        val spec = ModuleSpecs.of(ModuleType.TRAVEL_DOC)
        val entries = ModuleItems.of(ModuleType.TRAVEL_DOC, b).map { entryFor(spec, it) }
        if (entries.isEmpty()) return
        addAll(PdfBlocks.section(s(R.string.mod_docs), listOf(PdfBlock.Keep(listOf(PdfBlock.Paragraph(s(R.string.pdf_docs_note), PdfStyle.SMALL), PdfBlock.Gap(6f)))) + entries))
    }

    private fun entryFor(spec: ModuleSpec, item: ModuleItem): PdfBlock.Keep {
        val v = item.values
        return PdfBlocks.entry(titleOf(spec, v), subtitleOf(spec.type, v), rowsOf(spec, v))
    }

    private fun choice(def: FieldDef?, key: String): String =
        def?.choices?.firstOrNull { it.key == key }?.let { s(it.labelRes) } ?: key

    private fun titleOf(spec: ModuleSpec, v: Map<String, String>): String {
        fun g(k: String) = v[k].orEmpty().trim()
        val title = when (spec.type) {
            ModuleType.FLIGHT -> join(" ", g("airline"), g("flightNumber"))
            ModuleType.TRANSPORT -> join(" ", g("company"), g("model")).ifEmpty { choice(spec.field("type"), g("type")) }
            ModuleType.TRAVEL_DOC -> g("title").ifEmpty { choice(spec.field("docType"), g("docType")) }
            ModuleType.EXPENSE -> g("concept")
            ModuleType.DESTINATION -> join(", ", g("city"), g("country"))
            else -> g("name")
        }
        return title.ifBlank { s(spec.titleRes) }
    }

    private fun subtitleOf(type: ModuleType, v: Map<String, String>): String {
        fun g(k: String) = v[k].orEmpty().trim()
        return when (type) {
            ModuleType.FLIGHT -> {
                val flight = runCatching { FlightCodec.build(0, 0, v) }.getOrNull()
                val minutes = flight?.takeIf { FlightCodec.hasArrival(it) }?.let { FlightCodec.durationMinutes(it) }?.takeIf { it > 0 }
                join(" · ", join(" → ", Airports.displayName(g("departureAirport")), Airports.displayName(g("arrivalAirport"))),
                    minutes?.let { Durations.text(it) })
            }
            ModuleType.TRANSPORT -> join(" → ", g("originPlace"), g("destinationPlace"))
            ModuleType.LODGING -> join(", ", g("address"), g("city"), g("country"))
            ModuleType.ACTIVITY -> g("location")
            ModuleType.RESTAURANT -> g("address")
            else -> ""
        }
    }

    /** Campos que ya salen en el título o el resumen del elemento: no se repiten como fila. */
    private fun skipKeys(type: ModuleType): Set<String> = when (type) {
        ModuleType.FLIGHT -> setOf("airline", "flightNumber", "departureAirport", "arrivalAirport")
        ModuleType.TRANSPORT -> setOf("company", "model", "originPlace", "destinationPlace")
        ModuleType.LODGING -> setOf("name", "address", "city", "country")
        ModuleType.ACTIVITY -> setOf("name", "location")
        ModuleType.RESTAURANT -> setOf("name", "address")
        ModuleType.TRAVEL_DOC -> setOf("title")
        ModuleType.DESTINATION -> setOf("city", "country")
        ModuleType.EXPENSE -> setOf("concept")
    }

    private fun rowsOf(spec: ModuleSpec, v: Map<String, String>): List<Pair<String, String>> {
        val skip = skipKeys(spec.type)
        val defs = spec.sections.flatMap { it.keys }.distinct().mapNotNull { spec.field(it) }
            .filter { it.key !in skip && it.type != FieldType.ZONE && it.type != FieldType.CURRENCY }
        val rows = ArrayList<Pair<String, String>>()
        var i = 0
        while (i < defs.size) {
            val d = defs[i]
            val next = defs.getOrNull(i + 1)
            if (d.type == FieldType.DATE && next != null && next.type == FieldType.TIME) {
                // Fecha y hora van en la misma fila: "12 jun 2027  11:45".
                rows += s(d.labelRes) to join("  ", valueOf(d, v), valueOf(next, v))
                i += 2
            } else {
                rows += s(d.labelRes) to valueOf(d, v)
                i += 1
            }
        }
        // Actividad con hora de inicio y duración: a qué hora termina, calculada por la app.
        if (spec.type == ModuleType.ACTIVITY) activityEndClock(f, v)?.let { rows += s(R.string.pdf_activity_end) to it }
        return rows
    }

    private fun valueOf(def: FieldDef, v: Map<String, String>): String {
        val raw = v[def.key].orEmpty().trim()
        if (raw.isEmpty()) return ""
        return when {
            def.sensitive -> DocWarnings.mask(raw)
            def.type == FieldType.DATE -> runCatching { f.date(LocalDate.parse(raw)) }.getOrDefault(raw)
            def.type == FieldType.TIME -> runCatching { f.time(LocalTime.parse(raw)) }.getOrDefault(raw)
            def.type == FieldType.AIRPORT -> Airports.displayName(raw)
            def.type == FieldType.CHOICE -> choice(def, raw)
            def.type == FieldType.MONEY -> formatMoneyValue(raw, v["currency"]) ?: raw
            def.type == FieldType.DURATION -> Durations.parse(raw)?.let { Durations.text(it.toLong()) } ?: raw
            else -> raw
        }
    }

    // ---- Gastos, notas y archivos ---------------------------------------------------------------

    private fun categoryName(c: CostCategory): String = s(
        when (c) {
            CostCategory.FLIGHTS -> R.string.cost_flights
            CostCategory.LODGING -> R.string.cost_lodging
            CostCategory.ACTIVITIES -> R.string.cost_activities
            CostCategory.TRANSPORT -> R.string.cost_transport
            CostCategory.RESTAURANTS -> R.string.cost_restaurants
            CostCategory.OTHER -> R.string.cost_other
        },
    )

    private fun amounts(byCurrency: Map<String, Long>): String =
        byCurrency.entries.joinToString(" · ") { (cur, minor) -> Money.format(minor, cur) }

    private fun MutableList<PdfBlock>.costs(b: TripBundle) {
        val lines = CostBreakdown.of(b)
        if (lines.isEmpty()) return
        val rows = ArrayList<Pair<String, String>>()
        lines.forEach { rows += categoryName(it.category) to amounts(it.byCurrency) }
        if (lines.size > 1) rows += s(R.string.cost_total) to amounts(CostBreakdown.totals(lines))
        addAll(PdfBlocks.section(s(R.string.mod_budget), listOf(PdfBlocks.entry("", "", rows))))
    }

    private fun kindName(k: NoteKind): String = s(
        when (k) {
            NoteKind.NOTE -> R.string.nk_note
            NoteKind.PACKING -> R.string.nk_packing
            NoteKind.TODO -> R.string.nk_todo
            NoteKind.REMINDER -> R.string.nk_reminder
            NoteKind.SHOPPING -> R.string.nk_shopping
            NoteKind.INFO -> R.string.nk_info
        },
    )

    private fun MutableList<PdfBlock>.notes(b: TripBundle) {
        val entries = NoteKind.entries.mapNotNull { kind ->
            val list = b.notes.filter { NoteKind.fromName(it.kind) == kind }
            if (list.isEmpty()) return@mapNotNull null
            val blocks = ArrayList<PdfBlock>()
            blocks += PdfBlock.Paragraph(kindName(kind), PdfStyle.ENTRY_TITLE)
            list.forEach { n ->
                val mark = if (kind.checkable) (if (n.done) "[x] " else "[ ] ") else ""
                val due = n.dueDate?.let { d -> " (" + f.date(d) + (n.dueTime?.let { t -> " " + f.time(t) } ?: "") + ")" }.orEmpty()
                blocks += PdfBlock.Paragraph(mark + n.text + due, PdfStyle.BODY)
                if (n.body.isNotBlank()) blocks += PdfBlock.Paragraph(n.body.trim(), PdfStyle.SMALL, indent = 12f)
            }
            blocks += PdfBlock.Gap(8f)
            PdfBlock.Keep(blocks)
        }
        addAll(PdfBlocks.section(s(R.string.mod_notes), entries))
    }

    private fun MutableList<PdfBlock>.files(b: TripBundle) {
        val groups = FileGroups.group(b.attachments, b.transports.associate { it.id to it.type })
        val shown = groups.filter { it.group != FileGroup.DOCUMENTS }.flatMap { it.files }
        if (shown.isEmpty()) return
        add(PdfBlock.PageBreak)
        add(PdfBlock.Paragraph(s(R.string.mod_files), PdfStyle.HEADING))
        add(PdfBlock.Rule)
        if (groups.any { it.group == FileGroup.DOCUMENTS }) add(PdfBlock.Paragraph(s(R.string.pdf_files_private), PdfStyle.SMALL))
        if (!includeFiles) {
            add(PdfBlock.Paragraph(s(R.string.pdf_files_no_memory), PdfStyle.BODY))
            return
        }
        add(PdfBlock.Gap(6f))

        var budget = TripPdf.MAX_ATTACHMENT_PAGES
        var omittedPages = 0
        var first = true
        val notShown = ArrayList<String>()
        for (att in shown) {
            val title = attachmentTitle(att)
            val owner = ownerLine(b, att)
            when (val plan = pages.plan(att, budget)) {
                AttachmentPages.Plan.Unsupported -> notShown += join(" · ", title, owner) + " — " + s(R.string.pdf_file_unsupported)
                AttachmentPages.Plan.Failed -> notShown += join(" · ", title, owner) + " — " + s(R.string.pdf_file_failed)
                is AttachmentPages.Plan.Pages -> {
                    budget -= plan.pages.size
                    omittedPages += plan.total - plan.pages.size
                    plan.pages.forEachIndexed { i, ref ->
                        if (!first) add(PdfBlock.PageBreak)
                        first = false
                        add(PdfBlock.Paragraph(title, PdfStyle.ENTRY_TITLE))
                        if (owner.isNotBlank()) add(PdfBlock.Paragraph(owner, PdfStyle.BODY))
                        if (plan.total > 1) add(PdfBlock.Paragraph(context.getString(R.string.pdf_file_page, i + 1, plan.total), PdfStyle.SMALL))
                        add(PdfBlock.Gap(6f))
                        add(PdfBlock.Picture(ref.key, ref.aspect))
                    }
                }
            }
        }
        if (notShown.isNotEmpty() || omittedPages > 0) {
            val notes = ArrayList<PdfBlock>()
            notes += PdfBlock.Gap(10f)
            notes += PdfBlock.Paragraph(s(R.string.pdf_files_not_shown), PdfStyle.ENTRY_TITLE)
            notShown.forEach { notes += PdfBlock.Paragraph("• $it", PdfStyle.BODY, indent = 8f) }
            if (omittedPages > 0) {
                notes += PdfBlock.Paragraph(context.resources.getQuantityString(R.plurals.pdf_files_omitted, omittedPages, omittedPages), PdfStyle.BODY, indent = 8f)
            }
            add(PdfBlock.Keep(notes))
        }
    }

    /** Qué es el archivo ("Reserva", "Tarjeta de embarque"...); sin categoría, qué clase de archivo es. Nunca su nombre (xxx.pdf). */
    private fun attachmentTitle(att: AttachmentEntity): String = when (AttachmentCategory.fromName(att.category)) {
        AttachmentCategory.BOARDING_PASS -> s(R.string.cat_boarding_pass)
        AttachmentCategory.TICKET -> s(R.string.cat_ticket)
        AttachmentCategory.RESERVATION -> s(R.string.cat_reservation)
        AttachmentCategory.CONFIRMATION -> s(R.string.cat_confirmation)
        AttachmentCategory.INSURANCE -> s(R.string.cat_insurance)
        AttachmentCategory.VISA -> s(R.string.cat_visa)
        AttachmentCategory.CONTRACT -> s(R.string.cat_contract)
        AttachmentCategory.PHOTO -> s(R.string.cat_photo)
        AttachmentCategory.OTHER -> when {
            att.mimeType == "application/pdf" -> s(R.string.file_kind_pdf)
            att.mimeType.startsWith("image/") -> s(R.string.file_kind_image)
            else -> s(R.string.file_kind_other)
        }
    }

    /** De qué reserva es el archivo: "Alojamiento · Hotel Roma", "Vuelo · IB 3250"... Vacío si no está asociado a ninguna. */
    private fun ownerLine(b: TripBundle, att: AttachmentEntity): String {
        val type = att.ownerType?.let { ModuleType.fromName(it) } ?: return ""
        val id = att.ownerId ?: return ""
        val v = ModuleItems.of(type, b).firstOrNull { it.id == id }?.values ?: return ""
        val spec = ModuleSpecs.of(type)
        return when (type) {
            ModuleType.FLIGHT -> join(" · ", s(R.string.pdf_owner_flight), titleOf(spec, v))
            ModuleType.LODGING -> join(" · ", s(R.string.pdf_owner_lodging), titleOf(spec, v))
            ModuleType.ACTIVITY -> join(" · ", s(R.string.pdf_owner_activity), titleOf(spec, v))
            ModuleType.RESTAURANT -> join(" · ", s(R.string.pdf_owner_restaurant), titleOf(spec, v))
            ModuleType.TRANSPORT -> join(
                " · ", choice(spec.field("type"), v["type"].orEmpty()),
                join(" ", v["company"], v["model"]).ifBlank { subtitleOf(type, v) },
            )
            else -> titleOf(spec, v)
        }
    }
}
