package com.travelindex.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.travelindex.app.data.AppShortcuts
import com.travelindex.app.data.ImportInbox
import com.travelindex.app.data.ShortcutInbox
import com.travelindex.app.domain.AppSettings
import com.travelindex.app.domain.Formats
import com.travelindex.app.domain.ThemeMode
import com.travelindex.app.ui.AppViewModel
import com.travelindex.app.ui.navigation.TravelIndexNavHost
import com.travelindex.app.ui.theme.LocalFormats
import com.travelindex.app.ui.theme.LocalWidthClass
import com.travelindex.app.ui.theme.TravelIndexTheme
import com.travelindex.app.ui.theme.widthClassFor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    @Inject lateinit var importInbox: ImportInbox
    @Inject lateinit var shortcutInbox: ShortcutInbox

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // El splash se queda hasta tener los ajustes (tema, formato de fecha): evita un parpadeo de tema equivocado.
        splash.setKeepOnScreenCondition { appViewModel.settings.value == null }

        // Un viaje que te han compartido: otra app nos entrega el archivo (abrir o compartir hacia Travel Index).
        if (savedInstanceState == null) handleIncoming(intent)

        // Opcional del usuario: impedir capturas de pantalla y miniaturas en recientes.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appViewModel.settings.filterNotNull().map { it.blockScreenshots }.distinctUntilChanged().collect { block ->
                    if (block) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        setContent {
            val loaded by appViewModel.settings.collectAsStateWithLifecycle()
            val settings = loaded ?: AppSettings()
            // Solo hay tema claro y oscuro; por defecto es oscuro (el valor antiguo "sistema" también se trata como oscuro).
            val dark = settings.themeMode != ThemeMode.LIGHT
            // Barras del sistema transparentes con iconos legibles según el tema elegido (no solo el del sistema).
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(
                        Color.argb(0xe6, 0xFF, 0xFF, 0xFF), Color.argb(0x80, 0x1b, 0x1b, 0x1b),
                    ) { dark },
                )
                onDispose { }
            }
            val widthDp = LocalConfiguration.current.screenWidthDp
            CompositionLocalProvider(
                LocalFormats provides Formats(settings.dateFormat),
                LocalWidthClass provides widthClassFor(widthDp),
            ) {
                TravelIndexTheme(darkTheme = dark) {
                    if (loaded != null) TravelIndexNavHost()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    /** Recoge el archivo de un viaje compartido (ACTION_VIEW o ACTION_SEND) y lo deja en el buzón de importación. */
    private fun handleIncoming(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent?.data
            Intent.ACTION_SEND ->
                if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            else -> null
        }
        if (uri != null) {
            // travelindex://nuevo|buscar son los accesos directos del icono; cualquier otra cosa es un viaje compartido.
            if (uri.scheme == AppShortcuts.SCHEME) shortcutInbox.offer(uri.host.orEmpty()) else importInbox.offer(uri)
            setIntent(Intent(this, MainActivity::class.java))   // para no procesarlo otra vez al recrear la actividad
        }
    }
}
