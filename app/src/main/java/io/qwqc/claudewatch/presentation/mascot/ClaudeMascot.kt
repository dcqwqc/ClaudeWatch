package io.qwqc.claudewatch.presentation.mascot

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * A composable that displays the animated "Clawd" mascot.
 *
 * This component loads the mascot's animated GIF from a remote URL using Coil,
 * which automatically handles caching for offline viewing. It's designed to
 * fit within the layout bounds provided by its parent.
 *
 * @param mood This parameter is kept for source compatibility and future use,
 *             but the current animated GIF does not change based on mood.
 * @param modifier The [Modifier] to be applied to the image.
 */
@Composable
fun ClaudeMascot(
    mood: MascotMood = MascotMood.Idle,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data("https://i.postimg.cc/6QFb7j5j/Clawd-Laptop.gif")
            .build(),
        contentDescription = "Clawd mascot",
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

/** Defines the possible states for the mascot, for future use. */
enum class MascotMood { Idle, Thinking, Done }
