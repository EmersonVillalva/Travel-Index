package com.travelindex.app.domain

import com.travelindex.app.domain.forms.FieldError
import java.time.LocalDate

/** Tipos de nota/lista de un viaje. [name] es lo que se guarda en la base de datos. */
enum class NoteKind {
    NOTE, PACKING, TODO, REMINDER, SHOPPING, INFO;

    /** Tiene casilla de "hecho". */
    val checkable: Boolean get() = this == PACKING || this == TODO || this == REMINDER || this == SHOPPING

    /** El contenido principal es el texto largo (notas e información). */
    val usesBody: Boolean get() = this == NOTE || this == INFO

    /** Admite fecha/hora (y por tanto genera recordatorio y aparece en el itinerario). */
    val usesDue: Boolean get() = this == TODO || this == REMINDER

    companion object {
        fun fromName(n: String?): NoteKind = entries.firstOrNull { it.name == n } ?: NOTE
    }
}

object NoteValidator {
    const val MAX_TEXT = 200
    const val MAX_BODY = 4000

    /** Claves de error: "text" y "dueDate". */
    fun validate(kind: NoteKind, text: String, dueDate: LocalDate?): Map<String, FieldError> {
        val errors = linkedMapOf<String, FieldError>()
        if (text.isBlank()) errors["text"] = FieldError.REQUIRED
        if (kind == NoteKind.REMINDER && dueDate == null) errors["dueDate"] = FieldError.REQUIRED
        return errors
    }
}
