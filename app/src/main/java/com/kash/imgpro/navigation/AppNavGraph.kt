package com.kash.imgpro.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kash.imgpro.ui.screens.CompressorScreen
import com.kash.imgpro.ui.screens.ConverterScreen
import com.kash.imgpro.ui.screens.HomeScreen

/**
 * Type-safe route definitions.
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Compressor : Screen("compressor")
    data object Converter : Screen("converter")
}

/**
 * App-level navigation graph with smooth slide+fade transitions.
 */
@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val transitionDuration = 350

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(transitionDuration),
            ) + fadeIn(animationSpec = tween(transitionDuration))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(transitionDuration),
            ) + fadeOut(animationSpec = tween(transitionDuration))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(transitionDuration),
            ) + fadeIn(animationSpec = tween(transitionDuration))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(transitionDuration),
            ) + fadeOut(animationSpec = tween(transitionDuration))
        },
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToCompressor = {
                    navController.navigate(Screen.Compressor.route)
                },
                onNavigateToConverter = {
                    navController.navigate(Screen.Converter.route)
                },
            )
        }

        composable(Screen.Compressor.route) {
            CompressorScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Screen.Converter.route) {
            ConverterScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
