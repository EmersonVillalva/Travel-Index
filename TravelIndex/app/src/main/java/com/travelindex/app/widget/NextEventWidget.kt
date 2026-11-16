package com.travelindex.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.travelindex.app.MainActivity
import com.travelindex.app.R
import com.travelindex.app.data.repo.TripRepository
import com.travelindex.app.domain.TripBundle
import com.travelindex.app.domain.WidgetPlanner
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Widget «Próximo evento»: una tarjeta en la pantalla de inicio con lo siguiente del viaje (vuelo, check-in,
 * actividad…). Se refresca al cambiar los datos (lo empuja TravelIndexApp), cada media hora por el sistema
 * y al tocarlo abre la app. Clásico RemoteViews: sin dependencias nuevas.
 */
@AndroidEntryPoint
class NextEventWidget : AppWidgetProvider() {
    @Inject lateinit var trips: TripRepository

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val bundles = withTimeoutOrNull(5_000) { trips.bundles.filterNotNull().first() } ?: emptyList()
                val views = render(context, bundles)
                ids.forEach { manager.updateAppWidget(it, views) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Refresco empujado desde la app cuando cambian los datos; sin widgets colocados no hace nada. */
        fun updateAll(context: Context, bundles: List<TripBundle>) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, NextEventWidget::class.java))
            if (ids.isEmpty()) return
            val views = render(context, bundles)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun render(context: Context, bundles: List<TripBundle>): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_next_event)
            val next = WidgetPlanner.next(bundles, System.currentTimeMillis(), ZoneId.systemDefault())
            if (next == null) {
                rv.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
                rv.setTextViewText(R.id.widget_line, context.getString(R.string.widget_empty))
            } else {
                val at = Instant.ofEpochMilli(next.startMs).atZone(ZoneId.of(next.zoneId))
                val date = at.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
                val time = if (next.timeKnown) " · " + at.format(DateTimeFormatter.ofPattern("HH:mm")) else ""
                rv.setTextViewText(R.id.widget_title, next.tripName)
                rv.setTextViewText(
                    R.id.widget_line,
                    if (next.isTripStart) context.getString(R.string.widget_trip_starts, date)
                    else listOf(date + time, next.title).filter { it.isNotBlank() }.joinToString(" · "),
                )
            }
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            rv.setOnClickPendingIntent(R.id.widget_root, open)
            return rv
        }
    }
}
