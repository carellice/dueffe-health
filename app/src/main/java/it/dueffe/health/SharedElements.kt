package it.dueffe.health

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

@OptIn(ExperimentalSharedTransitionApi::class)
internal val LocalHealthSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

internal val LocalHealthAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.healthSharedBounds(key: String): Modifier {
    val sharedScope = LocalHealthSharedTransitionScope.current
    val visibilityScope = LocalHealthAnimatedVisibilityScope.current
    return if (sharedScope != null && visibilityScope != null) {
        with(sharedScope) {
            this@healthSharedBounds.sharedBounds(
                sharedContentState = rememberSharedContentState(key = key),
                animatedVisibilityScope = visibilityScope
            )
        }
    } else this
}
