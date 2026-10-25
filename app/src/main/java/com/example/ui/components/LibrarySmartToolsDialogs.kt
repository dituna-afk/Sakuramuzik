package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PlayCountManager
import com.example.downloader.SavedMediaResult
import com.example.ui.theme.LocalAppThemePalette
import java.util.Locale

@Composable
fun OfflineTagEditorDialog(
    saved: SavedMediaResult,
    onSaveMetadata: (String, String, String, String?) -> Unit,
    onAutoFetchYoutubeTitle: (SavedMediaResult, (String, String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalAppThemePalette.current
    var titleInput by remember(saved) { mutableStateOf(saved.trackInfo.title) }
    var artistInput by remember(saved) { mutableStateOf(saved.trackInfo.artist) }
    var albumInput by remember(saved) { mutableStateOf(saved.trackInfo.album) }
    var coverUrlInput by remember(saved) {
        mutableStateOf(
            saved.trackInfo.thumbnailUrl?.takeIf { it.startsWith("http") }.orEmpty()
        )
    }
    var isFetchingAuto by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = palette.primaryAccent
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Çevrimdışı ID3 Etiket & Kapak Düzenleyici", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        if (isFetchingAuto) return@OutlinedButton
                        isFetchingAuto = true
                        onAutoFetchYoutubeTitle(saved) { fetchedTitle, fetchedArtist ->
                            isFetchingAuto = false
                            if (fetchedTitle.isNotBlank()) titleInput = fetchedTitle
                            if (fetchedArtist.isNotBlank()) artistInput = fetchedArtist
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isFetchingAuto) "YouTube'dan Çekiliyor..." else "YouTube'dan Gerçek Şarkı Adını Çek",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("Şarkı Adı (Title)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = artistInput,
                    onValueChange = { artistInput = it },
                    label = { Text("Sanatçı (Artist)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = albumInput,
                    onValueChange = { albumInput = it },
                    label = { Text("Albüm / Liste") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = coverUrlInput,
                    onValueChange = { coverUrlInput = it },
                    label = { Text("Yeni Kapak Görseli URL (İsteğe Bağlı)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveMetadata(
                        titleInput,
                        artistInput,
                        albumInput,
                        coverUrlInput.takeIf { it.isNotBlank() }
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primaryAccent,
                    contentColor = palette.onPrimary
                )
            ) {
                Text("Kaydet & Dosyayı Güncelle", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}

@Composable
fun LibraryDoctorCard(
    downloadedTracks: List<SavedMediaResult>,
    isEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onRunDoctorCleanup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current

    val unnamedTracksCount = remember(downloadedTracks) {
        downloadedTracks.count {
            it.trackInfo.title.startsWith("Parça ") || it.trackInfo.artist == "YouTube Playlist"
        }
    }

    val duplicateCount = remember(downloadedTracks) {
        val seen = mutableSetOf<String>()
        var dupes = 0
        for (item in downloadedTracks) {
            val key = if (item.trackInfo.id.length == 11) {
                "id:${item.trackInfo.id}"
            } else {
                "meta:${item.trackInfo.artist.lowercase(Locale.US).trim()}_${item.trackInfo.title.lowercase(Locale.US).trim()}"
            }
            if (!seen.add(key)) dupes++
        }
        dupes
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surfaceContainer)
            .border(1.dp, palette.dividerOrBorder, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CleaningServices,
                    contentDescription = null,
                    tint = palette.primaryAccent
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Kütüphane Doktoru & İsim Onarıcı",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface
                    )
                    Text(
                        text = "İsimsiz ('Parça 1') şarkıları onarır, kopya ve bozuk dosyaları temizler",
                        fontSize = 11.sp,
                        color = palette.onSurfaceMuted
                    )
                }
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggleEnabled
            )
        }

        if (isEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "İsimsiz ('Parça X'): $unnamedTracksCount adet",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (unnamedTracksCount > 0) palette.primaryAccent else palette.onSurfaceMuted
                )
                Text(
                    text = "Kopya Şarkı: $duplicateCount adet",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (duplicateCount > 0) palette.primaryAccent else palette.onSurfaceMuted
                )
            }

            Button(
                onClick = onRunDoctorCleanup,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primaryAccent,
                    contentColor = palette.onPrimary
                ),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp)
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Kütüphaneyi Tara, Gerçek İsimleri Onar & Kopyaları Temizle",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SakuraMusicStatsCard(
    downloadedTracks: List<SavedMediaResult>,
    playCountManager: PlayCountManager,
    isEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current
    val allCounts by playCountManager.playCountsFlow.collectAsState()
    val allListeningMs by playCountManager.listeningMsFlow.collectAsState()
    val totalListeningMs by playCountManager.totalListeningMsFlow.collectAsState()

    val totalPlays = remember(allCounts) { allCounts.values.sum() }
    val totalSeconds = remember(totalListeningMs) { totalListeningMs / 1000L }
    val totalDurationFormatted = remember(totalSeconds) { PlayCountManager.formatDuration(totalSeconds) }

    val totalStorageMb = remember(downloadedTracks) {
        val bytes = downloadedTracks.sumOf { it.audioFile.length() }
        bytes / (1024L * 1024L)
    }

    val topPlayedTracks = remember(downloadedTracks, allCounts, allListeningMs) {
        downloadedTracks
            .map { saved ->
                val id = saved.trackInfo.id.ifBlank { saved.audioFile.name }
                val count = allCounts[id] ?: 0
                val listenSecs = (allListeningMs[id] ?: 0L) / 1000L
                Triple(saved, count, listenSecs)
            }
            .filter { it.second > 0 || it.third > 0 }
            .sortedWith(compareByDescending<Triple<SavedMediaResult, Int, Long>> { it.third }.thenByDescending { it.second })
            .take(3)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surfaceContainer)
            .border(1.dp, palette.dividerOrBorder, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = null,
                    tint = palette.primaryAccent
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Sakura Müzik İstatistikleri (Wrapped)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface
                    )
                    Text(
                        text = "Gerçek dinleme süreniz, oynatma sayısı ve kütüphane boyutu",
                        fontSize = 11.sp,
                        color = palette.onSurfaceMuted
                    )
                }
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggleEnabled
            )
        }

        if (isEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatMetricChip(label = "Toplam Oynatma", value = "$totalPlays Kez")
                StatMetricChip(label = "Toplam Dinleme", value = totalDurationFormatted)
                StatMetricChip(label = "Çevrimdışı Arşiv", value = "${downloadedTracks.size} Şarkı ($totalStorageMb MB)")
            }

            if (topPlayedTracks.isNotEmpty()) {
                Text(
                    text = "En Çok Dinlenen Favorileriniz:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.primaryAccent
                )
                topPlayedTracks.forEachIndexed { idx, (saved, count, listenSecs) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${idx + 1}. ${saved.trackInfo.artist} - ${saved.trackInfo.title}",
                            fontSize = 12.sp,
                            color = palette.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (listenSecs > 0) "$count kez • ${PlayCountManager.formatDuration(listenSecs)}" else "$count kez",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.primaryAccent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMetricChip(
    label: String,
    value: String
) {
    val palette = LocalAppThemePalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = palette.primaryAccent
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = palette.onSurfaceMuted
            )
        }
    }
}
