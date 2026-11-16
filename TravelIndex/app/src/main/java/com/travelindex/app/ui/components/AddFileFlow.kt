package com.travelindex.app.ui.components

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.travelindex.app.R
import com.travelindex.app.data.CameraCapture

/**
 * Cómo se sube un documento: elegirlo del dispositivo (PDF, fotos, capturas...) o escanearlo haciendo una foto con la cámara.
 * Devuelve la función que hay que llamar al pulsar "Añadir archivo": pregunta cuál de las dos y, al terminar,
 * entrega los archivos elegidos (o la foto) en [onPicked].
 */
@Composable
fun rememberAddFileFlow(onPicked: (List<Uri>) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val current by rememberUpdatedState(onPicked)
    var chooser by rememberSaveable { mutableStateOf(false) }
    // La foto se escribe en un archivo temporal; si Android recrea la pantalla mientras se abre la cámara, no se pierde.
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val noCamera = stringResource(R.string.error_no_camera)

    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) current(uris)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val pending = cameraUri?.let { Uri.parse(it) }
        cameraUri = null
        if (pending != null) {
            if (taken) current(listOf(pending)) else CameraCapture.deleteIfCapture(ctx, pending)
        }
    }

    fun openCamera() {
        val uri = runCatching { CameraCapture.newUri(ctx) }.getOrNull()
        if (uri == null) { Toast.makeText(ctx, noCamera, Toast.LENGTH_LONG).show(); return }
        cameraUri = uri.toString()
        try {
            camera.launch(uri)
        } catch (e: ActivityNotFoundException) {
            cameraUri = null
            CameraCapture.deleteIfCapture(ctx, uri)
            Toast.makeText(ctx, noCamera, Toast.LENGTH_LONG).show()
        }
    }

    if (chooser) AddFileDialog(
        onDismiss = { chooser = false },
        onDevice = { chooser = false; documents.launch(arrayOf("*/*")) },
        onCamera = { chooser = false; openCamera() },
    )
    return remember { { chooser = true } }
}

@Composable
private fun AddFileDialog(onDismiss: () -> Unit, onDevice: () -> Unit, onCamera: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_file_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ChoiceRow(Icons.Outlined.FolderOpen, stringResource(R.string.add_file_device), stringResource(R.string.add_file_device_hint), onDevice)
                ChoiceRow(Icons.Outlined.PhotoCamera, stringResource(R.string.add_file_scan), stringResource(R.string.add_file_scan_hint), onCamera)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ChoiceRow(icon: ImageVector, title: String, hint: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
