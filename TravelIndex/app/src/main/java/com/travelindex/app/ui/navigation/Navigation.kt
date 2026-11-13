package com.travelindex.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.travelindex.app.R
import com.travelindex.app.domain.ItineraryEvent
import com.travelindex.app.domain.ModuleType
import com.travelindex.app.domain.moduleType
import com.travelindex.app.ui.calendar.CalendarScreen
import com.travelindex.app.ui.files.FilesScreen
import com.travelindex.app.ui.home.HomeScreen
import com.travelindex.app.ui.itinerary.ItineraryScreen
import com.travelindex.app.ui.modules.ModuleDetailScreen
import com.travelindex.app.ui.modules.ModuleFormScreen
import com.travelindex.app.ui.modules.ModuleListScreen
import com.travelindex.app.ui.places.PlacesScreen
import com.travelindex.app.ui.profile.AboutScreen
import com.travelindex.app.ui.profile.PrivacyPolicyScreen
import com.travelindex.app.ui.profile.ProfileScreen
import com.travelindex.app.ui.share.ShareImportHost
import com.travelindex.app.ui.theme.LocalWidthClass
import com.travelindex.app.ui.theme.WidthClass
import com.travelindex.app.ui.trips.CreateTripScreen
import com.travelindex.app.ui.trips.TripActions
import com.travelindex.app.ui.trips.TripDetailScreen
import com.travelindex.app.ui.trips.TripsScreen

enum class TopLevel(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    Home("home", R.string.nav_home, Icons.Outlined.Home),
    Trips("trips", R.string.nav_trips, Icons.Outlined.Luggage),
    Calendar("calendar", R.string.nav_calendar, Icons.Outlined.CalendarMonth),
    Profile("profile", R.string.nav_profile, Icons.Outlined.Person),
}

/** Todas las rutas de la app. Los argumentos tienen el mismo nombre que leen los ViewModels (tripId, module, itemId, add). */
object Routes {
    const val NEW_TRIP = "newtrip"
    const val PRIVACY = "profile/privacy"
    const val ABOUT = "profile/about"

    const val TRIP_PATTERN = "trip/{tripId}"
    const val TRIP_EDIT_PATTERN = "trip/{tripId}/edit"
    const val ITINERARY_PATTERN = "trip/{tripId}/itinerary"
    const val FILES_PATTERN = "trip/{tripId}/files"
    const val PLACES_PATTERN = "trip/{tripId}/places"
    const val LIST_PATTERN = "trip/{tripId}/m/{module}"
    const val FORM_PATTERN = "trip/{tripId}/m/{module}/form?itemId={itemId}"
    const val ITEM_PATTERN = "trip/{tripId}/m/{module}/item/{itemId}"

    fun trip(id: Long) = "trip/$id"
    fun tripEdit(id: Long) = "trip/$id/edit"
    fun itinerary(id: Long) = "trip/$id/itinerary"
    fun files(id: Long) = "trip/$id/files"
    fun places(id: Long) = "trip/$id/places"
    fun list(id: Long, type: ModuleType) = "trip/$id/m/${type.name}"
    fun form(id: Long, type: ModuleType, itemId: Long = 0) = "trip/$id/m/${type.name}/form?itemId=$itemId"
    fun item(id: Long, type: ModuleType, itemId: Long) = "trip/$id/m/${type.name}/item/$itemId"
}

private fun NavHostController.openTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavBackStackEntry.tripId(): Long = arguments?.getLong("tripId") ?: 0L
private fun NavBackStackEntry.module(): ModuleType = ModuleType.fromName(arguments?.getString("module")) ?: ModuleType.FLIGHT

private val longArg = { name: String -> navArgument(name) { type = NavType.LongType } }

