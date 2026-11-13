package com.travelindex.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Tras reiniciar el móvil o actualizar la app, las alarmas se pierden: se vuelven a programar desde la base de datos. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java)
        CoroutineScope(Dispatchers.Default).launch {
            try { entry.coordinator().syncNow() } finally { pending.finish() }
        }
    }
}
