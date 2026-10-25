package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision

/**
 * Native LazyColumn / LazyVerticalGrid virtualization state.
 * Preserves the exact API contract across all 6 screens while eliminating mid-scroll dataset
 * slicing (.take(loadedCount)) and per-row layoutInfo snapshot recompositions.
 * LazyColumn/LazyVerticalGrid natively compose only visible + prefetched items and recycle off-screen slots.
 */
@Immutable
class ProgressiveWindowState(
    val loadedCount: Int,
    private val firstActiveTrackIndex: Int = 0,
    private val lastActiveTrackIndex: Int = Int.MAX_VALUE
) {
    fun isTrackInActiveMemoryWindow(trackIndex: Int): Boolean {
        if (loadedCount <= 0) return false
        return trackIndex in firstActiveTrackIndex..minOf(lastActiveTrackIndex, loadedCount - 1)
    }
}

@Composable
fun rememberProgressiveWindowState(
    @Suppress("UNUSED_PARAMETER") listState: LazyListState,
    totalItemsCount: Int,
    @Suppress("UNUSED_PARAMETER") headerItemsOffset: Int = 1,
    @Suppress("UNUSED_PARAMETER") initialBatchSize: Int = 24,
    @Suppress("UNUSED_PARAMETER") stepBatchSize: Int = 24,
    @Suppress("UNUSED_PARAMETER") activeWindowBuffer: Int = 24
): ProgressiveWindowState {
    val safeTotal = totalItemsCount.coerceAtLeast(0)
    return remember(safeTotal) {
        ProgressiveWindowState(
            loadedCount = safeTotal,
            firstActiveTrackIndex = 0,
            lastActiveTrackIndex = (safeTotal - 1).coerceAtLeast(0)
        )
    }
}

@Composable
fun rememberGridProgressiveWindowState(
    @Suppress("UNUSED_PARAMETER") gridState: LazyGridState,
    totalItemsCount: Int,
    @Suppress("UNUSED_PARAMETER") headerItemsOffset: Int = 1,
    @Suppress("UNUSED_PARAMETER") initialBatchSize: Int = 24,
    @Suppress("UNUSED_PARAMETER") stepBatchSize: Int = 24,
    @Suppress("UNUSED_PARAMETER") activeWindowBuffer: Int = 24
): ProgressiveWindowState {
    val safeTotal = totalItemsCount.coerceAtLeast(0)
    return remember(safeTotal) {
        ProgressiveWindowState(
            loadedCount = safeTotal,
            firstActiveTrackIndex = 0,
            lastActiveTrackIndex = (safeTotal - 1).coerceAtLeast(0)
        )
    }
}

/**
 * Memory-aware thumbnail loader that uses fixed container sizing to prevent layout jumps,
 * RGB_565 only for small list thumbnails (<= 180px), and full ARGB_8888 for larger covers.
 */
@Composable
fun OptimizedThumbnailImage(
    model: Any?,
    contentDescription: String?,
    isWithinActiveWindow: Boolean = true,
    fallbackIcon: ImageVector = Icons.Default.MusicNote,
    fallbackTint: Color,
    backgroundColor: Color,
    iconSize: Dp = 24.dp,
    targetPixelSize: Int = 144,
    allowRgb565ForSmallThumbnails: Boolean = targetPixelSize <= 180,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = modifier.background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = fallbackIcon,
            contentDescription = null,
            tint = fallbackTint.copy(alpha = 0.65f),
            modifier = Modifier.size(iconSize)
        )
        if (model != null && isWithinActiveWindow) {
            val safePx = targetPixelSize.coerceIn(64, 1024)
            val imageRequest = remember(model, safePx, allowRgb565ForSmallThumbnails) {
                ImageRequest.Builder(context)
                    .data(model)
                    .size(safePx, safePx)
                    .precision(Precision.INEXACT)
                    .crossfade(false)
                    .allowHardware(true)
                    .allowRgb565(allowRgb565ForSmallThumbnails)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
