package com.travelindex.app.ui.profile

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.travelindex.app.R
import com.travelindex.app.data.FileActions

/** Apoyo voluntario al proyecto. Solo se ofrece en Perfil; nunca sale solo ni interrumpe al usuario. */
object Support {
    const val DONATION_URL = "https://www.paypal.me/EmersonVillalva258"
}

/** Fila discreta de Perfil → Información. Al tocarla se pide confirmación antes de salir a PayPal. */
@Composable
fun SupportRow(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.support_title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.support_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Explica que es opcional y que se abre en el navegador; no hace nada hasta que el usuario pulsa «Abrir PayPal». */
@Composable
fun SupportDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.FavoriteBorder, contentDescription = null) },
        title = { Text(stringResource(R.string.support_dialog_title)) },
        text = { Text(stringResource(R.string.support_dialog_body), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = {
                if (!FileActions.openUrl(ctx, Support.DONATION_URL)) {
                    Toast.makeText(ctx, ctx.getString(R.string.error_no_app), Toast.LENGTH_LONG).show()
                }
                onDismiss()
            }) { Text(stringResource(R.string.support_open)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.support_not_now)) } },
    )
}
