package com.travelindex.app.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Abrir, compartir y lanzar otras apps (mapas, navegador, teléfono). Todas devuelven false si no hay app capaz. */
object FileActions {
    private fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent); true
    } catch (e: ActivityNotFoundException) { false } catch (e: SecurityException) { false }

    fun open(context: Context, file: File, mime: String): Boolean {
        if (!file.exists()) return false
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(context, file), mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return start(context, i)
    }

    fun share(context: Context, file: File, mime: String, text: String? = null): Boolean {
        if (!file.exists()) return false
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uriFor(context, file))
            if (text != null) putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return start(context, Intent.createChooser(send, null))
    }

    /** Abre una búsqueda en Google Maps (o en la app de mapas instalada). No necesita clave de API. */
    fun openInMaps(context: Context, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return false
        if (start(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q))))) return true
        return start(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(q))))
    }

    /** Ruta hasta un lugar en Google Maps (o la app de mapas que haya). Sin clave de API. */
    fun directions(context: Context, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return false
        return start(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(q))))
    }

    fun openUrl(context: Context, url: String): Boolean {
        val u = url.trim()
        if (u.isEmpty()) return false
        val full = if (u.startsWith("http://") || u.startsWith("https://")) u else "https://$u"
        return start(context, Intent(Intent.ACTION_VIEW, Uri.parse(full)))
    }

    fun dial(context: Context, phone: String): Boolean {
        val p = phone.trim()
        if (p.isEmpty()) return false
        return start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(p))))
    }
}
