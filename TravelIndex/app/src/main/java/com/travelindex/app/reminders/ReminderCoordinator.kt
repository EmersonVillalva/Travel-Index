package com.travelindex.app.reminders

import com.travelindex.app.data.SettingsRepository
import com.travelindex.app.data.repo.TripRepository
import com.travelindex.app.domain.ReminderPlanner
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Mantiene las alarmas sincronizadas con los datos y los ajustes: cualquier cambio replanifica. */
@Singleton
class ReminderCoordinator @Inject constructor(
    private val repo: TripRepository,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
) {
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(repo.bundles.filterNotNull(), settings.settings) { bundles, s ->
                ReminderPlanner.plan(bundles, s, System.currentTimeMillis())
            }.debounce(750).collect { scheduler.sync(it) }
        }
    }

    /** Replanifica una vez (arranque del dispositivo / actualización de la app). */
    suspend fun syncNow() {
        val bundles = repo.bundles.filterNotNull().first()
        val s = settings.settings.first()
        scheduler.sync(ReminderPlanner.plan(bundles, s, System.currentTimeMillis()))
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun coordinator(): ReminderCoordinator
}
