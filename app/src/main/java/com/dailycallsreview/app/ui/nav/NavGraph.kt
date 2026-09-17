package com.dailycallsreview.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dailycallsreview.app.DailyCallsReviewApplication
import com.dailycallsreview.app.ui.daydetail.DayDetailScreen
import com.dailycallsreview.app.ui.history.HistoryScreen
import com.dailycallsreview.app.ui.home.HomeScreen
import com.dailycallsreview.app.ui.rangedetail.RangeDetailScreen
import com.dailycallsreview.app.ui.settings.SettingsScreen
import com.dailycallsreview.app.ui.team.TeamSetupScreen
import java.time.LocalDate

object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val TEAM_SETUP = "team_setup"
    const val SETTINGS = "settings"
    const val DAY_DETAIL = "day_detail/{date}"
    const val RANGE_DETAIL = "range_detail/{start}/{end}"

    fun dayDetail(date: LocalDate) = "day_detail/$date"
    fun rangeDetail(start: LocalDate, end: LocalDate) = "range_detail/$start/$end"
}

@Composable
fun AppNavGraph(app: DailyCallsReviewApplication, navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                app = app,
                onOpenHistory = { navController.navigate(Routes.HISTORY) { launchSingleTop = true } },
                onOpenTeamSetup = { navController.navigate(Routes.TEAM_SETUP) { launchSingleTop = true } },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } }
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                app = app,
                onOpenDay = { date -> navController.navigate(Routes.dayDetail(date)) { launchSingleTop = true } },
                onOpenRange = { start, end -> navController.navigate(Routes.rangeDetail(start, end)) { launchSingleTop = true } }
            )
        }
        composable(Routes.TEAM_SETUP) {
            TeamSetupScreen(app = app)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(app = app)
        }
        composable(
            route = Routes.DAY_DETAIL,
            arguments = listOf(navArgument("date") { type = NavType.StringType })
        ) { backStackEntry ->
            val date = LocalDate.parse(backStackEntry.arguments?.getString("date"))
            DayDetailScreen(app = app, date = date)
        }
        composable(
            route = Routes.RANGE_DETAIL,
            arguments = listOf(
                navArgument("start") { type = NavType.StringType },
                navArgument("end") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val start = LocalDate.parse(backStackEntry.arguments?.getString("start"))
            val end = LocalDate.parse(backStackEntry.arguments?.getString("end"))
            RangeDetailScreen(
                app = app,
                startDate = start,
                endDate = end,
                onOpenDay = { date -> navController.navigate(Routes.dayDetail(date)) { launchSingleTop = true } }
            )
        }
    }
}