@Composable
fun TravelIndexNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val current = TopLevel.entries.firstOrNull { tl -> destination?.hierarchy?.any { it.route == tl.route } == true }
    val width = LocalWidthClass.current
    val showRail = current != null && width != WidthClass.Compact
    val showBar = current != null && width == WidthClass.Compact

    fun openEvent(tripId: Long, e: ItineraryEvent) {
        val type = e.type.moduleType()
        if (type == null) nav.navigate(Routes.itinerary(tripId)) else nav.navigate(Routes.item(tripId, type, e.sourceId))
    }

    Row {
        if (showRail) {
            NavigationRail {
                TopLevel.entries.forEach { tl ->
                    NavigationRailItem(
                        selected = tl == current, onClick = { nav.openTopLevel(tl.route) },
                        icon = { Icon(tl.icon, contentDescription = null) }, label = { Text(stringResource(tl.label)) },
                    )
                }
            }
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showBar) NavigationBar {
                    TopLevel.entries.forEach { tl ->
                        NavigationBarItem(
                            selected = tl == current, onClick = { nav.openTopLevel(tl.route) },
                            icon = { Icon(tl.icon, contentDescription = null) }, label = { Text(stringResource(tl.label)) },
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = nav, startDestination = TopLevel.Home.route,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding).let {
                    if (showRail) it.consumeWindowInsets(WindowInsets.systemBars.only(WindowInsetsSides.Start)) else it
                },
                enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 14 } },
                exitTransition = { fadeOut(tween(140)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { it / 14 } },
            ) {
                // ---- Pestañas ----
                composable(TopLevel.Home.route) {
                    HomeScreen(
                        onOpenTrip = { nav.navigate(Routes.trip(it)) },
                        onNewTrip = { nav.navigate(Routes.NEW_TRIP) },
                        onOpenTrips = { nav.openTopLevel(TopLevel.Trips.route) },
                        onOpenEvent = { openEvent(it.trip.id, it.event) },
                        onOpenAttention = { nav.navigate(Routes.item(it.tripId, it.module, it.itemId)) },
                    )
                }
                composable(TopLevel.Trips.route) {
                    TripsScreen(onOpenTrip = { nav.navigate(Routes.trip(it)) }, onNewTrip = { nav.navigate(Routes.NEW_TRIP) })
                }
                composable(TopLevel.Calendar.route) {
                    CalendarScreen(
                        onOpenTrip = { nav.navigate(Routes.trip(it)) },
                        onOpenEvent = { openEvent(it.trip.id, it.event) },
                    )
                }
                composable(TopLevel.Profile.route) {
                    ProfileScreen(
                        onOpenPrivacy = { nav.navigate(Routes.PRIVACY) },
                        onOpenAbout = { nav.navigate(Routes.ABOUT) },
                    )
                }
                composable(Routes.PRIVACY) { PrivacyPolicyScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.ABOUT) { AboutScreen(onBack = { nav.popBackStack() }) }

                // ---- Viaje: crear, editar y panel ----
                composable(Routes.NEW_TRIP) {
                    CreateTripScreen(
                        onClose = { nav.popBackStack() },
                        onSaved = { id, _ ->
                            nav.navigate(Routes.trip(id)) { popUpTo(Routes.NEW_TRIP) { inclusive = true } }
                        },
                    )
                }
                composable(Routes.TRIP_EDIT_PATTERN, arguments = listOf(longArg("tripId"))) {
                    CreateTripScreen(onClose = { nav.popBackStack() }, onSaved = { _, _ -> nav.popBackStack() })
                }
                composable(Routes.TRIP_PATTERN, arguments = listOf(longArg("tripId"))) { entry ->
                    val id = entry.tripId()
                    TripDetailScreen(
                        TripActions(
                            onBack = { nav.popBackStack() },
                            onEdit = { nav.navigate(Routes.tripEdit(id)) },
                            openItinerary = { nav.navigate(Routes.itinerary(id)) },
                            openList = { nav.navigate(Routes.list(id, it)) },
                            addItem = { nav.navigate(Routes.form(id, it)) },
                            openFiles = { nav.navigate(Routes.files(id)) },
                            openPlaces = { nav.navigate(Routes.places(id)) },
                            openEvent = { openEvent(id, it) },
                        )
                    )
                }

                // ---- Pantallas del viaje ----
                composable(Routes.ITINERARY_PATTERN, arguments = listOf(longArg("tripId"))) { entry ->
                    val id = entry.tripId()
                    ItineraryScreen(onBack = { nav.popBackStack() }, onOpenEvent = { openEvent(id, it) })
                }
                composable(Routes.FILES_PATTERN, arguments = listOf(longArg("tripId"))) { FilesScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.PLACES_PATTERN, arguments = listOf(longArg("tripId"))) { entry ->
                    val id = entry.tripId()
                    PlacesScreen(onBack = { nav.popBackStack() }, onOpenItem = { itemId -> nav.navigate(Routes.item(id, ModuleType.FLIGHT, itemId)) })
                }

                // ---- Módulos (vuelos, alojamientos...) ----
                val moduleArgs = listOf(longArg("tripId"), navArgument("module") { type = NavType.StringType })
                composable(Routes.LIST_PATTERN, arguments = moduleArgs) { entry ->
                    val id = entry.tripId()
                    val type = entry.module()
                    ModuleListScreen(
                        type = type, onBack = { nav.popBackStack() },
                        onOpen = { nav.navigate(Routes.item(id, type, it)) },
                        onAdd = { nav.navigate(Routes.form(id, type)) },
                    )
                }
                composable(
                    Routes.FORM_PATTERN,
                    arguments = moduleArgs + navArgument("itemId") { type = NavType.LongType; defaultValue = 0L },
                ) { ModuleFormScreen(onClose = { nav.popBackStack() }) }
                composable(
                    Routes.ITEM_PATTERN,
                    arguments = moduleArgs + longArg("itemId"),
                ) { entry ->
                    val id = entry.tripId()
                    val type = entry.module()
                    ModuleDetailScreen(onBack = { nav.popBackStack() }, onEdit = { nav.navigate(Routes.form(id, type, it)) })
                }
            }
        }
    }

    // Ventanas de "¿añadir este viaje?" cuando alguien te comparte uno.
    ShareImportHost(onOpenTrip = { nav.navigate(Routes.trip(it)) })
}
