package io.qwqc.claudewatch.presentation.mascot

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

/** Kept for source compatibility; the animated GIF ignores mood. */
enum class MascotMood { Idle, Thinking, Done }

/**
 * The Clawd mascot — an animated pixel-art GIF, loaded from a URL by Coil.
 * It is cached for offline use.
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
