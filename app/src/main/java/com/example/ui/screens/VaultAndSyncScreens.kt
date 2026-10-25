package com.example.ui.screens

import android.content.Intent
import com.example.data.UserPlaylist
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.FileProvider
import com.example.downloader.DownloadFormatOption
import com.example.model.CookieItem
import com.example.model.CookieParser
import com.example.sync.SubscriptionSource
import com.example.ui.components.AddEditCookieDialog
import com.example.ui.components.CookieInspectorPanel
import com.example.ui.components.ExportJsonDialog
import com.example.ui.components.OptimizedThumbnailImage
import com.example.ui.components.rememberProgressiveWindowState
import com.example.ui.theme.LocalAppThemePalette
import com.example.vault.VaultItem
import com.example.viewmodel.CookieViewModel
import java.io.File

enum class VaultFilterType(val label: String) {
    ALL("Tümü"),
    AUDIO("Ses"),
    VIDEO("Video")
}

@Composable
fun VaultScreen(
    vaultItems: List<VaultItem>,
    onPlayVaultItem: (VaultItem) -> Unit,
    onDeleteVaultItem: (VaultItem) -> Unit,
    onRefreshVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalAppThemePalette.current
    var filter by remember { mutableStateOf(VaultFilterType.ALL) }

    val filtered = remember(vaultItems, filter) {
        when (filter) {
            VaultFilterType.ALL -> vaultItems
            VaultFilterType.AUDIO -> vaultItems.filter { !it.isVideo }
            VaultFilterType.VIDEO -> vaultItems.filter { it.isVideo }
        }
    }

    val listState = rememberLazyListState()
    val windowState = rememberProgressiveWindowState(
        listState = listState,
        totalItemsCount = filtered.size,
        headerItemsOffset = 2,
        initialBatchSize = 6,
        stepBatchSize = 6,
        activeWindowBuffer = 2
    )

    val displayedVaultItems = remember(filtered, windowState.loadedCount) {
        filtered.take(windowState.loadedCount)
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "vault_header", contentType = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Sakura Medya Kasası",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.onBackground
                    )
                    Text(
                        text = "ExoPlayer destekli çevrimdışı arşiv (${displayedVaultItems.size}/${filtered.size} aktif)",
                        fontSize = 12.sp,
                        color = palette.onSurfaceMuted
                    )
                }
                IconButton(onClick = onRefreshVault) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Yenile",
                        tint = palette.primaryAccent
                    )
                }
            }
        }

        item(key = "vault_filter_chips", contentType = "filters") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VaultFilterType.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f.label) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item(key = "vault_empty", contentType = "empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(palette.surfaceContainer)
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Kasada bu filtreye uygun medya dosyası bulunamadı.",
                        color = palette.onSurfaceMuted
                    )
                }
            }
        } else {
            itemsIndexed(
                items = displayedVaultItems,
                key = { _, item -> item.id },
                contentType = { _, _ -> "vault_item_row" }
            ) { index, item ->
                val isInActiveWindow = windowState.isTrackInActiveMemoryWindow(index)
                val thumbFile = remember(item.thumbnailPath) {
                    item.thumbnailPath?.takeIf { it.isNotBlank() }?.let { File(it) }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.surfaceContainer)
                        .border(1.dp, palette.dividerOrBorder, RoundedCornerShape(16.dp))
                        .clickable { onPlayVaultItem(item) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OptimizedThumbnailImage(
                        model = thumbFile,
                        contentDescription = item.title,
                        isWithinActiveWindow = isInActiveWindow,
                        fallbackIcon = if (item.isVideo) Icons.Default.Movie else Icons.Default.MusicNote,
                        fallbackTint = palette.primaryAccent,
                        backgroundColor = palette.surfaceVariant,
                        iconSize = 24.dp,
                        targetPixelSize = 128,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.artist} • ${item.resolutionOrBitrate} • ${item.fileSizeBytes / 1024} KB",
                            fontSize = 12.sp,
                            color = palette.onSurfaceMuted
                        )
                    }

                    IconButton(
                        onClick = {
                            try {
                                val f = File(item.filePath)
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    f
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = if (item.isVideo) "video/*" else "audio/*"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, item.title))
                            } catch (_: Exception) {
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Paylaş",
                            tint = palette.secondaryAccent
                        )
                    }

                    IconButton(onClick = { onDeleteVaultItem(item) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Sil",
                            tint = palette.tertiaryAccent
                        )
                    }

                    IconButton(onClick = { onPlayVaultItem(item) }) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Oynat",
                            tint = palette.primaryAccent
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SubscriptionManagerScreen(
    subscriptions: List<SubscriptionSource>,
    isWifiOnly: Boolean,
    intervalHours: Long,
    intervalMinutes: Long = intervalHours * 60L,
    availablePlaylists: List<UserPlaylist> = emptyList(),
    onAddSubscription: (String, String, DownloadFormatOption, String?, String?) -> Unit,
    onUpdateSubscriptionTarget: (String, String?, String?) -> Unit = { _, _, _ -> },
    onRemoveSubscription: (String) -> Unit,
    onToggleSubscription: (String, Boolean) -> Unit,
    onSetWifiOnly: (Boolean) -> Unit,
    onSetIntervalHours: (Long) -> Unit,
    onSetIntervalMinutes: (Long) -> Unit = { onSetIntervalHours((it / 60L).coerceAtLeast(1L)) },
    onTriggerImmediateSync: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppThemePalette.current
    var newTitle by remember { mutableStateOf("") }
    var newUrl by remember { mutableStateOf("") }
    var selectedFormat by remember { mutableStateOf(DownloadFormatOption.AUDIO_M4A_HIGH) }
    var newTargetPlaylistTitle by remember { mutableStateOf<String?>(null) }
    var newTargetPlaylistId by remember { mutableStateOf<String?>(null) }

    var showAddTargetDialog by remember { mutableStateOf(false) }
    var showTargetDialogForSub by remember { mutableStateOf<SubscriptionSource?>(null) }
    var customTargetInput by remember { mutableStateOf("") }
    var tempSelectedTitle by remember { mutableStateOf<String?>(null) }
    var tempSelectedId by remember { mutableStateOf<String?>(null) }

    val presetIntervals = listOf(
        30L to "30 Dakika",
        60L to "1 Saat",
        180L to "3 Saat",
        360L to "6 Saat"
    )

    var isCustomSelected by remember(intervalMinutes) {
        mutableStateOf(presetIntervals.none { it.first == intervalMinutes })
    }
    var customMinutesInput by remember(intervalMinutes) {
        mutableStateOf(if (isCustomSelected) intervalMinutes.toString() else "45")
    }

    val subListState = rememberLazyListState()
    val subWindowState = rememberProgressiveWindowState(
        listState = subListState,
        totalItemsCount = subscriptions.size,
        headerItemsOffset = 4,
        initialBatchSize = 6,
        stepBatchSize = 6,
        activeWindowBuffer = 2
    )
    val displayedSubscriptions = remember(subscriptions, subWindowState.loadedCount) {
        subscriptions.take(subWindowState.loadedCount)
    }

    LazyColumn(
        state = subListState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "sub_header", contentType = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Otomatik Kanal & Liste Senkronizasyonu",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.onBackground
                )
                OutlinedButton(onClick = onBack) {
                    Text("Geri")
                }
            }
        }

        // Wi-Fi Guarantee Informative Card
        item(key = "sub_wifi_card", contentType = "info_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.primaryAccent.copy(alpha = 0.12f))
                    .border(1.dp, palette.primaryAccent.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = palette.primaryAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Wi-Fi Bağlantı & Kuyruk Garantisi (WorkManager)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = palette.primaryAccent
                    )
                }
                Text(
                    text = "Belirlenen kontrol vaktinde Wi-Fi bağlı değilse, senkronizasyon isteği KESİNLİKLE silinmez. Android İş Yöneticisi bu görevi hafızada tutar ve cihaza Wi-Fi bağlandığı ilk anda indirme kontrolünü otomatik başlatır.",
                    fontSize = 11.5.sp,
                    color = palette.onSurface,
                    lineHeight = 16.sp
                )
            }
        }

        // Controls & Interval Selector Card
        item(key = "sub_controls_card", contentType = "controls_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.surfaceContainer)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Yalnızca Wi-Fi Üzerinde Senkronize Et", color = palette.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Hücresel veri kullanımını engeller", color = palette.onSurfaceMuted, fontSize = 11.sp)
                    }
                    Switch(checked = isWifiOnly, onCheckedChange = onSetWifiOnly)
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Kontrol Sıklığı (Mevcut: ${if (intervalMinutes % 60L == 0L) "${intervalMinutes / 60L} Saat" else "$intervalMinutes Dakika"})",
                    color = palette.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetIntervals.forEach { (mins, label) ->
                        val isSelected = !isCustomSelected && intervalMinutes == mins
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                isCustomSelected = false
                                onSetIntervalMinutes(mins)
                            },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                    FilterChip(
                        selected = isCustomSelected,
                        onClick = { isCustomSelected = true },
                        label = { Text("Özel", fontSize = 11.sp) }
                    )
                }

                if (isCustomSelected) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customMinutesInput,
                            onValueChange = { customMinutesInput = it.filter { char -> char.isDigit() } },
                            label = { Text("Özel Süre (Dakika, Min 15)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val parsed = customMinutesInput.toLongOrNull() ?: 30L
                                onSetIntervalMinutes(parsed.coerceAtLeast(15L))
                            }
                        ) {
                            Text("Kaydet")
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onTriggerImmediateSync) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Şimdi Manuel Tetikle")
                    }
                }
            }
        }

        item(key = "sub_add_card", contentType = "add_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.surfaceContainer)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Yeni Kanal veya Çalma Listesi Aboneliği Ekle",
                    fontWeight = FontWeight.Bold,
                    color = palette.onSurface
                )
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Başlık (Örn: Lo-Fi Sakura Kanalı)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newUrl,
                    onValueChange = { newUrl = it },
                    label = { Text("YouTube / SoundCloud Liste veya Kanal URL'si") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Target playlist destination selector card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.surfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, palette.dividerOrBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Kayıt Hedefi (Oynatma Listesi)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.onSurface
                        )
                        Text(
                            text = newTargetPlaylistTitle?.let { "Seçili Liste: $it" }
                                ?: "Varsayılan Kitaplık (Kanal adına göre sahte liste açılmaz)",
                            fontSize = 11.sp,
                            color = if (newTargetPlaylistTitle != null) palette.primaryAccent else palette.onSurfaceMuted
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            tempSelectedTitle = newTargetPlaylistTitle
                            tempSelectedId = newTargetPlaylistId
                            customTargetInput = newTargetPlaylistTitle.orEmpty()
                            showAddTargetDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(if (newTargetPlaylistTitle != null) "Değiştir" else "Hedef Belirle", fontSize = 11.5.sp)
                    }
                }

                Button(
                    onClick = {
                        if (newUrl.isNotBlank()) {
                            onAddSubscription(newTitle, newUrl, selectedFormat, newTargetPlaylistTitle, newTargetPlaylistId)
                            newTitle = ""
                            newUrl = ""
                            newTargetPlaylistTitle = null
                            newTargetPlaylistId = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Aboneliği Kaydet")
                }
            }
        }

        itemsIndexed(
            items = displayedSubscriptions,
            key = { _, sub -> sub.id },
            contentType = { _, _ -> "subscription_row" }
        ) { _, sub ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.surfaceContainer)
                    .border(1.dp, palette.dividerOrBorder, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(sub.title, fontWeight = FontWeight.Bold, color = palette.onSurface)
                    Text(sub.url, fontSize = 11.sp, color = palette.onSurfaceMuted, maxLines = 1)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(palette.primaryAccent.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = sub.targetPlaylistTitle?.let { "Hedef: $it" } ?: "Hedef: Varsayılan Kitaplık",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.primaryAccent
                            )
                        }
                        TextButton(
                            onClick = {
                                showTargetDialogForSub = sub
                                tempSelectedTitle = sub.targetPlaylistTitle
                                tempSelectedId = sub.targetPlaylistId
                                customTargetInput = sub.targetPlaylistTitle.orEmpty()
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("Hedefi Değiştir", fontSize = 11.sp)
                        }
                    }
                    Text(
                        text = "${sub.downloadedVideoIds.size} parça otomatik indirildi",
                        fontSize = 11.sp,
                        color = palette.onSurfaceMuted
                    )
                }
                Switch(
                    checked = sub.isEnabled,
                    onCheckedChange = { onToggleSubscription(sub.id, it) }
                )
                IconButton(onClick = { onRemoveSubscription(sub.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Sil", tint = palette.tertiaryAccent)
                }
            }
        }
    }

    // Modal Dialog for selecting target playlist destination
    if (showAddTargetDialog || showTargetDialogForSub != null) {
        val editingSub = showTargetDialogForSub
        val dialogTitle = if (editingSub != null) {
            "Kayıt Hedefi: ${editingSub.title}"
        } else {
            "Yeni Abonelik Kayıt Hedefi"
        }

        AlertDialog(
            onDismissRequest = {
                showAddTargetDialog = false
                showTargetDialogForSub = null
            },
            title = {
                Text(dialogTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Yeni indirilen içerikler seçilen oynatma listesine otomatik kaydedilir. Hedef seçilmezse parçalar ana kitaplığa bağımsız olarak eklenir.",
                        fontSize = 12.sp,
                        color = palette.onSurfaceMuted,
                        lineHeight = 16.sp
                    )

                    FilterChip(
                        selected = tempSelectedTitle == null && customTargetInput.isBlank(),
                        onClick = {
                            tempSelectedTitle = null
                            tempSelectedId = null
                            customTargetInput = ""
                        },
                        label = { Text("Varsayılan Kitaplık (Oynatma Listesi Yok)") }
                    )

                    if (availablePlaylists.isNotEmpty()) {
                        Text(
                            text = "Mevcut Oynatma Listeleri:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availablePlaylists.take(6).forEach { pl ->
                                val isSelected = tempSelectedTitle.equals(pl.name, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        tempSelectedTitle = pl.name
                                        tempSelectedId = pl.id
                                        customTargetInput = pl.name
                                    },
                                    label = { Text(pl.name, maxLines = 1, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = customTargetInput,
                        onValueChange = {
                            customTargetInput = it
                            tempSelectedTitle = it.trim().takeIf { s -> s.isNotBlank() }
                            tempSelectedId = availablePlaylists.firstOrNull { pl -> pl.name.equals(it.trim(), ignoreCase = true) }?.id
                        },
                        label = { Text("Veya Özel Liste Adı Girin") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalTitle = customTargetInput.trim().takeIf { it.isNotBlank() } ?: tempSelectedTitle
                        val finalId = tempSelectedId
                        if (editingSub != null) {
                            onUpdateSubscriptionTarget(editingSub.id, finalTitle, finalId)
                        } else {
                            newTargetPlaylistTitle = finalTitle
                            newTargetPlaylistId = finalId
                        }
                        showAddTargetDialog = false
                        showTargetDialogForSub = null
                    }
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddTargetDialog = false
                        showTargetDialogForSub = null
                    }
                ) {
                    Text("İptal")
                }
            }
        )
    }
}

@Composable
fun CookieInspectorScreen(
    cookieViewModel: CookieViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val palette = LocalAppThemePalette.current
    val state by cookieViewModel.uiState.collectAsState()

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingCookie by remember { mutableStateOf<CookieItem?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var rawCookiePasteInput by remember { mutableStateOf("") }

    val cookieFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText()
                }
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { fileContent ->
                rawCookiePasteInput = fileContent
                cookieViewModel.injectRawCookieText(fileContent)
            }
        }
    }

    val hasAuthCookie = remember(state.cookies) {
        com.example.downloader.NetscapeCookieHelper.hasYouTubeAuthCookies(state.cookies)
    }

    val hasSapisid = remember(state.cookies) {
        state.cookies.any {
            (it.name.equals("SAPISID", ignoreCase = true) ||
                it.name.equals("__Secure-3PAPISID", ignoreCase = true) ||
                it.name.equals("__Secure-1PAPISID", ignoreCase = true)) && it.value.isNotBlank()
        }
    }

    val hasSecurePsid = remember(state.cookies) {
        state.cookies.any {
            (it.name.equals("__Secure-1PSID", ignoreCase = true) ||
                it.name.equals("__Secure-3PSID", ignoreCase = true) ||
                it.name.equals("SID", ignoreCase = true)) && it.value.isNotBlank()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(bottom = 90.dp)
    ) {
        // Saf Cookie Import (WebView'sız, Bot Korumasına Takılmayan Doğrudan Çerez Aktarım & SAPISIDHASH Doğrulama Paneli)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(palette.surfaceContainer)
                .border(
                    1.dp,
                    if (state.isSessionValidated || hasAuthCookie) palette.primaryAccent.copy(alpha = 0.7f)
                    else palette.dividerOrBorder,
                    RoundedCornerShape(16.dp)
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            state.isSessionValidated -> {
                                val acc = state.verifiedAccountName?.let { " • $it" }.orEmpty()
                                "✅ YT Music Oturumu Doğrulandı (SAPISIDHASH Aktif$acc)"
                            }
                            hasAuthCookie -> "🟢 Çerezler Yüklü (${state.cookies.size} Adet) • Doğrulamaya Hazır"
                            else -> "🔑 Saf Çerez İçe Aktarma (WebView'sız Sıfır Kasma)"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.isSessionValidated || hasAuthCookie) palette.primaryAccent else palette.onSurface
                    )
                    Text(
                        text = "SAPISID: ${if (hasSapisid) "✔ Hazır" else "✖ Eksik"}  •  __Secure-1PSID: ${if (hasSecurePsid) "✔ Hazır" else "✖ Eksik"}  •  Toplam: ${state.cookies.size} çerez",
                        fontSize = 10.5.sp,
                        color = palette.onSurfaceMuted
                    )
                }
            }

            if (!state.statusMessage.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.primaryAccent.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = state.statusMessage.orEmpty(),
                        fontSize = 11.sp,
                        color = palette.onSurface
                    )
                }
            }

            OutlinedTextField(
                value = rawCookiePasteInput,
                onValueChange = { rawCookiePasteInput = it },
                label = {
                    Text(
                        "cookies.txt (Netscape) veya Ham Cookie Başlığı Yapıştırın",
                        fontSize = 11.sp
                    )
                },
                placeholder = {
                    Text(
                        "SAPISID=...; __Secure-1PSID=...; __Secure-3PAPISID=... veya # Netscape HTTP Cookie File",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val clipText = clipboardManager.getText()?.text.orEmpty()
                        if (clipText.isNotBlank()) {
                            rawCookiePasteInput = clipText
                            cookieViewModel.injectRawCookieText(clipText)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text("Panodan Yapıştır", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }

                OutlinedButton(
                    onClick = { cookieFilePicker.launch("*/*") },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text("cookies.txt Seç", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }

                OutlinedButton(
                    onClick = { cookieViewModel.validateCurrentSessionWithInnerTube() },
                    enabled = !state.isValidatingSession,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        if (state.isValidatingSession) "Kontrol..." else "Oturumu Doğrula",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            Button(
                onClick = {
                    cookieViewModel.injectRawCookieText(rawCookiePasteInput)
                    rawCookiePasteInput = ""
                },
                enabled = rawCookiePasteInput.isNotBlank() && !state.isValidatingSession,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Text(
                    text = if (state.isValidatingSession) {
                        "SAPISIDHASH Üretiliyor ve /account/account_menu Doğrulanıyor..."
                    } else {
                        "Çerezleri CookieManager'a Yaz & SAPISIDHASH İle Doğrula"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        CookieInspectorPanel(
            cookies = state.filteredCookies,
            searchQuery = state.searchQuery,
            onSearchQueryChange = cookieViewModel::updateSearchQuery,
            logEntries = state.logEntries,
            onRefreshCookies = cookieViewModel::refreshCookiesForCurrentUrl,
            onAddCookieClick = {
                editingCookie = null
                showAddEditDialog = true
            },
            onEditCookieClick = { c ->
                editingCookie = c
                showAddEditDialog = true
            },
            onDeleteCookieClick = cookieViewModel::deleteCookie,
            onClearAllCookies = cookieViewModel::clearAllCookies,
            onSaveToStorage = cookieViewModel::saveCurrentCookiesToStorage,
            onExportJsonClick = { showExportDialog = true },
            onClearLogs = cookieViewModel::clearLogs,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }

    if (showAddEditDialog) {
        AddEditCookieDialog(
            initialCookie = editingCookie,
            defaultDomain = CookieParser.extractDomain(state.currentUrl),
            onDismiss = { showAddEditDialog = false },
            onSave = { cookie ->
                cookieViewModel.addOrUpdateCookie(cookie)
                showAddEditDialog = false
            }
        )
    }

    if (showExportDialog) {
        ExportJsonDialog(
            jsonContent = cookieViewModel.exportCookiesAsJson(),
            onDismiss = { showExportDialog = false }
        )
    }
}


