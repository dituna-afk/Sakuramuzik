package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.downloader.MediaTrackInfo
import com.example.downloader.SavedMediaResult
import com.example.model.LrcParser
import com.example.ui.components.OptimizedThumbnailImage
import com.example.ui.components.rememberProgressiveWindowState
import com.example.ui.theme.LocalAppThemePalette

@Composable
fun SearchScreen(
    searchQuery: String,
    isSearchingCatalog: Boolean,
    catalogResults: List<MediaTrackInfo>,
    downloadedTracks: List<SavedMediaResult>,
    onSearchQueryChange: (String) -> Unit,
    onTriggerSearch: () -> Unit,
    onPlayCatalogTrack: (MediaTrackInfo) -> Unit,
    onDownloadCatalogTrack: (MediaTrackInfo) -> Unit,
    onPlaySavedTrack: (SavedMediaResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current

    val localMatches = remember(searchQuery, downloadedTracks) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            val q = searchQuery.trim().lowercase()
            downloadedTracks.filter {
                it.trackInfo.title.lowercase().contains(q) ||
                    it.trackInfo.artist.lowercase().contains(q)
            }.take(15)
        }
    }

    val listState = rememberLazyListState()
    val catalogHeaderOffset = 2 + (if (localMatches.isNotEmpty()) 1 + localMatches.size else 0)

    // İlk başta 5 arama sonucu yüklenir, kaydırdıkça 5'er açılır ve yukarıda kalan görseller bellekten düşürülür
    val windowState = rememberProgressiveWindowState(
        listState = listState,
        totalItemsCount = catalogResults.size,
        headerItemsOffset = catalogHeaderOffset,
        initialBatchSize = 5,
        stepBatchSize = 5,
        activeWindowBuffer = 2
    )

    val displayedCatalogResults = remember(catalogResults, windowState.loadedCount) {
        catalogResults.take(windowState.loadedCount)
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "search_header_box", contentType = "search_header") {
            Text(
                text = "Müzik Keşfet & Ara",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = palette.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Şarkı, sanatçı veya albüm ara...") },
                leadingIcon = {
                    IconButton(onClick = onTriggerSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Ara")
                    }
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Temizle")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_input")
            )
        }

        if (localMatches.isNotEmpty()) {
            item(key = "local_matches_title", contentType = "section_header") {
                Text(
                    text = "İndirilenlerinizde Eşleşenler (${localMatches.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.primaryAccent
                )
            }
            itemsIndexed(
                items = localMatches,
                key = { _, saved -> "local_${saved.audioFile.absolutePath}" },
                contentType = { _, _ -> "local_match_row" }
            ) { _, saved ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.surfaceContainer)
                        .clickable { onPlaySavedTrack(saved) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = saved.trackInfo.title,
                            fontWeight = FontWeight.Bold,
                            color = palette.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${saved.trackInfo.artist} • Çevrimdışı Hazır",
                            fontSize = 12.sp,
                            color = palette.primaryAccent
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Dinle",
                        tint = palette.primaryAccent
                    )
                }
            }
        }

        item(key = "catalog_results_title", contentType = "section_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Çevrimiçi Katalog Sonuçları",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.onBackground
                    )
                    if (isSearchingCatalog) {
                        Spacer(modifier = Modifier.width(10.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = palette.primaryAccent
                        )
                    }
                }
                if (catalogResults.isNotEmpty()) {
                    Text(
                        text = "${displayedCatalogResults.size}/${catalogResults.size} yüklü",
                        fontSize = 11.sp,
                        color = palette.onSurfaceMuted
                    )
                }
            }
        }

        itemsIndexed(
            items = displayedCatalogResults,
            key = { _, track -> "cat_${track.id}" },
            contentType = { _, _ -> "catalog_track_row" }
        ) { index, track ->
            val isInActiveWindow = windowState.isTrackInActiveMemoryWindow(index)
            val durText = remember(track.durationSeconds) {
                if (track.durationSeconds > 0L) {
                    " • ${LrcParser.formatTimestamp(track.durationSeconds * 1000L)}"
                } else ""
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.surfaceContainer)
                    .border(1.dp, palette.dividerOrBorder, RoundedCornerShape(16.dp))
                    .clickable { onPlayCatalogTrack(track) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OptimizedThumbnailImage(
                    model = track.thumbnailUrl?.takeIf { it.isNotBlank() },
                    contentDescription = track.title,
                    isWithinActiveWindow = isInActiveWindow,
                    fallbackIcon = Icons.Default.MusicNote,
                    fallbackTint = palette.primaryAccent,
                    backgroundColor = palette.surfaceVariant,
                    iconSize = 24.dp,
                    targetPixelSize = 120,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${track.artist}$durText",
                        fontSize = 12.sp,
                        color = palette.onSurfaceMuted,
                        maxLines = 1
                    )
                }

                IconButton(onClick = { onPlayCatalogTrack(track) }) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Dinle",
                        tint = palette.secondaryAccent
                    )
                }

                IconButton(onClick = { onDownloadCatalogTrack(track) }) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "İndir",
                        tint = palette.primaryAccent
                    )
                }
            }
        }
    }
}
