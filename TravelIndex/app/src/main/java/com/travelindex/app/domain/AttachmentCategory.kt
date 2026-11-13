package com.travelindex.app.domain

enum class AttachmentCategory {
    BOARDING_PASS, TICKET, RESERVATION, CONFIRMATION, INSURANCE, VISA, CONTRACT, PHOTO, OTHER;

    companion object {
        fun fromName(n: String?): AttachmentCategory = entries.firstOrNull { it.name == n } ?: OTHER

        /** Categoría sugerida al adjuntar un archivo a un elemento concreto. */
        fun defaultFor(ownerType: String?, mime: String): AttachmentCategory = when (ownerType) {
            "FLIGHT" -> BOARDING_PASS
            "LODGING", "TRANSPORT", "RESTAURANT" -> RESERVATION
            "ACTIVITY" -> TICKET
            "TRAVEL_DOC" -> VISA
            else -> if (mime.startsWith("image/")) PHOTO else OTHER
        }
    }
}
