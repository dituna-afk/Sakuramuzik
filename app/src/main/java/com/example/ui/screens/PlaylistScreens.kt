package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.UserPlaylist
import com.example.downloader.SavedMediaResult
import com.example.ui.components.OptimizedThumbnailImage
import com.example.ui.components.rememberGridProgressiveWindowState
import com.example.ui.components.rememberProgressiveWindowState
import com.example.ui.theme.LocalAppThemePalette

@Composable
fun PlaylistsScreen(
    playlists: List<UserPlaylist>,
    selectedPlaylistId: String?,
    downloadedTracks: List<SavedMediaResult>,
    isGridViewSaved: Boolean = false,
    onToggleGridView: (Boolean) -> Unit = {},
    onCreatePlaylist: (String, String, String?) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onOpenPlaylistDetail: (String) -> Unit,
    onClosePlaylistDetail: () -> Unit,
    onUpdatePlaylistCover: (String, String?) -> Unit,
    onAddTrackToPlaylist: (String, SavedMediaResult) -> Unit,
    onRemoveTrackFromPlaylist: (String, String) -> Unit,
    onPlayPlaylistQueue: (List<SavedMediaResult>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current
    var showCreateSheet by remember { mutableStateOf(false) }
    var isGridView by rememberSaveable { mutableStateOf(isGridViewSaved) }

    LaunchedEffect(isGridViewSaved) {
        if (isGridView != isGridViewSaved) {
            isGridView = isGridViewSaved
        }
    }

    val selectedPlaylist = remember(playlists, selectedPlaylistId) {
        playlists.find { it.id == selectedPlaylistId }
    }

    val playlistsGridState = rememberLazyGridState()
    val playlistsListState = rememberLazyListState()

    val gridWindow = rememberGridProgressiveWindowState(
        gridState = playlistsGridState,
        totalItemsCount = playlists.size,
        headerItemsOffset = 0,
        initialBatchSize = 6,
        stepBatchSize = 6,
        activeWindowBuffer = 2
    )

    val listWindow = rememberProgressiveWindowState(
        listState = playlistsListState,
        totalItemsCount = playlists.size,
        headerItemsOffset = 0,
        initialBatchSize = 6,
        stepBatchSize = 6,
        activeWindowBuffer = 2
    )

    AnimatedContent(
        targetState = selectedPlaylist,
        transitionSpec = {
            if (targetState != null) {
                (scaleIn(initialScale = 0.88f, animationSpec = tween(350)) +
                 slideInVertically(animationSpec = tween(350)) { height -> height / 4 } +
                 fadeIn(animationSpec = tween(350)))
                    .togetherWith(
                        scaleOut(targetScale = 0.95f, animationSpec = tween(300)) +
                        fadeOut(animationSpec = tween(300))
                    )
            } else {
                (scaleIn(initialScale = 0.95f, animationSpec = tween(300)) +
                 fadeIn(animationSpec = tween(300)))
                    .togetherWith(
                        scaleOut(targetScale = 0.88f, animationSpec = tween(300)) +
                        slideOutVertically(animationSpec = tween(300)) { height -> height / 4 } +
                        fadeOut(animationSpec = tween(300))
                    )
            }
        },
        label = "playlist_detail_anim"
    ) { detailPlaylist ->
        if (detailPlaylist != null) {
            PlaylistDetailScreen(
                playlist = detailPlaylist,
                allDownloadedTracks = downloadedTracks,
                onBack = onClosePlaylistDetail,
                onUpdateCover = { uri -> onUpdatePlaylistCover(detailPlaylist.id, uri) },
                onAddTrack = { track -> onAddTrackToPlaylist(detailPlaylist.id, track) },
                onRemoveTrack = { path -> onRemoveTrackFromPlaylist(detailPlaylist.id, path) },
                onPlayTrackAt = { index -> onPlayPlaylistQueue(detailPlaylist.tracks, index) },
                onDeletePlaylist = { onDeletePlaylist(detailPlaylist.id) },
                modifier = modifier
            )
        } else {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Çalma Listelerim",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.onBackground
                        )
                        Text(
                            text = "Room veritabanı ile kalıcı özel listeler",
                            fontSize = 12.sp,
                            color = palette.onSurfaceMuted
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            val nextMode = !isGridView
                            isGridView = nextMode
                            onToggleGridView(nextMode)
                        }) {
                            Icon(
                                imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = if (isGridView) "Liste Görünümü" else "Izgara Görünümü",
                                tint = palette.primaryAccent
                            )
                        }

                        Button(
                            onClick = { showCreateSheet = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.primaryAccent,
                                contentColor = palette.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Yeni Liste")
                        }
                    }
                }

                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(palette.surfaceContainer)
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Henüz bir çalma listesi oluşturmadınız. 'Yeni Liste' düğmesiyle özel kapaklı listenizi oluşturun.",
                            color = palette.onSurfaceMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (isGridView) {
                    val displayedGridPlaylists = remember(playlists, gridWindow.loadedCount) {
                        playlists.take(gridWindow.loadedCount)
                    }
                    LazyVerticalGrid(
                        state = playlistsGridState,
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(bottom = 110.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(
                            items = displayedGridPlaylists,
                            key = { _, playlist -> playlist.id },
                            contentType = { _, _ -> "playlist_grid_card" }
                        ) { index, playlist ->
                            val isInActiveWindow = gridWindow.isTrackInActiveMemoryWindow(index)
                            val coverModel = playlist.coverUri
                                ?: playlist.tracks.firstOrNull()?.coverFile
                                ?: playlist.tracks.firstOrNull()?.trackInfo?.thumbnailUrl

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(palette.surfaceContainer)
                                    .border(1.dp, palette.dividerOrBorder, RoundedCornerShape(18.dp))
                                    .clickable { onOpenPlaylistDetail(playlist.id) }
                                    .padding(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(palette.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    OptimizedThumbnailImage(
                                        model = coverModel,
                                        contentDescription = playlist.name,
                                        isWithinActiveWindow = isInActiveWindow,
                                        fallbackIcon = Icons.AutoMirrored.Filled.QueueMusic,
                                        fallbackTint = palette.primaryAccent,
                                        backgroundColor = palette.surfaceVariant,
                                        iconSize = 44.dp,
                                        targetPixelSize = 180,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    if (playlist.tracks.isNotEmpty()) {
                                        Surface(
                                            onClick = { onPlayPlaylistQueue(playlist.tracks, 0) },
                                            shape = CircleShape,
                                            color = palette.primaryAccent,
                                            contentColor = palette.onPrimary,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(8.dp)
                                                .size(38.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Oynat",
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = playlist.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = "${playlist.tracks.size} şarkı",
                                    fontSize = 12.sp,
                                    color = palette.primaryAccent
                                )
                            }
                        }
                    }
                } else {
                    val displayedListPlaylists = remember(playlists, listWindow.loadedCount) {
                        playlists.take(listWindow.loadedCount)
                    }
                    LazyColumn(
                        state = playlistsListState,
                        contentPadding = PaddingValues(bottom = 110.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(
                            items = displayedListPlaylists,
                            key = { _, playlist -> playlist.id },
                            contentType = { _, _ -> "playlist_list_row" }
                        ) { index, playlist ->
                            val isInActiveWindow = listWindow.isTrackInActiveMemoryWindow(index)
                            val coverModel = playlist.coverUri
                                ?: playlist.tracks.firstOrNull()?.coverFile
                                ?: playlist.tracks.firstOrNull()?.trackInfo?.thumbnailUrl

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(palette.surfaceContainer)
                                    .border(1.dp, palette.dividerOrBorder, RoundedCornerShape(18.dp))
                                    .clickable { onOpenPlaylistDetail(playlist.id) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OptimizedThumbnailImage(
                                    model = coverModel,
                                    contentDescription = playlist.name,
                                    isWithinActiveWindow = isInActiveWindow,
                                    fallbackIcon = Icons.AutoMirrored.Filled.QueueMusic,
                                    fallbackTint = palette.primaryAccent,
                                    backgroundColor = palette.surfaceVariant,
                                    iconSize = 30.dp,
                                    targetPixelSize = 136,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                )

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (playlist.description.isNotBlank()) {
                                        Text(
                                            text = playlist.description,
                                            fontSize = 12.sp,
                                            color = palette.onSurfaceMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "${playlist.tracks.size} şarkı",
                                        fontSize = 12.sp,
                                        color = palette.primaryAccent
                                    )
                                }

                                if (playlist.tracks.isNotEmpty()) {
                                    IconButton(onClick = { onPlayPlaylistQueue(playlist.tracks, 0) }) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Listeyi Çal",
                                            tint = palette.primaryAccent
                                        )
                                    }
                                }

                                IconButton(onClick = { onDeletePlaylist(playlist.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Sil",
                                        tint = palette.tertiaryAccent
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        CreatePlaylistBottomSheet(
            onDismiss = { showCreateSheet = false },
            onCreate = { name, desc, coverUri ->
                onCreatePlaylist(name, desc, coverUri)
                showCreateSheet = false
            }
        )
    }
}

@Composable
fun PlaylistDetailScreen(
    playlist: UserPlaylist,
    allDownloadedTracks: List<SavedMediaResult>,
    onBack: () -> Unit,
    onUpdateCover: (String?) -> Unit,
    onAddTrack: (SavedMediaResult) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onPlayTrackAt: (Int) -> Unit,
    onDeletePlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val palette = LocalAppThemePalette.current
    var showAddTrackDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onUpdateCover(uri.toString())
        }
    }

    val durationText = remember(playlist.tracks) {
        val totalDurationSec = playlist.tracks.sumOf { it.trackInfo.durationSeconds }
        val hours = totalDurationSec / 3600
        val minutes = (totalDurationSec % 3600) / 60
        if (hours > 0) "${hours} sa. ${minutes} dk." else "${minutes} dk."
    }

    val detailGridState = rememberLazyGridState()
    val detailWindow = rememberGridProgressiveWindowState(
        gridState = detailGridState,
        totalItemsCount = playlist.tracks.size,
        headerItemsOffset = 3,
        initialBatchSize = 6,
        stepBatchSize = 6,
        activeWindowBuffer = 2
    )

    val displayedPlaylistTracks = remember(playlist.tracks, detailWindow.loadedCount) {
        playlist.tracks.take(detailWindow.loadedCount)
    }

    LazyVerticalGrid(
        state = detailGridState,
        columns = GridCells.Fixed(if (isGridView) 2 else 1),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 110.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Navigation Bar (Span full width)
        item(key = "detail_top_bar", span = { GridItemSpan(maxLineSpan) }, contentType = "top_bar") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Geri",
                        tint = palette.onBackground
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isGridView = !isGridView }) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = if (isGridView) "Liste Görünümü" else "Izgara Görünümü",
                            tint = palette.primaryAccent
                        )
                    }
                }
            }
        }

        // Header Section
        item(key = "detail_hero_header", span = { GridItemSpan(maxLineSpan) }, contentType = "hero_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(palette.surfaceContainer)
                        .border(1.dp, palette.dividerOrBorder, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val coverModel = playlist.coverUri
                        ?: playlist.tracks.firstOrNull()?.coverFile
                        ?: playlist.tracks.firstOrNull()?.trackInfo?.thumbnailUrl

                    if (coverModel != null) {
                        AsyncImage(
                            model = coverModel,
                            contentDescription = playlist.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = palette.primaryAccent,
                            modifier = Modifier.size(72.dp)
                        )
                    }

                    Surface(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = CircleShape,
                        color = Color.White,
                        contentColor = Color.Black,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Kapağı Değiştir",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = playlist.name,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = palette.primaryAccent.copy(alpha = 0.2f),
                        modifier = Modifier.size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = palette.primaryAccent,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Tuna-",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "${playlist.tracks.size} şarkı • $durationText • Herkese açık",
                    fontSize = 12.sp,
                    color = palette.onSurfaceMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularActionButton(
                        icon = Icons.Default.Download,
                        contentDescription = "Tümünü İndir",
                        onClick = { if (playlist.tracks.isNotEmpty()) onPlayTrackAt(0) }
                    )

                    CircularActionButton(
                        icon = Icons.Default.Edit,
                        contentDescription = "Kapağı Düzenle",
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )

                    Surface(
                        onClick = { if (playlist.tracks.isNotEmpty()) onPlayTrackAt(0) },
                        enabled = playlist.tracks.isNotEmpty(),
                        shape = CircleShape,
                        color = palette.onSurface,
                        contentColor = palette.background,
                        modifier = Modifier.size(58.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Listeyi Oynat",
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    CircularActionButton(
                        icon = Icons.Default.Add,
                        contentDescription = "Şarkı Ekle",
                        onClick = { showAddTrackDialog = true }
                    )

                    Box {
                        CircularActionButton(
                            icon = Icons.Default.MoreVert,
                            contentDescription = "Diğer Seçenekler",
                            onClick = { showMoreMenu = true }
                        )

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Listeyi Sil") },
                                onClick = {
                                    showMoreMenu = false
                                    onDeletePlaylist()
                                    onBack()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = palette.tertiaryAccent
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // Section Title: Tracks
        item(key = "detail_tracks_title", span = { GridItemSpan(maxLineSpan) }, contentType = "section_title") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Şarkılar (${playlist.tracks.size})",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.onSurface
                )
                if (playlist.tracks.isNotEmpty()) {
                    Text(
                        text = "${displayedPlaylistTracks.size}/${playlist.tracks.size} aktif",
                        fontSize = 11.sp,
                        color = palette.onSurfaceMuted
                    )
                }
            }
        }

        if (playlist.tracks.isEmpty()) {
            item(key = "detail_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.surfaceContainer)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Bu listede henüz şarkı yok. Yukarıdaki '+' butonuna basarak indirilen şarkılarınızı ekleyebilirsiniz.",
                        color = palette.onSurfaceMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (isGridView) {
            itemsIndexed(
                items = displayedPlaylistTracks,
                key = { _, t -> t.audioFile.absolutePath },
                contentType = { _, _ -> "detail_grid_track" }
            ) { index, saved ->
                val isInActiveWindow = detailWindow.isTrackInActiveMemoryWindow(index)
                val thumbModel = saved.coverFile ?: saved.trackInfo.thumbnailUrl

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.surfaceContainer)
                        .border(1.dp, palette.dividerOrBorder, RoundedCornerShape(16.dp))
                        .clickable { onPlayTrackAt(index) }
                        .padding(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        OptimizedThumbnailImage(
                            model = thumbModel,
                            contentDescription = saved.trackInfo.title,
                            isWithinActiveWindow = isInActiveWindow,
                            fallbackIcon = Icons.Default.MusicNote,
                            fallbackTint = palette.primaryAccent,
                            backgroundColor = palette.surfaceVariant,
                            iconSize = 36.dp,
                            targetPixelSize = 160,
                            modifier = Modifier.fillMaxSize()
                        )

                        Surface(
                            onClick = { onPlayTrackAt(index) },
                            shape = CircleShape,
                            color = palette.primaryAccent,
                            contentColor = palette.onPrimary,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Oynat",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = saved.trackInfo.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = saved.trackInfo.artist,
                            fontSize = 11.sp,
                            color = palette.onSurfaceMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = { onRemoveTrack(saved.audioFile.absolutePath) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Listeden Çıkar",
                                tint = palette.tertiaryAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        } else {
            itemsIndexed(
                items = displayedPlaylistTracks,
                key = { _, t -> t.audioFile.absolutePath },
                contentType = { _, _ -> "detail_list_track" }
            ) { index, saved ->
                val isInActiveWindow = detailWindow.isTrackInActiveMemoryWindow(index)
                val thumbModel = saved.coverFile ?: saved.trackInfo.thumbnailUrl

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.surfaceContainer)
                        .clickable { onPlayTrackAt(index) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.primaryAccent,
                        modifier = Modifier.width(26.dp)
                    )

                    OptimizedThumbnailImage(
                        model = thumbModel,
                        contentDescription = saved.trackInfo.title,
                        isWithinActiveWindow = isInActiveWindow,
                        fallbackIcon = Icons.Default.MusicNote,
                        fallbackTint = palette.primaryAccent,
                        backgroundColor = palette.surfaceVariant,
                        iconSize = 24.dp,
                        targetPixelSize = 120,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = saved.trackInfo.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = saved.trackInfo.artist,
                            fontSize = 12.sp,
                            color = palette.onSurfaceMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = { onRemoveTrack(saved.audioFile.absolutePath) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Listeden Çıkar",
                            tint = palette.tertiaryAccent
                        )
                    }
                }
            }
        }
    }

    if (showAddTrackDialog) {
        val addDialogListState = rememberLazyListState()
        val addDialogWindow = rememberProgressiveWindowState(
            listState = addDialogListState,
            totalItemsCount = allDownloadedTracks.size,
            headerItemsOffset = 0,
            initialBatchSize = 8,
            stepBatchSize = 8,
            activeWindowBuffer = 2
        )
        val displayedAddTracks = remember(allDownloadedTracks, addDialogWindow.loadedCount) {
            allDownloadedTracks.take(addDialogWindow.loadedCount)
        }

        AlertDialog(
            onDismissRequest = { showAddTrackDialog = false },
            title = { Text("İndirilenlerden Şarkı Ekle") },
            text = {
                if (allDownloadedTracks.isEmpty()) {
                    Text("Önce Ana Sayfa veya Keşfet sekmesinden şarkı indirin.")
                } else {
                    LazyColumn(
                        state = addDialogListState,
                        modifier = Modifier.height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            items = displayedAddTracks,
                            key = { it.audioFile.absolutePath },
                            contentType = { "add_track_btn" }
                        ) { saved ->
                            Button(
                                onClick = {
                                    onAddTrack(saved)
                                    showAddTrackDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "${saved.trackInfo.artist} - ${saved.trackInfo.title}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddTrackDialog = false }) {
                    Text("Kapat")
                }
            }
        )
    }
}

@Composable
private fun CircularActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val palette = LocalAppThemePalette.current
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = palette.surfaceContainer,
        contentColor = palette.onSurface,
        modifier = Modifier
            .size(46.dp)
            .border(1.dp, palette.dividerOrBorder, CircleShape)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlaylistBottomSheet(
    onDismiss: () -> Unit,
    onCreate: (String, String, String?) -> Unit
) {
    val palette = LocalAppThemePalette.current
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var coverUri by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coverUri = uri.toString()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Yeni Çalma Listesi Oluştur",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = palette.onSurface
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Liste Adı") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Açıklama (İsteğe Bağlı)") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (coverUri == null) "Galeriden Kapak Seç" else "Kapak Seçildi")
                }

                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            onCreate(name.trim(), description.trim(), coverUri)
                        }
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text("Oluştur")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
