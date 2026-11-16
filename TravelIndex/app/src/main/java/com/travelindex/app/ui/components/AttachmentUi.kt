package com.travelindex.app.ui.components

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.travelindex.app.R
import com.travelindex.app.data.FileActions
import com.travelindex.app.domain.AttachmentCategory
import com.travelindex.app.data.FileStorage
import com.travelindex.app.data.db.AttachmentEntity
import java.io.File
import java.util.Locale

fun humanSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1048576.0)
}

/** Qué es el archivo, sin enseñar su nombre (xxx.pdf): "Documento PDF", "Imagen" o "Archivo". */
@Composable
fun fileKindLabel(mime: String): String = stringResource(
    when {
        mime == "application/pdf" -> R.string.file_kind_pdf
        mime.startsWith("image/") -> R.string.file_kind_image
        else -> R.string.file_kind_other
    }
)

/** Nombre visible de cada categoría de archivo. */
@Composable
fun categoryLabel(c: AttachmentCategory): String = stringResource(
    when (c) {
        AttachmentCategory.BOARDING_PASS -> R.string.cat_boarding_pass
        AttachmentCategory.TICKET -> R.string.cat_ticket
        AttachmentCategory.RESERVATION -> R.string.cat_reservation
        AttachmentCategory.CONFIRMATION -> R.string.cat_confirmation
        AttachmentCategory.INSURANCE -> R.string.cat_insurance
        AttachmentCategory.VISA -> R.string.cat_visa
        AttachmentCategory.CONTRACT -> R.string.cat_contract
        AttachmentCategory.PHOTO -> R.string.cat_photo
        AttachmentCategory.OTHER -> R.string.cat_other
    }
)

fun iconForMime(mime: String): ImageVector = when {
    mime.startsWith("image/") -> Icons.Outlined.Image
    mime == "application/pdf" -> Icons.Outlined.PictureAsPdf
    else -> Icons.Outlined.InsertDriveFile
}

fun attachmentFile(context: Context, att: AttachmentEntity): File =
    File(File(context.filesDir, FileStorage.DIR), att.relativePath)

/** Abre el archivo con otra app; si no hay ninguna que pueda, avisa. */
fun openAttachment(context: Context, att: AttachmentEntity) {
    if (!FileActions.open(context, attachmentFile(context, att), att.mimeType)) {
        Toast.makeText(context, context.getString(R.string.error_no_app_to_open), Toast.LENGTH_LONG).show()
    }
}

fun shareAttachment(context: Context, att: AttachmentEntity) {
    if (!FileActions.share(context, attachmentFile(context, att), att.mimeType)) {
        Toast.makeText(context, context.getString(R.string.error_share), Toast.LENGTH_LONG).show()
    }
}

/** Un archivo ya guardado: vista previa visible (imagen o PDF) y debajo abrir (pulsar), compartir y eliminar. */
@Composable
fun AttachmentRow(att: AttachmentEntity, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilePreview(mime = att.mimeType, file = attachmentFile(ctx, att), onClick = { openAttachment(ctx, att) })
        Row(
            Modifier.fillMaxWidth().clickable { openAttachment(ctx, att) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(iconForMime(att.mimeType), contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                // Se rotula por su categoría ("Billete", "Reserva"...), nunca por el nombre del archivo (xxx.pdf).
                Text(categoryLabel(AttachmentCategory.fromName(att.category)),
                    style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOf(fileKindLabel(att.mimeType), humanSize(att.sizeBytes)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { shareAttachment(ctx, att) }) {
                Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.action_share))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        }
    }
}

/** Un archivo elegido pero aún no guardado (se guarda al pulsar Guardar), también con vista previa. */
@Composable
fun PendingFileRow(onRemove: () -> Unit, modifier: Modifier = Modifier, uri: Uri? = null) {
    val ctx = LocalContext.current
    val mime = remember(uri) { uri?.let { ctx.contentResolver.getType(it) }.orEmpty() }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (uri != null) FilePreview(mime = mime, uri = uri)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(iconForMime(mime), contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Column(Modifier.weight(1f)) {
                Text(fileKindLabel(mime), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.attach_pending), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete)) }
        }
    }
}
