package com.travelindex.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.travelindex.app.MainActivity
import com.travelindex.app.R

/**
 * Accesos directos al mantener pulsado el icono de la app. Son dinámicos (se registran al arrancar) y no
 * estáticos porque el paquete de debug lleva sufijo ".debug": un XML estático apuntaría al paquete equivocado.
 */
object AppShortcuts {
    const val SCHEME = "travelindex"
    const val DEST_NEW_TRIP = "new"
    const val DEST_SEARCH = "search"

    fun install(context: Context) {
        runCatching {
            fun shortcut(id: String, labelRes: Int, iconRes: Int, dest: String) =
                ShortcutInfoCompat.Builder(context, id)
                    .setShortLabel(context.getString(labelRes))
                    .setIcon(IconCompat.createWithResource(context, iconRes))
                    .setIntent(
                        Intent(context, MainActivity::class.java)
                            .setAction(Intent.ACTION_VIEW)
                            .setData(Uri.parse("$SCHEME://$dest")),
                    )
                    .build()
            ShortcutManagerCompat.setDynamicShortcuts(
                context,
                listOf(
                    shortcut("new_trip", R.string.action_new_trip, R.drawable.ic_shortcut_new, DEST_NEW_TRIP),
                    shortcut("search", R.string.action_search, R.drawable.ic_shortcut_search, DEST_SEARCH),
                ),
            )
        }
    }
}
