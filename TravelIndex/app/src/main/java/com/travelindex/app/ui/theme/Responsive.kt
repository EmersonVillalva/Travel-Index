package com.travelindex.app.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.travelindex.app.domain.DateFormatOption
import com.travelindex.app.domain.Formats

/** Compact: móvil en vertical · Medium: móvil horizontal / tablet pequeña · Expanded: tablet / escritorio. */
enum class WidthClass { Compact, Medium, Expanded }

fun widthClassFor(widthDp: Int): WidthClass = when {
    widthDp < 600 -> WidthClass.Compact
    widthDp < 840 -> WidthClass.Medium
    else -> WidthClass.Expanded
}

val LocalWidthClass = staticCompositionLocalOf { WidthClass.Compact }

/** Formateadores de fecha/hora según las preferencias del usuario. */
val LocalFormats = staticCompositionLocalOf { Formats(DateFormatOption.SYSTEM) }

/** Limita el ancho del contenido y lo centra: formularios y listas no se estiran en tablets. */
@Composable
fun CenteredContent(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 720.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxSize()) { content() }
    }
}
