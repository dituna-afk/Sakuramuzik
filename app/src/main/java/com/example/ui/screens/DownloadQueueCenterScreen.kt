package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.downloader.DownloadFormatOption
import com.example.downloader.LikedSongsSourceType
import com.example.downloader.MediaTrackInfo
import com.example.downloader.RemotePlaylistSummary
import com.example.downloader.YoutubeDLDownloadState
import com.example.ui.components.OptimizedThumbnailImage
import com.example.ui.theme.LocalAppThemePalette

private enum class QueueCenterSubTab(val title: String) {
    PLAYLIST_FINDER("Kütüphane Listeleri & Aralık Seçimi"),
    ACTIVE_QUEUE("İndirme Kuyruğu & Zamanlayıcı")
}

/**
 * D. Bağımsız Tam Ekran İndirme ve Kuyruk Merkezi (DownloadQueueCenterScreen):
 * - Ana ekranı (HomeScreen) ağır iç içe listelerden kurtarır.
 * - Tek tuşla kullanıcının YouTube Music kütüphanesindeki tüm oynatma listelerini
 *   (Liste Adı + Tam Şarkı Sayısı ile) listeler ve seçtirir.
 * - Özel sayı aralığı (Örn: 15 - 60) ve hızlı aralık çipleriyle şarkı seçimi sunar.
 * - Şarkılar arası bekleme süresi (0sn, 3sn, 5sn, 10sn, 15sn, 30sn) ile aralıklı indirme sağlar.
 * - Kuyruk duraklatma, devam ettirme, hatalıları yeniden deneme, en üste taşıma ve iptal işlemlerini yönetir.
 */
