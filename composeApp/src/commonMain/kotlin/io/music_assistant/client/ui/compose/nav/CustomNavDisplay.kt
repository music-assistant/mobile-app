package io.music_assistant.client.ui.compose.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/**
 * Custom version of [NavDisplay] that accepts (and handles the boilerplate for) [entries], but
 * also allows the back handling to be enabled and disabled.
 *
 * An issue has been filed about this option being currently missing from Navigation 3: https://issuetracker.google.com/u/2/issues/518651940
 */
@Composable
fun <T : Any> ConditionalBackNavDisplay(
    entries: List<NavEntry<T>>,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    sceneStrategies: List<SceneStrategy<T>> = listOf(SinglePaneSceneStrategy()),
    sceneDecoratorStrategies: List<SceneDecoratorStrategy<T>> = emptyList(),
    sharedTransitionScope: SharedTransitionScope? = null,
    sizeTransform: SizeTransform? = null,
    transitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
        defaultTransitionSpec(),
    popTransitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform =
        defaultPopTransitionSpec(),
    predictivePopTransitionSpec:
    AnimatedContentTransitionScope<Scene<T>>.(
        @NavigationEvent.SwipeEdge Int,
    ) -> ContentTransform =
        defaultPredictivePopTransitionSpec(),
    onBack: () -> Unit,
    backEnabled: Boolean = true,
) {
    require(entries.isNotEmpty()) { "NavDisplay entries cannot be empty" }

    val sceneState =
        rememberSceneState(
            entries,
            sceneStrategies,
            sceneDecoratorStrategies,
            sharedTransitionScope,
            onBack,
        )
    val scene = sceneState.currentScene

    // Predictive Back Handling
    val currentInfo = SceneInfo(scene)
    val previousSceneInfos = sceneState.previousScenes.map { SceneInfo(it) }
    val gestureState =
        rememberNavigationEventState(currentInfo = currentInfo, backInfo = previousSceneInfos)

    NavigationBackHandler(
        state = gestureState,
        isBackEnabled = backEnabled && scene.previousEntries.isNotEmpty(),
        onBackCompleted = {
            // If `enabled` becomes stale (e.g., it was set to false but a gesture was
            // dispatched in the same frame), this may result in no entries being popped
            // due to entries.size being smaller than scene.previousEntries.size
            // but that's preferable to crashing with an IndexOutOfBoundsException
            repeat(entries.size - scene.previousEntries.size) { onBack() }
        },
    )

    NavDisplay(
        sceneState,
        gestureState,
        modifier,
        contentAlignment,
        sizeTransform,
        transitionSpec,
        popTransitionSpec,
        predictivePopTransitionSpec,
    )
}
