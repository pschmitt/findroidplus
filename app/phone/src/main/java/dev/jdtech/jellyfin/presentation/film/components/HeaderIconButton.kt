package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color

/**
 * The one circular "floating over content" button every screen header uses - a black, 70%-alpha
 * circle with white content. Previously [ItemTopBar] (Movie/Show/Season/Episode/Person) and
 * [HomeHeader] each defined their own look (this one vs. solid `surfaceContainerHigh` circles),
 * so Home's search/favorites/settings buttons read as a different component even though they do
 * the same job. Both now build every icon button from this.
 */
@Composable
fun HeaderIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.alpha(0.7f),
        colors =
            IconButtonDefaults.iconButtonColors(containerColor = Color.Black, contentColor = Color.White),
        content = content,
    )
}