@Composable
fun DownloadQueueCenterScreen(
    youtubeDLState: YoutubeDLDownloadState,
    selectedFormat: DownloadFormatOption,
    downloadIntervalDelaySeconds: Int,
    activeIntervalCountdownSeconds: Int,
    isDownloadQueuePaused: Boolean,
    downloadQueue: List<MediaTrackInfo>,
    failedQueueTracks: List<MediaTrackInfo>,
    batchDownloadCompletedCount: Int,
    batchDownloadTotalCount: Int,
    resolvedPlaylistTitle: String?,
    resolvedPlaylistTracks: List<MediaTrackInfo>,
    selectedPlaylistTrackIds: Set<String>,
    remoteLibraryPlaylists: List<RemotePlaylistSummary>,
    isLoadingRemoteLibraryPlaylists: Boolean,
    isResolvingStream: Boolean,
    onSelectFormat: (DownloadFormatOption) -> Unit,
    onSelectIntervalDelaySeconds: (Int) -> Unit,
    onFetchRemoteLibraryPlaylists: () -> Unit,
    onSelectRemoteLibraryPlaylist: (RemotePlaylistSummary) -> Unit,
    onDownloadAccountLibraryWithCookies: (LikedSongsSourceType) -> Unit,
    onTogglePlaylistTrackSelection: (String) -> Unit,
    onSelectAllPlaylistTracks: (Boolean) -> Unit,
    onSelectCustomPlaylistRange: (Int, Int) -> Unit,
    onDownloadAllSelectedPlaylistTracks: () -> Unit,
    onClearResolvedPlaylist: () -> Unit,
    onPauseQueue: () -> Unit,
    onResumeQueue: () -> Unit,
    onRetryFailedTracks: () -> Unit,
    onCancelActiveDownload: () -> Unit,
    onMoveToTopOfQueue: (String) -> Unit,
    onRemoveFromQueue: (String) -> Unit,
    onClearQueue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current
    BackHandler(onBack = onBack)

    var activeSubTab by rememberSaveable {
        mutableStateOf(
            if (downloadQueue.isNotEmpty() && resolvedPlaylistTracks.isEmpty()) {
                QueueCenterSubTab.ACTIVE_QUEUE
            } else {
                QueueCenterSubTab.PLAYLIST_FINDER
            }
        )
    }

    var rangeStartInput by rememberSaveable(resolvedPlaylistTracks.size) {
        mutableStateOf(if (resolvedPlaylistTracks.isNotEmpty()) "1" else "")
    }
    var rangeEndInput by rememberSaveable(resolvedPlaylistTracks.size) {
        mutableStateOf(if (resolvedPlaylistTracks.isNotEmpty()) "${minOf(25, resolvedPlaylistTracks.size)}" else "")
    }
    var rangeValidationMessage by remember { mutableStateOf<String?>(null) }

    val intervalOptions = remember { listOf(0, 3, 5, 10, 15, 30) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("download_queue_center_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Üst Başlık Çubuğu
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(palette.surfaceContainer)
                        .testTag("queue_center_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Geri",
                        tint = palette.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "İndirme ve Kuyruk Merkezi",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.onBackground
                    )
                    Text(
                        text = "Oynatma Listesi Seçici • Özel Aralık • Aralıklı İndirme",
                        fontSize = 11.sp,
                        color = palette.primaryAccent
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.primaryAccent.copy(alpha = 0.16f))
                    .border(1.dp, palette.primaryAccent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "Kuyruk: ${downloadQueue.size}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.primaryAccent
                )
            }
        }

        // İzole Aktif İndirme ve Aralıklı Bekleme Durum Çubuğu
        DownloadCenterActiveStatusHeader(
            youtubeDLState = youtubeDLState,
            activeIntervalCountdownSeconds = activeIntervalCountdownSeconds,
            isQueuePaused = isDownloadQueuePaused,
            completedCount = batchDownloadCompletedCount,
            totalCount = batchDownloadTotalCount,
            pendingCount = downloadQueue.size,
            onCancelActive = onCancelActiveDownload
        )

        // Alt Sekme Seçici (Oynatma Listeleri & Aralık Seçimi | İndirme Kuyruğu & Zamanlayıcı)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(palette.surfaceContainer)
                .border(1.dp, palette.dividerOrBorder, RoundedCornerShape(14.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            QueueCenterSubTab.entries.forEach { tab ->
                val selected = activeSubTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) palette.primaryAccent else Color.Transparent)
                        .clickable { activeSubTab = tab }
                        .padding(vertical = 9.dp)
                        .testTag("queue_center_tab_${tab.name}"),
                    contentAlignment = Alignment.Center
                ) {
                    val label = if (tab == QueueCenterSubTab.ACTIVE_QUEUE && downloadQueue.isNotEmpty()) {
                        "${tab.title} (${downloadQueue.size})"
                    } else {
                        tab.title
                    }
                    Text(
                        text = label,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) palette.onPrimary else palette.onSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Aralıklı İndirme (Şarkılar Arası Bekleme Süresi) & Format Çubuğu
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(palette.surfaceContainer)
                .border(1.dp, palette.dividerOrBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = palette.primaryAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Aralıklı İndirme (Şarkılar Arası Bekleme):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface
                    )
                }
                Text(
                    text = if (downloadIntervalDelaySeconds <= 0) "Anında (0 sn)" else "$downloadIntervalDelaySeconds sn",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.primaryAccent
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                intervalOptions.forEach { sec ->
                    val isSelected = downloadIntervalDelaySeconds == sec
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectIntervalDelaySeconds(sec) },
                        label = {
                            Text(
                                text = if (sec == 0) "Anında (0 sn)" else "$sec sn",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.primaryAccent,
                            selectedLabelColor = palette.onPrimary
                        ),
                        modifier = Modifier.testTag("interval_delay_chip_$sec")
                    )
                }
            }
        }

        // Ana İçerik Alanı
        when (activeSubTab) {
            QueueCenterSubTab.PLAYLIST_FINDER -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Tek Tuşla Kütüphanedeki Tüm Oynatma Listelerini Göster Kartı
                    item(key = "remote_library_playlists_header", contentType = "library_playlists_card") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(palette.surfaceContainer)
                                .border(1.dp, palette.primaryAccent.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LibraryMusic,
                                        contentDescription = null,
                                        tint = palette.primaryAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Kütüphanedeki Tüm Oynatma Listeleri",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.onSurface
                                        )
                                        Text(
                                            text = "Liste adlarını ve içindeki tam şarkı sayısını görerek seçin",
                                            fontSize = 11.sp,
                                            color = palette.onSurfaceMuted
                                        )
                                    }
                                }

                                Button(
                                    onClick = onFetchRemoteLibraryPlaylists,
                                    enabled = !isLoadingRemoteLibraryPlaylists,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = palette.primaryAccent,
                                        contentColor = palette.onPrimary
                                    ),
                                    modifier = Modifier.testTag("fetch_remote_playlists_button")
                                ) {
                                    if (isLoadingRemoteLibraryPlaylists) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = palette.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Yükleniyor...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (remoteLibraryPlaylists.isEmpty()) "Listeleri Göster" else "Listeleri Yenile",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Hızlı Beğenilenler Kısayolları
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onDownloadAccountLibraryWithCookies(LikedSongsSourceType.YOUTUBE_MUSIC) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Beğenilenler (YT Müzik)", fontSize = 11.sp, maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = { onDownloadAccountLibraryWithCookies(LikedSongsSourceType.YOUTUBE_VIDEOS) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Beğenilen Videolar (LL)", fontSize = 11.sp, maxLines = 1)
                                }
                            }

                            if (remoteLibraryPlaylists.isNotEmpty()) {
                                Text(
                                    text = "Bulunan Oynatma Listeleri (${remoteLibraryPlaylists.size} Liste):",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.primaryAccent
                                )
                            }
                        }
                    }

                    // Kullanıcının Kütüphanesindeki Oynatma Listesi Satırları (Ad + Tam Şarkı Sayısı)
                    itemsIndexed(
                        items = remoteLibraryPlaylists,
                        key = { index, pl -> "${pl.playlistId}_$index" },
                        contentType = { _, _ -> "remote_playlist_summary_row" }
                    ) { _, playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(palette.surfaceContainer)
                                .border(1.dp, palette.dividerOrBorder, RoundedCornerShape(14.dp))
                                .clickable(enabled = !isResolvingStream) { onSelectRemoteLibraryPlaylist(playlist) }
                                .padding(10.dp)
                                .testTag("remote_playlist_item_${playlist.playlistId}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OptimizedThumbnailImage(
                                model = playlist.thumbnailUrl,
                                contentDescription = playlist.title,
                                isWithinActiveWindow = true,
                                fallbackIcon = Icons.AutoMirrored.Filled.PlaylistPlay,
                                fallbackTint = palette.primaryAccent,
                                backgroundColor = palette.surfaceVariant,
                                iconSize = 22.dp,
                                targetPixelSize = 120,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.title,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(palette.primaryAccent.copy(alpha = 0.18f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${playlist.trackCount} Şarkı",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = palette.primaryAccent
                                        )
                                    }
                                    if (playlist.authorOrSubtitle.isNotBlank()) {
                                        Text(
                                            text = playlist.authorOrSubtitle,
                                            fontSize = 11.sp,
                                            color = palette.onSurfaceMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = { onSelectRemoteLibraryPlaylist(playlist) },
                                enabled = !isResolvingStream,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.primaryAccent,
                                    contentColor = palette.onPrimary
                                )
                            ) {
                                Text("Seç", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // 2. Çözümlenen Oynatma Listesi: Özel Sayı Aralığı (Örn: 15-60) ve Şarkı Seçim Alanı
                    if (resolvedPlaylistTracks.isNotEmpty()) {
                        item(key = "resolved_playlist_range_controls", contentType = "range_controls") {
                            val totalTracks = resolvedPlaylistTracks.size
                            val selectedCount = selectedPlaylistTrackIds.size
                            val allSelected = selectedCount == totalTracks && totalTracks > 0

                            val quickRanges = remember(totalTracks) {
                                buildList {
                                    add(Triple("Tümü ($totalTracks)", 1, totalTracks))
                                    if (totalTracks > 1) add(Triple("1 - ${minOf(25, totalTracks)}", 1, minOf(25, totalTracks)))
                                    if (totalTracks > 25) add(Triple("26 - ${minOf(50, totalTracks)}", 26, minOf(50, totalTracks)))
                                    if (totalTracks > 50) add(Triple("51 - ${minOf(75, totalTracks)}", 51, minOf(75, totalTracks)))
                                    if (totalTracks > 75) add(Triple("76 - ${minOf(100, totalTracks)}", 76, minOf(100, totalTracks)))
                                    if (totalTracks > 25) add(Triple("Son 25", (totalTracks - 24).coerceAtLeast(1), totalTracks))
                                    if (totalTracks > 50) add(Triple("Son 50", (totalTracks - 49).coerceAtLeast(1), totalTracks))
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(palette.surfaceContainer)
                                    .border(1.5.dp, palette.primaryAccent.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = resolvedPlaylistTitle ?: "Seçilen Oynatma Listesi",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = palette.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Toplam $totalTracks Şarkı • Seçili: $selectedCount Şarkı • Yalnızca gerçek liste öğeleri",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = palette.primaryAccent
                                        )
                                    }
                                    IconButton(
                                        onClick = onClearResolvedPlaylist,
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Listeyi Kapat",
                                            tint = palette.onSurfaceMuted
                                        )
                                    }
                                }

                                // Özel Sayı Aralığı Seçici (Örn: 15 - 60)
                                Text(
                                    text = "Özel Sayı Aralığı Seçerek İndir (1 - $totalTracks):",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.onSurface
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = rangeStartInput,
                                        onValueChange = {
                                            rangeStartInput = it.filter { ch -> ch.isDigit() }
                                            rangeValidationMessage = null
                                        },
                                        label = { Text("Başlangıç (Örn: 15)", fontSize = 10.sp) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("custom_range_start_input")
                                    )
                                    Text("—", fontWeight = FontWeight.Bold, color = palette.onSurfaceMuted)
                                    OutlinedTextField(
                                        value = rangeEndInput,
                                        onValueChange = {
                                            rangeEndInput = it.filter { ch -> ch.isDigit() }
                                            rangeValidationMessage = null
                                        },
                                        label = { Text("Bitiş (Örn: 60)", fontSize = 10.sp) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("custom_range_end_input")
                                    )
                                    Button(
                                        onClick = {
                                            val startVal = rangeStartInput.toIntOrNull()
                                            val endVal = rangeEndInput.toIntOrNull()
                                            when {
                                                startVal == null || endVal == null -> {
                                                    rangeValidationMessage = "Lütfen geçerli bir başlangıç ve bitiş sayısı girin."
                                                }
                                                startVal <= 0 || endVal <= 0 -> {
                                                    rangeValidationMessage = "Sıra numaraları 1 veya daha büyük olmalıdır."
                                                }
                                                startVal > totalTracks -> {
                                                    rangeValidationMessage = "Başlangıç sayısı liste boyutundan ($totalTracks) büyük olamaz."
                                                }
                                                else -> {
                                                    val s = minOf(startVal, endVal).coerceIn(1, totalTracks)
                                                    val e = maxOf(startVal, endVal).coerceIn(s, totalTracks)
                                                    onSelectCustomPlaylistRange(s, e)
                                                    rangeValidationMessage = "$s ile $e arasındaki (${e - s + 1}) şarkı seçildi."
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = palette.primaryAccent,
                                            contentColor = palette.onPrimary
                                        ),
                                        modifier = Modifier.testTag("apply_custom_range_button")
                                    ) {
                                        Text("Aralığı Seç", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (!rangeValidationMessage.isNullOrBlank()) {
                                    Text(
                                        text = rangeValidationMessage!!,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = palette.primaryAccent
                                    )
                                }

                                // Hızlı Aralık Çipleri
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    quickRanges.forEach { (label, sOne, eOne) ->
                                        FilterChip(
                                            selected = false,
                                            onClick = {
                                                rangeStartInput = sOne.toString()
                                                rangeEndInput = eOne.toString()
                                                onSelectCustomPlaylistRange(sOne, eOne)
                                                rangeValidationMessage = "$sOne - $eOne aralığı seçildi."
                                            },
                                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                                        )
                                    }
                                }

                                // Tümünü Seç / Seçimi Bırak ve İndir Butonu
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { onSelectAllPlaylistTracks(!allSelected) }
                                    ) {
                                        Icon(
                                            imageVector = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (allSelected) "Seçimi Temizle" else "Tümünü Seç ($totalTracks)",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            onDownloadAllSelectedPlaylistTracks()
                                            activeSubTab = QueueCenterSubTab.ACTIVE_QUEUE
                                        },
                                        enabled = selectedCount > 0,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = palette.primaryAccent,
                                            contentColor = palette.onPrimary
                                        ),
                                        modifier = Modifier.testTag("download_selected_playlist_tracks_button")
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Seçili $selectedCount Şarkıyı İndir",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }

                        // Çözümlenen Liste Şarkıları (Sanallaştırılmış LazyColumn öğeleri)
                        itemsIndexed(
                            items = resolvedPlaylistTracks,
                            key = { index, track -> "resolved_${track.id}_$index" },
                            contentType = { _, _ -> "resolved_playlist_track_row" }
                        ) { index, track ->
                            val isSelected = track.id in selectedPlaylistTrackIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) palette.primaryAccent.copy(alpha = 0.14f)
                                        else palette.surfaceContainer.copy(alpha = 0.7f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) palette.primaryAccent else palette.dividerOrBorder,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onTogglePlaylistTrackSelection(track.id) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) palette.primaryAccent else palette.onSurfaceMuted,
                                    modifier = Modifier.width(30.dp)
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 6.dp)
                                ) {
                                    Text(
                                        text = track.title,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) palette.onSurface else palette.onSurfaceMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = track.artist.ifBlank { "YouTube Music" },
                                        fontSize = 10.5.sp,
                                        color = palette.onSurfaceMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isSelected) palette.primaryAccent else palette.onSurfaceMuted.copy(alpha = 0.45f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            QueueCenterSubTab.ACTIVE_QUEUE -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Kuyruk Kontrol Butonları: Duraklat / Devam Et / Hatalıları Yeniden Dene / Temizle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (downloadQueue.isNotEmpty()) {
                            Button(
                                onClick = {
                                    if (isDownloadQueuePaused) onResumeQueue() else onPauseQueue()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDownloadQueuePaused) palette.primaryAccent else palette.secondaryAccent,
                                    contentColor = palette.onPrimary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("queue_pause_resume_button")
                            ) {
                                Icon(
                                    imageVector = if (isDownloadQueuePaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isDownloadQueuePaused) "Kuyruğu Devam Ettir" else "Kuyruğu Duraklat",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (failedQueueTracks.isNotEmpty()) {
                            OutlinedButton(
                                onClick = onRetryFailedTracks,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("queue_retry_failed_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Hatalıları Dene (${failedQueueTracks.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (downloadQueue.isNotEmpty() || failedQueueTracks.isNotEmpty()) {
                            OutlinedButton(
                                onClick = onClearQueue,
                                modifier = Modifier.testTag("queue_center_clear_all_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    tint = palette.tertiaryAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Temizle", fontSize = 11.sp, color = palette.tertiaryAccent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (downloadQueue.isEmpty() && failedQueueTracks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(palette.surfaceContainer.copy(alpha = 0.6f))
                                .border(1.dp, palette.dividerOrBorder, RoundedCornerShape(18.dp))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = palette.primaryAccent,
                                    modifier = Modifier.size(42.dp)
                                )
                                Text(
                                    text = "İndirme Kuyruğu Boş",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.onSurface
                                )
                                Text(
                                    text = "Sol sekmeden kütüphanenizdeki oynatma listelerini görüntüleyip özel aralık seçerek kuyruğa şarkı ekleyebilirsiniz.",
                                    fontSize = 12.sp,
                                    color = palette.onSurfaceMuted,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(
                                items = downloadQueue,
                                key = { index, track -> "pending_${track.id}_$index" },
                                contentType = { _, _ -> "pending_queue_track_row" }
                            ) { index, track ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(palette.surfaceContainer)
                                        .border(
                                            width = 1.dp,
                                            color = if (index == 0) palette.primaryAccent.copy(alpha = 0.65f) else palette.dividerOrBorder,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (index == 0) palette.primaryAccent.copy(alpha = 0.22f)
                                                else palette.surfaceVariant
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (index == 0) palette.primaryAccent else palette.onSurfaceMuted
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            fontSize = 12.5.sp,
                                            fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Medium,
                                            color = palette.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (index == 0) "Sıradaki İlk Şarkı • ${track.artist}" else track.artist,
                                            fontSize = 10.5.sp,
                                            color = if (index == 0) palette.primaryAccent else palette.onSurfaceMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (index > 0) {
                                        IconButton(
                                            onClick = { onMoveToTopOfQueue(track.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowUp,
                                                contentDescription = "En Üste Taşı",
                                                tint = palette.primaryAccent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { onRemoveFromQueue(track.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Kuyruktan Çıkar",
                                            tint = palette.onSurfaceMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadCenterActiveStatusHeader(
    youtubeDLState: YoutubeDLDownloadState,
    activeIntervalCountdownSeconds: Int,
    isQueuePaused: Boolean,
    completedCount: Int,
    totalCount: Int,
    pendingCount: Int,
    onCancelActive: () -> Unit
) {
    val palette = LocalAppThemePalette.current

    if (youtubeDLState is YoutubeDLDownloadState.Idle &&
        activeIntervalCountdownSeconds <= 0 &&
        !isQueuePaused &&
        pendingCount == 0
    ) {
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surfaceContainer)
            .border(1.dp, palette.primaryAccent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (isQueuePaused) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Pause,
                    contentDescription = null,
                    tint = palette.tertiaryAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Kuyruk duraklatıldı • Bekleyen: $pendingCount şarkı",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.tertiaryAccent
                )
            }
        } else if (activeIntervalCountdownSeconds > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = palette.primaryAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Aralıklı indirme beklemesi: Sonraki şarkı $activeIntervalCountdownSeconds sn içinde başlayacak...",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.primaryAccent
                )
            }
        }

        when (youtubeDLState) {
            is YoutubeDLDownloadState.Downloading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = youtubeDLState.title,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "%${youtubeDLState.progressPercent.toInt()} • Kalan: ${youtubeDLState.etaSeconds}sn • Tamamlanan: $completedCount/$totalCount",
                            fontSize = 11.sp,
                            color = palette.primaryAccent
                        )
                    }
                    TextButton(onClick = onCancelActive) {
                        Text("İptal", fontSize = 11.sp, color = palette.tertiaryAccent, fontWeight = FontWeight.Bold)
                    }
                }
                LinearProgressIndicator(
                    progress = { (youtubeDLState.progressPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = palette.primaryAccent,
                    trackColor = palette.surfaceVariant
                )
            }
            is YoutubeDLDownloadState.Initializing, is YoutubeDLDownloadState.FetchingInfo, is YoutubeDLDownloadState.SavingMetadata -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = palette.primaryAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "İndirme hazırlanıyor... ($completedCount/$totalCount tamamlandı)",
                        fontSize = 11.5.sp,
                        color = palette.onSurface
                    )
                }
            }
            is YoutubeDLDownloadState.Error -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = palette.tertiaryAccent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = youtubeDLState.message,
                        fontSize = 11.5.sp,
                        color = palette.tertiaryAccent,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            else -> Unit
        }
    }
}
